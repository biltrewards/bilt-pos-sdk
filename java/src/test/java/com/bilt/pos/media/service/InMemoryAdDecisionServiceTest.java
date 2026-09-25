package com.bilt.pos.media.service;

import static org.junit.jupiter.api.Assertions.*;

import com.bilt.pos.media.AdInteraction;
import com.bilt.pos.media.Capabilities;
import com.bilt.pos.media.Offer;
import com.bilt.pos.media.Placement;
import com.bilt.pos.media.SurfaceKind;
import com.bilt.pos.widget.Action;
import com.bilt.pos.widget.Cta;
import com.bilt.pos.widget.MediaSpec;
import com.bilt.pos.widget.Rendering;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InMemoryAdDecisionServiceTest {

  private static final Placement BANNER = Placement.of("lane-banner");
  private static final Cta APPLY = Cta.of("Apply offer", Action.APPLY_OFFER, "act_apply");
  private static final Cta TEXT_ME = Cta.of("Text me", Action.SEND_TO_PHONE, "act_text");
  private static final Duration TIMEOUT = Duration.ofMillis(500);

  private static final Capabilities FULL =
      Capabilities.builder()
          .sdkVersion("test")
          .format(MediaSpec.MediaType.IMAGE)
          .format(MediaSpec.MediaType.VIDEO)
          .action(Action.APPLY_OFFER)
          .action(Action.SEND_TO_PHONE)
          .action(Action.DISMISS)
          .placement(BANNER, SurfaceKind.WEB)
          .build();

  private static Rendering.Builder creative(String id) {
    return Rendering.builder()
        .creativeId(id)
        .placement(BANNER.getId())
        .media(MediaSpec.image(URI.create("https://cdn.example/" + id + ".png")))
        .headline("Save on " + id)
        .ttl(Duration.ofSeconds(30));
  }

  private static AdSessionSnapshot snapshot(String phase, String... skus) {
    AdSessionSnapshot.Builder builder =
        AdSessionSnapshot.builder()
            .saleId("sale-1")
            .storeLocation("store-42")
            .laneId("lane-3")
            .currency("USD")
            .phase(phase);
    for (String sku : skus) {
      builder.addLine(AdSessionSnapshot.LineSummary.of(sku, sku, 1, new BigDecimal("4.99")));
    }
    return builder.build();
  }

  private static Offer offer(String creativeId) {
    return Offer.builder()
        .id("off-1")
        .scope(Offer.Scope.LINE_ITEM)
        .sku("SENSO-1")
        .amount(new BigDecimal("2.00"))
        .creativeId(creativeId)
        .build();
  }

  private static final class RecordingListener implements AdEventListener {
    final List<Offer> offers = new ArrayList<>();
    final List<AdInteraction> interactions = new ArrayList<>();

    @Override
    public void onOffer(Offer offer) {
      offers.add(offer);
    }

    @Override
    public void onInteraction(AdInteraction interaction) {
      interactions.add(interaction);
    }
  }

  @Test
  void servesScriptedRenderingsInRotationAndRecordsWhatItServed() {
    Rendering first = creative("crt_1").build();
    Rendering second = creative("crt_2").build();
    InMemoryAdDecisionService service =
        new InMemoryAdDecisionService().onPlacement(BANNER, first, second);
    SessionHandle handle = service.registerSession(snapshot("browsing"));

    assertEquals(Optional.of(first), service.decide(handle, BANNER, FULL, TIMEOUT));
    assertEquals(Optional.of(second), service.decide(handle, BANNER, FULL, TIMEOUT));
    assertEquals(Optional.of(first), service.decide(handle, BANNER, FULL, TIMEOUT));
    assertEquals(List.of(first, second, first), service.served(handle));
  }

  @Test
  void unscriptedPlacementIsAMiss() {
    InMemoryAdDecisionService service = new InMemoryAdDecisionService();
    SessionHandle handle = service.registerSession(snapshot("browsing"));

    assertEquals(Optional.empty(), service.decide(handle, BANNER, FULL, TIMEOUT));
    assertEquals(
        Optional.empty(), service.decide(handle, Placement.of("elsewhere"), FULL, TIMEOUT));
  }

  @Test
  void rulesSeeTheLatestSnapshot() {
    Rendering toothpaste = creative("crt_tp").build();
    InMemoryAdDecisionService service =
        new InMemoryAdDecisionService()
            .onPlacement(
                BANNER,
                snapshot ->
                    snapshot.getLines().stream().anyMatch(l -> l.getSku().equals("SENSO-1"))
                        ? Optional.of(toothpaste)
                        : Optional.empty());
    SessionHandle handle = service.registerSession(snapshot("browsing"));

    assertEquals(Optional.empty(), service.decide(handle, BANNER, FULL, TIMEOUT));

    service.updateSession(handle, snapshot("browsing", "SENSO-1"));

    assertEquals(Optional.of(toothpaste), service.decide(handle, BANNER, FULL, TIMEOUT));
    assertEquals(2, service.snapshots(handle).size());
    assertEquals("SENSO-1", service.snapshots(handle).get(1).getLines().get(0).getSku());
  }

  @Test
  void refusesARenderingWhoseFormatIsNotSupported() {
    Rendering video =
        creative("crt_v")
            .media(MediaSpec.video(URI.create("https://cdn.example/v.mp4"), Duration.ofSeconds(5)))
            .build();
    InMemoryAdDecisionService service = new InMemoryAdDecisionService().onPlacement(BANNER, video);
    SessionHandle handle = service.registerSession(snapshot("browsing"));
    Capabilities imageOnly =
        FULL.toBuilder().formats(java.util.Set.of(MediaSpec.MediaType.IMAGE)).build();

    assertEquals(Optional.empty(), service.decide(handle, BANNER, imageOnly, TIMEOUT));
    assertEquals(Optional.of(video), service.decide(handle, BANNER, FULL, TIMEOUT));
  }

  @Test
  void refusesARenderingWhoseCtaActionIsNotSupported() {
    Rendering withText = creative("crt_t").cta(APPLY).secondary(TEXT_ME).build();
    InMemoryAdDecisionService service =
        new InMemoryAdDecisionService().onPlacement(BANNER, withText);
    SessionHandle handle = service.registerSession(snapshot("browsing"));
    Capabilities noPhone = FULL.toBuilder().actions(java.util.Set.of(Action.APPLY_OFFER)).build();

    assertEquals(Optional.empty(), service.decide(handle, BANNER, noPhone, TIMEOUT));
    assertTrue(service.served(handle).isEmpty(), "a refused rendering is not recorded as served");
  }

  @Test
  void validatesItsOwnTokensAndIssuesRegisteredOffers() {
    Rendering rendering = creative("crt_1").cta(APPLY).secondary(TEXT_ME).build();
    Offer offer = offer("crt_1");
    InMemoryAdDecisionService service =
        new InMemoryAdDecisionService().onPlacement(BANNER, rendering).offerFor("act_apply", offer);
    SessionHandle handle = service.registerSession(snapshot("browsing"));
    service.decide(handle, BANNER, FULL, TIMEOUT);

    ActionOutcome applied = service.validateAction(handle, APPLY, "crt_1");
    assertTrue(applied.isAccepted());
    assertEquals(Optional.of(offer), applied.getOffer());

    ActionOutcome texted = service.validateAction(handle, TEXT_ME, "crt_1");
    assertTrue(texted.isAccepted());
    assertEquals(Optional.empty(), texted.getOffer());
  }

  @Test
  void rejectsForeignUnservedAndMismatchedTokens() {
    Rendering rendering = creative("crt_1").cta(APPLY).build();
    InMemoryAdDecisionService service =
        new InMemoryAdDecisionService()
            .onPlacement(BANNER, rendering)
            .offerFor("act_apply", offer("crt_1"));
    SessionHandle served = service.registerSession(snapshot("browsing"));
    SessionHandle other = service.registerSession(snapshot("browsing"));
    service.decide(served, BANNER, FULL, TIMEOUT);

    ActionOutcome forged =
        service.validateAction(served, Cta.of("x", Action.APPLY_OFFER, "act_forged"), "crt_1");
    assertFalse(forged.isAccepted());
    assertNotNull(forged.getReason());

    ActionOutcome otherSession = service.validateAction(other, APPLY, "crt_1");
    assertFalse(otherSession.isAccepted(), "a token served to one session is foreign to another");

    ActionOutcome wrongCreative = service.validateAction(served, APPLY, "crt_other");
    assertFalse(wrongCreative.isAccepted());
  }

  @Test
  void acceptedTokensAreSingleUseUntilServedAgain() {
    Rendering rendering = creative("crt_1").cta(APPLY).secondary(TEXT_ME).build();
    InMemoryAdDecisionService service =
        new InMemoryAdDecisionService()
            .onPlacement(BANNER, rendering)
            .offerFor("act_apply", offer("crt_1"));
    SessionHandle handle = service.registerSession(snapshot("browsing"));
    service.decide(handle, BANNER, FULL, TIMEOUT);

    assertTrue(service.validateAction(handle, APPLY, "crt_1").isAccepted());
    ActionOutcome doubleTap = service.validateAction(handle, APPLY, "crt_1");
    assertFalse(doubleTap.isAccepted(), "a double tap must not apply the offer twice");
    assertTrue(doubleTap.getReason().contains("already used"));

    assertTrue(service.validateAction(handle, TEXT_ME, "crt_1").isAccepted());
    assertFalse(service.validateAction(handle, TEXT_ME, "crt_1").isAccepted());

    service.decide(handle, BANNER, FULL, TIMEOUT);
    assertTrue(service.validateAction(handle, APPLY, "crt_1").isAccepted());
  }

  @Test
  void applyOfferWithoutARegisteredOfferIsRejected() {
    Rendering rendering = creative("crt_1").cta(APPLY).build();
    InMemoryAdDecisionService service =
        new InMemoryAdDecisionService().onPlacement(BANNER, rendering);
    SessionHandle handle = service.registerSession(snapshot("browsing"));
    service.decide(handle, BANNER, FULL, TIMEOUT);

    ActionOutcome outcome = service.validateAction(handle, APPLY, "crt_1");

    assertFalse(outcome.isAccepted());
    assertTrue(outcome.getReason().contains("offer"));
  }

  @Test
  void recordsReportsPerSessionAndOverall() {
    InMemoryAdDecisionService service = new InMemoryAdDecisionService();
    SessionHandle a = service.registerSession(snapshot("browsing"));
    SessionHandle b = service.registerSession(snapshot("browsing"));
    AdInteraction shown = AdInteraction.of("crt_1", BANNER, AdInteraction.Kind.SHOWN);
    AdInteraction tapped =
        AdInteraction.builder()
            .creativeId("crt_1")
            .placement(BANNER)
            .kind(AdInteraction.Kind.TAPPED)
            .action(Action.APPLY_OFFER)
            .build();

    service.report(a, shown);
    service.report(b, tapped);

    assertEquals(List.of(shown), service.reports(a));
    assertEquals(List.of(tapped), service.reports(b));
    assertEquals(List.of(shown, tapped), service.reports());
  }

  @Test
  void latencyBeyondTheTimeoutIsAMissAnsweredWithinTheTimeout() {
    InMemoryAdDecisionService service =
        new InMemoryAdDecisionService()
            .onPlacement(BANNER, creative("crt_1").build())
            .latency(Duration.ofSeconds(5));
    SessionHandle handle = service.registerSession(snapshot("browsing"));

    long start = System.nanoTime();
    Optional<Rendering> decision = service.decide(handle, BANNER, FULL, Duration.ofMillis(50));
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertEquals(Optional.empty(), decision);
    assertTrue(elapsedMs < 2_000, "decide took " + elapsedMs + "ms");
  }

  @Test
  void latencyWithinTheTimeoutStillServes() {
    Rendering rendering = creative("crt_1").build();
    InMemoryAdDecisionService service =
        new InMemoryAdDecisionService()
            .onPlacement(BANNER, rendering)
            .latency(Duration.ofMillis(20));
    SessionHandle handle = service.registerSession(snapshot("browsing"));

    assertEquals(Optional.of(rendering), service.decide(handle, BANNER, FULL, TIMEOUT));
  }

  @Test
  void injectedFailureIsAMissForDecideARejectionForValidateAndAThrowForReport() {
    Rendering rendering = creative("crt_1").cta(APPLY).build();
    InMemoryAdDecisionService service =
        new InMemoryAdDecisionService()
            .onPlacement(BANNER, rendering)
            .offerFor("act_apply", offer("crt_1"));
    SessionHandle handle = service.registerSession(snapshot("browsing"));

    service.failNext(new IllegalStateException("platform down"));
    assertEquals(Optional.empty(), service.decide(handle, BANNER, FULL, TIMEOUT));
    assertEquals(Optional.of(rendering), service.decide(handle, BANNER, FULL, TIMEOUT));

    service.failNext(new IllegalStateException("platform down"));
    assertFalse(service.validateAction(handle, APPLY, "crt_1").isAccepted());
    assertTrue(service.validateAction(handle, APPLY, "crt_1").isAccepted());

    service.failNext(new IllegalStateException("platform down"));
    assertThrows(
        IllegalStateException.class,
        () -> service.report(handle, AdInteraction.of("crt_1", BANNER, AdInteraction.Kind.SHOWN)));
    assertEquals(2, service.swallowedFailures().size());
  }

  @Test
  void deliversInjectedEventsToSubscribersAndStopsAfterClose() {
    InMemoryAdDecisionService service = new InMemoryAdDecisionService();
    SessionHandle handle = service.registerSession(snapshot("browsing"));
    SessionHandle other = service.registerSession(snapshot("browsing"));
    RecordingListener listener = new RecordingListener();
    RecordingListener otherListener = new RecordingListener();
    AdEventSubscription subscription = service.subscribe(handle, listener);
    service.subscribe(other, otherListener);
    Offer offer = offer("crt_hosted");
    AdInteraction interaction = AdInteraction.of("crt_hosted", BANNER, AdInteraction.Kind.VIEWED);

    service.emit(handle, offer);
    service.emit(handle, interaction);

    assertEquals(List.of(offer), listener.offers);
    assertEquals(List.of(interaction), listener.interactions);
    assertTrue(otherListener.offers.isEmpty(), "events are scoped to the session");

    subscription.close();
    subscription.close();
    service.emit(handle, offer);

    assertEquals(1, listener.offers.size());
  }

  @Test
  void closingTheSessionClosesSubscriptionsAndRetiresTheHandle() {
    InMemoryAdDecisionService service =
        new InMemoryAdDecisionService().onPlacement(BANNER, creative("crt_1").build());
    SessionHandle handle = service.registerSession(snapshot("browsing"));
    RecordingListener listener = new RecordingListener();
    service.subscribe(handle, listener);

    service.closeSession(handle);
    service.closeSession(handle);
    service.emit(handle, offer("crt_1"));

    assertTrue(listener.offers.isEmpty());
    assertFalse(service.isOpen(handle));
    assertThrows(IllegalStateException.class, () -> service.decide(handle, BANNER, FULL, TIMEOUT));
    assertThrows(
        IllegalStateException.class,
        () -> service.decide(SessionHandle.of("never-issued"), BANNER, FULL, TIMEOUT));
  }

  @Test
  void aThrowingListenerDoesNotStopDeliveryToOthers() {
    InMemoryAdDecisionService service = new InMemoryAdDecisionService();
    SessionHandle handle = service.registerSession(snapshot("browsing"));
    RecordingListener healthy = new RecordingListener();
    service.subscribe(
        handle,
        new AdEventListener() {
          @Override
          public void onOffer(Offer offer) {
            throw new IllegalStateException("listener exploded");
          }

          @Override
          public void onInteraction(AdInteraction interaction) {}
        });
    service.subscribe(handle, healthy);

    assertDoesNotThrow(() -> service.emit(handle, offer("crt_1")));
    assertEquals(1, healthy.offers.size());
  }
}
