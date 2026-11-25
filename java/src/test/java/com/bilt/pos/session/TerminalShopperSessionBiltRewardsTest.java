package com.bilt.pos.session;

import static org.junit.jupiter.api.Assertions.*;

import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.AmountsReq;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.PaymentData;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.payment.CardPaymentResult;
import com.bilt.pos.session.settlement.BiltRewardsPaymentRecord;
import com.bilt.pos.session.settlement.CardPaymentRecord;
import com.bilt.pos.session.settlement.OriginalSaleRecord;
import com.bilt.pos.session.settlement.PartialApprovalDecision;
import com.bilt.pos.session.settlement.PartialCardApproval;
import com.bilt.pos.session.settlement.RefundAllocation;
import com.bilt.pos.session.settlement.SettlementFailure;
import com.bilt.pos.session.settlement.SettlementMovement;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.bilt.pos.session.settlement.SettlementRecovery;
import com.bilt.pos.session.settlement.SettlementResult;
import com.bilt.pos.session.settlement.SettlementStep;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Bilt rewards spent inside a payment (implicit loyalty): the terminal's {@code biltRewardsAmount}
 * in a payment, reversal or refund response's {@code AdditionalResponse}.
 */
class TerminalShopperSessionBiltRewardsTest {

  private final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private MockWebServer server;
  private TerminalShopperSession session;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    session = start(sessionBuilder());
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  private TerminalShopperSession.Builder sessionBuilder() {
    return TerminalShopperSession.builder()
        .client(
            BiltNexoTerminalClient.builder()
                .endpoint(server.url("/nexo").toString())
                .disableRecoveryOnNetworkError()
                .build())
        .saleId("POS-LANE-3")
        .poiId("VictaLane-275839164")
        .currency("USD")
        .autoDisplay(false);
  }

  private TerminalShopperSession start(TerminalShopperSession.Builder builder) throws Exception {
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.ADMIN_OK));
    TerminalShopperSession started = builder.start().get();
    server.takeRequest(5, TimeUnit.SECONDS);
    return started;
  }

  private void addHundredDollarItem() {
    session.basket().addItem(BasketItem.sale("SKU-1", "Item", 1, new BigDecimal("100.00")));
  }

  // ─── Terminal replies ───

  /**
   * The terminal's points leg with multi-tender on: a Partial for the rewards value, the
   * merchant-payment-svc attempt as its POI reference, no card, acquirer data or receipt.
   */
  private static String rewardsPartial(String attempt, String rewards) {
    return "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
        + "\"Response\":{\"Result\":\"Partial\",\"AdditionalResponse\":\"biltRewardsAmount="
        + rewards
        + "\"},"
        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\""
        + attempt
        + "\",\"TimeStamp\":\"2026-11-25T10:00:01Z\"}},"
        + "\"PaymentResult\":{\"AmountsResp\":{\"Currency\":\"USD\",\"AuthorizedAmount\":"
        + rewards
        + "}}}}}";
  }

  /** A card payment, with {@code additionalResponse} (may be empty) in its Response. */
  private static String card(
      String result, String poiTxn, double authorized, String additionalResponse) {
    return "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
        + cardBody(result, poiTxn, authorized, additionalResponse)
        + "}}}";
  }

  private static String cardBody(
      String result, String poiTxn, double authorized, String additionalResponse) {
    return "\"Response\":{\"Result\":\""
        + result
        + "\""
        + (additionalResponse.isEmpty()
            ? ""
            : ",\"AdditionalResponse\":\"" + additionalResponse + "\"")
        + "},"
        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\""
        + poiTxn
        + "\",\"TimeStamp\":\"2026-11-25T10:00:03Z\"}},"
        + "\"PaymentResult\":{"
        + "\"AmountsResp\":{\"Currency\":\"USD\",\"AuthorizedAmount\":"
        + authorized
        + "},"
        + "\"PaymentAcquirerData\":{\"ApprovalCode\":\"A-"
        + poiTxn
        + "\"},"
        + "\"PaymentInstrumentData\":{\"CardData\":{\"PaymentBrand\":\"Visa\"}}}";
  }

  private static String cardOk(String poiTxn, double authorized) {
    return card("Success", poiTxn, authorized, "");
  }

  private static String transactionStatus(String paymentBody) {
    return "{\"SaleToPOIResponse\":{\"TransactionStatusResponse\":{"
        + "\"Response\":{\"Result\":\"Success\"},"
        + "\"RepeatedMessageResponse\":{"
        + "\"MessageHeader\":{\"MessageCategory\":\"Payment\"},"
        + "\"RepeatedResponseMessageBody\":{\"PaymentResponse\":{"
        + paymentBody
        + "}}}}}}";
  }

  private static String reversalOk(String poiTxn, double reversed) {
    return "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
        + "\"Response\":{\"Result\":\"Success\"},"
        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\""
        + poiTxn
        + "\",\"TimeStamp\":\"2026-11-25T11:00:00Z\"}},"
        + "\"ReversedAmount\":"
        + reversed
        + "}}}";
  }

  private static String refundOk(String poiTxn, double authorized, String additionalResponse) {
    return "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
        + "\"Response\":{\"Result\":\"Success\""
        + (additionalResponse.isEmpty()
            ? ""
            : ",\"AdditionalResponse\":\"" + additionalResponse + "\"")
        + "},"
        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\""
        + poiTxn
        + "\",\"TimeStamp\":\"2026-11-25T11:00:00Z\"}},"
        + "\"PaymentResult\":{\"AmountsResp\":{\"Currency\":\"USD\","
        + "\"AuthorizedAmount\":"
        + authorized
        + "}}}}}";
  }

  private void enqueue(String... bodies) {
    for (String body : bodies) {
      server.enqueue(new MockResponse().setBody(body));
    }
  }

  private List<SaleToPOIRequest> drainRequests() throws Exception {
    List<SaleToPOIRequest> requests = new ArrayList<>();
    RecordedRequest recorded;
    while ((recorded = server.takeRequest(200, TimeUnit.MILLISECONDS)) != null) {
      requests.add(
          mapper
              .readValue(recorded.getBody().readUtf8(), NexoTerminalAPI.class)
              .getSaleToPOIRequest());
    }
    return requests;
  }

  private static List<SaleToPOIRequest> payments(List<SaleToPOIRequest> requests) {
    return requests.stream()
        .filter(request -> request.getPaymentRequest() != null)
        .collect(Collectors.toList());
  }

  private static AmountsReq amounts(SaleToPOIRequest request) {
    return request.getPaymentRequest().getPaymentTransaction().getAmountsReq();
  }

  private static List<String> reversed(List<SaleToPOIRequest> requests) {
    return requests.stream()
        .filter(request -> request.getReversalRequest() != null)
        .map(
            request ->
                request
                    .getReversalRequest()
                    .getOriginalPOITransaction()
                    .getPoiTransactionID()
                    .getTransactionID())
        .collect(Collectors.toList());
  }

  private static List<String> refunded(List<SaleToPOIRequest> requests) {
    return payments(requests).stream()
        .map(
            request ->
                request
                    .getPaymentRequest()
                    .getPaymentTransaction()
                    .getOriginalPOITransaction()
                    .getPoiTransactionID()
                    .getTransactionID())
        .collect(Collectors.toList());
  }

  private static void assertMoney(String expected, BigDecimal actual) {
    assertNotNull(actual, "expected " + expected);
    assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected);
  }

  private static void assertMoney(String expected, Double actual) {
    assertNotNull(actual, "expected " + expected);
    assertEquals(new BigDecimal(expected).doubleValue(), actual, 0.0001);
  }

  private static void assertTender(
      TenderAmount leg, TenderType tender, String poiTxn, String amount) {
    assertEquals(tender, leg.getTender());
    assertEquals(poiTxn, leg.getPoiTransactionId());
    assertMoney(amount, leg.getAmount());
  }

  /** A $100 sale whose points leg paid $40 and a card the remaining $60. */
  private SettlementResult settleRewardsThenCard() {
    addHundredDollarItem();
    enqueue(rewardsPartial("ATT-1", "40.00"), cardOk("POI-PAY-1", 60.00));
    return session.settle().get();
  }

  // ─── A rewards-only Partial, then a card ───

  @Test
  void aRewardsPartialIsNotACardPaymentAndTheCardPaysTheRest() throws Exception {
    List<SettlementMovement> charged = new ArrayList<>();
    addHundredDollarItem();
    enqueue(rewardsPartial("ATT-1", "40.00"), cardOk("POI-PAY-1", 60.00));

    SettlementResult result = session.settle().onCardCharged(charged::add).get();

    assertEquals(1, result.getCardPayments().size(), "the card list holds only the card");
    CardPaymentResult card = result.getCardPayments().get(0);
    assertEquals("POI-PAY-1", card.getPoiTransactionId());
    assertMoney("60.00", card.getAmount());
    assertMoney("0", card.getBiltRewardsAmount());
    assertMoney("60.00", card.getCardAmount());
    assertEquals(1, result.getBiltRewardsPayments().size());
    assertEquals("ATT-1", result.getBiltRewardsPayments().get(0).getPoiTransactionId());
    assertMoney("40.00", result.getBiltRewardsPayments().get(0).getAmount());
    assertMoney("40.00", result.getBiltRewardsAmount());
    assertMoney("60.00", result.getCardAmountCharged());
    assertMoney("100.00", result.getAuthorizedAmount());
    assertMoney("60.00", result.getFinalBasket().getCardPaymentTotal());
    assertMoney("40.00", result.getFinalBasket().getBiltRewardsTotal());
    assertMoney("100.00", result.getFinalBasket().getGrandTotal());
    assertEquals("POI-PAY-1", result.getPoiTransactionId(), "the card's, not the rewards'");
    assertEquals("A-POI-PAY-1", result.getApprovalCode());

    // onCardCharged fires for both; the movement tells them apart
    assertEquals(2, charged.size());
    assertMoney("40.00", charged.get(0).getBiltRewardsAmount());
    assertNull(charged.get(1).getBiltRewardsAmount());

    List<SaleToPOIRequest> requests = payments(drainRequests());
    assertEquals(2, requests.size());
    assertMoney("100.00", amounts(requests.get(0)).getRequestedAmount());
    assertMoney("60.00", amounts(requests.get(1)).getRequestedAmount());
    assertMoney("40.00", amounts(requests.get(1)).getPaidAmount());
    PaymentData data = requests.get(1).getPaymentRequest().getPaymentData();
    assertEquals(Boolean.TRUE, data.getSplitPaymentFlag());
    assertEquals(
        requests.get(0).getPaymentRequest().getSaleData().getSaleTransactionID().getTransactionID(),
        requests.get(1).getPaymentRequest().getSaleData().getSaleTransactionID().getTransactionID(),
        "the card continues the rewards leg's sale");

    OriginalSaleRecord record = result.toOriginalSaleRecord(null);
    assertEquals(1, record.getCardPayments().size());
    assertEquals("POI-PAY-1", record.getCardPoiTransactionId());
    assertEquals(
        List.of(
            BiltRewardsPaymentRecord.builder()
                .poiTransactionId("ATT-1")
                .poiTransactionTimestamp(Instant.parse("2026-11-25T10:00:01Z"))
                .amount(new BigDecimal("40.00"))
                .build()),
        record.getBiltRewardsPayments());
  }

  @Test
  void voidReversesTheCardThenTheRewardsAndBreaksTheTotalDownByTender() throws Exception {
    settleRewardsThenCard();
    drainRequests();
    enqueue(reversalOk("POI-REV-1", 60.00), reversalOk("POI-REV-2", 40.00));

    VoidResult result = session.voidTransaction().get();

    assertEquals(List.of("POI-PAY-1", "ATT-1"), reversed(drainRequests()));
    assertEquals(1, result.getCardReversals().size());
    assertEquals("POI-PAY-1", result.getCardReversals().get(0).getOriginalPoiTransactionId());
    assertEquals(1, result.getBiltRewardsReversals().size());
    TenderMovement rewards = result.getBiltRewardsReversals().get(0);
    assertEquals(TenderType.BILT_REWARDS, rewards.getTenderType());
    assertEquals("ATT-1", rewards.getOriginalPoiTransactionId());
    assertMoney("40.00", rewards.getAmount());
    assertEquals(2, result.getReversedAmountByTender().size());
    assertTender(result.getReversedAmountByTender().get(0), TenderType.CARD, "POI-PAY-1", "60.00");
    assertTender(
        result.getReversedAmountByTender().get(1), TenderType.BILT_REWARDS, "ATT-1", "40.00");
    assertMoney("100.00", result.getReversedAmount());
  }

  @Test
  void aVoidThatFailsOnTheRewardsNamesThemAndResumesThere() throws Exception {
    settleRewardsThenCard();
    drainRequests();
    enqueue(
        reversalOk("POI-REV-1", 60.00),
        "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
            + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"UnreachableHost\"}}}}");
    List<ReversalStep> failedSteps = new ArrayList<>();

    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .voidTransaction()
                    .onError(
                        (step, error) -> {
                          failedSteps.add(step);
                          return ReversalDecision.ABORT;
                        })
                    .get());

    assertEquals(List.of(ReversalStep.BILT_REWARDS), failedSteps);
    assertTrue(
        failure.getError().getMessage().contains("the Bilt rewards payment ATT-1"),
        failure.getError().getMessage());
    drainRequests();

    enqueue(reversalOk("POI-REV-2", 40.00));
    VoidResult retry = session.voidTransaction().get();
    assertEquals(List.of("ATT-1"), reversed(drainRequests()));
    assertTrue(retry.getCardReversals().isEmpty());
    assertEquals(1, retry.getBiltRewardsReversals().size());
  }

  @Test
  void fullRefundReturnsTheCardThenTheRewards() throws Exception {
    settleRewardsThenCard();
    drainRequests();
    enqueue(refundOk("POI-REF-1", 60.00, ""), refundOk("POI-REF-2", 40.00, ""));

    RefundResult result = session.refund().get();

    assertEquals(List.of("POI-PAY-1", "ATT-1"), refunded(drainRequests()));
    assertEquals(1, result.getCardRefunds().size());
    assertEquals(1, result.getBiltRewardsRefunds().size());
    assertEquals(TenderType.BILT_REWARDS, result.getBiltRewardsRefunds().get(0).getTenderType());
    assertTender(result.getRefundedAmountByTender().get(0), TenderType.CARD, "POI-PAY-1", "60.00");
    assertTender(
        result.getRefundedAmountByTender().get(1), TenderType.BILT_REWARDS, "ATT-1", "40.00");
    assertMoney("100.00", result.getRefundedAmount());
  }

  @Test
  void refundOfAnAmountCannotChooseBetweenTheCardAndTheRewards() {
    settleRewardsThenCard();

    SessionException refused =
        assertThrows(SessionException.class, () -> session.refund(new BigDecimal("10.00")).get());

    assertEquals(SessionErrorCode.INVALID_STATE, refused.getError().getCode());
    assertTrue(refused.getError().getMessage().contains("card and Bilt rewards payments"));
  }

  @Test
  void theRegistersHandlerHearsThePartialWasRewards() throws Exception {
    addHundredDollarItem();
    enqueue(rewardsPartial("ATT-1", "40.00"), cardOk("POI-PAY-1", 60.00));
    List<PartialCardApproval> asked = new ArrayList<>();

    SettlementResult result =
        session
            .settle()
            .onPartialCardApproval(
                partial -> {
                  asked.add(partial);
                  return PartialApprovalDecision.anotherCard();
                })
            .get();

    assertEquals(1, asked.size());
    PartialCardApproval partial = asked.get(0);
    assertTrue(partial.isBiltRewards());
    assertEquals("ATT-1", partial.getBiltRewardsPayment().getPoiTransactionId());
    assertMoney("40.00", partial.getBiltRewardsAmount());
    assertMoney("40.00", partial.getApproved());
    assertMoney("60.00", partial.getShortfall());
    assertTrue(partial.getCardPayments().isEmpty(), "a rewards payment is not a card payment");
    assertEquals(1, result.getCardPayments().size());
    assertMoney("40.00", amounts(payments(drainRequests()).get(1)).getPaidAmount());
  }

  @Test
  void aCardPartialAfterTheRewardsIsNotRewards() throws Exception {
    addHundredDollarItem();
    enqueue(
        rewardsPartial("ATT-1", "40.00"),
        card("Partial", "POI-PAY-1", 25.00, ""),
        cardOk("POI-PAY-2", 35.00));
    List<PartialCardApproval> asked = new ArrayList<>();

    session
        .settle()
        .onPartialCardApproval(
            partial -> {
              asked.add(partial);
              return PartialApprovalDecision.anotherCard();
            })
        .get();

    assertEquals(2, asked.size());
    assertFalse(asked.get(1).isBiltRewards());
    assertNull(asked.get(1).getBiltRewardsPayment());
    assertMoney("0", asked.get(1).getBiltRewardsAmount());
    assertEquals(1, asked.get(1).getCardPayments().size());
    List<SaleToPOIRequest> requests = payments(drainRequests());
    assertMoney("65.00", amounts(requests.get(2)).getPaidAmount(), "rewards plus the first card");
  }

  private static void assertMoney(String expected, Double actual, String message) {
    assertNotNull(actual, message);
    assertEquals(new BigDecimal(expected).doubleValue(), actual, 0.0001, message);
  }

  @Test
  void theRewardsPartialCountsTowardTheAutomaticCardRequests() throws Exception {
    addHundredDollarItem();
    // the rewards leg, then two card partials: the third request is the last one allowed
    enqueue(
        rewardsPartial("ATT-1", "40.00"),
        card("Partial", "POI-PAY-1", 25.00, ""),
        card("Partial", "POI-PAY-2", 15.00, ""),
        reversalOk("POI-REV-3", 15.00),
        reversalOk("POI-REV-2", 25.00),
        reversalOk("POI-REV-1", 40.00));
    List<SettlementFailure> failures = new ArrayList<>();

    assertThrows(
        SessionException.class,
        () ->
            session
                .settle()
                .onError(
                    failure -> {
                      failures.add(failure);
                      return SettlementRecovery.abort();
                    })
                .get());

    assertEquals(1, failures.size());
    assertEquals(SettlementStep.CARD_CHARGE, failures.get(0).getStep());
    assertEquals(SessionErrorCode.DECLINED, failures.get(0).getError().getCode());
    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(
        SettlementFlow.MAX_AUTOMATIC_CARD_REQUESTS,
        payments(requests).size(),
        "the rewards leg is one of the automatic requests");
    assertEquals(List.of("POI-PAY-2", "POI-PAY-1", "ATT-1"), reversed(requests));
  }

  // ─── A mixed payment (multi-tender off) ───

  @Test
  void aMixedPaymentSplitsItsCardAndRewardsParts() throws Exception {
    addHundredDollarItem();
    enqueue(card("Success", "POI-PAY-1", 100.00, "biltRewardsAmount=25.00"));

    SettlementResult result = session.settle().get();

    assertEquals(1, result.getCardPayments().size());
    CardPaymentResult card = result.getCardPayments().get(0);
    assertMoney("100.00", card.getAmount());
    assertMoney("25.00", card.getBiltRewardsAmount());
    assertMoney("75.00", card.getCardAmount());
    assertTrue(result.getBiltRewardsPayments().isEmpty());
    assertMoney("75.00", result.getCardAmountCharged());
    assertMoney("25.00", result.getBiltRewardsAmount());
    assertMoney("100.00", result.getAuthorizedAmount());
    assertMoney("75.00", result.getFinalBasket().getCardPaymentTotal());
    assertMoney("25.00", result.getFinalBasket().getBiltRewardsTotal());
    assertMoney("25.00", result.getMovements().get(0).getBiltRewardsAmount());
    CardPaymentRecord record = result.toOriginalSaleRecord(null).getCardPayments().get(0);
    assertMoney("25.00", record.getBiltRewardsAmount());
  }

  @Test
  void aMixedPaymentsVoidSplitsTheReversalByTender() throws Exception {
    addHundredDollarItem();
    enqueue(card("Success", "POI-PAY-1", 100.00, "biltRewardsAmount=25.00"));
    session.settle().get();
    drainRequests();
    enqueue(reversalOk("POI-REV-1", 100.00));

    VoidResult result = session.voidTransaction().get();

    assertEquals(1, result.getCardReversals().size());
    assertMoney("100.00", result.getCardReversals().get(0).getAmount());
    assertMoney("25.00", result.getCardReversals().get(0).getBiltRewardsAmount());
    assertTrue(result.getBiltRewardsReversals().isEmpty());
    assertEquals(2, result.getReversedAmountByTender().size());
    assertTender(result.getReversedAmountByTender().get(0), TenderType.CARD, "POI-PAY-1", "75.00");
    assertTender(
        result.getReversedAmountByTender().get(1), TenderType.BILT_REWARDS, "POI-PAY-1", "25.00");
    assertMoney("100.00", result.getReversedAmount());
  }

  @Test
  void aMixedPaymentsFullRefundSplitsByTheRecord() throws Exception {
    addHundredDollarItem();
    enqueue(card("Success", "POI-PAY-1", 100.00, "biltRewardsAmount=25.00"));
    session.settle().get();
    drainRequests();
    enqueue(refundOk("POI-REF-1", 100.00, ""));

    RefundResult result = session.refund().get();

    assertTender(result.getRefundedAmountByTender().get(0), TenderType.CARD, "POI-PAY-1", "75.00");
    assertTender(
        result.getRefundedAmountByTender().get(1), TenderType.BILT_REWARDS, "POI-PAY-1", "25.00");
    assertMoney("25.00", result.getCardRefunds().get(0).getBiltRewardsAmount());
  }

  @Test
  void aMixedPaymentsPartialRefundSplitsOnlyByWhatTheTerminalReports() throws Exception {
    addHundredDollarItem();
    enqueue(card("Success", "POI-PAY-1", 100.00, "biltRewardsAmount=25.00"));
    session.settle().get();
    drainRequests();
    enqueue(
        refundOk("POI-REF-1", 10.00, ""), refundOk("POI-REF-2", 10.00, "biltRewardsAmount=4.00"));

    RefundResult unreported = session.refund(new BigDecimal("10.00")).get();
    RefundResult reported = session.refund(new BigDecimal("10.00")).get();

    assertEquals(1, unreported.getRefundedAmountByTender().size(), "the split is unknown");
    assertTender(
        unreported.getRefundedAmountByTender().get(0), TenderType.CARD, "POI-PAY-1", "10.00");
    assertNull(unreported.getCardRefunds().get(0).getBiltRewardsAmount());
    assertTender(reported.getRefundedAmountByTender().get(0), TenderType.CARD, "POI-PAY-1", "6.00");
    assertTender(
        reported.getRefundedAmountByTender().get(1), TenderType.BILT_REWARDS, "POI-PAY-1", "4.00");
  }

  @Test
  void aMixedPaymentsRefundOfItsWholeAmountSplitsByTheRecord() throws Exception {
    addHundredDollarItem();
    enqueue(card("Success", "POI-PAY-1", 100.00, "biltRewardsAmount=25.00"));
    session.settle().get();
    drainRequests();
    enqueue(refundOk("POI-REF-1", 100.00, ""));

    RefundResult result = session.refund(new BigDecimal("100.00")).get();

    assertEquals(2, result.getRefundedAmountByTender().size());
    assertTender(result.getRefundedAmountByTender().get(0), TenderType.CARD, "POI-PAY-1", "75.00");
    assertTender(
        result.getRefundedAmountByTender().get(1), TenderType.BILT_REWARDS, "POI-PAY-1", "25.00");
    assertMoney("25.00", result.getCardRefunds().get(0).getBiltRewardsAmount());
  }

  // ─── Without the key, or with a bad one ───

  @Test
  void withoutTheKeyEveryPaymentIsCardMoneyAsBefore() throws Exception {
    addHundredDollarItem();
    enqueue(card("Partial", "POI-PAY-1", 40.00, "currentBalance=0.00"), cardOk("POI-PAY-2", 60.00));

    SettlementResult result = session.settle().get();

    assertEquals(2, result.getCardPayments().size());
    assertTrue(result.getBiltRewardsPayments().isEmpty());
    assertMoney("0", result.getBiltRewardsAmount());
    assertMoney("100.00", result.getCardAmountCharged());
    assertMoney("100.00", result.getFinalBasket().getCardPaymentTotal());
    assertMoney("0", result.getFinalBasket().getBiltRewardsTotal());
    assertNull(result.getMovements().get(0).getBiltRewardsAmount());
    assertTrue(result.toOriginalSaleRecord(null).getBiltRewardsPayments().isEmpty());
  }

  @Test
  void aKeyAboveTheAuthorizedAmountIsIgnoredWithAWarning() throws Exception {
    addHundredDollarItem();
    enqueue(card("Success", "POI-PAY-1", 100.00, "biltRewardsAmount=120.00"));

    SettlementResult result = session.settle().get();

    assertEquals(1, result.getCardPayments().size());
    assertMoney("0", result.getBiltRewardsAmount());
    assertMoney("100.00", result.getCardAmountCharged());
    assertTrue(
        result.getWarnings().stream().anyMatch(warning -> warning.contains("biltRewardsAmount")),
        result.getWarnings().toString());
  }

  @Test
  void aZeroKeyMeansNoRewardsWithAWarning() throws Exception {
    addHundredDollarItem();
    enqueue(card("Success", "POI-PAY-1", 100.00, "biltRewardsAmount=0.00"));

    SettlementResult result = session.settle().get();

    assertMoney("0", result.getBiltRewardsAmount());
    assertMoney("100.00", result.getCardAmountCharged());
    assertNull(result.getMovements().get(0).getBiltRewardsAmount());
    assertEquals(1, result.getWarnings().size(), result.getWarnings().toString());
    assertTrue(result.getWarnings().get(0).contains("biltRewardsAmount=0.00"));
  }

  // ─── Recovered through TransactionStatus ───

  @Test
  void aPaymentRecoveredThroughTransactionStatusKeepsItsRewards() throws Exception {
    session =
        start(
            sessionBuilder()
                .client(
                    BiltNexoTerminalClient.builder()
                        .endpoint(server.url("/nexo").toString())
                        .readTimeout(Duration.ofMillis(300))
                        .disableRecoveryOnNetworkError()
                        .build()));
    drainRequests();
    addHundredDollarItem();
    server.enqueue(
        new MockResponse()
            .setBodyDelay(1000, TimeUnit.MILLISECONDS)
            .setBody(card("Success", "POI-PAY-1", 100.00, "biltRewardsAmount=30.00")));
    enqueue(transactionStatus(cardBody("Success", "POI-PAY-1", 100.00, "biltRewardsAmount=30.00")));

    SettlementResult result = session.settle().get();

    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(1, payments(requests).size(), "status recovery must not resend the payment");
    assertNotNull(requests.get(1).getTransactionStatusRequest());
    assertMoney("30.00", result.getBiltRewardsAmount());
    assertMoney("70.00", result.getCardAmountCharged());
    assertMoney("30.00", result.getCardPayments().get(0).getBiltRewardsAmount());
  }

  // ─── A sale the rewards covered whole ───

  @Test
  void aSaleTheRewardsCoveredHasNoCardPayment() throws Exception {
    addHundredDollarItem();
    enqueue(rewardsPartial("ATT-1", "100.00").replace("Partial", "Success"));

    SettlementResult result = session.settle().get();

    assertTrue(result.getCardPayments().isEmpty());
    assertEquals(1, result.getBiltRewardsPayments().size());
    assertMoney("0", result.getCardAmountCharged());
    assertMoney("100.00", result.getBiltRewardsAmount());
    assertEquals("ATT-1", result.getPoiTransactionId(), "its single reference is the rewards'");
    OriginalSaleRecord record = result.toOriginalSaleRecord(null);
    assertTrue(record.getCardPayments().isEmpty());
    assertNull(record.getCardPoiTransactionId());
    assertEquals(1, record.getBiltRewardsPayments().size());
    assertThrows(
        NullPointerException.class,
        () -> RefundAllocation.card(new BigDecimal("10.00"), record),
        "a card refund helper must not refund the rewards");
    RefundAllocation rewards = RefundAllocation.biltRewards(new BigDecimal("10.00"), record);
    assertEquals("ATT-1", rewards.getOriginalPoiTransactionId());
  }

  @Test
  void aCardReferenceEqualToARewardsPaymentIsReadAsTheRewards() {
    OriginalSaleRecord record =
        OriginalSaleRecord.builder()
            .cardPoiTransactionId("ATT-1")
            .addBiltRewardsPayment(
                BiltRewardsPaymentRecord.builder().poiTransactionId("ATT-1").build())
            .build();

    assertTrue(record.getCardPayments().isEmpty());
    assertNull(record.getCardPoiTransactionId());
    assertTrue(record.hasMovement());
    assertTrue(
        record.sharesMovementWith(
            OriginalSaleRecord.builder().cardPoiTransactionId("ATT-1").build()));
  }

  @Test
  void aPriorSalesVoidReversesItsRewardsPayment() throws Exception {
    OriginalSaleRecord record =
        OriginalSaleRecord.builder()
            .addCardPayment(
                CardPaymentRecord.builder()
                    .poiTransactionId("POI-PAY-1")
                    .amount(new BigDecimal("60.00"))
                    .build())
            .addBiltRewardsPayment(
                BiltRewardsPaymentRecord.builder()
                    .poiTransactionId("ATT-1")
                    .amount(new BigDecimal("40.00"))
                    .build())
            .build();
    enqueue(reversalOk("POI-REV-1", 60.00), TerminalShopperSessionTest.REVERSAL_OK);

    VoidResult result = session.voidTransaction(record).get();

    assertEquals(List.of("POI-PAY-1", "ATT-1"), reversed(drainRequests()));
    assertTender(
        result.getReversedAmountByTender().get(1),
        TenderType.BILT_REWARDS,
        "ATT-1",
        "40.00"); // the reversal echoed no amount: the record's
  }

  // ─── Return settlements ───

  @Test
  void aRewardsRefundAllocationIsReportedAsRewards() throws Exception {
    session
        .basket()
        .addItem(BasketItem.returnItem("RET-1", "Returned item", 1, new BigDecimal("40.00")));
    enqueue(refundOk("POI-REF-1", 25.00, ""), refundOk("POI-REF-2", 15.00, ""));

    SettlementResult result =
        session
            .settle(
                SettlementOptions.builder()
                    .addRefund(RefundAllocation.card(new BigDecimal("25.00"), "POI-PAY-1", null))
                    .addRefund(RefundAllocation.biltRewards(new BigDecimal("15.00"), "ATT-1", null))
                    .build())
            .get();

    assertMoney("25.00", result.getCardRefundedAmount());
    assertMoney("15.00", result.getBiltRewardsRefundedAmount());
    assertEquals(SettlementStep.CARD_REFUND, result.getMovements().get(1).getStep());
    assertMoney("15.00", result.getMovements().get(1).getBiltRewardsAmount());
    assertNull(result.getMovements().get(0).getBiltRewardsAmount());
    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(List.of("POI-PAY-1", "ATT-1"), refunded(requests));
    assertEquals(
        "Refund", requests.get(1).getPaymentRequest().getPaymentData().getPaymentType().toValue());
    assertMoney("15.00", amounts(requests.get(1)).getRequestedAmount());
  }

  @Test
  void aRewardsRefundAllocationNeedsAnOriginalReference() {
    assertThrows(
        NullPointerException.class,
        () -> RefundAllocation.biltRewards(new BigDecimal("1.00"), (String) null, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            RefundAllocation.biltRewards(
                new BigDecimal("1.00"), OriginalSaleRecord.builder().build()));
  }
}
