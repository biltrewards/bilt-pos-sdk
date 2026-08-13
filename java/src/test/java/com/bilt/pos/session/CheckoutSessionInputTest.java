package com.bilt.pos.session;

import com.bilt.pos.display.DisplayPayloadHelper;
import com.bilt.pos.display.InputPayload;
import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.session.input.ConfirmationOptions;
import com.bilt.pos.session.input.InputOptions;
import com.bilt.pos.session.input.MenuSelection;
import com.bilt.pos.session.input.PinOptions;
import com.bilt.pos.session.input.PinResult;
import com.bilt.pos.session.input.Signature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutSessionInputTest {

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

    private static String inputResponse(String inputFields) {
        return "{\"SaleToPOIResponse\":{\"InputResponse\":{\"InputResult\":{"
                + "\"Device\":\"CustomerInput\",\"InfoQualify\":\"Input\","
                + "\"Response\":{\"Result\":\"Success\"},"
                + "\"Input\":{" + inputFields + "}}}}}";
    }

    private SaleToPOIRequest recordedRequest() throws Exception {
        RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(recorded);
        return mapper.readValue(recorded.getBody().readUtf8(), NexoTerminalAPI.class)
                .getSaleToPOIRequest();
    }

    // ─── Native inputs ───

    @Test
    void digitStringSendsPromptAndParsesResult() throws Exception {
        server.enqueue(new MockResponse().setBody(
                inputResponse("\"InputCommand\":\"DigitString\",\"DigitInput\":\"10001\"")));

        String zip = session.requestDigitString("Enter your zip code",
                InputOptions.builder().minLength(5).maxLength(5).build()).get();

        assertEquals("10001", zip);
        SaleToPOIRequest sent = recordedRequest();
        assertEquals("Input", sent.getMessageHeader().getMessageCategory().toValue());
        assertEquals("Device", sent.getMessageHeader().getMessageClass().toValue());
        assertEquals("DigitString", sent.getInputRequest().getInputData().getInputCommand().toValue());
        assertEquals(5L, sent.getInputRequest().getInputData().getMinLength());
        assertEquals(5L, sent.getInputRequest().getInputData().getMaxLength());

        InputPayload payload = DisplayPayloadHelper.inputFromBase64(
                sent.getInputRequest().getDisplayOutput().getOutputContent().getOutputXHTML());
        assertEquals("Enter your zip code", payload.getDisplay().getTitle());
    }

    @Test
    void decimalStringParsesAmount() {
        server.enqueue(new MockResponse().setBody(
                inputResponse("\"InputCommand\":\"DecimalString\",\"DigitInput\":\"4.50\"")));

        BigDecimal tip = session.requestDecimalString("Enter tip amount").get();

        assertEquals(new BigDecimal("4.50"), tip);
    }

    @Test
    void textStringParsesText() {
        server.enqueue(new MockResponse().setBody(
                inputResponse("\"InputCommand\":\"TextString\",\"TextInput\":\"a@b.com\"")));

        assertEquals("a@b.com", session.requestTextString("Enter your email").get());
    }

    @Test
    void inputArrivingAfterAbortIsStillDelivered() throws Exception {
        CountDownLatch promptOnTheWire = new CountDownLatch(1);
        CountDownLatch aborted = new CountDownLatch(1);
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                if (request.getBody().readUtf8().contains("InputRequest")) {
                    promptOnTheWire.countDown();
                    // hold the customer's answer until abort() has fired
                    aborted.await(5, TimeUnit.SECONDS);
                    return new MockResponse().setBody(inputResponse(
                            "\"InputCommand\":\"GetConfirmation\",\"ConfirmedFlag\":true"));
                }
                return new MockResponse();   // the AbortRequest, best-effort
            }
        });

        AtomicReference<Boolean> delivered = new AtomicReference<>();
        AtomicReference<SessionException> failure = new AtomicReference<>();
        Thread register = new Thread(() -> {
            try {
                delivered.set(session.requestConfirmation("Print receipt?").get());
            } catch (SessionException e) {
                failure.set(e);
            }
        });
        register.start();
        assertTrue(promptOnTheWire.await(5, TimeUnit.SECONDS));

        session.abort().executeSync();
        aborted.countDown();
        register.join(5_000);
        assertFalse(register.isAlive());

        // abort is operation-scoped and the customer answered before the
        // terminal processed it: the answer is delivered, the session
        // continues unchanged
        assertNull(failure.get());
        assertEquals(Boolean.TRUE, delivered.get());
        assertEquals(SessionState.IDLE, session.getState(),
                "abort does not end the session; the checkout continues");
    }

    @Test
    void confirmationParsesFlagAndSendsCustomButtons() throws Exception {
        server.enqueue(new MockResponse().setBody(
                inputResponse("\"InputCommand\":\"GetConfirmation\",\"ConfirmedFlag\":true")));

        Boolean confirmed = session.requestConfirmation("Print receipt?",
                ConfirmationOptions.withButtons("Print", "No thanks")).get();

        assertTrue(confirmed);
        SaleToPOIRequest sent = recordedRequest();
        InputPayload payload = DisplayPayloadHelper.inputFromBase64(
                sent.getInputRequest().getDisplayOutput().getOutputContent().getOutputXHTML());
        assertEquals("Print", payload.getConfirmation().getConfirmButton());
        assertEquals("No thanks", payload.getConfirmation().getCancelButton());
    }

    @Test
    void confirmationWithThreeButtonsIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfirmationOptions.withButtons("Email", "Print", "No receipt"));
    }

    @Test
    void menuEntryMapsOneBasedWireSelectionToZeroBasedIndex() throws Exception {
        server.enqueue(new MockResponse().setBody(
                inputResponse("\"InputCommand\":\"GetMenuEntry\",\"MenuEntryNumber\":[2]")));

        MenuSelection selection = session.requestMenuEntry("Select tip",
                List.of("15%", "18%", "20%")).get();

        assertEquals(1, selection.getIndex());
        assertEquals("18%", selection.getValue());

        SaleToPOIRequest sent = recordedRequest();
        assertEquals(3, sent.getInputRequest().getDisplayOutput().getMenuEntry().length);
        assertEquals("18%", sent.getInputRequest().getDisplayOutput()
                .getMenuEntry()[1].getOutputText()[0].getText());
        assertEquals(1L, sent.getInputRequest().getInputData().getMaxLength(),
                "single-select limits the selection to one entry");
    }

    @Test
    void menuEntryMultiSelectReturnsAllEntries() throws Exception {
        server.enqueue(new MockResponse().setBody(
                inputResponse("\"InputCommand\":\"GetMenuEntry\",\"MenuEntryNumber\":[1,3]")));

        MenuSelection selection = session.requestMenuEntry("Pick",
                List.of("A", "B", "C"),
                com.bilt.pos.session.input.MenuOptions.builder().multiSelect(true).build()).get();

        assertEquals(List.of(0, 2), selection.getIndices());
        assertEquals(List.of("A", "C"), selection.getValues());

        SaleToPOIRequest sent = recordedRequest();
        assertEquals(1L, sent.getInputRequest().getInputData().getMinLength());
        assertEquals(3L, sent.getInputRequest().getInputData().getMaxLength(),
                "multi-select allows selecting up to every entry");
    }

    @Test
    void cancelledInputMapsToCancelledError() {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"InputResponse\":{\"InputResult\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Cancel\"}}}}}"));

        SessionException e = assertThrows(SessionException.class,
                () -> session.requestDigitString("Zip?").get());
        assertEquals(SessionErrorCode.CANCELLED, e.getError().getCode());
    }

    // ─── XSD-based inputs ───

    @Test
    void signatureDecodesPngWithDimensions() throws Exception {
        byte[] png = tinyPng(320, 120);
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"InputResponse\":{\"InputResult\":{"
                        + "\"Response\":{\"Result\":\"Success\",\"AdditionalResponse\":\""
                        + Base64.getEncoder().encodeToString(png) + "\"},"
                        + "\"Input\":{\"InputCommand\":\"GetConfirmation\",\"ConfirmedFlag\":true}}}}}"));

        Signature signature = session.requestSignature("Please sign below").get();

        assertArrayEquals(png, signature.getImageData());
        assertEquals("PNG", signature.getFormat());
        assertEquals(320, signature.getWidth());
        assertEquals(120, signature.getHeight());
        assertNotNull(signature.getCapturedAt());

        SaleToPOIRequest sent = recordedRequest();
        InputPayload payload = DisplayPayloadHelper.inputFromBase64(
                sent.getInputRequest().getDisplayOutput().getOutputContent().getOutputXHTML());
        assertNotNull(payload.getSignature());
        assertEquals("Please sign below", payload.getDisplay().getTitle());
    }

    @Test
    void declinedSignatureMapsToCancelled() {
        server.enqueue(new MockResponse().setBody(
                inputResponse("\"InputCommand\":\"GetConfirmation\",\"ConfirmedFlag\":false")));

        SessionException e = assertThrows(SessionException.class,
                () -> session.requestSignature("Sign").get());
        assertEquals(SessionErrorCode.CANCELLED, e.getError().getCode());
    }

    @Test
    void amountConfirmationShowsAmountLine() throws Exception {
        server.enqueue(new MockResponse().setBody(
                inputResponse("\"InputCommand\":\"GetConfirmation\",\"ConfirmedFlag\":true")));

        assertTrue(session.requestAmountConfirmation(new BigDecimal("97.94"), "Confirm total").get());

        SaleToPOIRequest sent = recordedRequest();
        InputPayload payload = DisplayPayloadHelper.inputFromBase64(
                sent.getInputRequest().getDisplayOutput().getOutputContent().getOutputXHTML());
        assertEquals("Confirm total", payload.getDisplay().getTitle());
        assertEquals("USD 97.94", payload.getDisplay().getText().get(0));
        assertNotNull(payload.getConfirmation());
    }

    // ─── PIN ───

    @Test
    void pinEntrySendsPinRequestOnTerminal() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"PINResponse\":{\"Response\":{\"Result\":\"Success\"},"
                        + "\"CardholderPIN\":{\"PINFormat\":\"ISO0\"}}}}"));

        PinResult result = session.requestPinEntry(PinOptions.builder()
                .timeout(java.time.Duration.ofSeconds(30))
                .build()).get();

        assertEquals(com.bilt.pos.session.input.PinMode.PIN_ENTER, result.getMode());
        assertNotNull(result.getCardholderPin());

        SaleToPOIRequest sent = recordedRequest();
        assertEquals("PIN", sent.getMessageHeader().getMessageCategory().toValue());
        assertEquals("PINEnter", sent.getPinRequest().getPinRequestType().toValue());
        assertEquals(30L, sent.getPinRequest().getMaxWaitingTime());
    }

    @Test
    void pinVerifyReportsVerification() {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"PINResponse\":{\"Response\":{\"Result\":\"Success\"}}}}"));

        PinResult result = session.requestPinVerify(PinOptions.defaults()).get();

        assertTrue(result.isVerified());
    }

    @Test
    void wrongPinMapsToDeclined() {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"PINResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"WrongPIN\"}}}}"));

        SessionException e = assertThrows(SessionException.class,
                () -> session.requestPinVerifyOnly(PinOptions.defaults()).get());
        assertEquals(SessionErrorCode.DECLINED, e.getError().getCode());
    }

    // ─── External display routing ───

    @Test
    void inputGoesToExternalDisplayButPinStaysOnTerminal() throws Exception {
        try (MockWebServer displayServer = new MockWebServer()) {
            displayServer.start();
            displayServer.enqueue(new MockResponse().setBody(
                    inputResponse("\"InputCommand\":\"DigitString\",\"DigitInput\":\"42\"")));
            server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));
            server.enqueue(new MockResponse().setBody(
                    "{\"SaleToPOIResponse\":{\"PINResponse\":{\"Response\":{\"Result\":\"Success\"}}}}"));

            CheckoutSession dual = CheckoutSession.builder()
                    .client(BiltNexoTerminalClient.builder()
                            .endpoint(server.url("/nexo").toString())
                            .disableRecoveryOnNetworkError()
                            .build())
                    .externalDisplayClient(BiltNexoTerminalClient.builder()
                            .endpoint(displayServer.url("/nexo").toString())
                            .disableRecoveryOnNetworkError()
                            .build())
                    .saleId("POS-LANE-3")
                    .poiId("VictaLane-275839164")
                    .currency("USD")
                    .start()
                    .get();

            assertEquals("42", dual.requestDigitString("Lane number?").get());
            assertNotNull(dual.requestPinEntry(PinOptions.defaults()).getOrNull());

            assertEquals(1, displayServer.getRequestCount(), "input must hit external display");
            assertEquals(3, server.getRequestCount(),
                    "both session starts and the PIN must stay on the terminal");
        }
    }

    private static byte[] tinyPng(int width, int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {(byte) 137, 'P', 'N', 'G', 13, 10, 26, 10});  // signature
        out.write(new byte[] {0, 0, 0, 13});                                 // IHDR length
        out.write(new byte[] {'I', 'H', 'D', 'R'});
        out.write(intBytes(width));
        out.write(intBytes(height));
        out.write(new byte[] {8, 6, 0, 0, 0});                               // bit depth etc.
        return out.toByteArray();
    }

    private static byte[] intBytes(int value) {
        return new byte[] {
                (byte) (value >>> 24), (byte) (value >>> 16),
                (byte) (value >>> 8), (byte) value};
    }
}
