package com.bilt.pos.session;

import static org.junit.jupiter.api.Assertions.*;

import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.identity.Member;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The local {@link ShopperSession}: a basket and a lifecycle with no terminal anywhere. */
class ShopperSessionTest {

  private static BasketItem candle() {
    return BasketItem.sale("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 2, new BigDecimal("24.99"));
  }

  @Test
  void localSessionRingsABasketAndEndsWithoutATerminal() {
    ShopperSession session =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .storeLocation("store-7")
            .start();

    assertNotNull(session.getSessionId());
    assertEquals("POS-LANE-3", session.getSaleId());
    assertEquals("USD", session.getCurrency());
    assertEquals("store-7", session.getStoreLocation());
    assertNull(session.getMember());
    assertNull(session.member());
    assertTrue(session.basket().snapshot().isEmpty());

    Basket basket = session.basket().addItem(candle());
    assertEquals(1, basket.getItems().size());
    assertEquals(new BigDecimal("49.98"), basket.getGrandTotal());
    assertEquals(basket.getGrandTotal(), session.basket().snapshot().getGrandTotal());

    Basket cleared = session.basket().clear();
    assertTrue(cleared.isEmpty());
    assertNotEquals(basket.getCartId(), cleared.getCartId());

    session.end().executeSync();

    assertThrows(IllegalStateException.class, () -> session.basket().addItem(candle()));
    assertThrows(IllegalStateException.class, () -> session.basket().clear());
    SessionException again = assertThrows(SessionException.class, () -> session.end().get());
    assertEquals(SessionErrorCode.INVALID_STATE, again.getError().getCode());
  }

  @Test
  void endExecutesAsynchronouslyLikeEveryOperation() throws Exception {
    ShopperSession session = ShopperSession.builder().saleId("POS-LANE-3").currency("USD").start();
    CountDownLatch completed = new CountDownLatch(1);
    AtomicReference<SessionError> failure = new AtomicReference<>();

    session.end().onError(failure::set).onComplete(completed::countDown).execute();

    assertTrue(completed.await(5, TimeUnit.SECONDS));
    assertNull(failure.get());
    assertThrows(IllegalStateException.class, () -> session.basket().addItem(candle()));
  }

  @Test
  void closeEndsOnceAndLeavesAnEndedSessionAlone() {
    ShopperSession session = ShopperSession.builder().saleId("POS-LANE-3").currency("USD").start();
    session.close();
    session.close();
    assertThrows(IllegalStateException.class, () -> session.basket().addItem(candle()));
  }

  // ─── Member ───

  @Test
  void resolvedMemberAttachesImmediatelyAndClears() {
    List<Member> changes = new CopyOnWriteArrayList<>();
    ShopperSession session =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .onMemberChanged(changes::add)
            .start();

    session.member(Member.id("mbr_8f2a"));

    assertEquals(Member.id("mbr_8f2a"), session.member());
    assertEquals("mbr_8f2a", session.getMember().getMemberId());
    assertEquals(List.of(Member.id("mbr_8f2a")), changes);

    session.member(Member.id("mbr_8f2a"));
    assertEquals(1, changes.size(), "re-attaching the same member is not a change");

    session.member(null);
    assertNull(session.member());
    assertNull(session.getMember());
    assertEquals(2, changes.size());
    assertNull(changes.get(1));

    session.member(null);
    assertEquals(2, changes.size(), "clearing a cleared member is not a change");
  }

  @Test
  void pendingMemberStaysPendingWithoutAResolver() throws Exception {
    List<Member> changes = new CopyOnWriteArrayList<>();
    ShopperSession session =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .onMemberChanged(changes::add)
            .start();

    Member pending = Member.idResolver().phone("+12015550123");
    session.member(pending);
    // the no-op resolver runs on the operation lane; end() queues behind it
    session.end().executeSync();

    assertSame(pending, session.member());
    assertFalse(session.member().isResolved());
    assertNull(session.getMember(), "a pending member is a guest for settlement purposes");
    assertEquals(List.of(pending), changes);
  }

  @Test
  void aResolverResolvesThePendingMemberAndAnnouncesIt() throws Exception {
    List<Member> changes = new CopyOnWriteArrayList<>();
    CountDownLatch resolved = new CountDownLatch(1);
    ShopperSession.Builder builder =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .onMemberChanged(
                member -> {
                  changes.add(member);
                  if (member != null && member.isResolved()) {
                    resolved.countDown();
                  }
                });
    ShopperSession session =
        new LocalShopperSession(
            builder,
            pending -> {
              assertEquals("+12015550123", pending.resolver().value());
              return Member.id("mbr_" + pending.resolver().type().name().toLowerCase());
            });

    Member pending = Member.idResolver().phone("+12015550123");
    session.member(pending);

    assertTrue(resolved.await(5, TimeUnit.SECONDS));
    assertEquals(Member.id("mbr_phone"), session.member());
    assertEquals("mbr_phone", session.getMember().getMemberId());
    assertEquals(List.of(pending, Member.id("mbr_phone")), changes);
  }

  @Test
  void aFailingResolverReportsABackgroundErrorAndLeavesTheMemberPending() throws Exception {
    List<SessionError> errors = new CopyOnWriteArrayList<>();
    CountDownLatch reported = new CountDownLatch(1);
    ShopperSession.Builder builder =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .onBackgroundError(
                error -> {
                  errors.add(error);
                  reported.countDown();
                });
    ShopperSession session =
        new LocalShopperSession(
            builder,
            pending -> {
              throw new SessionException(
                  new SessionError(SessionErrorCode.NETWORK, "the platform is unreachable"));
            });

    Member pending = Member.idResolver().email("shopper@example.com");
    session.member(pending);

    assertTrue(reported.await(5, TimeUnit.SECONDS));
    assertEquals(SessionErrorCode.NETWORK, errors.get(0).getCode());
    assertSame(pending, session.member());
  }

  @Test
  void aResolverFindingNobodyClearsThePendingMember() throws Exception {
    List<Member> changes = new CopyOnWriteArrayList<>();
    CountDownLatch cleared = new CountDownLatch(1);
    ShopperSession.Builder builder =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .onMemberChanged(
                member -> {
                  changes.add(member);
                  if (member == null) {
                    cleared.countDown();
                  }
                });
    ShopperSession session = new LocalShopperSession(builder, pending -> null);

    Member pending = Member.idResolver().accountId("4823");
    session.member(pending);

    assertTrue(cleared.await(5, TimeUnit.SECONDS));
    assertNull(session.member());
    assertEquals(2, changes.size());
    assertSame(pending, changes.get(0));
  }

  @Test
  void aPreSeededMemberIsInitialStateNotAChange() throws Exception {
    List<Member> changes = new CopyOnWriteArrayList<>();
    ShopperSession session =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .member(Member.id("mbr_8f2a"))
            .onMemberChanged(changes::add)
            .start();

    assertEquals(Member.id("mbr_8f2a"), session.member());
    assertTrue(changes.isEmpty());
  }

  @Test
  void aPreSeededPendingMemberIsResolvedOnStart() throws Exception {
    CountDownLatch resolved = new CountDownLatch(1);
    ShopperSession.Builder builder =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .member(Member.idResolver().accountId("4823"))
            .onMemberChanged(member -> resolved.countDown());
    ShopperSession session =
        new LocalShopperSession(builder, pending -> Member.id("mbr_4823")).start();

    assertTrue(resolved.await(5, TimeUnit.SECONDS));
    assertEquals(Member.id("mbr_4823"), session.member());
  }

  @Test
  void memberChangesAreRefusedAfterEnd() {
    List<Member> announced = new CopyOnWriteArrayList<>();
    ShopperSession session =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .member(Member.id("mbr_8f2a"))
            .onMemberChanged(announced::add)
            .start();
    session.end().executeSync();

    assertThrows(
        IllegalStateException.class,
        () -> session.member(Member.idResolver().phone("+12015550123")));
    assertThrows(IllegalStateException.class, () -> session.member(null));

    assertEquals(Member.id("mbr_8f2a"), session.member());
    assertTrue(announced.isEmpty());
  }

  @Test
  void memberNotificationsArriveInTheOrderTheMemberChanged() throws Exception {
    List<Member> announced = new CopyOnWriteArrayList<>();
    CountDownLatch firstHandlerEntered = new CountDownLatch(1);
    CountDownLatch releaseFirstHandler = new CountDownLatch(1);
    ShopperSession session =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .onMemberChanged(
                member -> {
                  if (announced.isEmpty()) {
                    firstHandlerEntered.countDown();
                    try {
                      releaseFirstHandler.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                    }
                  }
                  announced.add(member);
                })
            .start();

    Thread first = new Thread(() -> session.member(Member.id("mbr_1")));
    first.start();
    assertTrue(firstHandlerEntered.await(5, TimeUnit.SECONDS));

    // a second change while the first notification is still being delivered
    session.member(Member.id("mbr_2"));
    releaseFirstHandler.countDown();
    first.join(5_000);

    assertEquals(List.of(Member.id("mbr_1"), Member.id("mbr_2")), announced);
    assertEquals(Member.id("mbr_2"), session.member());
  }

  @Test
  void memberHandlerRunsOutsideTheSessionLock() throws Exception {
    AtomicReference<ShopperSession> holder = new AtomicReference<>();
    AtomicReference<Boolean> otherThreadGotIn = new AtomicReference<>(false);
    ShopperSession session =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .onMemberChanged(
                member -> {
                  // a handler that waits on another thread's use of the session would deadlock
                  // if it ran under the session lock
                  Thread other =
                      new Thread(
                          () -> {
                            holder.get().context().attribute("k", "v"); // takes the session lock
                            otherThreadGotIn.set(true);
                          });
                  other.start();
                  try {
                    other.join(5_000);
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                  if (other.isAlive()) {
                    other.interrupt();
                  }
                })
            .start();
    holder.set(session);
    session.member(Member.id("mbr_1"));

    assertTrue(otherThreadGotIn.get(), "the handler must not hold the session lock");
  }

  @Test
  void builderRequiresSaleIdAndCurrency() {
    assertThrows(
        IllegalStateException.class, () -> ShopperSession.builder().currency("USD").start());
    assertThrows(
        IllegalStateException.class, () -> ShopperSession.builder().saleId("POS-LANE-3").start());
  }
}
