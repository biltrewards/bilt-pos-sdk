package com.bilt.pos.nexo.client;

import com.bilt.pos.nexo.model.*;
import com.bilt.pos.nexo.security.MessageEncryptor;
import com.bilt.pos.nexo.security.SaleToPOISecuredMessage;
import com.bilt.pos.nexo.security.SecurityKey;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class BiltNexoTerminalClientTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private MockWebServer server;
    private BiltNexoTerminalClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = BiltNexoTerminalClient.builder()
                .endpoint(server.url("/nexo").toString())
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void requestSerializesAndSendsPayment() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"SaleToPOIResponse\":{\"MessageHeader\":{\"ProtocolVersion\":\"3.0\"},"
                        + "\"PaymentResponse\":{\"Response\":{\"Result\":\"Success\"}}}}"));

        NexoTerminalAPI request = NexoTerminalAPI.builder()
                .saleToPOIRequest(SaleToPOIRequest.builder()
                        .messageHeader(MessageHeader.builder()
                                .protocolVersion("3.0")
                                .messageClass(MessageClassType.SERVICE)
                                .messageCategory(MessageCategoryType.PAYMENT)
                                .messageType(MessageTypeType.REQUEST)
                                .serviceID("txn-001")
                                .saleID("POS-1")
                                .poiid("TERM-1")
                                .build())
                        .paymentRequest(PaymentRequest.builder()
                                .paymentTransaction(PaymentTransaction.builder()
                                        .amountsReq(AmountsReq.builder()
                                                .currency("USD")
                                                .requestedAmount(25.00)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build();

        NexoTerminalAPI response = client.request(request);

        assertEquals(ResultType.SUCCESS, response.getSaleToPOIResponse().getPaymentResponse().getResponse().getResult());

        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/nexo", recorded.getPath());
        assertTrue(recorded.getHeader("Content-Type").startsWith("application/json"));

        String sentJson = recorded.getBody().readUtf8();
        assertTrue(sentJson.contains("\"SaleToPOIRequest\""));
        assertTrue(sentJson.contains("\"RequestedAmount\":25.0"));
        assertTrue(sentJson.contains("\"Currency\":\"USD\""));
        assertFalse(sentJson.contains("null"));
    }

    @Test
    void requestDeserializesFullPaymentResponse() throws Exception {
        String responseJson = "{\"SaleToPOIResponse\":{"
                + "\"MessageHeader\":{"
                + "  \"ProtocolVersion\":\"3.0\","
                + "  \"MessageClass\":\"Service\","
                + "  \"MessageCategory\":\"Payment\","
                + "  \"MessageType\":\"Response\","
                + "  \"ServiceID\":\"txn-001\","
                + "  \"SaleID\":\"POS-1\","
                + "  \"POIID\":\"TERM-1\""
                + "},"
                + "\"PaymentResponse\":{"
                + "  \"Response\":{\"Result\":\"Success\"},"
                + "  \"PaymentResult\":{"
                + "    \"PaymentType\":\"Normal\","
                + "    \"AmountsResp\":{\"Currency\":\"USD\",\"AuthorizedAmount\":25.0}"
                + "  }"
                + "}}}";

        server.enqueue(new MockResponse().setBody(responseJson));

        NexoTerminalAPI response = client.request(
                NexoTerminalAPI.builder()
                        .saleToPOIRequest(SaleToPOIRequest.builder()
                                .messageHeader(MessageHeader.builder().build())
                                .build())
                        .build());

        SaleToPOIResponse poiResponse = response.getSaleToPOIResponse();
        assertEquals("3.0", poiResponse.getMessageHeader().getProtocolVersion());
        assertEquals(ResultType.SUCCESS, poiResponse.getPaymentResponse().getResponse().getResult());
        assertEquals(25.0, poiResponse.getPaymentResponse().getPaymentResult()
                .getAmountsResp().getAuthorizedAmount());
    }

    @Test
    void requestHandlesFailureResponse() throws Exception {
        String responseJson = "{\"SaleToPOIResponse\":{"
                + "\"MessageHeader\":{\"ProtocolVersion\":\"3.0\"},"
                + "\"PaymentResponse\":{"
                + "  \"Response\":{"
                + "    \"Result\":\"Failure\","
                + "    \"ErrorCondition\":\"Refusal\","
                + "    \"AdditionalResponse\":\"Insufficient funds\""
                + "  }"
                + "}}}";

        server.enqueue(new MockResponse().setBody(responseJson));

        NexoTerminalAPI response = client.request(
                NexoTerminalAPI.builder()
                        .saleToPOIRequest(SaleToPOIRequest.builder()
                                .messageHeader(MessageHeader.builder().build())
                                .build())
                        .build());

        SaleToPOIResponse poiResponse = response.getSaleToPOIResponse();
        assertEquals(ResultType.FAILURE, poiResponse.getPaymentResponse().getResponse().getResult());
        assertEquals("Insufficient funds",
                poiResponse.getPaymentResponse().getResponse().getAdditionalResponse());
    }

    @Test
    void requestThrowsOnHttpError() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal error"));

        BiltNexoClientException ex = assertThrows(BiltNexoClientException.class, () ->
                client.request(NexoTerminalAPI.builder()
                        .saleToPOIRequest(SaleToPOIRequest.builder()
                                .messageHeader(MessageHeader.builder().build())
                                .build())
                        .build()));

        assertTrue(ex.getMessage().contains("500"));
        assertTrue(ex.getMessage().contains("Internal error"));
    }

    @Test
    void requestThrowsOnConnectionFailure() throws Exception {
        server.shutdown();

        BiltNexoClientException ex = assertThrows(BiltNexoClientException.class, () ->
                client.request(NexoTerminalAPI.builder()
                        .saleToPOIRequest(SaleToPOIRequest.builder()
                                .messageHeader(MessageHeader.builder().build())
                                .build())
                        .build()));

        assertTrue(ex.getMessage().contains("Failed to communicate"));
    }

    @Test
    void requestWithPerRequestTimeout() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"SaleToPOIResponse\":{\"MessageHeader\":{\"ProtocolVersion\":\"3.0\"}}}"));

        NexoTerminalAPI response = client.request(
                NexoTerminalAPI.builder()
                        .saleToPOIRequest(SaleToPOIRequest.builder()
                                .messageHeader(MessageHeader.builder().build())
                                .build())
                        .build(),
                Duration.ofSeconds(5));

        assertNotNull(response);
    }

    @Test
    void builderRequiresEndpoint() {
        assertThrows(IllegalStateException.class, () ->
                BiltNexoTerminalClient.builder().build());
    }

    @Test
    void createConvenienceFactory() {
        BiltNexoTerminalClient c = BiltNexoTerminalClient.create("192.168.1.100");
        assertNotNull(c);
    }

    @Test
    void isEncryptedReturnsFalseByDefault() {
        assertFalse(client.isEncrypted());
    }

    @Test
    void encryptedClientRoundTrip() throws Exception {
        SecurityKey key = SecurityKey.builder()
                .passphrase("testPassphrase")
                .keyIdentifier("testTerminal")
                .keyVersion(0)
                .build();

        BiltNexoTerminalClient encryptedClient = BiltNexoTerminalClient.builder()
                .endpoint(server.url("/nexo").toString())
                .securityKey(key)
                .build();
        assertTrue(encryptedClient.isEncrypted());

        // Simulate a terminal that decrypts the request and encrypts the response
        // using the same key. We use MessageEncryptor directly to build the mock response.
        MessageEncryptor encryptor = new MessageEncryptor(key);
        String plainResponse = "{\"MessageHeader\":{\"ProtocolVersion\":\"3.0\"},"
                + "\"PaymentResponse\":{\"Response\":{\"Result\":\"Success\"}}}";
        MessageHeader respHeader = MessageHeader.builder()
                .protocolVersion("3.0").build();
        SaleToPOISecuredMessage securedResp = encryptor.encrypt(plainResponse, respHeader);

        // Wrap in the wire envelope: {"SaleToPOIResponse": <secured>}
        String encryptedResponseJson = mapper.writeValueAsString(
                new TestSecuredResponseEnvelope(securedResp));

        server.enqueue(new MockResponse().setBody(encryptedResponseJson));

        NexoTerminalAPI request = NexoTerminalAPI.builder()
                .saleToPOIRequest(SaleToPOIRequest.builder()
                        .messageHeader(MessageHeader.builder()
                                .protocolVersion("3.0")
                                .messageClass(MessageClassType.SERVICE)
                                .messageCategory(MessageCategoryType.PAYMENT)
                                .messageType(MessageTypeType.REQUEST)
                                .serviceID("txn-001")
                                .saleID("POS-1")
                                .poiid("TERM-1")
                                .build())
                        .paymentRequest(PaymentRequest.builder()
                                .paymentTransaction(PaymentTransaction.builder()
                                        .amountsReq(AmountsReq.builder()
                                                .currency("USD")
                                                .requestedAmount(42.00)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build();

        NexoTerminalAPI response = encryptedClient.request(request);
        assertEquals(ResultType.SUCCESS, response.getSaleToPOIResponse().getPaymentResponse().getResponse().getResult());

        // Verify the request was sent encrypted (contains EnvelopedData, not plaintext)
        RecordedRequest recorded = server.takeRequest();
        String sentJson = recorded.getBody().readUtf8();
        assertTrue(sentJson.contains("EnvelopedData"));
        assertFalse(sentJson.contains("RequestedAmount"));
    }

    @Test
    void encryptedClientHandlesUnencryptedErrorResponse() throws Exception {
        SecurityKey key = SecurityKey.builder()
                .passphrase("testPassphrase")
                .keyIdentifier("testTerminal")
                .keyVersion(0)
                .build();

        BiltNexoTerminalClient encryptedClient = BiltNexoTerminalClient.builder()
                .endpoint(server.url("/nexo").toString())
                .securityKey(key)
                .build();

        // Server sends back plaintext error (no encryption key configured on terminal)
        String errorJson = "{\"SaleToPOIResponse\":{"
                + "\"MessageHeader\":{\"ProtocolVersion\":\"3.0\"},"
                + "\"PaymentResponse\":{\"Response\":{"
                + "\"Result\":\"Failure\","
                + "\"ErrorCondition\":\"Refusal\","
                + "\"AdditionalResponse\":\"Encryption not configured\"}}}}";
        server.enqueue(new MockResponse().setBody(errorJson));

        NexoTerminalAPI request = NexoTerminalAPI.builder()
                .saleToPOIRequest(SaleToPOIRequest.builder()
                        .messageHeader(MessageHeader.builder()
                                .protocolVersion("3.0")
                                .messageClass(MessageClassType.SERVICE)
                                .messageCategory(MessageCategoryType.PAYMENT)
                                .messageType(MessageTypeType.REQUEST)
                                .serviceID("txn-001")
                                .saleID("POS-1")
                                .poiid("TERM-1")
                                .build())
                        .paymentRequest(PaymentRequest.builder().build())
                        .build())
                .build();

        NexoTerminalAPI response = encryptedClient.request(request);
        assertEquals(ResultType.FAILURE,
                response.getSaleToPOIResponse().getPaymentResponse().getResponse().getResult());
        assertEquals("Encryption not configured",
                response.getSaleToPOIResponse().getPaymentResponse().getResponse().getAdditionalResponse());
    }

    /** Test helper to serialize the secured response envelope */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class TestSecuredResponseEnvelope {
        @JsonProperty("SaleToPOIResponse")
        final SaleToPOISecuredMessage saleToPOIResponse;
        TestSecuredResponseEnvelope(SaleToPOISecuredMessage msg) { this.saleToPOIResponse = msg; }
    }

    @Test
    void requestThrowsWhenSaleToPOIRequestIsNull() {
        BiltNexoClientException ex = assertThrows(BiltNexoClientException.class, () ->
                client.request(NexoTerminalAPI.builder().build()));

        assertTrue(ex.getMessage().contains("saleToPOIRequest"));
    }

    @Test
    void roundTripSerialization() throws Exception {
        SaleToPOIRequest original = SaleToPOIRequest.builder()
                .messageHeader(MessageHeader.builder()
                        .protocolVersion("3.0")
                        .messageClass(MessageClassType.SERVICE)
                        .messageCategory(MessageCategoryType.DIAGNOSIS)
                        .messageType(MessageTypeType.REQUEST)
                        .serviceID("diag-001")
                        .saleID("POS-1")
                        .poiid("TERM-1")
                        .build())
                .diagnosisRequest(DiagnosisRequest.builder().build())
                .build();

        String json = mapper.writeValueAsString(original);
        SaleToPOIRequest deserialized = mapper.readValue(json, SaleToPOIRequest.class);

        assertEquals(original.getMessageHeader().getProtocolVersion(),
                deserialized.getMessageHeader().getProtocolVersion());
        assertEquals(original.getMessageHeader().getMessageCategory(),
                deserialized.getMessageHeader().getMessageCategory());
        assertEquals(original.getMessageHeader().getServiceID(),
                deserialized.getMessageHeader().getServiceID());
    }
}
