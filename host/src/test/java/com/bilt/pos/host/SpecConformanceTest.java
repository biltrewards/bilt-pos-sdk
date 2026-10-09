package com.bilt.pos.host;

import static com.bilt.pos.host.HostClient.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bilt.pos.media.service.InMemoryAdDecisionService;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The host against the Session Protocol spec. The spec's request examples are sent to a running
 * host and every response is validated against the response schema of its route; every event the
 * host emits along the way is validated against the {@code Event} schema. {@link HostClient} does
 * the checking, so the other suites conform too; this one drives the shapes they do not reach.
 */
class SpecConformanceTest {

  /** The terminal of {@code examples/create-session-request.json}. */
  private static final String POI = "VictaLane-275839164";

  private static final String DIAGNOSIS_OK =
      "{\"SaleToPOIResponse\":{\"DiagnosisResponse\":{\"Response\":{\"Result\":\"Success\"},"
          + "\"HostStatus\":[]}}}";

  private static final String BALANCE_FOUND =
      "{\"SaleToPOIResponse\":{\"BalanceInquiryResponse\":{\"Response\":{\"Result\":\"Success\"},"
          + "\"LoyaltyAccountStatus\":{\"LoyaltyAccount\":{\"LoyaltyAccountID\":{"
          + "\"LoyaltyID\":\"98234\"},\"LoyaltyBrand\":\"Bilt\"},\"CurrentBalance\":1200}}}}";

  private static final String TOTALS_OK =
      "{\"SaleToPOIResponse\":{\"GetTotalsResponse\":{"
          + "\"Response\":{\"Result\":\"Success\"},\"POIReconciliationID\":\"REC-1\","
          + "\"TransactionTotals\":[{\"PaymentInstrument\":\"Card\",\"AcquirerID\":\"ACQ\"}]}}}";

  private static final String RECONCILIATION_OK =
      "{\"SaleToPOIResponse\":{\"ReconciliationResponse\":{"
          + "\"Response\":{\"Result\":\"Success\"},\"ReconciliationType\":\"SaleReconciliation\","
          + "\"POIReconciliationID\":\"REC-2\"}}}";

  private static final String PRINT_OK =
      "{\"SaleToPOIResponse\":{\"PrintResponse\":{"
          + "\"Response\":{\"Result\":\"Success\"},\"DocumentQualifier\":\"CustomerReceipt\"}}}";

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final SpecValidator SPEC = SpecValidator.shared();

  private ScriptedTerminalClient terminal;
  private SessionHost host;
  private HostClient client;

  @BeforeEach
  void start() {
    terminal =
        new ScriptedTerminalClient()
            .reply(MessageCategoryType.ADMIN, ScriptedTerminalClient.ADMIN_OK)
            .reply(MessageCategoryType.ABORT, ScriptedTerminalClient.ADMIN_OK)
            .reply(MessageCategoryType.DIAGNOSIS, DIAGNOSIS_OK)
            .reply(MessageCategoryType.GET_TOTALS, TOTALS_OK)
            .reply(MessageCategoryType.RECONCILIATION, RECONCILIATION_OK)
            .reply(MessageCategoryType.PRINT, PRINT_OK)
            .reply(MessageCategoryType.BALANCE_INQUIRY, BALANCE_FOUND)
            .reply(MessageCategoryType.INPUT, ScriptedTerminalClient.INPUT_CONFIRMED)
            .reply(
                MessageCategoryType.PAYMENT, ScriptedTerminalClient.paymentOk("POI-PAY-1", "89.50"))
            .reply(MessageCategoryType.LOYALTY, ScriptedTerminalClient.AWARD_OK)
            .replyLoyalty("Rebate", ScriptedTerminalClient.REBATE_OK)
            .replyLoyalty("Award", ScriptedTerminalClient.AWARD_OK);
    host =
        SessionHost.builder()
            .port(0)
            .adDecisionService(new InMemoryAdDecisionService())
            .terminal(
                TerminalClientProvider.of(
                    terminal, TerminalInfo.of("Lane 3", "VictaLane").withReachable(true)))
            .build();
    host.start();
    client = new HostClient(host.port());
  }

  @AfterEach
  void stop() {
    terminal.release();
    host.stop();
  }

  private static JsonNode example(String file) throws Exception {
    return MAPPER.readTree(Files.readString(SPEC.examples().resolve(file)));
  }

  private static List<String> types(List<JsonNode> events) {
    return events.stream().map(e -> e.path("type").asText()).collect(Collectors.toList());
  }

  private List<JsonNode> eventsUntilEnded(String id) throws Exception {
    List<JsonNode> events =
        client.sseUntil(
            id, 0, e -> e.path("type").asText().equals("session.ended"), Duration.ofSeconds(10));
    assertEquals("session.started", events.get(0).path("type").asText());
    assertEquals(1, events.get(0).path("seq").asLong());
    for (int i = 1; i < events.size(); i++) {
      assertEquals(events.get(i - 1).path("seq").asLong() + 1, events.get(i).path("seq").asLong());
    }
    return events;
  }

  @Test
  void theSpecExamplesValidateAgainstTheirOwnSchemas() throws Exception {
    JsonNode index = example("index.json");
    assertTrue(index.isArray() && index.size() > 0);
    for (JsonNode entry : index) {
      Path file = SPEC.examples().resolve(entry.path("file").asText());
      SPEC.schema(entry.path("schema").asText(), MAPPER.readTree(Files.readString(file)));
    }
  }

  @Test
  void healthAndTheTerminalFollowTheSpec() throws Exception {
    JsonNode health = client.get("/health").expect(200).body;
    assertEquals("bridge", health.path("host").asText());
    assertEquals("Lane 3", health.path("terminal").path("label").asText());
    assertEquals(0, health.path("sessions").asInt());

    assertEquals("VictaLane", client.get("/v1/terminal").expect(200).text("model"));
    JsonNode diagnosis = client.post("/v1/terminal/diagnose", "{}").expect(200).body;
    assertTrue(diagnosis.path("hostStatuses").isArray());
    assertEquals("bilt-session-host", lastPoiId());
    client.post("/v1/terminal/diagnose?poiId=" + POI, "{}").expect(200);
    assertEquals(POI, lastPoiId());

    JsonNode totals = client.post("/v1/terminal/totals?storeLocation=STR-1", "{}").expect(200).body;
    assertEquals("REC-1", totals.path("poiReconciliationId").asText());
    assertEquals(1, totals.path("transactionTotals").size());
    JsonNode reconciliation =
        client.post("/v1/terminal/reconcile?poiId=ANY-LANE", "{}").expect(200).body;
    assertEquals("REC-2", reconciliation.path("poiReconciliationId").asText());
    assertEquals("ANY-LANE", lastPoiId());

    String print = json("{'format':'TEXT','content':'Thank you','documentQualifier':'JOURNAL'}");
    SPEC.request("POST", "/v1/terminal/print", MAPPER.readTree(print));
    client.post("/v1/terminal/print", print).expect(204);
    client.post("/v1/terminal/print", json("{'format':'TEXT'}")).expect(400);
    client.post("/v1/terminal/sound", json("{'action':'NOPE'}")).expect(400);
  }

  private String lastPoiId() {
    List<SaleToPOIRequest> requests = terminal.requests();
    return requests.get(requests.size() - 1).getMessageHeader().getPoiid();
  }

  @Test
  void aHostWithoutATerminalAnswersAsTheSpecSays() throws Exception {
    try (SessionHost bare = SessionHost.builder().port(0).build()) {
      bare.start();
      HostClient bareClient = new HostClient(bare.port());
      assertTrue(bareClient.get("/health").expect(200).body.path("terminal").isMissingNode());
      assertEquals("NOT_FOUND", bareClient.get("/v1/terminal").expect(404).text("code"));
      bareClient.post("/v1/terminal/diagnose", "{}").expect(404);
      bareClient.post("/v1/terminal/totals", "{}").expect(404);
      bareClient.post("/v1/terminal/reconcile", "{}").expect(404);
      bareClient
          .post("/v1/terminal/print", json("{'format':'TEXT','content':'Thank you'}"))
          .expect(404);
      bareClient.post("/v1/terminal/sound", json("{'action':'STOP'}")).expect(404);
      assertEquals(
          "UNSUPPORTED",
          bareClient
              .post("/v1/sessions", json("{'kind':'terminal','saleId':'L','currency':'USD'}"))
              .expect(409)
              .text("code"));
      assertEquals(
          "UNSUPPORTED",
          bareClient
              .post(
                  "/v1/sessions",
                  json(
                      "{'kind':'local','saleId':'L','currency':'USD',"
                          + "'widgets':[{'type':'retail-media','placements':['lane-banner']}]}"))
              .expect(409)
              .text("code"));
    }
  }

  @Test
  void aReusedIdempotencyKeyWithADifferentBodyIsRefusedAsTheSpecSays() throws Exception {
    String key = "reused-key";
    client
        .post("/v1/sessions", json("{'kind':'local','saleId':'A','currency':'USD'}"), key)
        .expect(201);
    assertEquals(
        "VALIDATION",
        client
            .post("/v1/sessions", json("{'kind':'local','saleId':'B','currency':'USD'}"), key)
            .expect(422)
            .text("code"));
  }

  @Test
  void theCreateSessionExampleStartsATerminalSessionWithWidgets() throws Exception {
    JsonNode request = example("create-session-request.json");
    SPEC.request("POST", "/v1/sessions", request);
    HostClient.Response created = client.post("/v1/sessions", request.toString()).expect(201);
    String id = created.text("id");
    assertEquals("terminal", created.text("kind"));
    assertEquals(POI, created.text("poiId"));
    assertEquals(1, client.get("/health").expect(200).body.path("sessions").asInt());

    JsonNode widgets = client.get("/v1/sessions/" + id + "/widgets").expect(200).body;
    assertEquals(1, widgets.size());
    JsonNode placement = widgets.get(0).path("placements").get(0);
    assertEquals("lane-banner", placement.path("id").asText());
    assertEquals("HANDOFF", placement.path("surfaceKind").asText());
    assertEquals("IMAGE", placement.path("formats").get(0).asText());
    client.post("/v1/sessions/" + id + "/widgets/retail-media/pause", "{}").expect(200);
    JsonNode resumed =
        client.post("/v1/sessions/" + id + "/widgets/retail-media/resume", "{}").expect(200).body;
    assertFalse(resumed.path("paused").asBoolean());
    client.post("/v1/sessions/" + id + "/widgets/other/pause", "{}").expect(404);

    JsonNode context = client.get("/v1/sessions/" + id + "/context").expect(200).body;
    assertEquals("pharmacy", context.path("attributes").path("lane-type").asText());
    assertEquals("STR-0142", context.path("storeLocation").asText());

    // the example's member is a phone number the terminal resolves in the background
    JsonNode operation = client.delete("/v1/sessions/" + id).expect(202).body;
    client.awaitOperation(id, operation.path("id").asText(), "succeeded", Duration.ofSeconds(10));
    List<JsonNode> events = eventsUntilEnded(id);
    List<String> types = types(events);
    assertTrue(types.contains("member.changed"), types.toString());
    JsonNode member =
        events.stream()
            .filter(e -> e.path("type").asText().equals("member.changed"))
            .reduce((first, second) -> second)
            .get()
            .path("payload")
            .path("member");
    assertEquals("98234", member.path("memberId").asText());
    assertTrue(member.path("resolved").asBoolean());
  }

  @Test
  void bareStringPlacementsAreStillAccepted() throws Exception {
    HostClient.Response created =
        client
            .post(
                "/v1/sessions",
                json(
                    "{'kind':'local','saleId':'LANE-1','currency':'USD','storeLocation':'STR-1',"
                        + "'widgets':[{'type':'retail-media','placements':['lane-banner']}],"
                        + "'clientCapabilities':{'formats':['IMAGE'],'surfaceKind':'WEB'}}"))
            .expect(201);
    JsonNode widgets = client.get("/v1/sessions/" + created.text("id") + "/widgets").body;
    JsonNode placement = widgets.get(0).path("placements").get(0);
    assertEquals("lane-banner", placement.path("id").asText());
    assertEquals("WEB", placement.path("surfaceKind").asText());
    client.delete("/v1/sessions/" + created.text("id")).expect(202);
  }

  @Test
  void retailMediaActionsAreAcceptedAndRejectionsSurfaceAsEvents() throws Exception {
    String id =
        client
            .post(
                "/v1/sessions",
                json(
                    "{'kind':'local','saleId':'LANE-1','currency':'USD','storeLocation':'STR-1',"
                        + "'widgets':[{'type':'retail-media','placements':[{'id':'lane-banner'}]}],"
                        + "'clientCapabilities':{'formats':['IMAGE'],'surfaceKind':'WEB'}}"))
            .expect(201)
            .text("id");
    String path = "/v1/sessions/" + id + "/widgets/retail-media/actions";

    // nothing is on display, so the report is accepted and the rejection arrives as an event
    String stale = json("{'kind':'viewed','creativeId':'gone','placement':'lane-banner'}");
    SPEC.request("POST", path, MAPPER.readTree(stale));
    client.post(path, stale).expect(202);
    client
        .post(path, json("{'kind':'viewed','creativeId':'gone','placement':'nowhere'}"))
        .expect(404);
    client
        .post(path, json("{'kind':'nope','creativeId':'gone','placement':'lane-banner'}"))
        .expect(400);
    client.post(path, json("{'kind':'viewed'}")).expect(400);

    client.delete("/v1/sessions/" + id).expect(202);
    List<String> types = types(eventsUntilEnded(id));
    assertTrue(types.contains("background.error"), types.toString());
  }

  @Test
  void theSettleExampleRunsThroughItsStepAndCompletes() throws Exception {
    String id =
        client
            .post(
                "/v1/sessions",
                json(
                    "{'kind':'terminal','saleId':'LANE-3','poiId':'"
                        + POI
                        + "','currency':'USD','storeLocation':'STR-0142','autoDisplay':false}"))
            .expect(201)
            .text("id");
    String base = "/v1/sessions/" + id;
    client.put(base + "/member", json("{'id':'98234'}")).expect(200);
    client
        .post(
            base + "/basket/items",
            json("{'sku':'SKU-1','description':'Item','unitPrice':'100.00'}"))
        .expect(200);

    JsonNode settle = example("settle-request.json");
    SPEC.request("POST", base + "/operations", settle);
    String operationId =
        client.post(base + "/operations", settle.toString()).expect(202).text("id");
    JsonNode waiting =
        client.awaitOperation(id, operationId, "awaitingReply", Duration.ofSeconds(10));
    JsonNode step = waiting.path("pendingStep");
    assertEquals("TOTAL_REQUIRED", step.path("kind").asText());

    // the example reply names a step id of its own, so it is stale here and refused with 422
    JsonNode staleReply = example("step-reply-total.json");
    SPEC.request("POST", base + "/operations/" + operationId + "/reply", staleReply);
    JsonNode refused =
        client
            .post(base + "/operations/" + operationId + "/reply", staleReply.toString())
            .expect(422)
            .body;
    assertEquals("INVALID_STATE", refused.path("code").asText());

    ObjectNode reply = (ObjectNode) staleReply.deepCopy();
    reply.put("stepId", step.path("stepId").asText()).put("total", "89.50");
    SPEC.request("POST", base + "/operations/" + operationId + "/reply", reply);
    client.post(base + "/operations/" + operationId + "/reply", reply.toString()).expect(200);
    JsonNode done = client.awaitOperation(id, operationId, "succeeded", Duration.ofSeconds(10));
    assertEquals("89.50", done.path("result").path("cardAmountCharged").asText());
    client.get(base + "/operations").expect(200);
    client.get(base + "/operations?status=succeeded,failed").expect(200);
    client.get(base + "/operations/nope").expect(404);
    client.post(base + "/abort", "{}").expect(202);

    client.delete(base).expect(202);
    List<String> types = types(eventsUntilEnded(id));
    for (String expected :
        List.of(
            "basket.changed",
            "member.changed",
            "operation.step",
            "operation.movement",
            "context.changed",
            "operation.completed")) {
      assertTrue(types.contains(expected), expected + " missing from " + types);
    }
  }

  @Test
  void aLocalSessionRoundTripsEveryBasketShape() throws Exception {
    HostClient.Response created =
        client
            .post(
                "/v1/sessions",
                json(
                    "{'kind':'local','saleId':'LANE-1','currency':'USD',"
                        + "'member':{'id':'98234'},'context':{'phase':'SCANNING'}}"))
            .expect(201);
    String id = created.text("id");
    String base = "/v1/sessions/" + id;
    client.get(base).expect(200);
    assertEquals("98234", client.get(base + "/member").expect(200).text("memberId"));

    String toothpaste =
        json(
            "{'reference':'line-1','sku':'SKU-4471','description':'Toothpaste','quantity':2,"
                + "'unitPrice':'4.99','category':'health','taxRate':'0.08875',"
                + "'discounts':[{'reference':'OFFER-1','label':'Member price','amount':'1.00'}],"
                + "'metadata':{'aisle':'7'}}");
    SPEC.request("POST", base + "/basket/items", MAPPER.readTree(toothpaste));
    JsonNode basket = client.post(base + "/basket/items", toothpaste).expect(200).body;
    String itemId = basket.path("items").get(0).path("itemId").asText();
    client
        .post(
            base + "/basket/items",
            json(
                "{'sku':'SKU-RET','description':'Returned mug','unitPrice':'12.00','type':'RETURN'}"))
        .expect(200);

    String patch = json("{'quantity':3,'discounts':[],'taxAmount':'1.11'}");
    SPEC.request("PATCH", base + "/basket/items/" + itemId, MAPPER.readTree(patch));
    basket = client.patch(base + "/basket/items/" + itemId, patch).expect(200).body;
    assertEquals(3, basket.path("items").get(0).path("quantity").asInt());
    client
        .patch(base + "/basket/items/" + itemId, json("{'taxRate':'0.1','taxAmount':'1'}"))
        .expect(400);
    client.patch(base + "/basket/items/nope", json("{'quantity':1}")).expect(404);

    String mutations =
        json(
            "{'mutations':["
                + "{'op':'ADD_ITEM','itemId':'42','item':{'sku':'SKU-2','description':'Floss','unitPrice':'2.50'}},"
                + "{'op':'ADD_ITEM','item':{'sku':'SKU-3','description':'Soap','unitPrice':'3.00'}},"
                + "{'op':'UPDATE_ITEM_QUANTITY','itemId':'42','quantity':2},"
                + "{'op':'SET_DISCOUNTS','sku':'SKU-2','discounts':[{'label':'Manual','amount':'0.50'}]},"
                + "{'op':'SET_TAX_RATE','itemId':'42','amount':'0.05'},"
                + "{'op':'SET_TAX_AMOUNT','sku':'SKU-3','amount':'0.30'},"
                + "{'op':'REMOVE_ITEM','sku':'SKU-RET'},"
                + "{'op':'SET_TAX_TOTAL','amount':'9.99'}]}");
    SPEC.request("POST", base + "/basket/mutations", MAPPER.readTree(mutations));
    basket = client.post(base + "/basket/mutations", mutations).expect(200).body;
    assertEquals("9.99", basket.path("taxTotal").asText());
    assertEquals(
        List.of("SKU-4471", "SKU-2", "SKU-3"),
        basket.path("items").findValuesAsText("sku").stream()
            .distinct()
            .collect(Collectors.toList()));
    client.post(base + "/basket/mutations", json("{'mutations':[{'op':'nope'}]}")).expect(400);
    client.post(base + "/basket/mutations", json("{'mutations':[]}")).expect(400);

    client.post(base + "/basket/tax-total", json("{'amount':'1.23'}")).expect(200);
    basket = client.post(base + "/basket/tax-total", json("{'amount':null}")).expect(200).body;
    assertFalse(basket.path("taxTotal").asText().equals("1.23"));

    // a snapshot round trip: what GET reported is accepted back whole, and nothing but the
    // SDK's own updatedAt stamp differs afterwards
    ObjectNode snapshot = (ObjectNode) client.get(base + "/basket").expect(200).body;
    ObjectNode replace = MAPPER.createObjectNode();
    replace.set("snapshot", snapshot);
    SPEC.request("PUT", base + "/basket", replace);
    ObjectNode replaced =
        (ObjectNode) client.put(base + "/basket", replace.toString()).expect(200).body;
    assertEquals(snapshot.without("updatedAt"), replaced.without("updatedAt"));
    String items = json("{'items':[{'sku':'SKU-9','description':'Replaced','unitPrice':'1.00'}]}");
    SPEC.request("PUT", base + "/basket", MAPPER.readTree(items));
    basket = client.put(base + "/basket", items).expect(200).body;
    assertEquals(1, basket.path("items").size());
    client.put(base + "/basket", json("{'snapshot':{'items':[]},'items':[]}")).expect(400);
    client
        .delete(base + "/basket/items/" + basket.path("items").get(0).path("itemId").asText())
        .expect(200);
    client.post(base + "/basket/clear", "{}").expect(200);

    JsonNode pending =
        client
            .put(
                base + "/member",
                json(
                    "{'resolver':{'type':'CUSTOM','customType':'loyalty-card','value':'ABC12345',"
                        + "'keyedByCashier':true}}"))
            .expect(200)
            .body;
    assertEquals("****2345", pending.path("resolver").path("value").asText());
    client
        .put(base + "/member", json("{'id':'1','resolver':{'type':'PHONE','value':'1'}}"))
        .expect(400);
    client.delete(base + "/member").expect(204);
    client.get(base + "/member").expect(204);

    client.patch(base + "/context", json("{'attributes':{'till':'7'}}")).expect(200);
    client.patch(base + "/context", json("{'phase':'MEMBER_IDENTIFIED'}")).expect(200);
    client.patch(base + "/context", "{}").expect(400);
    client.post(base + "/abort", "{}").expect(202);
    client.post(base + "/operations", json("{'type':'settle'}")).expect(409);
    client.post(base + "/operations", json("{'type':'nope'}")).expect(400);
    client.post(base + "/operations/nope/reply", json("{'stepId':'x'}")).expect(404);

    client.post(base + "/force-end", json("{'reason':' '}")).expect(400);
    JsonNode forced =
        client.post(base + "/force-end", json("{'reason':'conformance run'}")).expect(202).body;
    assertEquals("forceEnd", forced.path("type").asText());
    client.awaitOperation(id, forced.path("id").asText(), "succeeded", Duration.ofSeconds(10));
    assertEquals("ended", client.get(base).expect(200).text("state"));
    client.post(base + "/force-end", json("{'reason':'again'}")).expect(409);
    client.patch(base + "/context", json("{'phase':'SCANNING'}")).expect(409);

    List<JsonNode> events = eventsUntilEnded(id);
    JsonNode ended = events.get(events.size() - 1).path("payload");
    assertTrue(ended.path("forced").asBoolean());
    assertEquals("conformance run", ended.path("reason").asText());
    List<String> types = types(events);
    assertTrue(
        types.contains("basket.changed") && types.contains("context.changed"), types.toString());
  }

  @Test
  void errorsAreSessionErrorsWithTheSpecCodes() throws Exception {
    assertEquals("NOT_FOUND", client.get("/v1/sessions/nope").expect(404).text("code"));
    assertEquals("VALIDATION", client.post("/v1/sessions", "{nope").expect(400).text("code"));
    assertEquals(
        "VALIDATION",
        client
            .postUnkeyed("/v1/sessions", json("{'kind':'local','saleId':'L','currency':'USD'}"))
            .expect(400)
            .text("code"));
    assertEquals(
        "VALIDATION",
        client
            .post("/v1/sessions", json("{'kind':'local','saleId':'L','currency':'usd'}"))
            .expect(400)
            .text("code"));
  }
}
