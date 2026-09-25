package com.bilt.pos.session;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;

/** {@link SessionContext} on the local session, and the change publication behind it. */
class SessionContextTest {

  @Test
  void contextIsReadableAndSettableOnALocalSession() {
    ShopperSession session =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .storeLocation("store-7")
            .start();
    SessionContext context = session.context();

    assertSame(context, session.context(), "one context per session");
    assertEquals(CheckoutPhase.SCANNING, context.phase());
    assertTrue(context.attributes().isEmpty());
    assertEquals("POS-LANE-3", context.saleId());
    assertEquals("USD", context.currency());
    assertEquals("store-7", context.storeLocation());
    assertNull(context.poiId(), "a local session has no terminal");

    context.phase(CheckoutPhase.MEMBER_IDENTIFIED).attribute("lane-type", "pharmacy");

    assertEquals(CheckoutPhase.MEMBER_IDENTIFIED, context.phase());
    assertEquals(Map.of("lane-type", "pharmacy"), context.attributes());
    assertThrows(NullPointerException.class, () -> context.phase(null));
    assertThrows(NullPointerException.class, () -> context.attribute(null, "x"));
  }

  @Test
  void builderSeedsPhaseAndAttributes() {
    ShopperSession session =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .phase(CheckoutPhase.MEMBER_IDENTIFIED)
            .attribute("lane-type", "pharmacy")
            .attribute("cashier-assisted", "true")
            .attribute("dropped", "yes")
            .attribute("dropped", null)
            .start();

    assertEquals(CheckoutPhase.MEMBER_IDENTIFIED, session.context().phase());
    assertEquals(
        List.of("lane-type", "cashier-assisted"),
        new ArrayList<>(session.context().attributes().keySet()),
        "insertion order is kept");
    assertEquals("true", session.context().attributes().get("cashier-assisted"));
    assertThrows(NullPointerException.class, () -> ShopperSession.builder().phase(null));
  }

  @Test
  void attributesAddRemoveAndSnapshotIndependently() {
    ShopperSession session = ShopperSession.builder().saleId("POS-LANE-3").currency("USD").start();
    SessionContext context = session.context();

    context.attribute("a", "1").attribute("b", "2");
    Map<String, String> before = context.attributes();
    context.attribute("a", "1b");
    context.removeAttribute("b");
    context.removeAttribute("never-set");
    context.attribute("c", "3").attribute("c", null);

    assertEquals(Map.of("a", "1", "b", "2"), before, "an earlier snapshot is unaffected");
    assertEquals(Map.of("a", "1b"), context.attributes());
    assertThrows(UnsupportedOperationException.class, () -> context.attributes().put("x", "y"));
    assertThrows(
        UnsupportedOperationException.class, () -> context.snapshot().attributes().remove("a"));
  }

  @Test
  void snapshotIsAConsistentImmutableCopy() {
    ShopperSession session =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .storeLocation("store-7")
            .attribute("lane-type", "pharmacy")
            .start();

    SessionContextSnapshot snapshot = session.context().snapshot();
    session.context().phase(CheckoutPhase.TENDERING).attribute("lane-type", "grocery");

    assertEquals(CheckoutPhase.SCANNING, snapshot.phase());
    assertEquals(Map.of("lane-type", "pharmacy"), snapshot.attributes());
    assertEquals("POS-LANE-3", snapshot.saleId());
    assertEquals("USD", snapshot.currency());
    assertEquals("store-7", snapshot.storeLocation());
    assertNull(snapshot.poiId());
    assertEquals(
        SessionContextSnapshot.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .storeLocation("store-7")
            .attribute("lane-type", "pharmacy")
            .build(),
        snapshot,
        "snapshots are values");
    assertNotEquals(snapshot, session.context().snapshot());
    assertEquals(CheckoutPhase.TENDERING, session.context().snapshot().phase());
  }

  @Test
  void contextWritesAreRefusedAfterEndButReadsStillWork() {
    ShopperSession session = ShopperSession.builder().saleId("POS-LANE-3").currency("USD").start();
    session.context().phase(CheckoutPhase.COMPLETE).attribute("lane-type", "pharmacy");
    session.end().executeSync();

    assertThrows(
        IllegalStateException.class, () -> session.context().phase(CheckoutPhase.SCANNING));
    assertThrows(IllegalStateException.class, () -> session.context().attribute("a", "1"));
    assertThrows(IllegalStateException.class, () -> session.context().removeAttribute("lane-type"));
    assertEquals(CheckoutPhase.COMPLETE, session.context().phase());
    assertEquals(Map.of("lane-type", "pharmacy"), session.context().attributes());
  }

  @Test
  void everyEffectiveWritePublishesASnapshotUnderTheLock() {
    ReentrantLock lock = new ReentrantLock();
    List<SessionContextSnapshot> published = new ArrayList<>();
    DefaultSessionContext context =
        new DefaultSessionContext(
            lock,
            "POS-LANE-3",
            "USD",
            null,
            "VictaLane-275839164",
            CheckoutPhase.SCANNING,
            Map.of("seeded", "yes"),
            () -> false,
            snapshot -> {
              assertTrue(lock.isHeldByCurrentThread(), "published under the session lock");
              published.add(snapshot);
            });

    context.phase(CheckoutPhase.SCANNING); // unchanged: nothing published
    context.phase(CheckoutPhase.TENDERING);
    context.attribute("seeded", "yes"); // unchanged: nothing published
    context.attribute("lane-type", "pharmacy");
    context.removeAttribute("never-set"); // unchanged: nothing published
    context.removeAttribute("seeded");

    assertEquals(3, published.size());
    assertEquals(CheckoutPhase.TENDERING, published.get(0).phase());
    assertEquals(Map.of("seeded", "yes"), published.get(0).attributes());
    assertEquals(Map.of("seeded", "yes", "lane-type", "pharmacy"), published.get(1).attributes());
    assertEquals(Map.of("lane-type", "pharmacy"), published.get(2).attributes());
    assertEquals("VictaLane-275839164", published.get(2).poiId());
    assertFalse(lock.isLocked(), "the lock is released after every call");
  }
}
