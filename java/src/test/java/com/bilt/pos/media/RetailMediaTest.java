/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.media;

import static org.junit.jupiter.api.Assertions.*;

import com.bilt.pos.internal.SdkVersion;
import com.bilt.pos.media.service.AdSessionSnapshot;
import com.bilt.pos.media.service.InMemoryAdDecisionService;
import com.bilt.pos.media.service.SessionHandle;
import com.bilt.pos.platform.BiltCredentials;
import com.bilt.pos.platform.BiltEnvironment;
import com.bilt.pos.platform.BiltPlatformClient;
import com.bilt.pos.session.CheckoutPhase;
import com.bilt.pos.session.SessionContextSnapshot;
import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.SessionErrorCode;
import com.bilt.pos.session.ShopperSession;
import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketChange;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.basket.BasketLineItem;
import com.bilt.pos.session.identity.Member;
import com.bilt.pos.widget.Action;
import com.bilt.pos.widget.ActionSink;
import com.bilt.pos.widget.Cta;
import com.bilt.pos.widget.MediaSpec;
import com.bilt.pos.widget.Rendering;
import com.bilt.pos.widget.Surface;
import com.bilt.pos.widget.WebSurface;
import com.bilt.pos.widget.WidgetHost;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RetailMediaTest {

  private static final Placement BANNER = Placement.of("lane-banner");
  private static final Placement INTERSTITIAL = Placement.of("lane-interstitial");

  private final ExecutorService callbacks =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "test-callback"));

  @AfterEach
  void shutDown() {
    callbacks.shutdownNow();
  }

  // ─── Doubles ───

  /** A surface that records what it was asked to draw and keeps the sink it was handed. */
  static class FakeSurface implements Surface {
    final List<Rendering> shown = new CopyOnWriteArrayList<>();
    final List<ActionSink> sinks = new CopyOnWriteArrayList<>();
    volatile int clears;

    @Override
    public void show(Rendering rendering, ActionSink actions) {
      shown.add(rendering);
      sinks.add(actions);
    }

    @Override
    public void clear() {
      clears++;
    }

    ActionSink lastSink() {
      return sinks.get(sinks.size() - 1);
    }
  }

  static final class FakeWebSurface extends WebSurface {
    FakeWebSurface() {
      super("https://media.bilt.test/renderer");
    }

    @Override
    protected void loadUrl(String url) {}

    @Override
    protected void evaluateJavascript(String script) {}
  }

  /** The widget's view of a session, with a recording error sink and a named callback thread. */
  final class FakeHost implements WidgetHost {
    final List<SessionError> errors = new CopyOnWriteArrayList<>();
    volatile SessionContextSnapshot context;

    FakeHost(SessionContextSnapshot context) {
      this.context = context;
    }

    @Override
    public String sessionId() {
      return "sess-1";
    }

    @Override
    public BiltCredentials credentials() {
      return null;
    }

    @Override
    public BiltEnvironment environment() {
      return BiltEnvironment.PRODUCTION;
    }

    @Override
    public BiltPlatformClient platformClient() {
      return null;
    }

    @Override
    public Executor operationExecutor() {
      return Runnable::run;
    }

    @Override
    public Executor callbackExecutor() {
      return callbacks;
    }

    @Override
    public void reportBackgroundError(SessionError error) {
      errors.add(error);
    }

    @Override
    public SessionContextSnapshot context() {
      return context;
    }
  }

  static final class Recorded<T> {
    final T value;
    final Thread thread = Thread.currentThread();

    Recorded(T value) {
      this.value = value;
    }
  }

  private static SessionContextSnapshot context(CheckoutPhase phase) {
    return SessionContextSnapshot.builder()
        .phase(phase)
        .saleId("POS-LANE-3")
        .currency("USD")
        .storeLocation("STR-0142")
        .attribute("lane-type", "pharmacy")
        .build();
  }

  private static Rendering rendering(String creativeId, String applyToken, String secondaryToken) {
    return Rendering.builder()
        .creativeId(creativeId)
        .placement(BANNER.getId())
        .media(MediaSpec.image(URI.create("https://cdn.bilt.test/" + creativeId + ".png")))
        .headline("Save $2 on Sensodyne")
        .cta(Cta.of("Apply offer", Action.APPLY_OFFER, applyToken))
        .secondary(Cta.of("No thanks", Action.DISMISS, secondaryToken))
        .ttl(Duration.ofSeconds(30))
        .build();
  }

  private static Offer offer(String creativeId) {
    return Offer.builder()
        .id("off_1")
        .scope(Offer.Scope.BASKET)
        .amount(new BigDecimal("2.00"))
        .creativeId(creativeId)
        .build();
  }

  private static Basket basketOf(BasketLineItem... items) {
    return Basket.builder()
        .items(Arrays.asList(items))
        .originalTotal(BigDecimal.ZERO)
        .discountTotal(BigDecimal.ZERO)
        .taxTotal(BigDecimal.ZERO)
        .grandTotal(BigDecimal.ZERO)
        .build();
  }

  private static BasketLineItem line(String sku, int quantity) {
    return BasketLineItem.builder()
        .itemId("item-" + sku)
        .sku(sku)
        .description("Item " + sku)
        .category("oral-care")
        .quantity(quantity)
        .unitPrice(new BigDecimal("4.99"))
        .build();
  }

  private static void await(String what, BooleanSupplier condition) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        fail("timed out waiting for " + what);
      }
      Thread.sleep(5);
    }
  }

  private RetailMedia attached(InMemoryAdDecisionService service, FakeHost host, Surface surface) {
    RetailMedia widget = RetailMedia.builder().surface(BANNER, surface).adService(service).build();
    widget.attach(host);
    widget.started(host.context());
    assertNotNull(widget.handle(), "started must register the session");
    return widget;
  }

  // ─── Builder ───

  @Test
  void aWidgetNeedsAtLeastOneSurface() {
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> RetailMedia.builder().build());
    assertTrue(e.getMessage().contains("surface"), e.getMessage());
  }

  @Test
  void aPlacementIsBoundOnce() {
    RetailMedia.Builder builder = RetailMedia.builder().surface(BANNER, new FakeSurface());
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> builder.surface(BANNER, new FakeSurface()));
    assertTrue(e.getMessage().contains("lane-banner"), e.getMessage());
  }

  @Test
  void builderValidatesItsDurationsAndPhases() {
    RetailMedia.Builder builder = RetailMedia.builder().surface(BANNER, new FakeSurface());
    assertThrows(IllegalArgumentException.class, () -> builder.decisionTimeout(Duration.ZERO));
    assertThrows(
        IllegalArgumentException.class, () -> builder.renderingTtl(Duration.ofSeconds(-1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> builder.eligiblePhases(EnumSet.noneOf(CheckoutPhase.class)));
    RetailMedia widget = builder.renderingTtl(null).build();
    assertEquals(Duration.ofMillis(500), widget.decisionTimeout());
    assertNull(widget.renderingTtl());
    assertEquals(
        EnumSet.of(
            CheckoutPhase.SCANNING, CheckoutPhase.MEMBER_IDENTIFIED, CheckoutPhase.TENDERING),
        widget.eligiblePhases());
  }

  // ─── Capabilities ───

  @Test
  void capabilitiesDeriveFromTheSurfaces() {
    RetailMedia widget =
        RetailMedia.builder()
            .surface(BANNER, new FakeSurface())
            .surface(INTERSTITIAL, new FakeWebSurface())
            .build();

    Capabilities capabilities = widget.capabilities();
    assertEquals(SdkVersion.current(), capabilities.getSdkVersion());
    assertNotEquals(SdkVersion.UNKNOWN, capabilities.getSdkVersion());
    assertEquals(EnumSet.allOf(MediaSpec.MediaType.class), capabilities.getFormats());
    assertEquals(EnumSet.allOf(Action.class), capabilities.getActions());
    assertEquals(SurfaceKind.NATIVE, capabilities.getSurfaces().get(BANNER));
    assertEquals(SurfaceKind.WEB, capabilities.getSurfaces().get(INTERSTITIAL));
    assertEquals(
        Arrays.asList(BANNER, INTERSTITIAL), new java.util.ArrayList<>(widget.surfaces().keySet()));
  }

  @Test
  void aSurfaceNarrowsTheFormatsAndTheVersionCanBeOverridden() {
    Surface imagesOnly =
        new FakeSurface() {
          @Override
          public java.util.Set<MediaSpec.MediaType> supportedFormats() {
            return EnumSet.of(MediaSpec.MediaType.IMAGE);
          }
        };
    RetailMedia widget =
        RetailMedia.builder().surface(BANNER, imagesOnly).sdkVersion("9.9.9-wrapper").build();
    assertEquals(EnumSet.of(MediaSpec.MediaType.IMAGE), widget.capabilities().getFormats());
    assertEquals("9.9.9-wrapper", widget.capabilities().getSdkVersion());
  }

  // ─── Attach ───

  @Test
  void withoutAServiceAttachReportsAndTheWidgetStaysInert() {
    FakeSurface surface = new FakeSurface();
    FakeHost host = new FakeHost(context(CheckoutPhase.SCANNING));
    RetailMedia widget = RetailMedia.builder().surface(BANNER, surface).build();

    widget.attach(host);

    assertEquals(1, host.errors.size());
    SessionError error = host.errors.get(0);
    assertEquals(SessionErrorCode.UNSUPPORTED, error.getCode());
    assertTrue(error.getMessage().contains("adService"), error.getMessage());
    assertTrue(error.getMessage().contains("InMemoryAdDecisionService"), error.getMessage());

    widget.started(host.context());
    widget.basketChanged(
        BasketChange.between(
            basketOf(), basketOf(line("SKU-1", 1)), BasketChange.Source.INCREMENTAL));
    widget.memberChanged(Member.id("mbr_1"));
    widget.pause();
    assertTrue(widget.isPaused());
    widget.resume();
    assertFalse(widget.isPaused());
    widget.ended();
    widget.detach();
    assertNull(widget.handle());
    assertEquals(1, host.errors.size(), "an inert widget reports nothing further");
    assertTrue(surface.shown.isEmpty());
  }

  @Test
  void aRealSessionKeepsGoingWhenTheWidgetHasNoService() throws Exception {
    List<SessionError> errors = new CopyOnWriteArrayList<>();
    RetailMedia widget = RetailMedia.builder().surface(BANNER, new FakeSurface()).build();
    ShopperSession session =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .storeLocation("STR-0142")
            .onBackgroundError(errors::add)
            .widget(widget)
            .start();

    await("the attach failure to be reported", () -> !errors.isEmpty());
    assertEquals(SessionErrorCode.UNSUPPORTED, errors.get(0).getCode());
    assertSame(widget, session.widget(RetailMedia.class));
    session.basket().addItem(BasketItem.sale("SKU-1", "Toothpaste", 1, new BigDecimal("4.99")));
    session.end().executeSync();
    assertEquals(1, errors.size());
  }

  @Test
  void aWidgetBelongsToOneSession() {
    FakeHost host = new FakeHost(context(CheckoutPhase.SCANNING));
    RetailMedia widget =
        RetailMedia.builder()
            .surface(BANNER, new FakeSurface())
            .adService(new InMemoryAdDecisionService())
            .build();
    widget.attach(host);
    assertThrows(IllegalStateException.class, () -> widget.attach(host));
  }

  // ─── Session bookkeeping ───

  @Test
  void startedRegistersASnapshotOfTheSession() {
    InMemoryAdDecisionService service = new InMemoryAdDecisionService();
    FakeHost host = new FakeHost(context(CheckoutPhase.MEMBER_IDENTIFIED));

    RetailMedia widget = attached(service, host, new FakeSurface());

    SessionHandle handle = widget.handle();
    assertTrue(service.isOpen(handle));
    List<AdSessionSnapshot> snapshots = service.snapshots(handle);
    assertEquals(1, snapshots.size());
    AdSessionSnapshot registered = snapshots.get(0);
    assertEquals("POS-LANE-3", registered.getSaleId());
    assertEquals("STR-0142", registered.getStoreLocation());
    assertEquals("USD", registered.getCurrency());
    assertEquals("MEMBER_IDENTIFIED", registered.getPhase());
    assertEquals("pharmacy", registered.getAttributes().get("lane-type"));
    assertNull(registered.getLaneId());
    assertNull(registered.getMemberId());
    assertTrue(registered.getLines().isEmpty());
    assertTrue(host.errors.isEmpty());
  }

  @Test
  void basketMemberAndContextChangesUpdateTheSnapshotAndEndedCloses() {
    InMemoryAdDecisionService service = new InMemoryAdDecisionService();
    FakeHost host = new FakeHost(context(CheckoutPhase.SCANNING));
    RetailMedia widget = attached(service, host, new FakeSurface());
    SessionHandle handle = widget.handle();

    Basket one = basketOf(line("SKU-1", 2));
    widget.basketChanged(BasketChange.between(basketOf(), one, BasketChange.Source.INCREMENTAL));
    widget.memberChanged(Member.idResolver().phone("+12015550123"));
    widget.memberChanged(Member.id("mbr_8f2a"));
    widget.contextChanged(context(CheckoutPhase.TENDERING));
    widget.memberChanged(null);

    List<AdSessionSnapshot> snapshots = service.snapshots(handle);
    assertEquals(6, snapshots.size());
    AdSessionSnapshot afterBasket = snapshots.get(1);
    assertEquals(1, afterBasket.getLines().size());
    AdSessionSnapshot.LineSummary summary = afterBasket.getLines().get(0);
    assertEquals("SKU-1", summary.getSku());
    assertEquals("Item SKU-1", summary.getDescription());
    assertEquals(2, summary.getQuantity());
    assertEquals(new BigDecimal("4.99"), summary.getUnitPrice());
    assertEquals("oral-care", summary.getCategory());
    assertNull(snapshots.get(2).getMemberId(), "a pending member is still a guest");
    assertEquals("mbr_8f2a", snapshots.get(3).getMemberId());
    assertEquals("TENDERING", snapshots.get(4).getPhase());
    assertEquals(1, snapshots.get(4).getLines().size(), "context changes keep the lines");
    assertEquals("mbr_8f2a", snapshots.get(4).getMemberId(), "context changes keep the member");
    assertNull(snapshots.get(5).getMemberId(), "sign-out clears the member");

    widget.ended();
    assertFalse(service.isOpen(handle));
    assertNull(widget.handle());
    widget.detach();
    assertTrue(host.errors.isEmpty(), host.errors.toString());
  }

  @Test
  void aRealSessionDrivesTheServiceEndToEnd() throws Exception {
    InMemoryAdDecisionService service = new InMemoryAdDecisionService();
    List<SessionError> errors = new CopyOnWriteArrayList<>();
    RetailMedia widget =
        RetailMedia.builder()
            .surface(BANNER, new FakeSurface())
            .adService(service)
            .onOffer(offer -> {})
            .build();
    ShopperSession session =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .storeLocation("STR-0142")
            .attribute("lane-type", "pharmacy")
            .onBackgroundError(errors::add)
            .widget(widget)
            .start();

    await("the session to register", () -> widget.handle() != null);
    SessionHandle handle = widget.handle();
    session.basket().addItem(BasketItem.sale("SKU-1", "Toothpaste", 1, new BigDecimal("4.99")));
    session.member(Member.id("mbr_8f2a"));
    session.context().phase(CheckoutPhase.TENDERING);
    session.end().executeSync();
    await("the session to close on the service", () -> !service.isOpen(handle));

    List<AdSessionSnapshot> snapshots = service.snapshots(handle);
    assertTrue(snapshots.isEmpty(), "a closed session is forgotten by the fake");
    AdSessionSnapshot last = widget.snapshot();
    assertEquals("STR-0142", last.getStoreLocation());
    assertEquals("pharmacy", last.getAttributes().get("lane-type"));
    assertEquals("TENDERING", last.getPhase());
    assertEquals("mbr_8f2a", last.getMemberId());
    assertEquals(
        Arrays.asList("SKU-1"),
        last.getLines().stream()
            .map(AdSessionSnapshot.LineSummary::getSku)
            .collect(Collectors.toList()));
    assertTrue(errors.isEmpty(), errors.toString());
  }

  @Test
  void aServiceThatFailsToRegisterIsReportedAndTheWidgetGoesInert() {
    InMemoryAdDecisionService service = new InMemoryAdDecisionService();
    service.failNext(new IllegalStateException("edge down"));
    FakeHost host = new FakeHost(context(CheckoutPhase.SCANNING));
    RetailMedia widget =
        RetailMedia.builder().surface(BANNER, new FakeSurface()).adService(service).build();
    widget.attach(host);
    widget.started(host.context());

    assertNull(widget.handle());
    assertEquals(1, host.errors.size());
    assertTrue(host.errors.get(0).getMessage().contains("edge down"));
    assertNotNull(host.errors.get(0).getCause());
    widget.memberChanged(Member.id("mbr_1"));
    assertEquals(1, host.errors.size());
  }

  // ─── Pause, resume, phases ───

  @Test
  void pauseClearsAndSuppressesUntilResume() throws Exception {
    InMemoryAdDecisionService service = new InMemoryAdDecisionService();
    FakeSurface surface = new FakeSurface();
    FakeHost host = new FakeHost(context(CheckoutPhase.SCANNING));
    RetailMedia widget = attached(service, host, surface);
    Rendering first = rendering("crt_1", "tok_a", "tok_b");

    widget.present(BANNER, first);
    await("the rendering to show", () -> surface.shown.size() == 1);
    assertSame(first, widget.current(BANNER));

    assertFalse(widget.isPaused());
    widget.pause();
    widget.pause();
    assertTrue(widget.isPaused());
    await("the surface to clear", () -> surface.clears >= 1);
    assertNull(widget.current(BANNER));

    widget.present(BANNER, rendering("crt_2", "tok_c", "tok_d"));
    Thread.sleep(50);
    assertEquals(1, surface.shown.size(), "nothing shows while paused");

    widget.resume();
    widget.resume();
    assertFalse(widget.isPaused());
    Rendering third = rendering("crt_3", "tok_e", "tok_f");
    widget.present(BANNER, third);
    await("the rendering to show again", () -> surface.shown.size() == 2);
    assertSame(third, widget.current(BANNER));
    widget.detach();
  }

  @Test
  void leavingAnEligiblePhaseClearsTheSurfaces() throws Exception {
    InMemoryAdDecisionService service = new InMemoryAdDecisionService();
    FakeSurface surface = new FakeSurface();
    FakeHost host = new FakeHost(context(CheckoutPhase.TENDERING));
    RetailMedia widget = attached(service, host, surface);
    widget.present(BANNER, rendering("crt_1", "tok_a", "tok_b"));
    await("the rendering to show", () -> surface.shown.size() == 1);

    widget.contextChanged(context(CheckoutPhase.COMPLETE));

    await("the surface to clear", () -> surface.clears >= 1);
    assertNull(widget.current(BANNER));
    assertEquals("COMPLETE", widget.snapshot().getPhase());
    widget.detach();
  }

  // ─── Action sink ───

  @Test
  void anAcceptedApplyOfferReachesTheRegisterOnTheCallbackExecutor() throws Exception {
    InMemoryAdDecisionService service = new InMemoryAdDecisionService();
    FakeSurface surface = new FakeSurface();
    FakeHost host = new FakeHost(context(CheckoutPhase.SCANNING));
    List<Recorded<Offer>> offers = new CopyOnWriteArrayList<>();
    List<Recorded<AdInteraction>> interactions = new CopyOnWriteArrayList<>();
    RetailMedia widget =
        RetailMedia.builder()
            .surface(BANNER, surface)
            .adService(service)
            .onOffer(offer -> offers.add(new Recorded<>(offer)))
            .onInteraction(interaction -> interactions.add(new Recorded<>(interaction)))
            .build();
    widget.attach(host);
    widget.started(host.context());
    SessionHandle handle = widget.handle();

    Rendering rendering = rendering("crt_91ad", "act_apply", "act_dismiss");
    service.onPlacement(BANNER, rendering);
    Optional<Rendering> decided =
        service.decide(handle, BANNER, widget.capabilities(), Duration.ofSeconds(1));
    assertEquals(Optional.of(rendering), decided, "the fake mints the tokens when it serves");
    Offer offer = offer("crt_91ad");
    service.offerFor("act_apply", offer);

    widget.present(BANNER, rendering);
    await("the rendering to show", () -> surface.shown.size() == 1);
    ActionSink sink = surface.lastSink();

    sink.perform(rendering.getCta());

    await("the offer to arrive", () -> !offers.isEmpty());
    await("the acceptance to arrive", () -> interactions.size() >= 2);
    assertEquals(offer, offers.get(0).value);
    assertEquals("test-callback", offers.get(0).thread.getName());
    assertEquals(AdInteraction.Kind.TAPPED, interactions.get(0).value.getKind());
    AdInteraction accepted = interactions.get(1).value;
    assertEquals(AdInteraction.Kind.CTA_ACCEPTED, accepted.getKind());
    assertEquals(Action.APPLY_OFFER, accepted.getAction());
    assertEquals("crt_91ad", accepted.getCreativeId());
    assertEquals(BANNER, accepted.getPlacement());
    assertEquals("test-callback", interactions.get(1).thread.getName());
    assertTrue(host.errors.isEmpty(), host.errors.toString());
    assertSame(rendering, widget.current(BANNER), "an accepted offer leaves the creative up");
    widget.detach();
  }

  @Test
  void aForeignTokenIsDroppedAndReported() throws Exception {
    InMemoryAdDecisionService service = new InMemoryAdDecisionService();
    FakeSurface surface = new FakeSurface();
    FakeHost host = new FakeHost(context(CheckoutPhase.SCANNING));
    List<Offer> offers = new CopyOnWriteArrayList<>();
    RetailMedia widget =
        RetailMedia.builder()
            .surface(BANNER, surface)
            .adService(service)
            .onOffer(offers::add)
            .build();
    widget.attach(host);
    widget.started(host.context());
    Rendering rendering = rendering("crt_91ad", "act_apply", "act_dismiss");
    service.onPlacement(BANNER, rendering);
    service.decide(widget.handle(), BANNER, widget.capabilities(), Duration.ofSeconds(1));
    service.offerFor("act_apply", offer("crt_91ad"));
    widget.present(BANNER, rendering);
    await("the rendering to show", () -> surface.shown.size() == 1);
    ActionSink sink = surface.lastSink();

    sink.perform(Cta.of("Apply offer", Action.APPLY_OFFER, "act_forged"));
    sink.perform(Cta.of("Apply offer", Action.APPLY_OFFER, "act_dismiss"));

    assertEquals(2, host.errors.size(), "both are rejected before any platform call");
    assertEquals(SessionErrorCode.INVALID_STATE, host.errors.get(0).getCode());
    assertTrue(host.errors.get(0).getMessage().contains("act_forged"));
    assertTrue(
        host.errors.get(1).getMessage().contains("DISMISS"), host.errors.get(1).getMessage());
    Thread.sleep(50);
    assertTrue(offers.isEmpty());
    widget.detach();
  }

  @Test
  void aTokenThePlatformRefusesIsReportedAsDeclined() throws Exception {
    InMemoryAdDecisionService service = new InMemoryAdDecisionService();
    FakeSurface surface = new FakeSurface();
    FakeHost host = new FakeHost(context(CheckoutPhase.SCANNING));
    List<Offer> offers = new CopyOnWriteArrayList<>();
    RetailMedia widget =
        RetailMedia.builder()
            .surface(BANNER, surface)
            .adService(service)
            .onOffer(offers::add)
            .build();
    widget.attach(host);
    widget.started(host.context());
    // never served by the fake, so its tokens are unknown to the platform
    Rendering rendering = rendering("crt_unserved", "act_x", "act_y");
    widget.present(BANNER, rendering);
    await("the rendering to show", () -> surface.shown.size() == 1);

    surface.lastSink().perform(rendering.getCta());

    await("the refusal to be reported", () -> !host.errors.isEmpty());
    assertEquals(SessionErrorCode.DECLINED, host.errors.get(0).getCode());
    assertTrue(host.errors.get(0).getMessage().contains("unknown token"));
    assertTrue(offers.isEmpty());
    widget.detach();
  }

  @Test
  void aStaleSinkIsRejectedAndADismissClearsThePlacement() throws Exception {
    InMemoryAdDecisionService service = new InMemoryAdDecisionService();
    FakeSurface surface = new FakeSurface();
    FakeHost host = new FakeHost(context(CheckoutPhase.SCANNING));
    List<AdInteraction> interactions = new CopyOnWriteArrayList<>();
    RetailMedia widget =
        RetailMedia.builder()
            .surface(BANNER, surface)
            .adService(service)
            .onInteraction(interactions::add)
            .build();
    widget.attach(host);
    widget.started(host.context());
    Rendering first = rendering("crt_1", "tok_a", "tok_b");
    Rendering second = rendering("crt_2", "tok_c", "tok_d");
    service.onPlacement(BANNER, first, second);
    service.decide(widget.handle(), BANNER, widget.capabilities(), Duration.ofSeconds(1));
    service.decide(widget.handle(), BANNER, widget.capabilities(), Duration.ofSeconds(1));
    widget.present(BANNER, first);
    widget.present(BANNER, second);
    await("both renderings to show", () -> surface.shown.size() == 2);
    ActionSink stale = surface.sinks.get(0);
    ActionSink live = surface.sinks.get(1);

    stale.perform(first.getSecondary());
    assertEquals(1, host.errors.size());
    assertTrue(host.errors.get(0).getMessage().contains("no longer showing"));
    stale.viewed(first);
    stale.dismissed(first);

    live.viewed(second);
    live.viewed(second);
    live.perform(second.getSecondary());

    await("the placement to clear", () -> widget.current(BANNER) == null);
    await("the interactions to arrive", () -> interactions.size() >= 3);
    assertEquals(
        Arrays.asList(
            AdInteraction.Kind.VIEWED, AdInteraction.Kind.TAPPED, AdInteraction.Kind.CTA_ACCEPTED),
        interactions.stream().map(AdInteraction::getKind).collect(Collectors.toList()));
    assertTrue(interactions.stream().allMatch(i -> i.getCreativeId().equals("crt_2")));
    assertEquals(1, surface.clears);
    assertEquals(1, host.errors.size(), host.errors.toString());
    widget.detach();
  }

  @Test
  void aThrowingRegisterHandlerIsReportedNotPropagated() throws Exception {
    InMemoryAdDecisionService service = new InMemoryAdDecisionService();
    FakeSurface surface = new FakeSurface();
    FakeHost host = new FakeHost(context(CheckoutPhase.SCANNING));
    RetailMedia widget =
        RetailMedia.builder()
            .surface(BANNER, surface)
            .adService(service)
            .onInteraction(
                interaction -> {
                  throw new IllegalStateException("register UI is gone");
                })
            .build();
    widget.attach(host);
    widget.started(host.context());
    Rendering rendering = rendering("crt_1", "tok_a", "tok_b");
    widget.present(BANNER, rendering);
    await("the rendering to show", () -> surface.shown.size() == 1);

    surface.lastSink().viewed(rendering);

    await("the handler failure to be reported", () -> !host.errors.isEmpty());
    assertTrue(host.errors.get(0).getMessage().contains("onInteraction"));
    assertTrue(host.errors.get(0).getMessage().contains("register UI is gone"));
    widget.detach();
  }
}
