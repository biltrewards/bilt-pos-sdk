package com.bilt.pos.session;

import com.bilt.pos.display.DisplayPayloadHelper;
import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutSessionTest {

    static final String ADMIN_OK =
            "{\"SaleToPOIResponse\":{\"MessageHeader\":{\"ProtocolVersion\":\"3.0\"},"
                    + "\"AdminResponse\":{\"Response\":{\"Result\":\"Success\"}}}}";

    // canonical terminal replies for the reversal verbs, shared by the
    // session test classes
    static final String REVERSAL_OK =
            "{\"SaleToPOIResponse\":{\"ReversalResponse\":{\"Response\":{\"Result\":\"Success\"}}}}";

    static final String LOYALTY_REFUND_OK =
            "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{\"Response\":{\"Result\":\"Success\"}}}}";

    static final String LOYALTY_REFUND_FAILED =
            "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
                    + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"UnavailableService\"}}}}";

    /** A successful refund reply authorizing the given amount. */
    static String refundOk(double authorized) {
        return "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                + "\"Response\":{\"Result\":\"Success\"},"
                + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-REF-100\","
                + "\"TimeStamp\":\"2026-03-02T16:00:05+00:00\"}},"
                + "\"PaymentResult\":{\"AmountsResp\":{\"Currency\":\"USD\","
                + "\"AuthorizedAmount\":" + authorized + "},"
                + "\"PaymentAcquirerData\":{\"ApprovalCode\":\"APPR01\"}}}}}";
    }

    /**
     * A dispatcher answering the session start/end Admin exchanges out of
     * band, so tests enqueue their responses in wire order without the
     * bracket eating them.
     */
    static QueueDispatcher adminAnsweringDispatcher() {
        return new QueueDispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                if (request.getBody().clone().readUtf8().contains("\"AdminRequest\"")) {
                    return new MockResponse().setBody(ADMIN_OK);
                }
                return super.dispatch(request);
            }
        };
    }

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private MockWebServer server;
    private CheckoutSession session;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        session = start(CheckoutSession.builder()
                .client(terminalClient())
                .saleId("POS-LANE-3")
                .poiId("VictaLane-275839164")
                .currency("USD")
                .storeLocation("STR-0142"));
    }

    /** Starts the session against a mocked session-start acknowledgement. */
    private CheckoutSession start(CheckoutSession.Builder builder) throws Exception {
        server.enqueue(new MockResponse().setBody(ADMIN_OK));
        CheckoutSession started = builder.start().get();
        server.takeRequest(5, TimeUnit.SECONDS); // drain the session-start Admin request
        return started;
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private BiltNexoTerminalClient terminalClient() {
        return BiltNexoTerminalClient.builder()
                .endpoint(server.url("/nexo").toString())
                .disableRecoveryOnNetworkError()
                .build();
    }

    private SaleToPOIRequest recordedRequest() throws Exception {
        RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(recorded, "expected a request to reach the terminal");
        return mapper.readValue(recorded.getBody().readUtf8(), NexoTerminalAPI.class)
                .getSaleToPOIRequest();
    }

    // ─── Builder ───

    @Test
    void builderRequiresMandatoryFields() {
        assertThrows(IllegalStateException.class, () -> CheckoutSession.builder().start());
        assertThrows(IllegalStateException.class, () -> CheckoutSession.builder()
                .client(terminalClient()).saleId("s").poiId("p").start());
    }

    @Test
    void sessionStartsIdleWithIdentity() {
        assertEquals(SessionState.IDLE, session.getState());
        assertNotNull(session.getSessionId());
        assertEquals("USD", session.getCurrency());
        assertEquals("STR-0142", session.getStoreLocation());
        assertNotNull(session.getClient());
    }

    // ─── Laziness ───

    @Test
    void creatingAnOperationSendsNothing() {
        session.diagnose().onSuccess(r -> { }).onError(e -> { });
        assertEquals(1, server.getRequestCount(), "only the session start may hit the wire");
    }

    // ─── Diagnose ───

    @Test
    void diagnoseSendsWellFormedRequestAndMapsResponse() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"MessageHeader\":{\"ProtocolVersion\":\"3.0\"},"
                        + "\"DiagnosisResponse\":{\"Response\":{\"Result\":\"Success\"},"
                        + "\"POIStatus\":{\"GlobalStatus\":\"OK\",\"PrinterStatus\":\"OK\"},"
                        + "\"HostStatus\":[{\"AcquirerID\":\"ACQ1\",\"IsReachableFlag\":true}]}}}"));

        DiagnosisResult result = session.diagnose().get();

        assertNotNull(result.getPoiStatus());
        assertEquals(1, result.getHostStatuses().size());
        assertEquals("ACQ1", result.getHostStatuses().get(0).getAcquirerID());

        SaleToPOIRequest sent = recordedRequest();
        assertEquals("3.0", sent.getMessageHeader().getProtocolVersion());
        assertEquals("Service", sent.getMessageHeader().getMessageClass().toValue());
        assertEquals("Diagnosis", sent.getMessageHeader().getMessageCategory().toValue());
        assertEquals("Request", sent.getMessageHeader().getMessageType().toValue());
        assertEquals("POS-LANE-3", sent.getMessageHeader().getSaleID());
        assertEquals("VictaLane-275839164", sent.getMessageHeader().getPoiid());
        String serviceId = sent.getMessageHeader().getServiceID();
        assertNotNull(serviceId);
        assertTrue(serviceId.matches("[A-Z0-9]{10}"), "ServiceID format: " + serviceId);
        assertNotNull(sent.getDiagnosisRequest());
    }

    @Test
    void diagnoseFailureResultIsMappedToTerminalError() {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"DiagnosisResponse\":"
                        + "{\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"UnavailableDevice\"}}}}"));

        AtomicReference<SessionError> error = new AtomicReference<>();
        session.diagnose().onError(error::set).executeSync();

        assertNotNull(error.get());
        assertEquals(SessionErrorCode.TERMINAL_ERROR, error.get().getCode());
        assertEquals("UnavailableDevice", error.get().getNexoErrorCondition());
    }

    @Test
    void transportFailureIsMappedToNetworkError() throws Exception {
        server.shutdown();

        SessionException e = assertThrows(SessionException.class, () -> session.diagnose().get());
        assertEquals(SessionErrorCode.NETWORK, e.getError().getCode());
    }

    // ─── Reconcile ───

    @Test
    void reconcileSendsSaleReconciliationAndMapsTotals() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"ReconciliationResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},"
                        + "\"POIReconciliationID\":\"REC-42\","
                        + "\"TransactionTotals\":[{\"PaymentCurrency\":\"USD\"}]}}}"));

        ReconciliationResult result = session.reconcile().get();

        assertEquals("REC-42", result.getPoiReconciliationId());
        assertEquals(1, result.getTransactionTotals().size());

        SaleToPOIRequest sent = recordedRequest();
        assertEquals("Reconciliation", sent.getMessageHeader().getMessageCategory().toValue());
        assertEquals("SaleReconciliation",
                sent.getReconciliationRequest().getReconciliationType().toValue());
    }

    // ─── Print ───

    @Test
    void printSendsTextContent() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"PrintResponse\":{\"Response\":{\"Result\":\"Success\"}}}}"));

        session.print(PrintPayload.text("THANK YOU")).executeSync();

        SaleToPOIRequest sent = recordedRequest();
        assertEquals("Print", sent.getMessageHeader().getMessageCategory().toValue());
        assertEquals("Device", sent.getMessageHeader().getMessageClass().toValue());
        assertEquals("CustomerReceipt",
                sent.getPrintRequest().getPrintOutput().getDocumentQualifier().toValue());
        assertEquals("Text",
                sent.getPrintRequest().getPrintOutput().getOutputContent().getOutputFormat().toValue());
        assertEquals("THANK YOU",
                sent.getPrintRequest().getPrintOutput().getOutputContent().getOutputText()[0].getText());
    }

    // ─── Transaction status ───

    @Test
    void transactionStatusFoundRepeatsOriginalPaymentResponse() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"TransactionStatusResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},"
                        + "\"RepeatedMessageResponse\":{"
                        + "\"MessageHeader\":{\"MessageCategory\":\"Payment\"},"
                        + "\"RepeatedResponseMessageBody\":{"
                        + "\"PaymentResponse\":{\"Response\":{\"Result\":\"Success\"}}}}}}}"));

        TransactionStatusResult result = session.getTransactionStatus("SVC0084200").get();

        assertTrue(result.isFound());
        assertEquals("Payment", result.getMessageCategory());
        assertNotNull(result.getPaymentResponse());
        assertNull(result.getLoyaltyResponse());

        SaleToPOIRequest sent = recordedRequest();
        assertEquals("TransactionStatus", sent.getMessageHeader().getMessageCategory().toValue());
        assertEquals("SVC0084200",
                sent.getTransactionStatusRequest().getMessageReference().getServiceID());
        assertEquals("POS-LANE-3",
                sent.getTransactionStatusRequest().getMessageReference().getSaleID());
    }

    @Test
    void transactionStatusNotFoundIsSuccessWithFoundFalse() {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"TransactionStatusResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"NotFound\"}}}}"));

        TransactionStatusResult result = session.getTransactionStatus("UNKNOWN123").get();

        assertFalse(result.isFound());
        assertNull(result.getMessageCategory());
    }

    // ─── Display routing ───

    @Test
    void updateDisplayGoesToExternalDisplayWhenConfigured() throws Exception {
        try (MockWebServer displayServer = new MockWebServer()) {
            displayServer.start();
            displayServer.enqueue(new MockResponse().setBody(
                    "{\"SaleToPOIResponse\":{\"DisplayResponse\":{}}}"));

            CheckoutSession dual = start(CheckoutSession.builder()
                    .client(terminalClient())
                    .externalDisplayClient(BiltNexoTerminalClient.builder()
                            .endpoint(displayServer.url("/nexo").toString())
                            .disableRecoveryOnNetworkError()
                            .build())
                    .saleId("POS-LANE-3")
                    .poiId("VictaLane-275839164")
                    .currency("USD"));

            dual.updateDisplay(DisplayPayloadHelper.standby("welcome"));

            RecordedRequest recorded = displayServer.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(recorded, "display request must hit the external display");
            assertEquals(2, server.getRequestCount(),
                    "terminal must not receive display traffic (only the two session starts)");

            SaleToPOIRequest sent = mapper.readValue(recorded.getBody().readUtf8(),
                    NexoTerminalAPI.class).getSaleToPOIRequest();
            assertEquals("Display", sent.getMessageHeader().getMessageCategory().toValue());
            assertEquals("CustomerDisplay",
                    sent.getDisplayRequest().getDisplayOutput()[0].getDevice().toValue());
            assertNotNull(sent.getDisplayRequest().getDisplayOutput()[0]
                    .getOutputContent().getOutputXHTML());
        }
    }

    @Test
    void updateDisplayFailureDoesNotThrow() throws Exception {
        server.shutdown();
        assertDoesNotThrow(() ->
                session.updateDisplay(DisplayPayloadHelper.standby("welcome")));
    }

    // ─── Abort ───

    @Test
    void abortWithoutInFlightOperationIsANoOp() {
        SessionState before = session.getState();
        session.abort();

        assertEquals(before, session.getState(),
                "abort is operation-scoped; with nothing in flight the session is untouched");
        assertEquals(1, server.getRequestCount(), "only the session start may hit the wire");
        assertDoesNotThrow(session::abort);
    }
}
