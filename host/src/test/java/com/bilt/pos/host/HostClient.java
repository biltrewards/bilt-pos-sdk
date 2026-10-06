package com.bilt.pos.host;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/** A small JSON client for the tests: every call returns status and parsed body. */
final class HostClient {

  static final class Response {
    final int status;
    final JsonNode body;
    final java.net.http.HttpHeaders headers;

    Response(int status, JsonNode body, java.net.http.HttpHeaders headers) {
      this.status = status;
      this.body = body;
      this.headers = headers;
    }

    Response expect(int expected) {
      assertEquals(expected, status, () -> "unexpected status; body: " + body);
      return this;
    }

    String text(String field) {
      return body.path(field).asText(null);
    }
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final String base;

  HostClient(int port) {
    this.base = "http://127.0.0.1:" + port;
  }

  String base() {
    return base;
  }

  Response get(String path) throws Exception {
    return send(HttpRequest.newBuilder(URI.create(base + path)).GET(), null);
  }

  Response post(String path, String json) throws Exception {
    return post(path, json, UUID.randomUUID().toString());
  }

  Response post(String path, String json, String idempotencyKey) throws Exception {
    return send(
        HttpRequest.newBuilder(URI.create(base + path))
            .POST(HttpRequest.BodyPublishers.ofString(json == null ? "" : json)),
        idempotencyKey);
  }

  Response put(String path, String json) throws Exception {
    return send(
        HttpRequest.newBuilder(URI.create(base + path))
            .PUT(HttpRequest.BodyPublishers.ofString(json)),
        UUID.randomUUID().toString());
  }

  Response patch(String path, String json) throws Exception {
    return send(
        HttpRequest.newBuilder(URI.create(base + path))
            .method("PATCH", HttpRequest.BodyPublishers.ofString(json)),
        UUID.randomUUID().toString());
  }

  Response delete(String path) throws Exception {
    return send(
        HttpRequest.newBuilder(URI.create(base + path)).DELETE(), UUID.randomUUID().toString());
  }

  /** A CORS preflight from {@code origin}, as a browser would send before a POST. */
  Response preflight(String path, String origin) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(base + path))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .header("Origin", origin)
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "content-type,idempotency-key")
            .header("Access-Control-Request-Private-Network", "true")
            .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    return new Response(response.statusCode(), MAPPER.nullNode(), response.headers());
  }

  /** Sends without any Idempotency-Key, to check the requirement. */
  Response postUnkeyed(String path, String json) throws Exception {
    return send(
        HttpRequest.newBuilder(URI.create(base + path))
            .POST(HttpRequest.BodyPublishers.ofString(json)),
        null);
  }

  private Response send(HttpRequest.Builder request, String idempotencyKey) throws Exception {
    request.header("Content-Type", "application/json").timeout(Duration.ofSeconds(30));
    if (idempotencyKey != null) {
      request.header("Idempotency-Key", idempotencyKey);
    }
    HttpResponse<String> response =
        http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    JsonNode body =
        response.body() == null || response.body().isEmpty()
            ? MAPPER.nullNode()
            : MAPPER.readTree(response.body());
    return new Response(response.statusCode(), body, response.headers());
  }

  /** Polls an operation until it reaches the status, or fails after the timeout. */
  JsonNode awaitOperation(String sessionId, String operationId, String status, Duration timeout)
      throws Exception {
    long deadline = System.nanoTime() + timeout.toNanos();
    JsonNode last = null;
    while (System.nanoTime() < deadline) {
      last = get("/v1/sessions/" + sessionId + "/operations/" + operationId).expect(200).body;
      if (status.equals(last.path("status").asText())) {
        return last;
      }
      TimeUnit.MILLISECONDS.sleep(25);
    }
    throw new AssertionError("operation never reached " + status + "; last: " + last);
  }

  /**
   * Reads the SSE stream from {@code since} until an event matches, returning every envelope seen
   * up to and including it.
   */
  List<JsonNode> sseUntil(String sessionId, long since, Predicate<JsonNode> until, Duration timeout)
      throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(base + "/v1/sessions/" + sessionId + "/events?since=" + since))
            .header("Accept", "text/event-stream")
            .timeout(timeout)
            .GET()
            .build();
    HttpResponse<InputStream> response =
        http.send(request, HttpResponse.BodyHandlers.ofInputStream());
    assertEquals(200, response.statusCode());
    List<JsonNode> events = new ArrayList<>();
    long deadline = System.nanoTime() + timeout.toNanos();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
      StringBuilder data = new StringBuilder();
      while (System.nanoTime() < deadline) {
        String line = reader.readLine();
        if (line == null) {
          break;
        }
        if (line.startsWith("data:")) {
          data.append(line.substring(5).trim());
        } else if (line.isEmpty() && data.length() > 0) {
          JsonNode event = MAPPER.readTree(data.toString());
          data.setLength(0);
          events.add(event);
          if (until.test(event)) {
            return events;
          }
        }
      }
    } catch (IOException e) {
      // the stream closed; fall through with what was read
    }
    throw new AssertionError("SSE stream ended without the expected event; saw " + events);
  }

  static String json(String singleQuoted) {
    return singleQuoted.replace('\'', '"');
  }
}
