package com.bilt.pos.widget;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RenderingTest {

  private static final MediaSpec IMAGE = MediaSpec.image(URI.create("https://cdn.example/a.png"));

  private static Rendering.Builder valid() {
    return Rendering.builder()
        .creativeId("crt_1")
        .placement("lane-banner")
        .media(IMAGE)
        .headline("Hello")
        .ttl(Duration.ofSeconds(30));
  }

  @Test
  void buildsWithOnlyTheRequiredFields() {
    Rendering rendering = valid().build();

    assertEquals("crt_1", rendering.getCreativeId());
    assertEquals("lane-banner", rendering.getPlacement());
    assertSame(IMAGE, rendering.getMedia());
    assertNull(rendering.getBody());
    assertNull(rendering.getCta());
    assertNull(rendering.getSecondary());
    assertTrue(rendering.getTracking().isEmpty());
  }

  @Test
  void requiresCreativeIdPlacementMediaHeadlineAndTtl() {
    assertThrows(NullPointerException.class, () -> valid().creativeId(null).build());
    assertThrows(NullPointerException.class, () -> valid().placement(null).build());
    assertThrows(NullPointerException.class, () -> valid().media(null).build());
    assertThrows(NullPointerException.class, () -> valid().headline(null).build());
    assertThrows(NullPointerException.class, () -> valid().ttl(null).build());
  }

  @Test
  void ttlMustBePositive() {
    assertThrows(IllegalArgumentException.class, () -> valid().ttl(Duration.ZERO).build());
    assertThrows(IllegalArgumentException.class, () -> valid().ttl(Duration.ofMillis(-1)).build());
  }

  @Test
  void ctaTokensMustBeDistinct() {
    Cta apply = Cta.of("Apply", Action.APPLY_OFFER, "act_same");
    Cta text = Cta.of("Text", Action.SEND_TO_PHONE, "act_same");

    assertThrows(IllegalArgumentException.class, () -> valid().cta(apply).secondary(text).build());
  }

  @Test
  void ctaForTokenFindsEitherCallToAction() {
    Cta apply = Cta.of("Apply", Action.APPLY_OFFER, "act_a");
    Cta text = Cta.of("Text", Action.SEND_TO_PHONE, "act_b");
    Rendering rendering = valid().cta(apply).secondary(text).build();

    assertEquals(apply, rendering.ctaForToken("act_a"));
    assertEquals(text, rendering.ctaForToken("act_b"));
    assertNull(rendering.ctaForToken("act_c"));
    assertNull(rendering.ctaForToken(null));
  }

  @Test
  void trackingIsCopiedAndUnmodifiable() {
    Map<String, URI> tracking = new LinkedHashMap<>();
    tracking.put("impression", URI.create("https://t.example/i"));
    Rendering rendering = valid().tracking(tracking).build();
    tracking.put("later", URI.create("https://t.example/l"));

    assertEquals(1, rendering.getTracking().size());
    assertThrows(
        UnsupportedOperationException.class,
        () -> rendering.getTracking().put("x", URI.create("https://t.example/x")));
  }

  @Test
  void toBuilderRoundTripsEveryField() {
    Rendering original =
        valid()
            .body("body")
            .cta(Cta.of("Apply", Action.APPLY_OFFER, "act_a"))
            .secondary(Cta.of("Text", Action.SEND_TO_PHONE, "act_b"))
            .addTracking("impression", URI.create("https://t.example/i"))
            .build();

    Rendering copy = original.toBuilder().build();

    assertEquals(original, copy);
    assertEquals(original.hashCode(), copy.hashCode());
    assertNotEquals(original, original.toBuilder().creativeId("crt_2").build());
  }

  @Test
  void mediaSpecValidatesTypeUrlAndDuration() {
    assertThrows(NullPointerException.class, () -> MediaSpec.builder().url(IMAGE.getUrl()).build());
    assertThrows(
        NullPointerException.class,
        () -> MediaSpec.builder().type(MediaSpec.MediaType.IMAGE).build());
    assertThrows(
        IllegalArgumentException.class, () -> MediaSpec.video(IMAGE.getUrl(), Duration.ZERO));

    MediaSpec video = MediaSpec.video(IMAGE.getUrl(), Duration.ofSeconds(15));
    assertEquals(MediaSpec.MediaType.VIDEO, video.getType());
    assertEquals(Duration.ofSeconds(15), video.getDuration());
    assertNull(video.getPoster());
  }

  @Test
  void ctaRequiresEveryField() {
    assertThrows(NullPointerException.class, () -> Cta.of(null, Action.DETAILS, "t"));
    assertThrows(NullPointerException.class, () -> Cta.of("l", null, "t"));
    assertThrows(NullPointerException.class, () -> Cta.of("l", Action.DETAILS, null));
  }
}
