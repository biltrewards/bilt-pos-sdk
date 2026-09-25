/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.widget;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * The JSON wire shapes of the {@link WebSurface} bridge, in both directions.
 *
 * <p>Outbound messages are delivered to the page as the single argument of {@code
 * window.BiltMedia.receive(...)}; inbound messages arrive as the string a page passes to the
 * platform's JavaScript interface. Both are described by {@code bridge-messages.schema.json} in
 * this package's resources; this class is the Java side of that contract and the only place the
 * field names live.
 */
final class BridgeMessages {

  static final String TYPE_RENDERING = "rendering";
  static final String TYPE_CLEAR = "clear";
  static final String TYPE_READY = "ready";
  static final String TYPE_ACTION = "action";
  static final String TYPE_VIEWED = "viewed";
  static final String TYPE_DISMISSED = "dismissed";
  static final String TYPE_COMPLETED = "completed";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private BridgeMessages() {}

  /**
   * Null-checks {@code value} and rejects an empty one, for the identifiers the bridge schema
   * requires to be non-empty ({@code creativeId}, {@code placement}, CTA {@code token}).
   */
  static String requireNonEmpty(String value, String name) {
    if (Objects.requireNonNull(value, name).isEmpty()) {
      throw new IllegalArgumentException(name + " must not be empty");
    }
    return value;
  }

  /** {@code window.BiltMedia.receive({"type":"rendering","rendering":{...}});} */
  static String renderingScript(Rendering rendering) {
    ObjectNode message = MAPPER.createObjectNode();
    message.put("type", TYPE_RENDERING);
    message.set("rendering", toJson(rendering));
    return receiveScript(message);
  }

  /** {@code window.BiltMedia.receive({"type":"clear"});} */
  static String clearScript() {
    ObjectNode message = MAPPER.createObjectNode();
    message.put("type", TYPE_CLEAR);
    return receiveScript(message);
  }

  static ObjectNode toJson(Rendering rendering) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("creativeId", rendering.getCreativeId());
    node.put("placement", rendering.getPlacement());
    node.set("media", toJson(rendering.getMedia()));
    node.put("headline", rendering.getHeadline());
    if (rendering.getBody() != null) {
      node.put("body", rendering.getBody());
    }
    if (rendering.getCta() != null) {
      node.set("cta", toJson(rendering.getCta()));
    }
    if (rendering.getSecondary() != null) {
      node.set("secondary", toJson(rendering.getSecondary()));
    }
    node.put("ttlMs", wireMillis(rendering.getTtl()));
    ObjectNode tracking = node.putObject("tracking");
    for (Map.Entry<String, URI> beacon : rendering.getTracking().entrySet()) {
      tracking.put(beacon.getKey(), beacon.getValue().toString());
    }
    return node;
  }

  private static ObjectNode toJson(MediaSpec media) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("type", media.getType().name().toLowerCase(java.util.Locale.ROOT));
    node.put("url", media.getUrl().toString());
    if (media.getDuration() != null) {
      node.put("durationMs", wireMillis(media.getDuration()));
    }
    if (media.getPoster() != null) {
      node.put("poster", media.getPoster().toString());
    }
    return node;
  }

  // The schema requires at least 1 ms, and the builders accept any positive duration, so round up
  // rather than let a sub-millisecond value truncate to 0.
  private static long wireMillis(Duration duration) {
    long millis = duration.toMillis();
    return duration.equals(Duration.ofMillis(millis)) ? millis : millis + 1;
  }

  private static ObjectNode toJson(Cta cta) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("label", cta.getLabel());
    node.put("action", cta.getAction().name());
    node.put("token", cta.getToken());
    return node;
  }

  private static String receiveScript(ObjectNode message) {
    try {
      return "window.BiltMedia.receive(" + MAPPER.writeValueAsString(message) + ");";
    } catch (JsonProcessingException e) {
      // Only strings, numbers and nested objects are ever written; Jackson cannot fail here.
      throw new IllegalStateException("could not serialize bridge message", e);
    }
  }

  /** Parses an inbound message into a tree, or throws {@link IllegalArgumentException}. */
  static JsonNode parse(String json) {
    if (json == null) {
      throw new IllegalArgumentException("null message");
    }
    JsonNode node;
    try {
      node = MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("malformed JSON: " + e.getOriginalMessage(), e);
    }
    if (node == null || !node.isObject()) {
      throw new IllegalArgumentException("message is not a JSON object");
    }
    return node;
  }

  /** The string field {@code name}, or {@code null} when absent or not textual. */
  static String text(JsonNode node, String name) {
    JsonNode value = node.get(name);
    return value != null && value.isTextual() ? value.asText() : null;
  }
}
