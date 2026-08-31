package com.bilt.pos.session;

import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.StoredValueData;
import com.bilt.pos.session.basket.BasketDiscount;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.settlement.OriginalSaleRecord;
import com.bilt.pos.session.settlement.SettlementMovement;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.bilt.pos.session.settlement.SettlementResult;
import com.bilt.pos.session.settlement.SettlementStep;
import com.bilt.pos.session.settlement.SettlementTarget;
import com.bilt.pos.session.settlement.StoredValueLoad;
import com.bilt.pos.session.storedvalue.StoredValueCard;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutSessionStoredValuePurchaseTest {

    private static final String STORED_VALUE_FAILED =
            "{\"SaleToPOIResponse\":{\"StoredValueResponse\":{"
                    + "\"Response\":{\"Result\":\"Failure\","
                    + "\"ErrorCondition\":\"Refusal\"}}}}";

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
        server.takeRequest(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void settlementChargesThenActivatesTheReferencedStoredValueLine() throws Exception {
        session.basket().addItem(BasketItem.storedValueLoad(
                "gift-card-1", "GIFT-CARD", "Gift card", new BigDecimal("25.00")));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 25.00)));
        server.enqueue(new MockResponse().setBody(
                storedValueOk("Activate", "POI-LOAD-1", 25.00, 25.00)));

        List<SettlementMovement> loadedCallbacks = new ArrayList<>();
        SettlementResult result = session.settle(SettlementOptions.builder()
                        .addFulfillment(StoredValueLoad.activate("gift-card-1",
                                StoredValueCard.scanned("GC-1")))
                        .build())
                .onStoredValueLoaded(loadedCallbacks::add)
                .get();

        assertTrue(result.isSuccess());
        assertEquals(new BigDecimal("25.00"), result.getCardAmountCharged());
        assertEquals(new BigDecimal("25.00"), result.getStoredValueLoadedAmount());
        assertEquals(1, result.getStoredValueLoads().size());
        assertEquals("gift-card-1", result.getStoredValueLoads().get(0).getBasketReference());
        assertEquals("POI-LOAD-1", result.getStoredValueLoads().get(0).getPoiTransactionId());
        assertEquals(1, loadedCallbacks.size());
        assertEquals(SettlementTarget.basketLine("gift-card-1"),
                loadedCallbacks.get(0).getTarget());

        SaleToPOIRequest charge = recordedRequest();
        assertEquals(25.00, charge.getPaymentRequest().getPaymentTransaction()
                .getAmountsReq().getRequestedAmount());
        SaleToPOIRequest activation = recordedRequest();
        StoredValueData load = activation.getStoredValueRequest().getStoredValueData()[0];
        assertEquals("Activate", load.getStoredValueTransactionType().toValue());
        assertEquals(25.00, load.getItemAmount());
        assertEquals("GC-1", load.getStoredValueAccountID().getStoredValueID());

        OriginalSaleRecord original = OriginalSaleRecord.from(result, null);
        assertEquals(result.getStoredValueLoads(), original.getStoredValueLoads());
    }

    @Test
    void fullyDiscountedStoredValueLineLoadsItsFaceValueWithoutACharge() throws Exception {
        session.basket().addItem(BasketItem.storedValueLoad(
                        "gift-card-1", "GIFT-CARD", "Promotional gift card",
                        new BigDecimal("25.00"))
                .withDiscount(BasketDiscount.offer("OFFER-1", "Complimentary card",
                        new BigDecimal("25.00"))));
        server.enqueue(new MockResponse().setBody(
                storedValueOk("Activate", "POI-LOAD-1", 25.00, 25.00)));

        SettlementResult result = session.settle(SettlementOptions.builder()
                .addFulfillment(StoredValueLoad.activate(
                        "gift-card-1", StoredValueCard.number("GC-1")))
                .build()).get();

        assertEquals(0, BigDecimal.ZERO.compareTo(result.getCardAmountCharged()));
        assertEquals(new BigDecimal("25.00"), result.getStoredValueLoadedAmount());
        assertEquals(new BigDecimal("0.00"), result.getFinalBasket().getGrandTotal());
        SaleToPOIRequest onlyRequest = recordedRequest();
        assertNull(onlyRequest.getPaymentRequest());
        assertEquals("Activate", onlyRequest.getStoredValueRequest()
                .getStoredValueData()[0].getStoredValueTransactionType().toValue());
        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS));

        server.enqueue(new MockResponse().setBody(
                storedValueOk("Reverse", "POI-REVERSE-LOAD-1", 25.00, 0.00)));
        VoidResult voided = session.voidTransaction().get();

        assertTrue(voided.isSuccess());
        assertEquals(0, new BigDecimal("25.00").compareTo(voided.getReversedAmount()));
        assertEquals("POI-REVERSE-LOAD-1", voided.getPoiTransactionId());
        assertEquals("Reverse", recordedRequest().getStoredValueRequest()
                .getStoredValueData()[0].getStoredValueTransactionType().toValue());
    }

    @Test
    void registerCreditCanFundAStoredValueLoadWithoutARefundAllocation() throws Exception {
        session.basket().addItem(BasketItem.storedValueLoad(
                "gift-card-1", "GIFT-CARD", "Customer service gift card",
                new BigDecimal("50.00")));
        session.basket().addItem(BasketItem.credit(
                "GOODWILL", "Customer service credit", 1, new BigDecimal("50.00")));
        server.enqueue(new MockResponse().setBody(
                storedValueOk("Activate", "POI-LOAD-1", 50.00, 50.00)));

        SettlementResult result = session.settle(SettlementOptions.builder()
                .addFulfillment(StoredValueLoad.activate(
                        "gift-card-1", StoredValueCard.number("GC-1")))
                .build()).get();

        assertEquals(0, BigDecimal.ZERO.compareTo(result.getCardAmountCharged()));
        assertEquals(new BigDecimal("50.00"), result.getStoredValueLoadedAmount());
        assertEquals(new BigDecimal("0.00"), result.getFinalBasket().getGrandTotal());
        assertTrue(result.getFinalBasket().getItem("2").isCredit());
        SaleToPOIRequest onlyRequest = recordedRequest();
        assertNull(onlyRequest.getPaymentRequest());
        assertEquals("Activate", onlyRequest.getStoredValueRequest()
                .getStoredValueData()[0].getStoredValueTransactionType().toValue());
        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void everyStoredValueLoadLineRequiresExactlyOneMatchingFulfillment() {
        session.basket().addItem(BasketItem.storedValueLoad(
                "gift-card-1", "GIFT-CARD", "Gift card", new BigDecimal("25.00")));

        SessionException missing = assertThrows(SessionException.class,
                () -> session.settle().get());
        assertEquals(SessionErrorCode.INVALID_STATE, missing.getError().getCode());

        SessionException orphan = assertThrows(SessionException.class,
                () -> session.settle(SettlementOptions.builder()
                        .addFulfillment(StoredValueLoad.activate(
                                "missing", StoredValueCard.number("GC-1")))
                        .build()).get());
        assertEquals(SessionErrorCode.INVALID_STATE, orphan.getError().getCode());
        assertEquals(1, server.getRequestCount(),
                "validation must not send anything after the session-start request");
    }

    @Test
    void failedActivationReversesTheFundingCharge() throws Exception {
        session.basket().addItem(BasketItem.storedValueLoad(
                "gift-card-1", "GIFT-CARD", "Gift card", new BigDecimal("25.00")));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 25.00)));
        server.enqueue(new MockResponse().setBody(STORED_VALUE_FAILED));
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.REVERSAL_OK));

        assertThrows(SessionException.class, () -> session.settle(SettlementOptions.builder()
                .addFulfillment(StoredValueLoad.activate(
                        "gift-card-1", StoredValueCard.number("GC-1")))
                .build()).get());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(3, requests.size());
        assertNotNull(requests.get(0).getPaymentRequest());
        assertNotNull(requests.get(1).getStoredValueRequest());
        assertNotNull(requests.get(2).getReversalRequest());
    }

    @Test
    void partialActivationIsReversedWithItsFundingCharge() throws Exception {
        session.basket().addItem(BasketItem.storedValueLoad(
                "gift-card-1", "GIFT-CARD", "Gift card", new BigDecimal("25.00")));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 25.00)));
        server.enqueue(new MockResponse().setBody(
                storedValueOk("Activate", "POI-LOAD-1", 20.00, 20.00)));
        server.enqueue(new MockResponse().setBody(
                storedValueOk("Reverse", "POI-REVERSE-LOAD-1", 20.00, 0.00)));
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.REVERSAL_OK));

        SessionException failure = assertThrows(SessionException.class,
                () -> session.settle(SettlementOptions.builder()
                        .addFulfillment(StoredValueLoad.activate(
                                "gift-card-1", StoredValueCard.number("GC-1")))
                        .build()).get());

        assertEquals(SessionErrorCode.DECLINED, failure.getError().getCode());
        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(4, requests.size());
        assertEquals("Activate", requests.get(1).getStoredValueRequest()
                .getStoredValueData()[0].getStoredValueTransactionType().toValue());
        assertEquals("Reverse", requests.get(2).getStoredValueRequest()
                .getStoredValueData()[0].getStoredValueTransactionType().toValue());
        assertNotNull(requests.get(3).getReversalRequest());
    }

    @Test
    void wholeSettlementVoidReversesTheLoadBeforeItsFundingCharge() throws Exception {
        session.basket().addItem(BasketItem.storedValueLoad(
                "gift-card-1", "GIFT-CARD", "Gift card", new BigDecimal("25.00")));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 25.00)));
        server.enqueue(new MockResponse().setBody(
                storedValueOk("Activate", "POI-LOAD-1", 25.00, 25.00)));
        session.settle(SettlementOptions.builder()
                .addFulfillment(StoredValueLoad.activate(
                        "gift-card-1", StoredValueCard.number("GC-1")))
                .build()).get();
        drainRequests();

        server.enqueue(new MockResponse().setBody(
                storedValueOk("Reverse", "POI-REVERSE-LOAD-1", 25.00, 0.00)));
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.REVERSAL_OK));

        assertTrue(session.voidTransaction().get().isSuccess());

        List<SaleToPOIRequest> voidRequests = drainRequests();
        assertEquals(2, voidRequests.size());
        StoredValueData reverse = voidRequests.get(0).getStoredValueRequest()
                .getStoredValueData()[0];
        assertEquals("Reverse", reverse.getStoredValueTransactionType().toValue());
        assertEquals("POI-LOAD-1", reverse.getOriginalPOITransaction()
                .getPoiTransactionID().getTransactionID());
        assertNotNull(voidRequests.get(1).getReversalRequest());
    }

    @Test
    void multipleStoredValueLinesKeepIndependentFulfillmentAndVoidReferences() throws Exception {
        session.basket().addItem(BasketItem.storedValueLoad(
                "gift-card-1", "GIFT-CARD", "Gift card", new BigDecimal("10.00")));
        session.basket().addItem(BasketItem.storedValueLoad(
                "gift-card-2", "GIFT-CARD", "Gift card", new BigDecimal("15.00")));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 25.00)));
        server.enqueue(new MockResponse().setBody(
                storedValueOk("Activate", "POI-LOAD-1", 10.00, 10.00)));
        server.enqueue(new MockResponse().setBody(
                storedValueOk("Load", "POI-LOAD-2", 15.00, 30.00)));

        SettlementResult result = session.settle(SettlementOptions.builder()
                .addFulfillment(StoredValueLoad.activate(
                        "gift-card-1", StoredValueCard.number("GC-1")))
                .addFulfillment(StoredValueLoad.reload(
                        "gift-card-2", StoredValueCard.number("GC-2")))
                .build()).get();

        assertEquals(2, result.getStoredValueLoads().size());
        assertEquals("POI-LOAD-1", result.getStoredValueLoads().get(0).getPoiTransactionId());
        assertEquals("POI-LOAD-2", result.getStoredValueLoads().get(1).getPoiTransactionId());
        drainRequests();

        server.enqueue(new MockResponse().setBody(
                storedValueOk("Reverse", "POI-REVERSE-1", 10.00, 0.00)));
        server.enqueue(new MockResponse().setBody(
                storedValueOk("Reverse", "POI-REVERSE-2", 15.00, 15.00)));
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.REVERSAL_OK));
        assertTrue(session.voidTransaction().get().isSuccess());

        List<SaleToPOIRequest> voidRequests = drainRequests();
        assertEquals(3, voidRequests.size());
        assertEquals("POI-LOAD-1", originalStoredValueTransaction(voidRequests.get(0)));
        assertEquals("POI-LOAD-2", originalStoredValueTransaction(voidRequests.get(1)));
        assertNotNull(voidRequests.get(2).getReversalRequest());
    }

    private static String originalStoredValueTransaction(SaleToPOIRequest request) {
        return request.getStoredValueRequest().getStoredValueData()[0]
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID();
    }

    private SaleToPOIRequest recordedRequest() throws Exception {
        RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(recorded, "expected another terminal request");
        return mapper.readValue(recorded.getBody().readUtf8(), NexoTerminalAPI.class)
                .getSaleToPOIRequest();
    }

    private List<SaleToPOIRequest> drainRequests() throws Exception {
        List<SaleToPOIRequest> requests = new ArrayList<>();
        RecordedRequest recorded;
        while ((recorded = server.takeRequest(200, TimeUnit.MILLISECONDS)) != null) {
            requests.add(mapper.readValue(recorded.getBody().readUtf8(), NexoTerminalAPI.class)
                    .getSaleToPOIRequest());
        }
        return requests;
    }

    private static String paymentOk(String poiTxn, double authorized) {
        return "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                + "\"Response\":{\"Result\":\"Success\"},"
                + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"" + poiTxn + "\","
                + "\"TimeStamp\":\"2026-08-31T10:00:00Z\"}},"
                + "\"PaymentResult\":{\"AmountsResp\":{\"Currency\":\"USD\","
                + "\"AuthorizedAmount\":" + authorized + "}}}}}";
    }

    private static String storedValueOk(String type, String poiTxn, double amount,
                                        double balance) {
        return "{\"SaleToPOIResponse\":{\"StoredValueResponse\":{"
                + "\"Response\":{\"Result\":\"Success\"},"
                + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"" + poiTxn + "\","
                + "\"TimeStamp\":\"2026-08-31T10:00:01Z\"}},"
                + "\"StoredValueResult\":[{"
                + "\"StoredValueTransactionType\":\"" + type + "\","
                + "\"ItemAmount\":" + amount + ",\"Currency\":\"USD\","
                + "\"StoredValueAccountStatus\":{\"CurrentBalance\":" + balance + "}}]}}}";
    }
}
