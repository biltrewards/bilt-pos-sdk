package com.bilt.pos.session;

import com.bilt.pos.display.DisplayPayloadHelper;
import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.session.basket.BasketItem;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

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
        assertEquals(SessionState.IDLE, session.getState());

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

        IllegalStateException e = assertThrows(IllegalStateException.class, start::execute);
        assertEquals("register bug", e.getMessage());

        // the terminal had acknowledged Start; the escaping handler must not
        // strand that session-scoped context — the session is ended for it
        assertEquals(SessionState.ENDED, delivered.get().getState());
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

        IllegalStateException e = assertThrows(IllegalStateException.class, start::execute);
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

        session.end().execute();

        assertEquals(SessionState.ENDED, session.getState());
        assertTrue(session.getState().isTerminal());
        SaleToPOIRequest sent = recordedRequest();
        assertEquals("Admin", sent.getMessageHeader().getMessageCategory().toValue());
        assertEquals("BiltSession,End,v1," + session.getSessionId(),
                sent.getAdminRequest().getServiceIdentification());
    }

    @Test
    void endIsAllowedFromAborted() throws Exception {
        CheckoutSession session = startedSession();
        session.abort();
        assertEquals(SessionState.ABORTED, session.getState());

        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));
        session.end().execute();

        assertEquals(SessionState.ENDED, session.getState());
    }

    @Test
    void secondEndFailsWithInvalidState() throws Exception {
        CheckoutSession session = startedSession();
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));
        session.end().execute();

        SessionException e = assertThrows(SessionException.class,
                () -> session.end().get());
        assertEquals(SessionErrorCode.INVALID_STATE, e.getError().getCode());
    }

    @Test
    void failedEndKeepsTheSessionUsableForARetry() throws Exception {
        CheckoutSession session = startedSession();
        session.addItem(BasketItem.of("SKU-1", "Item", 1, "10.00"));

        server.enqueue(new MockResponse().setBody(ADMIN_FAILED));
        SessionException e = assertThrows(SessionException.class, () -> session.end().get());
        assertEquals(SessionErrorCode.TERMINAL_ERROR, e.getError().getCode());
        assertEquals(SessionState.ACTIVE, session.getState(),
                "a failed end must leave the session where it was");

        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));
        session.end().execute();
        assertEquals(SessionState.ENDED, session.getState());
    }

    // ─── Ended sessions are sealed ───

    @Test
    void endedSessionRejectsEveryOperation() throws Exception {
        CheckoutSession session = startedSession();
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));
        session.end().execute();
        int requestsBefore = server.getRequestCount();

        assertThrows(IllegalStateException.class,
                () -> session.addItem(BasketItem.of("SKU-1", "Item", 1, "10.00")));
        assertThrows(IllegalStateException.class, session::pay);
        assertEquals(SessionErrorCode.INVALID_STATE, assertThrows(SessionException.class,
                () -> session.identifyMember().get()).getError().getCode());
        assertEquals(SessionErrorCode.INVALID_STATE, assertThrows(SessionException.class,
                () -> session.refund().get()).getError().getCode());
        assertEquals(SessionErrorCode.INVALID_STATE, assertThrows(SessionException.class,
                () -> session.voidTransaction().get()).getError().getCode());
        assertEquals(SessionErrorCode.INVALID_STATE, assertThrows(SessionException.class,
                () -> session.diagnose().get()).getError().getCode());
        assertEquals(SessionErrorCode.INVALID_STATE, assertThrows(SessionException.class,
                () -> session.getTotals().get()).getError().getCode());
        assertEquals(SessionErrorCode.INVALID_STATE, assertThrows(SessionException.class,
                () -> session.print(PrintPayload.text("x")).get()).getError().getCode());
        assertEquals(SessionErrorCode.INVALID_STATE, assertThrows(SessionException.class,
                () -> session.playSound("chime").get()).getError().getCode());
        assertEquals(SessionErrorCode.INVALID_STATE, assertThrows(SessionException.class,
                () -> session.getTransactionStatus("SVC1").get()).getError().getCode());

        // display is best-effort and never throws — it skips quietly
        assertDoesNotThrow(() -> session.updateDisplay(DisplayPayloadHelper.standby("bye")));

        assertEquals(requestsBefore, server.getRequestCount(),
                "nothing may reach the wire after end()");
    }

    @Test
    void abortAfterEndLeavesTheSessionEnded() throws Exception {
        CheckoutSession session = startedSession();
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));
        session.end().execute();

        assertDoesNotThrow(session::abort);
        assertEquals(SessionState.ENDED, session.getState());
    }

    // ─── try-with-resources ───

    @Test
    void closeSendsTheEndSignal() throws Exception {
        CheckoutSession session = startedSession();
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));

        try (CheckoutSession resource = session) {
            assertEquals(SessionState.IDLE, resource.getState());
        }

        assertEquals(SessionState.ENDED, session.getState());
        SaleToPOIRequest sent = recordedRequest();
        assertEquals("BiltSession,End,v1," + session.getSessionId(),
                sent.getAdminRequest().getServiceIdentification());
    }

    @Test
    void closeAfterEndIsANoOp() throws Exception {
        CheckoutSession session = startedSession();
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));
        session.end().execute();
        int requestsBefore = server.getRequestCount();

        assertDoesNotThrow(session::close);
        assertEquals(requestsBefore, server.getRequestCount());
    }

    @Test
    void closeSwallowsAFailedEnd() throws Exception {
        CheckoutSession session = startedSession();
        server.enqueue(new MockResponse().setBody(ADMIN_FAILED));

        assertDoesNotThrow(session::close);
        assertEquals(SessionState.IDLE, session.getState(),
                "a failed best-effort end leaves the session where it was");
    }
}
