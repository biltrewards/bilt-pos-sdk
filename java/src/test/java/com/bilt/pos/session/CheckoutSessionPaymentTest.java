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

    @Test
    void storeLocationIsSentAsTotalsGroupId() throws Exception {
        CheckoutSession storeSession = CheckoutSession.builder()
                .client(BiltNexoTerminalClient.builder()
                        .endpoint(server.url("/nexo").toString())
                        .disableRecoveryOnNetworkError()
                        .build())
                .saleId("POS-LANE-3")
                .poiId("VictaLane-275839164")
                .currency("USD")
                .storeLocation("STR-0142")
                .autoDisplay(false)
                .build();
        storeSession.addItem(BasketItem.of("SKU-1", "Item", 1, "10.00"));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 10.00)));

        storeSession.pay().execute();

        SaleToPOIRequest sent = nextRequest();
        assertEquals("STR-0142", sent.getPaymentRequest().getSaleData()
                .getSaleTerminalData().getTotalsGroupID(),
                "the store location must reach the wire as TotalsGroupID");
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
        assertEquals(90.00, requests.get(1).getLoyaltyRequest()
                .getLoyaltyTransaction().getTotalAmount(),
                "loyalty TotalAmount is the item sum (rebate-adjusted), not the running total");
        assertNotNull(requests.get(1).getLoyaltyRequest().getSaleData().getSaleToPOIData(),
                "redemption must carry the rewardRefs payload");
        assertEquals("Monetary", requests.get(1).getLoyaltyRequest().getLoyaltyData()[0]
                .getLoyaltyAmount().getLoyaltyUnit().toValue(),
                "redemption requires a Monetary LoyaltyAmount of 0.00");
        assertEquals(0.0, requests.get(1).getLoyaltyRequest().getLoyaltyData()[0]
                .getLoyaltyAmount().getAmountValue());
        assertEquals("USD", requests.get(1).getLoyaltyRequest().getLoyaltyData()[0]
                .getLoyaltyAmount().getCurrency());
        assertNull(requests.get(0).getLoyaltyRequest().getLoyaltyData()[0].getLoyaltyAmount(),
                "the rebate request carries no LoyaltyAmount");
        assertEquals(84.50, requests.get(2).getPaymentRequest()
                .getPaymentTransaction().getAmountsReq().getRequestedAmount());
        assertEquals(90.00, requests.get(2).getPaymentRequest().getPaymentTransaction()
                .getSaleItem()[0].getItemAmount(), "card step sends rebate-adjusted items");
        assertEquals("Award", requests.get(3).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());

        // contract: every loyalty request's TotalAmount equals its item sum
        for (int i : new int[] {0, 1, 3}) {
            double itemSum = 0;
            for (com.bilt.pos.nexo.model.SaleItem item : requests.get(i)
                    .getLoyaltyRequest().getLoyaltyTransaction().getSaleItem()) {
                itemSum += item.getItemAmount();
            }
            assertEquals(itemSum, requests.get(i).getLoyaltyRequest()
                    .getLoyaltyTransaction().getTotalAmount(), 0.001,
                    "TotalAmount must equal the SaleItem sum (request " + i + ")");
        }
    }

    @Test
    void globalRebateWithoutItemEntriesIsProratedOntoLines() throws Exception {
        String globalRebate =
                "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},"
                        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-RB-1\","
                        + "\"TimeStamp\":\"2026-07-20T10:00:01Z\"}},"
                        + "\"LoyaltyResult\":[{\"Rebates\":{\"TotalRebate\":10.00,"
                        + "\"RebateLabel\":\"Fall Promo\"}}]}}}";

        identifyMember();
        session.addItem(BasketItem.of("SKU-1", "Item A", 1, "50.00"));
        session.addItem(BasketItem.of("SKU-2", "Item B", 1, "25.00"));

        server.enqueue(new MockResponse().setBody(globalRebate));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 60.00)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));

        CheckoutResult result = session.pay()
                .onRebatesRedeemed(rebates -> {
                    assertEquals(0, new BigDecimal("10.00")
                            .compareTo(rebates.getTotalRebateAmount()));
                    assertEquals(1, rebates.getRebates().size());
                    assertNull(rebates.getRebates().get(0).getItemId(),
                            "an unattributed rebate surfaces as a cart-level entry");
                    assertEquals(0, new BigDecimal("10.00")
                            .compareTo(rebates.getRebates().get(0).getAmount()));
                    assertEquals("Fall Promo", rebates.getRebates().get(0).getLabel());
                    // 10.00 prorated by weight: 50→6.67, 25 (last) absorbs 3.33
                    assertEquals(0, new BigDecimal("43.33").compareTo(rebates
                            .getUpdatedBasket().getItem("1").getAdjustedTotal()));
                    assertEquals(0, new BigDecimal("21.67").compareTo(rebates
                            .getUpdatedBasket().getItem("2").getAdjustedTotal()));
                    assertEquals("Fall Promo",
                            rebates.getUpdatedBasket().getItem("1").getRebateLabel());
                    return rebates.getSuggestedTotal();  // 65.00
                })
                .get();

        assertTrue(result.isSuccess());
        assertEquals(0, new BigDecimal("60.00").compareTo(result.getCardAmountCharged()));
        assertEquals(0, new BigDecimal("10.00").compareTo(
                result.getFinalBasket().getRebateTotal()));

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(4, requests.size());
        assertEquals(65.00, requests.get(1).getLoyaltyRequest()
                .getLoyaltyTransaction().getTotalAmount(), 0.001,
                "redemption TotalAmount reflects the prorated global rebate");
        assertEquals(60.00, requests.get(2).getPaymentRequest()
                .getPaymentTransaction().getAmountsReq().getRequestedAmount());
        assertEquals(43.33, requests.get(2).getPaymentRequest().getPaymentTransaction()
                .getSaleItem()[0].getItemAmount(), 0.001,
                "card step sale items carry the prorated discount");
        assertEquals(21.67, requests.get(2).getPaymentRequest().getPaymentTransaction()
                .getSaleItem()[1].getItemAmount(), 0.001);
    }

    // ─── Reward-only checkout (no payment legs) ───

    private static final String REBATE_FULL_50 =
            "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
                    + "\"Response\":{\"Result\":\"Success\"},"
                    + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-RB-1\","
                    + "\"TimeStamp\":\"2026-07-20T10:00:01Z\"}},"
                    + "\"LoyaltyResult\":[{\"Rebates\":{\"TotalRebate\":50.00,"
                    + "\"RebateLabel\":\"Half Off\"}}]}}}";

    private static final String REDEEM_50 =
            "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
                    + "\"Response\":{\"Result\":\"Success\"},"
                    + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-RD-1\","
                    + "\"TimeStamp\":\"2026-07-20T10:00:02Z\"}},"
                    + "\"LoyaltyResult\":[{\"CurrentBalance\":100,"
                    + "\"LoyaltyAmount\":{\"AmountValue\":50.00,\"LoyaltyUnit\":\"Monetary\"}}]}}}";

    private static final String LOYALTY_REFUND_FAILED =
            "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
                    + "\"Response\":{\"Result\":\"Failure\","
                    + "\"ErrorCondition\":\"UnavailableService\"}}}}";

    /** Rewards cover the whole basket: rebate 50 + redemption 50 on a 100 item. */
    private void completeRewardOnlyCheckout() throws Exception {
        identifyMember();
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(REBATE_FULL_50));
        server.enqueue(new MockResponse().setBody(REDEEM_50));
        server.enqueue(new MockResponse().setBody(AWARD_OK));
        CheckoutResult result = session.pay().get();
        assertNull(result.getPoiTransactionId(), "no card payment was made");
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getCardAmountCharged()));
        assertEquals("POI-RB-1", result.getRebatePoiTransactionId());
        assertEquals("POI-RD-1", result.getRedemptionPoiTransactionId());
        assertEquals(3, drainRequests().size(), "rebate, redemption, award — no payment");
    }

    @Test
    void incompleteRollbackBlocksRetryAndSurfacesUnreversedMovements() throws Exception {
        identifyMember();
        addHundredDollarItem();

        server.enqueue(new MockResponse().setBody(REBATE_OK));             // rebate commits
        server.enqueue(new MockResponse().setBody(REDEEM_OK));             // redemption commits
        server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));      // card fails
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));     // redemption refund ok
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED)); // rebate refund FAILS

        AtomicInteger errorCalls = new AtomicInteger();
        SessionException failure = assertThrows(SessionException.class, () ->
                session.pay()
                        .onError(error -> {
                            errorCalls.incrementAndGet();
                            return PaymentOptions.retryWithoutLoyalty();   // must be refused
                        })
                        .get());

        assertEquals(1, errorCalls.get());
        assertEquals(SessionState.FAILED, session.getState());
        assertTrue(failure.getError().getMessage().contains("REBATE"),
                "the error must name the movement that is still standing: "
                        + failure.getError().getMessage());
        assertTrue(failure.getError().getMessage().contains("manual reconciliation"));

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(5, requests.size(),
                "no retry may run on top of an incomplete unwind");
        assertEquals("RebateRefund", requests.get(4).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
    }

    @Test
    void pointRedemptionIsSkippedWhenRebatesCoverTheTotal() throws Exception {
        String rebateFull =
                "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},"
                        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-RB-1\","
                        + "\"TimeStamp\":\"2026-07-20T10:00:01Z\"}},"
                        + "\"LoyaltyResult\":[{\"Rebates\":{\"TotalRebate\":100.00,"
                        + "\"RebateLabel\":\"Full Cover\"}}]}}}";

        identifyMember();                       // member HAS rewards
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(rebateFull));
        server.enqueue(new MockResponse().setBody(AWARD_OK));

        CheckoutResult result = session.pay().get();

        assertTrue(result.isSuccess());
        assertEquals(0, result.getPointsRedeemed());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getPointsMonetaryValue()));
        assertNull(result.getRedemptionPoiTransactionId());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(2, requests.size(),
                "rebate and award only — a zeroed total must not trigger a redemption");
        assertEquals("Rebate", requests.get(0).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("Award", requests.get(1).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
    }

    @Test
    void rewardOnlyCheckoutVoidsViaLoyaltyRefunds() throws Exception {
        completeRewardOnlyCheckout();

        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // rebate refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // award refund

        assertTrue(session.voidTransaction().get().isSuccess());
        assertEquals(SessionState.VOIDED, session.getState());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(3, requests.size());
        assertEquals("RedemptionRefund", requests.get(0).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("POI-RD-1", requests.get(0).getLoyaltyRequest().getLoyaltyTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
        assertNotNull(requests.get(0).getLoyaltyRequest().getSaleData().getSaleToPOIData(),
                "the redemption refund must carry the rewardRefs payload");
        assertEquals("98234", requests.get(0).getLoyaltyRequest().getLoyaltyData()[0]
                .getLoyaltyAccountID().getLoyaltyID());
        assertEquals("RebateRefund", requests.get(1).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("POI-RB-1", requests.get(1).getLoyaltyRequest().getLoyaltyTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
        assertEquals("AwardRefund", requests.get(2).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("POI-AW-1", requests.get(2).getLoyaltyRequest().getLoyaltyTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
    }

    @Test
    void rewardOnlyVoidRetryResumesAtRebateLeg() throws Exception {
        completeRewardOnlyCheckout();

        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));       // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED));   // rebate refund

        SessionException failure = assertThrows(SessionException.class,
                () -> session.voidTransaction().get());
        assertTrue(failure.getError().getMessage().contains("POI-RD-1"),
                "the error must say the redemption was already refunded");
        assertEquals(SessionState.COMPLETED, session.getState(),
                "a failed void restores the pre-void state");
        assertEquals(2, drainRequests().size());

        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // rebate refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // award refund

        assertTrue(session.voidTransaction().get().isSuccess());
        assertEquals(SessionState.VOIDED, session.getState());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(2, requests.size(), "the redemption leg must not be re-credited");
        assertEquals("RebateRefund", requests.get(0).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("AwardRefund", requests.get(1).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
    }

    @Test
    void finalBasketReflectsHandlerRecalculatedTax() throws Exception {
        identifyMember();
        session.addItem(BasketItem.of("SKU-1", "Item", 1, "100.00"));
        session.setTaxRateBySku("SKU-1", new BigDecimal("0.08"));  // grand 108.00

        server.enqueue(new MockResponse().setBody(REBATE_OK));                    // -10.00
        server.enqueue(new MockResponse().setBody(REDEEM_OK));                    // -5.00
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 92.20)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));

        CheckoutResult result = session.pay()
                .onRebatesRedeemed(rebates -> {
                    // tax-on-discounted-price jurisdiction: 90.00 × 0.08 = 7.20
                    return new BigDecimal("90.00").add(new BigDecimal("7.20"));
                })
                .get();

        assertEquals(0, new BigDecimal("92.20").compareTo(result.getCardAmountCharged()));
        // the final basket must reconcile with the money actually moved
        assertEquals(0, new BigDecimal("7.20").compareTo(
                result.getFinalBasket().getTaxTotal()),
                "the recalculated tax must reach the final basket");
        assertEquals(0, new BigDecimal("107.20").compareTo(
                result.getFinalBasket().getGrandTotal()));
        // display identity: grand − rebates − points == amount charged
        assertEquals(0, result.getFinalBasket().getGrandTotal()
                .subtract(result.getFinalBasket().getRebateTotal())
                .subtract(result.getFinalBasket().getPointDiscountTotal())
                .compareTo(result.getCardAmountCharged()));
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

    @Test
    void pointUnitRedemptionIsNotSubtractedAsMoney() throws Exception {
        identifyMember();
        addHundredDollarItem();

        server.enqueue(new MockResponse().setBody(REBATE_OK));
        // off-contract response: the redemption reports a POINT count, not money
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},"
                        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-RD-1\"}},"
                        + "\"LoyaltyResult\":[{\"CurrentBalance\":200,"
                        + "\"LoyaltyAmount\":{\"AmountValue\":500,\"LoyaltyUnit\":\"Point\"}}]}}}"));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 90.00)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));

        CheckoutResult result = session.pay()
                .onPointsRedeemed(points -> {
                    assertEquals(500, points.getPointsUsed());
                    assertEquals(0, BigDecimal.ZERO.compareTo(points.getMonetaryValue()),
                            "a point count must not become a dollar discount");
                    assertEquals(0, points.getPreviousTotal()
                            .compareTo(points.getSuggestedTotal()));
                    return points.getSuggestedTotal();
                })
                .get();

        assertEquals(500, result.getPointsRedeemed());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getPointsMonetaryValue()));

        List<SaleToPOIRequest> requests = drainRequests();
        // rebate (-10) leaves 90; the 500 points must not have reduced it
        assertEquals(90.00, requests.get(2).getPaymentRequest()
                .getPaymentTransaction().getAmountsReq().getRequestedAmount());
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
        assertEquals("POI-PAY-1", result.getPoiTransactionId(),
                "in a split tender the card payment's reference wins");

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
        assertEquals("POI-GC-1", result.getPoiTransactionId(),
                "the gift card payment's reference must reach the result");
        assertEquals("APPR7", result.getApprovalCode());
        assertEquals(1, drainRequests().size(), "no card payment when the gift card covers it");

        // and the checkout is voidable via that reference
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));
        assertTrue(session.voidTransaction().get().isSuccess());
        SaleToPOIRequest reversal = nextRequest();
        assertEquals("POI-GC-1", reversal.getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
    }

    @Test
    void storedValueDeclineWithLowBalanceIsLabelledInsufficient() throws Exception {
        addHundredDollarItem();
        session.setStoredValueCard("GC-1234-5678");
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Refusal\","
                        + "\"AdditionalResponse\":\"currentBalance=5.00\"}}}}"));

        AtomicReference<SessionError> seen = new AtomicReference<>();
        session.pay().onError(error -> {
            seen.set(error);
            return PaymentOptions.voidAndAbort();
        }).getOrNull();

        assertEquals(SessionErrorCode.STORED_VALUE_INSUFFICIENT, seen.get().getCode());
        assertTrue(seen.get().getMessage().contains("5.00"));
    }

    @Test
    void storedValueHardDeclineStaysDeclined() throws Exception {
        addHundredDollarItem();
        session.setStoredValueCard("GC-1234-5678");
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Refusal\","
                        + "\"AdditionalResponse\":\"Card+is+deactivated\"}}}}"));

        AtomicReference<SessionError> seen = new AtomicReference<>();
        session.pay().onError(error -> {
            seen.set(error);
            return PaymentOptions.voidAndAbort();
        }).getOrNull();

        assertEquals(SessionErrorCode.DECLINED, seen.get().getCode(),
                "a hard decline must not masquerade as low balance");
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
        String reversedRefs = new String(java.util.Base64.getDecoder().decode(
                requests.get(3).getLoyaltyRequest().getSaleData().getSaleToPOIData()),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(reversedRefs.contains("rwd:RWD-44021"),
                "the redemption reversal must carry the redeemed rewardRefs: " + reversedRefs);
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
    void staleAbortFlagDoesNotKillAPaymentRetry() throws Exception {
        addHundredDollarItem();

        // abort() races a decline: the payment settles FAILED on its own
        server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
        session.pay().onError(error -> {
            session.abort();  // deferred: the payment thread owns the outcome
            return PaymentOptions.voidAndAbort();
        }).getOrNull();
        assertEquals(SessionState.FAILED, session.getState());

        // the leftover flag must not abort the legitimate retry
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-2", 100.00)));
        assertTrue(session.pay().get().isSuccess());
        assertEquals(SessionState.COMPLETED, session.getState());
    }

    @Test
    void basketCanBeAdjustedAfterAFailedPayment() throws Exception {
        addHundredDollarItem();
        session.addItem(BasketItem.of("SKU-2", "Expensive Item", 1, "50.00"));
        server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
        session.pay().getOrNull();
        assertEquals(SessionState.FAILED, session.getState());

        // the customer drops an item and the register retries
        session.removeItemBySku("SKU-2");
        assertEquals(SessionState.ACTIVE, session.getState());

        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-2", 100.00)));
        CheckoutResult result = session.pay().get();
        assertEquals(0, new BigDecimal("100.00").compareTo(result.getCardAmountCharged()));
        assertEquals(SessionState.COMPLETED, session.getState());
    }

    @Test
    void emptyingTheBasketAfterAFailedPaymentReturnsToIdle() throws Exception {
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
        session.pay().getOrNull();

        session.removeItemBySku("SKU-1");
        assertEquals(SessionState.IDLE, session.getState());
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

    @Test
    void underAuthorizedCardPaymentIsReversedAndFailsTheCheckout() throws Exception {
        addHundredDollarItem();

        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                        + "\"Response\":{\"Result\":\"Partial\"},"
                        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-PAY-1\"}},"
                        + "\"PaymentResult\":{\"AmountsResp\":{\"AuthorizedAmount\":60.00}}}}}"));
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));  // partial auth reversed

        AtomicReference<SessionError> seen = new AtomicReference<>();
        PaymentFlow flow = session.pay().onError(error -> {
            seen.set(error);
            return PaymentOptions.voidAndAbort();
        });

        assertNull(flow.getOrNull());
        assertEquals(SessionErrorCode.DECLINED, seen.get().getCode());
        assertTrue(seen.get().getMessage().contains("60.00"));
        assertTrue(seen.get().getMessage().contains("100.00"));
        assertEquals(SessionState.FAILED, session.getState());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(2, requests.size());
        assertEquals("POI-PAY-1", requests.get(1).getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID(),
                "the partial authorization must be reversed");
    }

    @Test
    void throwingHandlerUnwindsCommittedStepsAndFailsTheSession() throws Exception {
        identifyMember();
        addHundredDollarItem();

        server.enqueue(new MockResponse().setBody(REBATE_OK));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));  // rebate refund

        AtomicReference<SessionError> seen = new AtomicReference<>();
        PaymentFlow flow = session.pay()
                .onRebatesRedeemed(rebates -> {
                    throw new NullPointerException("register bug");
                })
                .onError(error -> {
                    seen.set(error);
                    return PaymentOptions.voidAndAbort();
                });

        assertNull(flow.getOrNull());
        assertEquals(SessionErrorCode.UNKNOWN, seen.get().getCode());
        assertTrue(seen.get().getCause() instanceof NullPointerException);
        assertEquals(SessionState.FAILED, session.getState(),
                "the session must not stay frozen in PAYING");

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(2, requests.size());
        assertEquals("RebateRefund", requests.get(1).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue(),
                "the committed rebate must be unwound");

        // and the register can retry once the bug is out of the way
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-2", 100.00)));
        assertTrue(session.pay(PaymentOptions.retryWithoutLoyalty()).get().isSuccess());
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
                    // abort() defers to the payment thread while PAYING: the
                    // state must not flip mid-run (that would race the
                    // COMPLETED/ABORTED decision after orchestration returns)
                    assertEquals(SessionState.PAYING, session.getState());
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
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // award refund
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
        assertEquals(8, requests.size());
        assertEquals("AwardRefund", requests.get(4).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue(),
                "points credited by the award must be reversed with the tender");
        assertEquals("POI-AW-1", requests.get(4).getLoyaltyRequest().getLoyaltyTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
        assertEquals("98234", requests.get(4).getLoyaltyRequest().getLoyaltyData()[0]
                .getLoyaltyAccountID().getLoyaltyID());
        assertEquals("POI-PAY-1", requests.get(5).getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
        assertEquals("RedemptionRefund", requests.get(6).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("RebateRefund", requests.get(7).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
    }

    @Test
    void abortKillingTheInFlightStepBypassesOnError() throws Exception {
        addHundredDollarItem();
        // the terminal aborts the in-flight card payment after our abort()
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Aborted\"}}}}"));

        PaymentFlow flow = session.pay()
                .beforeStep(ctx -> {
                    session.abort();  // lands while the card request is in flight
                    return ctx.getDefaultTransactionId();
                })
                .onError(error -> {
                    fail("an intentional abort must not reach onError");
                    return null;
                });

        SessionException e = assertThrows(SessionException.class, flow::get);
        assertEquals(SessionErrorCode.ABORTED, e.getError().getCode());
        assertEquals(SessionState.ABORTED, session.getState());
    }

    @Test
    void terminalInitiatedAbortStillReachesOnError() throws Exception {
        addHundredDollarItem();
        // no abort() from us: the terminal aborted the payment on its own
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Aborted\"}}}}"));

        AtomicReference<SessionError> seen = new AtomicReference<>();
        session.pay().onError(error -> {
            seen.set(error);
            return PaymentOptions.voidAndAbort();
        }).getOrNull();

        assertEquals(SessionErrorCode.ABORTED, seen.get().getCode(),
                "a spontaneous terminal abort is surfaced to the register via onError");
        assertEquals(SessionState.ABORTED, session.getState());
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
    void failedVoidAfterPaymentDoesNotAllowRePayment() throws Exception {
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
        session.pay().execute();
        drainRequests();

        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"UnreachableHost\"}}}}"));
        assertThrows(SessionException.class, () -> session.voidTransaction().get());

        // the payment still stands: no second charge may be authorized
        assertEquals(SessionState.COMPLETED, session.getState());
        assertThrows(IllegalStateException.class, () -> session.pay());

        // but the void can be retried
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));
        assertTrue(session.voidTransaction().get().isSuccess());
        assertEquals(SessionState.VOIDED, session.getState());
    }

    @Test
    void voidOfSplitTenderReversesBothLegs() throws Exception {
        addHundredDollarItem();
        session.setStoredValueCard("GC-1234-5678");
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                        + "\"Response\":{\"Result\":\"Partial\"},"
                        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-GC-1\"}},"
                        + "\"PaymentResult\":{\"AmountsResp\":{\"AuthorizedAmount\":35.00}}}}}"));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 65.00)));
        session.pay().execute();
        drainRequests();

        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},\"ReversedAmount\":65.00}}}"));
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},\"ReversedAmount\":35.00}}}"));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));

        VoidResult voided = session.voidTransaction().get();

        assertEquals(0, new BigDecimal("100.00").compareTo(voided.getReversedAmount()),
                "both legs must be reversed");
        assertEquals(SessionState.VOIDED, session.getState());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(3, requests.size());
        assertEquals("POI-PAY-1", requests.get(0).getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
        assertEquals("POI-GC-1", requests.get(1).getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
        assertEquals("AwardRefund", requests.get(2).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
    }

    @Test
    void voidOfGiftCardOnlyCheckoutSendsASingleReversal() throws Exception {
        addHundredDollarItem();
        session.setStoredValueCard("GC-1234-5678");
        server.enqueue(new MockResponse().setBody(paymentOk("POI-GC-1", 100.00)));
        session.pay().execute();
        drainRequests();

        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));
        session.voidTransaction().execute();

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(2, requests.size(), "one transaction, one reversal — no duplicate");
        assertEquals("POI-GC-1", requests.get(0).getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
    }

    @Test
    void failedStoredValueLegReversalReportsThePartialVoid() throws Exception {
        addHundredDollarItem();
        session.setStoredValueCard("GC-1234-5678");
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                        + "\"Response\":{\"Result\":\"Partial\"},"
                        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-GC-1\"}},"
                        + "\"PaymentResult\":{\"AmountsResp\":{\"AuthorizedAmount\":35.00}}}}}"));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 65.00)));
        session.pay().execute();
        drainRequests();

        server.enqueue(new MockResponse().setBody(REVERSAL_OK));  // card leg reversed
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"UnreachableHost\"}}}}"));

        SessionException e = assertThrows(SessionException.class,
                () -> session.voidTransaction().get());
        assertTrue(e.getError().getMessage().contains("POI-PAY-1 was reversed"));
        assertTrue(e.getError().getMessage().contains("POI-GC-1 was not"));
        assertEquals(SessionState.COMPLETED, session.getState(),
                "the void failed; the session returns to its pre-void state");
        drainRequests();

        // the retry resumes at the outstanding gift card leg — the card
        // payment must not be reversed a second time
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));
        assertTrue(session.voidTransaction().get().isSuccess());
        assertEquals(SessionState.VOIDED, session.getState());

        List<SaleToPOIRequest> retryRequests = drainRequests();
        assertEquals(2, retryRequests.size());
        assertEquals("POI-GC-1", retryRequests.get(0).getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID(),
                "only the stored value leg remains to reverse");
        assertEquals("AwardRefund", retryRequests.get(1).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
    }

    @Test
    void voidAfterLoyaltyCheckoutReversesTheAwardByItsOwnReference() throws Exception {
        identifyMember();
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));  // POI-AW-1
        session.pay(PaymentOptions.builder()
                .disableRebates(true).disablePoints(true).build()).execute();
        drainRequests();

        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));
        session.voidTransaction().execute();

        List<SaleToPOIRequest> requests = drainRequests();
        SaleToPOIRequest awardRefund = requests.get(1);
        assertEquals("AwardRefund", awardRefund.getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("POI-AW-1", awardRefund.getLoyaltyRequest().getLoyaltyTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID(),
                "the reversal must reference the award, not the payment");
        assertEquals("98234", awardRefund.getLoyaltyRequest().getLoyaltyData()[0]
                .getLoyaltyAccountID().getLoyaltyID(),
                "the reversal must carry the member's LoyaltyData");
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
