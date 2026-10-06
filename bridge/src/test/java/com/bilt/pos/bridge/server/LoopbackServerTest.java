package com.bilt.pos.bridge.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bilt.pos.bridge.BridgeStatus;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LoopbackServerTest {

  private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();
  private static final BridgeStatus STATUS =
      new BridgeStatus("1.2.3", "4.5.6", "127.0.0.1", 1, 2, 0, false);

  private final HttpClient http = HttpClient.newHttpClient();

  private static LoopbackServer server(int port, int fallback, List<String> origins) {
    return new LoopbackServer(
        LOOPBACK, port, fallback, () -> origins, new HealthHandler(() -> STATUS));
  }

  @Test
  void servesHealthOnEphemeralPort() throws Exception {
    try (LoopbackServer server = server(0, 0, List.of("*"))) {
      int port = server.start();
      assertTrue(port > 0);

      HttpResponse<String> res = get(port, "/health", null);
      assertEquals(200, res.statusCode());
      assertTrue(res.body().contains("\"kind\":\"bridge\""), res.body());
      assertTrue(res.body().contains("\"version\":\"1.2.3\""), res.body());
      assertTrue(res.body().contains("\"terminals\":2"), res.body());
      assertEquals(
          "application/json; charset=utf-8", res.headers().firstValue("Content-Type").get());

      assertEquals(404, get(port, "/nope", null).statusCode());
    }
  }

  @Test
  void fallsBackToNextFreePort() throws Exception {
    try (ServerSocket taken = new ServerSocket(0, 1, LOOPBACK)) {
      int busy = taken.getLocalPort();
      try (LoopbackServer server = server(busy, 10, List.of("*"))) {
        int chosen = server.start();
        assertTrue(chosen > busy && chosen <= busy + 10, "chosen " + chosen + " for busy " + busy);
        assertEquals(200, get(chosen, "/health", null).statusCode());
      }
    }
  }

  @Test
  void failsWhenNoFallbackPortIsFree() throws Exception {
    try (ServerSocket taken = new ServerSocket(0, 1, LOOPBACK)) {
      int busy = taken.getLocalPort();
      try (LoopbackServer server = server(busy, 0, List.of("*"))) {
        IOException e = assertThrows(IOException.class, server::start);
        assertTrue(e.getMessage().contains("no free port"), e.getMessage());
      }
    }
  }

  @Test
  void refusesNonLoopbackBind() throws Exception {
    InetAddress any = InetAddress.getByName("0.0.0.0");
    assertThrows(
        IllegalArgumentException.class,
        () -> new LoopbackServer(any, 0, 0, () -> List.of("*"), new HealthHandler(() -> STATUS)));
  }

  @Test
  void corsAllowsAnyOriginInDevMode() throws Exception {
    try (LoopbackServer server = server(0, 0, List.of("*"))) {
      int port = server.start();

      HttpResponse<String> res = get(port, "/health", "https://pos.example.com");
      assertEquals("*", res.headers().firstValue("Access-Control-Allow-Origin").get());
      assertEquals("true", res.headers().firstValue("Access-Control-Allow-Private-Network").get());

      HttpResponse<String> preflight = options(port, "https://pos.example.com");
      assertEquals(204, preflight.statusCode());
      assertTrue(
          preflight.headers().firstValue("Access-Control-Allow-Methods").get().contains("POST"));
      assertTrue(
          preflight
              .headers()
              .firstValue("Access-Control-Allow-Headers")
              .get()
              .contains("Idempotency-Key"));
    }
  }

  @Test
  void corsEchoesListedOriginsAndRejectsOthers() throws Exception {
    AtomicReference<List<String>> origins =
        new AtomicReference<>(List.of("https://pos.example.com"));
    try (LoopbackServer server =
        new LoopbackServer(LOOPBACK, 0, 0, origins::get, new HealthHandler(() -> STATUS))) {
      int port = server.start();

      HttpResponse<String> allowed = get(port, "/health", "https://POS.example.com");
      assertEquals(
          "https://pos.example.com",
          allowed.headers().firstValue("Access-Control-Allow-Origin").get());
      assertEquals("Origin", allowed.headers().firstValue("Vary").get());

      HttpResponse<String> other = get(port, "/health", "https://evil.example.com");
      assertEquals(200, other.statusCode());
      assertFalse(other.headers().firstValue("Access-Control-Allow-Origin").isPresent());
      assertEquals(403, options(port, "https://evil.example.com").statusCode());

      // the allow-list is live: a reload changes behaviour without a restart
      origins.set(List.of("https://evil.example.com"));
      assertEquals(204, options(port, "https://evil.example.com").statusCode());
    }
  }

  private HttpResponse<String> get(int port, String path, String origin) throws Exception {
    HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    if (origin != null) {
      req.header("Origin", origin);
    }
    return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> options(int port, String origin) throws Exception {
    HttpRequest req =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health"))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .header("Origin", origin)
            .header("Access-Control-Request-Method", "POST")
            .build();
    return http.send(req, HttpResponse.BodyHandlers.ofString());
  }
}
