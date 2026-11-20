package com.bilt.pos.session;

import static org.junit.jupiter.api.Assertions.*;

import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.AmountsReq;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.PaymentData;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.payment.CardPaymentResult;
import com.bilt.pos.session.settlement.CardPayment;
import com.bilt.pos.session.settlement.ExternalPayment;
import com.bilt.pos.session.settlement.OriginalSaleRecord;
import com.bilt.pos.session.settlement.PartialApprovalDecision;
import com.bilt.pos.session.settlement.PartialCardApproval;
import com.bilt.pos.session.settlement.SettlementFailure;
import com.bilt.pos.session.settlement.SettlementLoyalty;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.bilt.pos.session.settlement.SettlementRecovery;
import com.bilt.pos.session.settlement.SettlementResult;
import com.bilt.pos.session.settlement.SettlementStep;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code SettlementFlow.onPartialCardApproval}: the register's decision on a short card payment.
 */
class TerminalShopperSessionPartialCardApprovalTest {

  private static final String REVERSAL_OK = TerminalShopperSessionTest.REVERSAL_OK;

  private final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private MockWebServer server;
  private TerminalShopperSession session;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.ADMIN_OK));
    session =
        TerminalShopperSession.builder()
            .client(
                BiltNexoTerminalClient.builder()
                    .endpoint(server.url("/nexo").toString())
                    .disableRecoveryOnNetworkError()
                    .build())
            .saleId("POS-LANE-3")
            .poiId("VictaLane-275839164")
            .currency("USD")
            .autoDisplay(false)
            .start()
            .get();
    server.takeRequest(5, TimeUnit.SECONDS); // the session-start Admin request
    session.basket().addItem(BasketItem.sale("SKU-1", "Item", 1, new BigDecimal("100.00")));
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  /** A card payment the terminal approved in full. */
  private static String approved(String poiTxn, double authorized) {
    return payment("Success", poiTxn, authorized, "");
  }

  /** A card payment the terminal approved for less than it was asked. */
  private static String partial(String poiTxn, double authorized) {
    return payment("Partial", poiTxn, authorized, "");
  }

  private static String payment(String result, String poiTxn, double authorized, String amounts) {
    return "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
        + "\"Response\":{\"Result\":\""
        + result
        + "\"},"
        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\""
        + poiTxn
        + "\",\"TimeStamp\":\"2026-07-20T10:00:03Z\"}},"
        + "\"PaymentResult\":{"
        + "\"AmountsResp\":{\"Currency\":\"USD\",\"AuthorizedAmount\":"
        + authorized
        + amounts
        + "},"
        + "\"PaymentAcquirerData\":{\"ApprovalCode\":\"A-"
        + poiTxn
        + "\"},"
        + "\"PaymentInstrumentData\":{\"CardData\":{\"PaymentBrand\":\"Visa\"}}}}}}";
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

  private static String saleTransactionId(SaleToPOIRequest request) {
    return request.getPaymentRequest().getSaleData().getSaleTransactionID().getTransactionID();
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

  private static void assertMoney(String expected, BigDecimal actual) {
    assertNotNull(actual);
    assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected);
  }

  private static void assertMoney(String expected, Double actual) {
    assertNotNull(actual);
    assertEquals(new BigDecimal(expected).doubleValue(), actual, 0.0001);
  }

  /** Asserts a split continuation: the flag, a Normal payment type and the given PaidAmount. */
  private static void assertSplit(SaleToPOIRequest request, String requested, String paid) {
    assertMoney(requested, amounts(request).getRequestedAmount());
    assertMoney(paid, amounts(request).getPaidAmount());
    PaymentData data = request.getPaymentRequest().getPaymentData();
    assertNotNull(data, "a split continuation carries PaymentData");
    assertEquals(Boolean.TRUE, data.getSplitPaymentFlag());
    assertEquals("Normal", data.getPaymentType().toValue());
  }

  // ─── A single $100 payment approving $60 ───

  @Test
  void anotherCardChargesTheShortfallAfterTheMovementAndOnCardCharged() throws Exception {
    enqueue(partial("POI-PAY-1", 60.00), approved("POI-PAY-2", 40.00));
    List<String> events = new ArrayList<>();
    List<PartialCardApproval> asked = new ArrayList<>();

    SettlementResult result =
        session
            .settle()
            .onMovement(movement -> events.add("movement " + movement.getAmount()))
            .onCardCharged(movement -> events.add("charged " + movement.getAmount()))
            .onPartialCardApproval(
                partial -> {
                  events.add("partial");
                  asked.add(partial);
                  return PartialApprovalDecision.anotherCard();
                })
            .get();

    assertEquals(
        List.of("movement 60.00", "charged 60.00", "partial", "movement 40.00", "charged 40.00"),
        events);
    PartialCardApproval partial = asked.get(0);
    assertMoney("60.00", partial.getApproved());
    assertMoney("100.00", partial.getRequested());
    assertMoney("40.00", partial.getShortfall());
    assertMoney("40.00", partial.getAmountDue());
    assertEquals(1, partial.getCardPayments().size());
    assertTrue(partial.getCardPayments().get(0).isPartialApproval());
    assertEquals(SettlementStep.CARD_CHARGE, partial.getMovement().getStep());
    assertEquals("POI-PAY-1", partial.getMovement().getPoiTransactionId());

    assertTrue(result.isSuccess());
    assertMoney("100.00", result.getCardAmountCharged());
    assertMoney("100.00", result.getAuthorizedAmount());
    List<CardPaymentResult> cards = result.getCardPayments();
    assertEquals(2, cards.size());
    assertMoney("60.00", cards.get(0).getAmount());
    assertTrue(cards.get(0).isPartialApproval());
    assertMoney("40.00", cards.get(1).getAmount());
    assertFalse(cards.get(1).isPartialApproval());
    assertEquals("POI-PAY-1", result.getPoiTransactionId(), "the single getters: first payment");
    assertEquals(2, result.getMovements().size());

    List<SaleToPOIRequest> payments = payments(drainRequests());
    assertEquals(2, payments.size());
    assertMoney("100.00", amounts(payments.get(0)).getRequestedAmount());
    assertNull(amounts(payments.get(0)).getPaidAmount());
    assertNull(payments.get(0).getPaymentRequest().getPaymentData());
    assertSplit(payments.get(1), "40.00", "60.00");
    assertEquals(
        saleTransactionId(payments.get(0)),
        saleTransactionId(payments.get(1)),
        "every split payment carries the first payment's SaleTransactionID");
  }

  @Test
  void anotherCardThatIsPartialAgainAsksAgain() throws Exception {
    enqueue(partial("POI-PAY-1", 60.00), partial("POI-PAY-2", 25.00), approved("POI-PAY-3", 15.00));
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

    assertEquals(2, asked.size());
    PartialCardApproval second = asked.get(1);
    assertMoney("25.00", second.getApproved());
    assertMoney("40.00", second.getRequested());
    assertMoney("15.00", second.getShortfall());
    assertMoney("15.00", second.getAmountDue());
    assertEquals(2, second.getCardPayments().size());
    assertEquals("POI-PAY-2", second.getMovement().getPoiTransactionId());

    assertMoney("100.00", result.getCardAmountCharged());
    assertEquals(3, result.getCardPayments().size());

    List<SaleToPOIRequest> payments = payments(drainRequests());
    assertEquals(3, payments.size());
    assertSplit(payments.get(1), "40.00", "60.00");
    assertSplit(payments.get(2), "15.00", "85.00");
    assertEquals(saleTransactionId(payments.get(0)), saleTransactionId(payments.get(2)));
  }

  @Test
  void externalPaysTheRestAndKeepsTheCardPayment() throws Exception {
    enqueue(partial("POI-PAY-1", 60.00));
    List<String> external = new ArrayList<>();

    SettlementResult result =
        session
            .settle()
            .onExternallyPaid(movement -> external.add(movement.getExternalTenderType()))
            .onPartialCardApproval(
                partial ->
                    PartialApprovalDecision.external(
                        ExternalPayment.cash(partial.getAmountDue(), "DRAWER-1")))
            .get();

    assertEquals(List.of("CASH"), external);
    assertMoney("60.00", result.getCardAmountCharged());
    assertMoney("40.00", result.getExternalPaymentAmount());
    assertEquals(1, result.getCardPayments().size());
    assertEquals(
        List.of(SettlementStep.CARD_CHARGE, SettlementStep.EXTERNAL_PAYMENT),
        result.getMovements().stream().map(m -> m.getStep()).collect(Collectors.toList()));
    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(1, requests.size(), "no reversal: the card payment stays");
  }

  @Test
  void abortReversesThePartialAndDoesNotReachOnError() throws Exception {
    enqueue(partial("POI-PAY-1", 60.00), REVERSAL_OK);
    AtomicInteger onError = new AtomicInteger();

    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle()
                    .onError(
                        error -> {
                          onError.incrementAndGet();
                          return SettlementRecovery.abort();
                        })
                    .onPartialCardApproval(partial -> PartialApprovalDecision.abort())
                    .get());

    assertEquals(SessionErrorCode.DECLINED, failure.getError().getCode());
    assertTrue(failure.getError().getMessage().contains("the register aborted the settlement"));
    assertEquals(0, onError.get(), "the register chose the abort itself");
    assertEquals(List.of("POI-PAY-1"), reversed(drainRequests()));
  }

  @Test
  void abortAfterSeveralCardPaymentsReversesEveryOneNewestFirst() throws Exception {
    enqueue(partial("POI-PAY-1", 60.00), partial("POI-PAY-2", 25.00), REVERSAL_OK, REVERSAL_OK);

    assertThrows(
        SessionException.class,
        () ->
            session
                .settle()
                .onPartialCardApproval(
                    partial ->
                        partial.getCardPayments().size() < 2
                            ? PartialApprovalDecision.anotherCard()
                            : PartialApprovalDecision.abort())
                .get());

    assertEquals(List.of("POI-PAY-2", "POI-PAY-1"), reversed(drainRequests()));
  }

  @Test
  void aRegisteredHandlerHasNoLimit() throws Exception {
    enqueue(
        partial("POI-PAY-1", 60.00),
        partial("POI-PAY-2", 20.00),
        partial("POI-PAY-3", 10.00),
        partial("POI-PAY-4", 5.00),
        approved("POI-PAY-5", 5.00));

    SettlementResult result =
        session
            .settle()
            .onPartialCardApproval(partial -> PartialApprovalDecision.anotherCard())
            .get();

    assertEquals(5, result.getCardPayments().size());
    assertMoney("100.00", result.getCardAmountCharged());
  }

  // ─── Without a handler: automatic follow-up requests ───

  @Test
  void withoutAHandlerTheShortfallGoesOnAnotherCardRequest() throws Exception {
    enqueue(partial("POI-PAY-1", 60.00), approved("POI-PAY-2", 40.00));
    List<String> charged = new ArrayList<>();

    SettlementResult result =
        session
            .settle()
            .onCardCharged(movement -> charged.add(movement.getPoiTransactionId()))
            .get();

    assertEquals(List.of("POI-PAY-1", "POI-PAY-2"), charged);
    assertMoney("100.00", result.getCardAmountCharged());
    assertTrue(result.getCardPayments().get(0).isPartialApproval());
    List<SaleToPOIRequest> payments = payments(drainRequests());
    assertEquals(2, payments.size());
    assertSplit(payments.get(1), "40.00", "60.00");
    assertEquals(saleTransactionId(payments.get(0)), saleTransactionId(payments.get(1)));
  }

  @Test
  void withoutAHandlerFollowUpsWorkWithImplicitLoyalty() throws Exception {
    // unlike planned card payments, a partial approval's follow-up is allowed with implicit
    // loyalty: the terminal books the earn only on the payment that completes the sale
    enqueue(partial("POI-PAY-1", 60.00), approved("POI-PAY-2", 40.00));

    SettlementResult result =
        session
            .settle(SettlementOptions.builder().loyalty(SettlementLoyalty.implicit()).build())
            .get();

    assertMoney("100.00", result.getCardAmountCharged());
    List<SaleToPOIRequest> payments = payments(drainRequests());
    assertEquals(2, payments.size());
    assertSplit(payments.get(1), "40.00", "60.00");
    assertNull(
        payments.get(1).getPaymentRequest().getPaymentTransaction().getTransactionConditions(),
        "implicit loyalty sends no LoyaltyHandling on the follow-up either");
  }

  @Test
  void withoutAHandlerTheThirdPartialRequestFailsAsDeclined() throws Exception {
    assertEquals(3, SettlementFlow.MAX_AUTOMATIC_CARD_REQUESTS);
    enqueue(
        partial("POI-PAY-1", 60.00),
        partial("POI-PAY-2", 20.00),
        partial("POI-PAY-3", 10.00),
        REVERSAL_OK,
        REVERSAL_OK,
        REVERSAL_OK);
    List<SettlementFailure> failures = new ArrayList<>();

    assertNull(
        session
            .settle()
            .onError(
                failure -> {
                  failures.add(failure);
                  return SettlementRecovery.abort();
                })
            .getOrNull());

    assertEquals(1, failures.size());
    assertEquals(SessionErrorCode.DECLINED, failures.get(0).getCode());
    assertMoney("20.00", failures.get(0).getAmountDue());
    assertEquals(3, failures.get(0).getCommittedMovements().size());
    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(3, payments(requests).size(), "three card requests in total");
    assertSplit(payments(requests).get(1), "40.00", "60.00");
    assertSplit(payments(requests).get(2), "20.00", "80.00");
    assertEquals(List.of("POI-PAY-3", "POI-PAY-2", "POI-PAY-1"), reversed(requests));
  }

  @Test
  void withoutAHandlerExternalAfterTheLimitKeepsTheEarlierPartials() throws Exception {
    enqueue(
        partial("POI-PAY-1", 60.00),
        partial("POI-PAY-2", 20.00),
        partial("POI-PAY-3", 10.00),
        REVERSAL_OK);

    SettlementResult result =
        session
            .settle()
            .onError(
                failure ->
                    SettlementRecovery.external(ExternalPayment.cash(failure.getAmountDue())))
            .get();

    assertMoney("80.00", result.getCardAmountCharged());
    assertMoney("20.00", result.getExternalPaymentAmount());
    assertEquals(2, result.getCardPayments().size());
    assertEquals(
        List.of("POI-PAY-3"),
        reversed(drainRequests()),
        "external() first reverses only the failed last request");
  }

  @Test
  void withoutAHandlerTheLimitCountsPerSettlementAndPlannedPaymentsDoNot() throws Exception {
    // plan [20, rest]: 20 approves 15 (follow-up 1: 5, approved), then the remainder 80 approves
    // 70 (follow-up 2: 10) and that follow-up approves 5: the automatic requests are used up
    enqueue(
        partial("POI-PAY-1", 15.00),
        approved("POI-PAY-2", 5.00),
        partial("POI-PAY-3", 70.00),
        partial("POI-PAY-4", 5.00),
        REVERSAL_OK,
        REVERSAL_OK,
        REVERSAL_OK,
        REVERSAL_OK);
    List<SettlementFailure> failures = new ArrayList<>();

    assertNull(
        session
            .settle(plan("20.00"))
            .onError(
                failure -> {
                  failures.add(failure);
                  return SettlementRecovery.abort();
                })
            .getOrNull());

    assertEquals(SessionErrorCode.DECLINED, failures.get(0).getCode());
    assertMoney("10.00", failures.get(0).getAmountDue());
    List<SaleToPOIRequest> requests = drainRequests();
    List<SaleToPOIRequest> payments = payments(requests);
    assertEquals(4, payments.size());
    assertSplit(payments.get(1), "5.00", "15.00");
    assertSplit(payments.get(2), "80.00", "20.00");
    assertSplit(payments.get(3), "10.00", "90.00");
    assertEquals(List.of("POI-PAY-4", "POI-PAY-3", "POI-PAY-2", "POI-PAY-1"), reversed(requests));
  }

  @Test
  void withoutAHandlerACashbackPartialStillFailsAsDeclined() throws Exception {
    enqueue(approved("POI-PAY-1", 100.00), REVERSAL_OK);

    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle(SettlementOptions.builder().cashback(new BigDecimal("20.00")).build())
                    .get());

    assertEquals(SessionErrorCode.DECLINED, failure.getError().getCode());
    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(1, payments(requests).size(), "no automatic follow-up on a cashback sale");
    assertEquals(List.of("POI-PAY-1"), reversed(requests));
  }

  // ─── A plan [20, rest] where payment 1 approves $15 ───

  private static SettlementOptions plan(String... amounts) {
    CardPayment[] payments = new CardPayment[amounts.length];
    for (int i = 0; i < amounts.length; i++) {
      payments[i] = CardPayment.of(new BigDecimal(amounts[i]));
    }
    return SettlementOptions.builder().cardPayments(payments).build();
  }

  @Test
  void addToNextGrowsTheRemainderPayment() throws Exception {
    enqueue(partial("POI-PAY-1", 15.00), approved("POI-PAY-2", 85.00));
    List<PartialCardApproval> asked = new ArrayList<>();

    SettlementResult result =
        session
            .settle(plan("20.00"))
            .onPartialCardApproval(
                partial -> {
                  asked.add(partial);
                  return PartialApprovalDecision.addToNext();
                })
            .get();

    assertMoney("5.00", asked.get(0).getShortfall());
    assertMoney("85.00", asked.get(0).getAmountDue());
    assertMoney("100.00", result.getCardAmountCharged());
    List<SaleToPOIRequest> payments = payments(drainRequests());
    assertEquals(2, payments.size());
    assertMoney("20.00", amounts(payments.get(0)).getRequestedAmount());
    assertSplit(payments.get(1), "85.00", "15.00");
  }

  @Test
  void addToNextGrowsTheNextPlannedPayment() throws Exception {
    enqueue(
        partial("POI-PAY-1", 15.00), approved("POI-PAY-2", 35.00), approved("POI-PAY-3", 50.00));

    session
        .settle(plan("20.00", "30.00"))
        .onPartialCardApproval(partial -> PartialApprovalDecision.addToNext())
        .get();

    List<SaleToPOIRequest> payments = payments(drainRequests());
    assertEquals(3, payments.size());
    assertSplit(payments.get(1), "35.00", "15.00");
    assertSplit(payments.get(2), "50.00", "50.00");
  }

  @Test
  void anotherCardChargesTheShortfallBeforeTheRemainder() throws Exception {
    enqueue(partial("POI-PAY-1", 15.00), approved("POI-PAY-2", 5.00), approved("POI-PAY-3", 80.00));

    SettlementResult result =
        session
            .settle(plan("20.00"))
            .onPartialCardApproval(partial -> PartialApprovalDecision.anotherCard())
            .get();

    assertEquals(3, result.getCardPayments().size());
    List<SaleToPOIRequest> payments = payments(drainRequests());
    assertEquals(3, payments.size());
    assertSplit(payments.get(1), "5.00", "15.00");
    assertSplit(payments.get(2), "80.00", "20.00");
    assertEquals(saleTransactionId(payments.get(0)), saleTransactionId(payments.get(2)));
  }

  @Test
  void addToNextOnTheLastPaymentIsRefusedAndLeavesItForTheVoid() throws Exception {
    enqueue(partial("POI-PAY-1", 60.00));
    List<SettlementFailure> failures = new ArrayList<>();

    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle()
                    .onError(
                        error -> {
                          failures.add(error);
                          return SettlementRecovery.abort();
                        })
                    .onPartialCardApproval(partial -> PartialApprovalDecision.addToNext())
                    .get());

    assertEquals(SessionErrorCode.INVALID_STATE, failure.getError().getCode());
    assertTrue(failure.getError().getMessage().contains("addToNext()"));
    assertEquals(
        List.of(SessionErrorCode.INVALID_STATE),
        failures.stream().map(SettlementFailure::getCode).collect(Collectors.toList()),
        "a refused decision is reported to onError, like a refused recovery");
    assertEquals(1, drainRequests().size(), "refused like an invalid recovery: nothing unwinds");

    enqueue(REVERSAL_OK);
    assertTrue(session.voidTransaction().get().isSuccess());
    assertEquals(List.of("POI-PAY-1"), reversed(drainRequests()));
  }

  @Test
  void externalForLessThanTheAmountDueIsRefused() throws Exception {
    enqueue(partial("POI-PAY-1", 15.00));

    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle(plan("20.00"))
                    .onPartialCardApproval(
                        partial ->
                            PartialApprovalDecision.external(
                                ExternalPayment.cash(partial.getShortfall())))
                    .get());

    assertEquals(SessionErrorCode.INVALID_STATE, failure.getError().getCode());
    assertTrue(
        failure.getError().getMessage().contains("external payment 5.00 must equal"),
        failure.getError().getMessage());
  }

  // ─── When the handler is not called ───

  @Test
  void aPartialOnACashbackSaleFailsAsDeclined() throws Exception {
    // approves the sale but not the cashback
    enqueue(approved("POI-PAY-1", 100.00), REVERSAL_OK);
    assertCashbackPartialDeclined();
  }

  @Test
  void aRefusedCashbackFailsAsDeclined() throws Exception {
    // nexo: a refused cashback comes back Partial with CashBackAmount=0
    enqueue(payment("Partial", "POI-PAY-1", 100.00, ",\"CashBackAmount\":0"), REVERSAL_OK);
    assertCashbackPartialDeclined();
  }

  private void assertCashbackPartialDeclined() throws Exception {
    AtomicInteger asked = new AtomicInteger();
    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle(SettlementOptions.builder().cashback(new BigDecimal("20.00")).build())
                    .onPartialCardApproval(
                        partial -> {
                          asked.incrementAndGet();
                          return PartialApprovalDecision.anotherCard();
                        })
                    .get());

    assertEquals(SessionErrorCode.DECLINED, failure.getError().getCode());
    assertEquals(0, asked.get(), "the handler is never consulted on a cashback sale");
    assertEquals(List.of("POI-PAY-1"), reversed(drainRequests()));
  }

  @Test
  void aZeroAuthorizationIsADecline() throws Exception {
    enqueue(partial("POI-PAY-1", 0.00), REVERSAL_OK);
    AtomicInteger asked = new AtomicInteger();

    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle()
                    .onPartialCardApproval(
                        partial -> {
                          asked.incrementAndGet();
                          return PartialApprovalDecision.anotherCard();
                        })
                    .get());

    assertEquals(SessionErrorCode.DECLINED, failure.getError().getCode());
    assertEquals(0, asked.get());
  }

  // ─── A handler that throws ───

  @Test
  void aThrowingHandlerGoesToOnErrorAndRetryAsksAgain() throws Exception {
    enqueue(partial("POI-PAY-1", 60.00), approved("POI-PAY-2", 40.00));
    AtomicInteger calls = new AtomicInteger();
    List<SettlementFailure> failures = new ArrayList<>();

    SettlementResult result =
        session
            .settle()
            .onError(
                failure -> {
                  failures.add(failure);
                  return SettlementRecovery.retry();
                })
            .onPartialCardApproval(
                partial -> {
                  if (calls.incrementAndGet() == 1) {
                    throw new IllegalStateException("register UI not ready");
                  }
                  return PartialApprovalDecision.anotherCard();
                })
            .get();

    assertEquals(2, calls.get());
    assertEquals(1, failures.size());
    assertEquals(SessionErrorCode.UNKNOWN, failures.get(0).getCode());
    assertEquals(SettlementStep.CARD_CHARGE, failures.get(0).getStep());
    assertMoney("40.00", failures.get(0).getAmountDue());
    assertTrue(failures.get(0).getMessage().contains("onPartialCardApproval"));
    assertMoney("100.00", result.getCardAmountCharged());
  }

  @Test
  void aThrowingHandlerWithoutOnErrorAbortsAndReverses() throws Exception {
    enqueue(partial("POI-PAY-1", 60.00), REVERSAL_OK);

    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle()
                    .onPartialCardApproval(
                        partial -> {
                          throw new IllegalStateException("boom");
                        })
                    .get());

    assertEquals(SessionErrorCode.UNKNOWN, failure.getError().getCode());
    assertEquals(List.of("POI-PAY-1"), reversed(drainRequests()));
  }

  @Test
  void aNullDecisionIsTreatedAsAThrowingHandler() throws Exception {
    enqueue(partial("POI-PAY-1", 60.00), REVERSAL_OK);
    Function<PartialCardApproval, PartialApprovalDecision> none = partial -> null;

    SessionException failure =
        assertThrows(
            SessionException.class, () -> session.settle().onPartialCardApproval(none).get());

    assertEquals(SessionErrorCode.UNKNOWN, failure.getError().getCode());
    assertTrue(failure.getError().getMessage().contains("returned no decision"));
    assertEquals(List.of("POI-PAY-1"), reversed(drainRequests()));
  }

  @Test
  void aThrowingHandlerCannotBeSkipped() throws Exception {
    enqueue(partial("POI-PAY-1", 60.00));

    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle()
                    .onError(error -> SettlementRecovery.skip())
                    .onPartialCardApproval(
                        partial -> {
                          throw new IllegalStateException("boom");
                        })
                    .get());

    assertEquals(SessionErrorCode.INVALID_STATE, failure.getError().getCode());
  }

  // ─── After the sale ───

  @Test
  void theRecordAndASameSessionVoidCoverEveryCardPayment() throws Exception {
    enqueue(partial("POI-PAY-1", 60.00), approved("POI-PAY-2", 40.00));
    SettlementResult result =
        session
            .settle()
            .onPartialCardApproval(partial -> PartialApprovalDecision.anotherCard())
            .get();
    drainRequests();

    OriginalSaleRecord record = result.toOriginalSaleRecord(null);
    assertEquals(2, record.getCardPayments().size());
    assertEquals("POI-PAY-1", record.getCardPayments().get(0).getPoiTransactionId());
    assertMoney("60.00", record.getCardPayments().get(0).getAmount());
    assertEquals("POI-PAY-2", record.getCardPayments().get(1).getPoiTransactionId());
    assertMoney("40.00", record.getCardPayments().get(1).getAmount());
    assertEquals("POI-PAY-1", record.getCardPoiTransactionId());

    enqueue(REVERSAL_OK, REVERSAL_OK);
    VoidResult voided = session.voidTransaction().get();

    assertTrue(voided.isSuccess());
    assertEquals(List.of("POI-PAY-2", "POI-PAY-1"), reversed(drainRequests()));
  }
}
