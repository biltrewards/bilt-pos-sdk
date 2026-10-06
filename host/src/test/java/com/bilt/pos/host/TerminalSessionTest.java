package com.bilt.pos.host;

import static com.bilt.pos.host.HostClient.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bilt.pos.nexo.client.TerminalClient;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@code terminal} sessions against a scripted terminal: steps, replies, aborts, idempotency. */
class TerminalSessionTest {

  private static final String POI = "VictaLane-1";

  private ScriptedTerminalClient terminal;
  private SessionHost host;
  private HostClient client;

  @BeforeEach
  void start() {
    terminal =
        new ScriptedTerminalClient()
            .reply(MessageCategoryType.ADMIN, ScriptedTerminalClient.ADMIN_OK)
            .reply(MessageCategoryType.ABORT, ScriptedTerminalClient.ADMIN_OK)
            .reply(MessageCategoryType.INPUT, ScriptedTerminalClient.INPUT_CONFIRMED)
            .reply(MessageCategoryType.PAYMENT, ScriptedTerminalClient.paymentOk("POI-PAY-1", "89.50"))
            .reply(MessageCategoryType.LOYALTY, ScriptedTerminalClient.AWARD_OK)
            .replyLoyalty("Rebate", ScriptedTerminalClient.REBATE_OK)
            .replyLoyalty("Award", ScriptedTerminalClient.AWARD_OK);
    host = host(StepDeadlines.defaults());
    host.start();
    client = new HostClient(host.port());
  }

  private SessionHost host(StepDeadlines deadlines) {
    return SessionHost.builder()
        .port(0)
        .stepDeadlines(deadlines)
        .terminalClients(
            new TerminalClientProvider() {
              @Override
              public TerminalClient forPoi(String poiId) {
                return POI.equals(poiId) ? terminal : null;
              }

              @Override
              public List<TerminalInfo> terminals() {
                return List.of(TerminalInfo.of(POI, "Lane 1", "VictaLane"));
              }
            })
        .build();
  }

  @AfterEach
  void stop() {
    terminal.release();
    host.stop();
  }

  private String createWithMemberAndItem() throws Exception {
    String id =
        client
            .post(
                "/v1/sessions",
                json(
                    "{'kind':'terminal','saleId':'LANE-1','poiId':'"
                        + POI
                        + "','currency':'USD','storeLocation':'STR-1','autoDisplay':false}"))
            .expect(201)
            .text("id");
    client.put("/v1/sessions/" + id + "/member", json("{'id':'98234'}")).expect(200);
    client
        .post(
            "/v1/sessions/" + id + "/basket/items",
            json("{'sku':'SKU-1','description':'Item','unitPrice':'100.00'}"))
        .expect(200);
    return id;
  }

  private JsonNode submit(String id, String body) throws Exception {
    return client.post("/v1/sessions/" + id + "/operations", body).expect(202).body;
  }

  @Test
  void terminalsAreListedAndSessionCarriesPoiId() throws Exception {
    JsonNode terminals = client.get("/v1/terminals").expect(200).body;
    assertEquals(POI, terminals.get(0).path("poiId").asText());
    assertEquals("Lane 1", terminals.get(0).path("label").asText());
    assertEquals(1, client.get("/health").expect(200).body.path("terminals").asInt());
    client.post("/v1/terminals/unknown/diagnose", "{}").expect(404);

    String id = createWithMemberAndItem();
    JsonNode session = client.get("/v1/sessions/" + id).expect(200).body;
    assertEquals("terminal", session.path("kind").asText());
    assertEquals(POI, session.path("poiId").asText());
    assertTrue(client.get("/v1/sessions/" + id + "/widgets").expect(200).body.isEmpty());
    client.delete("/v1/sessions/" + id).expect(200);
  }

  @Test
  void settleRaisesTotalRequiredAndTakesTheReply() throws Exception {
    String id = createWithMemberAndItem();
    JsonNode operation = submit(id, json("{'type':'settle'}"));
    String operationId = operation.path("id").asText();
    assertEquals("settle", operation.path("type").asText());

    JsonNode waiting = client.awaitOperation(id, operationId, "awaitingReply", Duration.ofSeconds(10));
    JsonNode step = waiting.path("pendingStep");
    assertEquals("TOTAL_REQUIRED", step.path("kind").asText());
    assertEquals("REBATE_REDEMPTION", step.path("settlementStep").asText());
    assertEquals("90.00", step.path("suggestedTotal").asText());
    assertEquals("90.00", step.path("default").path("total").asText());
    assertNotNull(step.path("deadlineAt").asText(null));

    client
        .post(
            "/v1/sessions/" + id + "/operations/" + operationId + "/reply",
            json("{'stepId':'" + step.path("stepId").asText() + "','total':'89.50'}"))
        .expect(200);
    client
        .post(
            "/v1/sessions/" + id + "/operations/" + operationId + "/reply",
            json("{'stepId':'" + step.path("stepId").asText() + "','total':'1.00'}"))
        .expect(409);

    JsonNode done = client.awaitOperation(id, operationId, "succeeded", Duration.ofSeconds(10));
    assertEquals("89.50", done.path("result").path("cardAmountCharged").asText());
    assertEquals("10.00", done.path("result").path("totalRebateAmount").asText());
    assertEquals(89, done.path("result").path("totalPointsEarned").asInt());

    List<JsonNode> events =
        client.sseUntil(
            id,
            0,
            e ->
                e.path("type").asText().equals("operation.completed")
                    && e.path("payload").path("id").asText().equals(operationId),
            Duration.ofSeconds(10));
    List<String> types = events.stream().map(e -> e.path("type").asText()).toList();
    assertTrue(types.contains("operation.step"), types.toString());
    assertTrue(types.contains("operation.movement"), types.toString());
    assertTrue(types.contains("context.changed"), types.toString());
    JsonNode stepEvent =
        events.stream().filter(e -> e.path("type").asText().equals("operation.step")).findFirst().get();
    assertEquals(operationId, stepEvent.path("payload").path("operationId").asText());
    assertEquals("TOTAL_REQUIRED", stepEvent.path("payload").path("step").path("kind").asText());
    assertEquals("succeeded", events.get(events.size() - 1).path("payload").path("status").asText());
  }

  @Test
  void stepDeadlineAppliesTheDefault() throws Exception {
    host.stop();
    host = host(StepDeadlines.uniform(Duration.ofMillis(300)));
    host.start();
    client = new HostClient(host.port());

    String id = createWithMemberAndItem();
    terminal.reply(MessageCategoryType.PAYMENT, ScriptedTerminalClient.paymentOk("POI-PAY-2", "90.00"));
    String operationId = submit(id, json("{'type':'settle'}")).path("id").asText();
    JsonNode done = client.awaitOperation(id, operationId, "succeeded", Duration.ofSeconds(10));
    assertEquals("90.00", done.path("result").path("cardAmountCharged").asText());
  }

  @Test
  void stepsCanBeDeclinedUpFront() throws Exception {
    String id = createWithMemberAndItem();
    terminal.reply(MessageCategoryType.PAYMENT, ScriptedTerminalClient.paymentOk("POI-PAY-3", "90.00"));
    String operationId = submit(id, json("{'type':'settle','steps':[]}")).path("id").asText();
    JsonNode done = client.awaitOperation(id, operationId, "succeeded", Duration.ofSeconds(10));
    assertEquals("90.00", done.path("result").path("cardAmountCharged").asText());
  }

  @Test
  void queuedOperationsFollowTheLaneAndCanBeAborted() throws Exception {
    String id = createWithMemberAndItem();
    terminal.hold(MessageCategoryType.INPUT);
    String first = submit(id, json("{'type':'requestConfirmation','prompt':'Receipt?'}")).path("id").asText();
    JsonNode second = submit(id, json("{'type':'requestConfirmation','prompt':'Bag?'}"));
    assertEquals("queued", second.path("status").asText());
    assertTrue(terminal.awaitHeld(Duration.ofSeconds(5)));
    assertEquals("running", client.get("/v1/sessions/" + id + "/operations/" + first).body.path("status").asText());

    JsonNode aborted =
        client.post("/v1/sessions/" + id + "/operations/" + second.path("id").asText() + "/abort", "{}").expect(202).body;
    assertEquals("aborted", aborted.path("status").asText());
    assertEquals("ABORTED", aborted.path("error").path("code").asText());
    client.post("/v1/sessions/" + id + "/operations/" + second.path("id").asText() + "/abort", "{}").expect(409);

    client.delete("/v1/sessions/" + id).expect(409);

    terminal.release();
    JsonNode done = client.awaitOperation(id, first, "succeeded", Duration.ofSeconds(10));
    assertTrue(done.path("result").path("confirmed").asBoolean());
    client.delete("/v1/sessions/" + id).expect(200);
  }

  @Test
  void abortDuringAPendingStepAbortsTheSettlement() throws Exception {
    String id = createWithMemberAndItem();
    String operationId = submit(id, json("{'type':'settle'}")).path("id").asText();
    client.awaitOperation(id, operationId, "awaitingReply", Duration.ofSeconds(10));
    client.post("/v1/sessions/" + id + "/operations/" + operationId + "/abort", "{}").expect(202);
    JsonNode done = client.awaitOperation(id, operationId, "aborted", Duration.ofSeconds(10));
    assertEquals("ABORTED", done.path("error").path("code").asText());
    assertTrue(client.get("/v1/sessions/" + id + "/operations/" + operationId).body.path("pendingStep").isMissingNode());
  }

  @Test
  void postingAnOperationTwiceWithOneKeyReplaysTheOperation() throws Exception {
    String id = createWithMemberAndItem();
    terminal.hold(MessageCategoryType.INPUT);
    String body = json("{'type':'requestConfirmation','prompt':'Receipt?'}");
    JsonNode first = client.post("/v1/sessions/" + id + "/operations", body, "op-key").expect(202).body;
    JsonNode replay = client.post("/v1/sessions/" + id + "/operations", body, "op-key").expect(202).body;
    assertEquals(first.path("id").asText(), replay.path("id").asText());
    JsonNode fresh = client.post("/v1/sessions/" + id + "/operations", body, "op-key-2").expect(202).body;
    assertNotEquals(first.path("id").asText(), fresh.path("id").asText());
    assertEquals(2, client.get("/v1/sessions/" + id + "/operations").expect(200).body.size());
    terminal.release();
  }
}
