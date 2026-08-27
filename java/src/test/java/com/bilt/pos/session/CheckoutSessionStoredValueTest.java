package com.bilt.pos.session;

import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.StoredValueData;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.storedvalue.StoredValueBalance;
import com.bilt.pos.session.storedvalue.StoredValueCard;
import com.bilt.pos.session.storedvalue.StoredValueOperationResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutSessionStoredValueTest {

    private static String storedValueOk(String type, double amount, double balance) {
        return "{\"SaleToPOIResponse\":{\"StoredValueResponse\":{"
                + "\"Response\":{\"Result\":\"Success\"},"
                + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-SV-1\","
                + "\"TimeStamp\":\"2026-07-27T10:00:00Z\"}},"
                + "\"StoredValueResult\":[{"
                + "\"StoredValueTransactionType\":\"" + type + "\","
                + "\"ItemAmount\":" + amount + ",\"Currency\":\"USD\","
                + "\"HostTransactionID\":{\"TransactionID\":\"HOST-77\"},"
                + "\"StoredValueAccountStatus\":{\"CurrentBalance\":" + balance + "}}]}}}";
    }

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

    // ─── Lifecycle operations ───

    @Test
    void activateSendsStoredValueRequestWithInitialLoad() throws Exception {
        server.enqueue(new MockResponse().setBody(storedValueOk("Activate", 25.00, 25.00)));

        StoredValueOperationResult result = session.storedValueActivate(
                StoredValueCard.scanned("6006491260550218157").withProvider("givex"),
                new BigDecimal("25.00")).get();

        assertEquals("Activate", result.getTransactionType().toValue());
        assertEquals(0, new BigDecimal("25.00").compareTo(result.getAmount()));
        assertEquals(0, new BigDecimal("25.00").compareTo(result.getCurrentBalance()));
        assertEquals("POI-SV-1", result.getPoiTransactionId());
        assertEquals("HOST-77", result.getHostTransactionId());

        SaleToPOIRequest sent = recordedRequest();
        assertEquals("StoredValue", sent.getMessageHeader().getMessageCategory().toValue());
        StoredValueData data = sent.getStoredValueRequest().getStoredValueData()[0];
        assertEquals("Activate", data.getStoredValueTransactionType().toValue());
        assertEquals(25.00, data.getItemAmount());
        assertEquals("USD", data.getCurrency());
        assertEquals("6006491260550218157", data.getStoredValueAccountID().getStoredValueID());
        assertEquals("BarCode", data.getStoredValueAccountID().getIdentificationType().toValue());
        assertEquals("Scanned", data.getStoredValueAccountID().getEntryMode()[0].toValue());
        assertEquals("givex", data.getStoredValueAccountID().getStoredValueProvider());
        assertEquals("GiftCard", data.getStoredValueAccountID().getStoredValueAccountType().toValue());
        assertNotNull(sent.getStoredValueRequest().getSaleData().getSaleTransactionID());
    }

    @Test
    void loadAndUnloadSendCorrespondingTypes() throws Exception {
        server.enqueue(new MockResponse().setBody(storedValueOk("Load", 10.00, 35.00)));
        session.storedValueLoad(StoredValueCard.number("GC-1"), new BigDecimal("10.00")).executeSync();
        assertEquals("Load", recordedRequest().getStoredValueRequest()
                .getStoredValueData()[0].getStoredValueTransactionType().toValue());

        server.enqueue(new MockResponse().setBody(storedValueOk("Unload", 5.00, 30.00)));
        session.storedValueUnload(StoredValueCard.number("GC-1"), new BigDecimal("5.00")).executeSync();
        StoredValueData unload = recordedRequest().getStoredValueRequest().getStoredValueData()[0];
        assertEquals("Unload", unload.getStoredValueTransactionType().toValue());
        assertEquals(5.00, unload.getItemAmount());
    }

    @Test
    void deactivateIsUnloadWithZeroAmount() throws Exception {
        server.enqueue(new MockResponse().setBody(storedValueOk("Unload", 0.00, 0.00)));

        session.storedValueDeactivate(StoredValueCard.number("GC-1")).executeSync();

        StoredValueData data = recordedRequest().getStoredValueRequest().getStoredValueData()[0];
        assertEquals("Unload", data.getStoredValueTransactionType().toValue());
        assertEquals(0.00, data.getItemAmount());
    }

    @Test
    void swipedCardOmitsStoredValueId() throws Exception {
        server.enqueue(new MockResponse().setBody(storedValueOk("Load", 10.00, 10.00)));

        session.storedValueLoad(StoredValueCard.swiped(), new BigDecimal("10.00")).executeSync();

        StoredValueData data = recordedRequest().getStoredValueRequest().getStoredValueData()[0];
        assertNull(data.getStoredValueAccountID().getStoredValueID());
        assertEquals("MagStripe", data.getStoredValueAccountID().getEntryMode()[0].toValue());
    }

    @Test
    void reverseReferencesOriginalTransaction() throws Exception {
        server.enqueue(new MockResponse().setBody(storedValueOk("Reverse", 10.00, 20.00)));

        session.storedValueReverse("POI-SV-1", Instant.parse("2026-07-27T10:00:00Z")).executeSync();

        StoredValueData data = recordedRequest().getStoredValueRequest().getStoredValueData()[0];
        assertEquals("Reverse", data.getStoredValueTransactionType().toValue());
        assertEquals("POI-SV-1", data.getOriginalPOITransaction()
                .getPoiTransactionID().getTransactionID());
        assertNull(data.getStoredValueAccountID());
    }

    @Test
    void amountValidation() {
        StoredValueCard card = StoredValueCard.number("GC-1");
        assertThrows(IllegalArgumentException.class,
                () -> session.storedValueLoad(card, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> session.storedValueUnload(card, new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,
                () -> session.storedValueActivate(card, new BigDecimal("-1")));
        assertDoesNotThrow(() -> session.storedValueActivate(card, BigDecimal.ZERO));
    }

    @Test
    void declinedOperationGoesToErrorChannel() {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"StoredValueResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Refusal\"}}}}"));

        SessionException e = assertThrows(SessionException.class,
                () -> session.storedValueLoad(StoredValueCard.number("GC-1"),
                        new BigDecimal("10.00")).get());
        assertEquals(SessionErrorCode.DECLINED, e.getError().getCode());
    }

    // ─── Balance inquiry ───

    @Test
    void balanceUsesPaymentAccountReq() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"BalanceInquiryResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},"
                        + "\"PaymentAccountStatus\":{\"CurrentBalance\":65.00,\"Currency\":\"USD\"}}}}"));

        StoredValueBalance balance = session.storedValueBalance(
                StoredValueCard.number("6006491260550218157")).get();

        assertEquals(0, new BigDecimal("65.00").compareTo(balance.getBalance()));
        assertEquals("USD", balance.getCurrency());

        SaleToPOIRequest sent = recordedRequest();
        assertEquals("BalanceInquiry", sent.getMessageHeader().getMessageCategory().toValue());
        assertEquals("StoredValue", sent.getBalanceInquiryRequest().getPaymentAccountReq()
                .getPaymentInstrumentData().getPaymentInstrumentType().toValue());
        assertEquals("6006491260550218157", sent.getBalanceInquiryRequest().getPaymentAccountReq()
                .getPaymentInstrumentData().getStoredValueAccountID().getStoredValueID());
    }

    // ─── Split tender carries the full card description ───

    @Test
    void payStoredValueStepCarriesProviderAndEntryMode() throws Exception {
        session.basket().addItem(BasketItem.of("SKU-1", "Item", 1, "50.00"));
        session.setStoredValueCard(StoredValueCard.scanned("GC-9").withProvider("svs"));

        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},"
                        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-GC-1\"}},"
                        + "\"PaymentResult\":{\"AmountsResp\":{\"AuthorizedAmount\":50.00}}}}}"));

        session.settle().executeSync();

        SaleToPOIRequest sent = recordedRequest();
        assertEquals("svs", sent.getPaymentRequest().getPaymentData().getPaymentInstrumentData()
                .getStoredValueAccountID().getStoredValueProvider());
        assertEquals("Scanned", sent.getPaymentRequest().getPaymentData().getPaymentInstrumentData()
                .getStoredValueAccountID().getEntryMode()[0].toValue());
    }
}
