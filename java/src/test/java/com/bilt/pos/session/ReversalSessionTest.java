package com.bilt.pos.session;

import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ReversalSessionTest {

    private static final String ORIGINAL_POI_TXN = "a816b0a9-8a11-4dc0-ba9d-5ad1e8c7e0d6";
    private static final Instant ORIGINAL_TS = Instant.parse("2026-03-02T14:35:12Z");

    private static final String REFUND_OK = CheckoutSessionTest.refundOk(24.99);

    private static final String AWARD_REFUND_OK =
            "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
                    + "\"Response\":{\"Result\":\"Success\"},"
                    + "\"LoyaltyResult\":[{\"CurrentBalance\":1100,"
                    + "\"LoyaltyAmount\":{\"AmountValue\":140,\"LoyaltyUnit\":\"Point\"}}]}}}";

    private static final String REVERSAL_OK = CheckoutSessionTest.REVERSAL_OK;
    private static final String LOYALTY_REFUND_OK = CheckoutSessionTest.LOYALTY_REFUND_OK;
    private static final String LOYALTY_REFUND_FAILED = CheckoutSessionTest.LOYALTY_REFUND_FAILED;

    // the original sale's leg references, as a POS would persist them from
    // a CheckoutResult
    private static final String STORED_VALUE_POI_TXN = "POI-SV-9";
    private static final Instant STORED_VALUE_TS = Instant.parse("2026-03-02T14:35:09Z");
    private static final String REBATE_POI_TXN = "POI-RB-9";
    private static final Instant REBATE_TS = Instant.parse("2026-03-02T14:35:10Z");
    private static final String REDEMPTION_POI_TXN = "POI-RD-9";
    private static final Instant REDEMPTION_TS = Instant.parse("2026-03-02T14:35:11Z");
    private static final String AWARD_POI_TXN = "POI-AW-9";

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

    private ReversalSession.Builder sessionBuilder() {
        return ReversalSession.builder()
                .client(BiltNexoTerminalClient.builder()
                        .endpoint(server.url("/nexo").toString())
                        .disableRecoveryOnNetworkError()
                        .build())
                .saleId("POS-LANE-3")
                .poiId("VictaLane-275839164")
                .currency("USD");
    }

    private ReversalSession cardSession() throws Exception {
        return start(sessionBuilder()
                .poiTransactionId(ORIGINAL_POI_TXN)
                .poiTransactionTimestamp(ORIGINAL_TS));
    }

    /** A referenced session that also knows the original sale's award. */
    private ReversalSession cardSessionWithAward() throws Exception {
        return start(sessionBuilder()
                .poiTransactionId(ORIGINAL_POI_TXN)
                .poiTransactionTimestamp(ORIGINAL_TS)
                .awardPoiTransactionId(AWARD_POI_TXN));
    }

    /** Starts the session and drains the session-start Admin request. */
    private ReversalSession start(ReversalSession.Builder builder) throws Exception {
        ReversalSession session = builder.start().get();
        server.takeRequest(5, TimeUnit.SECONDS);
        return session;
    }

    private SaleToPOIRequest recordedRequest() throws Exception {
        RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(recorded);
        return mapper.readValue(recorded.getBody().readUtf8(), NexoTerminalAPI.class)
                .getSaleToPOIRequest();
    }

    // ─── Builder ───

    @Test
    void startRequiresATransactionReference() {
        ReversalSession.Builder builder = sessionBuilder();
        IllegalStateException e = assertThrows(IllegalStateException.class, builder::start);
        assertTrue(e.getMessage().contains("transaction reference"),
                "a reversal session without any reference has nothing to reverse: "
                        + e.getMessage());
    }

    // ─── Refund ───

    @Test
    void refundOutcomeIsDeliveredEvenWhenAbortRacesIt() throws Exception {
        CountDownLatch refundOnTheWire = new CountDownLatch(1);
        CountDownLatch aborted = new CountDownLatch(1);
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                String body = request.getBody().readUtf8();
                if (body.contains("\"AdminRequest\"")) {
                    return new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK);
                }
                if (body.contains("\"PaymentRequest\"")) {
                    refundOnTheWire.countDown();
                    // hold the refund response until abort() has been sent
                    aborted.await(5, TimeUnit.SECONDS);
                    return new MockResponse().setBody(REFUND_OK);
                }
                if (body.contains("\"LoyaltyRequest\"")) {
                    return new MockResponse().setBody(AWARD_REFUND_OK);
                }
                return new MockResponse();   // the AbortRequest, best-effort
            }
        });

        ReversalSession session = cardSession();
        AtomicReference<RefundResult> delivered = new AtomicReference<>();
        AtomicReference<SessionError> failed = new AtomicReference<>();
        Thread register = new Thread(() -> session.refund(new BigDecimal("24.99"))
                .onSuccess(delivered::set)
                .onError((step, error) -> {
                    failed.set(error);
                    return ReversalDecision.ABORT;
                })
                .execute());
        register.start();
        assertTrue(refundOnTheWire.await(5, TimeUnit.SECONDS));

        session.abort();
        aborted.countDown();
        register.join(5_000);
        assertFalse(register.isAlive());

        // money moved on the terminal: unlike read-only prompts, the outcome
        // must be delivered even though an abort raced it
        assertNull(failed.get());
        assertNotNull(delivered.get(),
                "a completed refund is money moved — its outcome must not be discarded");
        assertEquals(0, new BigDecimal("24.99").compareTo(delivered.get().getRefundedAmount()));
        assertEquals(SessionState.IDLE, session.getState(),
                "abort is operation-scoped; the session continues");
    }

    @Test
    void abortedAwardReversalAfterTheTenderRefundStillRaisesTheVoidGuard() throws Exception {
        server.enqueue(new MockResponse().setBody(REFUND_OK));               // money moved
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED));   // award fails

        ReversalSession session = cardSessionWithAward();
        assertThrows(SessionException.class, () -> session.refund(new BigDecimal("24.99"))
                .onError((step, error) -> ReversalDecision.ABORT)
                .get());

        // the refund flow aborted AFTER the tender refund completed: the
        // money moved, so a void would return the full amount on top of it
        SessionException e = assertThrows(SessionException.class,
                () -> session.voidTransaction().get());
        assertEquals(SessionErrorCode.INVALID_STATE, e.getError().getCode());
        assertTrue(e.getError().getMessage().contains("refund"),
                "the error must steer the register to further refunds: "
                        + e.getError().getMessage());
        assertEquals(3, server.getRequestCount(),
                "the rejected void must not reach the wire (start, refund, failed award)");
    }

    @Test
    void voidAfterRefundIsRejectedButFurtherRefundsWork() throws Exception {
        server.enqueue(new MockResponse().setBody(REFUND_OK));
        ReversalSession session = cardSession();
        assertTrue(session.refund(new BigDecimal("24.99")).get().isSuccess());
        recordedRequest();

        SessionException failure = assertThrows(SessionException.class,
                () -> session.voidTransaction().get());
        assertEquals(SessionErrorCode.INVALID_STATE, failure.getError().getCode());
        assertTrue(failure.getError().getMessage().contains("refund"),
                "the error must steer the register to further refunds");
        assertEquals(2, server.getRequestCount(),
                "the rejected void must not reach the wire (start plus the refund)");

        // further partial returns stay possible — that is the correct path
        server.enqueue(new MockResponse().setBody(REFUND_OK));
        assertTrue(session.refund(new BigDecimal("10.00")).get().isSuccess());
    }

    @Test
    void partialRefundSendsRefundThenAwardRefund() throws Exception {
        server.enqueue(new MockResponse().setBody(REFUND_OK));
        server.enqueue(new MockResponse().setBody(AWARD_REFUND_OK));

        RefundResult result = cardSessionWithAward().refund(new BigDecimal("24.99")).get();

        assertTrue(result.isSuccess());
        assertEquals(0, new BigDecimal("24.99").compareTo(result.getRefundedAmount()));
        assertEquals("APPR01", result.getApprovalCode());
        assertEquals("POI-REF-100", result.getPoiTransactionId());
        assertEquals(Instant.parse("2026-03-02T16:00:05Z"), result.getPoiTransactionTimestamp());
        assertEquals(140, result.getPointsReversed());
        assertEquals(1100, result.getRemainingPointBalance());

        SaleToPOIRequest refund = recordedRequest();
        assertEquals("Payment", refund.getMessageHeader().getMessageCategory().toValue());
        assertEquals("Refund", refund.getPaymentRequest().getPaymentData().getPaymentType().toValue());
        assertEquals(ORIGINAL_POI_TXN, refund.getPaymentRequest().getPaymentTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
        assertEquals(24.99, refund.getPaymentRequest().getPaymentTransaction()
                .getAmountsReq().getRequestedAmount());

        SaleToPOIRequest awardRefund = recordedRequest();
        assertEquals("Loyalty", awardRefund.getMessageHeader().getMessageCategory().toValue());
        assertEquals("AwardRefund", awardRefund.getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals(AWARD_POI_TXN, awardRefund.getLoyaltyRequest().getLoyaltyTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID(),
                "the award is reversed by its own reference, never the payment's");
    }

    @Test
    void refundWithoutAwardReferenceSendsNoLoyaltyReversal() throws Exception {
        server.enqueue(new MockResponse().setBody(REFUND_OK));

        RefundResult result = cardSession().refund(new BigDecimal("24.99")).get();

        assertTrue(result.isSuccess());
        assertEquals(0, result.getPointsReversed());
        assertEquals(2, server.getRequestCount(),
                "no award reference persisted means no award reversal — the start "
                        + "plus the refund only");
    }

    @Test
    void memberIdReachesTheRefundAwardReversal() throws Exception {
        server.enqueue(new MockResponse().setBody(REFUND_OK));
        server.enqueue(new MockResponse().setBody(AWARD_REFUND_OK));

        start(sessionBuilder()
                .poiTransactionId(ORIGINAL_POI_TXN)
                .poiTransactionTimestamp(ORIGINAL_TS)
                .awardPoiTransactionId(AWARD_POI_TXN)
                .memberId("98234"))
                .refund(new BigDecimal("10.00")).execute();

        recordedRequest();  // the refund itself
        SaleToPOIRequest awardRefund = recordedRequest();
        assertEquals(AWARD_POI_TXN, awardRefund.getLoyaltyRequest().getLoyaltyTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
        assertEquals("98234", awardRefund.getLoyaltyRequest().getLoyaltyData()[0]
                .getLoyaltyAccountID().getLoyaltyID(),
                "the builder-supplied member must reach the reversal's LoyaltyData");
    }

    @Test
    void fullRefundOmitsAmount() throws Exception {
        server.enqueue(new MockResponse().setBody(REFUND_OK));

        cardSession().refund().execute();

        SaleToPOIRequest refund = recordedRequest();
        assertNull(refund.getPaymentRequest().getPaymentTransaction()
                .getAmountsReq().getRequestedAmount());
        assertEquals("USD", refund.getPaymentRequest().getPaymentTransaction()
                .getAmountsReq().getCurrency());
    }

    @Test
    void refundWithoutCardReferenceFailsBeforeTheWire() throws Exception {
        ReversalSession session = start(sessionBuilder()
                .rebatePoiTransactionId(REBATE_POI_TXN)
                .rebatePoiTransactionTimestamp(REBATE_TS));
        AtomicReference<SessionError> seen = new AtomicReference<>();
        AtomicReference<ReversalStep> seenStep = new AtomicReference<>(ReversalStep.CARD);

        assertNull(session.refund()
                .onError((step, error) -> {
                    seenStep.set(step);
                    seen.set(error);
                    return ReversalDecision.RETRY;
                })
                .getOrNull());

        assertNotNull(seen.get(), "a failure before the first step must reach onError");
        assertEquals(SessionErrorCode.INVALID_STATE, seen.get().getCode());
        assertNull(seenStep.get(), "no step was running — the handler gets a null step");
        assertEquals(1, server.getRequestCount(), "only the session start may hit the wire");
    }

    @Test
    void failedAwardReversalDoesNotFailTheRefund() throws Exception {
        server.enqueue(new MockResponse().setBody(REFUND_OK));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED));

        RefundResult result = cardSessionWithAward().refund(new BigDecimal("10.00")).get();

        assertTrue(result.isSuccess());
        assertEquals(0, result.getPointsReversed());
    }

    @Test
    void declinedRefundGoesToErrorChannel() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Refusal\"}}}}"));

        SessionException e = assertThrows(SessionException.class,
                () -> cardSession().refund(new BigDecimal("10.00")).get());
        assertEquals(SessionErrorCode.DECLINED, e.getError().getCode());
        assertEquals(2, server.getRequestCount(), "no loyalty reversal after a declined refund");
    }

    @Test
    void refundAmountMustBePositive() throws Exception {
        ReversalSession session = cardSession();
        assertThrows(IllegalArgumentException.class, () -> session.refund(BigDecimal.ZERO));
    }

    // ─── Void ───

    @Test
    void voidSendsReversalAndAwardRefundAndEndsVoided() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},\"ReversedAmount\":97.94,"
                        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-REV-7\","
                        + "\"TimeStamp\":\"2026-03-02T16:10:00+00:00\"}}}}}"));
        server.enqueue(new MockResponse().setBody(AWARD_REFUND_OK));

        ReversalSession session = cardSessionWithAward();
        VoidResult result = session.voidTransaction().get();

        assertTrue(result.isSuccess());
        assertEquals(0, new BigDecimal("97.94").compareTo(result.getReversedAmount()));
        assertEquals("POI-REV-7", result.getPoiTransactionId());
        assertEquals(140, result.getPointsReversed());
        assertEquals(SessionState.VOIDED, session.getState());

        SaleToPOIRequest reversal = recordedRequest();
        assertEquals("Reversal", reversal.getMessageHeader().getMessageCategory().toValue());
        assertEquals("MerchantCancel", reversal.getReversalRequest().getReversalReason().toValue());
        assertEquals(ORIGINAL_POI_TXN, reversal.getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
    }

    @Test
    void failedVoidRestoresThePreVoidState() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"NotFound\"}}}}"));

        ReversalSession session = cardSession();
        assertThrows(SessionException.class, () -> session.voidTransaction().get());

        // the referenced transaction still stands; the session returns to
        // IDLE and the void can be retried
        assertEquals(SessionState.IDLE, session.getState());
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
        assertTrue(session.voidTransaction().get().isSuccess());
        assertEquals(SessionState.VOIDED, session.getState());
    }

    @Test
    void memberIdReachesTheVoidLoyaltyReversal() throws Exception {
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
        server.enqueue(new MockResponse().setBody(AWARD_REFUND_OK));

        start(sessionBuilder()
                .poiTransactionId(ORIGINAL_POI_TXN)
                .poiTransactionTimestamp(ORIGINAL_TS)
                .awardPoiTransactionId(AWARD_POI_TXN)
                .memberId("98234"))
                .voidTransaction().execute();

        recordedRequest();  // the reversal
        SaleToPOIRequest awardRefund = recordedRequest();
        assertEquals("98234", awardRefund.getLoyaltyRequest().getLoyaltyData()[0]
                .getLoyaltyAccountID().getLoyaltyID());
    }

    @Test
    void voidedSessionRejectsFurtherOperations() throws Exception {
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));

        ReversalSession session = cardSession();
        session.voidTransaction().execute();

        assertEquals(SessionState.VOIDED, session.getState());
        SessionException e = assertThrows(SessionException.class,
                () -> session.refund().get());
        assertEquals(SessionErrorCode.INVALID_STATE, e.getError().getCode());
    }

    // ─── Every-leg reversal ───

    /** A fresh session referencing every leg of a persisted sale. */
    private ReversalSession.Builder fullyReferencedBuilder() {
        return sessionBuilder()
                .poiTransactionId(ORIGINAL_POI_TXN)
                .poiTransactionTimestamp(ORIGINAL_TS)
                .storedValuePoiTransactionId(STORED_VALUE_POI_TXN)
                .storedValuePoiTransactionTimestamp(STORED_VALUE_TS)
                .rebatePoiTransactionId(REBATE_POI_TXN)
                .rebatePoiTransactionTimestamp(REBATE_TS)
                .redemptionPoiTransactionId(REDEMPTION_POI_TXN)
                .redemptionPoiTransactionTimestamp(REDEMPTION_TS)
                .awardPoiTransactionId(AWARD_POI_TXN)
                .memberId("98234");
    }

    @Test
    void voidReversesEveryReferencedLeg() throws Exception {
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));         // card leg
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));         // stored value leg
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // rebate refund
        server.enqueue(new MockResponse().setBody(AWARD_REFUND_OK));     // award refund

        ReversalSession session = start(fullyReferencedBuilder());
        assertTrue(session.voidTransaction().get().isSuccess());
        assertEquals(SessionState.VOIDED, session.getState());

        SaleToPOIRequest cardReversal = recordedRequest();
        assertEquals("Reversal", cardReversal.getMessageHeader().getMessageCategory().toValue());
        assertEquals(ORIGINAL_POI_TXN, cardReversal.getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());

        SaleToPOIRequest storedValueReversal = recordedRequest();
        assertEquals("Reversal", storedValueReversal.getMessageHeader()
                .getMessageCategory().toValue());
        assertEquals(STORED_VALUE_POI_TXN, storedValueReversal.getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());

        SaleToPOIRequest redemptionRefund = recordedRequest();
        assertEquals("RedemptionRefund", redemptionRefund.getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals(REDEMPTION_POI_TXN, redemptionRefund.getLoyaltyRequest()
                .getLoyaltyTransaction().getOriginalPOITransaction()
                .getPoiTransactionID().getTransactionID());
        assertNull(redemptionRefund.getLoyaltyRequest().getSaleData().getSaleToPOIData(),
                "per the reversal contract the original transaction reference suffices "
                        + "— no rewardRefs payload rides along");
        assertEquals("98234", redemptionRefund.getLoyaltyRequest().getLoyaltyData()[0]
                .getLoyaltyAccountID().getLoyaltyID(),
                "the builder-supplied member must reach the reversal's LoyaltyData");

        SaleToPOIRequest rebateRefund = recordedRequest();
        assertEquals("RebateRefund", rebateRefund.getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals(REBATE_POI_TXN, rebateRefund.getLoyaltyRequest()
                .getLoyaltyTransaction().getOriginalPOITransaction()
                .getPoiTransactionID().getTransactionID());

        SaleToPOIRequest awardRefund = recordedRequest();
        assertEquals("AwardRefund", awardRefund.getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals(AWARD_POI_TXN, awardRefund.getLoyaltyRequest()
                .getLoyaltyTransaction().getOriginalPOITransaction()
                .getPoiTransactionID().getTransactionID());
    }

    @Test
    void voidTreatsLoyaltyFailuresAsBestEffort() throws Exception {
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));             // card leg
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));             // stored value leg
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED));   // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED));   // rebate refund
        server.enqueue(new MockResponse().setBody(AWARD_REFUND_OK));         // award refund

        ReversalSession session = start(fullyReferencedBuilder());
        assertTrue(session.voidTransaction().get().isSuccess(),
                "the tender was reversed; loyalty refund failures are best-effort (SAF)");
        assertEquals(SessionState.VOIDED, session.getState());
        assertEquals(6, server.getRequestCount(),
                "the remaining loyalty refunds still run after one fails "
                        + "(start, two reversals, redemption, rebate, award)");
    }

    @Test
    void partiallyVoidedSaleRefusesRefundsUntilTheVoidFinishes() throws Exception {
        ReversalSession session = start(sessionBuilder()
                .poiTransactionId(ORIGINAL_POI_TXN)
                .poiTransactionTimestamp(ORIGINAL_TS)
                .storedValuePoiTransactionId(STORED_VALUE_POI_TXN)
                .storedValuePoiTransactionTimestamp(STORED_VALUE_TS));

        server.enqueue(new MockResponse().setBody(REVERSAL_OK));   // card leg reversed
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"UnreachableHost\"}}}}"));
        assertThrows(SessionException.class, () -> session.voidTransaction().get());
        assertEquals(SessionState.IDLE, session.getState());
        recordedRequest();  // the card reversal
        recordedRequest();  // the failed stored value reversal

        // the card leg is already reversed: a refund against it would
        // double-return the money, so refunds are refused mid-void
        SessionException e = assertThrows(SessionException.class,
                () -> session.refund(new BigDecimal("10.00")).get());
        assertEquals(SessionErrorCode.INVALID_STATE, e.getError().getCode());
        assertTrue(e.getError().getMessage().contains("voidTransaction"),
                "the error must steer the register to finishing the void: "
                        + e.getError().getMessage());
        assertEquals(3, server.getRequestCount(),
                "the refused refund must not reach the wire");

        // finishing the void remains the correct path
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));   // stored value leg
        assertTrue(session.voidTransaction().get().isSuccess());
        assertEquals(SessionState.VOIDED, session.getState());
    }

    @Test
    void loyaltyOnlyVoidIsStrictAndResumable() throws Exception {
        ReversalSession session = start(sessionBuilder()
                .redemptionPoiTransactionId(REDEMPTION_POI_TXN)
                .redemptionPoiTransactionTimestamp(REDEMPTION_TS)
                .rebatePoiTransactionId(REBATE_POI_TXN)
                .rebatePoiTransactionTimestamp(REBATE_TS)
                .memberId("98234"));

        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));       // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED));   // rebate refund

        SessionException failure = assertThrows(SessionException.class,
                () -> session.voidTransaction().get());
        assertTrue(failure.getError().getMessage().contains(REDEMPTION_POI_TXN),
                "the error must say the redemption was already refunded: "
                        + failure.getError().getMessage());
        assertEquals(SessionState.IDLE, session.getState(),
                "a failed void restores the pre-void state");
        recordedRequest();  // the redemption refund
        recordedRequest();  // the failed rebate refund

        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // rebate refund

        assertTrue(session.voidTransaction().get().isSuccess());
        assertEquals(SessionState.VOIDED, session.getState());

        SaleToPOIRequest rebateRefund = recordedRequest();
        assertEquals("RebateRefund", rebateRefund.getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue(),
                "the retry must not re-credit the reversed redemption leg");
        assertEquals(4, server.getRequestCount(),
                "no award reference, no award reversal (start, redemption, "
                        + "failed rebate, retried rebate)");
    }

    @Test
    void retryOfAMoneyAnchoredVoidKeepsLoyaltyBestEffort() throws Exception {
        ReversalSession session = start(fullyReferencedBuilder());

        // first attempt: both money legs reverse, then the register's own
        // handler aborts on the failing redemption
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));             // card leg
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));             // stored value leg
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED));   // redemption refund
        assertThrows(SessionException.class, () -> session.voidTransaction()
                .onError((step, error) -> ReversalDecision.ABORT)
                .get());
        recordedRequest();
        recordedRequest();
        recordedRequest();

        // the retry under the DEFAULT policy is still the reversal of a
        // money-anchored sale — the money is already reversed, so a still-
        // failing loyalty leg stays best-effort instead of turning strict
        // and stranding the void
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED));   // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));       // rebate refund
        server.enqueue(new MockResponse().setBody(AWARD_REFUND_OK));         // award refund

        assertTrue(session.voidTransaction().get().isSuccess());
        assertEquals(SessionState.VOIDED, session.getState());
        assertEquals(7, server.getRequestCount(),
                "the retry sends only the standing loyalty legs "
                        + "(start, two reversals, redemption, then redemption again, "
                        + "rebate, award)");
    }

    @Test
    void retryWhoseRemainingLegsAllSkipStillCompletesTheVoid() throws Exception {
        ReversalSession session = start(fullyReferencedBuilder());

        // first attempt: both money legs reverse, the register aborts on
        // the failing redemption
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));             // card leg
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));             // stored value leg
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED));   // redemption refund
        assertThrows(SessionException.class, () -> session.voidTransaction()
                .onError((step, error) -> ReversalDecision.ABORT)
                .get());
        recordedRequest();
        recordedRequest();
        recordedRequest();

        // second attempt, default policy: every remaining loyalty leg still
        // fails and is skipped (money-anchored, SAF-retryable) — the void
        // must complete on the strength of the already-reversed money legs
        // instead of stranding the session outside VOIDED
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED));   // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED));   // rebate refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED));   // award refund

        assertTrue(session.voidTransaction().get().isSuccess());
        assertEquals(SessionState.VOIDED, session.getState());
        assertEquals(7, server.getRequestCount());
    }

    @Test
    void loyaltyOnlyVoidIsStrictAboutTheAwardToo() throws Exception {
        ReversalSession session = start(sessionBuilder()
                .redemptionPoiTransactionId(REDEMPTION_POI_TXN)
                .redemptionPoiTransactionTimestamp(REDEMPTION_TS)
                .rebatePoiTransactionId(REBATE_POI_TXN)
                .rebatePoiTransactionTimestamp(REBATE_TS)
                .awardPoiTransactionId(AWARD_POI_TXN));

        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));       // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));       // rebate refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED));   // award refund

        SessionException failure = assertThrows(SessionException.class,
                () -> session.voidTransaction().get());
        assertTrue(failure.getError().getMessage().contains(AWARD_POI_TXN),
                "with no money leg the loyalty movements are the substance of the "
                        + "void — a standing award must fail it loudly: "
                        + failure.getError().getMessage());
        assertTrue(failure.getError().getMessage().contains(REDEMPTION_POI_TXN),
                "the error must say what was already reversed");
        assertEquals(SessionState.IDLE, session.getState(),
                "a failed void restores the pre-void state");
        recordedRequest();  // the redemption refund
        recordedRequest();  // the rebate refund
        recordedRequest();  // the failed award refund

        // the retry resumes at the award alone
        server.enqueue(new MockResponse().setBody(AWARD_REFUND_OK));

        assertTrue(session.voidTransaction().get().isSuccess());
        assertEquals(SessionState.VOIDED, session.getState());
        SaleToPOIRequest awardRefund = recordedRequest();
        assertEquals("AwardRefund", awardRefund.getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue(),
                "the reversed redemption and rebate legs must not be re-credited");
        assertEquals(5, server.getRequestCount());
    }

    @Test
    void rebateOnlyVoidRefundsTheRebate() throws Exception {
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // rebate refund

        ReversalSession session = start(sessionBuilder()
                .rebatePoiTransactionId(REBATE_POI_TXN)
                .rebatePoiTransactionTimestamp(REBATE_TS));
        assertTrue(session.voidTransaction().get().isSuccess());
        assertEquals(SessionState.VOIDED, session.getState());

        SaleToPOIRequest rebateRefund = recordedRequest();
        assertEquals("RebateRefund", rebateRefund.getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals(REBATE_POI_TXN, rebateRefund.getLoyaltyRequest()
                .getLoyaltyTransaction().getOriginalPOITransaction()
                .getPoiTransactionID().getTransactionID());
        assertEquals(2, server.getRequestCount(), "the start plus the rebate refund");
    }

    @Test
    void storedValueOnlyVoidReversesTheGiftCardLeg() throws Exception {
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));

        ReversalSession session = start(sessionBuilder()
                .storedValuePoiTransactionId(STORED_VALUE_POI_TXN)
                .storedValuePoiTransactionTimestamp(STORED_VALUE_TS));
        assertTrue(session.voidTransaction().get().isSuccess());
        assertEquals(SessionState.VOIDED, session.getState());

        SaleToPOIRequest reversal = recordedRequest();
        assertEquals("Reversal", reversal.getMessageHeader().getMessageCategory().toValue());
        assertEquals(STORED_VALUE_POI_TXN, reversal.getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
        assertEquals(2, server.getRequestCount(), "the start plus the reversal");
    }

    @Test
    void redemptionOnlyVoidOmitsLoyaltyDataWithoutMember() throws Exception {
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // redemption refund

        ReversalSession session = start(sessionBuilder()
                .redemptionPoiTransactionId(REDEMPTION_POI_TXN)
                .redemptionPoiTransactionTimestamp(REDEMPTION_TS));
        assertTrue(session.voidTransaction().get().isSuccess());

        SaleToPOIRequest redemptionRefund = recordedRequest();
        assertEquals("RedemptionRefund", redemptionRefund.getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals(REDEMPTION_POI_TXN, redemptionRefund.getLoyaltyRequest()
                .getLoyaltyTransaction().getOriginalPOITransaction()
                .getPoiTransactionID().getTransactionID());
        assertNull(redemptionRefund.getLoyaltyRequest().getLoyaltyData(),
                "without a builder memberId the reversal must omit LoyaltyData — the "
                        + "original transaction reference suffices per the wire contract");
    }

    @Test
    void giftCardOnlySaleIsReversedOnce() throws Exception {
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));

        // a gift-card-only sale reports the same reference for both money
        // legs on its CheckoutResult; persisting and supplying both must
        // not reverse the transaction twice
        ReversalSession session = start(sessionBuilder()
                .poiTransactionId(STORED_VALUE_POI_TXN)
                .poiTransactionTimestamp(STORED_VALUE_TS)
                .storedValuePoiTransactionId(STORED_VALUE_POI_TXN)
                .storedValuePoiTransactionTimestamp(STORED_VALUE_TS));
        assertTrue(session.voidTransaction().get().isSuccess());

        assertEquals(2, server.getRequestCount(),
                "one transaction, one reversal — no duplicate (start plus the reversal)");
    }

    // ─── Reversal decisions ───

    @Test
    void retryDecisionResendsTheFailedStep() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"UnavailableService\"}}}}"));
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));

        ReversalSession session = cardSession();
        AtomicReference<ReversalStep> failedStep = new AtomicReference<>();
        VoidResult result = session.voidTransaction()
                .onError((step, error) -> {
                    failedStep.set(step);
                    return ReversalDecision.RETRY;
                })
                .get();

        assertTrue(result.isSuccess());
        assertEquals(ReversalStep.CARD, failedStep.get());
        assertEquals(SessionState.VOIDED, session.getState());
        assertEquals(3, server.getRequestCount(),
                "the failed reversal must be re-sent (start plus two attempts)");
    }

    @Test
    void skipDecisionLeavesTheMovementStandingAndContinues() throws Exception {
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED));   // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));       // rebate refund

        ReversalSession session = start(sessionBuilder()
                .redemptionPoiTransactionId(REDEMPTION_POI_TXN)
                .redemptionPoiTransactionTimestamp(REDEMPTION_TS)
                .rebatePoiTransactionId(REBATE_POI_TXN)
                .rebatePoiTransactionTimestamp(REBATE_TS));
        VoidResult result = session.voidTransaction()
                .onError((step, error) -> step == ReversalStep.REDEMPTION
                        ? ReversalDecision.SKIP : ReversalDecision.ABORT)
                .get();

        assertTrue(result.isSuccess(),
                "the register chose to skip the failed redemption; the rebate leg "
                        + "still runs and the void completes");
        assertEquals(SessionState.VOIDED, session.getState());
        assertEquals(3, server.getRequestCount());
    }

    @Test
    void fullySkippedVoidFailsAndDeliversTheTerminalFailure() throws Exception {
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED));   // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED));   // rebate refund

        ReversalSession session = start(sessionBuilder()
                .redemptionPoiTransactionId(REDEMPTION_POI_TXN)
                .redemptionPoiTransactionTimestamp(REDEMPTION_TS)
                .rebatePoiTransactionId(REBATE_POI_TXN)
                .rebatePoiTransactionTimestamp(REBATE_TS));
        AtomicReference<SessionError> terminal = new AtomicReference<>();

        assertThrows(SessionException.class, () -> session.voidTransaction()
                .onError((step, error) -> {
                    if (step == null) {
                        terminal.set(error);
                        return ReversalDecision.ABORT;
                    }
                    return ReversalDecision.SKIP;
                })
                .get());

        assertNotNull(terminal.get(),
                "a void that reversed nothing must fail loudly, and the terminal "
                        + "failure must reach onError even after SKIP answers");
        assertEquals(SessionState.IDLE, session.getState(),
                "nothing was reversed; the session restores its pre-void state");
    }

    // ─── Lifecycle ───

    @Test
    void endedSessionRejectsFurtherReversals() throws Exception {
        // the dispatcher answers the Admin end exchange out of band
        ReversalSession session = cardSession();
        session.end().execute();

        assertEquals(SessionState.ENDED, session.getState());
        assertEquals(SessionErrorCode.INVALID_STATE, assertThrows(SessionException.class,
                () -> session.voidTransaction().get()).getError().getCode());
        assertEquals(SessionErrorCode.INVALID_STATE, assertThrows(SessionException.class,
                () -> session.refund().get()).getError().getCode());
    }
}
