package com.bilt.pos.bridge.server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

/**
 * Cross-origin policy for the loopback listener. A browser POS page lives on some HTTPS origin and
 * calls {@code http://127.0.0.1}, so every request is cross-origin and most carry a preflight.
 *
 * <p>Chrome additionally gates calls from a public page to loopback behind its Local Network Access
 * check, whose preflight expects {@code Access-Control-Allow-Private-Network: true}; without it the
 * page sees a network error rather than a CORS one, so it is always sent for allowed origins.
 */
final class Cors {

  private static final String ALLOWED_METHODS = "GET, POST, PUT, PATCH, DELETE, OPTIONS";
  private static final String ALLOWED_HEADERS =
      "Content-Type, Authorization, Idempotency-Key, X-Requested-With";

  private final Supplier<List<String>> allowedOrigins;

  Cors(Supplier<List<String>> allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  /**
   * Adds the response headers for the request's origin and answers preflights outright. Returns
   * {@code true} when the exchange has been fully answered and must not reach the application.
   */
  boolean apply(HttpExchange exchange) throws IOException {
    String origin = exchange.getRequestHeaders().getFirst("Origin");
    boolean preflight = "OPTIONS".equalsIgnoreCase(exchange.getRequestMethod());
    if (origin == null) {
      if (preflight) {
        exchange.sendResponseHeaders(204, -1);
        return true;
      }
      return false;
    }
    String allow = allowFor(origin);
    if (allow == null) {
      if (preflight) {
        Responses.json(exchange, 403, "{\"error\":\"origin_not_allowed\"}");
        return true;
      }
      return false;
    }
    Headers headers = exchange.getResponseHeaders();
    headers.set("Access-Control-Allow-Origin", allow);
    if (!allow.equals("*")) {
      headers.add("Vary", "Origin");
    }
    headers.set("Access-Control-Allow-Private-Network", "true");
    if (preflight) {
      headers.set("Access-Control-Allow-Methods", ALLOWED_METHODS);
      headers.set("Access-Control-Allow-Headers", ALLOWED_HEADERS);
      headers.set("Access-Control-Max-Age", "600");
      exchange.sendResponseHeaders(204, -1);
      return true;
    }
    return false;
  }

  private String allowFor(String origin) {
    List<String> allowed = allowedOrigins.get();
    if (allowed.contains("*")) {
      return "*";
    }
    return allowed.stream().filter(o -> o.equalsIgnoreCase(origin)).findFirst().orElse(null);
  }
}
