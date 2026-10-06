/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.host.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one {@link ObjectMapper} the host uses, plus the small reading helpers every parser needs.
 * Request bodies are read as trees and picked apart by hand so the wire shapes are explicit in
 * code rather than implied by bean conventions; money crosses as decimal strings and timestamps as
 * ISO-8601 instants.
 */
public final class Json {

  public static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private Json() {}

  public static ObjectNode object() {
    return MAPPER.createObjectNode();
  }

  public static ArrayNode array() {
    return MAPPER.createArrayNode();
  }

  public static String write(JsonNode node) {
    try {
      return MAPPER.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("could not serialize response", e);
    }
  }

  /** Parses a request body; an empty body is an empty object, anything unparsable is a 400. */
  public static ObjectNode body(String raw) {
    if (raw == null || raw.isBlank()) {
      return object();
    }
    JsonNode node;
    try {
      node = MAPPER.readTree(raw);
    } catch (JsonProcessingException e) {
      throw HostError.badRequest("malformed JSON: " + e.getOriginalMessage());
    }
    if (!node.isObject()) {
      throw HostError.badRequest("the request body must be a JSON object");
    }
    return (ObjectNode) node;
  }

  public static boolean has(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    return value != null && !value.isNull();
  }

  public static String text(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isValueNode()) {
      throw HostError.badRequest(field + " must be a string");
    }
    return value.asText();
  }

  public static String requireText(JsonNode node, String field) {
    String value = text(node, field);
    if (value == null || value.isEmpty()) {
      throw HostError.badRequest(field + " is required");
    }
    return value;
  }

  public static Boolean bool(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isBoolean()) {
      throw HostError.badRequest(field + " must be a boolean");
    }
    return value.asBoolean();
  }

  public static boolean bool(JsonNode node, String field, boolean fallback) {
    Boolean value = bool(node, field);
    return value == null ? fallback : value;
  }

  public static Integer integer(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.canConvertToInt()) {
      throw HostError.badRequest(field + " must be an integer");
    }
    return value.asInt();
  }

  public static BigDecimal decimal(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    return decimalOf(value, field);
  }

  public static BigDecimal decimalOf(JsonNode value, String field) {
    if (value.isNumber()) {
      return value.decimalValue();
    }
    if (value.isTextual()) {
      try {
        return new BigDecimal(value.asText().trim());
      } catch (NumberFormatException e) {
        throw HostError.badRequest(field + " is not a decimal amount: " + value.asText());
      }
    }
    throw HostError.badRequest(field + " must be a decimal amount");
  }

  public static BigDecimal requireDecimal(JsonNode node, String field) {
    BigDecimal value = decimal(node, field);
    if (value == null) {
      throw HostError.badRequest(field + " is required");
    }
    return value;
  }

  public static Instant instant(JsonNode node, String field) {
    String text = text(node, field);
    if (text == null) {
      return null;
    }
    try {
      return Instant.parse(text);
    } catch (RuntimeException e) {
      throw HostError.badRequest(field + " is not an ISO-8601 instant: " + text);
    }
  }

  /** A duration given in milliseconds, as {@code <field>Ms}. */
  public static Duration millis(JsonNode node, String field) {
    Integer value = integer(node, field);
    return value == null ? null : Duration.ofMillis(value);
  }

  public static <E extends Enum<E>> E enumValue(JsonNode node, String field, Class<E> type) {
    String text = text(node, field);
    if (text == null) {
      return null;
    }
    return enumValue(text, field, type);
  }

  public static <E extends Enum<E>> E enumValue(String text, String field, Class<E> type) {
    for (E constant : type.getEnumConstants()) {
      if (constant.name().equalsIgnoreCase(text)) {
        return constant;
      }
    }
    throw HostError.badRequest(
        field + " must be one of " + List.of(type.getEnumConstants()) + ", not '" + text + "'");
  }

  public static List<String> strings(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isArray()) {
      throw HostError.badRequest(field + " must be an array of strings");
    }
    List<String> result = new ArrayList<>();
    for (JsonNode element : value) {
      if (!element.isTextual()) {
        throw HostError.badRequest(field + " must be an array of strings");
      }
      result.add(element.asText());
    }
    return result;
  }

  public static ArrayNode array(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isArray()) {
      throw HostError.badRequest(field + " must be an array");
    }
    return (ArrayNode) value;
  }

  public static ObjectNode objectField(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isObject()) {
      throw HostError.badRequest(field + " must be an object");
    }
    return (ObjectNode) value;
  }

  /** A string map; {@code null} values are kept so a PATCH can express removal. */
  public static Map<String, String> stringMap(JsonNode node, String field) {
    ObjectNode value = objectField(node, field);
    if (value == null) {
      return null;
    }
    Map<String, String> result = new LinkedHashMap<>();
    value.fields()
        .forEachRemaining(
            entry -> {
              JsonNode v = entry.getValue();
              if (v.isNull()) {
                result.put(entry.getKey(), null);
              } else if (v.isValueNode()) {
                result.put(entry.getKey(), v.asText());
              } else {
                throw HostError.badRequest(field + "." + entry.getKey() + " must be a string");
              }
            });
    return result;
  }

  public static JsonNode nullNode() {
    return NullNode.getInstance();
  }

  public static String money(BigDecimal amount) {
    return amount == null ? null : amount.toPlainString();
  }

  public static void putMoney(ObjectNode node, String field, BigDecimal amount) {
    if (amount != null) {
      node.put(field, amount.toPlainString());
    }
  }

  public static void putInstant(ObjectNode node, String field, Instant instant) {
    if (instant != null) {
      node.put(field, instant.toString());
    }
  }

  public static void putText(ObjectNode node, String field, String value) {
    if (value != null) {
      node.put(field, value);
    }
  }
}
