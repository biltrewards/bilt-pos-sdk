package com.bilt.pos.bridge.server;

import com.bilt.pos.bridge.BridgeStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * Serves {@code GET /health}, the unauthenticated probe the JavaScript SDK uses to detect a bridge
 * and decide whether it is new enough. Everything else is a 404 until the Session Host is embedded
 * and takes over routing.
 */
public final class HealthHandler implements HttpHandler {

  private final ObjectMapper mapper = new ObjectMapper();
  private final Supplier<BridgeStatus> status;

  public HealthHandler(Supplier<BridgeStatus> status) {
    this.status = status;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    if (!path.equals("/health")) {
      Responses.json(exchange, 404, "{\"error\":\"not_found\"}");
      return;
    }
    if (!exchange.getRequestMethod().equals("GET") && !exchange.getRequestMethod().equals("HEAD")) {
      exchange.getResponseHeaders().set("Allow", "GET, HEAD");
      Responses.json(exchange, 405, "{\"error\":\"method_not_allowed\"}");
      return;
    }
    Responses.json(exchange, 200, mapper.writeValueAsString(body(status.get())));
  }

  private ObjectNode body(BridgeStatus s) {
    ObjectNode node = mapper.createObjectNode();
    node.put("kind", "bridge");
    node.put("mode", "development");
    node.put("version", s.bridgeVersion());
    node.put("sdkVersion", s.sdkVersion());
    node.putArray("protocolVersions");
    node.put("port", s.port());
    node.put("terminals", s.terminalCount());
    node.put("sessions", s.sessionCount());
    node.put("sessionHost", s.sessionHostEmbedded());
    return node;
  }
}
