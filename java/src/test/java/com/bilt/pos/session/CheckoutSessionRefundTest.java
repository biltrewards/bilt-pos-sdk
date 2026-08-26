package com.bilt.pos.session;

import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.StoredValueData;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.settlement.RefundAllocation;
import com.bilt.pos.session.settlement.SettlementMovement;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.bilt.pos.session.settlement.SettlementResult;
import com.bilt.pos.session.settlement.SettlementStep;
import com.bilt.pos.session.storedvalue.StoredValueCard;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Refunds and voids on the checkout session itself. Same-session reversal
 * of a completed payment is covered with the payment tests in
 * {@link CheckoutSessionPaymentTest}.
 */
class CheckoutSessionRefundTest {

    private static final String REFUND_OK = CheckoutSessionTest.refundOk(15.00);
    private static final String PAYMENT_DECLINED =
            "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                    + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Refusal\"}}}}";
    private static final Instant ORIGINAL_TIMESTAMP = Instant.parse("2026-07-20T10:00:00Z");
    private static final String ORIGINAL_STORED_VALUE_TXN = "POI-ORIG-SV";

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.setDispatcher(CheckoutSessionTest.adminAnsweringDispatcher());
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private CheckoutSession session() throws Exception {
        CheckoutSession session = CheckoutSession.builder()
                .client(BiltNexoTerminalClient.builder()
                        .endpoint(server.url("/nexo").toString())
                        .disableRecoveryOnNetworkError()
                        .build())
                .saleId("POS-LANE-3")
                .poiId("VictaLane-275839164")
                .currency("USD")
                .autoDisplay(false)
                .start()
                .get();
        server.takeRequest(5, TimeUnit.SECONDS);  // drain the session-start Admin request
        return session;
    }

    private SaleToPOIRequest recordedRequest() throws Exception {
        RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(recorded);
        return mapper.readValue(recorded.getBody().readUtf8(), NexoTerminalAPI.class)
                .getSaleToPOIRequest();
    }

    private List<SaleToPOIRequest> drainRequests() throws Exception {
        List<SaleToPOIRequest> requests = new ArrayList<>();
        RecordedRequest recorded;
        while ((recorded = server.takeRequest(200, TimeUnit.MILLISECONDS)) != null) {
            requests.add(mapper.readValue(recorded.getBody().readUtf8(), NexoTerminalAPI.class)
                    .getSaleToPOIRequest());
        }
        return requests;
    }

    private static String refundOk(String poiTxn, double authorized) {
        return "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                + "\"Response\":{\"Result\":\"Success\"},"
                + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"" + poiTxn + "\","
                + "\"TimeStamp\":\"2026-07-20T10:00:01Z\"}},"
                + "\"PaymentResult\":{\"AmountsResp\":{\"Currency\":\"USD\","
                + "\"AuthorizedAmount\":" + authorized + "},"
                + "\"PaymentAcquirerData\":{\"ApprovalCode\":\"APPR01\"}}}}}";
    }

    private static String paymentOk(String poiTxn, double authorized) {
        return "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                + "\"Response\":{\"Result\":\"Success\"},"
                + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"" + poiTxn + "\","
                + "\"TimeStamp\":\"2026-07-20T10:00:03Z\"}},"
                + "\"PaymentResult\":{\"AmountsResp\":{\"Currency\":\"USD\","
                + "\"AuthorizedAmount\":" + authorized + "}}}}}";
    }

    private static String storedValueOk(String poiTxn, double amount, double balance) {
        return "{\"SaleToPOIResponse\":{\"StoredValueResponse\":{"
                + "\"Response\":{\"Result\":\"Success\"},"
                + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"" + poiTxn + "\","
                + "\"TimeStamp\":\"2026-07-20T10:00:02Z\"}},"
                + "\"StoredValueResult\":[{\"StoredValueTransactionType\":\"Load\","
                + "\"ItemAmount\":" + amount + ",\"Currency\":\"USD\","
                + "\"StoredValueAccountStatus\":{\"CurrentBalance\":" + balance + "}}]}}}";
    }

    @Test
    void unlinkedRefundSendsNoOriginalTransactionAndNoLoyaltyReversal() throws Exception {
        server.enqueue(new MockResponse().setBody(REFUND_OK));

        RefundResult result = session().refundUnlinked(new BigDecimal("15.00")).get();

        assertTrue(result.isSuccess());
        assertEquals(0, result.getPointsReversed());
        assertEquals(2, server.getRequestCount(), "the session start plus the refund");

        SaleToPOIRequest refund = recordedRequest();
        assertEquals("Refund", refund.getPaymentRequest().getPaymentData().getPaymentType().toValue());
        assertNull(refund.getPaymentRequest().getPaymentTransaction().getOriginalPOITransaction());
        assertEquals(15.00, refund.getPaymentRequest().getPaymentTransaction()
                .getAmountsReq().getRequestedAmount());
    }

    @Test
    void voidAfterUnlinkedRefundIsRejected() throws Exception {
        server.enqueue(new MockResponse().setBody(REFUND_OK));
        CheckoutSession session = session();
        assertTrue(session.refundUnlinked(new BigDecimal("15.00")).get().isSuccess());
        recordedRequest();

        SessionException failure = assertThrows(SessionException.class,
                () -> session.voidTransaction().get());
        assertEquals(SessionErrorCode.INVALID_STATE, failure.getError().getCode());
        assertTrue(failure.getError().getMessage().contains("refund"),
                "the error must steer the register to further refunds");
        assertEquals(2, server.getRequestCount(),
                "the rejected void must not reach the wire (start plus the refund)");
    }

    @Test
    void linkedRefundWithoutPaymentFailsWithInvalidState() throws Exception {
        CheckoutSession session = session();
        SessionException e = assertThrows(SessionException.class,
                () -> session.refund().get());
        assertEquals(SessionErrorCode.INVALID_STATE, e.getError().getCode());
        assertTrue(e.getError().getMessage().contains("SettlementOptions"),
                "the error must point at settlement allocations for prior sales: "
                        + e.getError().getMessage());
        assertEquals(1, server.getRequestCount(), "only the session start may hit the wire");
    }

    @Test
    void voidWithoutPaymentFailsWithInvalidState() throws Exception {
        CheckoutSession session = session();
        SessionException e = assertThrows(SessionException.class,
                () -> session.voidTransaction().get());
        assertEquals(SessionErrorCode.INVALID_STATE, e.getError().getCode());
        assertTrue(e.getError().getMessage().contains("OriginalSaleRecord"),
                "the error must point at original-sale records for prior voids: "
                        + e.getError().getMessage());
        assertEquals(1, server.getRequestCount(), "only the session start may hit the wire");
    }

    @Test
    void refundAmountMustBePositive() throws Exception {
        CheckoutSession session = session();
        assertThrows(IllegalArgumentException.class, () -> session.refund(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> session.refundUnlinked(new BigDecimal("-1")));
    }

    @Test
    void settlementRefundAllocationsCanSplitReturnAcrossCardAndGiftCard() throws Exception {
        CheckoutSession session = session();
        session.basket().addItem(BasketItem.credit("RET-1", "Returned item", 1, "40.00"));

        server.enqueue(new MockResponse().setBody(refundOk("POI-CARD-REF-1", 25.00)));
        server.enqueue(new MockResponse().setBody(refundOk("POI-SV-REF-1", 15.00)));

        SettlementResult result = session.settle(SettlementOptions.builder()
                .addRefundAllocation(RefundAllocation.card(new BigDecimal("25.00"),
                        "POI-ORIG-CARD", ORIGINAL_TIMESTAMP))
                .addRefundAllocation(RefundAllocation.storedValue(new BigDecimal("15.00"),
                        ORIGINAL_STORED_VALUE_TXN, ORIGINAL_TIMESTAMP))
                .build()).get();

        assertTrue(result.isSuccess());
        assertEquals(0, new BigDecimal("25.00").compareTo(result.getCardRefundedAmount()));
        assertEquals(0, new BigDecimal("15.00").compareTo(
                result.getStoredValueRefundedAmount()));
        assertEquals(2, result.getMovements().size());
        assertEquals(SettlementStep.CARD_REFUND, result.getMovements().get(0).getStep());
        assertEquals(SettlementStep.STORED_VALUE_REFUND, result.getMovements().get(1).getStep());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(2, requests.size());
        assertEquals("Refund", requests.get(0).getPaymentRequest()
                .getPaymentData().getPaymentType().toValue());
        assertEquals("POI-ORIG-CARD", requests.get(0).getPaymentRequest()
                .getPaymentTransaction().getOriginalPOITransaction()
                .getPoiTransactionID().getTransactionID());
        assertNull(requests.get(1).getStoredValueRequest());
        assertEquals("Refund", requests.get(1).getPaymentRequest()
                .getPaymentData().getPaymentType().toValue());
        assertEquals(ORIGINAL_STORED_VALUE_TXN, requests.get(1).getPaymentRequest()
                .getPaymentTransaction().getOriginalPOITransaction()
                .getPoiTransactionID().getTransactionID());
        assertEquals(15.00, requests.get(1).getPaymentRequest().getPaymentTransaction()
                .getAmountsReq().getRequestedAmount());
    }

    @Test
    void settlementCanIssueStoreCreditByLoadingStoredValueCard() throws Exception {
        CheckoutSession session = session();
        session.basket().addItem(BasketItem.credit("RET-1", "Returned item", 1, "15.00"));

        server.enqueue(new MockResponse().setBody(storedValueOk("POI-SV-LOAD-1", 15.00, 65.00)));

        SettlementResult result = session.settle(SettlementOptions.builder()
                .addRefundAllocation(RefundAllocation.storeCredit(
                        StoredValueCard.number("GC-1"), new BigDecimal("15.00")))
                .build())
                .beforeStep(ctx -> "TXN-" + ctx.getStep())
                .get();

        assertTrue(result.isSuccess());
        assertEquals(0, new BigDecimal("15.00").compareTo(
                result.getStoredValueRefundedAmount()));
        assertEquals(SettlementStep.STORED_VALUE_REFUND,
                result.getMovements().get(0).getStep());

        SaleToPOIRequest request = recordedRequest();
        assertNull(request.getPaymentRequest());
        assertEquals("TXN-" + SettlementStep.STORED_VALUE_REFUND,
                request.getStoredValueRequest().getSaleData()
                        .getSaleTransactionID().getTransactionID());
        StoredValueData storedValue = request.getStoredValueRequest().getStoredValueData()[0];
        assertEquals("Load", storedValue.getStoredValueTransactionType().toValue());
        assertNull(storedValue.getOriginalPOITransaction());
        assertEquals("GC-1", storedValue.getStoredValueAccountID().getStoredValueID());
        assertEquals(15.00, storedValue.getItemAmount());
    }

    @Test
    void settlementExchangeRefundsReturnAllocationsAndChargesSaleLines() throws Exception {
        CheckoutSession session = session();
        session.basket().addItem(BasketItem.of("BUY-1", "New item", 1, "75.00"));
        session.basket().addItem(BasketItem.credit("RET-1", "Returned item", 1, "40.00"));
        List<SettlementStep> callbacks = new ArrayList<>();

        server.enqueue(new MockResponse().setBody(refundOk("POI-CARD-REF-1", 25.00)));
        server.enqueue(new MockResponse().setBody(refundOk("POI-SV-REF-1", 15.00)));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-CARD-SALE-1", 75.00)));

        SettlementResult result = session.settle(SettlementOptions.builder()
                .addRefundAllocation(RefundAllocation.card(new BigDecimal("25.00"),
                        "POI-ORIG-CARD", ORIGINAL_TIMESTAMP))
                .addRefundAllocation(RefundAllocation.storedValue(new BigDecimal("15.00"),
                        ORIGINAL_STORED_VALUE_TXN, ORIGINAL_TIMESTAMP))
                .build())
                .beforeStep(ctx -> "TXN-" + ctx.getStep())
                .onCardRefunded(movement -> callbacks.add(movement.getStep()))
                .onGiftCardRefunded(movement -> callbacks.add(movement.getStep()))
                .onCardCharged(movement -> callbacks.add(movement.getStep()))
                .get();

        assertTrue(result.isSuccess());
        assertEquals(0, new BigDecimal("75.00").compareTo(result.getCardAmountCharged()));
        assertEquals(0, new BigDecimal("25.00").compareTo(result.getCardRefundedAmount()));
        assertEquals(0, new BigDecimal("15.00").compareTo(
                result.getStoredValueRefundedAmount()));
        assertEquals(List.of(SettlementStep.CARD_REFUND,
                SettlementStep.STORED_VALUE_REFUND,
                SettlementStep.CARD_CHARGE), callbacks);
        assertEquals(2, result.getFinalBasket().getItemCount());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(3, requests.size());
        assertEquals("TXN-" + SettlementStep.CARD_REFUND, requests.get(0).getPaymentRequest()
                .getSaleData().getSaleTransactionID().getTransactionID());
        assertEquals("TXN-" + SettlementStep.STORED_VALUE_REFUND,
                requests.get(1).getPaymentRequest().getSaleData()
                        .getSaleTransactionID().getTransactionID());
        assertEquals(ORIGINAL_STORED_VALUE_TXN, requests.get(1).getPaymentRequest()
                .getPaymentTransaction().getOriginalPOITransaction()
                .getPoiTransactionID().getTransactionID());
        assertEquals("TXN-" + SettlementStep.CARD_CHARGE, requests.get(2).getPaymentRequest()
                .getSaleData().getSaleTransactionID().getTransactionID());
        assertEquals(1, requests.get(2).getPaymentRequest().getPaymentTransaction()
                .getSaleItem().length);
        assertEquals("BUY-1", requests.get(2).getPaymentRequest().getPaymentTransaction()
                .getSaleItem()[0].getProductCode());

        SessionException voidFailure = assertThrows(SessionException.class,
                () -> session.voidTransaction().get());
        assertEquals(SessionErrorCode.INVALID_STATE, voidFailure.getError().getCode());
        assertTrue(voidFailure.getError().getMessage().contains("pure sale settlement"));
    }

    @Test
    void exchangeSettlementPreservesBasketTaxTotalOverrideAcrossSaleAndReturnSides()
            throws Exception {
        CheckoutSession session = session();
        session.basket().addItem(BasketItem.of("BUY-1", "New item", 1, "100.00"));
        session.basket().addItem(BasketItem.credit("RET-1", "Returned item", 1, "40.00"));
        session.basket().setTaxTotal(new BigDecimal("5.40"));

        server.enqueue(new MockResponse().setBody(refundOk("POI-CARD-REF-1", 43.60)));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-CARD-SALE-1", 109.00)));

        SettlementResult result = session.settle(SettlementOptions.builder()
                .addRefundAllocation(RefundAllocation.card(new BigDecimal("43.60"),
                        "POI-ORIG-CARD", ORIGINAL_TIMESTAMP))
                .build()).get();

        assertTrue(result.isSuccess());
        assertEquals(0, new BigDecimal("43.60").compareTo(result.getCardRefundedAmount()));
        assertEquals(0, new BigDecimal("109.00").compareTo(result.getCardAmountCharged()));
        assertEquals(0, new BigDecimal("65.40").compareTo(
                result.getFinalBasket().getGrandTotal()));
        assertEquals(0, new BigDecimal("5.40").compareTo(
                result.getFinalBasket().getTaxTotal()));

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(2, requests.size());
        assertEquals(43.60, requests.get(0).getPaymentRequest().getPaymentTransaction()
                .getAmountsReq().getRequestedAmount(),
                "return allocations must include the return side's tax share");
        assertEquals(109.00, requests.get(1).getPaymentRequest().getPaymentTransaction()
                .getAmountsReq().getRequestedAmount(),
                "sale-side tax share must be included in the card charge");
    }

    @Test
    void settlementRequiresRefundAllocationsToMatchReturnTotal() throws Exception {
        CheckoutSession session = session();
        session.basket().addItem(BasketItem.credit("RET-1", "Returned item", 1, "40.00"));

        SessionException e = assertThrows(SessionException.class,
                () -> session.settle(SettlementOptions.builder()
                        .addRefundAllocation(RefundAllocation.card(new BigDecimal("25.00"),
                                "POI-ORIG-CARD", ORIGINAL_TIMESTAMP))
                        .build()).get());

        assertEquals(SessionErrorCode.INVALID_STATE, e.getError().getCode());
        assertTrue(e.getError().getMessage().contains("return lines total 40.00"));
        assertEquals(1, server.getRequestCount(), "only the session start may hit the wire");
    }

    @Test
    void settlementCanRecordExternalRefundAllocationWithoutTerminalMovement()
            throws Exception {
        CheckoutSession session = session();
        session.basket().addItem(BasketItem.credit("RET-1", "Returned item", 1, "40.00"));
        List<SettlementStep> beforeSteps = new ArrayList<>();
        List<SettlementStep> movementCallbacks = new ArrayList<>();

        SettlementResult result = session.settle(SettlementOptions.builder()
                .addRefundAllocation(RefundAllocation.external(new BigDecimal("40.00")))
                .build())
                .beforeStep(ctx -> {
                    beforeSteps.add(ctx.getStep());
                    return "TXN-" + ctx.getStep();
                })
                .onExternalRefunded(movement -> movementCallbacks.add(movement.getStep()))
                .get();

        assertTrue(result.isSuccess());
        assertEquals(0, new BigDecimal("40.00").compareTo(
                result.getExternalRefundedAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getCardRefundedAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getStoredValueRefundedAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getLoyaltyRefundedAmount()));
        assertEquals(List.of(SettlementStep.EXTERNAL_REFUND), beforeSteps);
        assertEquals(List.of(SettlementStep.EXTERNAL_REFUND), movementCallbacks);
        assertEquals(1, result.getMovements().size());
        SettlementMovement movement = result.getMovements().get(0);
        assertEquals(SettlementStep.EXTERNAL_REFUND, movement.getStep());
        assertEquals(0, new BigDecimal("40.00").compareTo(movement.getAmount()));
        assertEquals("TXN-" + SettlementStep.EXTERNAL_REFUND,
                movement.getSaleTransactionId());
        assertNull(movement.getPoiTransactionId());
        assertNull(movement.getPoiTransactionTimestamp());
        assertEquals(1, server.getRequestCount(), "only the session start may hit the wire");
    }

    @Test
    void settlementRetryDoesNotResendCommittedRefundAllocations() throws Exception {
        CheckoutSession session = session();
        session.basket().addItem(BasketItem.of("BUY-1", "New item", 1, "75.00"));
        session.basket().addItem(BasketItem.credit("RET-1", "Returned item", 1, "40.00"));

        server.enqueue(new MockResponse().setBody(refundOk("POI-CARD-REF-1", 25.00)));
        server.enqueue(new MockResponse().setBody(refundOk("POI-SV-REF-1", 15.00)));
        server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));

        SessionException firstFailure = assertThrows(SessionException.class,
                () -> session.settle(splitRefundOptions()).get());
        assertEquals(SessionErrorCode.DECLINED, firstFailure.getError().getCode());
        assertEquals(SessionState.FAILED, session.getState());
        assertThrows(IllegalStateException.class,
                () -> session.basket().addItem(BasketItem.of("EXTRA", "Extra", 1, "1.00")));
        SessionException voidFailure = assertThrows(SessionException.class,
                () -> session.voidTransaction().get());
        assertTrue(voidFailure.getError().getMessage().contains(
                "committed refund allocations"));

        List<SaleToPOIRequest> firstAttempt = drainRequests();
        assertEquals(3, firstAttempt.size());
        assertEquals("Refund", firstAttempt.get(0).getPaymentRequest()
                .getPaymentData().getPaymentType().toValue());
        assertEquals("Refund", firstAttempt.get(1).getPaymentRequest()
                .getPaymentData().getPaymentType().toValue());
        assertEquals(ORIGINAL_STORED_VALUE_TXN, firstAttempt.get(1).getPaymentRequest()
                .getPaymentTransaction().getOriginalPOITransaction()
                .getPoiTransactionID().getTransactionID());
        assertNotNull(firstAttempt.get(2).getPaymentRequest());

        server.enqueue(new MockResponse().setBody(paymentOk("POI-CARD-SALE-2", 75.00)));
        List<SettlementStep> retryPriorSteps = new ArrayList<>();
        SettlementResult retried = session.settle(splitRefundOptions())
                .beforeStep(ctx -> {
                    if (ctx.getStep() == SettlementStep.CARD_CHARGE) {
                        retryPriorSteps.add(ctx.getPriorSteps().get(0).getStep());
                        retryPriorSteps.add(ctx.getPriorSteps().get(1).getStep());
                    }
                    return "TXN-RETRY-" + ctx.getStep();
                })
                .get();

        assertTrue(retried.isSuccess());
        assertEquals(0, new BigDecimal("25.00").compareTo(retried.getCardRefundedAmount()));
        assertEquals(0, new BigDecimal("15.00").compareTo(
                retried.getStoredValueRefundedAmount()));
        assertEquals(0, new BigDecimal("75.00").compareTo(retried.getCardAmountCharged()));
        assertEquals(List.of(SettlementStep.CARD_REFUND,
                SettlementStep.STORED_VALUE_REFUND), retryPriorSteps);
        assertEquals(List.of(SettlementStep.CARD_REFUND,
                SettlementStep.STORED_VALUE_REFUND,
                SettlementStep.CARD_CHARGE), retried.getMovements().stream()
                .map(movement -> movement.getStep())
                .toList());

        List<SaleToPOIRequest> retryRequests = drainRequests();
        assertEquals(1, retryRequests.size(), "retry must not resend refund allocations");
        assertNotNull(retryRequests.get(0).getPaymentRequest());
        assertEquals("BUY-1", retryRequests.get(0).getPaymentRequest()
                .getPaymentTransaction().getSaleItem()[0].getProductCode());
    }

    @Test
    void settlementRetryRequiresSameCommittedRefundAllocationPrefix() throws Exception {
        CheckoutSession session = session();
        session.basket().addItem(BasketItem.of("BUY-1", "New item", 1, "75.00"));
        session.basket().addItem(BasketItem.credit("RET-1", "Returned item", 1, "40.00"));

        server.enqueue(new MockResponse().setBody(refundOk("POI-CARD-REF-1", 25.00)));
        server.enqueue(new MockResponse().setBody(refundOk("POI-SV-REF-1", 15.00)));
        server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
        assertThrows(SessionException.class, () -> session.settle(splitRefundOptions()).get());
        assertEquals(3, drainRequests().size());

        SessionException mismatch = assertThrows(SessionException.class,
                () -> session.settle(SettlementOptions.builder()
                        .addRefundAllocation(RefundAllocation.card(new BigDecimal("20.00"),
                                "POI-ORIG-CARD", ORIGINAL_TIMESTAMP))
                        .addRefundAllocation(RefundAllocation.storedValue(
                                new BigDecimal("20.00"), ORIGINAL_STORED_VALUE_TXN,
                                ORIGINAL_TIMESTAMP))
                        .build()).get());

        assertEquals(SessionErrorCode.INVALID_STATE, mismatch.getError().getCode());
        assertTrue(mismatch.getError().getMessage().contains("same refund allocations"));
        assertEquals(4, server.getRequestCount(),
                "the rejected retry must not send another terminal movement");
    }

    private static SettlementOptions splitRefundOptions() {
        return SettlementOptions.builder()
                .addRefundAllocation(RefundAllocation.card(new BigDecimal("25.00"),
                        "POI-ORIG-CARD", ORIGINAL_TIMESTAMP))
                .addRefundAllocation(RefundAllocation.storedValue(new BigDecimal("15.00"),
                        ORIGINAL_STORED_VALUE_TXN, ORIGINAL_TIMESTAMP))
                .build();
    }
}
