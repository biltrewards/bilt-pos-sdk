package com.bilt.pos.host;

import static com.bilt.pos.host.HostClient.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bilt.pos.host.internal.Json;
import com.bilt.pos.host.internal.Parsers;
import com.bilt.pos.host.internal.Views;
import com.bilt.pos.nexo.model.AmountsReq;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Split card payments and partial card approvals over the bridge: {@code options.cardPayments}, the
 * {@code PARTIAL_CARD_APPROVAL} step and its decisions, its deadline, and the SDK's automatic
 * follow-ups when the register does not declare the step.
 */
class SplitPaymentSessionTest {

  private static final String POI = "VictaLane-1";

  /** No SDK loyalty steps: the card amount is the basket's $100.00. */
  private static final String NO_LOYALTY = "'loyalty':{'handling':'FORBIDDEN'}";

  private ScriptedTerminalClient terminal;
  private SessionHost host;
  private HostClient client;
  private String sessionId;

  @BeforeEach
  void start() throws Exception {
    terminal =
        new ScriptedTerminalClient()
            .reply(MessageCategoryType.ADMIN, ScriptedTerminalClient.ADMIN_OK)
            .reply(MessageCategoryType.ABORT, ScriptedTerminalClient.ADMIN_OK)
            .reply(MessageCategoryType.REVERSAL, ScriptedTerminalClient.REVERSAL_OK);
    startHost(StepDeadlines.defaults());
  }

  private void startHost(StepDeadlines deadlines) throws Exception {
    host =
        SessionHost.builder()
            .port(0)
            .stepDeadlines(deadlines)
            .terminal(
                TerminalClientProvider.of(
                    terminal, TerminalInfo.of("Lane 1", "VictaLane").withReachable(true)))
            .build();
    host.start();
    client = new HostClient(host.port());
    sessionId =
        client
            .post(
                "/v1/sessions",
                json(
                    "{'kind':'terminal','saleId':'LANE-1','poiId':'"
                        + POI
                        + "','currency':'USD','storeLocation':'STR-1','autoDisplay':false}"))
            .expect(201)
            .text("id");
    client.put("/v1/sessions/" + sessionId + "/member", json("{'id':'98234'}")).expect(200);
    client
        .post(
            "/v1/sessions/" + sessionId + "/basket/items",
            json("{'sku':'SKU-1','description':'Item','unitPrice':'100.00'}"))
        .expect(200);
  }

  @AfterEach
  void stop() {
    terminal.release();
    host.stop();
  }

  private String settle(String optionsJson, String handledStepsJson) throws Exception {
    return settle(optionsJson, handledStepsJson, false);
  }

  private String settle(String optionsJson, String handledStepsJson, boolean reversalNotices)
      throws Exception {
    return client
        .post(
            "/v1/sessions/" + sessionId + "/operations",
            json(
                "{'type':'settle','options':{"
                    + NO_LOYALTY
                    + optionsJson
                    + "},'handledSteps':"
                    + handledStepsJson
                    + (reversalNotices ? ",'reversalNotices':true" : "")
                    + "}"))
        .expect(202)
        .text("id");
  }

  private JsonNode awaitStep(String operationId, String kind) throws Exception {
    JsonNode waiting =
        client.awaitOperation(sessionId, operationId, "awaitingReply", Duration.ofSeconds(10));
    JsonNode step = waiting.path("pendingStep");
    assertEquals(kind, step.path("kind").asText());
    return step;
  }

  private HostClient.Response reply(String operationId, String stepId, String fields)
      throws Exception {
    return client.post(
        "/v1/sessions/" + sessionId + "/operations/" + operationId + "/reply",
        json("{'stepId':'" + stepId + "'," + fields + "}"));
  }

  private JsonNode done(String operationId, String status) throws Exception {
    return client.awaitOperation(sessionId, operationId, status, Duration.ofSeconds(10));
  }

  private List<SaleToPOIRequest> payments() {
    return terminal.requests().stream().filter(r -> r.getPaymentRequest() != null).toList();
  }

  private static AmountsReq amounts(SaleToPOIRequest request) {
    return request.getPaymentRequest().getPaymentTransaction().getAmountsReq();
  }

  private static String money(Double amount) {
    return BigDecimal.valueOf(amount).setScale(2).toPlainString();
  }

  private static String requested(SaleToPOIRequest request) {
    return money(amounts(request).getRequestedAmount());
  }

  private List<String> reversedPoiIds() {
    return terminal.requests().stream()
        .filter(r -> r.getReversalRequest() != null)
        .map(r -> r.getReversalRequest().getOriginalPOITransaction().getPoiTransactionID())
        .map(id -> id.getTransactionID())
        .toList();
  }

  private void expectPartialThenFull(String firstPoi, String firstAuthorized, String secondPoi) {
    terminal.replyInOrder(
        MessageCategoryType.PAYMENT,
        ScriptedTerminalClient.paymentPartial(firstPoi, firstAuthorized),
        ScriptedTerminalClient.paymentOk(secondPoi, "40.00"));
  }

  // ─── cardPayments in the settle request ───

  @Test
  void cardPaymentsReachTheJavaOptionsAndSplitTheCardAmount() throws Exception {
    terminal.replyInOrder(
        MessageCategoryType.PAYMENT,
        ScriptedTerminalClient.paymentOk("POI-PAY-1", "20.00"),
        ScriptedTerminalClient.paymentOk("POI-PAY-2", "80.00"));

    String operationId = settle(",'cardPayments':[{'amount':'20.00'}]", "[]");
    JsonNode result = done(operationId, "succeeded").path("result");

    JsonNode cards = result.path("cardPayments");
    assertEquals(2, cards.size());
    assertEquals("20.00", cards.path(0).path("amount").asText());
    assertEquals("80.00", cards.path(1).path("amount").asText());
    assertEquals("100.00", result.path("cardAmountCharged").asText());

    List<SaleToPOIRequest> sent = payments();
    assertEquals(2, sent.size());
    assertEquals("20.00", requested(sent.get(0)));
    assertEquals("80.00", requested(sent.get(1)));
    assertNull(amounts(sent.get(0)).getPaidAmount());
    assertEquals("20.00", money(amounts(sent.get(1)).getPaidAmount()));
    assertEquals(
        Boolean.TRUE, sent.get(1).getPaymentRequest().getPaymentData().getSplitPaymentFlag());
  }

  @Test
  void beforeStepReportsTheStepAmountOfEachSplitCardPayment() throws Exception {
    terminal.replyInOrder(
        MessageCategoryType.PAYMENT,
        ScriptedTerminalClient.paymentOk("POI-PAY-1", "20.00"),
        ScriptedTerminalClient.paymentOk("POI-PAY-2", "80.00"));

    String operationId = settle(",'cardPayments':[{'amount':'20.00'}]", "['BEFORE_STEP']");
    JsonNode first = awaitStep(operationId, "BEFORE_STEP");
    JsonNode firstContext = first.path("context");
    assertEquals("CARD_CHARGE", firstContext.path("step").asText());
    assertEquals("20.00", firstContext.path("stepAmount").asText());
    assertEquals("100.00", firstContext.path("currentTotal").asText());
    reply(operationId, first.path("stepId").asText(), "'saleTransactionId':''").expect(200);

    JsonNode second = nextStep(operationId, first.path("stepId").asText());
    JsonNode secondContext = second.path("context");
    assertEquals("80.00", secondContext.path("stepAmount").asText());
    assertEquals("80.00", secondContext.path("currentTotal").asText());
    reply(operationId, second.path("stepId").asText(), "'saleTransactionId':''").expect(200);
    done(operationId, "succeeded");
  }

  private JsonNode nextStep(String operationId, String previousStepId) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (System.nanoTime() < deadline) {
      JsonNode step =
          client
              .get("/v1/sessions/" + sessionId + "/operations/" + operationId)
              .expect(200)
              .body
              .path("pendingStep");
      if (!step.isMissingNode() && !previousStepId.equals(step.path("stepId").asText())) {
        return step;
      }
      TimeUnit.MILLISECONDS.sleep(25);
    }
    throw new AssertionError("no step after " + previousStepId);
  }

  @Test
  void plannedCardPaymentsThatAddUpToTheTotalFailWithTheNewCodeBeforeAnyCardIsCharged()
      throws Exception {
    String operationId = settle(",'cardPayments':[{'amount':'60.00'},{'amount':'40.00'}]", "[]");
    JsonNode failed = done(operationId, "failed");
    assertEquals("CARD_PAYMENTS_EXCEED_TOTAL", failed.path("error").path("code").asText());
    assertTrue(payments().isEmpty());
  }

  @Test
  void badCardPaymentsAreRefusedWith400() throws Exception {
    String base = "/v1/sessions/" + sessionId + "/operations";
    for (String options :
        List.of(
            "'cashback':'5.00','cardPayments':[{'amount':'20.00'}]",
            "'cardPayments':[{'amount':'0'}]",
            "'cardPayments':[{'amount':'-1.00'}]",
            "'cardPayments':[{'amount':'20.001'}]",
            "'cardPayments':[{}]",
            "'cardPayments':['20.00']")) {
      client.post(base, json("{'type':'settle','options':{" + options + "}}")).expect(400);
    }
  }

  // ─── PARTIAL_CARD_APPROVAL ───

  @Test
  void aPartialApprovalRaisesTheStepAndAnotherCardChargesTheShortfall() throws Exception {
    expectPartialThenFull("POI-PAY-1", "60.00", "POI-PAY-2");

    String operationId = settle("", "['PARTIAL_CARD_APPROVAL']");
    JsonNode step = awaitStep(operationId, "PARTIAL_CARD_APPROVAL");

    assertEquals("60.00", step.path("approved").asText());
    assertEquals("100.00", step.path("requested").asText());
    assertEquals("40.00", step.path("shortfall").asText());
    assertEquals("40.00", step.path("amountDue").asText());
    assertEquals(1, step.path("cardPayments").size());
    assertEquals("POI-PAY-1", step.path("cardPayments").path(0).path("poiTransactionId").asText());
    assertTrue(step.path("cardPayments").path(0).path("partialApproval").asBoolean());
    assertEquals("CARD_CHARGE", step.path("movement").path("step").asText());
    assertEquals("60.00", step.path("movement").path("amount").asText());
    assertEquals("POI-PAY-1", step.path("movement").path("poiTransactionId").asText());
    assertEquals(
        "ABORT", step.path("default").path("partialApprovalDecision").path("action").asText());
    assertEquals(step.path("stepId").asText(), step.path("default").path("stepId").asText());
    Duration window =
        Duration.between(
            java.time.Instant.now(), java.time.Instant.parse(step.path("deadlineAt").asText()));
    assertTrue(window.compareTo(Duration.ofSeconds(60)) > 0, "two minutes by default: " + window);

    String stepId = step.path("stepId").asText();
    reply(operationId, stepId, "'partialApprovalDecision':{'action':'ANOTHER_CARD'}").expect(200);
    // the reply is idempotent: the step is settled, a second one is refused
    assertEquals(
        "INVALID_STATE",
        reply(operationId, stepId, "'partialApprovalDecision':{'action':'ABORT'}")
            .expect(422)
            .text("code"));

    JsonNode result = done(operationId, "succeeded").path("result");
    JsonNode cards = result.path("cardPayments");
    assertEquals(2, cards.size());
    assertTrue(cards.path(0).path("partialApproval").asBoolean());
    assertFalse(cards.path(1).path("partialApproval").asBoolean());
    assertEquals("100.00", result.path("cardAmountCharged").asText());

    List<SaleToPOIRequest> sent = payments();
    assertEquals("100.00", requested(sent.get(0)));
    assertEquals("40.00", requested(sent.get(1)));
    assertEquals("60.00", money(amounts(sent.get(1)).getPaidAmount()));
    assertEquals(
        Boolean.TRUE, sent.get(1).getPaymentRequest().getPaymentData().getSplitPaymentFlag());
    assertTrue(reversedPoiIds().isEmpty());
  }

  @Test
  void theStepEventAndTheMovementEventGoOutInOrder() throws Exception {
    expectPartialThenFull("POI-PAY-1", "60.00", "POI-PAY-2");
    String operationId = settle("", "['PARTIAL_CARD_APPROVAL']");
    JsonNode step = awaitStep(operationId, "PARTIAL_CARD_APPROVAL");
    reply(
            operationId,
            step.path("stepId").asText(),
            "'partialApprovalDecision':{'action':'ANOTHER_CARD'}")
        .expect(200);
    done(operationId, "succeeded");

    List<JsonNode> events =
        client.sseUntil(
            sessionId,
            0,
            e ->
                e.path("type").asText().equals("operation.completed")
                    && e.path("payload").path("id").asText().equals(operationId),
            Duration.ofSeconds(10));
    List<String> flow =
        events.stream()
            .map(
                e ->
                    e.path("type").asText().equals("operation.step")
                        ? "step " + e.path("payload").path("kind").asText()
                        : e.path("type").asText().equals("operation.movement")
                            ? "movement "
                                + e.path("payload").path("movement").path("amount").asText()
                            : "")
            .filter(s -> !s.isEmpty())
            .toList();
    assertEquals(List.of("movement 60.00", "step PARTIAL_CARD_APPROVAL", "movement 40.00"), flow);
  }

  @Test
  void addToNextAddsTheShortfallToTheRemainderPayment() throws Exception {
    // $50.00 planned, then the remainder; the first card approves only $30.00
    terminal.replyInOrder(
        MessageCategoryType.PAYMENT,
        ScriptedTerminalClient.paymentPartial("POI-PAY-1", "30.00"),
        ScriptedTerminalClient.paymentOk("POI-PAY-2", "70.00"));
    String operationId =
        settle(",'cardPayments':[{'amount':'50.00'}]", "['PARTIAL_CARD_APPROVAL']");
    JsonNode step = awaitStep(operationId, "PARTIAL_CARD_APPROVAL");
    assertEquals("50.00", step.path("requested").asText());
    assertEquals("20.00", step.path("shortfall").asText());
    assertEquals("70.00", step.path("amountDue").asText());
    reply(
            operationId,
            step.path("stepId").asText(),
            "'partialApprovalDecision':{'action':'ADD_TO_NEXT'}")
        .expect(200);

    JsonNode result = done(operationId, "succeeded").path("result");
    assertEquals(2, result.path("cardPayments").size());
    assertEquals("100.00", result.path("cardAmountCharged").asText());
    assertEquals("70.00", requested(payments().get(1)));
  }

  @Test
  void addToNextWithNoPaymentLeftFailsTheSettlementAsInvalidState() throws Exception {
    terminal.reply(
        MessageCategoryType.PAYMENT, ScriptedTerminalClient.paymentPartial("POI-PAY-1", "60.00"));
    String operationId = settle("", "['PARTIAL_CARD_APPROVAL']");
    JsonNode step = awaitStep(operationId, "PARTIAL_CARD_APPROVAL");
    reply(
            operationId,
            step.path("stepId").asText(),
            "'partialApprovalDecision':{'action':'ADD_TO_NEXT'}")
        .expect(200);

    JsonNode failed = done(operationId, "failed");
    assertEquals("INVALID_STATE", failed.path("error").path("code").asText());
    // a refused decision leaves the committed steps standing for voidTransaction
    assertTrue(reversedPoiIds().isEmpty());
  }

  @Test
  void externalRecordsTheRestAsAnExternalPaymentAndKeepsTheCardPayment() throws Exception {
    terminal.reply(
        MessageCategoryType.PAYMENT, ScriptedTerminalClient.paymentPartial("POI-PAY-1", "60.00"));
    String operationId = settle("", "['PARTIAL_CARD_APPROVAL']");
    JsonNode step = awaitStep(operationId, "PARTIAL_CARD_APPROVAL");
    reply(
            operationId,
            step.path("stepId").asText(),
            "'partialApprovalDecision':{'action':'EXTERNAL','externalPayment':"
                + "{'tenderType':'CASH','amount':'40.00','reference':'DRAWER-1'}}")
        .expect(200);

    JsonNode result = done(operationId, "succeeded").path("result");
    assertEquals("60.00", result.path("cardAmountCharged").asText());
    assertEquals("40.00", result.path("externalPaymentAmount").asText());
    assertEquals(1, result.path("cardPayments").size());
    assertEquals(1, payments().size());
    assertTrue(reversedPoiIds().isEmpty());
    List<String> steps = result.path("movements").findValuesAsText("step").stream().toList();
    assertTrue(steps.contains("EXTERNAL_PAYMENT"), steps.toString());
  }

  @Test
  void externalForTheWrongAmountFailsTheSettlementAsInvalidState() throws Exception {
    terminal.reply(
        MessageCategoryType.PAYMENT, ScriptedTerminalClient.paymentPartial("POI-PAY-1", "60.00"));
    String operationId = settle("", "['PARTIAL_CARD_APPROVAL']");
    JsonNode step = awaitStep(operationId, "PARTIAL_CARD_APPROVAL");
    reply(
            operationId,
            step.path("stepId").asText(),
            "'partialApprovalDecision':{'action':'EXTERNAL','externalPayment':"
                + "{'tenderType':'CASH','amount':'10.00'}}")
        .expect(200);
    assertEquals("INVALID_STATE", done(operationId, "failed").path("error").path("code").asText());
  }

  @Test
  void abortReversesEveryCardPaymentAndFailsAsDeclined() throws Exception {
    terminal.reply(
        MessageCategoryType.PAYMENT, ScriptedTerminalClient.paymentPartial("POI-PAY-1", "60.00"));
    String operationId = settle("", "['PARTIAL_CARD_APPROVAL']");
    JsonNode step = awaitStep(operationId, "PARTIAL_CARD_APPROVAL");
    reply(operationId, step.path("stepId").asText(), "'partialApprovalDecision':{'action':'ABORT'}")
        .expect(200);

    assertEquals("DECLINED", done(operationId, "failed").path("error").path("code").asText());
    assertEquals(List.of("POI-PAY-1"), reversedPoiIds());
  }

  @Test
  void anAbortedPartialApprovalSendsAReversalNoticeForTheCardPayment() throws Exception {
    terminal.reply(
        MessageCategoryType.PAYMENT, ScriptedTerminalClient.paymentPartial("POI-PAY-1", "60.00"));
    String operationId = settle("", "['PARTIAL_CARD_APPROVAL']", true);
    JsonNode step = awaitStep(operationId, "PARTIAL_CARD_APPROVAL");
    reply(operationId, step.path("stepId").asText(), "'partialApprovalDecision':{'action':'ABORT'}")
        .expect(200);
    done(operationId, "failed");

    List<JsonNode> movements =
        client
            .sseUntil(
                sessionId,
                0,
                e ->
                    e.path("type").asText().equals("operation.completed")
                        && e.path("payload").path("id").asText().equals(operationId),
                Duration.ofSeconds(10))
            .stream()
            .filter(e -> e.path("type").asText().equals("operation.movement"))
            .map(e -> e.path("payload").path("movement"))
            .toList();
    assertEquals(2, movements.size());
    JsonNode charge = movements.get(0);
    assertTrue(charge.path("reversal").isMissingNode(), "a charge is not a notice");
    JsonNode notice = movements.get(1);
    assertTrue(notice.path("reversal").asBoolean());
    assertEquals("CARD_CHARGE", notice.path("step").asText());
    assertEquals("60.00", notice.path("amount").asText());
    // the notice's references are the reversal request's own
    assertEquals(charge.path("saleTransactionId"), notice.path("saleTransactionId"));
    assertEquals("POI-PAY-1", notice.path("reversedMovement").path("poiTransactionId").asText());
    assertEquals(charge.path("serviceId"), notice.path("reversedMovement").path("serviceId"));
    assertTrue(notice.path("reversedMovement").path("reversal").isMissingNode());
  }

  @Test
  void withoutReversalNoticesRequestedTheAbortSendsNoNotice() throws Exception {
    terminal.reply(
        MessageCategoryType.PAYMENT, ScriptedTerminalClient.paymentPartial("POI-PAY-1", "60.00"));
    String operationId = settle("", "['PARTIAL_CARD_APPROVAL']");
    JsonNode step = awaitStep(operationId, "PARTIAL_CARD_APPROVAL");
    reply(operationId, step.path("stepId").asText(), "'partialApprovalDecision':{'action':'ABORT'}")
        .expect(200);
    done(operationId, "failed");
    assertEquals(List.of("POI-PAY-1"), reversedPoiIds());

    List<JsonNode> movements =
        client
            .sseUntil(
                sessionId,
                0,
                e ->
                    e.path("type").asText().equals("operation.completed")
                        && e.path("payload").path("id").asText().equals(operationId),
                Duration.ofSeconds(10))
            .stream()
            .filter(e -> e.path("type").asText().equals("operation.movement"))
            .map(e -> e.path("payload").path("movement"))
            .toList();
    // a client that did not ask would read a notice as another charge
    assertEquals(1, movements.size());
    assertTrue(movements.get(0).path("reversal").isMissingNode());
  }

  @Test
  void aMalformedDecisionIsRefusedAndTheStepStaysPending() throws Exception {
    terminal.reply(
        MessageCategoryType.PAYMENT, ScriptedTerminalClient.paymentPartial("POI-PAY-1", "60.00"));
    String operationId = settle("", "['PARTIAL_CARD_APPROVAL']");
    JsonNode step = awaitStep(operationId, "PARTIAL_CARD_APPROVAL");
    String stepId = step.path("stepId").asText();

    reply(operationId, stepId, "'total':'1.00'").expect(400);
    reply(operationId, stepId, "'partialApprovalDecision':{'action':'RETRY'}").expect(400);
    reply(operationId, stepId, "'partialApprovalDecision':{'action':'EXTERNAL'}").expect(400);
    awaitStep(operationId, "PARTIAL_CARD_APPROVAL");

    reply(operationId, stepId, "'partialApprovalDecision':{'action':'ABORT'}").expect(200);
    done(operationId, "failed");
  }

  @Test
  void anAbortDuringThePendingStepUnwindsTheCardPayment() throws Exception {
    terminal.reply(
        MessageCategoryType.PAYMENT, ScriptedTerminalClient.paymentPartial("POI-PAY-1", "60.00"));
    String operationId = settle("", "['PARTIAL_CARD_APPROVAL']");
    awaitStep(operationId, "PARTIAL_CARD_APPROVAL");
    client.post("/v1/sessions/" + sessionId + "/abort", "{}").expect(202);
    done(operationId, "failed");
    assertEquals(List.of("POI-PAY-1"), reversedPoiIds());
  }

  @Test
  void anAbortThatLandsBeforeTheStepIsPendingReleasesTheStepAtOnce() throws Exception {
    terminal.reply(
        MessageCategoryType.PAYMENT, ScriptedTerminalClient.paymentPartial("POI-PAY-1", "60.00"));
    terminal.hold(MessageCategoryType.PAYMENT);
    String operationId = settle("", "['PARTIAL_CARD_APPROVAL']");
    assertTrue(terminal.awaitHeld(Duration.ofSeconds(10)));
    // the abort finds no pending step: the card request is still in flight
    client.post("/v1/sessions/" + sessionId + "/abort", "{}").expect(202);
    terminal.release();

    // not the two minutes of the step's deadline
    JsonNode done = client.awaitOperation(sessionId, operationId, "failed", Duration.ofSeconds(10));
    // the step is answered ABORT, which fails the sale as DECLINED after reversing the card
    assertEquals("DECLINED", done.path("error").path("code").asText());
    assertEquals(List.of("POI-PAY-1"), reversedPoiIds());
  }

  @Test
  void theStepDeadlineAppliesTheAbortDefault() throws Exception {
    host.stop();
    startHost(StepDeadlines.defaults().withPartialApproval(Duration.ofMillis(300)));
    terminal.reply(
        MessageCategoryType.PAYMENT, ScriptedTerminalClient.paymentPartial("POI-PAY-1", "60.00"));

    String operationId = settle("", "['PARTIAL_CARD_APPROVAL']");
    awaitStep(operationId, "PARTIAL_CARD_APPROVAL");
    assertEquals("DECLINED", done(operationId, "failed").path("error").path("code").asText());
    assertEquals(List.of("POI-PAY-1"), reversedPoiIds());
  }

  @Test
  void theOptionsViewOfAnAbandonedRecordListsThePlannedCardPayments() throws Exception {
    JsonNode request =
        Json.MAPPER.readTree(json("{'cardPayments':[{'amount':'20.00'},{'amount':'5.50'}]}"));
    JsonNode view = Views.settlementOptions(Parsers.settlementOptions(request));
    assertEquals(List.of("20.00", "5.50"), view.path("cardPayments").findValuesAsText("amount"));
    assertTrue(
        Views.settlementOptions(Parsers.settlementOptions(Json.MAPPER.readTree("{}")))
            .path("cardPayments")
            .isMissingNode());
  }

  @Test
  void theDeadlineIsItsOwnSetting() {
    assertEquals(Duration.ofSeconds(120), StepDeadlines.defaults().partialApproval());
    assertEquals(
        Duration.ofSeconds(5),
        StepDeadlines.defaults().withPartialApproval(Duration.ofSeconds(5)).partialApproval());
    assertEquals(Duration.ofSeconds(120), StepDeadlines.defaults().recovery());
    assertEquals(
        Duration.ofMillis(7), StepDeadlines.uniform(Duration.ofMillis(7)).partialApproval());
  }

  // ─── no handler declared ───

  @Test
  void withTheStepUnhandledTheSdkChargesTheShortfallOnAnotherCardByItself() throws Exception {
    expectPartialThenFull("POI-PAY-1", "60.00", "POI-PAY-2");

    String operationId = settle("", "['RECOVERY_REQUIRED']");
    JsonNode result = done(operationId, "succeeded").path("result");

    assertEquals(2, result.path("cardPayments").size());
    assertEquals("100.00", result.path("cardAmountCharged").asText());
    assertEquals(2, payments().size());
    assertEquals(
        Boolean.TRUE, payments().get(1).getPaymentRequest().getPaymentData().getSplitPaymentFlag());
    List<JsonNode> events =
        client.sseUntil(
            sessionId,
            0,
            e -> e.path("type").asText().equals("operation.completed"),
            Duration.ofSeconds(10));
    assertTrue(
        events.stream().noneMatch(e -> e.path("type").asText().equals("operation.step")),
        "no step is raised for a kind the request did not declare");
  }

  @Test
  void withTheStepUnhandledTheAutomaticFollowUpsStopAtThreeCardRequests() throws Exception {
    terminal.replyInOrder(
        MessageCategoryType.PAYMENT,
        ScriptedTerminalClient.paymentPartial("POI-PAY-1", "30.00"),
        ScriptedTerminalClient.paymentPartial("POI-PAY-2", "30.00"),
        ScriptedTerminalClient.paymentPartial("POI-PAY-3", "20.00"));

    String operationId = settle("", "[]");
    JsonNode failed = done(operationId, "failed");

    assertEquals("DECLINED", failed.path("error").path("code").asText());
    assertEquals(3, payments().size());
    assertEquals(List.of("POI-PAY-3", "POI-PAY-2", "POI-PAY-1"), reversedPoiIds());
  }
}
