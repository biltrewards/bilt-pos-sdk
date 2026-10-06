package com.bilt.pos.host;

import static com.bilt.pos.host.HostClient.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A {@code local} session driven end to end over HTTP: no terminal, no operations. */
class LocalSessionTest {

  private static SessionHost host;
  private static HostClient client;

  @BeforeAll
  static void start() {
    host = SessionHost.builder().port(0).allowedOrigins(List.of("https://pos.example")).build();
    host.start();
    client = new HostClient(host.port());
  }

  @AfterAll
  static void stop() {
    host.stop();
  }

  private static String create() throws Exception {
    HostClient.Response created =
        client
            .post(
                "/v1/sessions",
                json(
                    "{'kind':'local','saleId':'LANE-1','currency':'USD','storeLocation':'STR-1',"
                        + "'context':{'attributes':{'register':'3'}}}"))
            .expect(201);
    assertEquals("local", created.text("kind"));
    assertEquals("open", created.text("state"));
    assertTrue(created.body.path("autoDisplay").asBoolean());
    assertEquals("/v1/sessions/" + created.text("id") + "/events", created.text("eventsUrl"));
    return created.text("id");
  }

  @Test
  void healthReportsVersionsAndProtocol() throws Exception {
    JsonNode health = client.get("/health").expect(200).body;
    assertEquals("bridge", health.path("host").asText());
    assertEquals("1", health.path("protocolVersions").get(0).asText());
    assertNotNull(health.path("sdkVersion").asText(null));
    assertTrue(health.path("terminals").isArray());
    assertEquals(0, health.path("terminals").size());
  }

  @Test
  void corsPreflightIsAnsweredForAllowedOriginsOnly() throws Exception {
    HostClient.Response allowed =
        client.preflight("/v1/sessions", "https://pos.example").expect(204);
    assertEquals(
        "https://pos.example",
        allowed.headers.firstValue("Access-Control-Allow-Origin").orElse(null));
    assertTrue(
        allowed
            .headers
            .firstValue("Access-Control-Allow-Headers")
            .orElse("")
            .contains("Idempotency-Key"));
    assertEquals(
        "true", allowed.headers.firstValue("Access-Control-Allow-Private-Network").orElse(null));
    HostClient.Response denied =
        client.preflight("/v1/sessions", "https://evil.example").expect(204);
    assertTrue(denied.headers.firstValue("Access-Control-Allow-Origin").isEmpty());
  }

  @Test
  void basketMemberContextAndEndOverHttp() throws Exception {
    String id = create();
    String base = "/v1/sessions/" + id;
    assertEquals(1, host.activeSessions());

    JsonNode basket =
        client
            .post(
                base + "/basket/items",
                json("{'sku':'SKU-1','description':'Toothpaste','quantity':2,'unitPrice':'4.99'}"))
            .expect(200)
            .body;
    assertEquals(1, basket.path("items").size());
    assertEquals("9.98", basket.path("grandTotal").asText());
    assertNotNull(basket.path("saleTransactionId").path("transactionId").asText(null));
    String itemId = basket.path("items").get(0).path("itemId").asText();

    basket =
        client.patch(base + "/basket/items/" + itemId, json("{'quantity':3}")).expect(200).body;
    assertEquals(3, basket.path("items").get(0).path("quantity").asInt());

    basket =
        client
            .post(
                base + "/basket/mutations",
                json(
                    "{'mutations':[{'op':'ADD_ITEM','item':{'sku':'SKU-2','description':'Floss',"
                        + "'unitPrice':'2.50'}},{'op':'SET_TAX_RATE','itemId':'"
                        + itemId
                        + "','amount':'0.10'}]}"))
            .expect(200)
            .body;
    assertEquals(2, basket.path("items").size());
    assertEquals("1.50", basket.path("items").get(0).path("taxAmount").asText());

    basket =
        client.patch(base + "/basket/items/" + itemId, json("{'quantity':0}")).expect(200).body;
    assertEquals(1, basket.path("items").size());

    basket =
        client
            .put(
                base + "/basket",
                json("{'items':[{'sku':'SKU-9','description':'Replaced','unitPrice':'1.00'}]}"))
            .expect(200)
            .body;
    assertEquals(1, basket.path("items").size());
    assertEquals("SKU-9", basket.path("items").get(0).path("sku").asText());

    client.delete(base + "/basket/items/nope").expect(404);
    client
        .post(base + "/basket/items", json("{'sku':'','description':'x','unitPrice':'1'}"))
        .expect(400);

    client.get(base + "/member").expect(204);
    JsonNode member = client.put(base + "/member", json("{'id':'98234'}")).expect(200).body;
    assertTrue(member.path("resolved").asBoolean());
    assertEquals("98234", member.path("memberId").asText());
    assertEquals(0, member.path("pointBalance").asInt());
    member =
        client
            .put(base + "/member", json("{'resolver':{'type':'PHONE','value':'+12015550123'}}"))
            .expect(200)
            .body;
    assertFalse(member.path("resolved").asBoolean());
    assertEquals("********0123", member.path("resolver").path("value").asText());
    client.delete(base + "/member").expect(204);
    client.get(base + "/member").expect(204);

    JsonNode context =
        client
            .patch(
                base + "/context",
                json("{'phase':'TENDERING','attributes':{'register':null,'till':'7'}}"))
            .expect(200)
            .body;
    assertEquals("TENDERING", context.path("phase").asText());
    assertEquals("7", context.path("attributes").path("till").asText());
    assertFalse(context.path("attributes").has("register"));

    JsonNode refused =
        client
            .post(base + "/operations", json("{'type':'requestConfirmation','prompt':'Receipt?'}"))
            .expect(409)
            .body;
    assertEquals("UNSUPPORTED", refused.path("code").asText());

    JsonNode cleared = client.post(base + "/basket/clear", "{}").expect(200).body;
    assertEquals(0, cleared.path("items").size());

    JsonNode end = client.delete(base).expect(202).body;
    assertEquals("end", end.path("type").asText());
    assertEquals("succeeded", end.path("status").asText());
    assertEquals("ended", client.get(base).expect(200).text("state"));
    client.delete(base).expect(409);
    client
        .post(base + "/basket/items", json("{'sku':'SKU-1','description':'x','unitPrice':'1'}"))
        .expect(409);

    List<JsonNode> events =
        client.sseUntil(
            id, 0, e -> e.path("type").asText().equals("session.ended"), Duration.ofSeconds(10));
    assertEquals("session.started", events.get(0).path("type").asText());
    assertEquals(1, events.get(0).path("seq").asLong());
    assertEquals(id, events.get(0).path("payload").path("session").path("id").asText());
    List<String> types = events.stream().map(e -> e.path("type").asText()).toList();
    assertTrue(types.contains("basket.changed"), types.toString());
    assertTrue(types.contains("member.changed"), types.toString());
    assertTrue(types.contains("context.changed"), types.toString());
    assertTrue(types.contains("operation.completed"), types.toString());
    for (int i = 1; i < events.size(); i++) {
      assertEquals(events.get(i - 1).path("seq").asLong() + 1, events.get(i).path("seq").asLong());
    }
    JsonNode ended = events.get(events.size() - 1).path("payload");
    assertFalse(ended.path("forced").asBoolean());

    long since = events.get(events.size() - 2).path("seq").asLong();
    List<JsonNode> replayed =
        client.sseUntil(
            id,
            since,
            e -> e.path("type").asText().equals("session.ended"),
            Duration.ofSeconds(10));
    assertEquals(1, replayed.size());
    assertEquals(since + 1, replayed.get(0).path("seq").asLong());
  }

  @Test
  void idempotencyKeyIsRequiredAndReplayed() throws Exception {
    client
        .postUnkeyed("/v1/sessions", json("{'kind':'local','saleId':'L','currency':'USD'}"))
        .expect(400);
    String id = create();
    String base = "/v1/sessions/" + id;
    String item = json("{'sku':'SKU-1','description':'Once','unitPrice':'5.00'}");

    JsonNode first = client.post(base + "/basket/items", item, "key-1").expect(200).body;
    HostClient.Response replay = client.post(base + "/basket/items", item, "key-1").expect(200);
    assertEquals(first, replay.body);
    assertEquals("true", replay.headers.firstValue("Idempotent-Replayed").orElse(null));
    assertEquals(1, client.get(base + "/basket").expect(200).body.path("items").size());

    JsonNode reused =
        client
            .post(
                base + "/basket/items",
                json("{'sku':'SKU-2','description':'Other','unitPrice':'1'}"),
                "key-1")
            .expect(422)
            .body;
    assertEquals("VALIDATION", reused.path("code").asText());
  }

  @Test
  void unknownSessionIs404AndMalformedBodyIs400() throws Exception {
    client.get("/v1/sessions/nope").expect(404);
    client.post("/v1/sessions", "{not json").expect(400);
    client
        .post(
            "/v1/sessions",
            json("{'kind':'terminal','saleId':'L','poiId':'none','currency':'USD'}"))
        .expect(404);
    JsonNode error =
        client.post("/v1/sessions", json("{'kind':'local','currency':'USD'}")).expect(400).body;
    assertEquals("VALIDATION", error.path("code").asText());
    assertEquals("NOT_FOUND", client.get("/v1/sessions/nope").body.path("code").asText());
  }

  @Test
  void aConflictIsNotReplayedForARetryWithTheSameKey() throws Exception {
    try (SessionHost host = SessionHost.builder().port(0).build()) {
      host.start();
      HostClient client = new HostClient(host.port());
      String id =
          client
              .post("/v1/sessions", json("{'kind':'local','saleId':'LANE-1','currency':'USD'}"))
              .expect(201)
              .text("id");
      String body = json("{'type':'requestConfirmation','prompt':'Receipt?'}");
      String path = "/v1/sessions/" + id + "/operations";

      HostClient.Response first = client.post(path, body, "retry-key").expect(409);
      HostClient.Response retry = client.post(path, body, "retry-key").expect(409);

      assertFalse(first.headers.firstValue("Idempotent-Replayed").isPresent());
      // the retry ran again rather than answering from the cache
      assertFalse(retry.headers.firstValue("Idempotent-Replayed").isPresent());
    }
  }
}
