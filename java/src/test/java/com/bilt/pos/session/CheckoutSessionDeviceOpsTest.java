package com.bilt.pos.session;

import com.bilt.pos.display.DisplayPayloadHelper;
import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.MessageCategoryType;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutSessionDeviceOpsTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private MockWebServer server;
    private CheckoutSession session;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));
        session = CheckoutSession.builder()
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
        server.takeRequest(5, TimeUnit.SECONDS); // drain the session-start Admin request
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private SaleToPOIRequest recordedRequest() throws Exception {
        RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(recorded);
        return mapper.readValue(recorded.getBody().readUtf8(), NexoTerminalAPI.class)
                .getSaleToPOIRequest();
    }

    // ─── Sound ───

    @Test
    void playSoundSendsStartSoundWithReferenceAndVolume() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"SoundResponse\":{\"Response\":{\"Result\":\"Success\"}}}}"));

        session.playSound("chime-approved", 80).executeSync();

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
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"SoundResponse\":{\"Response\":{\"Result\":\"Success\"}}}}"));

        session.stopSound().executeSync();

        assertEquals("StopSound", recordedRequest().getSoundRequest().getSoundAction().toValue());
    }

    @Test
    void playSoundValidatesVolume() {
        assertThrows(IllegalArgumentException.class, () -> session.playSound("x", 101));
        assertThrows(IllegalArgumentException.class, () -> session.playSound("x", -1));
    }

    // ─── GetTotals ───

    @Test
    void getTotalsFiltersBySaleIdAndMapsTotals() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"GetTotalsResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},"
                        + "\"POIReconciliationID\":\"REC-7\","
                        + "\"TransactionTotals\":[{\"PaymentCurrency\":\"USD\"}]}}}"));

        ReconciliationResult totals = session.getTotals().get();

        assertEquals("REC-7", totals.getPoiReconciliationId());
        assertEquals(1, totals.getTransactionTotals().size());

        SaleToPOIRequest sent = recordedRequest();
        assertEquals("GetTotals", sent.getMessageHeader().getMessageCategory().toValue());
        assertEquals("POS-LANE-3", sent.getGetTotalsRequest().getTotalFilter().getSaleID());
    }

    // ─── Transaction status options ───

    @Test
    void transactionStatusOptionsControlReprintAndCategory() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"TransactionStatusResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"NotFound\"}}}}"));

        session.getTransactionStatus("SVC0000001", TransactionStatusOptions.builder()
                .originalCategory(MessageCategoryType.LOYALTY)
                .receiptReprint(true)
                .build()).executeSync();

        SaleToPOIRequest sent = recordedRequest();
        assertEquals("Loyalty", sent.getTransactionStatusRequest()
                .getMessageReference().getMessageCategory().toValue());
        assertEquals(Boolean.TRUE, sent.getTransactionStatusRequest().getReceiptReprintFlag());
        assertEquals("CustomerReceipt", sent.getTransactionStatusRequest()
                .getDocumentQualifier()[0].toValue());
        assertEquals("CashierReceipt", sent.getTransactionStatusRequest()
                .getDocumentQualifier()[1].toValue());
    }

    @Test
    void defaultTransactionStatusSendsNoReprintFields() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"TransactionStatusResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"NotFound\"}}}}"));

        session.getTransactionStatus("SVC0000001").executeSync();

        SaleToPOIRequest sent = recordedRequest();
        assertNull(sent.getTransactionStatusRequest().getReceiptReprintFlag());
        assertNull(sent.getTransactionStatusRequest().getDocumentQualifier());
        assertEquals("Payment", sent.getTransactionStatusRequest()
                .getMessageReference().getMessageCategory().toValue());
    }

    // ─── InputUpdate ───

    @Test
    void updateInputDisplayReferencesTheInFlightInput() throws Exception {
        // input response held back long enough for the update to go out first
        server.enqueue(new MockResponse()
                .setBodyDelay(1500, TimeUnit.MILLISECONDS)
                .setBody("{\"SaleToPOIResponse\":{\"InputResponse\":{\"InputResult\":{"
                        + "\"Response\":{\"Result\":\"Success\"},"
                        + "\"Input\":{\"InputCommand\":\"DigitString\",\"DigitInput\":\"42\"}}}}}"));
        server.enqueue(new MockResponse().setBody("{\"SaleToPOIResponse\":{}}"));

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> inputValue = new AtomicReference<>();
        Thread register = new Thread(() -> {
            inputValue.set(session.requestDigitString("Lane number?").getOrNull());
            done.countDown();
        });
        register.start();

        // wait until the input request is on the wire, then update its display
        RecordedRequest inputRecorded = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(inputRecorded);
        SaleToPOIRequest inputRequest = mapper.readValue(
                inputRecorded.getBody().readUtf8(), NexoTerminalAPI.class).getSaleToPOIRequest();

        session.updateInputDisplay(DisplayPayloadHelper.standby("updated")).executeSync();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals("42", inputValue.get());

        SaleToPOIRequest update = recordedRequest();
        assertEquals("InputUpdate", update.getMessageHeader().getMessageCategory().toValue());
        assertEquals("Input", update.getInputUpdate().getMessageReference()
                .getMessageCategory().toValue());
        assertEquals(inputRequest.getMessageHeader().getServiceID(),
                update.getInputUpdate().getMessageReference().getServiceID());
        assertNotNull(update.getInputUpdate().getOutputContent().getOutputXHTML());
    }

    @Test
    void updateInputDisplayWithoutInFlightInputFails() {
        SessionException e = assertThrows(SessionException.class,
                () -> session.updateInputDisplay(DisplayPayloadHelper.standby("x")).get());
        assertEquals(SessionErrorCode.INVALID_STATE, e.getError().getCode());
        assertEquals(1, server.getRequestCount(), "only the session start may hit the wire");
    }
}
