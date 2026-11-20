package com.bilt.pos.session;

import static org.junit.jupiter.api.Assertions.*;

import com.bilt.pos.display.DisplayPayload;
import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.AmountsReq;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.PaymentData;
import com.bilt.pos.nexo.model.PaymentRequest;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.payment.CardPaymentResult;
import com.bilt.pos.session.settlement.AbandonedSettlementRecord;
import com.bilt.pos.session.settlement.CardPayment;
import com.bilt.pos.session.settlement.ExternalPayment;
import com.bilt.pos.session.settlement.SettlementFailure;
import com.bilt.pos.session.settlement.SettlementLoyalty;
import com.bilt.pos.session.settlement.SettlementMovement;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.bilt.pos.session.settlement.SettlementRecovery;
import com.bilt.pos.session.settlement.SettlementResult;
import com.bilt.pos.session.settlement.SettlementStep;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Planned split card payments: {@link SettlementOptions.Builder#cardPayments}. */
class TerminalShopperSessionSplitPaymentTest {

  private static final String REWARDS_B64 =
      java.util.Base64.getEncoder()
          .encodeToString(
              ("{\"rewards\":[{\"rewardRef\":\"rwd:RWD-44021\",\"type\":\"reward\","
                      + "\"name\":\"$10 Off Purchase\"}],\"rewardCount\":1}")
                  .getBytes(java.nio.charset.StandardCharsets.UTF_8));

  private static final String IDENTIFY_OK =
      "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
          + "\"Response\":{\"Result\":\"Success\",\"AdditionalResponse\":\""
          + REWARDS_B64
          + "\"},"
          + "\"LoyaltyAccount\":[{\"LoyaltyAccountID\":{\"LoyaltyID\":\"98234\"},"
          + "\"LoyaltyBrand\":\"K-Club\"}]}}}";

  private static final String REBATE_OK =
      "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
          + "\"Response\":{\"Result\":\"Success\"},"
          + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-RB-1\","
          + "\"TimeStamp\":\"2026-07-20T10:00:01Z\"}},"
          + "\"LoyaltyResult\":[{\"Rebates\":{\"TotalRebate\":10.00,"
          + "\"RebateLabel\":\"Gold Member\","
          + "\"SaleItemRebate\":[{\"ItemID\":1,\"ProductCode\":\"SKU-1\","
          + "\"ItemAmount\":10.00,\"RebateLabel\":\"Gold: $10 off\"}]}}]}}}";

  private static final String REDEEM_OK =
      "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
          + "\"Response\":{\"Result\":\"Success\"},"
          + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-RD-1\","
          + "\"TimeStamp\":\"2026-07-20T10:00:02Z\"}},"
          + "\"LoyaltyResult\":[{\"CurrentBalance\":700,"
          + "\"LoyaltyAmount\":{\"AmountValue\":5.00,\"LoyaltyUnit\":\"Monetary\"}}]}}}";

  private static final String AWARD_OK =
      "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
          + "\"Response\":{\"Result\":\"Success\"},"
          + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-AW-1\"}},"
          + "\"LoyaltyResult\":[{\"CurrentBalance\":789,"
          + "\"LoyaltyAmount\":{\"AmountValue\":89,\"LoyaltyUnit\":\"Point\"}}]}}}";

  private static final String GIFT_CARD_PARTIAL_35 =
      "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
          + "\"Response\":{\"Result\":\"Partial\",\"AdditionalResponse\":\"currentBalance=0.00\"},"
          + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-GC-1\","
          + "\"TimeStamp\":\"2026-07-20T10:00:02Z\"}},"
          + "\"PaymentResult\":{\"AmountsResp\":{\"AuthorizedAmount\":35.00}}}}}";

  private static final String PAYMENT_DECLINED =
      "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
          + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Refusal\"}}}}";

  private static final String REVERSAL_OK = TerminalShopperSessionTest.REVERSAL_OK;

  private static final String DISPLAY_OK = "{\"SaleToPOIResponse\":{\"DisplayResponse\":{}}}";

  private static String paymentOk(String poiTxn, double authorized) {
    return "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
        + "\"Response\":{\"Result\":\"Success\"},"
        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\""
        + poiTxn
        + "\","
        + "\"TimeStamp\":\"2026-07-20T10:00:03Z\"}},"
        + "\"PaymentResult\":{"
        + "\"AmountsResp\":{\"Currency\":\"USD\",\"AuthorizedAmount\":"
        + authorized
        + "},"
        + "\"PaymentAcquirerData\":{\"ApprovalCode\":\"APPR-"
        + poiTxn
        + "\","
        + "\"AcquirerTransactionID\":{\"TransactionID\":\"ACQ-"
        + poiTxn
        + "\"}},"
        + "\"PaymentInstrumentData\":{\"CardData\":{\"PaymentBrand\":\"Visa\"}}}}}}";
  }

  private static String transactionStatusPaymentOk(String poiTxn, double authorized) {
    return "{\"SaleToPOIResponse\":{\"TransactionStatusResponse\":{"
        + "\"Response\":{\"Result\":\"Success\"},"
        + "\"RepeatedMessageResponse\":{"
        + "\"MessageHeader\":{\"MessageCategory\":\"Payment\"},"
        + "\"RepeatedResponseMessageBody\":{\"PaymentResponse\":{"
        + "\"Response\":{\"Result\":\"Success\"},"
        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\""
        + poiTxn
        + "\","
        + "\"TimeStamp\":\"2026-07-20T10:00:03Z\"}},"
        + "\"PaymentResult\":{\"AmountsResp\":{"
        + "\"Currency\":\"USD\",\"AuthorizedAmount\":"
        + authorized
        + "}}"
        + "}}}}}}}";
  }

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

  private void identifyMember() throws Exception {
    server.enqueue(new MockResponse().setBody(IDENTIFY_OK));
    session.identifyMember().executeSync();
    server.takeRequest(5, TimeUnit.SECONDS);
  }

  private void addHundredDollarItem() {
    session.basket().addItem(BasketItem.sale("SKU-1", "Item", 1, new BigDecimal("100.00")));
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

  private static SettlementOptions plan(String... amounts) {
    List<CardPayment> payments = new ArrayList<>();
    for (String amount : amounts) {
      payments.add(CardPayment.of(new BigDecimal(amount)));
    }
    return SettlementOptions.builder().cardPayments(payments).build();
  }

  private static List<PaymentRequest> cardPayments(List<SaleToPOIRequest> requests) {
    return requests.stream()
        .map(SaleToPOIRequest::getPaymentRequest)
        .filter(
            payment ->
                payment != null
                    && (payment.getPaymentData() == null
                        || payment.getPaymentData().getPaymentInstrumentData() == null))
        .collect(Collectors.toList());
  }

  private static double requested(PaymentRequest payment) {
    return payment.getPaymentTransaction().getAmountsReq().getRequestedAmount();
  }

  private static String saleTransactionId(PaymentRequest payment) {
    return payment.getSaleData().getSaleTransactionID().getTransactionID();
  }

  private static String reversedPoiTransaction(SaleToPOIRequest request) {
    return request
        .getReversalRequest()
        .getOriginalPOITransaction()
        .getPoiTransactionID()
        .getTransactionID();
  }

  /** Card payment 1 is a normal payment, without any split field. */
  private static void assertNormalPayment(PaymentRequest payment) {
    PaymentData data = payment.getPaymentData();
    assertTrue(data == null || data.getSplitPaymentFlag() == null);
    assertNull(payment.getPaymentTransaction().getAmountsReq().getPaidAmount());
  }

  /** A later card payment is a nexo split payment carrying what was paid before it. */
  private static void assertSplitPayment(PaymentRequest payment, double paidBefore) {
    assertEquals(Boolean.TRUE, payment.getPaymentData().getSplitPaymentFlag());
    assertEquals("Normal", payment.getPaymentData().getPaymentType().toValue());
    AmountsReq amounts = payment.getPaymentTransaction().getAmountsReq();
    assertEquals(paidBefore, amounts.getPaidAmount());
  }

  private static void assertAmount(String expected, BigDecimal actual) {
    assertEquals(0, new BigDecimal(expected).compareTo(actual), expected + " != " + actual);
  }

  // ─── Options ───

  @Test
  void plannedAmountsMustBePositive() {
    assertThrows(IllegalArgumentException.class, () -> CardPayment.of(BigDecimal.ZERO));
    assertThrows(IllegalArgumentException.class, () -> CardPayment.of(new BigDecimal("-1.00")));
    assertThrows(IllegalArgumentException.class, () -> CardPayment.of(null));
  }

  @Test
  void plannedAmountsMustBeWholeCents() {
    assertThrows(IllegalArgumentException.class, () -> CardPayment.of(new BigDecimal("20.001")));
    assertAmount("20.00", CardPayment.of(new BigDecimal("20.000")).getAmount());
    assertAmount("20", CardPayment.of(new BigDecimal("20")).getAmount());
  }

  @Test
  void cardPaymentsCannotBeCombinedWithCashback() {
    SettlementOptions.Builder builder =
        SettlementOptions.builder()
            .cashback(new BigDecimal("20.00"))
            .cardPayments(CardPayment.of(new BigDecimal("20.00")));
    assertThrows(IllegalStateException.class, builder::build);
  }

  @Test
  void cardPaymentsCannotUseImplicitLoyalty() {
    SettlementOptions.Builder builder =
        SettlementOptions.builder()
            .loyalty(SettlementLoyalty.implicit())
            .cardPayments(CardPayment.of(new BigDecimal("20.00")));
    IllegalStateException refused = assertThrows(IllegalStateException.class, builder::build);
    assertTrue(refused.getMessage().contains("SettlementLoyalty.explicit()"));

    assertEquals(
        1,
        SettlementOptions.builder()
            .loyalty(SettlementLoyalty.forbidden())
            .cardPayments(CardPayment.of(new BigDecimal("20.00")))
            .build()
            .getCardPayments()
            .size());
    assertEquals(
        SettlementLoyalty.Handling.IMPLICIT,
        SettlementOptions.builder()
            .loyalty(SettlementLoyalty.implicit())
            .build()
            .getLoyalty()
            .getHandling(),
        "implicit loyalty without a plan is unchanged");
  }

  @Test
  void noPlanMeansOneCardPaymentForTheWholeAmount() throws Exception {
    addHundredDollarItem();
    enqueue(paymentOk("POI-PAY-1", 100.00));

    SettlementResult result = session.settle().get();

    assertEquals(1, result.getCardPayments().size());
    List<PaymentRequest> payments = cardPayments(drainRequests());
    assertEquals(1, payments.size());
    assertEquals(100.00, requested(payments.get(0)));
    assertNormalPayment(payments.get(0));
  }

  // ─── Planned splits ───

  @Test
  void twentyThenTheRestIsTwoCardPayments() throws Exception {
    addHundredDollarItem();
    enqueue(paymentOk("POI-PAY-1", 20.00), paymentOk("POI-PAY-2", 80.00));
    List<SettlementMovement> charged = new ArrayList<>();
    List<SettlementMovement> movements = new ArrayList<>();

    SettlementResult result =
        session.settle(plan("20.00")).onCardCharged(charged::add).onMovement(movements::add).get();

    List<PaymentRequest> payments = cardPayments(drainRequests());
    assertEquals(2, payments.size());
    assertEquals(20.00, requested(payments.get(0)));
    assertNormalPayment(payments.get(0));
    assertEquals(80.00, requested(payments.get(1)));
    assertSplitPayment(payments.get(1), 20.00);
    assertEquals(saleTransactionId(payments.get(0)), saleTransactionId(payments.get(1)));
    assertEquals(
        payments.get(0).getSaleData().getSaleTransactionID().getTimeStamp(),
        payments.get(1).getSaleData().getSaleTransactionID().getTimeStamp());
    assertEquals(1, payments.get(0).getPaymentTransaction().getSaleItem().length);
    assertEquals(
        payments.get(0).getPaymentTransaction().getSaleItem()[0].getItemAmount(),
        payments.get(1).getPaymentTransaction().getSaleItem()[0].getItemAmount(),
        "every card payment sends the whole adjusted basket");

    assertEquals(2, charged.size(), "onCardCharged fires once per card payment");
    assertAmount("20.00", charged.get(0).getAmount());
    assertAmount("80.00", charged.get(1).getAmount());
    assertEquals("POI-PAY-1", charged.get(0).getPoiTransactionId());
    assertEquals("POI-PAY-2", charged.get(1).getPoiTransactionId());
    assertNotEquals(charged.get(0).getServiceId(), charged.get(1).getServiceId());
    assertEquals(charged, movements);
    assertEquals(charged, result.getMovements());

    List<CardPaymentResult> results = result.getCardPayments();
    assertEquals(2, results.size());
    assertAmount("20.00", results.get(0).getAmount());
    assertEquals("POI-PAY-1", results.get(0).getPoiTransactionId());
    assertEquals("APPR-POI-PAY-1", results.get(0).getApprovalCode());
    assertAmount("80.00", results.get(1).getAmount());
    assertEquals("POI-PAY-2", results.get(1).getPoiTransactionId());
    assertEquals("APPR-POI-PAY-2", results.get(1).getApprovalCode());
    assertAmount("100.00", result.getCardAmountCharged());
    assertAmount("100.00", result.getAuthorizedAmount());
    assertAmount("100.00", result.getFinalBasket().getCardPaymentTotal());
    assertEquals("POI-PAY-1", result.getPoiTransactionId(), "legacy getters: the first payment");
  }

  @Test
  void processingDisplayIsShownBeforeEveryCardPayment() throws Exception {
    addHundredDollarItem();
    DisplayPayload processing = new DisplayPayload();
    processing.setVersion("1.0");
    processing.setLayout("processing.xslt");
    enqueue(DISPLAY_OK, paymentOk("POI-PAY-1", 20.00), DISPLAY_OK, paymentOk("POI-PAY-2", 80.00));

    session
        .settle(
            SettlementOptions.builder()
                .paymentProcessingDisplay(processing)
                .cardPayments(CardPayment.of(new BigDecimal("20.00")))
                .build())
        .get();

    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(4, requests.size());
    assertNotNull(requests.get(0).getDisplayRequest());
    assertNotNull(requests.get(1).getPaymentRequest());
    assertNotNull(requests.get(2).getDisplayRequest(), "the next card starts on the display too");
    assertNotNull(requests.get(3).getPaymentRequest());
  }

  @Test
  void eachPlannedPaymentIsItsOwnSplitWithTheRunningPaidAmount() throws Exception {
    addHundredDollarItem();
    enqueue(
        paymentOk("POI-PAY-1", 20.00),
        paymentOk("POI-PAY-2", 30.00),
        paymentOk("POI-PAY-3", 50.00));
    List<SettlementMovement> charged = new ArrayList<>();

    SettlementResult result =
        session.settle(plan("20.00", "30.00")).onCardCharged(charged::add).get();

    List<SaleToPOIRequest> requests = drainRequests();
    List<PaymentRequest> payments = cardPayments(requests);
    assertEquals(3, payments.size());
    assertEquals(20.00, requested(payments.get(0)));
    assertNormalPayment(payments.get(0));
    assertEquals(30.00, requested(payments.get(1)));
    assertSplitPayment(payments.get(1), 20.00);
    assertEquals(50.00, requested(payments.get(2)));
    assertSplitPayment(payments.get(2), 50.00);
    Set<String> saleTransactions = new HashSet<>();
    Set<String> serviceIds = new HashSet<>();
    for (int i = 0; i < 3; i++) {
      saleTransactions.add(saleTransactionId(payments.get(i)));
      serviceIds.add(requests.get(i).getMessageHeader().getServiceID());
    }
    assertEquals(1, saleTransactions.size(), "nexo Rule 8: one SaleTransactionID for every split");
    assertEquals(3, serviceIds.size(), "each card payment is its own request");
    assertEquals(
        serviceIds,
        charged.stream().map(SettlementMovement::getServiceId).collect(Collectors.toSet()));

    assertEquals(3, result.getCardPayments().size());
    assertAmount("100.00", result.getCardAmountCharged());
  }

  @Test
  void planAppliesToTheCardAmountLeftAfterLoyaltyAndStoredValue() throws Exception {
    identifyMember();
    addHundredDollarItem();
    session.setStoredValueCard("GC-1234-5678");
    // $100 − $10 rebate − $5 points − $35 gift card = $50 for the cards
    enqueue(
        REBATE_OK,
        REDEEM_OK,
        GIFT_CARD_PARTIAL_35,
        paymentOk("POI-PAY-1", 20.00),
        paymentOk("POI-PAY-2", 30.00),
        AWARD_OK);
    AtomicReference<BigDecimal> awardTotal = new AtomicReference<>();

    SettlementResult result =
        session
            .settle(plan("20.00"))
            .beforeStep(
                ctx -> {
                  if (ctx.getStep() == SettlementStep.AWARD) {
                    awardTotal.set(ctx.getCurrentTotal());
                  }
                  return null;
                })
            .get();

    List<PaymentRequest> payments = cardPayments(drainRequests());
    assertEquals(2, payments.size());
    assertEquals(20.00, requested(payments.get(0)));
    assertNormalPayment(payments.get(0));
    assertEquals(30.00, requested(payments.get(1)));
    assertSplitPayment(payments.get(1), 55.00);
    assertAmount("50.00", result.getCardAmountCharged());
    assertAmount("35.00", result.getStoredValueAmountUsed());
    assertAmount("85.00", result.getAuthorizedAmount());
    assertAmount("85.00", awardTotal.get());
  }

  @Test
  void planReachingTheCardAmountFailsBeforeAnyCardIsCharged() throws Exception {
    addHundredDollarItem();
    session.setStoredValueCard("GC-1234-5678");
    enqueue(GIFT_CARD_PARTIAL_35, REVERSAL_OK);
    AtomicReference<SettlementFailure> seen = new AtomicReference<>();
    List<SettlementMovement> charged = new ArrayList<>();

    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle(plan("40.00", "25.00"))
                    .onCardCharged(charged::add)
                    .onError(
                        error -> {
                          seen.set(error);
                          return SettlementRecovery.abort();
                        })
                    .get());

    assertEquals(SessionErrorCode.CARD_PAYMENTS_EXCEED_TOTAL, failure.getError().getCode());
    assertEquals(SessionErrorCode.CARD_PAYMENTS_EXCEED_TOTAL, seen.get().getError().getCode());
    assertEquals(SettlementStep.CARD_CHARGE, seen.get().getStep());
    assertAmount("65.00", seen.get().getAmountDue());
    assertEquals(1, seen.get().getCommittedMovements().size(), "only the gift card stands");
    assertTrue(charged.isEmpty());
    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(2, requests.size(), "the gift card charge, then its reversal on abort");
    assertTrue(cardPayments(requests).isEmpty());
    assertEquals("POI-GC-1", reversedPoiTransaction(requests.get(1)));
  }

  @Test
  void planExceedingTheCardAmountCanBeAbandonedButNotRetried() throws Exception {
    addHundredDollarItem();
    AtomicReference<AbandonedSettlementRecord> abandoned = new AtomicReference<>();

    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle(plan("150.00"))
                    .onError(error -> SettlementRecovery.abandon())
                    .onAbandoned(abandoned::set)
                    .get());

    assertEquals(SessionErrorCode.ABANDONED, failure.getError().getCode());
    assertAmount("100.00", abandoned.get().getOutstandingAmount());
    assertEquals(
        SessionErrorCode.CARD_PAYMENTS_EXCEED_TOTAL,
        abandoned.get().getFailure().getError().getCode());
    assertTrue(drainRequests().isEmpty());

    session.basket().clear();
    addHundredDollarItem();
    SessionException retried =
        assertThrows(
            SessionException.class,
            () ->
                session.settle(plan("100.00")).onError(error -> SettlementRecovery.retry()).get());
    assertEquals(SessionErrorCode.INVALID_STATE, retried.getError().getCode());
    assertTrue(drainRequests().isEmpty());
  }

  // ─── Failures on a later card payment ───

  @Test
  void declinedSecondPaymentCanBeRetriedWithTheSameSplitFields() throws Exception {
    addHundredDollarItem();
    enqueue(paymentOk("POI-PAY-1", 20.00), PAYMENT_DECLINED, paymentOk("POI-PAY-2", 80.00));
    List<SettlementFailure> failures = new ArrayList<>();

    SettlementResult result =
        session
            .settle(plan("20.00"))
            .onError(
                failure -> {
                  failures.add(failure);
                  return SettlementRecovery.retry();
                })
            .get();

    assertEquals(1, failures.size());
    SettlementFailure failure = failures.get(0);
    assertEquals(SessionErrorCode.DECLINED, failure.getError().getCode());
    assertEquals(SettlementStep.CARD_CHARGE, failure.getStep());
    assertAmount("80.00", failure.getAmountDue());
    assertEquals(1, failure.getCommittedMovements().size(), "card payment 1 stays committed");

    List<SaleToPOIRequest> requests = drainRequests();
    List<PaymentRequest> payments = cardPayments(requests);
    assertEquals(3, payments.size());
    assertEquals(80.00, requested(payments.get(1)));
    assertSplitPayment(payments.get(1), 20.00);
    assertEquals(80.00, requested(payments.get(2)));
    assertSplitPayment(payments.get(2), 20.00);
    assertEquals(saleTransactionId(payments.get(0)), saleTransactionId(payments.get(2)));
    assertNotEquals(
        requests.get(1).getMessageHeader().getServiceID(),
        requests.get(2).getMessageHeader().getServiceID());
    assertTrue(requests.stream().noneMatch(request -> request.getReversalRequest() != null));
    assertEquals(2, result.getCardPayments().size());
    assertAmount("100.00", result.getCardAmountCharged());
  }

  @Test
  void declinedSecondPaymentCanBeFinishedInCashKeepingTheFirst() throws Exception {
    addHundredDollarItem();
    enqueue(paymentOk("POI-PAY-1", 20.00), PAYMENT_DECLINED);

    SettlementResult result =
        session
            .settle(plan("20.00", "30.00"))
            .onError(
                failure ->
                    SettlementRecovery.external(ExternalPayment.cash(failure.getAmountDue())))
            .get();

    assertEquals(1, result.getCardPayments().size());
    assertAmount("20.00", result.getCardAmountCharged());
    assertAmount("80.00", result.getExternalPaymentAmount());
    assertEquals(
        List.of(SettlementStep.CARD_CHARGE, SettlementStep.EXTERNAL_PAYMENT),
        result.getMovements().stream()
            .map(SettlementMovement::getStep)
            .collect(Collectors.toList()));
    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(2, requests.size(), "external ends the card phase: no third card payment");
    assertTrue(requests.stream().noneMatch(request -> request.getReversalRequest() != null));
  }

  @Test
  void externalPaymentOnALaterCardPaymentMustCoverEverythingStillDue() throws Exception {
    addHundredDollarItem();
    enqueue(paymentOk("POI-PAY-1", 20.00), PAYMENT_DECLINED);

    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle(plan("20.00", "30.00"))
                    .onError(
                        error ->
                            SettlementRecovery.external(
                                ExternalPayment.cash(new BigDecimal("30.00"))))
                    .get());

    assertEquals(SessionErrorCode.INVALID_STATE, failure.getError().getCode());
    assertTrue(failure.getError().getMessage().contains("80.00"));
  }

  @Test
  void laterCardPaymentCannotBeSkipped() throws Exception {
    addHundredDollarItem();
    enqueue(paymentOk("POI-PAY-1", 20.00), PAYMENT_DECLINED);

    SessionException failure =
        assertThrows(
            SessionException.class,
            () -> session.settle(plan("20.00")).onError(error -> SettlementRecovery.skip()).get());

    assertEquals(SessionErrorCode.INVALID_STATE, failure.getError().getCode());
  }

  @Test
  void abortOnALaterCardPaymentReversesEveryCardPaymentNewestFirst() throws Exception {
    addHundredDollarItem();
    enqueue(
        paymentOk("POI-PAY-1", 20.00),
        paymentOk("POI-PAY-2", 30.00),
        PAYMENT_DECLINED,
        REVERSAL_OK,
        REVERSAL_OK);

    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle(plan("20.00", "30.00"))
                    .onError(
                        error -> {
                          assertAmount("50.00", error.getAmountDue());
                          return SettlementRecovery.abort();
                        })
                    .get());

    assertEquals(SessionErrorCode.DECLINED, failure.getError().getCode());
    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(5, requests.size());
    assertEquals("POI-PAY-2", reversedPoiTransaction(requests.get(3)));
    assertEquals("POI-PAY-1", reversedPoiTransaction(requests.get(4)));
  }

  @Test
  void laterCardPaymentFailureCanBeAbandonedWithTheEarlierPaymentsStanding() throws Exception {
    addHundredDollarItem();
    enqueue(paymentOk("POI-PAY-1", 20.00), PAYMENT_DECLINED);
    AtomicReference<AbandonedSettlementRecord> abandoned = new AtomicReference<>();

    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle(plan("20.00"))
                    .onError(error -> SettlementRecovery.abandon())
                    .onAbandoned(abandoned::set)
                    .get());

    assertEquals(SessionErrorCode.ABANDONED, failure.getError().getCode());
    assertAmount("80.00", abandoned.get().getOutstandingAmount());
    assertEquals(1, abandoned.get().getCommittedMovements().size());
    assertEquals("POI-PAY-1", abandoned.get().getCommittedMovements().get(0).getPoiTransactionId());
    assertTrue(drainRequests().stream().noneMatch(request -> request.getReversalRequest() != null));
  }

  @Test
  void underAuthorizedLaterCardPaymentFailsAsDeclinedOnceTheAutomaticRequestsRunOut()
      throws Exception {
    addHundredDollarItem();
    // the remainder payment of 80 approves 50; the automatic follow-ups for 30 and then 10 come
    // back partial too, and the third request is the last of MAX_AUTOMATIC_CARD_REQUESTS
    enqueue(
        paymentOk("POI-PAY-1", 20.00),
        paymentOk("POI-PAY-2", 50.00),
        paymentOk("POI-PAY-3", 20.00),
        paymentOk("POI-PAY-4", 5.00),
        REVERSAL_OK,
        REVERSAL_OK,
        REVERSAL_OK,
        REVERSAL_OK);
    AtomicReference<SettlementFailure> seen = new AtomicReference<>();

    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle(plan("20.00"))
                    .onError(
                        error -> {
                          seen.set(error);
                          return SettlementRecovery.abort();
                        })
                    .get());

    assertEquals(SessionErrorCode.DECLINED, failure.getError().getCode());
    assertAmount("10.00", seen.get().getAmountDue());
    assertEquals(4, seen.get().getCommittedMovements().size(), "the partial charges are committed");
    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(8, requests.size());
    assertEquals("POI-PAY-4", reversedPoiTransaction(requests.get(4)));
    assertEquals("POI-PAY-3", reversedPoiTransaction(requests.get(5)));
    assertEquals("POI-PAY-2", reversedPoiTransaction(requests.get(6)));
    assertEquals("POI-PAY-1", reversedPoiTransaction(requests.get(7)));
  }

  @Test
  void indeterminateSecondPaymentIsRecoveredByTransactionStatus() throws Exception {
    session =
        start(
            sessionBuilder()
                .client(
                    BiltNexoTerminalClient.builder()
                        .endpoint(server.url("/nexo").toString())
                        .readTimeout(Duration.ofMillis(300))
                        .disableRecoveryOnNetworkError()
                        .build()));
    addHundredDollarItem();
    enqueue(paymentOk("POI-PAY-1", 20.00));
    server.enqueue(
        new MockResponse()
            .setBodyDelay(1000, TimeUnit.MILLISECONDS)
            .setBody(paymentOk("POI-PAY-2", 80.00)));
    enqueue(transactionStatusPaymentOk("POI-PAY-2", 80.00));
    AtomicInteger errorCalls = new AtomicInteger();

    SettlementResult result =
        session
            .settle(plan("20.00"))
            .onError(
                failure -> {
                  errorCalls.incrementAndGet();
                  return SettlementRecovery.abort();
                })
            .get();

    assertEquals(0, errorCalls.get());
    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(3, requests.size(), "status recovery must not resend the split payment");
    assertSplitPayment(requests.get(1).getPaymentRequest(), 20.00);
    assertEquals(
        requests.get(1).getMessageHeader().getServiceID(),
        requests.get(2).getTransactionStatusRequest().getMessageReference().getServiceID());
    assertEquals(2, result.getCardPayments().size());
    assertEquals("POI-PAY-2", result.getCardPayments().get(1).getPoiTransactionId());
    assertAmount("100.00", result.getCardAmountCharged());
  }

  // ─── The shared SaleTransactionID ───

  @Test
  void beforeStepRunsForEachCardPaymentWithTheFirstPaymentsIdAsDefault() throws Exception {
    addHundredDollarItem();
    enqueue(paymentOk("POI-PAY-1", 20.00), paymentOk("POI-PAY-2", 80.00));
    List<String> defaults = new ArrayList<>();
    List<String> serviceIds = new ArrayList<>();
    List<BigDecimal> totals = new ArrayList<>();
    List<BigDecimal> stepAmounts = new ArrayList<>();

    session
        .settle(plan("20.00"))
        .beforeStep(
            ctx -> {
              defaults.add(ctx.getDefaultTransactionId());
              serviceIds.add(ctx.getServiceId());
              totals.add(ctx.getCurrentTotal());
              stepAmounts.add(ctx.getStepAmount());
              return "TXN-" + ctx.getStep();
            })
        .get();

    assertEquals(2, defaults.size());
    assertAmount("100.00", totals.get(0));
    assertAmount("20.00", stepAmounts.get(0));
    assertAmount("80.00", totals.get(1));
    assertAmount("80.00", stepAmounts.get(1));
    assertEquals("TXN-CARD_CHARGE", defaults.get(1), "payment 2 defaults to payment 1's ID");
    assertNotEquals(serviceIds.get(0), serviceIds.get(1));
    List<PaymentRequest> payments = cardPayments(drainRequests());
    assertEquals("TXN-CARD_CHARGE", saleTransactionId(payments.get(0)));
    assertEquals("TXN-CARD_CHARGE", saleTransactionId(payments.get(1)));
  }

  @Test
  void beforeStepOverridingALaterCardPaymentsIdIsRefused() throws Exception {
    addHundredDollarItem();
    enqueue(paymentOk("POI-PAY-1", 20.00));
    AtomicInteger cardSteps = new AtomicInteger();
    List<SessionErrorCode> errors = new ArrayList<>();

    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle(plan("20.00"))
                    .beforeStep(ctx -> "TXN-" + cardSteps.incrementAndGet())
                    .onError(
                        error -> {
                          errors.add(error.getError().getCode());
                          return SettlementRecovery.retry();
                        })
                    .get());

    assertEquals(SessionErrorCode.INVALID_STATE, failure.getError().getCode());
    assertTrue(failure.getError().getMessage().contains("TXN-1"));
    assertTrue(failure.getError().getMessage().contains("TXN-2"));
    assertEquals(
        List.of(SessionErrorCode.INVALID_STATE),
        errors,
        "a refused override is not a recoverable step failure: onError only hears the outcome");
    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(1, requests.size(), "nothing is sent for the refused payment");
  }

  // ─── Void and refund after a split ───

  @Test
  void voidAfterASplitSaleReversesEveryCardPaymentNewestFirst() throws Exception {
    addHundredDollarItem();
    enqueue(paymentOk("POI-PAY-1", 20.00), paymentOk("POI-PAY-2", 80.00));
    session.settle(plan("20.00")).get();
    drainRequests();
    enqueue(
        "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
            + "\"Response\":{\"Result\":\"Success\"},\"ReversedAmount\":80.00}}}",
        "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
            + "\"Response\":{\"Result\":\"Success\"},\"ReversedAmount\":20.00}}}");

    VoidResult voided = session.voidTransaction().get();

    assertAmount("100.00", voided.getReversedAmount());
    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(2, requests.size());
    assertEquals("POI-PAY-2", reversedPoiTransaction(requests.get(0)));
    assertEquals("POI-PAY-1", reversedPoiTransaction(requests.get(1)));
  }

  @Test
  void refundAfterASplitSaleRefundsEveryCardPayment() throws Exception {
    addHundredDollarItem();
    enqueue(paymentOk("POI-PAY-1", 20.00), paymentOk("POI-PAY-2", 80.00));
    session.settle(plan("20.00")).get();
    drainRequests();

    SessionException partial =
        assertThrows(SessionException.class, () -> session.refund(new BigDecimal("10.00")).get());
    assertEquals(SessionErrorCode.INVALID_STATE, partial.getError().getCode());

    enqueue(TerminalShopperSessionTest.refundOk(80.00), TerminalShopperSessionTest.refundOk(20.00));
    RefundResult refund = session.refund().get();

    assertTrue(refund.isSuccess());
    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(2, requests.size());
    for (SaleToPOIRequest request : requests) {
      assertEquals(
          "Refund", request.getPaymentRequest().getPaymentData().getPaymentType().toValue());
    }
    assertEquals(
        List.of("POI-PAY-2", "POI-PAY-1"),
        requests.stream()
            .map(
                request ->
                    request
                        .getPaymentRequest()
                        .getPaymentTransaction()
                        .getOriginalPOITransaction()
                        .getPoiTransactionID()
                        .getTransactionID())
            .collect(Collectors.toList()));
  }
}
