package com.bilt.pos.session;

import com.bilt.pos.display.DisplayPayloadHelper;
import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.session.basket.BasketItem;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The session's terminal-side lifecycle bracket: the builder's
 * {@code start()} announces the session, {@code end()} tells the terminal to
 * discard its session-scoped data and seals the session.
 */
class CheckoutSessionLifecycleTest {

    private static final String ADMIN_FAILED =
            "{\"SaleToPOIResponse\":{\"AdminResponse\":{"
                    + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Busy\"}}}}";

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

    private CheckoutSession startedSession() throws Exception {
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));
        CheckoutSession session = sessionBuilder().start().get();
        server.takeRequest(5, TimeUnit.SECONDS);
        return session;
    }

    private SaleToPOIRequest recordedRequest() throws Exception {
        RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(recorded, "expected a request to reach the terminal");
        return mapper.readValue(recorded.getBody().readUtf8(), NexoTerminalAPI.class)
                .getSaleToPOIRequest();
    }

    // ─── Start ───

    @Test
    void startIsLazyUntilExecuted() {
        sessionBuilder().start();
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void startSendsSessionStartAdminSignalAndYieldsTheSession() throws Exception {
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));

        CheckoutSession session = sessionBuilder().start().get();

        assertNotNull(session);

        SaleToPOIRequest sent = recordedRequest();
        assertEquals("Service", sent.getMessageHeader().getMessageClass().toValue());
        assertEquals("Admin", sent.getMessageHeader().getMessageCategory().toValue());
        assertEquals("POS-LANE-3", sent.getMessageHeader().getSaleID());
        assertEquals("VictaLane-275839164", sent.getMessageHeader().getPoiid());
        assertEquals("BiltSession,Start,v1," + session.getSessionId(),
                sent.getAdminRequest().getServiceIdentification());
    }

    @Test
    void refusedStartYieldsNoSession() {
        server.enqueue(new MockResponse().setBody(ADMIN_FAILED));

        assertNull(sessionBuilder().start().getOrNull(),
                "a refused start must not hand out a session");
    }

    @Test
    void throwingStartSuccessHandlerEndsTheJustStartedSession() throws Exception {
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));  // start
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));  // release end

        java.util.concurrent.atomic.AtomicReference<CheckoutSession> delivered =
                new java.util.concurrent.atomic.AtomicReference<>();
        SessionResult<CheckoutSession> start = sessionBuilder().start()
                .onSuccess(session -> {
                    delivered.set(session);
                    throw new IllegalStateException("register bug");
                });

        IllegalStateException e = assertThrows(IllegalStateException.class, start::executeSync);
        assertEquals("register bug", e.getMessage());

        // the terminal had acknowledged Start; the escaping handler must not
        // strand that session-scoped context — the session is ended for it

        // and later accessors must not report the released session as a
        // success: the handler failure stays loud, the session is lost
        assertSame(e, assertThrows(IllegalStateException.class, start::getOrNull));
        assertSame(e, assertThrows(IllegalStateException.class, start::isSuccess));

        SaleToPOIRequest first = recordedRequest();
        SaleToPOIRequest second = recordedRequest();
        assertEquals("BiltSession,Start,v1," + delivered.get().getSessionId(),
                first.getAdminRequest().getServiceIdentification());
        assertEquals("BiltSession,End,v1," + delivered.get().getSessionId(),
                second.getAdminRequest().getServiceIdentification());
    }

    @Test
    void failedReleaseEndDoesNotMaskTheHandlerFailure() throws Exception {
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));  // start
        server.enqueue(new MockResponse().setBody(ADMIN_FAILED));                  // release end fails

        SessionResult<CheckoutSession> start = sessionBuilder().start()
                .onSuccess(session -> {
                    throw new IllegalStateException("register bug");
                });

        IllegalStateException e = assertThrows(IllegalStateException.class, start::executeSync);
        assertEquals("register bug", e.getMessage(),
                "the best-effort release must not replace the handler's exception");
    }

    @Test
    void failedStartThrowsFromGet() {
        server.enqueue(new MockResponse().setBody(ADMIN_FAILED));

        SessionException e = assertThrows(SessionException.class,
                () -> sessionBuilder().start().get());
        assertEquals(SessionErrorCode.TERMINAL_ERROR, e.getError().getCode());
    }

    // ─── End ───

    @Test
    void endSendsSessionEndAdminSignalAndSealsTheSession() throws Exception {
        CheckoutSession session = startedSession();
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));

        session.end().executeSync();

        SaleToPOIRequest sent = recordedRequest();
        assertEquals("Admin", sent.getMessageHeader().getMessageCategory().toValue());
        assertEquals("BiltSession,End,v1," + session.getSessionId(),
                sent.getAdminRequest().getServiceIdentification());
    }

    @Test
    void endIsAllowedAfterAnAbort() throws Exception {
        CheckoutSession session = startedSession();
        session.abort().executeSync();
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));
        session.end().executeSync();

    }

    @Test
    void secondEndFailsWithInvalidState() throws Exception {
        CheckoutSession session = startedSession();
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));
        session.end().executeSync();

        SessionException e = assertThrows(SessionException.class,
                () -> session.end().get());
        assertEquals(SessionErrorCode.INVALID_STATE, e.getError().getCode());
    }

    @Test
    void failedEndKeepsTheSessionUsableForARetry() throws Exception {
        CheckoutSession session = startedSession();
        session.basket().addItem(BasketItem.sale("SKU-1", "Item", 1, new BigDecimal("10.00")));

        server.enqueue(new MockResponse().setBody(ADMIN_FAILED));
        SessionException e = assertThrows(SessionException.class, () -> session.end().get());
        assertEquals(SessionErrorCode.TERMINAL_ERROR, e.getError().getCode());

        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));
        session.end().executeSync();
    }

    @Test
    void failedForceEndStillSealsTheLocalSession() throws Exception {
        CheckoutSession session = startedSession();
        server.enqueue(new MockResponse().setBody(ADMIN_FAILED));

        SessionException failure = assertThrows(SessionException.class,
                () -> session.forceEnd("terminal cleanup escalated").get());

        assertEquals(SessionErrorCode.TERMINAL_ERROR, failure.getError().getCode());
        assertEquals(SessionErrorCode.INVALID_STATE, assertThrows(SessionException.class,
                () -> session.identifyMember().get()).getError().getCode());
        assertEquals(SessionErrorCode.INVALID_STATE, assertThrows(SessionException.class,
                () -> session.forceEnd("second attempt").get()).getError().getCode());
        assertEquals(2, server.getRequestCount(),
                "a failed forced end cannot be retried on the sealed session");
    }

    @Test
    void forceEndRequiresAnOperationalReason() throws Exception {
        CheckoutSession session = startedSession();

        assertThrows(NullPointerException.class, () -> session.forceEnd(null));
        assertThrows(IllegalArgumentException.class, () -> session.forceEnd("  \t"));
        assertEquals(1, server.getRequestCount(),
                "invalid reasons must fail before creating or sending an operation");
    }

    @Test
    void abortDoesNotCancelAnInFlightEnd() throws Exception {
        CountDownLatch endOnTheWire = new CountDownLatch(1);
        CountDownLatch abortIssued = new CountDownLatch(1);
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                String body = request.getBody().readUtf8();
                if (body.contains("BiltSession,End")) {
                    endOnTheWire.countDown();
                    // hold the end response until abort() has run
                    abortIssued.await(5, TimeUnit.SECONDS);
                    return new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK);
                }
                if (body.contains("\"AdminRequest\"")) {
                    return new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK);
                }
                return new MockResponse();   // an AbortRequest would land here
            }
        });
        CheckoutSession session = sessionBuilder().start().get();

        AtomicReference<SessionError> failed = new AtomicReference<>();
        Thread register = new Thread(() -> session.end().onError(failed::set).executeSync());
        register.start();
        assertTrue(endOnTheWire.await(5, TimeUnit.SECONDS));

        session.abort().executeSync();
        abortIssued.countDown();
        register.join(5_000);
        assertFalse(register.isAlive());

        // Cancelling cleanup would only strand terminal session data, so the
        // end exchange settles despite the abort.
        assertNull(failed.get(), "the end exchange must settle despite the abort");
        assertEquals(2, server.getRequestCount(),
                "start and end only — no AbortRequest may target the end exchange");
    }

    // ─── Ended sessions are sealed ───

    @Test
    void endedSessionRejectsEveryOperation() throws Exception {
        CheckoutSession session = startedSession();
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));
        session.end().executeSync();
        int requestsBefore = server.getRequestCount();

        assertThrows(IllegalStateException.class,
                () -> session.basket().addItem(BasketItem.sale("SKU-1", "Item", 1, new BigDecimal("10.00"))));
        assertEquals(SessionErrorCode.INVALID_STATE, assertThrows(SessionException.class,
                () -> session.settle().get()).getError().getCode());
        assertEquals(SessionErrorCode.INVALID_STATE, assertThrows(SessionException.class,
                () -> session.identifyMember().get()).getError().getCode());
        assertEquals(SessionErrorCode.INVALID_STATE, assertThrows(SessionException.class,
                () -> session.refund().get()).getError().getCode());
        assertEquals(SessionErrorCode.INVALID_STATE, assertThrows(SessionException.class,
                () -> session.voidTransaction().get()).getError().getCode());
        assertEquals(SessionErrorCode.INVALID_STATE, assertThrows(SessionException.class,
                () -> session.getTransactionStatus("SVC1").get()).getError().getCode());
        // the device/admin operations are deliberately absent: they live on
        // session.terminal(), which is independent of the session bracket
        // and keeps working after end()

        // display fails like every other operation, through its own onError
        AtomicReference<SessionError> displayFailed = new AtomicReference<>();
        session.updateDisplay(DisplayPayloadHelper.standby("bye"))
                .onError(displayFailed::set)
                .executeSync();
        assertNotNull(displayFailed.get());
        assertEquals(SessionErrorCode.INVALID_STATE, displayFailed.get().getCode());

        assertEquals(requestsBefore, server.getRequestCount(),
                "nothing may reach the wire after end()");
    }

    @Test
    void abortAfterEndLeavesTheSessionEnded() throws Exception {
        CheckoutSession session = startedSession();
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));
        session.end().executeSync();

        assertDoesNotThrow(() -> session.abort().executeSync());
    }

    // ─── try-with-resources ───

    @Test
    void closeSendsTheEndSignal() throws Exception {
        CheckoutSession session = startedSession();
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));

        try (CheckoutSession resource = session) {
        }

        SaleToPOIRequest sent = recordedRequest();
        assertEquals("BiltSession,End,v1," + session.getSessionId(),
                sent.getAdminRequest().getServiceIdentification());
    }

    @Test
    void closeAfterEndIsANoOp() throws Exception {
        CheckoutSession session = startedSession();
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));
        session.end().executeSync();
        int requestsBefore = server.getRequestCount();

        assertDoesNotThrow(session::close);
        assertEquals(requestsBefore, server.getRequestCount());
    }

    @Test
    void closeSwallowsAFailedEnd() throws Exception {
        CheckoutSession session = startedSession();
        server.enqueue(new MockResponse().setBody(ADMIN_FAILED));

        assertDoesNotThrow(session::close);
    }
}
