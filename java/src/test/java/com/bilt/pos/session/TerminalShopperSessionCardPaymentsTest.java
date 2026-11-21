package com.bilt.pos.session;

import static org.junit.jupiter.api.Assertions.*;

import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.settlement.CardPayment;
import com.bilt.pos.session.settlement.CardPaymentRecord;
import com.bilt.pos.session.settlement.OriginalSaleRecord;
import com.bilt.pos.session.settlement.RefundAllocation;
import com.bilt.pos.session.settlement.SettlementLoyalty;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.bilt.pos.session.settlement.SettlementResult;
import com.bilt.pos.session.settlement.SettlementStep;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
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
 * Voids and refunds of a sale with several card payments: each card payment is its own reversal or
 * refund, newest first, and a retried partial one resumes at the first card payment still standing.
 */
class TerminalShopperSessionCardPaymentsTest {

  private static final Instant ORIGINAL_TIME = Instant.parse("2026-07-20T10:00:00Z");
  private static final String CARD_1 = "POI-CARD-1";
  private static final String CARD_2 = "POI-CARD-2";
  private static final String CARD_3 = "POI-CARD-3";
  private static final String STORED_VALUE = "POI-SV-1";
  private static final String AWARD = "POI-AWARD-1";
  private static final String REVERSAL_UNREACHABLE =
      "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
          + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"UnreachableHost\"}}}}";
  private static final String IDENTIFY_OK =
      "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
          + "\"Response\":{\"Result\":\"Success\"},"
          + "\"LoyaltyAccount\":[{\"LoyaltyAccountID\":{\"LoyaltyID\":\"98234\"},"
          + "\"LoyaltyBrand\":\"K-Club\"}]}}}";
  private static final String GIFT_CARD_PARTIAL_5 =
      "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
          + "\"Response\":{\"Result\":\"Partial\"},"
          + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\""
          + STORED_VALUE
          + "\",\"TimeStamp\":\"2026-07-20T10:00:00Z\"}},"
          + "\"PaymentResult\":{\"AmountsResp\":{\"AuthorizedAmount\":5.00}}}}}";
  private static final String AWARD_OK =
      "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
          + "\"Response\":{\"Result\":\"Success\"},"
          + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\""
          + AWARD
          + "\",\"TimeStamp\":\"2026-07-20T10:00:00Z\"}},"
          + "\"LoyaltyResult\":[{\"CurrentBalance\":789,"
          + "\"LoyaltyAmount\":{\"AmountValue\":65,\"LoyaltyUnit\":\"Point\"}}]}}}";
  private static final String REFUND_DECLINED =
      "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
          + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Refusal\"}}}}";

  private final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private MockWebServer server;
  private TerminalShopperSession session;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.setDispatcher(TerminalShopperSessionTest.adminAnsweringDispatcher());
    server.start();
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
    server.takeRequest(5, TimeUnit.SECONDS); // drain the session-start Admin request
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  // ─── Void of a prior sale ───

  @Test
  void priorSaleVoidReversesEveryCardPaymentNewestFirstBeforeTheOtherLegs() throws Exception {
    server.enqueue(new MockResponse().setBody(reversalOk("POI-REV-3", 30.00)));
    server.enqueue(new MockResponse().setBody(reversalOk("POI-REV-2", 20.00)));
    server.enqueue(new MockResponse().setBody(reversalOk("POI-REV-1", 10.00)));
    server.enqueue(new MockResponse().setBody(reversalOk("POI-REV-SV", 5.00)));
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.LOYALTY_REFUND_OK));

    VoidResult result = session.voidTransaction(threeCardSale().memberId("98234").build()).get();

    assertTrue(result.isSuccess());
    assertEquals(0, new BigDecimal("65.00").compareTo(result.getReversedAmount()));
    assertEquals(
        "POI-REV-3", result.getPoiTransactionId(), "the first reversal sent is the newest card's");
    assertEquals(
        List.of(
            new TenderAmount(TenderType.CARD, CARD_3, new BigDecimal("30.00")),
            new TenderAmount(TenderType.CARD, CARD_2, new BigDecimal("20.00")),
            new TenderAmount(TenderType.CARD, CARD_1, new BigDecimal("10.00")),
            new TenderAmount(TenderType.STORED_VALUE, STORED_VALUE, new BigDecimal("5.00"))),
        result.getReversedAmountByTender());
    assertSumsToTotal(result.getReversedAmountByTender(), result.getReversedAmount());
    List<TenderMovement> cards = result.getCardReversals();
    assertEquals(
        List.of(CARD_3, CARD_2, CARD_1),
        cards.stream()
            .map(TenderMovement::getOriginalPoiTransactionId)
            .collect(Collectors.toList()));
    assertEquals(
        List.of("POI-REV-3", "POI-REV-2", "POI-REV-1"),
        cards.stream().map(TenderMovement::getPoiTransactionId).collect(Collectors.toList()),
        "each card payment keeps its own reversal reference");
    assertEquals(
        3,
        cards.stream().map(TenderMovement::getServiceId).distinct().count(),
        "each reversal is its own request");
    assertEquals(result.getServiceId(), cards.get(0).getServiceId());
    assertTrue(cards.stream().allMatch(c -> c.getTenderType() == TenderType.CARD));
    TenderMovement giftCard = result.getStoredValueReversal();
    assertNotNull(giftCard, "the stored value leg keeps its own reversal and receipts");
    assertEquals(TenderType.STORED_VALUE, giftCard.getTenderType());
    assertEquals(STORED_VALUE, giftCard.getOriginalPoiTransactionId());
    assertEquals("POI-REV-SV", giftCard.getPoiTransactionId());
    assertEquals(0, new BigDecimal("5.00").compareTo(giftCard.getAmount()));
    assertEquals(0, new BigDecimal("30.00").compareTo(cards.get(0).getAmount()));
    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(List.of(CARD_3, CARD_2, CARD_1, STORED_VALUE), reversedIds(requests));
    assertEquals(
        "AwardRefund",
        requests
            .get(4)
            .getLoyaltyRequest()
            .getLoyaltyTransaction()
            .getLoyaltyTransactionType()
            .toValue());
  }

  @Test
  void priorSaleVoidRetryResumesAtTheFirstCardPaymentStillStanding() throws Exception {
    OriginalSaleRecord sale = threeCardSale().build();
    server.enqueue(new MockResponse().setBody(reversalOk("POI-REV-3", 30.00)));
    server.enqueue(new MockResponse().setBody(REVERSAL_UNREACHABLE));

    SessionException failure =
        assertThrows(SessionException.class, () -> session.voidTransaction(sale).get());

    assertTrue(failure.getError().getMessage().contains(CARD_3 + " was reversed"));
    assertTrue(failure.getError().getMessage().contains(CARD_2 + " was not"));
    assertEquals(
        List.of(CARD_3),
        failure.getError().getReversedMovements().stream()
            .map(ReversedMovement::getPoiTransactionId)
            .collect(Collectors.toList()));
    assertEquals(List.of(CARD_3, CARD_2), reversedIds(drainRequests()));

    server.enqueue(new MockResponse().setBody(reversalOk("POI-REV-2", 20.00)));
    server.enqueue(new MockResponse().setBody(reversalOk("POI-REV-1", 10.00)));
    server.enqueue(new MockResponse().setBody(reversalOk("POI-REV-SV", 5.00)));
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.LOYALTY_REFUND_OK));

    VoidResult retried = session.voidTransaction(sale).get();

    assertTrue(retried.isSuccess());
    assertEquals(
        0,
        new BigDecimal("35.00").compareTo(retried.getReversedAmount()),
        "a resumed void reports only what it reversed this time");
    assertEquals(
        List.of(CARD_2, CARD_1),
        retried.getCardReversals().stream()
            .map(TenderMovement::getOriginalPoiTransactionId)
            .collect(Collectors.toList()),
        "a resumed void lists only the card payments it reversed");
    assertSumsToTotal(retried.getReversedAmountByTender(), retried.getReversedAmount());
    List<SaleToPOIRequest> retry = drainRequests();
    assertEquals(List.of(CARD_2, CARD_1, STORED_VALUE), reversedIds(retry));
    assertEquals(4, retry.size(), "the three remaining money legs and the award");
  }

  @Test
  void priorSaleVoidReversesAGiftCardOnlySaleOnceAlongsideCardPayments() throws Exception {
    server.enqueue(new MockResponse().setBody(reversalOk("POI-REV-2", 20.00)));
    server.enqueue(new MockResponse().setBody(reversalOk("POI-REV-1", 10.00)));

    // a record that also lists the stored value payment as a card payment (as gift-card-only
    // records do) must not reverse it twice
    VoidResult result =
        session
            .voidTransaction(
                OriginalSaleRecord.builder()
                    .addCardPayment(card(STORED_VALUE, "10.00"))
                    .addCardPayment(card(CARD_2, "20.00"))
                    .storedValuePoiTransactionId(STORED_VALUE)
                    .storedValuePoiTransactionTimestamp(ORIGINAL_TIME)
                    .build())
            .get();

    assertEquals(List.of(CARD_2, STORED_VALUE), reversedIds(drainRequests()));
    assertEquals(
        List.of(
            new TenderAmount(TenderType.CARD, CARD_2, new BigDecimal("20.00")),
            new TenderAmount(TenderType.STORED_VALUE, STORED_VALUE, new BigDecimal("10.00"))),
        result.getReversedAmountByTender(),
        "the gift card payment goes back to the gift card");
    assertEquals(1, result.getCardReversals().size());
    assertEquals(STORED_VALUE, result.getStoredValueReversal().getOriginalPoiTransactionId());
  }

  @Test
  void priorSaleVoidFallsBackToCardAmountsAndIsUnknownWithoutAStoredValueAmount() throws Exception {
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.REVERSAL_OK));
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.REVERSAL_OK));

    VoidResult cardsOnly =
        session
            .voidTransaction(
                OriginalSaleRecord.builder()
                    .addCardPayment(card(CARD_1, "10.00"))
                    .addCardPayment(card(CARD_2, "20.00"))
                    .build())
            .get();

    assertEquals(
        0,
        new BigDecimal("30.00").compareTo(cardsOnly.getReversedAmount()),
        "a full reversal returns each card payment's recorded amount");
    assertSumsToTotal(cardsOnly.getReversedAmountByTender(), cardsOnly.getReversedAmount());

    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.REVERSAL_OK));
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.REVERSAL_OK));

    VoidResult withGiftCard =
        session
            .voidTransaction(
                OriginalSaleRecord.builder()
                    .addCardPayment(card(CARD_3, "30.00"))
                    .storedValuePoiTransactionId(STORED_VALUE)
                    .storedValuePoiTransactionTimestamp(ORIGINAL_TIME)
                    .build())
            .get();

    assertNull(
        withGiftCard.getReversedAmount(),
        "the record has no stored value amount, so the total is unknown, not understated");
    assertEquals(
        List.of(
            new TenderAmount(TenderType.CARD, CARD_3, new BigDecimal("30.00")),
            new TenderAmount(TenderType.STORED_VALUE, STORED_VALUE, null)),
        withGiftCard.getReversedAmountByTender());
  }

  @Test
  void aLegacyGiftCardOnlyRecordIsVoidedOnceAsItsStoredValuePayment() throws Exception {
    server.enqueue(new MockResponse().setBody(reversalOk("POI-REV-SV", 62.31)));

    VoidResult result =
        session
            .voidTransaction(
                OriginalSaleRecord.builder()
                    .cardPoiTransactionId(STORED_VALUE)
                    .cardPoiTransactionTimestamp(ORIGINAL_TIME)
                    .storedValuePoiTransactionId(STORED_VALUE)
                    .storedValuePoiTransactionTimestamp(ORIGINAL_TIME)
                    .build())
            .get();

    assertEquals(List.of(STORED_VALUE), reversedIds(drainRequests()));
    assertTrue(result.getCardReversals().isEmpty(), "a gift card payment is not a card payment");
    assertEquals("POI-REV-SV", result.getStoredValueReversal().getPoiTransactionId());
    assertEquals(
        List.of(new TenderAmount(TenderType.STORED_VALUE, STORED_VALUE, new BigDecimal("62.31"))),
        result.getReversedAmountByTender());
  }

  // ─── Refund of a prior sale ───

  @Test
  void priorSaleRefundTargetsEachCardPaymentThroughItsOwnAllocation() throws Exception {
    OriginalSaleRecord sale = threeCardSale().build();
    session
        .basket()
        .addItem(BasketItem.returnItem("RET-1", "Returned item", 1, new BigDecimal("45.00")));
    server.enqueue(new MockResponse().setBody(refundOk("POI-REF-3", 30.00)));
    server.enqueue(new MockResponse().setBody(refundOk("POI-REF-2", 15.00)));

    CardPaymentRecord third = sale.getCardPayments().get(2);
    CardPaymentRecord second = sale.getCardPayments().get(1);
    SettlementResult result =
        session
            .settle(
                SettlementOptions.builder()
                    .addRefund(
                        RefundAllocation.card(
                            third.getAmount(),
                            third.getPoiTransactionId(),
                            third.getPoiTransactionTimestamp()))
                    .addRefund(
                        RefundAllocation.card(
                            new BigDecimal("15.00"),
                            second.getPoiTransactionId(),
                            second.getPoiTransactionTimestamp()))
                    .build())
            .get();

    assertTrue(result.isSuccess());
    assertEquals(0, new BigDecimal("45.00").compareTo(result.getCardRefundedAmount()));
    assertEquals(SettlementStep.CARD_REFUND, result.getMovements().get(0).getStep());
    assertEquals(SettlementStep.CARD_REFUND, result.getMovements().get(1).getStep());
    assertEquals(List.of(CARD_3, CARD_2), refundedIds(drainRequests()));
  }

  @Test
  void refundAllocationFromARecordWithSeveralCardPaymentsIsRefused() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> RefundAllocation.card(new BigDecimal("5.00"), threeCardSale().build()));
    assertTrue(e.getMessage().contains("RefundAllocation.card(amount, poiTransactionId"));
  }

  // ─── Same-session void and refund ───

  @Test
  void sessionVoidReversesEveryCardPaymentAndARetryResumes() throws Exception {
    settleThreeCardSale();
    server.enqueue(new MockResponse().setBody(reversalOk("POI-REV-3", 30.00)));
    server.enqueue(new MockResponse().setBody(reversalOk("POI-REV-2", 20.00)));
    server.enqueue(new MockResponse().setBody(REVERSAL_UNREACHABLE));

    assertThrows(SessionException.class, () -> session.voidTransaction().get());
    assertEquals(List.of(CARD_3, CARD_2, CARD_1), reversedIds(drainRequests()));

    SessionException refundRefused =
        assertThrows(SessionException.class, () -> session.refund().get());
    assertEquals(SessionErrorCode.INVALID_STATE, refundRefused.getError().getCode());
    assertTrue(refundRefused.getError().getMessage().contains("partially complete"));
    assertEquals(
        0, drainRequests().size(), "a refund of a half-voided sale must not reach the wire");

    server.enqueue(new MockResponse().setBody(reversalOk("POI-REV-1", 10.00)));
    server.enqueue(new MockResponse().setBody(reversalOk("POI-REV-SV", 5.00)));
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.LOYALTY_REFUND_OK));

    assertTrue(session.voidTransaction().get().isSuccess());
    assertEquals(List.of(CARD_1, STORED_VALUE), reversedIds(drainRequests()));
  }

  @Test
  void sessionRefundRefundsEveryCardPaymentInFullNewestFirst() throws Exception {
    settleThreeCardSale();
    server.enqueue(new MockResponse().setBody(refundOk("POI-REF-3", 30.00)));
    server.enqueue(new MockResponse().setBody(refundOk("POI-REF-2", 20.00)));
    server.enqueue(new MockResponse().setBody(refundOk("POI-REF-1", 10.00)));
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.LOYALTY_REFUND_OK));

    RefundResult result = session.refund().get();

    assertTrue(result.isSuccess());
    assertEquals(0, new BigDecimal("60.00").compareTo(result.getRefundedAmount()));
    assertEquals("POI-REF-3", result.getPoiTransactionId());
    assertEquals(
        List.of("POI-REF-3", "POI-REF-2", "POI-REF-1"),
        result.getCardRefunds().stream()
            .map(TenderMovement::getPoiTransactionId)
            .collect(Collectors.toList()));
    assertEquals(
        List.of(CARD_3, CARD_2, CARD_1),
        result.getCardRefunds().stream()
            .map(TenderMovement::getOriginalPoiTransactionId)
            .collect(Collectors.toList()));
    assertEquals(
        List.of(
            new TenderAmount(TenderType.CARD, CARD_3, new BigDecimal("30.00")),
            new TenderAmount(TenderType.CARD, CARD_2, new BigDecimal("20.00")),
            new TenderAmount(TenderType.CARD, CARD_1, new BigDecimal("10.00"))),
        result.getRefundedAmountByTender());
    assertSumsToTotal(result.getRefundedAmountByTender(), result.getRefundedAmount());
    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(List.of(CARD_3, CARD_2, CARD_1), refundedIds(requests));
    for (SaleToPOIRequest refund : requests.subList(0, 3)) {
      assertNull(
          refund.getPaymentRequest().getPaymentTransaction().getAmountsReq().getRequestedAmount(),
          "a full refund leaves each card payment's amount to the terminal");
    }
    assertEquals(
        "AwardRefund",
        requests
            .get(3)
            .getLoyaltyRequest()
            .getLoyaltyTransaction()
            .getLoyaltyTransactionType()
            .toValue());

    SessionException voidRefused =
        assertThrows(SessionException.class, () -> session.voidTransaction().get());
    assertEquals(SessionErrorCode.INVALID_STATE, voidRefused.getError().getCode());

    SessionException again = assertThrows(SessionException.class, () -> session.refund().get());
    assertEquals(SessionErrorCode.INVALID_STATE, again.getError().getCode());
    assertTrue(again.getError().getMessage().contains("already refunded in full"));
    assertEquals(0, drainRequests().size(), "a repeated full refund must not reach the wire");
  }

  @Test
  void sessionRefundFallsBackToTheCardPaymentsAmountWhenTheTerminalDoesNotEchoIt()
      throws Exception {
    settleTwoCardSale();
    server.enqueue(new MockResponse().setBody(refundWithoutAmount("POI-REF-2")));
    server.enqueue(new MockResponse().setBody(refundOk("POI-REF-1", 20.00)));

    RefundResult result = session.refund().get();

    assertEquals(0, new BigDecimal("50.00").compareTo(result.getRefundedAmount()));
    assertEquals(
        List.of(
            new TenderAmount(TenderType.CARD, CARD_2, new BigDecimal("30.00")),
            new TenderAmount(TenderType.CARD, CARD_1, new BigDecimal("20.00"))),
        result.getRefundedAmountByTender());
    assertSumsToTotal(result.getRefundedAmountByTender(), result.getRefundedAmount());
  }

  @Test
  void sessionRefundTotalIsUnknownWhenNoAmountIsKnownForACardRefund() throws Exception {
    // a record built from bare references has no card amounts to fall back on
    settledWith(
        OriginalSaleRecord.builder()
            .addCardPayment(CardPaymentRecord.builder().poiTransactionId(CARD_1).build())
            .addCardPayment(CardPaymentRecord.builder().poiTransactionId(CARD_2).build())
            .build());
    server.enqueue(new MockResponse().setBody(refundWithoutAmount("POI-REF-2")));
    server.enqueue(new MockResponse().setBody(refundOk("POI-REF-1", 20.00)));

    RefundResult result = session.refund().get();

    assertTrue(result.isSuccess());
    assertNull(
        result.getRefundedAmount(), "a sum missing one card refund would understate the refund");
    assertEquals(
        List.of(
            new TenderAmount(TenderType.CARD, CARD_2, null),
            new TenderAmount(TenderType.CARD, CARD_1, new BigDecimal("20.00"))),
        result.getRefundedAmountByTender());
    assertEquals(List.of(CARD_2, CARD_1), refundedIds(drainRequests()));
  }

  @Test
  void sessionRefundRetryResumesAtTheFirstCardPaymentStillStanding() throws Exception {
    settleThreeCardSale();
    server.enqueue(new MockResponse().setBody(refundOk("POI-REF-3", 30.00)));
    server.enqueue(new MockResponse().setBody(REFUND_DECLINED));

    SessionException failure = assertThrows(SessionException.class, () -> session.refund().get());
    assertTrue(failure.getError().getMessage().contains("the tender refund of " + CARD_3));
    assertTrue(failure.getError().getMessage().contains(CARD_2 + " was not refunded"));
    assertEquals(
        List.of(CARD_3),
        failure.getError().getReversedMovements().stream()
            .map(ReversedMovement::getPoiTransactionId)
            .collect(Collectors.toList()),
        "the completed card refunds are reported as structured progress");
    assertEquals(List.of(CARD_3, CARD_2), refundedIds(drainRequests()));

    // the progress lives in this session: nothing may discard it before the retry
    SessionException endRefused = assertThrows(SessionException.class, () -> session.end().get());
    assertEquals(SessionErrorCode.INVALID_STATE, endRefused.getError().getCode());
    assertTrue(endRefused.getError().getMessage().contains("retry refund()"));
    assertThrows(IllegalStateException.class, () -> session.basket().clear());
    SessionException settleRefused =
        assertThrows(SessionException.class, () -> session.settle().get());
    assertTrue(settleRefused.getError().getMessage().contains("retry refund()"));
    assertEquals(0, drainRequests().size());

    SessionException voidRefused =
        assertThrows(SessionException.class, () -> session.voidTransaction().get());
    assertEquals(
        SessionErrorCode.INVALID_STATE,
        voidRefused.getError().getCode(),
        "money already returned by the refund rules out a void");

    // a retry that stops again reports the earlier card refund along with its own
    server.enqueue(new MockResponse().setBody(refundOk("POI-REF-2", 20.00)));
    server.enqueue(new MockResponse().setBody(REFUND_DECLINED));
    SessionException stoppedAgain =
        assertThrows(SessionException.class, () -> session.refund().get());
    assertEquals(
        List.of(CARD_3, CARD_2),
        stoppedAgain.getError().getReversedMovements().stream()
            .map(ReversedMovement::getPoiTransactionId)
            .collect(Collectors.toList()));
    assertEquals(List.of(CARD_2, CARD_1), refundedIds(drainRequests()));

    server.enqueue(new MockResponse().setBody(refundOk("POI-REF-1", 10.00)));
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.LOYALTY_REFUND_OK));

    RefundResult retried = session.refund().get();

    assertEquals(0, new BigDecimal("10.00").compareTo(retried.getRefundedAmount()));
    assertEquals(List.of(CARD_1), refundedIds(drainRequests()));
    assertDoesNotThrow(() -> session.basket().clear(), "a finished refund lifts the guard");
  }

  @Test
  void sessionRefundOnErrorSeesTheCardPaymentsAlreadyRefunded() throws Exception {
    settledWith(threeCardSale().build());
    server.enqueue(new MockResponse().setBody(refundOk("POI-REF-3", 30.00)));
    server.enqueue(new MockResponse().setBody(REFUND_DECLINED));
    List<List<String>> seen = new ArrayList<>();

    assertThrows(
        SessionException.class,
        () ->
            session
                .refund()
                .onError(
                    (step, error) -> {
                      seen.add(
                          error.getReversedMovements().stream()
                              .map(ReversedMovement::getPoiTransactionId)
                              .collect(Collectors.toList()));
                      return ReversalDecision.ABORT;
                    })
                .get());

    assertEquals(
        List.of(List.of(CARD_3)),
        seen,
        "the handler can persist the completed refunds before it aborts");
    drainRequests();
  }

  @Test
  void anAwardAbortAfterEveryCardRefundKeepsTheProgressAndTheGuard() throws Exception {
    settledWith(
        OriginalSaleRecord.builder()
            .addCardPayment(card(CARD_1, "10.00"))
            .addCardPayment(card(CARD_2, "20.00"))
            .awardPoiTransactionId(AWARD)
            .awardPoiTransactionTimestamp(ORIGINAL_TIME)
            .build());
    server.enqueue(new MockResponse().setBody(refundOk("POI-REF-2", 20.00)));
    server.enqueue(new MockResponse().setBody(refundOk("POI-REF-1", 10.00)));
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.LOYALTY_REFUND_FAILED));

    SessionException failure =
        assertThrows(
            SessionException.class,
            () -> session.refund().onError((step, error) -> ReversalDecision.ABORT).get());

    assertEquals(
        List.of(CARD_2, CARD_1),
        failure.getError().getReversedMovements().stream()
            .map(ReversedMovement::getPoiTransactionId)
            .collect(Collectors.toList()),
        "the thrown error lists every card refunded before the award aborted");
    drainRequests();
    SessionException endRefused = assertThrows(SessionException.class, () -> session.end().get());
    assertTrue(endRefused.getError().getMessage().contains("retry refund()"));

    // under the default policy an award-only retry skips the failed award; with nothing else left
    // to send it fails, and still reports the cards refunded before
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.LOYALTY_REFUND_FAILED));
    SessionException skipped = assertThrows(SessionException.class, () -> session.refund().get());
    assertEquals(
        List.of(CARD_2, CARD_1),
        skipped.getError().getReversedMovements().stream()
            .map(ReversedMovement::getPoiTransactionId)
            .collect(Collectors.toList()),
        "a retry that skipped every step still restates the earlier refunds");
    drainRequests();

    // an award-only retry that fails again still reports the cards refunded before
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.LOYALTY_REFUND_FAILED));
    SessionException again =
        assertThrows(
            SessionException.class,
            () -> session.refund().onError((step, error) -> ReversalDecision.ABORT).get());
    assertEquals(
        List.of(CARD_2, CARD_1),
        again.getError().getReversedMovements().stream()
            .map(ReversedMovement::getPoiTransactionId)
            .collect(Collectors.toList()),
        "a retry's error restates what earlier attempts refunded");
    drainRequests();

    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.LOYALTY_REFUND_OK));
    assertTrue(session.refund().get().isSuccess());
    List<SaleToPOIRequest> retry = drainRequests();
    assertEquals(List.of(), refundedIds(retry), "the retry sends only the award");
    assertEquals(1, retry.size());
    assertDoesNotThrow(() -> session.basket().clear());
  }

  @Test
  void sessionPartialRefundWithSeveralCardPaymentsIsRefused() throws Exception {
    settleThreeCardSale();

    SessionException e =
        assertThrows(SessionException.class, () -> session.refund(new BigDecimal("5.00")).get());

    assertEquals(SessionErrorCode.INVALID_STATE, e.getError().getCode());
    assertTrue(e.getError().getMessage().contains("RefundAllocation.card(amount"));
    assertTrue(e.getError().getMessage().contains("return lines"));
    assertEquals(0, drainRequests().size());
  }

  @Test
  void sessionRefundOfAGiftCardOnlySaleReturnsTheStoredValuePayment() throws Exception {
    session.basket().addItem(BasketItem.sale("SKU-1", "Item", 1, new BigDecimal("40.00")));
    session.setStoredValueCard("GC-1234-5678");
    server.enqueue(new MockResponse().setBody(paymentOk(STORED_VALUE, 40.00)));
    SettlementResult sale = session.settle().get();
    drainRequests();
    assertTrue(sale.getCardPayments().isEmpty(), "a gift card payment is not a card payment");
    assertEquals(
        STORED_VALUE, sale.getPoiTransactionId(), "the single-payment getter is unchanged");
    OriginalSaleRecord record = sale.toOriginalSaleRecord(null);
    assertTrue(record.getCardPayments().isEmpty());
    assertEquals(STORED_VALUE, record.getCardPoiTransactionId());

    server.enqueue(new MockResponse().setBody(refundOk("POI-REF-SV", 40.00)));

    RefundResult refund = session.refund().get();

    assertEquals(List.of(STORED_VALUE), refundedIds(drainRequests()));
    assertTrue(refund.getCardRefunds().isEmpty());
    assertEquals(
        List.of(new TenderAmount(TenderType.STORED_VALUE, STORED_VALUE, new BigDecimal("40.00"))),
        refund.getRefundedAmountByTender());
    assertSumsToTotal(refund.getRefundedAmountByTender(), refund.getRefundedAmount());
  }

  @Test
  void sessionPartialRefundWithOneCardPaymentIsUnchanged() throws Exception {
    session.basket().addItem(BasketItem.sale("SKU-1", "Item", 1, new BigDecimal("100.00")));
    server.enqueue(new MockResponse().setBody(paymentOk(CARD_1, 100.00)));
    SettlementResult sale = session.settle().get();
    drainRequests();
    assertEquals(1, sale.getCardPayments().size());
    assertEquals(List.of(CARD_1), cardIds(sale.toOriginalSaleRecord(null)));

    server.enqueue(new MockResponse().setBody(refundOk("POI-REF-1", 15.00)));
    server.enqueue(new MockResponse().setBody(refundOk("POI-REF-2", 15.00)));

    assertTrue(session.refund(new BigDecimal("15.00")).get().isSuccess());
    assertTrue(
        session.refund(new BigDecimal("15.00")).get().isSuccess(),
        "repeated partial refunds of one card payment stay allowed");

    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(List.of(CARD_1, CARD_1), refundedIds(requests));
    assertEquals(
        15.00,
        requests
            .get(0)
            .getPaymentRequest()
            .getPaymentTransaction()
            .getAmountsReq()
            .getRequestedAmount());
  }

  // ─── Settlement ───

  @Test
  void splitTenderListsOnlyTheCardPaymentAndKeepsTheStoredValueFields() throws Exception {
    session.basket().addItem(BasketItem.sale("SKU-1", "Item", 1, new BigDecimal("100.00")));
    session.setStoredValueCard("GC-1234-5678");
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                    + "\"Response\":{\"Result\":\"Partial\"},"
                    + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\""
                    + STORED_VALUE
                    + "\",\"TimeStamp\":\"2026-07-20T10:00:01Z\"}},"
                    + "\"PaymentResult\":{\"AmountsResp\":{\"AuthorizedAmount\":40.00},"
                    + "\"PaymentAcquirerData\":{\"ApprovalCode\":\"SV-APPR\"}}}}}"));
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                    + "\"Response\":{\"Result\":\"Success\"},"
                    + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\""
                    + CARD_1
                    + "\",\"TimeStamp\":\"2026-07-20T10:00:02Z\"}},"
                    + "\"PaymentResult\":{\"AmountsResp\":{\"AuthorizedAmount\":60.00},"
                    + "\"PaymentInstrumentData\":{\"PaymentInstrumentType\":\"Card\","
                    + "\"CardData\":{\"PaymentBrand\":\"Visa\","
                    + "\"MaskedPAN\":\"411111******1111\"}},"
                    + "\"PaymentAcquirerData\":{"
                    + "\"AcquirerTransactionID\":{\"TransactionID\":\"ACQ-1\"}}}}}}"));

    SettlementResult result = session.settle().get();

    assertEquals(1, result.getCardPayments().size());
    var card = result.getCardPayments().get(0);
    assertEquals(0, new BigDecimal("60.00").compareTo(card.getAmount()));
    assertEquals(CARD_1, card.getPoiTransactionId());
    assertEquals(Instant.parse("2026-07-20T10:00:02Z"), card.getPoiTransactionTimestamp());
    assertEquals("Visa", card.getPaymentBrand());
    assertEquals("411111******1111", card.getMaskedPan());
    assertEquals("ACQ-1", card.getAcquirerTransactionId());
    assertNull(card.getApprovalCode(), "the card payment carries only its own approval code");
    assertFalse(card.isPartialApproval());
    assertEquals(0, new BigDecimal("60.00").compareTo(result.getCardAmountCharged()));

    // the single-payment fields describe the card payment, keeping the stored value
    // payment's values where the card response has none, as before
    assertEquals(CARD_1, result.getPoiTransactionId());
    assertEquals("Visa", result.getPaymentBrand());
    assertEquals("SV-APPR", result.getApprovalCode());
    assertEquals(STORED_VALUE, result.getStoredValuePoiTransactionId());
    assertEquals(
        Instant.parse("2026-07-20T10:00:01Z"), result.getStoredValuePoiTransactionTimestamp());

    OriginalSaleRecord record = result.toOriginalSaleRecord(null);
    assertEquals(List.of(CARD_1), cardIds(record));
    assertEquals(STORED_VALUE, record.getStoredValuePoiTransactionId());
  }

  // ─── Helpers ───

  private static OriginalSaleRecord.Builder threeCardSale() {
    return OriginalSaleRecord.builder()
        .addCardPayment(card(CARD_1, "10.00"))
        .addCardPayment(card(CARD_2, "20.00"))
        .addCardPayment(card(CARD_3, "30.00"))
        .storedValuePoiTransactionId(STORED_VALUE)
        .storedValuePoiTransactionTimestamp(ORIGINAL_TIME)
        .awardPoiTransactionId(AWARD)
        .awardPoiTransactionTimestamp(ORIGINAL_TIME);
  }

  private static CardPaymentRecord card(String poiTransactionId, String amount) {
    return CardPaymentRecord.builder()
        .poiTransactionId(poiTransactionId)
        .poiTransactionTimestamp(ORIGINAL_TIME)
        .amount(new BigDecimal(amount))
        .build();
  }

  /**
   * Settles a $65 sale for an identified member like {@link #threeCardSale()}: $5 on the gift card,
   * card payments of $10 and $20 planned up front, $30 on a third card for the remainder, and the
   * award.
   */
  private void settleThreeCardSale() throws Exception {
    server.enqueue(new MockResponse().setBody(IDENTIFY_OK));
    session.identifyMember().executeSync();
    session.basket().addItem(BasketItem.sale("SKU-1", "Item", 1, new BigDecimal("65.00")));
    session.setStoredValueCard("GC-1234-5678");
    server.enqueue(new MockResponse().setBody(GIFT_CARD_PARTIAL_5));
    server.enqueue(new MockResponse().setBody(paymentOk(CARD_1, 10.00)));
    server.enqueue(new MockResponse().setBody(paymentOk(CARD_2, 20.00)));
    server.enqueue(new MockResponse().setBody(paymentOk(CARD_3, 30.00)));
    server.enqueue(new MockResponse().setBody(AWARD_OK));

    SettlementResult sale =
        session
            .settle(
                SettlementOptions.builder()
                    .loyalty(SettlementLoyalty.explicit(EnumSet.of(SettlementLoyalty.Step.AWARD)))
                    .cardPayments(
                        CardPayment.of(new BigDecimal("10.00")),
                        CardPayment.of(new BigDecimal("20.00")))
                    .build())
            .get();

    assertEquals(List.of(CARD_1, CARD_2, CARD_3), cardIds(sale.toOriginalSaleRecord(null)));
    assertEquals(STORED_VALUE, sale.getStoredValuePoiTransactionId());
    assertEquals(AWARD, sale.getAwardPoiTransactionId());
    drainRequests();
  }

  /** Settles a $50 guest sale as $20 on a first card and the remaining $30 on a second. */
  private void settleTwoCardSale() throws Exception {
    session.basket().addItem(BasketItem.sale("SKU-1", "Item", 1, new BigDecimal("50.00")));
    server.enqueue(new MockResponse().setBody(paymentOk(CARD_1, 20.00)));
    server.enqueue(new MockResponse().setBody(paymentOk(CARD_2, 30.00)));
    session
        .settle(
            SettlementOptions.builder()
                .cardPayments(CardPayment.of(new BigDecimal("20.00")))
                .build())
        .get();
    drainRequests();
  }

  /**
   * Makes {@code record} the session's most recent settlement. Only for records no settlement
   * produces, such as card payments without amounts; the other tests settle for real.
   */
  private void settledWith(OriginalSaleRecord record) throws Exception {
    Field field = NexoTerminalShopperSession.class.getDeclaredField("lastSettlementRecord");
    field.setAccessible(true);
    field.set(session, record);
  }

  private static List<String> cardIds(OriginalSaleRecord record) {
    return record.getCardPayments().stream()
        .map(CardPaymentRecord::getPoiTransactionId)
        .collect(Collectors.toList());
  }

  private static List<String> reversedIds(List<SaleToPOIRequest> requests) {
    List<String> ids = new ArrayList<>();
    for (SaleToPOIRequest request : requests) {
      if (request.getReversalRequest() != null) {
        ids.add(
            request
                .getReversalRequest()
                .getOriginalPOITransaction()
                .getPoiTransactionID()
                .getTransactionID());
      }
    }
    return ids;
  }

  private static List<String> refundedIds(List<SaleToPOIRequest> requests) {
    List<String> ids = new ArrayList<>();
    for (SaleToPOIRequest request : requests) {
      if (request.getPaymentRequest() != null) {
        ids.add(
            request
                .getPaymentRequest()
                .getPaymentTransaction()
                .getOriginalPOITransaction()
                .getPoiTransactionID()
                .getTransactionID());
      }
    }
    return ids;
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

  private static String reversalOk(String poiTxn, double reversed) {
    return "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
        + "\"Response\":{\"Result\":\"Success\"},"
        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\""
        + poiTxn
        + "\",\"TimeStamp\":\"2026-07-20T11:00:00Z\"}},"
        + "\"ReversedAmount\":"
        + reversed
        + "}}}";
  }

  private static String refundOk(String poiTxn, double authorized) {
    return "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
        + "\"Response\":{\"Result\":\"Success\"},"
        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\""
        + poiTxn
        + "\",\"TimeStamp\":\"2026-07-20T11:00:00Z\"}},"
        + "\"PaymentResult\":{\"AmountsResp\":{\"Currency\":\"USD\","
        + "\"AuthorizedAmount\":"
        + authorized
        + "}}}}}";
  }

  private static String refundWithoutAmount(String poiTxn) {
    return "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
        + "\"Response\":{\"Result\":\"Success\"},"
        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\""
        + poiTxn
        + "\"}}}}}";
  }

  /** The per-tender view always adds up to the total it breaks down. */
  private static void assertSumsToTotal(List<TenderAmount> byTender, BigDecimal total) {
    BigDecimal sum = BigDecimal.ZERO;
    for (TenderAmount leg : byTender) {
      sum = sum.add(leg.getAmount());
    }
    assertEquals(0, sum.compareTo(total), "the per-tender amounts must sum to the total");
  }

  private static String paymentOk(String poiTxn, double authorized) {
    return refundOk(poiTxn, authorized);
  }
}
