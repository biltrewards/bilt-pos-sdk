package com.bilt.pos.session;

import com.bilt.pos.display.DisplayPayload;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The asynchronous, conflated auto-display push and the session-level
 * background-error handler.
 */
class CheckoutSessionAutoDisplayTest {

    private static final String ADMIN_OK = CheckoutSessionTest.ADMIN_OK;

    private static final String DISPLAY_OK =
            "{\"SaleToPOIResponse\":{\"DisplayResponse\":{}}}";

    private static final String PAYMENT_OK =
            "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                    + "\"Response\":{\"Result\":\"Success\"},"
                    + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-PAY-1\","
                    + "\"TimeStamp\":\"2026-07-20T10:00:03Z\"}},"
                    + "\"PaymentResult\":{"
                    + "\"AmountsResp\":{\"Currency\":\"USD\",\"AuthorizedAmount\":50.00}}}}}";

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
                .currency("USD");
    }

    /** Starts the session and drains the session-start Admin request. */
    private CheckoutSession start(CheckoutSession.Builder builder) throws Exception {
        CheckoutSession session = builder.start().get();
        server.takeRequest(5, TimeUnit.SECONDS);
        return session;
    }

    /** All requests the server has recorded but the test has not consumed. */
    private List<SaleToPOIRequest> drainRequests() throws Exception {
        List<SaleToPOIRequest> requests = new ArrayList<>();
        RecordedRequest recorded;
        while ((recorded = server.takeRequest(200, TimeUnit.MILLISECONDS)) != null) {
            requests.add(mapper.readValue(recorded.getBody().readUtf8(),
                    NexoTerminalAPI.class).getSaleToPOIRequest());
        }
        return requests;
    }

    private static boolean isDisplay(SaleToPOIRequest request) {
        return "Display".equals(request.getMessageHeader().getMessageCategory().toValue());
    }

    private static int lineItemCount(SaleToPOIRequest displayRequest) throws Exception {
        DisplayPayload payload = DisplayPayloadHelper.fromBase64(displayRequest
                .getDisplayRequest().getDisplayOutput()[0].getOutputContent().getOutputXHTML());
        return payload.getReceipt().getLineItems().getLineItem().size();
    }

    // ─── Conflation ───

    @Test
    void rapidMutationsConflateToTheNewestSnapshot() throws Exception {
        CountDownLatch ringUpDone = new CountDownLatch(1);
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                if (request.getBody().clone().readUtf8().contains("\"DisplayRequest\"")) {
                    // park every display roundtrip until the ring-up is
                    // done, so the mutations outrun the pushes
                    ringUpDone.await(5, TimeUnit.SECONDS);
                    return new MockResponse().setBody(DISPLAY_OK);
                }
                return new MockResponse().setBody(ADMIN_OK);
            }
        });
        CheckoutSession session = start(sessionBuilder());

        for (int i = 1; i <= 5; i++) {
            session.basket().addItem(BasketItem.of("SKU-" + i, "Item " + i, 1, "10.00"));
        }
        ringUpDone.countDown();
        // queued behind any pending push, so its completion proves the
        // pushes have settled
        session.end().get();

        List<SaleToPOIRequest> displays = drainRequests().stream()
                .filter(CheckoutSessionAutoDisplayTest::isDisplay)
                .collect(Collectors.toList());
        assertFalse(displays.isEmpty(), "the ring-up must reach the customer display");
        assertTrue(displays.size() <= 2,
                "conflation allows at most one push in flight plus one queued, got "
                        + displays.size());
        assertEquals(5, lineItemCount(displays.get(displays.size() - 1)),
                "the last push must carry the newest snapshot");
    }

    // ─── Lane ordering ───

    @Test
    void payQueuesBehindThePendingDisplayPush() throws Exception {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String body = request.getBody().clone().readUtf8();
                if (body.contains("\"DisplayRequest\"")) {
                    return new MockResponse().setBody(DISPLAY_OK);
                }
                if (body.contains("\"PaymentRequest\"")) {
                    return new MockResponse().setBody(PAYMENT_OK);
                }
                return new MockResponse().setBody(ADMIN_OK);
            }
        });
        CheckoutSession session = start(sessionBuilder());

        CountDownLatch paid = new CountDownLatch(1);
        session.basket().addItem(BasketItem.of("SKU-1", "Item", 1, "50.00"));
        session.settle().onComplete(paid::countDown).execute();
        assertTrue(paid.await(5, TimeUnit.SECONDS));

        // three requests: the pending push, the payment queued behind it,
        // and the payment sequence's own final display refresh
        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(3, requests.size());
        assertTrue(isDisplay(requests.get(0)),
                "the pending push must run before the payment queued behind it");
        assertNotNull(requests.get(1).getPaymentRequest());
        assertTrue(isDisplay(requests.get(2)));
    }

    @Test
    void lateMutationPushDoesNotOverwriteThePaymentsFinalDisplay() throws Exception {
        CountDownLatch firstPushOnTheWire = new CountDownLatch(1);
        CountDownLatch lateItemRung = new CountDownLatch(1);
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                String body = request.getBody().clone().readUtf8();
                if (body.contains("\"DisplayRequest\"")) {
                    if (firstPushOnTheWire.getCount() > 0) {
                        firstPushOnTheWire.countDown();
                        // park the first push so both the payment and the
                        // late mutation queue behind it
                        lateItemRung.await(5, TimeUnit.SECONDS);
                    }
                    return new MockResponse().setBody(DISPLAY_OK);
                }
                if (body.contains("\"PaymentRequest\"")) {
                    return new MockResponse().setBody(
                            "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                                    + "\"Response\":{\"Result\":\"Success\"},"
                                    + "\"POIData\":{\"POITransactionID\":{"
                                    + "\"TransactionID\":\"POI-PAY-1\","
                                    + "\"TimeStamp\":\"2026-07-20T10:00:03Z\"}},"
                                    + "\"PaymentResult\":{\"AmountsResp\":{"
                                    + "\"Currency\":\"USD\",\"AuthorizedAmount\":60.00}}}}}");
                }
                return new MockResponse().setBody(ADMIN_OK);
            }
        });
        CheckoutSession session = start(sessionBuilder());

        session.basket().addItem(BasketItem.of("SKU-1", "Item", 1, "50.00"));
        assertTrue(firstPushOnTheWire.await(5, TimeUnit.SECONDS));
        CountDownLatch paid = new CountDownLatch(1);
        session.settle().onComplete(paid::countDown).execute();
        // accepted — the queued payment has not started, so the session is
        // still ACTIVE. The item is part of the charged basket; its push,
        // queued behind the payment, must not run at COMPLETED.
        session.basket().addItem(BasketItem.of("SKU-2", "Item", 1, "10.00"));
        lateItemRung.countDown();
        assertTrue(paid.await(5, TimeUnit.SECONDS));
        session.end().get();

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(4, requests.size(),
                "push, payment, final display, end — no stale cart display after the "
                        + "payment settled");
        assertEquals(1, lineItemCount(requests.get(0)));
        assertNotNull(requests.get(1).getPaymentRequest());
        assertEquals(2, lineItemCount(requests.get(2)),
                "the final display carries the basket the payment charged");
        assertNotNull(requests.get(3).getAdminRequest());
    }

    // ─── No push after end ───

    @Test
    void queuedPushAgainstAnEndedSessionSendsNothing() throws Exception {
        CountDownLatch endOnTheWire = new CountDownLatch(1);
        CountDownLatch mutated = new CountDownLatch(1);
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                String body = request.getBody().clone().readUtf8();
                if (body.contains("BiltSession,End")) {
                    endOnTheWire.countDown();
                    // hold the end so a mutation can queue its push behind it
                    mutated.await(5, TimeUnit.SECONDS);
                }
                return new MockResponse().setBody(ADMIN_OK);
            }
        });
        CheckoutSession session = start(sessionBuilder());

        CountDownLatch ended = new CountDownLatch(1);
        session.end().onComplete(ended::countDown).execute();
        assertTrue(endOnTheWire.await(5, TimeUnit.SECONDS));
        // the end has not settled, so the basket still accepts the mutation
        // — but its push is queued behind the end and runs against ENDED
        session.basket().addItem(BasketItem.of("SKU-1", "Item", 1, "10.00"));
        mutated.countDown();
        assertTrue(ended.await(5, TimeUnit.SECONDS));

        assertEquals(SessionState.ENDED, session.getState());
        List<SaleToPOIRequest> requests = drainRequests();
        assertTrue(requests.stream().noneMatch(CheckoutSessionAutoDisplayTest::isDisplay),
                "a push that finds the session ended must send nothing");
    }

    // ─── Background-error reporting ───

    @Test
    void autoDisplayFailureFiresOnBackgroundErrorAndTheCheckoutContinues() throws Exception {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getBody().clone().readUtf8().contains("\"DisplayRequest\"")) {
                    return new MockResponse().setResponseCode(500);
                }
                return new MockResponse().setBody(ADMIN_OK);
            }
        });
        ExecutorService callbackExecutor = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "register-ui"));
        try {
            CountDownLatch reported = new CountDownLatch(1);
            AtomicReference<SessionError> error = new AtomicReference<>();
            AtomicReference<String> deliveryThread = new AtomicReference<>();
            CheckoutSession session = start(sessionBuilder()
                    .callbackExecutor(callbackExecutor)
                    .onBackgroundError(e -> {
                        error.set(e);
                        deliveryThread.set(Thread.currentThread().getName());
                        reported.countDown();
                    }));

            session.basket().addItem(BasketItem.of("SKU-1", "Item", 1, "10.00"));

            assertTrue(reported.await(5, TimeUnit.SECONDS),
                    "the failed push must reach onBackgroundError");
            assertNotNull(error.get());
            assertEquals("register-ui", deliveryThread.get(),
                    "background errors deliver on the callback executor");
            assertEquals(SessionState.ACTIVE, session.getState(),
                    "a failed push never interrupts the checkout");
            assertEquals(new BigDecimal("20.00"), session.basket()
                    .addItem(BasketItem.of("SKU-2", "Item", 1, "10.00")).getGrandTotal());
        } finally {
            callbackExecutor.shutdownNow();
        }
    }

    @Test
    void manualUpdateDisplayFailureSkipsTheBackgroundHandler() throws Exception {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getBody().clone().readUtf8().contains("\"DisplayRequest\"")) {
                    return new MockResponse().setResponseCode(500);
                }
                return new MockResponse().setBody(ADMIN_OK);
            }
        });
        AtomicReference<SessionError> background = new AtomicReference<>();
        CheckoutSession session = start(sessionBuilder()
                .autoDisplay(false)
                .onBackgroundError(background::set));

        AtomicReference<SessionError> ownError = new AtomicReference<>();
        session.updateDisplay(DisplayPayloadHelper.standby("welcome"))
                .onError(ownError::set)
                .executeSync();

        assertNotNull(ownError.get(), "a manual update fails into its own onError");
        assertNull(background.get(),
                "manual updateDisplay failures must not blur into the background handler");
    }

    // ─── Abort overtake ───

    @Test
    void abortExecuteOvertakesAParkedPayment() throws Exception {
        CountDownLatch paymentOnTheWire = new CountDownLatch(1);
        CountDownLatch abortSeen = new CountDownLatch(1);
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                String body = request.getBody().clone().readUtf8();
                if (body.contains("\"PaymentRequest\"")) {
                    paymentOnTheWire.countDown();
                    // parked until the abort reaches the wire; declined, so
                    // the aborted payment settles without a rollback leg
                    abortSeen.await(5, TimeUnit.SECONDS);
                    return new MockResponse().setBody(
                            "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                                    + "\"Response\":{\"Result\":\"Failure\","
                                    + "\"ErrorCondition\":\"Refusal\"}}}}");
                }
                if (body.contains("\"AbortRequest\"")) {
                    abortSeen.countDown();
                    return new MockResponse();   // best-effort, no body expected
                }
                return new MockResponse().setBody(ADMIN_OK);
            }
        });
        CheckoutSession session = start(sessionBuilder().autoDisplay(false));
        session.basket().addItem(BasketItem.of("SKU-1", "Item", 1, "50.00"));

        CountDownLatch paymentSettled = new CountDownLatch(1);
        session.settle().onComplete(paymentSettled::countDown).execute();
        assertTrue(paymentOnTheWire.await(5, TimeUnit.SECONDS));

        session.abort().execute();

        assertTrue(abortSeen.await(5, TimeUnit.SECONDS),
                "abort().execute() must reach the wire while the payment is parked "
                        + "on the operation lane");
        assertTrue(paymentSettled.await(5, TimeUnit.SECONDS));
    }
}
