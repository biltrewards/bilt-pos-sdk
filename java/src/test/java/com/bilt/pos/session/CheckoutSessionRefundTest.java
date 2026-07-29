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

class CheckoutSessionRefundTest {

    private static final String ORIGINAL_POI_TXN = "a816b0a9-8a11-4dc0-ba9d-5ad1e8c7e0d6";
    private static final Instant ORIGINAL_TS = Instant.parse("2026-03-02T14:35:12Z");

    private static final String REFUND_OK =
            "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                    + "\"Response\":{\"Result\":\"Success\"},"
                    + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-REF-100\","
                    + "\"TimeStamp\":\"2026-03-02T16:00:05+00:00\"}},"
                    + "\"PaymentResult\":{\"AmountsResp\":{\"Currency\":\"USD\",\"AuthorizedAmount\":24.99},"
                    + "\"PaymentAcquirerData\":{\"ApprovalCode\":\"APPR01\"}}}}}";

    private static final String AWARD_REFUND_OK =
            "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
                    + "\"Response\":{\"Result\":\"Success\"},"
                    + "\"LoyaltyResult\":[{\"CurrentBalance\":1100,"
                    + "\"LoyaltyAmount\":{\"AmountValue\":140,\"LoyaltyUnit\":\"Point\"}}]}}}";

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private CheckoutSession.Builder sessionBuilder() {
        return CheckoutSession.builder()
                .client(BiltNexoTerminalClient.builder()
                        .endpoint(server.url("/nexo").toString())
                        .disableRecoveryOnNetworkError()
                        .build())
                .saleId("POS-LANE-3")
                .poiId("VictaLane-275839164")
                .currency("USD")
                .autoDisplay(false);
    }

    private CheckoutSession refundSession() {
        return sessionBuilder()
                .poiTransactionId(ORIGINAL_POI_TXN)
                .poiTransactionTimestamp(ORIGINAL_TS)
                .build();
    }

    private SaleToPOIRequest recordedRequest() throws Exception {
        RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(recorded);
        return mapper.readValue(recorded.getBody().readUtf8(), NexoTerminalAPI.class)
                .getSaleToPOIRequest();
    }

    // ─── Linked refunds ───

    @Test
    void refundOutcomeIsDeliveredEvenWhenAbortRacesIt() throws Exception {
        CountDownLatch refundOnTheWire = new CountDownLatch(1);
        CountDownLatch aborted = new CountDownLatch(1);
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                String body = request.getBody().readUtf8();
                if (body.contains("\"PaymentRequest\"")) {
                    refundOnTheWire.countDown();
                    // hold the refund response until abort() has ended the session
                    aborted.await(5, TimeUnit.SECONDS);
                    return new MockResponse().setBody(REFUND_OK);
                }
                if (body.contains("\"LoyaltyRequest\"")) {
                    return new MockResponse().setBody(AWARD_REFUND_OK);
                }
                return new MockResponse();   // the AbortRequest, best-effort
            }
        });

        CheckoutSession session = refundSession();
        AtomicReference<RefundResult> delivered = new AtomicReference<>();
        AtomicReference<SessionError> failed = new AtomicReference<>();
        Thread register = new Thread(() -> session.refund(new BigDecimal("24.99"))
                .onSuccess(delivered::set)
                .onError(failed::set)
                .execute());
        register.start();
        assertTrue(refundOnTheWire.await(5, TimeUnit.SECONDS));

        session.abort();
        aborted.countDown();
        register.join(5_000);
        assertFalse(register.isAlive());

        // money moved on the terminal: unlike read-only prompts, the outcome
        // must be delivered even though the session has ended
        assertNull(failed.get());
        assertNotNull(delivered.get(),
                "a completed refund is money moved — its outcome must not be discarded");
        assertEquals(0, new BigDecimal("24.99").compareTo(delivered.get().getRefundedAmount()));
        assertEquals(SessionState.ABORTED, session.getState());
    }

    @Test
    void voidAfterUnlinkedRefundIsRejected() throws Exception {
        server.enqueue(new MockResponse().setBody(REFUND_OK));
        CheckoutSession session = refundSession();
        assertTrue(session.refundUnlinked(new BigDecimal("24.99")).get().isSuccess());
        recordedRequest();

        SessionException failure = assertThrows(SessionException.class,
                () -> session.voidTransaction().get());
        assertEquals(SessionErrorCode.INVALID_STATE, failure.getError().getCode());
        assertEquals(1, server.getRequestCount(),
                "the rejected void must not reach the wire");

        // further refunds remain the correct path for additional returns
        server.enqueue(new MockResponse().setBody(REFUND_OK));
        server.enqueue(new MockResponse().setBody(AWARD_REFUND_OK));
        assertTrue(session.refund(new BigDecimal("10.00")).get().isSuccess());
    }

    @Test
    void voidAfterLinkedRefundIsRejectedButFurtherRefundsWork() throws Exception {
        server.enqueue(new MockResponse().setBody(REFUND_OK));
        server.enqueue(new MockResponse().setBody(AWARD_REFUND_OK));
        CheckoutSession session = refundSession();
        assertTrue(session.refund(new BigDecimal("24.99")).get().isSuccess());
        recordedRequest();
        recordedRequest();

        SessionException failure = assertThrows(SessionException.class,
                () -> session.voidTransaction().get());
        assertEquals(SessionErrorCode.INVALID_STATE, failure.getError().getCode());
        assertTrue(failure.getError().getMessage().contains("refund"),
                "the error must steer the register to further refunds");
        assertEquals(2, server.getRequestCount(),
                "the rejected void must not reach the wire");

        // further partial returns stay possible — that is the correct path
        server.enqueue(new MockResponse().setBody(REFUND_OK));
        server.enqueue(new MockResponse().setBody(AWARD_REFUND_OK));
        assertTrue(session.refund(new BigDecimal("10.00")).get().isSuccess());
    }

    @Test
    void partialLinkedRefundSendsRefundThenAwardRefund() throws Exception {
        server.enqueue(new MockResponse().setBody(REFUND_OK));
        server.enqueue(new MockResponse().setBody(AWARD_REFUND_OK));

        RefundResult result = refundSession().refund(new BigDecimal("24.99")).get();

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
        assertEquals(ORIGINAL_POI_TXN, awardRefund.getLoyaltyRequest().getLoyaltyTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
    }

    @Test
    void builderAwardReferenceIsUsedForTheLoyaltyReversal() throws Exception {
        server.enqueue(new MockResponse().setBody(REFUND_OK));
        server.enqueue(new MockResponse().setBody(AWARD_REFUND_OK));

        sessionBuilder()
                .poiTransactionId(ORIGINAL_POI_TXN)
                .poiTransactionTimestamp(ORIGINAL_TS)
                .awardPoiTransactionId("POI-AW-9")
                .memberId("98234")
                .build()
                .refund(new BigDecimal("10.00")).execute();

        recordedRequest();  // the refund itself
        SaleToPOIRequest awardRefund = recordedRequest();
        assertEquals("POI-AW-9", awardRefund.getLoyaltyRequest().getLoyaltyTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
        assertEquals("98234", awardRefund.getLoyaltyRequest().getLoyaltyData()[0]
                .getLoyaltyAccountID().getLoyaltyID(),
                "the builder-supplied member must reach the reversal's LoyaltyData");
    }

    @Test
    void builderMemberIdReachesVoidLoyaltyReversal() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"ReversalResponse\":{\"Response\":{\"Result\":\"Success\"}}}}"));
        server.enqueue(new MockResponse().setBody(AWARD_REFUND_OK));

        sessionBuilder()
                .poiTransactionId(ORIGINAL_POI_TXN)
                .poiTransactionTimestamp(ORIGINAL_TS)
                .memberId("98234")
                .build()
                .voidTransaction().execute();

        recordedRequest();  // the reversal
        SaleToPOIRequest awardRefund = recordedRequest();
        assertEquals("98234", awardRefund.getLoyaltyRequest().getLoyaltyData()[0]
                .getLoyaltyAccountID().getLoyaltyID());
    }

    @Test
    void fullLinkedRefundOmitsAmount() throws Exception {
        server.enqueue(new MockResponse().setBody(REFUND_OK));
        server.enqueue(new MockResponse().setBody(AWARD_REFUND_OK));

        refundSession().refund().execute();

        SaleToPOIRequest refund = recordedRequest();
        assertNull(refund.getPaymentRequest().getPaymentTransaction()
                .getAmountsReq().getRequestedAmount());
        assertEquals("USD", refund.getPaymentRequest().getPaymentTransaction()
                .getAmountsReq().getCurrency());
    }

    @Test
    void linkedRefundWithoutBuilderReferenceFailsWithInvalidState() {
        SessionException e = assertThrows(SessionException.class,
                () -> sessionBuilder().build().refund().get());
        assertEquals(SessionErrorCode.INVALID_STATE, e.getError().getCode());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void failedLoyaltyReversalDoesNotFailTheRefund() {
        server.enqueue(new MockResponse().setBody(REFUND_OK));
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"UnavailableService\"}}}}"));

        RefundResult result = refundSession().refund(new BigDecimal("10.00")).get();

        assertTrue(result.isSuccess());
        assertEquals(0, result.getPointsReversed());
    }

    @Test
    void declinedRefundGoesToErrorChannel() {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Refusal\"}}}}"));

        SessionException e = assertThrows(SessionException.class,
                () -> refundSession().refund(new BigDecimal("10.00")).get());
        assertEquals(SessionErrorCode.DECLINED, e.getError().getCode());
        assertEquals(1, server.getRequestCount(), "no loyalty reversal after a declined refund");
    }

    // ─── Unlinked refunds ───

    @Test
    void unlinkedRefundSendsNoOriginalTransactionAndNoLoyaltyReversal() throws Exception {
        server.enqueue(new MockResponse().setBody(REFUND_OK));

        RefundResult result = sessionBuilder().build()
                .refundUnlinked(new BigDecimal("15.00")).get();

        assertTrue(result.isSuccess());
        assertEquals(0, result.getPointsReversed());
        assertEquals(1, server.getRequestCount());

        SaleToPOIRequest refund = recordedRequest();
        assertEquals("Refund", refund.getPaymentRequest().getPaymentData().getPaymentType().toValue());
        assertNull(refund.getPaymentRequest().getPaymentTransaction().getOriginalPOITransaction());
        assertEquals(15.00, refund.getPaymentRequest().getPaymentTransaction()
                .getAmountsReq().getRequestedAmount());
    }

    @Test
    void refundAmountMustBePositive() {
        CheckoutSession session = refundSession();
        assertThrows(IllegalArgumentException.class, () -> session.refund(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> session.refundUnlinked(new BigDecimal("-1")));
    }

    // ─── Void ───

    @Test
    void voidTransactionSendsReversalAndAwardRefundAndEndsVoided() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},\"ReversedAmount\":97.94,"
                        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-REV-7\","
                        + "\"TimeStamp\":\"2026-03-02T16:10:00+00:00\"}}}}}"));
        server.enqueue(new MockResponse().setBody(AWARD_REFUND_OK));

        CheckoutSession session = refundSession();
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
    void failedVoidRestoresThePreVoidState() {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"NotFound\"}}}}"));

        CheckoutSession session = refundSession();
        assertThrows(SessionException.class, () -> session.voidTransaction().get());

        // the referenced transaction still stands; a fresh refund session
        // returns to IDLE and the void can be retried
        assertEquals(SessionState.IDLE, session.getState());
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"ReversalResponse\":{\"Response\":{\"Result\":\"Success\"}}}}"));
        server.enqueue(new MockResponse().setBody(AWARD_REFUND_OK));
        assertTrue(session.voidTransaction().get().isSuccess());
        assertEquals(SessionState.VOIDED, session.getState());
    }

    @Test
    void voidWithoutBuilderReferenceFailsWithInvalidState() {
        SessionException e = assertThrows(SessionException.class,
                () -> sessionBuilder().build().voidTransaction().get());
        assertEquals(SessionErrorCode.INVALID_STATE, e.getError().getCode());
    }

    @Test
    void voidedSessionRejectsFurtherOperations() {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"ReversalResponse\":{\"Response\":{\"Result\":\"Success\"}}}}"));
        server.enqueue(new MockResponse().setBody(AWARD_REFUND_OK));

        CheckoutSession session = refundSession();
        session.voidTransaction().execute();

        assertEquals(SessionState.VOIDED, session.getState());
        SessionException e = assertThrows(SessionException.class,
                () -> session.refund().get());
        assertEquals(SessionErrorCode.INVALID_STATE, e.getError().getCode());
    }
}
