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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Terminal}: the session-less device and admin operations. Unlike a
 * session there is no bracket — building one sends nothing, and closing one
 * sends nothing.
 */
class TerminalTest {

    private static final String DIAGNOSIS_OK =
            "{\"SaleToPOIResponse\":{\"DiagnosisResponse\":{"
                    + "\"Response\":{\"Result\":\"Success\"},"
                    + "\"POIStatus\":{\"GlobalStatus\":\"OK\"},"
                    + "\"HostStatus\":[{\"AcquirerID\":\"ACQ1\",\"IsReachableFlag\":true}]}}}";

    private static final String SOUND_OK =
            "{\"SaleToPOIResponse\":{\"SoundResponse\":{\"Response\":{\"Result\":\"Success\"}}}}";

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

    private Terminal.Builder terminalBuilder() {
        return Terminal.builder()
                .client(BiltNexoTerminalClient.builder()
                        .endpoint(server.url("/nexo").toString())
                        .disableRecoveryOnNetworkError()
                        .build())
                .saleId("POS-LANE-3")
                .poiId("VictaLane-275839164");
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
        assertThrows(IllegalStateException.class, () -> Terminal.builder().build());
        assertThrows(IllegalStateException.class,
                () -> Terminal.builder().saleId("s").poiId("p").build());
        assertThrows(IllegalStateException.class,
                () -> terminalBuilder().saleId(null).build());
        assertThrows(IllegalStateException.class,
                () -> terminalBuilder().poiId("").build());
    }

    @Test
    void buildSendsNothing() {
        Terminal terminal = terminalBuilder().build();

        assertNotNull(terminal);
        assertEquals(0, server.getRequestCount(),
                "there is no session bracket; build() must not touch the wire");
    }

    // ─── Diagnose ───

    @Test
    void diagnoseSendsWellFormedRequestAndMapsResponse() throws Exception {
        server.enqueue(new MockResponse().setBody(DIAGNOSIS_OK));

        DiagnosisResult result = terminalBuilder().build().diagnose().get();

        assertNotNull(result.getPoiStatus());
        assertEquals("ACQ1", result.getHostStatuses().get(0).getAcquirerID());

        SaleToPOIRequest sent = recordedRequest();
        assertEquals("Service", sent.getMessageHeader().getMessageClass().toValue());
        assertEquals("Diagnosis", sent.getMessageHeader().getMessageCategory().toValue());
        assertEquals("POS-LANE-3", sent.getMessageHeader().getSaleID());
        assertEquals("VictaLane-275839164", sent.getMessageHeader().getPoiid());
        assertNotNull(sent.getDiagnosisRequest());
    }

    @Test
    void diagnoseFailureResultIsMappedToTerminalError() {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"DiagnosisResponse\":"
                        + "{\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"UnavailableDevice\"}}}}"));

        AtomicReference<SessionError> error = new AtomicReference<>();
        terminalBuilder().build().diagnose().onError(error::set).executeSync();

        assertNotNull(error.get());
        assertEquals(SessionErrorCode.TERMINAL_ERROR, error.get().getCode());
        assertEquals("UnavailableDevice", error.get().getNexoErrorCondition());
    }

    // ─── Async execution ───

    @Test
    void asyncExecuteDeliversHandlersOnTheCallbackExecutor() throws Exception {
        server.enqueue(new MockResponse().setBody(DIAGNOSIS_OK));
        ExecutorService callback = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "terminal-test-callback"));
        try {
            Terminal terminal = terminalBuilder().callbackExecutor(callback).build();

            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<DiagnosisResult> delivered = new AtomicReference<>();
            AtomicReference<String> successThread = new AtomicReference<>();
            AtomicReference<String> completeThread = new AtomicReference<>();
            terminal.diagnose()
                    .onSuccess(result -> {
                        delivered.set(result);
                        successThread.set(Thread.currentThread().getName());
                    })
                    .onComplete(() -> {
                        completeThread.set(Thread.currentThread().getName());
                        completed.countDown();
                    })
                    .execute();

            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertNotNull(delivered.get());
            assertEquals("terminal-test-callback", successThread.get());
            assertEquals("terminal-test-callback", completeThread.get());
        } finally {
            callback.shutdownNow();
        }
    }

    // ─── Close ───

    @Test
    void closeSendsNothingAndIsIdempotent() {
        Terminal terminal = terminalBuilder().build();

        terminal.close();
        assertDoesNotThrow(terminal::close);

        assertEquals(0, server.getRequestCount());
    }

    @Test
    void asyncExecuteAfterCloseRejectsIntoHandlers() throws Exception {
        Terminal terminal = terminalBuilder().build();
        terminal.close();

        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<SessionError> error = new AtomicReference<>();
        terminal.diagnose()
                .onError(error::set)
                .onComplete(completed::countDown)
                .execute();

        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertNotNull(error.get(), "the rejection must reach onError");
        assertEquals(SessionErrorCode.INVALID_STATE, error.get().getCode());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void syncExecuteAfterCloseFailsWithInvalidState() {
        Terminal terminal = terminalBuilder().build();
        terminal.close();

        SessionException e = assertThrows(SessionException.class,
                () -> terminal.getTotals().get());
        assertEquals(SessionErrorCode.INVALID_STATE, e.getError().getCode());
        assertTrue(e.getError().getMessage().contains("close()"),
                "unexpected message: " + e.getError().getMessage());
        assertEquals(0, server.getRequestCount());
    }

    // ─── GetTotals ───

    @Test
    void getTotalsCarriesSaleIdAndTotalsGroupId() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"GetTotalsResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},"
                        + "\"POIReconciliationID\":\"REC-7\","
                        + "\"TransactionTotals\":[{\"PaymentCurrency\":\"USD\"}]}}}"));

        ReconciliationResult totals = terminalBuilder()
                .storeLocation("store-42")
                .build()
                .getTotals().get();

        assertEquals("REC-7", totals.getPoiReconciliationId());
        assertEquals(1, totals.getTransactionTotals().size());

        SaleToPOIRequest sent = recordedRequest();
        assertEquals("GetTotals", sent.getMessageHeader().getMessageCategory().toValue());
        assertEquals("POS-LANE-3", sent.getGetTotalsRequest().getTotalFilter().getSaleID());
        assertEquals("store-42", sent.getGetTotalsRequest().getTotalFilter().getTotalsGroupID());
    }

    @Test
    void getTotalsWithoutStoreLocationSendsNoTotalsGroupId() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"GetTotalsResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"}}}}"));

        terminalBuilder().build().getTotals().executeSync();

        assertNull(recordedRequest().getGetTotalsRequest().getTotalFilter().getTotalsGroupID());
    }

    // ─── Sound ───

    @Test
    void playSoundSendsStartSoundWithReferenceAndVolume() throws Exception {
        server.enqueue(new MockResponse().setBody(SOUND_OK));

        terminalBuilder().build().playSound("chime-approved", 80).executeSync();

        SaleToPOIRequest sent = recordedRequest();
        assertEquals("Sound", sent.getMessageHeader().getMessageCategory().toValue());
        assertEquals("Device", sent.getMessageHeader().getMessageClass().toValue());
        assertEquals("StartSound", sent.getSoundRequest().getSoundAction().toValue());
        assertEquals("SoundRef", sent.getSoundRequest().getSoundContent().getSoundFormat().toValue());
        assertEquals("chime-approved", sent.getSoundRequest().getSoundContent().getReferenceID());
        assertEquals(80L, sent.getSoundRequest().getSoundVolume());
    }

    @Test
    void stopSoundSendsStopAction() throws Exception {
        server.enqueue(new MockResponse().setBody(SOUND_OK));

        terminalBuilder().build().stopSound().executeSync();

        assertEquals("StopSound", recordedRequest().getSoundRequest().getSoundAction().toValue());
    }

    @Test
    void playSoundValidatesArguments() {
        Terminal terminal = terminalBuilder().build();

        assertThrows(IllegalArgumentException.class, () -> terminal.playSound("x", 101));
        assertThrows(IllegalArgumentException.class, () -> terminal.playSound("x", -1));
        assertThrows(NullPointerException.class, () -> terminal.playSound(null));
        assertEquals(0, server.getRequestCount());
    }

    // ─── Print ───

    @Test
    void printSendsTextContent() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"PrintResponse\":{\"Response\":{\"Result\":\"Success\"}}}}"));

        terminalBuilder().build().print(PrintPayload.text("THANK YOU")).executeSync();

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

    @Test
    void printRequiresAPayload() {
        assertThrows(NullPointerException.class, () -> terminalBuilder().build().print(null));
        assertEquals(0, server.getRequestCount());
    }
}
