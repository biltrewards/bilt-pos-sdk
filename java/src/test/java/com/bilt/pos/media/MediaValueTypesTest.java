package com.bilt.pos.media;

import static org.junit.jupiter.api.Assertions.*;

import com.bilt.pos.widget.Action;
import com.bilt.pos.widget.Cta;
import com.bilt.pos.widget.MediaSpec;
import com.bilt.pos.widget.Rendering;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MediaValueTypesTest {

  private static Offer.Builder basketOffer() {
    return Offer.builder()
        .id("off-1")
        .scope(Offer.Scope.BASKET)
        .amount(new BigDecimal("2.00"))
        .creativeId("crt_1");
  }

  @Test
  void placementHasValueSemantics() {
    assertEquals(Placement.of("lane-banner"), Placement.of("lane-banner"));
    assertEquals(Placement.of("lane-banner").hashCode(), Placement.of("lane-banner").hashCode());
    assertNotEquals(Placement.of("lane-banner"), Placement.of("interstitial"));
    assertThrows(NullPointerException.class, () -> Placement.of(null));
    assertThrows(IllegalArgumentException.class, () -> Placement.of(""));
  }

  @Test
  void offerRequiresExactlyOneOfAmountOrPercentage() {
    assertThrows(IllegalArgumentException.class, () -> basketOffer().amount(null).build());
    assertThrows(
        IllegalArgumentException.class,
        () -> basketOffer().percentage(new BigDecimal("10")).build());
    assertThrows(
        IllegalArgumentException.class, () -> basketOffer().amount(BigDecimal.ZERO).build());
    assertThrows(
        IllegalArgumentException.class,
        () -> basketOffer().amount(null).percentage(new BigDecimal("101")).build());

    Offer percent = basketOffer().amount(null).percentage(new BigDecimal("15")).build();
    assertNull(percent.getAmount());
    assertEquals(new BigDecimal("15"), percent.getPercentage());
  }

  @Test
  void lineItemOfferRequiresASku() {
    assertThrows(
        IllegalArgumentException.class, () -> basketOffer().scope(Offer.Scope.LINE_ITEM).build());

    Offer line =
        basketOffer().scope(Offer.Scope.LINE_ITEM).sku("SENSO-1").expiry(Instant.EPOCH).build();
    assertEquals("SENSO-1", line.getSku());
    assertEquals(Instant.EPOCH, line.getExpiry());
    assertEquals(line, line.toBuilder().build());
  }

  @Test
  void offerRequiresIdScopeAndCreative() {
    assertThrows(NullPointerException.class, () -> basketOffer().id(null).build());
    assertThrows(NullPointerException.class, () -> basketOffer().scope(null).build());
    assertThrows(NullPointerException.class, () -> basketOffer().creativeId(null).build());
  }

  @Test
  void interactionDefaultsItsTimestampAndCarriesAnOptionalAction() {
    Instant before = Instant.now();
    AdInteraction shown =
        AdInteraction.of("crt_1", Placement.of("lane-banner"), AdInteraction.Kind.SHOWN);

    assertFalse(shown.getTimestamp().isBefore(before));
    assertNull(shown.getAction());

    AdInteraction tapped =
        shown.toBuilder().kind(AdInteraction.Kind.TAPPED).action(Action.DETAILS).build();
    assertEquals(Action.DETAILS, tapped.getAction());
    assertEquals(shown.getTimestamp(), tapped.getTimestamp());
    assertThrows(
        NullPointerException.class,
        () -> AdInteraction.builder().creativeId("c").kind(AdInteraction.Kind.SHOWN).build());
  }

  @Test
  void capabilitiesDeclarePlacementsWithTheirSurfaceKind() {
    Placement banner = Placement.of("lane-banner");
    Placement interstitial = Placement.of("interstitial");
    Capabilities capabilities =
        Capabilities.builder()
            .sdkVersion("0.24.0")
            .format(MediaSpec.MediaType.IMAGE)
            .action(Action.APPLY_OFFER)
            .placement(banner, SurfaceKind.WEB)
            .placement(interstitial, SurfaceKind.NATIVE)
            .build();

    assertEquals(java.util.Set.of(banner, interstitial), capabilities.getPlacements());
    assertEquals(SurfaceKind.WEB, capabilities.getSurfaces().get(banner));
    assertEquals(capabilities, capabilities.toBuilder().build());
    assertThrows(
        UnsupportedOperationException.class,
        () -> capabilities.getFormats().add(MediaSpec.MediaType.VIDEO));
    assertThrows(NullPointerException.class, () -> Capabilities.builder().build());
  }

  @Test
  void emptyCapabilitiesBuildAndSupportNothing() {
    Capabilities none = Capabilities.builder().sdkVersion("0.24.0").build();
    Rendering image =
        Rendering.builder()
            .creativeId("crt_1")
            .placement("lane-banner")
            .media(MediaSpec.image(URI.create("https://cdn.example/a.png")))
            .headline("Hi")
            .ttl(Duration.ofSeconds(5))
            .build();

    assertTrue(none.getFormats().isEmpty());
    assertFalse(none.supports(image));
  }

  @Test
  void supportsChecksFormatAndEveryCtaAction() {
    Capabilities capabilities =
        Capabilities.builder()
            .sdkVersion("0.24.0")
            .format(MediaSpec.MediaType.IMAGE)
            .action(Action.APPLY_OFFER)
            .build();
    Rendering.Builder image =
        Rendering.builder()
            .creativeId("crt_1")
            .placement("lane-banner")
            .media(MediaSpec.image(URI.create("https://cdn.example/a.png")))
            .headline("Hi")
            .ttl(Duration.ofSeconds(5));

    assertTrue(capabilities.supports(image.build()));
    assertTrue(capabilities.supports(image.cta(Cta.of("Apply", Action.APPLY_OFFER, "a")).build()));
    assertFalse(
        capabilities.supports(image.secondary(Cta.of("Text", Action.SEND_TO_PHONE, "b")).build()));
    assertFalse(
        capabilities.supports(
            image
                .secondary(null)
                .media(
                    MediaSpec.video(URI.create("https://cdn.example/v.mp4"), Duration.ofSeconds(1)))
                .build()));
  }
}
