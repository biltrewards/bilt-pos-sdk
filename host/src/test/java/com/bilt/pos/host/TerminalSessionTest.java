package com.bilt.pos.host;

import static com.bilt.pos.host.HostClient.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.stream.StreamSupport;
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
            .reply(
                MessageCategoryType.PAYMENT, ScriptedTerminalClient.paymentOk("POI-PAY-1", "89.50"))
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
        .terminal(
            TerminalClientProvider.of(
                terminal, TerminalInfo.of("Lane 1", "VictaLane").withReachable(true)))
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

  private static final String SETTLE_WITH_TOTALS =
      json("{'type':'settle','handledSteps':['TOTAL_REQUIRED']}");

  @Test
  void theTerminalIsReportedAndTheSessionPoiIdIsPassedThrough() throws Exception {
    JsonNode info = client.get("/v1/terminal").expect(200).body;
    assertEquals("Lane 1", info.path("label").asText());
    assertEquals("VictaLane", info.path("model").asText());
    assertTrue(info.path("reachable").asBoolean());
    assertTrue(info.path("poiId").isMissingNode());
    assertEquals(
        "VictaLane",
        client.get("/health").expect(200).body.path("terminal").path("model").asText());

    String id = createWithMemberAndItem();
    JsonNode session = client.get("/v1/sessions/" + id).expect(200).body;
    assertEquals("terminal", session.path("kind").asText());
    assertEquals(POI, session.path("poiId").asText());
    assertTrue(client.get("/v1/sessions/" + id + "/widgets").expect(200).body.isEmpty());
    assertEquals("succeeded", client.delete("/v1/sessions/" + id).expect(202).text("status"));
    assertFalse(terminal.requests().isEmpty());
    terminal
        .requests()
        .forEach(request -> assertEquals(POI, request.getMessageHeader().getPoiid()));
  }

  @Test
  void anyPoiIdReachesTheOneTerminalAndAMissingOneFallsBackToTheDefault() throws Exception {
    String named =
        client
            .post(
                "/v1/sessions",
                json("{'kind':'terminal','saleId':'L','poiId':'ANY-LANE','currency':'USD'}"))
            .expect(201)
            .text("poiId");
    assertEquals("ANY-LANE", named);
    assertEquals("ANY-LANE", lastPoiId());

    String fallback =
        client
            .post("/v1/sessions", json("{'kind':'terminal','saleId':'L','currency':'USD'}"))
            .expect(201)
            .text("poiId");
    assertEquals("bilt-session-host", fallback);
    assertEquals("bilt-session-host", lastPoiId());
  }

  @Test
  void anEmptyPoiIdIsRejectedLikeTheSchemaSays() throws Exception {
    client
        .post("/v1/sessions", json("{'kind':'terminal','saleId':'L','poiId':'','currency':'USD'}"))
        .expect(400);
  }

  private String lastPoiId() {
    List<SaleToPOIRequest> requests = terminal.requests();
    return requests.get(requests.size() - 1).getMessageHeader().getPoiid();
  }

  @Test
  void aHostWithoutATerminalRefusesTerminalSessions() throws Exception {
    try (SessionHost bare = SessionHost.builder().port(0).build()) {
      bare.start();
      HostClient bareClient = new HostClient(bare.port());
      assertTrue(bareClient.get("/health").expect(200).body.path("terminal").isMissingNode());
      assertEquals("NOT_FOUND", bareClient.get("/v1/terminal").expect(404).text("code"));
      bareClient.post("/v1/terminal/diagnose", "{}").expect(404);
      assertEquals(
          "UNSUPPORTED",
          bareClient
              .post("/v1/sessions", json("{'kind':'terminal','saleId':'L','currency':'USD'}"))
              .expect(409)
              .text("code"));
    }
  }

  @Test
  void settleRaisesTotalRequiredAndTakesTheReply() throws Exception {
    String id = createWithMemberAndItem();
    JsonNode operation = submit(id, SETTLE_WITH_TOTALS);
    String operationId = operation.path("id").asText();
    assertEquals("settle", operation.path("type").asText());

    JsonNode waiting =
        client.awaitOperation(id, operationId, "awaitingReply", Duration.ofSeconds(10));
    JsonNode step = waiting.path("pendingStep");
    assertEquals("TOTAL_REQUIRED", step.path("kind").asText());
    assertEquals(operationId, step.path("operationId").asText());
    assertEquals("REBATE_REDEMPTION", step.path("step").asText());
    assertEquals("90.00", step.path("rebates").path("suggestedTotal").asText());
    assertEquals("90.00", step.path("default").path("total").asText());
    assertEquals(step.path("stepId").asText(), step.path("default").path("stepId").asText());
    assertNotNull(step.path("deadlineAt").asText(null));

    client
        .post(
            "/v1/sessions/" + id + "/operations/" + operationId + "/reply",
            json("{'stepId':'" + step.path("stepId").asText() + "','total':'89.50'}"))
        .expect(200);
    JsonNode stale =
        client
            .post(
                "/v1/sessions/" + id + "/operations/" + operationId + "/reply",
                json("{'stepId':'" + step.path("stepId").asText() + "','total':'1.00'}"))
            .expect(422)
            .body;
    assertEquals("INVALID_STATE", stale.path("code").asText());

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
        events.stream()
            .filter(e -> e.path("type").asText().equals("operation.step"))
            .findFirst()
            .get();
    assertEquals(operationId, stepEvent.path("payload").path("operationId").asText());
    assertEquals("TOTAL_REQUIRED", stepEvent.path("payload").path("kind").asText());
    assertEquals(
        "succeeded", events.get(events.size() - 1).path("payload").path("status").asText());
  }

  @Test
  void stepDeadlineAppliesTheDefault() throws Exception {
    host.stop();
    host = host(StepDeadlines.uniform(Duration.ofMillis(300)));
    host.start();
    client = new HostClient(host.port());

    String id = createWithMemberAndItem();
    terminal.reply(
        MessageCategoryType.PAYMENT, ScriptedTerminalClient.paymentOk("POI-PAY-2", "90.00"));
    String operationId = submit(id, SETTLE_WITH_TOTALS).path("id").asText();
    JsonNode done = client.awaitOperation(id, operationId, "succeeded", Duration.ofSeconds(10));
    assertEquals("90.00", done.path("result").path("cardAmountCharged").asText());
  }

  @Test
  void undeclaredStepsTakeTheDefaultWithoutAsking() throws Exception {
    String id = createWithMemberAndItem();
    terminal.reply(
        MessageCategoryType.PAYMENT, ScriptedTerminalClient.paymentOk("POI-PAY-3", "90.00"));
    String operationId = submit(id, json("{'type':'settle'}")).path("id").asText();
    JsonNode done = client.awaitOperation(id, operationId, "succeeded", Duration.ofSeconds(10));
    assertEquals("90.00", done.path("result").path("cardAmountCharged").asText());
    List<JsonNode> events =
        client.sseUntil(
            id,
            0,
            e -> e.path("type").asText().equals("operation.completed"),
            Duration.ofSeconds(10));
    assertTrue(events.stream().noneMatch(e -> e.path("type").asText().equals("operation.step")));
  }

  @Test
  void queuedOperationsFollowTheLaneAndEndQueuesBehindThem() throws Exception {
    String id = createWithMemberAndItem();
    terminal.hold(MessageCategoryType.INPUT);
    String first =
        submit(id, json("{'type':'requestConfirmation','prompt':'Receipt?'}")).path("id").asText();
    JsonNode second = submit(id, json("{'type':'requestConfirmation','prompt':'Bag?'}"));
    assertEquals("queued", second.path("status").asText());
    assertTrue(terminal.awaitHeld(Duration.ofSeconds(5)));
    assertEquals(
        "running",
        client.get("/v1/sessions/" + id + "/operations/" + first).body.path("status").asText());

    String secondId = second.path("id").asText();
    JsonNode aborted =
        client
            .post("/v1/sessions/" + id + "/operations/" + secondId + "/abort", "{}")
            .expect(202)
            .body;
    assertEquals("aborted", aborted.path("status").asText());
    assertEquals("ABORTED", aborted.path("error").path("code").asText());
    client.post("/v1/sessions/" + id + "/operations/" + secondId + "/abort", "{}").expect(200);

    JsonNode end = client.delete("/v1/sessions/" + id).expect(202).body;
    assertEquals("end", end.path("type").asText());
    assertEquals("queued", end.path("status").asText());
    assertEquals("ending", client.get("/v1/sessions/" + id).expect(200).text("state"));
    client.delete("/v1/sessions/" + id).expect(409);

    terminal.release();
    JsonNode done = client.awaitOperation(id, first, "succeeded", Duration.ofSeconds(10));
    assertTrue(done.path("result").asBoolean());
    client.awaitOperation(id, end.path("id").asText(), "succeeded", Duration.ofSeconds(10));
    assertEquals("ended", client.get("/v1/sessions/" + id).expect(200).text("state"));
    JsonNode listed =
        client.get("/v1/sessions/" + id + "/operations?status=aborted").expect(200).body;
    assertEquals(1, listed.size());
    assertEquals(secondId, listed.get(0).path("id").asText());
  }

  @Test
  void noOperationIsAcceptedOnceAnEndHasBeenAccepted() throws Exception {
    String id = createWithMemberAndItem();
    terminal.hold(MessageCategoryType.INPUT);
    String first =
        submit(id, json("{'type':'requestConfirmation','prompt':'Receipt?'}")).path("id").asText();
    assertTrue(terminal.awaitHeld(Duration.ofSeconds(5)));
    JsonNode end = client.delete("/v1/sessions/" + id).expect(202).body;
    assertEquals("queued", end.path("status").asText());

    // the end is queued behind the running operation: later work must be refused up front, not
    // accepted to fail when it reaches the head of the lane after the session has gone
    client
        .post(
            "/v1/sessions/" + id + "/operations",
            json("{'type':'requestConfirmation','prompt':'Bag?'}"))
        .expect(409);

    terminal.release();
    client.awaitOperation(id, first, "succeeded", Duration.ofSeconds(10));
    client.awaitOperation(id, end.path("id").asText(), "succeeded", Duration.ofSeconds(10));
    assertEquals(2, client.get("/v1/sessions/" + id + "/operations").expect(200).body.size());
  }

  @Test
  void abortDuringAPendingStepAbortsTheSettlement() throws Exception {
    String id = createWithMemberAndItem();
    String operationId = submit(id, SETTLE_WITH_TOTALS).path("id").asText();
    client.awaitOperation(id, operationId, "awaitingReply", Duration.ofSeconds(10));
    JsonNode aborted = client.post("/v1/sessions/" + id + "/abort", "{}").expect(202).body;
    assertEquals(operationId, aborted.path("operation").path("id").asText());
    JsonNode done = client.awaitOperation(id, operationId, "aborted", Duration.ofSeconds(10));
    assertEquals("ABORTED", done.path("error").path("code").asText());
    assertTrue(done.path("pendingStep").isMissingNode());
    assertTrue(client.post("/v1/sessions/" + id + "/abort", "{}").expect(202).body.isEmpty());
  }

  @Test
  void anIdleEndTheSdkRefusesLeavesNoOperationBehind() throws Exception {
    String id = createWithMemberAndItem();
    // the terminal authorises 89.50 of the 90.00 asked and has no REVERSAL scripted, so the
    // unwind fails and the standing charge makes the SDK refuse a plain end()
    String operationId = submit(id, SETTLE_WITH_TOTALS).path("id").asText();
    JsonNode waiting =
        client.awaitOperation(id, operationId, "awaitingReply", Duration.ofSeconds(10));
    client
        .post(
            "/v1/sessions/" + id + "/operations/" + operationId + "/reply",
            json(
                "{'stepId':'"
                    + waiting.path("pendingStep").path("stepId").asText()
                    + "','total':'90.00'}"))
        .expect(200);
    client.awaitOperation(id, operationId, "failed", Duration.ofSeconds(10));

    client.delete("/v1/sessions/" + id).expect(409);

    assertEquals("open", client.get("/v1/sessions/" + id).expect(200).text("state"));
    JsonNode operations = client.get("/v1/sessions/" + id + "/operations").expect(200).body;
    assertTrue(
        StreamSupport.stream(operations.spliterator(), false)
            .noneMatch(o -> o.path("type").asText().equals("end")));
  }

  @Test
  void postingAnOperationTwiceWithOneKeyReplaysTheOperation() throws Exception {
    String id = createWithMemberAndItem();
    terminal.hold(MessageCategoryType.INPUT);
    String body = json("{'type':'requestConfirmation','prompt':'Receipt?'}");
    JsonNode first =
        client.post("/v1/sessions/" + id + "/operations", body, "op-key").expect(202).body;
    JsonNode replay =
        client.post("/v1/sessions/" + id + "/operations", body, "op-key").expect(202).body;
    assertEquals(first.path("id").asText(), replay.path("id").asText());
    JsonNode fresh =
        client.post("/v1/sessions/" + id + "/operations", body, "op-key-2").expect(202).body;
    assertNotEquals(first.path("id").asText(), fresh.path("id").asText());
    assertEquals(2, client.get("/v1/sessions/" + id + "/operations").expect(200).body.size());
    terminal.release();
  }
}
