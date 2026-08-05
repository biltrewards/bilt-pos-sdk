package com.bilt.pos.session;

import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Refunds and voids on the checkout session itself. Same-session reversal
 * of a completed payment is covered with the payment tests in
 * {@link CheckoutSessionPaymentTest}; cross-session (referenced) reversal
 * lives in {@link ReversalSessionTest}.
 */
class CheckoutSessionRefundTest {

    private static final String REFUND_OK = CheckoutSessionTest.refundOk(15.00);

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
        assertTrue(e.getError().getMessage().contains("ReversalSession"),
                "the error must point at ReversalSession for prior sales: "
                        + e.getError().getMessage());
        assertEquals(1, server.getRequestCount(), "only the session start may hit the wire");
    }

    @Test
    void voidWithoutPaymentFailsWithInvalidState() throws Exception {
        CheckoutSession session = session();
        SessionException e = assertThrows(SessionException.class,
                () -> session.voidTransaction().get());
        assertEquals(SessionErrorCode.INVALID_STATE, e.getError().getCode());
        assertTrue(e.getError().getMessage().contains("ReversalSession"),
                "the error must point at ReversalSession for prior sales: "
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
}
