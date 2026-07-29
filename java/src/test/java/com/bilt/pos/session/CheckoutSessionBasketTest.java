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

    // answers both the display sends and the session-start Admin exchange,
    // so the catch-all dispatcher can serve every request this class makes
    private static final String DISPLAY_OK =
            "{\"SaleToPOIResponse\":{\"DisplayResponse\":{},"
                    + "\"AdminResponse\":{\"Response\":{\"Result\":\"Success\"}}}}";

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

    /** Starts the session and drains the session-start Admin request. */
    private CheckoutSession start(CheckoutSession.Builder builder) throws Exception {
        CheckoutSession session = builder.start().get();
        server.takeRequest(5, TimeUnit.SECONDS);
        return session;
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
    void negativeTaxValuesAreRejected() throws Exception {
        CheckoutSession session = start(sessionBuilder().autoDisplay(false));
        session.addItem(BasketItem.of("SKU-1", "Item", 1, "100.00"));

        assertThrows(IllegalArgumentException.class,
                () -> session.setTaxTotal(new BigDecimal("-100.00")));
        assertThrows(IllegalArgumentException.class,
                () -> session.setTaxRateBySku("SKU-1", new BigDecimal("-0.08")));
        assertThrows(IllegalArgumentException.class,
                () -> session.setTaxAmountBySku("SKU-1", new BigDecimal("-2.50")));

        // the rejected values left no trace on the basket
        assertEquals(0, new BigDecimal("100.00")
                .compareTo(session.getBasket().getGrandTotal()));
    }

    @Test
    void mutateIsAtomicWhenABatchOperationThrows() throws Exception {
        CheckoutSession session = start(sessionBuilder().autoDisplay(false));
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
    void basketDrivesIdleActiveTransitions() throws Exception {
        CheckoutSession session = start(sessionBuilder().autoDisplay(false));

        assertEquals(SessionState.IDLE, session.getState());
        session.addItem(BasketItem.of("SKU-1", "Item", 1, "10.00"));
        assertEquals(SessionState.ACTIVE, session.getState());
        session.removeItemBySku("SKU-1");
        assertEquals(SessionState.IDLE, session.getState());
    }

    @Test
    void basketOpsAreRejectedAfterAbort() throws Exception {
        CheckoutSession session = start(sessionBuilder().autoDisplay(false));
        session.abort();

        assertThrows(IllegalStateException.class,
                () -> session.addItem(BasketItem.of("SKU-1", "Item", 1, "10.00")));
    }

    // ─── Totals through the session API ───

    @Test
    void designDocScanningExample() throws Exception {
        CheckoutSession session = start(sessionBuilder().autoDisplay(false));

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
        CheckoutSession session = start(sessionBuilder());

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
    void autoDisplayOffSendsNothing() throws Exception {
        CheckoutSession session = start(sessionBuilder().autoDisplay(false));

        session.addItem(BasketItem.of("SKU-1", "Item", 1, "10.00"));
        session.setTaxTotal(new BigDecimal("1.00"));

        assertEquals(1, server.getRequestCount(), "only the session start may hit the wire");
    }

    @Test
    void mutateBatchesToSingleDisplayUpdate() throws Exception {
        CheckoutSession session = start(sessionBuilder());

        Basket basket = session.mutate(m -> m
                .addItem(BasketItem.of("SKU-1", "Item One", 1, "10.00"))
                .addItem(BasketItem.of("SKU-2", "Item Two", 2, "5.00"))
                .setTaxTotal(new BigDecimal("1.60")));

        assertEquals(new BigDecimal("21.60"), basket.getGrandTotal());
        // give the (synchronous) send a moment to be recorded, then assert exactly one request
        DisplayPayload payload = nextDisplayPayload();
        assertEquals(2, payload.getReceipt().getLineItems().getLineItem().size());
        assertEquals(2, server.getRequestCount(), "session start plus exactly one display update");
    }

    @Test
    void customDisplayRendererIsUsed() throws Exception {
        CheckoutSession session = start(sessionBuilder()
                .displayRenderer((basket, context) -> DisplayPayloadHelper.standby("custom")));

        session.addItem(BasketItem.of("SKU-1", "Item", 1, "10.00"));

        DisplayPayload payload = nextDisplayPayload();
        assertNotNull(payload.getStandby());
    }

    @Test
    void displayFailureDoesNotFailBasketOperation() throws Exception {
        CheckoutSession session = start(sessionBuilder());
        server.shutdown();

        Basket basket = session.addItem(BasketItem.of("SKU-1", "Item", 1, "10.00"));

        assertEquals(new BigDecimal("10.00"), basket.getGrandTotal());
        assertEquals(SessionState.ACTIVE, session.getState());
    }

    @Test
    void failingCustomRendererDoesNotFailBasketOperation() throws Exception {
        CheckoutSession session = start(sessionBuilder()
                .displayRenderer((basket, context) -> {
                    throw new IllegalStateException("renderer bug");
                }));

        assertDoesNotThrow(() -> session.addItem(BasketItem.of("SKU-1", "Item", 1, "10.00")));
        assertEquals(1, server.getRequestCount(), "only the session start may hit the wire");
    }
}
