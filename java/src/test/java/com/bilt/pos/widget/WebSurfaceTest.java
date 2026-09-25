package com.bilt.pos.widget;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebSurfaceTest {

  private static final String PAGE = "https://media.bilt.com/pos/renderer.html";

  /** Captures the calls a real subclass would forward to its browser control. */
  private static final class FakeWebSurface extends WebSurface {
    final List<String> loaded = new ArrayList<>();
    final List<String> evaluated = new ArrayList<>();
    final List<String> errors = new ArrayList<>();

    FakeWebSurface() {
      super(PAGE);
    }

    @Override
    protected void loadUrl(String url) {
      loaded.add(url);
    }

    @Override
    protected void evaluateJavascript(String script) {
      evaluated.add(script);
    }

    @Override
    protected void onBridgeError(String reason, String rawJson) {
      errors.add(reason);
    }

    void post(String json) {
      onBridgeMessage(json);
    }
  }

  private static final class RecordingSink implements ActionSink {
    final List<Cta> performed = new ArrayList<>();
    final List<Rendering> viewed = new ArrayList<>();
    final List<Rendering> dismissed = new ArrayList<>();
    final List<Rendering> completed = new ArrayList<>();

    @Override
    public void perform(Cta cta) {
      performed.add(cta);
    }

    @Override
    public void viewed(Rendering rendering) {
      viewed.add(rendering);
    }

    @Override
    public void dismissed(Rendering rendering) {
      dismissed.add(rendering);
    }

    @Override
    public void completed(Rendering rendering) {
      completed.add(rendering);
    }

    boolean isEmpty() {
      return performed.isEmpty() && viewed.isEmpty() && dismissed.isEmpty() && completed.isEmpty();
    }
  }

  private static final Cta APPLY = Cta.of("Apply offer", Action.APPLY_OFFER, "act_apply");
  private static final Cta TEXT_ME = Cta.of("Text me this", Action.SEND_TO_PHONE, "act_text");

  private static Rendering sensodyne() {
    return Rendering.builder()
        .creativeId("crt_91ad")
        .placement("lane-banner")
        .media(
            MediaSpec.builder()
                .type(MediaSpec.MediaType.VIDEO)
                .url(URI.create("https://cdn.example/signed"))
                .duration(Duration.ofSeconds(15))
                .poster(URI.create("https://cdn.example/poster.jpg"))
                .build())
        .headline("Save $2 on Sensodyne")
        .body("Applied at the register")
        .cta(APPLY)
        .secondary(TEXT_ME)
        .ttl(Duration.ofSeconds(30))
        .addTracking("impression", URI.create("https://t.example/i"))
        .addTracking("viewability", URI.create("https://t.example/v"))
        .build();
  }

  private static JsonNode payloadOf(String script) throws Exception {
    String prefix = "window.BiltMedia.receive(";
    assertTrue(script.startsWith(prefix), script);
    assertTrue(script.endsWith(");"), script);
    return new ObjectMapper().readTree(script.substring(prefix.length(), script.length() - 2));
  }

  private static FakeWebSurface readySurface(Rendering rendering, ActionSink sink) {
    FakeWebSurface surface = new FakeWebSurface();
    surface.show(rendering, sink);
    surface.post("{\"type\":\"ready\"}");
    surface.evaluated.clear();
    return surface;
  }

  @Test
  void firstShowLoadsThePageAndHoldsTheRenderingUntilReady() throws Exception {
    FakeWebSurface surface = new FakeWebSurface();
    Rendering rendering = sensodyne();

    surface.show(rendering, new RecordingSink());

    assertEquals(List.of(PAGE), surface.loaded);
    assertTrue(surface.evaluated.isEmpty(), "nothing may be evaluated before the page is ready");

    surface.post("{\"type\":\"ready\"}");

    assertEquals(1, surface.evaluated.size());
    JsonNode message = payloadOf(surface.evaluated.get(0));
    assertEquals("rendering", message.get("type").asText());
    JsonNode json = message.get("rendering");
    assertEquals("crt_91ad", json.get("creativeId").asText());
    assertEquals("lane-banner", json.get("placement").asText());
    assertEquals("video", json.path("media").path("type").asText());
    assertEquals("https://cdn.example/signed", json.path("media").path("url").asText());
    assertEquals(15000, json.path("media").path("durationMs").asLong());
    assertEquals("https://cdn.example/poster.jpg", json.path("media").path("poster").asText());
    assertEquals("Save $2 on Sensodyne", json.get("headline").asText());
    assertEquals("Applied at the register", json.get("body").asText());
    assertEquals("APPLY_OFFER", json.path("cta").path("action").asText());
    assertEquals("act_apply", json.path("cta").path("token").asText());
    assertEquals("Apply offer", json.path("cta").path("label").asText());
    assertEquals("SEND_TO_PHONE", json.path("secondary").path("action").asText());
    assertEquals(30000, json.get("ttlMs").asLong());
    assertEquals("https://t.example/i", json.path("tracking").path("impression").asText());
    assertEquals("https://t.example/v", json.path("tracking").path("viewability").asText());
  }

  @Test
  void onlyTheLatestRenderingIsPushedWhenThePageBecomesReady() throws Exception {
    FakeWebSurface surface = new FakeWebSurface();
    surface.show(sensodyne(), new RecordingSink());
    surface.show(sensodyne().toBuilder().creativeId("crt_later").build(), new RecordingSink());

    surface.post("{\"type\":\"ready\"}");

    assertEquals(1, surface.loaded.size(), "the page is loaded once");
    assertEquals(1, surface.evaluated.size());
    assertEquals(
        "crt_later",
        payloadOf(surface.evaluated.get(0)).path("rendering").path("creativeId").asText());
  }

  @Test
  void clearBeforeReadyLeavesThePageEmpty() {
    FakeWebSurface surface = new FakeWebSurface();
    surface.show(sensodyne(), new RecordingSink());
    surface.clear();

    surface.post("{\"type\":\"ready\"}");

    assertTrue(surface.evaluated.isEmpty());
  }

  @Test
  void subsequentShowsAndClearsEvaluateDirectly() throws Exception {
    FakeWebSurface surface = readySurface(sensodyne(), new RecordingSink());

    surface.show(sensodyne().toBuilder().creativeId("crt_2").build(), new RecordingSink());
    surface.clear();
    surface.clear();

    assertEquals(1, surface.loaded.size());
    assertEquals(2, surface.evaluated.size(), "second clear is a no-op");
    assertEquals("rendering", payloadOf(surface.evaluated.get(0)).get("type").asText());
    assertEquals("clear", payloadOf(surface.evaluated.get(1)).get("type").asText());
  }

  @Test
  void optionalFieldsAreOmittedFromTheWire() throws Exception {
    Rendering minimal =
        Rendering.builder()
            .creativeId("crt_min")
            .placement("lane-banner")
            .media(MediaSpec.image(URI.create("https://cdn.example/a.png")))
            .headline("Hi")
            .ttl(Duration.ofSeconds(5))
            .build();
    FakeWebSurface surface = readySurface(sensodyne(), new RecordingSink());

    surface.show(minimal, new RecordingSink());

    JsonNode json = payloadOf(surface.evaluated.get(0)).get("rendering");
    assertFalse(json.has("body"));
    assertFalse(json.has("cta"));
    assertFalse(json.has("secondary"));
    assertFalse(json.path("media").has("durationMs"));
    assertFalse(json.path("media").has("poster"));
    assertTrue(json.get("tracking").isObject());
    assertEquals(0, json.get("tracking").size());
  }

  @Test
  void aPageReloadReceivesTheCurrentRenderingAgain() {
    FakeWebSurface surface = readySurface(sensodyne(), new RecordingSink());

    surface.post("{\"type\":\"ready\"}");

    assertEquals(1, surface.evaluated.size());
    assertTrue(surface.evaluated.get(0).contains("\"type\":\"rendering\""));
  }

  @Test
  void aPreloadedPageNeedsNoLoadUrl() {
    FakeWebSurface surface = new FakeWebSurface();
    surface.post("{\"type\":\"ready\"}");

    surface.show(sensodyne(), new RecordingSink());

    assertTrue(surface.loaded.isEmpty());
    assertEquals(1, surface.evaluated.size());
  }

  @Test
  void wellFormedActionReachesTheSinkWithTheRenderingsOwnCta() {
    RecordingSink sink = new RecordingSink();
    FakeWebSurface surface = readySurface(sensodyne(), sink);

    surface.post(
        "{\"type\":\"action\",\"creativeId\":\"crt_91ad\",\"action\":\"SEND_TO_PHONE\",\"token\":\"act_text\"}");

    assertEquals(List.of(TEXT_ME), sink.performed);
    assertTrue(surface.errors.isEmpty());
  }

  @Test
  void lifecycleEventsReachTheSink() {
    RecordingSink sink = new RecordingSink();
    Rendering rendering = sensodyne();
    FakeWebSurface surface = readySurface(rendering, sink);

    surface.post("{\"type\":\"viewed\",\"creativeId\":\"crt_91ad\"}");
    surface.post("{\"type\":\"completed\",\"creativeId\":\"crt_91ad\"}");
    surface.post("{\"type\":\"dismissed\",\"creativeId\":\"crt_91ad\"}");

    assertEquals(List.of(rendering), sink.viewed);
    assertEquals(List.of(rendering), sink.completed);
    assertEquals(List.of(rendering), sink.dismissed);
    assertTrue(surface.errors.isEmpty());
  }

  @Test
  void foreignTokenIsRejected() {
    RecordingSink sink = new RecordingSink();
    FakeWebSurface surface = readySurface(sensodyne(), sink);

    surface.post(
        "{\"type\":\"action\",\"creativeId\":\"crt_91ad\",\"action\":\"APPLY_OFFER\",\"token\":\"act_forged\"}");

    assertTrue(sink.isEmpty());
    assertEquals(1, surface.errors.size());
    assertTrue(surface.errors.get(0).contains("token"), surface.errors.get(0));
  }

  @Test
  void actionMustMatchTheTokensDeclaredAction() {
    RecordingSink sink = new RecordingSink();
    FakeWebSurface surface = readySurface(sensodyne(), sink);

    surface.post(
        "{\"type\":\"action\",\"creativeId\":\"crt_91ad\",\"action\":\"APPLY_OFFER\",\"token\":\"act_text\"}");

    assertTrue(sink.isEmpty());
    assertEquals(1, surface.errors.size());
  }

  @Test
  void messagesForACreativeNotOnDisplayAreRejected() {
    RecordingSink sink = new RecordingSink();
    FakeWebSurface surface = readySurface(sensodyne(), sink);

    surface.post(
        "{\"type\":\"action\",\"creativeId\":\"crt_old\",\"action\":\"APPLY_OFFER\",\"token\":\"act_apply\"}");
    surface.post("{\"type\":\"viewed\",\"creativeId\":\"crt_old\"}");

    assertTrue(sink.isEmpty());
    assertEquals(2, surface.errors.size());
  }

  @Test
  void staleActionAfterClearIsRejected() {
    RecordingSink sink = new RecordingSink();
    FakeWebSurface surface = readySurface(sensodyne(), sink);
    surface.clear();

    surface.post(
        "{\"type\":\"action\",\"creativeId\":\"crt_91ad\",\"action\":\"APPLY_OFFER\",\"token\":\"act_apply\"}");

    assertTrue(sink.isEmpty());
    assertEquals(1, surface.errors.size());
  }

  @Test
  void unknownTypeMissingTypeAndMalformedJsonAreRejectedWithoutThrowing() {
    RecordingSink sink = new RecordingSink();
    FakeWebSurface surface = readySurface(sensodyne(), sink);

    assertDoesNotThrow(
        () -> {
          surface.post("{\"type\":\"navigate\",\"url\":\"https://evil.example\"}");
          surface.post("{\"creativeId\":\"crt_91ad\"}");
          surface.post("{not json");
          surface.post("[1,2,3]");
          surface.post(null);
          surface.post("{\"type\":\"action\",\"creativeId\":\"crt_91ad\"}");
        });

    assertTrue(sink.isEmpty());
    assertEquals(6, surface.errors.size());
    assertTrue(surface.errors.get(0).contains("unknown message type"), surface.errors.get(0));
  }

  @Test
  void aThrowingSinkIsReportedNotPropagated() {
    ActionSink hostile =
        new ActionSink() {
          @Override
          public void perform(Cta cta) {
            throw new IllegalStateException("sink exploded");
          }

          @Override
          public void viewed(Rendering rendering) {}

          @Override
          public void dismissed(Rendering rendering) {}

          @Override
          public void completed(Rendering rendering) {}
        };
    FakeWebSurface surface = readySurface(sensodyne(), hostile);

    assertDoesNotThrow(
        () ->
            surface.post(
                "{\"type\":\"action\",\"creativeId\":\"crt_91ad\",\"action\":\"APPLY_OFFER\",\"token\":\"act_apply\"}"));

    assertEquals(1, surface.errors.size());
    assertTrue(surface.errors.get(0).contains("sink exploded"));
  }

  @Test
  void defaultBridgeErrorHookDoesNotThrow() {
    WebSurface surface =
        new WebSurface(PAGE) {
          @Override
          protected void loadUrl(String url) {}

          @Override
          protected void evaluateJavascript(String script) {}

          void post(String json) {
            onBridgeMessage(json);
          }
        };

    assertDoesNotThrow(() -> surface.onBridgeError("test", "{}"));
  }
}
