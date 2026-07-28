package com.bilt.pos.session;

import com.bilt.pos.display.DisplayPayload;
import com.bilt.pos.display.DisplayPayloadHelper;
import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.session.basket.Basket;
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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutSessionBasketTest {

    private static final String DISPLAY_OK =
            "{\"SaleToPOIResponse\":{\"DisplayResponse\":{}}}";

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setBody(DISPLAY_OK);
            }
        });
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

    private DisplayPayload nextDisplayPayload() throws Exception {
        RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(recorded, "expected a display request");
        SaleToPOIRequest sent = mapper.readValue(recorded.getBody().readUtf8(),
                NexoTerminalAPI.class).getSaleToPOIRequest();
        assertEquals("Display", sent.getMessageHeader().getMessageCategory().toValue());
        return DisplayPayloadHelper.fromBase64(
                sent.getDisplayRequest().getDisplayOutput()[0].getOutputContent().getOutputXHTML());
    }

    // ─── State transitions ───

    @Test
    void mutateIsAtomicWhenABatchOperationThrows() {
        CheckoutSession session = sessionBuilder().autoDisplay(false).build();
        session.addItem(BasketItem.of("SKU-1", "Item", 2, "10.00"));

        assertThrows(IllegalArgumentException.class, () -> session.mutate(m -> m
                .updateItemQuantityBySku("SKU-1", 5)      // applies...
                .removeItemBySku("NO-SUCH-SKU")));        // ...then throws

        Basket basket = session.getBasket();
        assertEquals(2, basket.getItem("1").getQuantity(),
                "a failed batch must leave the basket untouched");
        assertEquals(SessionState.ACTIVE, session.getState());
    }

    @Test
    void basketDrivesIdleActiveTransitions() {
        CheckoutSession session = sessionBuilder().autoDisplay(false).build();

        assertEquals(SessionState.IDLE, session.getState());
        session.addItem(BasketItem.of("SKU-1", "Item", 1, "10.00"));
        assertEquals(SessionState.ACTIVE, session.getState());
        session.removeItemBySku("SKU-1");
        assertEquals(SessionState.IDLE, session.getState());
    }

    @Test
    void basketOpsAreRejectedAfterAbort() {
        CheckoutSession session = sessionBuilder().autoDisplay(false).build();
        session.abort();

        assertThrows(IllegalStateException.class,
                () -> session.addItem(BasketItem.of("SKU-1", "Item", 1, "10.00")));
    }

    // ─── Totals through the session API ───

    @Test
    void designDocScanningExample() {
        CheckoutSession session = sessionBuilder().autoDisplay(false).build();

        Basket basket = session.addItem(
                BasketItem.of("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 2, "24.99"));
        assertEquals(new BigDecimal("49.98"), basket.getGrandTotal());

        basket = session.addItem(BasketItem.of("KRK-FRAME-5X7-BLK", "5x7 Black Frame", 1, "14.99"));
        assertEquals(new BigDecimal("64.97"), basket.getGrandTotal());

        basket = session.addItem(
                BasketItem.of("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 1, "24.99"));
        assertEquals(new BigDecimal("89.96"), basket.getGrandTotal());

        session.setTaxRateBySku("KRK-CNDL-LRG-VAN", new BigDecimal("0.08875"));
        basket = session.setTaxRateBySku("KRK-FRAME-5X7-BLK", new BigDecimal("0.08875"));
        assertEquals(new BigDecimal("97.94"), basket.getGrandTotal());

        assertEquals(basket.getGrandTotal(), session.getBasket().getGrandTotal());
    }

    // ─── Auto-display ───

    @Test
    void addItemSendsItemisedReceiptDisplay() throws Exception {
        CheckoutSession session = sessionBuilder().build();

        session.addItem(BasketItem.of("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 2, "24.99"));

        DisplayPayload payload = nextDisplayPayload();
        assertEquals("receipt.xslt", payload.getLayout());
        assertNotNull(payload.getReceipt());
        assertEquals(1, payload.getReceipt().getLineItems().getLineItem().size());
        assertEquals("Large Vanilla Candle",
                payload.getReceipt().getLineItems().getLineItem().get(0).getDescription());
        assertEquals(0, new BigDecimal("49.98").compareTo(
                payload.getReceipt().getTotal().getAmount().getValue()));
    }

    @Test
    void autoDisplayOffSendsNothing() {
        CheckoutSession session = sessionBuilder().autoDisplay(false).build();

        session.addItem(BasketItem.of("SKU-1", "Item", 1, "10.00"));
        session.setTaxTotal(new BigDecimal("1.00"));

        assertEquals(0, server.getRequestCount());
    }

    @Test
    void mutateBatchesToSingleDisplayUpdate() throws Exception {
        CheckoutSession session = sessionBuilder().build();

        Basket basket = session.mutate(m -> m
                .addItem(BasketItem.of("SKU-1", "Item One", 1, "10.00"))
                .addItem(BasketItem.of("SKU-2", "Item Two", 2, "5.00"))
                .setTaxTotal(new BigDecimal("1.60")));

        assertEquals(new BigDecimal("21.60"), basket.getGrandTotal());
        // give the (synchronous) send a moment to be recorded, then assert exactly one request
        DisplayPayload payload = nextDisplayPayload();
        assertEquals(2, payload.getReceipt().getLineItems().getLineItem().size());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void customDisplayRendererIsUsed() throws Exception {
        CheckoutSession session = sessionBuilder()
                .displayRenderer((basket, context) -> DisplayPayloadHelper.standby("custom"))
                .build();

        session.addItem(BasketItem.of("SKU-1", "Item", 1, "10.00"));

        DisplayPayload payload = nextDisplayPayload();
        assertNotNull(payload.getStandby());
    }

    @Test
    void displayFailureDoesNotFailBasketOperation() throws Exception {
        CheckoutSession session = sessionBuilder().build();
        server.shutdown();

        Basket basket = session.addItem(BasketItem.of("SKU-1", "Item", 1, "10.00"));

        assertEquals(new BigDecimal("10.00"), basket.getGrandTotal());
        assertEquals(SessionState.ACTIVE, session.getState());
    }

    @Test
    void failingCustomRendererDoesNotFailBasketOperation() {
        CheckoutSession session = sessionBuilder()
                .displayRenderer((basket, context) -> {
                    throw new IllegalStateException("renderer bug");
                })
                .build();

        assertDoesNotThrow(() -> session.addItem(BasketItem.of("SKU-1", "Item", 1, "10.00")));
        assertEquals(0, server.getRequestCount());
    }
}
