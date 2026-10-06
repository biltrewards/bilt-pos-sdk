package com.bilt.pos.host;

import static com.bilt.pos.host.HostClient.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** The same event stream over WebSocket: replay from {@code since}, then live events. */
class WebSocketSmokeTest {

  @Test
  void eventsArriveOverWebSocketWithReplay() throws Exception {
    try (SessionHost host = SessionHost.builder().port(0).build()) {
      host.start();
      HostClient client = new HostClient(host.port());
      String id =
          client
              .post("/v1/sessions", json("{'kind':'local','saleId':'LANE-1','currency':'USD'}"))
              .expect(201)
              .text("id");
      client
          .post(
              "/v1/sessions/" + id + "/basket/items",
              json("{'sku':'SKU-1','description':'Item','unitPrice':'1.00'}"))
          .expect(200);

      LinkedBlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
      ObjectMapper mapper = new ObjectMapper();
      WebSocket socket =
          HttpClient.newHttpClient()
              .newWebSocketBuilder()
              .buildAsync(
                  URI.create(
                      "ws://127.0.0.1:" + host.port() + "/v1/sessions/" + id + "/events?since=1"),
                  new WebSocket.Listener() {
                    private final StringBuilder partial = new StringBuilder();

                    @Override
                    public CompletionStage<?> onText(
                        WebSocket ws, CharSequence data, boolean last) {
                      partial.append(data);
                      if (last) {
                        try {
                          received.add(mapper.readTree(partial.toString()));
                        } catch (Exception e) {
                          throw new IllegalStateException(e);
                        }
                        partial.setLength(0);
                      }
                      ws.request(1);
                      return null;
                    }
                  })
              .get(5, TimeUnit.SECONDS);

      JsonNode replayed = next(received);
      assertEquals("basket.changed", replayed.path("type").asText());
      assertEquals(2, replayed.path("seq").asLong());

      client.patch("/v1/sessions/" + id + "/context", json("{'phase':'TENDERING'}")).expect(200);
      JsonNode live = next(received);
      assertEquals("context.changed", live.path("type").asText());
      assertEquals("TENDERING", live.path("payload").path("phase").asText());

      client.delete("/v1/sessions/" + id).expect(202);
      JsonNode completed = next(received);
      assertEquals("operation.completed", completed.path("type").asText());
      assertEquals("end", completed.path("payload").path("type").asText());
      JsonNode ended = next(received);
      assertEquals("session.ended", ended.path("type").asText());
      assertTrue(socket.isInputClosed() || waitClosed(socket));
    }
  }

  /** The next frame, checked against the spec's {@code Event} schema like the SSE ones are. */
  private static JsonNode next(LinkedBlockingQueue<JsonNode> received) throws InterruptedException {
    JsonNode event = received.poll(5, TimeUnit.SECONDS);
    SpecValidator.shared().event(event);
    return event;
  }

  private static boolean waitClosed(WebSocket socket) throws InterruptedException {
    for (int i = 0; i < 50 && !socket.isInputClosed(); i++) {
      TimeUnit.MILLISECONDS.sleep(100);
    }
    return socket.isInputClosed();
  }

  @Test
  void theUpgradeIsAuthorizedAgainstTheRealPathAndPeerAddress() throws Exception {
    java.util.List<String> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
    HostAuth recording =
        request -> {
          if (request.path().endsWith("/events")) {
            seen.add(request.path() + " from " + request.remoteAddress());
          }
          return true;
        };
    try (SessionHost host = SessionHost.builder().port(0).auth(recording).build()) {
      host.start();
      HostClient client = new HostClient(host.port());
      String id =
          client
              .post("/v1/sessions", json("{'kind':'local','saleId':'LANE-1','currency':'USD'}"))
              .expect(201)
              .text("id");
      HttpClient.newHttpClient()
          .newWebSocketBuilder()
          .buildAsync(
              URI.create("ws://localhost:" + host.port() + "/v1/sessions/" + id + "/events"),
              new WebSocket.Listener() {})
          .get(5, TimeUnit.SECONDS);
      assertEquals(java.util.List.of("/v1/sessions/" + id + "/events from 127.0.0.1"), seen);
    }
  }
}
