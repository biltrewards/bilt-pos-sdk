package com.bilt.pos.session;

import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.payment.CheckoutResult;
import com.bilt.pos.session.payment.PaymentOptions;
import com.bilt.pos.session.payment.TransactionStep;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutSessionPaymentTest {

    private static final String REWARDS_B64 = java.util.Base64.getEncoder().encodeToString(
            ("{\"rewards\":[{\"rewardRef\":\"rwd:RWD-44021\",\"type\":\"reward\","
                    + "\"name\":\"$10 Off Purchase\"}],\"rewardCount\":1}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private static final String IDENTIFY_OK =
            "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                    + "\"Response\":{\"Result\":\"Success\",\"AdditionalResponse\":\"" + REWARDS_B64 + "\"},"
                    + "\"LoyaltyAccount\":[{\"LoyaltyAccountID\":{\"LoyaltyID\":\"98234\"},"
                    + "\"LoyaltyBrand\":\"K-Club\"}]}}}";

    private static final String REBATE_OK =
            "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
                    + "\"Response\":{\"Result\":\"Success\"},"
                    + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-RB-1\","
                    + "\"TimeStamp\":\"2026-07-20T10:00:01Z\"}},"
                    + "\"LoyaltyResult\":[{\"Rebates\":{\"TotalRebate\":10.00,"
                    + "\"RebateLabel\":\"Gold Member\","
                    + "\"SaleItemRebate\":[{\"ItemID\":1,\"ProductCode\":\"SKU-1\","
                    + "\"ItemAmount\":10.00,\"RebateLabel\":\"Gold: $10 off\"}]}}]}}}";

    private static final String REDEEM_OK =
            "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
                    + "\"Response\":{\"Result\":\"Success\"},"
                    + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-RD-1\","
                    + "\"TimeStamp\":\"2026-07-20T10:00:02Z\"}},"
                    + "\"LoyaltyResult\":[{\"CurrentBalance\":700,"
                    + "\"LoyaltyAmount\":{\"AmountValue\":5.00,\"LoyaltyUnit\":\"Monetary\"}}]}}}";

    private static final String LOYALTY_REFUND_OK =
            "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{\"Response\":{\"Result\":\"Success\"}}}}";

    private static final String AWARD_OK =
            "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
                    + "\"Response\":{\"Result\":\"Success\","
                    + "\"AdditionalResponse\":\"promotionalMessage=60+points+to+your+next+reward\"},"
                    + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-AW-1\"}},"
                    + "\"LoyaltyResult\":[{\"CurrentBalance\":789,"
                    + "\"LoyaltyAmount\":{\"AmountValue\":89,\"LoyaltyUnit\":\"Point\"}}]}}}";

    private static String paymentOk(String poiTxn, double authorized) {
        return "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                + "\"Response\":{\"Result\":\"Success\"},"
                + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"" + poiTxn + "\","
                + "\"TimeStamp\":\"2026-07-20T10:00:03Z\"}},"
                + "\"PaymentResult\":{"
                + "\"AmountsResp\":{\"Currency\":\"USD\",\"AuthorizedAmount\":" + authorized + "},"
                + "\"PaymentAcquirerData\":{\"ApprovalCode\":\"APPR7\","
                + "\"AcquirerTransactionID\":{\"TransactionID\":\"ACQ-1\"}},"
                + "\"PaymentInstrumentData\":{\"CardData\":{\"PaymentBrand\":\"Visa\"}}}}}}";
    }

    private static final String PAYMENT_DECLINED =
            "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                    + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Refusal\"}}}}";

    private static final String REVERSAL_OK =
            "{\"SaleToPOIResponse\":{\"ReversalResponse\":{\"Response\":{\"Result\":\"Success\"}}}}";

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private MockWebServer server;
    private CheckoutSession session;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        session = CheckoutSession.builder()
                .client(BiltNexoTerminalClient.builder()
                        .endpoint(server.url("/nexo").toString())
                        .disableRecoveryOnNetworkError()
                        .build())
                .saleId("POS-LANE-3")
                .poiId("VictaLane-275839164")
                .currency("USD")
                .autoDisplay(false)
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    /** Identifies the member (consumes one enqueued response + one request). */
    private void identifyMember() throws Exception {
        server.enqueue(new MockResponse().setBody(IDENTIFY_OK));
        session.identifyMember().execute();
        server.takeRequest(5, TimeUnit.SECONDS);
    }

    private void addHundredDollarItem() {
        session.addItem(BasketItem.of("SKU-1", "Item", 1, "100.00"));
    }

    private SaleToPOIRequest nextRequest() throws Exception {
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

    // ─── Laziness ───

    @Test
    void payIsInertUntilExecuted() throws Exception {
        addHundredDollarItem();

        PaymentFlow flow = session.pay()
                .onSuccess(r -> { })
                .onError(e -> PaymentOptions.voidAndAbort());

        assertEquals(0, server.getRequestCount());
        assertEquals(SessionState.ACTIVE, session.getState());

        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
        flow.execute();
        assertEquals(SessionState.COMPLETED, session.getState());
        assertThrows(IllegalStateException.class, flow::execute);
        assertThrows(IllegalStateException.class, () -> flow.onSuccess(r -> { }));
    }

    @Test
    void payRequiresItems() {
        assertThrows(IllegalStateException.class, () -> session.pay());
    }

    // ─── Guest checkout ───

    @Test
    void guestCheckoutIsCardOnly() throws Exception {
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));

        CheckoutResult result = session.pay().get();

        assertTrue(result.isSuccess());
        assertEquals(0, new BigDecimal("100.00").compareTo(result.getCardAmountCharged()));
        assertEquals("APPR7", result.getApprovalCode());
        assertEquals("Visa", result.getPaymentBrand());
        assertEquals("POI-PAY-1", result.getPoiTransactionId());
        assertEquals(SessionState.COMPLETED, session.getState());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(1, requests.size(), "guest checkout must send only the card payment");
        assertEquals("Payment", requests.get(0).getMessageHeader().getMessageCategory().toValue());
        assertEquals(100.00, requests.get(0).getPaymentRequest().getPaymentTransaction()
                .getAmountsReq().getRequestedAmount());
        assertEquals(1, requests.get(0).getPaymentRequest().getPaymentTransaction()
                .getSaleItem().length);
    }

    // ─── Full loyalty flow ───

    @Test
    void fullFlowThreadsHandlerTotalsThroughSteps() throws Exception {
        identifyMember();
        addHundredDollarItem();

        server.enqueue(new MockResponse().setBody(REBATE_OK));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 84.50)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));

        AtomicReference<BigDecimal> rebateSuggested = new AtomicReference<>();
        CheckoutResult result = session.pay()
                .onRebatesRedeemed(rebates -> {
                    rebateSuggested.set(rebates.getSuggestedTotal());
                    assertEquals(0, new BigDecimal("100.00").compareTo(rebates.getPreviousTotal()));
                    assertEquals(0, new BigDecimal("10.00").compareTo(rebates.getTotalRebateAmount()));
                    assertEquals("Gold: $10 off", rebates.getRebates().get(0).getLabel());
                    assertEquals(0, new BigDecimal("90.00").compareTo(rebates.getUpdatedBasket()
                            .getItem("1").getAdjustedTotal()));
                    // register recomputed tax on the discounted amount
                    return new BigDecimal("89.50");
                })
                .onPointsRedeemed(points -> {
                    assertEquals(0, new BigDecimal("89.50").compareTo(points.getPreviousTotal()));
                    assertEquals(0, new BigDecimal("5.00").compareTo(points.getMonetaryValue()));
                    assertEquals(700, points.getRemainingPointBalance());
                    return points.getSuggestedTotal();  // 84.50
                })
                .get();

        assertEquals(0, new BigDecimal("90.00").compareTo(rebateSuggested.get()));
        assertEquals(0, new BigDecimal("84.50").compareTo(result.getCardAmountCharged()));
        assertEquals(0, new BigDecimal("10.00").compareTo(result.getTotalRebateAmount()));
        assertEquals(0, new BigDecimal("5.00").compareTo(result.getPointsMonetaryValue()));
        assertEquals(89, result.getTotalPointsEarned());
        assertEquals(789, result.getPointsBalance());
        assertEquals(List.of("60 points to your next reward"), result.getPromotionMessages());
        assertEquals(0, new BigDecimal("10.00").compareTo(
                result.getFinalBasket().getRebateTotal()));
        assertEquals(0, new BigDecimal("84.50").compareTo(
                result.getFinalBasket().getCardPaymentTotal()));

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(4, requests.size());
        assertEquals("Rebate", requests.get(0).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals(100.00, requests.get(0).getLoyaltyRequest()
                .getLoyaltyTransaction().getTotalAmount());
        assertEquals("Redemption", requests.get(1).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals(89.50, requests.get(1).getLoyaltyRequest()
                .getLoyaltyTransaction().getTotalAmount());
        assertNotNull(requests.get(1).getLoyaltyRequest().getSaleData().getSaleToPOIData(),
                "redemption must carry the rewardRefs payload");
        assertEquals(84.50, requests.get(2).getPaymentRequest()
                .getPaymentTransaction().getAmountsReq().getRequestedAmount());
        assertEquals(90.00, requests.get(2).getPaymentRequest().getPaymentTransaction()
                .getSaleItem()[0].getItemAmount(), "card step sends rebate-adjusted items");
        assertEquals("Award", requests.get(3).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
    }

    @Test
    void beforeStepControlsSaleTransactionIds() throws Exception {
        identifyMember();
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(REBATE_OK));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 85.00)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));

        session.pay()
                .beforeStep(ctx -> "TXN-" + ctx.getStep())
                .execute();

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals("TXN-" + TransactionStep.REBATE, requests.get(0).getLoyaltyRequest()
                .getSaleData().getSaleTransactionID().getTransactionID());
        assertEquals("TXN-" + TransactionStep.CARD_PAYMENT, requests.get(2).getPaymentRequest()
                .getSaleData().getSaleTransactionID().getTransactionID());
    }

    // ─── Gift card split tender ───

    @Test
    void giftCardPartialAuthLeavesRemainderForCard() throws Exception {
        identifyMember();
        addHundredDollarItem();
        session.setStoredValueCard("GC-1234-5678");

        server.enqueue(new MockResponse().setBody(REBATE_OK));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                        + "\"Response\":{\"Result\":\"Partial\",\"AdditionalResponse\":\"currentBalance=0.00\"},"
                        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-GC-1\"}},"
                        + "\"PaymentResult\":{\"AmountsResp\":{\"AuthorizedAmount\":35.00}}}}}"));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 50.00)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));

        AtomicReference<BigDecimal> gcBalance = new AtomicReference<>();
        CheckoutResult result = session.pay()
                .onGiftCardPayment(gc -> {
                    gcBalance.set(gc.getRemainingCardBalance());
                    assertEquals(0, new BigDecimal("35.00").compareTo(gc.getAmountCharged()));
                    return gc.getSuggestedTotal();  // 85 - 35 = 50
                })
                .get();

        assertEquals(0, new BigDecimal("0.00").compareTo(gcBalance.get()));
        assertEquals(0, new BigDecimal("35.00").compareTo(result.getStoredValueAmountUsed()));
        assertEquals(0, new BigDecimal("50.00").compareTo(result.getCardAmountCharged()));
        assertEquals(0, new BigDecimal("85.00").compareTo(result.getAuthorizedAmount()));

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(5, requests.size());
        assertEquals("StoredValue", requests.get(2).getPaymentRequest().getPaymentData()
                .getPaymentInstrumentData().getPaymentInstrumentType().toValue());
        assertEquals("GC-1234-5678", requests.get(2).getPaymentRequest().getPaymentData()
                .getPaymentInstrumentData().getStoredValueAccountID().getStoredValueID());
        assertEquals(50.00, requests.get(3).getPaymentRequest()
                .getPaymentTransaction().getAmountsReq().getRequestedAmount());
    }

    @Test
    void giftCardCoveringFullAmountSkipsCardPayment() throws Exception {
        addHundredDollarItem();
        session.setStoredValueCard("GC-1234-5678");

        server.enqueue(new MockResponse().setBody(paymentOk("POI-GC-1", 100.00)));

        CheckoutResult result = session.pay().get();

        assertEquals(0, new BigDecimal("100.00").compareTo(result.getStoredValueAmountUsed()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getCardAmountCharged()));
        assertEquals(1, drainRequests().size(), "no card payment when the gift card covers it");
    }

    // ─── Rollback ───

    @Test
    void cardDeclineUnwindsCommittedLoyaltyStepsInReverseOrder() throws Exception {
        identifyMember();
        addHundredDollarItem();

        server.enqueue(new MockResponse().setBody(REBATE_OK));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));
        server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));  // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));  // rebate refund

        AtomicReference<SessionError> seen = new AtomicReference<>();
        PaymentFlow flow = session.pay().onError(error -> {
            seen.set(error);
            return PaymentOptions.voidAndAbort();
        });

        assertNull(flow.getOrNull());
        assertEquals(SessionErrorCode.DECLINED, seen.get().getCode());
        assertEquals(SessionState.FAILED, session.getState());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(5, requests.size());
        assertEquals("RedemptionRefund", requests.get(3).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("POI-RD-1", requests.get(3).getLoyaltyRequest().getLoyaltyTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
        assertEquals("RebateRefund", requests.get(4).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("POI-RB-1", requests.get(4).getLoyaltyRequest().getLoyaltyTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
    }

    @Test
    void retryWithoutLoyaltySucceedsPaymentOnly() throws Exception {
        identifyMember();
        addHundredDollarItem();

        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"UnavailableService\"}}}}"));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));

        CheckoutResult result = session.pay()
                .onError(error -> {
                    assertEquals(SessionErrorCode.LOYALTY_UNAVAILABLE, error.getCode());
                    return PaymentOptions.retryWithoutLoyalty();
                })
                .get();

        assertTrue(result.isSuccess());
        assertEquals(0, new BigDecimal("100.00").compareTo(result.getCardAmountCharged()));
        assertEquals(SessionState.COMPLETED, session.getState());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(3, requests.size());
        assertNotNull(requests.get(1).getPaymentRequest(), "retry goes straight to card payment");
        assertEquals("Award", requests.get(2).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
    }

    @Test
    void retryLimitForcesFailure() throws Exception {
        addHundredDollarItem();
        for (int i = 0; i < 4; i++) {
            server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
        }

        AtomicInteger errorCalls = new AtomicInteger();
        PaymentFlow flow = session.pay().onError(error -> {
            errorCalls.incrementAndGet();
            return PaymentOptions.defaults();  // always retry
        });

        assertThrows(SessionException.class, flow::get);
        assertEquals(3, errorCalls.get());
        assertEquals(3, server.getRequestCount(), "retry cap stops the loop");
        assertEquals(SessionState.FAILED, session.getState());
    }

    @Test
    void failedPaymentCanBeRetriedWithNewPayCall() throws Exception {
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
        session.pay().getOrNull();
        assertEquals(SessionState.FAILED, session.getState());

        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-2", 100.00)));
        CheckoutResult result = session.pay().get();

        assertTrue(result.isSuccess());
        assertEquals(SessionState.COMPLETED, session.getState());
    }

    // ─── Award failure is non-fatal ───

    @Test
    void awardFailureCompletesCheckoutWithWarning() throws Exception {
        identifyMember();
        addHundredDollarItem();

        server.enqueue(new MockResponse().setBody(REBATE_OK));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 85.00)));
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"UnavailableService\"}}}}"));

        AtomicReference<CheckoutResult> success = new AtomicReference<>();
        session.pay()
                .onSuccess(success::set)
                .onError(error -> {
                    fail("award failure must not reach onError");
                    return PaymentOptions.voidAndAbort();
                })
                .execute();

        assertNotNull(success.get());
        assertEquals(0, success.get().getTotalPointsEarned());
        assertFalse(success.get().getWarnings().isEmpty());
        assertEquals(SessionState.COMPLETED, session.getState());
    }

    // ─── Abort mid-flow ───

    @Test
    void abortInsideHandlerUnwindsAndAbortsSession() throws Exception {
        identifyMember();
        addHundredDollarItem();

        server.enqueue(new MockResponse().setBody(REBATE_OK));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));  // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));  // rebate refund

        PaymentFlow flow = session.pay()
                .onPointsRedeemed(points -> {
                    session.abort();  // customer walked away
                    return points.getSuggestedTotal();
                })
                .onError(error -> {
                    fail("abort must not reach onError");
                    return null;
                });

        SessionException e = assertThrows(SessionException.class, flow::get);
        assertEquals(SessionErrorCode.ABORTED, e.getError().getCode());
        assertEquals(SessionState.ABORTED, session.getState());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(4, requests.size());
        assertEquals("RedemptionRefund", requests.get(2).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("RebateRefund", requests.get(3).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
    }

    @Test
    void abortDuringAwardStepReversesTheCardCharge() throws Exception {
        identifyMember();
        addHundredDollarItem();

        server.enqueue(new MockResponse().setBody(REBATE_OK));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 85.00)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));         // card reversal
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // rebate refund

        PaymentFlow flow = session.pay()
                .beforeStep(ctx -> {
                    if (ctx.getStep() == TransactionStep.AWARD) {
                        session.abort();  // lands while the award is being submitted
                    }
                    return ctx.getDefaultTransactionId();
                })
                .onSuccess(r -> fail("an aborted payment must not report success"))
                .onError(e -> {
                    fail("abort must not reach onError");
                    return null;
                });

        SessionException e = assertThrows(SessionException.class, flow::get);
        assertEquals(SessionErrorCode.ABORTED, e.getError().getCode());
        assertEquals(SessionState.ABORTED, session.getState());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(7, requests.size());
        assertEquals("POI-PAY-1", requests.get(4).getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
        assertEquals("RedemptionRefund", requests.get(5).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("RebateRefund", requests.get(6).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
    }

    // ─── Post-payment void uses the payment's references ───

    @Test
    void refundAfterCompletedPaymentUsesLastTransactionReference() throws Exception {
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
        session.pay().execute();
        drainRequests();
        assertEquals(SessionState.COMPLETED, session.getState());

        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},"
                        + "\"PaymentResult\":{\"AmountsResp\":{\"AuthorizedAmount\":25.00}}}}}"));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));  // award refund

        RefundResult refund = session.refund(new BigDecimal("25.00")).get();

        assertTrue(refund.isSuccess());
        assertEquals(SessionState.COMPLETED, session.getState(), "refund leaves the state alone");
        SaleToPOIRequest sent = nextRequest();
        assertEquals("Refund", sent.getPaymentRequest().getPaymentData().getPaymentType().toValue());
        assertEquals("POI-PAY-1", sent.getPaymentRequest().getPaymentTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
    }

    @Test
    void voidAfterPaymentUsesLastTransactionReference() throws Exception {
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
        session.pay().execute();
        drainRequests();

        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));
        VoidResult voided = session.voidTransaction().get();

        assertTrue(voided.isSuccess());
        assertEquals(SessionState.VOIDED, session.getState());
        SaleToPOIRequest reversal = nextRequest();
        assertEquals("POI-PAY-1", reversal.getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
    }
}
