package com.bilt.pos.session;

import static org.junit.jupiter.api.Assertions.*;

import com.bilt.pos.platform.BiltEnvironment;
import com.bilt.pos.session.basket.BasketChange;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.identity.Member;
import com.bilt.pos.widget.SessionObserver;
import com.bilt.pos.widget.Widget;
import com.bilt.pos.widget.WidgetHost;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Widgets on a local {@link ShopperSession}: the {@link SessionObserver} seam that delivers the
 * session's state to them on the operation lane, the attach/detach lifecycle around it, the {@link
 * WidgetHost} they are handed, and the builder and accessor that register and find them.
 */
class WidgetSessionTest {

  private static BasketItem item(String sku) {
    return BasketItem.sale(sku, "Item " + sku, 1, new BigDecimal("10.00"));
  }

  /** One recorded callback: its name, its argument and the thread it ran on. */
  static final class Recorded {
    final String name;
    final Object argument;
    final Thread thread;

    Recorded(String name, Object argument) {
      this.name = name;
      this.argument = argument;
      this.thread = Thread.currentThread();
    }

    @Override
    public String toString() {
      return name;
    }
  }

  /** A widget that records every call the session makes, in order, with the delivering thread. */
  static class RecordingWidget implements Widget {

    final List<Recorded> calls = new CopyOnWriteArrayList<>();
    final CountDownLatch detached = new CountDownLatch(1);
    volatile WidgetHost host;
    private volatile boolean paused;

    List<String> names() {
      return calls.stream().map(call -> call.name).collect(Collectors.toList());
    }

    Recorded call(String name) {
      return calls.stream().filter(call -> call.name.equals(name)).findFirst().orElseThrow();
    }

    List<BasketChange> basketChanges() {
      return calls.stream()
          .filter(call -> call.name.equals("basketChanged"))
          .map(call -> (BasketChange) call.argument)
          .collect(Collectors.toList());
    }

    void record(String name, Object argument) {
      calls.add(new Recorded(name, argument));
    }

    @Override
    public void attach(WidgetHost host) {
      this.host = host;
      record("attach", host);
    }

    @Override
    public void detach() {
      record("detach", null);
      detached.countDown();
    }

    @Override
    public void pause() {
      paused = true;
    }

    @Override
    public void resume() {
      paused = false;
    }

    @Override
    public boolean isPaused() {
      return paused;
    }

    @Override
    public void started(SessionContextSnapshot context) {
      record("started", context);
    }

    @Override
    public void memberChanged(Member member) {
      record("memberChanged", member);
    }

    @Override
    public void contextChanged(SessionContextSnapshot context) {
      record("contextChanged", context);
    }

    @Override
    public void basketChanged(BasketChange change) {
      record("basketChanged", change);
    }

    @Override
    public void ended() {
      record("ended", null);
    }
  }

  /** A second widget type, so the typed accessor has something to miss. */
  static final class OtherWidget extends RecordingWidget {}

  private static Thread laneThread(ShopperSession session) {
    return ((AbstractShopperSession) session).operations.callOrdered(Thread::currentThread);
  }

  // ─── Delivery ───

  @Test
  void widgetSeesTheScriptedSessionInOrderOnTheOperationLane() throws Exception {
    RecordingWidget widget = new RecordingWidget();
    ShopperSession session =
        ShopperSession.builder().saleId("POS-LANE-3").currency("USD").widget(widget).start();
    Thread lane = laneThread(session);

    session.basket().addItem(item("SKU-1"));
    session.context().phase(CheckoutPhase.TENDERING);
    session.member(Member.id("mbr_8f2a"));
    session.end().executeSync();

    assertTrue(widget.detached.await(5, TimeUnit.SECONDS), "detach must follow ended");
    assertEquals(
        Arrays.asList(
            "attach",
            "started",
            "basketChanged",
            "contextChanged",
            "memberChanged",
            "ended",
            "detach"),
        widget.names());
    for (Recorded call : widget.calls) {
      assertSame(lane, call.thread, call.name + " must run on the operation lane");
      assertTrue(call.thread.getName().startsWith("bilt-session-"), call.thread.getName());
    }

    SessionContextSnapshot atStart = (SessionContextSnapshot) widget.call("started").argument;
    assertEquals(CheckoutPhase.SCANNING, atStart.phase());
    assertEquals("POS-LANE-3", atStart.saleId());
    BasketChange change = widget.basketChanges().get(0);
    assertTrue(change.previous().isEmpty());
    assertEquals(1, change.current().getItems().size());
    assertEquals(BasketChange.Source.INCREMENTAL, change.source());
    SessionContextSnapshot afterPhase =
        (SessionContextSnapshot) widget.call("contextChanged").argument;
    assertEquals(CheckoutPhase.TENDERING, afterPhase.phase());
    assertEquals(Member.id("mbr_8f2a"), widget.call("memberChanged").argument);
  }

  @Test
  void aSeededMemberFollowsStarted() throws Exception {
    RecordingWidget widget = new RecordingWidget();
    ShopperSession session =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .member(Member.id("mbr_seed"))
            .widget(widget)
            .start();
    session.end().executeSync();

    assertTrue(widget.detached.await(5, TimeUnit.SECONDS));
    assertEquals(
        Arrays.asList("attach", "started", "memberChanged", "ended", "detach"), widget.names());
    assertEquals(Member.id("mbr_seed"), widget.call("memberChanged").argument);
  }

  @Test
  void closeDeliversEndedAndDetaches() throws Exception {
    RecordingWidget widget = new RecordingWidget();
    ShopperSession session =
        ShopperSession.builder().saleId("POS-LANE-3").currency("USD").widget(widget).start();
    session.close();

    assertTrue(widget.detached.await(5, TimeUnit.SECONDS));
    assertEquals(Arrays.asList("attach", "started", "ended", "detach"), widget.names());
  }

  @Test
  void fastBasketChangesConflateIntoOneChangeSpanningTheGap() throws Exception {
    CountDownLatch firstDelivering = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    RecordingWidget widget =
        new RecordingWidget() {
          @Override
          public void basketChanged(BasketChange change) {
            super.basketChanged(change);
            if (basketChanges().size() == 1) {
              // hold the lane on the first delivery so the ring-up
              // outruns it, the way a slow render would
              firstDelivering.countDown();
              try {
                release.await(5, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            }
          }
        };
    ShopperSession session =
        ShopperSession.builder().saleId("POS-LANE-3").currency("USD").widget(widget).start();

    session.basket().addItem(item("SKU-1"));
    assertTrue(firstDelivering.await(5, TimeUnit.SECONDS));
    session.basket().addItem(item("SKU-2"));
    session.basket().mutate(m -> m.addItem(item("SKU-3")));
    session.basket().addItem(item("SKU-4"));
    release.countDown();
    session.end().executeSync();
    assertTrue(widget.detached.await(5, TimeUnit.SECONDS));

    List<BasketChange> changes = widget.basketChanges();
    assertEquals(2, changes.size(), "three changes behind an in-flight delivery merge into one");
    BasketChange merged = changes.get(1);
    assertEquals(
        changes.get(0).current().getGrandTotal(),
        merged.previous().getGrandTotal(),
        "the merged diff starts where the last ended");
    assertEquals(1, merged.previous().getItems().size());
    assertEquals(4, merged.current().getItems().size());
    assertEquals(3, merged.added().size());
    assertEquals(
        BasketChange.Source.BATCH,
        merged.source(),
        "changes of different sources merge as a batch");
  }

  @Test
  void aChangeUndoneBeforeDeliveryIsDropped() throws Exception {
    CountDownLatch firstDelivering = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    RecordingWidget widget =
        new RecordingWidget() {
          @Override
          public void basketChanged(BasketChange change) {
            super.basketChanged(change);
            if (basketChanges().size() == 1) {
              firstDelivering.countDown();
              try {
                release.await(5, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            }
          }
        };
    ShopperSession session =
        ShopperSession.builder().saleId("POS-LANE-3").currency("USD").widget(widget).start();

    session.basket().addItem(item("SKU-1"));
    assertTrue(firstDelivering.await(5, TimeUnit.SECONDS));
    session.basket().addItem(item("SKU-2"));
    session.basket().removeItemBySku("SKU-2");
    release.countDown();
    session.end().executeSync();
    assertTrue(widget.detached.await(5, TimeUnit.SECONDS));

    assertEquals(1, widget.basketChanges().size());
    assertEquals(
        Arrays.asList("attach", "started", "basketChanged", "ended", "detach"), widget.names());
  }

  // ─── Failures ───

  @Test
  void aThrowingObserverIsReportedAndTheOthersStillHear() throws Exception {
    CountDownLatch reported = new CountDownLatch(1);
    AtomicReference<SessionError> error = new AtomicReference<>();
    RecordingWidget failing =
        new RecordingWidget() {
          @Override
          public void basketChanged(BasketChange change) {
            super.basketChanged(change);
            throw new IllegalStateException("render blew up");
          }
        };
    RecordingWidget healthy = new RecordingWidget();
    ShopperSession session =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .onBackgroundError(
                e -> {
                  error.set(e);
                  reported.countDown();
                })
            .widget(failing)
            .widget(healthy)
            .start();

    session.basket().addItem(item("SKU-1"));
    session.end().executeSync();

    assertTrue(reported.await(5, TimeUnit.SECONDS), "the failure must reach onBackgroundError");
    assertEquals(SessionErrorCode.UNKNOWN, error.get().getCode());
    assertTrue(error.get().getMessage().contains("basketChanged"), error.get().getMessage());
    assertTrue(error.get().getCause() instanceof IllegalStateException);
    assertTrue(healthy.detached.await(5, TimeUnit.SECONDS));
    assertTrue(failing.detached.await(5, TimeUnit.SECONDS));
    assertEquals(
        Arrays.asList("attach", "started", "basketChanged", "ended", "detach"), healthy.names());
    assertEquals(
        Arrays.asList("attach", "started", "basketChanged", "ended", "detach"), failing.names());
  }

  @Test
  void aWidgetThatFailsToAttachIsReportedAndLeftOut() throws Exception {
    CountDownLatch reported = new CountDownLatch(1);
    AtomicReference<SessionError> error = new AtomicReference<>();
    RecordingWidget refusing =
        new RecordingWidget() {
          @Override
          public void attach(WidgetHost host) {
            throw new SessionException(
                new SessionError(SessionErrorCode.INVALID_STATE, "needs platform credentials"));
          }
        };
    RecordingWidget healthy = new RecordingWidget();
    ShopperSession session =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .onBackgroundError(
                e -> {
                  error.set(e);
                  reported.countDown();
                })
            .widget(refusing)
            .widget(healthy)
            .start();

    assertTrue(reported.await(5, TimeUnit.SECONDS));
    assertEquals(SessionErrorCode.INVALID_STATE, error.get().getCode());
    assertEquals("needs platform credentials", error.get().getMessage());
    assertTrue(session.widgets().contains(refusing), "still registered, just detached");

    session.basket().addItem(item("SKU-1"));
    session.end().executeSync();
    assertTrue(healthy.detached.await(5, TimeUnit.SECONDS));
    assertEquals(
        Arrays.asList("attach", "started", "basketChanged", "ended", "detach"), healthy.names());
    assertEquals(List.of(), refusing.names(), "a widget that never attached hears nothing");
  }

  // ─── Host ───

  @Test
  void theHostIsTheWidgetsViewOfTheSession() throws Exception {
    CountDownLatch reported = new CountDownLatch(1);
    AtomicReference<SessionError> error = new AtomicReference<>();
    RecordingWidget widget = new RecordingWidget();
    ShopperSession session =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .attribute("lane-type", "pharmacy")
            .onBackgroundError(
                e -> {
                  error.set(e);
                  reported.countDown();
                })
            .widget(widget)
            .start();
    WidgetHost host = widget.host;

    assertNotNull(host);
    assertEquals(session.getSessionId(), host.sessionId());
    assertNull(host.credentials());
    assertEquals(BiltEnvironment.PRODUCTION, host.environment());
    assertNull(host.platformClient(), "no credentials, no client");
    assertEquals("pharmacy", host.context().attributes().get("lane-type"));
    assertNotNull(host.callbackExecutor());

    Thread lane = laneThread(session);
    AtomicReference<Thread> ran = new AtomicReference<>();
    CountDownLatch done = new CountDownLatch(1);
    host.operationExecutor()
        .execute(
            () -> {
              ran.set(Thread.currentThread());
              done.countDown();
            });
    assertTrue(done.await(5, TimeUnit.SECONDS));
    assertSame(lane, ran.get());

    host.reportBackgroundError(new SessionError(SessionErrorCode.NETWORK, "decision timed out"));
    assertTrue(reported.await(5, TimeUnit.SECONDS));
    assertEquals(SessionErrorCode.NETWORK, error.get().getCode());
    assertEquals("decision timed out", error.get().getMessage());

    session.end().executeSync();
  }

  @Test
  void theHostReflectsTheBuildersEnvironmentAndCredentials() throws Exception {
    RecordingWidget widget = new RecordingWidget();
    ShopperSession session =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .environment(BiltEnvironment.STAGING)
            .credentials(
                com.bilt.pos.platform.BiltCredentials.clientCredentials("client", "secret"))
            .widget(widget)
            .start();

    assertEquals(BiltEnvironment.STAGING, widget.host.environment());
    assertEquals("client", widget.host.credentials().clientId());
    assertNotNull(widget.host.platformClient(), "credentials make a client available");
    assertSame(
        widget.host.platformClient(), widget.host.platformClient(), "one client per session");

    session.end().executeSync();
    assertTrue(widget.detached.await(5, TimeUnit.SECONDS));
    assertNull(widget.host.platformClient(), "closed with the session");
  }

  // ─── Registration and lookup ───

  @Test
  void typedAccessorFindsTheWidgetAndRefusesAMissingOne() {
    RecordingWidget recording = new RecordingWidget();
    ShopperSession session =
        ShopperSession.builder().saleId("POS-LANE-3").currency("USD").widget(recording).start();

    assertSame(recording, session.widget(RecordingWidget.class));
    assertSame(recording, session.widget(Widget.class), "matches by assignability");
    IllegalArgumentException missing =
        assertThrows(IllegalArgumentException.class, () -> session.widget(OtherWidget.class));
    assertTrue(missing.getMessage().contains("OtherWidget"), missing.getMessage());
    assertEquals(List.of(recording), session.widgets());

    session.end().executeSync();
  }

  @Test
  void typedAccessorRefusesAnAmbiguousType() {
    RecordingWidget first = new RecordingWidget();
    RecordingWidget second = new RecordingWidget();
    ShopperSession session =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .widget(first)
            .widget(second)
            .start();

    assertThrows(IllegalArgumentException.class, () -> session.widget(RecordingWidget.class));
    assertEquals(Arrays.asList(first, second), session.widgets());

    session.end().executeSync();
  }

  @Test
  void widgetsCollectionRegistersEachInOrderAndDuplicatesAreRefused() {
    RecordingWidget first = new RecordingWidget();
    OtherWidget second = new OtherWidget();
    List<Widget> assembled = new ArrayList<>(Arrays.asList(first, second));
    ShopperSession.Builder builder =
        ShopperSession.builder().saleId("POS-LANE-3").currency("USD").widgets(assembled);

    assertThrows(IllegalArgumentException.class, () -> builder.widget(first));
    ShopperSession session = builder.start();
    assertEquals(Arrays.asList(first, second), session.widgets());
    assertSame(second, session.widget(OtherWidget.class));
    assertThrows(UnsupportedOperationException.class, () -> session.widgets().add(first));

    session.end().executeSync();
  }

  @Test
  void aSessionWithoutWidgetsHasNone() {
    ShopperSession session = ShopperSession.builder().saleId("POS-LANE-3").currency("USD").start();
    assertTrue(session.widgets().isEmpty());
    assertThrows(IllegalArgumentException.class, () -> session.widget(RecordingWidget.class));
    session.end().executeSync();
  }
}
