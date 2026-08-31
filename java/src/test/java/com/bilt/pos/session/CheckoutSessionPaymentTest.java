package com.bilt.pos.session;

import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.settlement.OriginalSaleRecord;
import com.bilt.pos.session.settlement.SettlementRecovery;
import com.bilt.pos.session.settlement.SettlementResult;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.bilt.pos.session.settlement.SettlementStep;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutSessionPaymentTest {

    private static final java.time.Instant ORIGINAL_TIME =
            java.time.Instant.parse("2026-07-20T10:00:00Z");
    private static final String PRIOR_CARD_POI_TXN = "POI-CARD-9";
    private static final String PRIOR_STORED_VALUE_POI_TXN = "POI-SV-9";
    private static final String PRIOR_REBATE_POI_TXN = "POI-RB-9";
    private static final String PRIOR_REDEMPTION_POI_TXN = "POI-RD-9";

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

    private static final String LOYALTY_REFUND_OK = CheckoutSessionTest.LOYALTY_REFUND_OK;

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

    private static final String REVERSAL_OK = CheckoutSessionTest.REVERSAL_OK;
    private static final String REVERSAL_UNREACHABLE =
            "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
                    + "\"Response\":{\"Result\":\"Failure\","
                    + "\"ErrorCondition\":\"UnreachableHost\"}}}}";

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private MockWebServer server;
    private CheckoutSession session;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        session = start(sessionBuilder());
    }

    private CheckoutSession.Builder sessionBuilder() {
        return CheckoutSession.builder()
                .client(BiltNexoTerminalClient.builder()
                        .endpoint(server.url("/nexo").toString())
                        .disableRecoveryOnNetworkError()
                        .build())
                .saleId("POS-LANE-3")
                .poiId("VictaLane-275839164")
                .currency("USD")
                .autoDisplay(false);
    }

    /** Starts a session, answering and draining the session-start Admin exchange. */
    private CheckoutSession start(CheckoutSession.Builder builder) throws Exception {
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));
        CheckoutSession started = builder.start().get();
        server.takeRequest(5, TimeUnit.SECONDS); // drain the session-start Admin request
        return started;
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    /** Identifies the member (consumes one enqueued response + one request). */
    private void identifyMember() throws Exception {
        identifyMember("98234");
    }

    /** Identifies a specific member from the reusable terminal fixture. */
    private void identifyMember(String memberId) throws Exception {
        server.enqueue(new MockResponse().setBody(IDENTIFY_OK.replace("98234", memberId)));
        session.identifyMember().executeSync();
        server.takeRequest(5, TimeUnit.SECONDS);
    }

    private void addHundredDollarItem() {
        session.basket().addItem(BasketItem.sale("SKU-1", "Item", 1, "100.00"));
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
    void unexpectedFlowExceptionIsNotMaskedAsSuccess() {
        SettlementFlow flow = new SettlementFlow(f -> {
            throw new IllegalStateException("boom");
        });

        IllegalStateException first =
                assertThrows(IllegalStateException.class, flow::executeSync);

        // later accessors must rethrow the failure, never report success
        assertSame(first, assertThrows(IllegalStateException.class, flow::get));
        assertSame(first, assertThrows(IllegalStateException.class, flow::getOrNull));
    }

    @Test
    void throwingSuccessHandlerStaysLoudOnLaterAccessors() {
        SettlementFlow flow = new SettlementFlow(f -> SettlementResult.builder().success(true).build())
                .onSuccess(result -> {
                    throw new NullPointerException("register bug");
                });

        NullPointerException first =
                assertThrows(NullPointerException.class, flow::executeSync);

        // a throwing success handler is a bug like any other: later
        // accessors rethrow it instead of reporting a clean success
        assertSame(first, assertThrows(NullPointerException.class, flow::get));
        assertSame(first, assertThrows(NullPointerException.class, flow::getOrNull));
    }

    @Test
    void executeDeliversPreOrchestrationFailuresToOnError() {
        addHundredDollarItem();
        SettlementFlow flow = session.settle();
        AtomicReference<SessionError> seen = new AtomicReference<>();
        flow.onError(error -> {
            seen.set(error);
            return SettlementRecovery.abort();
        });

        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));
        session.end().get();   // the session ends before the flow executes
        flow.executeSync();        // must not return silently

        assertNotNull(seen.get(), "a failure before the sequence starts must reach onError");
        assertEquals(SessionErrorCode.INVALID_STATE, seen.get().getCode());
        assertEquals(2, server.getRequestCount(),
                "only the session start and end may hit the wire");
    }

    @Test
    void executeDeliversAFailedStandingDrainToOnError() throws Exception {
        identifyMember();
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(REBATE_OK));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));
        server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED));
        assertThrows(SessionException.class, () -> session.settle().get());
        drainRequests();

        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED)); // drain still fails
        AtomicReference<SessionError> seen = new AtomicReference<>();
        session.settle()
                .onError(error -> {
                    seen.set(error);
                    return SettlementRecovery.abort();
                })
                .executeSync();

        assertNotNull(seen.get(), "a failed standing-movement drain must reach onError");
        assertTrue(seen.get().getMessage().contains("retry did not start"),
                seen.get().getMessage());
    }

    @Test
    void payIsInertUntilExecuted() throws Exception {
        addHundredDollarItem();

        SettlementFlow flow = session.settle()
                .onSuccess(r -> { })
                .onError(e -> SettlementRecovery.abort());

        assertEquals(1, server.getRequestCount(), "only the session start may hit the wire");

        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
        flow.executeSync();
        assertThrows(IllegalStateException.class, flow::executeSync);
        assertThrows(IllegalStateException.class, () -> flow.onSuccess(r -> { }));
    }

    @Test
    void payRequiresItems() {
        // verified at execute time, not at settle(): a violation must reach
        // the flow's onError handler, which does not exist yet when settle()
        // itself runs
        AtomicReference<SessionError> error = new AtomicReference<>();
        session.settle()
                .onError(e -> {
                    error.set(e);
                    return SettlementRecovery.abort();
                })
                .executeSync();

        assertNotNull(error.get());
        assertEquals(SessionErrorCode.INVALID_STATE, error.get().getCode());
        assertEquals(1, server.getRequestCount(),
                "nothing beyond the session start may reach the wire");
    }

    @Test
    void settlementAllowsAZeroTotalBasket() {
        // Free and fully discounted items are still completed commercial lines,
        // even though settlement has no external movement to execute.
        addHundredDollarItem();
        SettlementFlow flow = session.settle();   // created while the total is positive
        session.basket().mutate(m -> m
                .removeItemBySku("SKU-1")
                .addItem(BasketItem.sale("SKU-FREE", "Comped Item", 1, "0.00")));

        assertTrue(flow.get().isSuccess());
        assertEquals(1, server.getRequestCount(), "nothing beyond the session start may reach the wire");
    }

    @Test
    void storeLocationIsSentAsTotalsGroupId() throws Exception {
        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));
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
                .start()
                .get();
        server.takeRequest(5, TimeUnit.SECONDS); // drain the session-start Admin request
        storeSession.basket().addItem(BasketItem.sale("SKU-1", "Item", 1, "10.00"));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 10.00)));

        storeSession.settle().executeSync();

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

        SettlementResult result = session.settle().get();

        assertTrue(result.isSuccess());
        assertEquals(0, new BigDecimal("100.00").compareTo(result.getCardAmountCharged()));
        assertEquals("APPR7", result.getApprovalCode());
        assertEquals("Visa", result.getPaymentBrand());
        assertEquals("POI-PAY-1", result.getPoiTransactionId());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(1, requests.size(), "guest checkout must send only the card payment");
        assertEquals("Payment", requests.get(0).getMessageHeader().getMessageCategory().toValue());
        assertEquals(100.00, requests.get(0).getPaymentRequest().getPaymentTransaction()
                .getAmountsReq().getRequestedAmount());
        assertEquals(1, requests.get(0).getPaymentRequest().getPaymentTransaction()
                .getSaleItem().length);
    }

    @Test
    void settledBasketCannotBeChargedTwice() throws Exception {
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
        session.settle().get();
        drainRequests();
        int requestsBeforeRetry = server.getRequestCount();

        SessionException failure = assertThrows(SessionException.class,
                () -> session.settle().get());

        assertEquals(SessionErrorCode.INVALID_STATE, failure.getError().getCode());
        assertTrue(failure.getError().getMessage().contains("basket().clear()"));
        assertEquals(requestsBeforeRetry, server.getRequestCount(),
                "the second settlement must fail before another payment request");
    }

    @Test
    void clearingSettledBasketAllowsAnotherSettlementInTheSameSession() throws Exception {
        addHundredDollarItem();
        String firstCartId = session.basket().snapshot().getCartId();
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
        SettlementResult first = session.settle().get();
        drainRequests();

        assertEquals("POI-PAY-1", first.getPoiTransactionId());
        assertThrows(IllegalStateException.class,
                () -> session.basket().addItem(BasketItem.sale("SKU-2", "Item", 1, "25.00")));

        assertTrue(session.basket().clear().isEmpty());
        assertNotEquals(firstCartId, session.basket().snapshot().getCartId());
        session.basket().addItem(BasketItem.sale("SKU-2", "Item", 1, "25.00"));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-2", 25.00)));

        SettlementResult second = session.settle().get();

        assertEquals("POI-PAY-2", second.getPoiTransactionId());
        SaleToPOIRequest request = nextRequest();
        assertEquals(25.00, request.getPaymentRequest().getPaymentTransaction()
                .getAmountsReq().getRequestedAmount());
    }

    @Test
    void clearingBasketClearsTheSelectedStoredValueTender() throws Exception {
        session.setStoredValueCard("GC-1234");
        session.basket().addItem(BasketItem.sale("DRAFT", "Draft", 1, "1.00"));

        session.basket().clear();
        session.basket().addItem(BasketItem.sale("SKU-1", "Item", 1, "10.00"));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 10.00)));

        session.settle().get();

        assertNotNull(nextRequest().getPaymentRequest(),
                "a stored-value tender selected for the cleared basket must not leak");
    }

    @Test
    void aLaterSettlementReplacesTheSameSessionVoidTarget() throws Exception {
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
        session.settle().get();
        drainRequests();

        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
        session.voidTransaction().get();
        drainRequests();

        session.basket().clear();
        session.basket().addItem(BasketItem.sale("SKU-2", "Item", 1, "25.00"));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-2", 25.00)));
        session.settle().get();
        drainRequests();

        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
        assertTrue(session.voidTransaction().get().isSuccess());

        SaleToPOIRequest reversal = nextRequest();
        assertEquals("POI-PAY-2", reversal.getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
    }

    @Test
    void sameSessionPaymentCannotBeVoidedTwice() throws Exception {
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
        session.settle().get();
        drainRequests();
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
        session.voidTransaction().get();
        drainRequests();
        int requestsAfterVoid = server.getRequestCount();

        SessionException failure = assertThrows(SessionException.class,
                () -> session.voidTransaction().get());

        assertEquals(SessionErrorCode.INVALID_STATE, failure.getError().getCode());
        assertTrue(failure.getError().getMessage().contains("already been voided"));
        assertEquals(requestsAfterVoid, server.getRequestCount());
    }

    @Test
    void settleChargesBasketLevelTaxTotalOverride() throws Exception {
        addHundredDollarItem();
        session.basket().setTaxTotal(new BigDecimal("8.88"));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 108.88)));

        SettlementResult result = session.settle().get();

        assertTrue(result.isSuccess());
        assertEquals(0, new BigDecimal("108.88").compareTo(result.getCardAmountCharged()));
        assertEquals(0, new BigDecimal("8.88").compareTo(
                result.getFinalBasket().getTaxTotal()));

        SaleToPOIRequest sent = nextRequest();
        assertEquals(108.88, sent.getPaymentRequest().getPaymentTransaction()
                .getAmountsReq().getRequestedAmount(),
                "basket-level tax overrides must be included in the charge amount");
    }

    @Test
    void earnedRewardsFromTheAwardResponseReachTheResult() throws Exception {
        String earnedB64 = java.util.Base64.getEncoder().encodeToString(
                ("{\"pointsEarned\":89,\"earnedRewards\":[{\"rewardRef\":\"rwd:RWD-90001\","
                        + "\"type\":\"reward\",\"name\":\"$5 Off Next Visit\",\"quantity\":2}]}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String awardWithRewards =
                "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\","
                        + "\"AdditionalResponse\":\"" + earnedB64 + "\"},"
                        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-AW-1\"}},"
                        + "\"LoyaltyResult\":[{\"CurrentBalance\":789,"
                        + "\"LoyaltyAmount\":{\"AmountValue\":89,\"LoyaltyUnit\":\"Point\"}}]}}}";

        identifyMember();
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(REBATE_OK));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 85.00)));
        server.enqueue(new MockResponse().setBody(awardWithRewards));

        SettlementResult result = session.settle().get();

        assertEquals(1, result.getEarnedRewards().size(),
                "rewards earned by the award must reach the result");
        assertEquals("$5 Off Next Visit", result.getEarnedRewards().get(0).getDescription());
        assertEquals("rwd:RWD-90001", result.getEarnedRewards().get(0).getRewardRef());
        assertEquals(2, result.getEarnedRewards().get(0).getQuantity());
        assertEquals(89, result.getTotalPointsEarned());
    }

    // ─── Cashback ───

    @Test
    void nonPositiveCashbackIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SettlementOptions.builder().cashback(new BigDecimal("-20.00")));
        assertThrows(IllegalArgumentException.class,
                () -> SettlementOptions.builder().cashback(BigDecimal.ZERO));
    }

    @Test
    void cashbackIsIncludedInTheRequestedAmount() throws Exception {
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 120.00)));

        SettlementResult result = session.settle(SettlementOptions.builder()
                .cashback(new BigDecimal("20.00"))
                .build()).get();

        assertTrue(result.isSuccess());
        assertEquals(0, new BigDecimal("120.00").compareTo(result.getCardAmountCharged()));

        SaleToPOIRequest sent = nextRequest();
        assertEquals(120.00, sent.getPaymentRequest().getPaymentTransaction()
                .getAmountsReq().getRequestedAmount(),
                "RequestedAmount is the total INCLUDING cashback per the nexo contract");
        assertEquals(20.00, sent.getPaymentRequest().getPaymentTransaction()
                .getAmountsReq().getCashBackAmount());
    }

    @Test
    void cashbackUnderAuthorizationFailsAndReverses() throws Exception {
        addHundredDollarItem();
        // terminal authorizes only the sale portion, not sale + cashback
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));

        SessionException failure = assertThrows(SessionException.class, () ->
                session.settle(SettlementOptions.builder()
                        .cashback(new BigDecimal("20.00"))
                        .build()).get());

        assertEquals(SessionErrorCode.DECLINED, failure.getError().getCode());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(2, requests.size());
        assertEquals("Reversal", requests.get(1).getMessageHeader()
                .getMessageCategory().toValue(),
                "the short authorization must be reversed, not kept");
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
        SettlementResult result = session.settle()
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
        session.basket().addItem(BasketItem.sale("SKU-1", "Item A", 1, "50.00"));
        session.basket().addItem(BasketItem.sale("SKU-2", "Item B", 1, "25.00"));

        server.enqueue(new MockResponse().setBody(globalRebate));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 60.00)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));

        SettlementResult result = session.settle()
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

    private static final String LOYALTY_REFUND_FAILED = CheckoutSessionTest.LOYALTY_REFUND_FAILED;

    /** Rewards cover the whole basket: rebate 50 + redemption 50 on a 100 item. */
    private void completeRewardOnlyCheckout() throws Exception {
        identifyMember();
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(REBATE_FULL_50));
        server.enqueue(new MockResponse().setBody(REDEEM_50));
        server.enqueue(new MockResponse().setBody(AWARD_OK));
        SettlementResult result = session.settle().get();
        assertNull(result.getPoiTransactionId(), "no card payment was made");
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getCardAmountCharged()));
        assertEquals("POI-RB-1", result.getRebatePoiTransactionId());
        assertEquals("POI-RD-1", result.getRedemptionPoiTransactionId());
        assertEquals(3, drainRequests().size(), "rebate, redemption, award — no payment");
    }

    @Test
    void throwingMutationDoesNotResumeAFailedSession() throws Exception {
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
        assertThrows(SessionException.class, () -> session.settle().get());

        assertThrows(IllegalArgumentException.class,
                () -> session.basket().removeItemBySku("NO-SUCH-SKU"));

        assertEquals(1, session.basket().snapshot().getItemCount());
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
                session.settle()
                        .onError(error -> {
                            errorCalls.incrementAndGet();
                            return SettlementRecovery.retryWithoutLoyalty();   // must be refused
                        })
                        .get());

        assertEquals(2, errorCalls.get(),
                "the consultation, then the notification that the retry was refused");
        assertTrue(failure.getError().getMessage().contains("REBATE"),
                "the error must name the movement that is still standing: "
                        + failure.getError().getMessage());
        assertTrue(failure.getError().getMessage().contains("manual reconciliation"));
        assertTrue(failure.getError().getMessage().contains("retry was refused"),
                failure.getError().getMessage());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(5, requests.size(),
                "no retry may run on top of an incomplete unwind");
        assertEquals("RebateRefund", requests.get(4).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
    }

    @Test
    void voidAfterIncompleteRollbackFinishesTheUnwind() throws Exception {
        identifyMember();
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(REBATE_OK));             // rebate commits
        server.enqueue(new MockResponse().setBody(REDEEM_OK));             // redemption commits
        server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));      // card fails
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));     // redemption refund ok
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED)); // rebate refund FAILS
        assertThrows(SessionException.class, () -> session.settle().get());
        drainRequests();

        // voiding the failed session retries exactly the standing reversal
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));
        assertTrue(session.voidTransaction().get().isSuccess());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(1, requests.size(), "only the movement still standing is reversed");
        assertEquals("RebateRefund", requests.get(0).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("POI-RB-1", requests.get(0).getLoyaltyRequest().getLoyaltyTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
    }

    @Test
    void unwindResumeRetriesOnlyMovementsStillStanding() throws Exception {
        identifyMember();
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(REBATE_OK));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));
        server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED)); // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED)); // rebate refund
        assertThrows(SessionException.class, () -> session.settle().get());
        drainRequests();

        // first void attempt: redemption refund succeeds, rebate refund fails
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED));
        assertThrows(SessionException.class, () -> session.voidTransaction().get());
        assertEquals(2, drainRequests().size());

        // retry resumes at the rebate leg — the redemption is not re-credited
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));
        assertTrue(session.voidTransaction().get().isSuccess());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(1, requests.size(), "the reversed movement must not run again");
        assertEquals("RebateRefund", requests.get(0).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
    }

    @Test
    void abortWithIncompleteRollbackSettlesFailedSoTheUnwindCanFinish() throws Exception {
        identifyMember();
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(REBATE_OK));             // rebate commits
        server.enqueue(new MockResponse().setBody(REDEEM_OK));             // redemption commits
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED)); // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));     // rebate refund

        SessionException failure = assertThrows(SessionException.class, () ->
                session.settle()
                        .onPointsRedeemed(points -> {
                            session.abort().executeSync();               // customer cancelled mid-payment
                            return points.getSuggestedTotal();
                        })
                        .get());

        assertEquals(SessionErrorCode.ABORTED, failure.getError().getCode());
        assertTrue(failure.getError().getMessage().contains("POINT_REDEMPTION"),
                failure.getError().getMessage());
        assertEquals(4, drainRequests().size());

        // the session remains available to finish the unwind
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));
        assertTrue(session.voidTransaction().get().isSuccess());
        assertEquals("RedemptionRefund", drainRequests().get(0).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
    }

    /** Fails a payment so that one rollback leg (the rebate refund) stands. */
    private void failPaymentWithStandingRebate() throws Exception {
        identifyMember();
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(REBATE_OK));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));
        server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));     // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED)); // rebate refund FAILS
        assertThrows(SessionException.class, () -> session.settle().get());
        drainRequests();
    }

    @Test
    void emptyBasketCannotPayFromAFailedSessionWithStandingMovements() throws Exception {
        failPaymentWithStandingRebate();

        // Create the flow while items exist, then empty the basket. The
        // concrete rollback progress remains available independently.
        SettlementFlow flow = session.settle();
        session.basket().removeItemBySku("SKU-1");
        assertEquals(0, session.basket().snapshot().getItemCount());

        // execute-time: the lazy flow must not run a zero-amount checkout
        int requestsBefore = server.getRequestCount();
        SessionException failure = assertThrows(SessionException.class, flow::get);
        assertEquals(SessionErrorCode.INVALID_STATE, failure.getError().getCode());
        assertEquals(requestsBefore, server.getRequestCount(),
                "nothing may reach the wire");

        // the standing movement is untouched and the void still finishes it
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));
        assertTrue(session.voidTransaction().get().isSuccess());
    }

    @Test
    void refundDuringRecoveryDoesNotBlockVoidingANewPayment() throws Exception {
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
        assertThrows(SessionException.class, () -> session.settle().get());
        drainRequests();

        // a return processed while the checkout is in recovery
        server.enqueue(new MockResponse().setBody(paymentOk("POI-REF-1", 25.00)));
        assertTrue(session.refundUnlinked(new BigDecimal("25.00")).get().isSuccess());
        drainRequests();

        // the retry completes a NEW payment — the session-fallback void
        // target is replaced, so the earlier refund must not pin it
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-2", 100.00)));
        assertTrue(session.settle().get().isSuccess());
        drainRequests();

        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));
        assertTrue(session.voidTransaction().get().isSuccess());
        assertEquals("POI-PAY-2", drainRequests().get(0).getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID(),
                "the void reverses the new payment, which no refund has touched");
    }

    @Test
    void basketEditKeepsFailedWhileTheRollbackIsIncomplete() throws Exception {
        failPaymentWithStandingRebate();

        session.basket().updateItemQuantityBySku("SKU-1", 2);

        // the void still finishes the unwind
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));
        assertTrue(session.voidTransaction().get().isSuccess());
    }

    @Test
    void abortWithNoOperationInFlightLeavesStandingMovementsToTheVoid() throws Exception {
        failPaymentWithStandingRebate();

        int requestsBefore = server.getRequestCount();
        session.abort().executeSync();

        // Abort is operation-scoped: with nothing in flight it does not
        // drain recovery progress. Finishing the unwind is the job of
        // voidTransaction() (or a retried settle())
        assertEquals(requestsBefore, server.getRequestCount(),
                "an abort with nothing in flight sends nothing");

        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));
        assertTrue(session.voidTransaction().get().isSuccess());
    }

    @Test
    void retriedPayFinishesStandingReversalsBeforeCharging() throws Exception {
        identifyMember();
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(REBATE_OK));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));
        server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));     // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED)); // rebate refund FAILS
        assertThrows(SessionException.class, () -> session.settle().get());
        drainRequests();

        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));     // standing rebate refund
        server.enqueue(new MockResponse().setBody(REBATE_OK));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-2", 85.00)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));

        assertTrue(session.settle().get().isSuccess());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(5, requests.size());
        assertEquals("RebateRefund", requests.get(0).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue(),
                "the retry must finish the standing reversal before charging anew");
        assertEquals("Rebate", requests.get(1).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
    }

    @Test
    void retriedPayRefusesToStartOverAStillStandingMovement() throws Exception {
        identifyMember();
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(REBATE_OK));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));
        server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED));
        assertThrows(SessionException.class, () -> session.settle().get());
        drainRequests();

        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED)); // still failing
        SessionException failure = assertThrows(SessionException.class,
                () -> session.settle().get());
        assertTrue(failure.getError().getMessage().contains("retry did not start"),
                failure.getError().getMessage());
        assertEquals(1, drainRequests().size(),
                "no new charge may start over a standing movement");

        // the standing movement is retained: the void can still finish it
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));
        assertTrue(session.voidTransaction().get().isSuccess());
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

        SettlementResult result = session.settle().get();

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
    void cartLevelProrationNeverProducesANegativeLineRebate() throws Exception {
        // 7 equal lines sharing a 0.05 rebate: rounding each share against
        // the FULL amount yields 6 x 0.01 = 0.06 and a -0.01 final share
        String tinyGlobalRebate =
                "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},"
                        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-RB-1\","
                        + "\"TimeStamp\":\"2026-07-20T10:00:01Z\"}},"
                        + "\"LoyaltyResult\":[{\"Rebates\":{\"TotalRebate\":0.05,"
                        + "\"RebateLabel\":\"Nickel Off\"}}]}}}";

        identifyMember();
        for (int i = 1; i <= 7; i++) {
            session.basket().addItem(BasketItem.sale("SKU-" + i, "Item " + i, 1, "10.00"));
        }
        server.enqueue(new MockResponse().setBody(tinyGlobalRebate));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));                     // -5.00
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 64.95)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));

        SettlementResult result = session.settle()
                .onRebatesRedeemed(rebates -> {
                    BigDecimal lineSum = BigDecimal.ZERO;
                    for (var line : rebates.getUpdatedBasket().getItems()) {
                        assertTrue(line.getRebateAmount().signum() >= 0,
                                line.getItemId() + " got a negative rebate: "
                                        + line.getRebateAmount());
                        assertTrue(line.getAdjustedTotal()
                                        .compareTo(line.getOriginalTotal()) <= 0,
                                "a rebate must never raise a line's total");
                        lineSum = lineSum.add(line.getRebateAmount());
                    }
                    assertEquals(0, new BigDecimal("0.05").compareTo(lineSum),
                            "the line shares must sum exactly to the cart-level rebate");
                    return rebates.getSuggestedTotal();  // 69.95
                })
                .get();

        assertTrue(result.isSuccess());
        assertEquals(0, new BigDecimal("64.95").compareTo(result.getCardAmountCharged()));
    }

    @Test
    void voidOfLoyaltyPaidCheckoutRefundsRedemptionAndRebate() throws Exception {
        identifyMember();
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(REBATE_OK));                    // -10.00
        server.enqueue(new MockResponse().setBody(REDEEM_OK));                    // -5.00
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 85.00)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));
        session.settle().executeSync();
        drainRequests();

        server.enqueue(new MockResponse().setBody(REVERSAL_OK));         // card leg
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // rebate refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // award refund

        assertTrue(session.voidTransaction().get().isSuccess());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(4, requests.size(),
                "a void must return the redeemed points and rebates, not just the tender");
        assertEquals("Reversal", requests.get(0).getMessageHeader()
                .getMessageCategory().toValue());
        assertEquals("RedemptionRefund", requests.get(1).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("POI-RD-1", requests.get(1).getLoyaltyRequest().getLoyaltyTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
        assertNull(requests.get(1).getLoyaltyRequest().getSaleData().getSaleToPOIData(),
                "per the reversal contract the original transaction reference suffices");
        assertEquals("RebateRefund", requests.get(2).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("POI-RB-1", requests.get(2).getLoyaltyRequest().getLoyaltyTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
        assertEquals("AwardRefund", requests.get(3).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
    }

    @Test
    void failedLoyaltyRefundDoesNotFailThePaidVoid() throws Exception {
        identifyMember();
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(REBATE_OK));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 85.00)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));
        session.settle().executeSync();
        drainRequests();

        server.enqueue(new MockResponse().setBody(REVERSAL_OK));             // card leg
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED));   // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));       // rebate refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));       // award refund

        assertTrue(session.voidTransaction().get().isSuccess(),
                "the tender was reversed; a loyalty refund failure is best-effort (SAF)");
        assertEquals(4, drainRequests().size(),
                "the remaining loyalty refunds still run after one fails");
    }

    @Test
    void rewardOnlyCheckoutVoidsViaLoyaltyRefunds() throws Exception {
        completeRewardOnlyCheckout();

        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // rebate refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // award refund

        assertTrue(session.voidTransaction().get().isSuccess());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(3, requests.size());
        assertEquals("RedemptionRefund", requests.get(0).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("POI-RD-1", requests.get(0).getLoyaltyRequest().getLoyaltyTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
        assertNull(requests.get(0).getLoyaltyRequest().getSaleData().getSaleToPOIData(),
                "per the reversal contract the original transaction reference suffices");
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
        assertEquals(2, drainRequests().size());

        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // rebate refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // award refund

        assertTrue(session.voidTransaction().get().isSuccess());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(2, requests.size(), "the redemption leg must not be re-credited");
        assertEquals("RebateRefund", requests.get(0).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("AwardRefund", requests.get(1).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
    }

    // ─── Cross-session (referenced) reversal from a persisted result ───

    @Test
    void persistedResultVoidsEveryLegFromCheckoutSessionOriginalSaleRecord() throws Exception {
        // the original sale: rebate + redemption + card + award
        identifyMember();
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(REBATE_OK));                    // -10.00
        server.enqueue(new MockResponse().setBody(REDEEM_OK));                    // -5.00
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 85.00)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));
        SettlementResult sale = session.settle().get();
        drainRequests();

        // a later process: a checkout session built only from what the POS
        // persisted
        CheckoutSession later = start(sessionBuilder());

        server.enqueue(new MockResponse().setBody(REVERSAL_OK));         // card leg
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // rebate refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));   // award refund
        assertTrue(later.voidTransaction(sale.toOriginalSaleRecord("98234")).get().isSuccess());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(4, requests.size(),
                "every persisted movement must be reversed: tender, redemption, rebate, award");
        assertEquals("POI-PAY-1", requests.get(0).getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
        assertEquals("RedemptionRefund", requests.get(1).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("POI-RD-1", requests.get(1).getLoyaltyRequest().getLoyaltyTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
        assertEquals("RebateRefund", requests.get(2).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("POI-RB-1", requests.get(2).getLoyaltyRequest().getLoyaltyTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
        assertEquals("AwardRefund", requests.get(3).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("POI-AW-1", requests.get(3).getLoyaltyRequest().getLoyaltyTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
    }

    @Test
    void originalSaleRecordVoidRequiresAReference() {
        SessionException failure = assertThrows(SessionException.class,
                () -> session.voidTransaction(OriginalSaleRecord.builder().build()).get());

        assertEquals(SessionErrorCode.INVALID_STATE, failure.getError().getCode());
        assertTrue(failure.getError().getMessage().contains(
                "at least one original transaction reference"));
        assertEquals(1, server.getRequestCount(), "only the session start may hit the wire");
    }

    @Test
    void originalSaleRecordVoidRejectsTheMostRecentSessionPayment() throws Exception {
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
        SettlementResult sale = session.settle().get();
        drainRequests();

        SessionException referencedVoid = assertThrows(SessionException.class,
                () -> session.voidTransaction(sale.toOriginalSaleRecord(null)).get());
        assertEquals(SessionErrorCode.INVALID_STATE, referencedVoid.getError().getCode());
        assertTrue(referencedVoid.getError().getMessage().contains(
                "parameterless voidTransaction()"));
        assertEquals(0, drainRequests().size(),
                "a referenced void must not bypass the latest payment's guards");

        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
        assertTrue(session.voidTransaction().get().isSuccess());
        drainRequests();

        SessionException refundRefused = assertThrows(SessionException.class,
                () -> session.refund(new BigDecimal("10.00")).get());
        assertEquals(SessionErrorCode.INVALID_STATE, refundRefused.getError().getCode());
        assertEquals(0, drainRequests().size(),
                "the shared guard must prevent refunding the voided payment");
    }

    @Test
    void originalSaleRecordVoidCanTargetAnOlderSettlementInTheSameSession() throws Exception {
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
        SettlementResult olderSale = session.settle().get();
        drainRequests();

        session.basket().clear();
        session.basket().addItem(BasketItem.sale("SKU-2", "Item", 1, "25.00"));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-2", 25.00)));
        session.settle().get();
        drainRequests();

        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
        assertTrue(session.voidTransaction(
                olderSale.toOriginalSaleRecord(null)).get().isSuccess());

        SaleToPOIRequest reversal = nextRequest();
        assertEquals("POI-PAY-1", reversal.getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
    }

    @Test
    void originalSaleRecordVoidCanRunAlongsideAnUnsettledBasket() throws Exception {
        session.basket().addItem(BasketItem.sale("SKU-1", "Item", 1, "10.00"));
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));

        VoidResult result = session.voidTransaction(OriginalSaleRecord.builder()
                .cardPoiTransactionId(PRIOR_CARD_POI_TXN)
                .cardPoiTransactionTimestamp(ORIGINAL_TIME)
                .build()).get();

        assertTrue(result.isSuccess());
        assertEquals(1, session.basket().snapshot().getItemCount(),
                "a prior-sale void must not consume the current basket");
        assertEquals(PRIOR_CARD_POI_TXN, nextRequest().getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
    }

    @Test
    void originalSaleRecordStoredValueOnlyVoidReversesGiftCardLeg() throws Exception {
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));

        VoidResult result = session.voidTransaction(OriginalSaleRecord.builder()
                .storedValuePoiTransactionId(PRIOR_STORED_VALUE_POI_TXN)
                .storedValuePoiTransactionTimestamp(ORIGINAL_TIME)
                .build()).get();

        assertTrue(result.isSuccess());

        SaleToPOIRequest reversal = nextRequest();
        assertEquals("Reversal", reversal.getMessageHeader().getMessageCategory().toValue());
        assertEquals(PRIOR_STORED_VALUE_POI_TXN, reversal.getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
        assertEquals(2, server.getRequestCount(), "the start plus the reversal");
    }

    @Test
    void originalSaleRecordRedemptionOnlyVoidOmitsLoyaltyDataWithoutMember() throws Exception {
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));

        VoidResult result = session.voidTransaction(OriginalSaleRecord.builder()
                .redemptionPoiTransactionId(PRIOR_REDEMPTION_POI_TXN)
                .redemptionPoiTransactionTimestamp(ORIGINAL_TIME)
                .build()).get();

        assertTrue(result.isSuccess());
        SaleToPOIRequest redemptionRefund = nextRequest();
        assertEquals("RedemptionRefund", redemptionRefund.getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals(PRIOR_REDEMPTION_POI_TXN, redemptionRefund.getLoyaltyRequest()
                .getLoyaltyTransaction().getOriginalPOITransaction()
                .getPoiTransactionID().getTransactionID());
        assertNull(redemptionRefund.getLoyaltyRequest().getLoyaltyData(),
                "without memberId the original transaction reference suffices");
    }

    @Test
    void originalSaleRecordRebateOnlyVoidIncludesMemberWhenKnown() throws Exception {
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));

        assertTrue(session.voidTransaction(OriginalSaleRecord.builder()
                .rebatePoiTransactionId(PRIOR_REBATE_POI_TXN)
                .rebatePoiTransactionTimestamp(ORIGINAL_TIME)
                .memberId("98234")
                .build()).get().isSuccess());

        SaleToPOIRequest rebateRefund = nextRequest();
        assertEquals("RebateRefund", rebateRefund.getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("98234", rebateRefund.getLoyaltyRequest().getLoyaltyData()[0]
                .getLoyaltyAccountID().getLoyaltyID());
    }

    @Test
    void originalSaleRecordGiftCardOnlySaleIsReversedOnce() throws Exception {
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));

        assertTrue(session.voidTransaction(OriginalSaleRecord.builder()
                .cardPoiTransactionId(PRIOR_STORED_VALUE_POI_TXN)
                .cardPoiTransactionTimestamp(ORIGINAL_TIME)
                .storedValuePoiTransactionId(PRIOR_STORED_VALUE_POI_TXN)
                .storedValuePoiTransactionTimestamp(ORIGINAL_TIME)
                .build()).get().isSuccess());

        assertEquals(2, server.getRequestCount(),
                "one original POI transaction, one reversal, plus session start");
        SaleToPOIRequest reversal = nextRequest();
        assertEquals(PRIOR_STORED_VALUE_POI_TXN, reversal.getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
    }

    @Test
    void originalSaleRecordVoidRetryResumesAfterPartialFailure() throws Exception {
        OriginalSaleRecord originalSale = OriginalSaleRecord.builder()
                .cardPoiTransactionId(PRIOR_CARD_POI_TXN)
                .cardPoiTransactionTimestamp(ORIGINAL_TIME)
                .storedValuePoiTransactionId(PRIOR_STORED_VALUE_POI_TXN)
                .storedValuePoiTransactionTimestamp(ORIGINAL_TIME)
                .build();

        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
        server.enqueue(new MockResponse().setBody(REVERSAL_UNREACHABLE));

        SessionException failure = assertThrows(SessionException.class,
                () -> session.voidTransaction(originalSale).get());
        assertTrue(failure.getError().getMessage().contains(
                PRIOR_CARD_POI_TXN + " was reversed"));
        assertTrue(failure.getError().getMessage().contains(
                PRIOR_STORED_VALUE_POI_TXN + " was not"));

        List<SaleToPOIRequest> firstAttempt = drainRequests();
        assertEquals(2, firstAttempt.size());
        assertEquals(PRIOR_CARD_POI_TXN, firstAttempt.get(0).getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
        assertEquals(PRIOR_STORED_VALUE_POI_TXN, firstAttempt.get(1).getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());

        SessionException endRefused = assertThrows(SessionException.class,
                () -> session.end().get());
        assertEquals(SessionErrorCode.INVALID_STATE, endRefused.getError().getCode());
        assertTrue(endRefused.getError().getMessage().contains(
                "voidTransaction(OriginalSaleRecord)"));
        assertEquals(0, drainRequests().size(),
                "end must not discard prior-sale void resume progress");

        assertDoesNotThrow(session::close,
                "best-effort close must leave the guarded session open");
        assertEquals(0, drainRequests().size(),
                "close must inherit end's prior-sale progress guard");

        SessionException wrongSale = assertThrows(SessionException.class,
                () -> session.voidTransaction(OriginalSaleRecord.builder()
                        .cardPoiTransactionId("POI-CARD-OTHER")
                        .cardPoiTransactionTimestamp(ORIGINAL_TIME)
                        .build()).get());
        assertEquals(SessionErrorCode.INVALID_STATE, wrongSale.getError().getCode());
        assertTrue(wrongSale.getError().getMessage().contains("another prior sale"));
        assertEquals(0, drainRequests().size(),
                "a different prior sale must not consume the saved retry progress");

        server.enqueue(new MockResponse().setBody(REVERSAL_OK));

        VoidResult retried = session.voidTransaction(originalSale).get();

        assertTrue(retried.isSuccess());

        List<SaleToPOIRequest> retry = drainRequests();
        assertEquals(1, retry.size());
        assertEquals(PRIOR_STORED_VALUE_POI_TXN, retry.get(0).getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID(),
                "retry must not reverse the already-completed card leg again");

        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.ADMIN_OK));
        assertDoesNotThrow(() -> session.end().get(),
                "end is allowed after the prior-sale void finishes");
    }

    @Test
    void finalBasketReflectsHandlerRecalculatedTax() throws Exception {
        identifyMember();
        session.basket().addItem(BasketItem.sale("SKU-1", "Item", 1, "100.00"));
        session.basket().setTaxRateBySku("SKU-1", new BigDecimal("0.08"));  // grand 108.00

        server.enqueue(new MockResponse().setBody(REBATE_OK));                    // -10.00
        server.enqueue(new MockResponse().setBody(REDEEM_OK));                    // -5.00
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 92.20)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));

        SettlementResult result = session.settle()
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

        session.settle()
                .beforeStep(ctx -> "TXN-" + ctx.getStep())
                .executeSync();

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals("TXN-" + SettlementStep.REBATE_REDEMPTION, requests.get(0).getLoyaltyRequest()
                .getSaleData().getSaleTransactionID().getTransactionID());
        assertEquals("TXN-" + SettlementStep.CARD_CHARGE, requests.get(2).getPaymentRequest()
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

        SettlementResult result = session.settle()
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
        SettlementResult result = session.settle()
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

        SettlementResult result = session.settle().get();

        assertEquals(0, new BigDecimal("100.00").compareTo(result.getStoredValueAmountUsed()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getCardAmountCharged()));
        assertEquals("POI-GC-1", result.getPoiTransactionId(),
                "the gift card payment's reference must reach the result");
        assertEquals("APPR7", result.getApprovalCode());
        assertEquals(1, drainRequests().size(), "no card payment when the gift card covers it");

        // and the checkout is voidable via that reference
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
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
        session.settle().onError(error -> {
            seen.set(error);
            return SettlementRecovery.abort();
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
        session.settle().onError(error -> {
            seen.set(error);
            return SettlementRecovery.abort();
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
        SettlementFlow flow = session.settle().onError(error -> {
            seen.set(error);
            return SettlementRecovery.abort();
        });

        assertNull(flow.getOrNull());
        assertEquals(SessionErrorCode.DECLINED, seen.get().getCode());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(5, requests.size());
        assertEquals("RedemptionRefund", requests.get(3).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("POI-RD-1", requests.get(3).getLoyaltyRequest().getLoyaltyTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
        assertNull(requests.get(3).getLoyaltyRequest().getSaleData().getSaleToPOIData(),
                "per the reversal contract the original transaction reference suffices");
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

        SettlementResult result = session.settle()
                .onError(error -> {
                    assertEquals(SessionErrorCode.LOYALTY_UNAVAILABLE, error.getCode());
                    return SettlementRecovery.retryWithoutLoyalty();
                })
                .get();

        assertTrue(result.isSuccess());
        assertEquals(0, new BigDecimal("100.00").compareTo(result.getCardAmountCharged()));

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(2, requests.size());
        assertNotNull(requests.get(1).getPaymentRequest(), "retry goes straight to card payment");
    }

    @Test
    void retryLimitForcesFailure() throws Exception {
        addHundredDollarItem();
        for (int i = 0; i < 4; i++) {
            server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
        }

        AtomicInteger errorCalls = new AtomicInteger();
        SettlementFlow flow = session.settle().onError(error -> {
            errorCalls.incrementAndGet();
            return SettlementRecovery.retry();  // always retry
        });

        SessionException failure = assertThrows(SessionException.class, flow::get);
        assertTrue(failure.getError().getMessage().contains("retry limit"),
                failure.getError().getMessage());
        assertEquals(4, errorCalls.get(),
                "3 consultations plus the notification that the last retry was refused");
        assertEquals(4, server.getRequestCount(), "retry cap stops the loop (plus the session start)");
    }

    @Test
    void staleAbortFlagDoesNotKillAPaymentRetry() throws Exception {
        addHundredDollarItem();

        // abort() races a decline: the payment failure completes on its own
        server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
        session.settle().onError(error -> {
            session.abort().executeSync();  // deferred: the payment thread owns the outcome
            return SettlementRecovery.abort();
        }).getOrNull();

        // the leftover flag must not abort the legitimate retry
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-2", 100.00)));
        assertTrue(session.settle().get().isSuccess());
    }

    @Test
    void basketCanBeAdjustedAfterAFailedPayment() throws Exception {
        addHundredDollarItem();
        session.basket().addItem(BasketItem.sale("SKU-2", "Expensive Item", 1, "50.00"));
        server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
        session.settle().getOrNull();

        // the customer drops an item and the register retries
        session.basket().removeItemBySku("SKU-2");

        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-2", 100.00)));
        SettlementResult result = session.settle().get();
        assertEquals(0, new BigDecimal("100.00").compareTo(result.getCardAmountCharged()));
    }

    @Test
    void basketCanBeEmptiedAfterAFailedPayment() throws Exception {
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
        session.settle().getOrNull();

        session.basket().removeItemBySku("SKU-1");
    }

    @Test
    void failedPaymentCanBeRetriedWithNewPayCall() throws Exception {
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
        session.settle().getOrNull();

        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-2", 100.00)));
        SettlementResult result = session.settle().get();

        assertTrue(result.isSuccess());
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
        SettlementFlow flow = session.settle().onError(error -> {
            seen.set(error);
            return SettlementRecovery.abort();
        });

        assertNull(flow.getOrNull());
        assertEquals(SessionErrorCode.DECLINED, seen.get().getCode());
        assertTrue(seen.get().getMessage().contains("60.00"));
        assertTrue(seen.get().getMessage().contains("100.00"));

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
        SettlementFlow flow = session.settle()
                .onRebatesRedeemed(rebates -> {
                    throw new NullPointerException("register bug");
                })
                .onError(error -> {
                    seen.set(error);
                    return SettlementRecovery.abort();
                });

        assertNull(flow.getOrNull());
        assertEquals(SessionErrorCode.UNKNOWN, seen.get().getCode());
        assertTrue(seen.get().getCause() instanceof NullPointerException);

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(2, requests.size());
        assertEquals("RebateRefund", requests.get(1).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue(),
                "the committed rebate must be unwound");

        // and the register can retry once the bug is out of the way —
        // retryWithoutLoyalty disables rebates and points but not the
        // award, which still runs for the identified member
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-2", 100.00)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));
        assertTrue(session.settle(SettlementOptions.builder()
                .disableRebates(true)
                .disablePoints(true)
                .build()).get().isSuccess());
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

        AtomicReference<SettlementResult> success = new AtomicReference<>();
        session.settle()
                .onSuccess(success::set)
                .onError(error -> {
                    fail("award failure must not reach onError");
                    return SettlementRecovery.abort();
                })
                .executeSync();

        assertNotNull(success.get());
        assertEquals(0, success.get().getTotalPointsEarned());
        assertFalse(success.get().getWarnings().isEmpty());
    }

    @Test
    void declinedGuestPaymentCanIdentifyBeforeRetryingWithLoyalty() throws Exception {
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
        assertThrows(SessionException.class, () -> session.settle().get());
        drainRequests();

        // the register attaches the member after the failed settlement, then
        // retries the payment with the loyalty steps enabled
        identifyMember();
        assertNotNull(session.getMember());

        server.enqueue(new MockResponse().setBody(REBATE_OK));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-2", 85.00)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));

        SettlementResult result = session.settle().get();

        assertEquals(0, new BigDecimal("10.00").compareTo(result.getTotalRebateAmount()));
        assertEquals(0, new BigDecimal("5.00").compareTo(result.getPointsMonetaryValue()));

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(4, requests.size(), "rebate, redemption, payment, award");
        assertEquals("Rebate", requests.get(0).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
    }

    @Test
    void disableAwardSkipsTheAwardStepButKeepsRedemptions() throws Exception {
        identifyMember();
        addHundredDollarItem();

        server.enqueue(new MockResponse().setBody(REBATE_OK));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 85.00)));

        SettlementResult result = session.settle(SettlementOptions.builder()
                .disableAward(true).build()).get();

        assertEquals(0, result.getTotalPointsEarned());
        assertNull(result.getAwardPoiTransactionId());
        assertEquals(0, new BigDecimal("10.00").compareTo(result.getTotalRebateAmount()));
        assertEquals(0, new BigDecimal("5.00").compareTo(result.getPointsMonetaryValue()));

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(3, requests.size(), "rebate, redemption, payment — no award");
        assertEquals("Rebate", requests.get(0).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("Redemption", requests.get(1).getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertNotNull(requests.get(2).getPaymentRequest());
    }

    // ─── Abort mid-flow ───

    @Test
    void abortUnwindsButLeavesTheSessionRetryable() throws Exception {
        identifyMember();
        addHundredDollarItem();

        server.enqueue(new MockResponse().setBody(REBATE_OK));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));  // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));  // rebate refund

        SettlementFlow flow = session.settle()
                .onPointsRedeemed(points -> {
                    session.abort().executeSync();  // register cancels this payment attempt
                    return points.getSuggestedTotal();
                });

        SessionException e = assertThrows(SessionException.class, flow::get);
        assertEquals(SessionErrorCode.ABORTED, e.getError().getCode());
        drainRequests();

        // the same session retries and completes
        server.enqueue(new MockResponse().setBody(REBATE_OK));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-2", 85.00)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));

        SettlementResult result = session.settle().get();

        assertTrue(result.isSuccess());
        assertEquals(4, drainRequests().size(), "rebate, redemption, payment, award");
    }

    @Test
    void abortInsideHandlerUnwindsInReverseOrder() throws Exception {
        identifyMember();
        addHundredDollarItem();

        server.enqueue(new MockResponse().setBody(REBATE_OK));
        server.enqueue(new MockResponse().setBody(REDEEM_OK));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));  // redemption refund
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));  // rebate refund

        SettlementFlow flow = session.settle()
                .onPointsRedeemed(points -> {
                    session.abort().executeSync();  // customer walked away
                    // abort() defers the settlement outcome to its operation
                    // thread so this callback cannot race finalization
                    return points.getSuggestedTotal();
                })
                .onError(error -> {
                    fail("abort must not reach onError");
                    return null;
                });

        SessionException e = assertThrows(SessionException.class, flow::get);
        assertEquals(SessionErrorCode.ABORTED, e.getError().getCode());

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

        SettlementFlow flow = session.settle()
                .beforeStep(ctx -> {
                    if (ctx.getStep() == SettlementStep.AWARD) {
                        session.abort().executeSync();  // lands while the award is being submitted
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

        SettlementFlow flow = session.settle()
                .beforeStep(ctx -> {
                    session.abort().executeSync();  // lands while the card request is in flight
                    return ctx.getDefaultTransactionId();
                })
                .onError(error -> {
                    fail("an intentional abort must not reach onError");
                    return null;
                });

        SessionException e = assertThrows(SessionException.class, flow::get);
        assertEquals(SessionErrorCode.ABORTED, e.getError().getCode());
    }

    @Test
    void terminalInitiatedAbortStillReachesOnError() throws Exception {
        addHundredDollarItem();
        // no abort() from us: the terminal aborted the payment on its own
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Aborted\"}}}}"));

        AtomicReference<SessionError> seen = new AtomicReference<>();
        session.settle().onError(error -> {
            seen.set(error);
            return SettlementRecovery.abort();
        }).getOrNull();

        assertEquals(SessionErrorCode.ABORTED, seen.get().getCode(),
                "a spontaneous terminal abort is surfaced to the register via onError");
    }

    // ─── Post-payment void uses the payment's references ───

    @Test
    void refundAfterCompletedPaymentUsesLastTransactionReference() throws Exception {
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
        session.settle().executeSync();
        drainRequests();

        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},"
                        + "\"PaymentResult\":{\"AmountsResp\":{\"AuthorizedAmount\":25.00}}}}}"));

        RefundResult refund = session.refund(new BigDecimal("25.00")).get();

        assertTrue(refund.isSuccess());
        SaleToPOIRequest sent = nextRequest();
        assertEquals("Refund", sent.getPaymentRequest().getPaymentData().getPaymentType().toValue());
        assertEquals("POI-PAY-1", sent.getPaymentRequest().getPaymentTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
    }

    @Test
    void refundAfterReidentifyUsesTheSettlementMember() throws Exception {
        identifyMember();
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));
        session.settle(SettlementOptions.builder()
                .disableRebates(true).disablePoints(true).build()).executeSync();
        drainRequests();

        session.basket().clear();
        identifyMember("56789");
        assertEquals("56789", session.getMember().getMemberId());

        server.enqueue(new MockResponse().setBody(CheckoutSessionTest.refundOk(25.00)));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));
        assertTrue(session.refund(new BigDecimal("25.00")).get().isSuccess());

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(2, requests.size());
        assertEquals("98234", requests.get(1).getLoyaltyRequest().getLoyaltyData()[0]
                .getLoyaltyAccountID().getLoyaltyID(),
                "the award refund belongs to the member on the settled payment");
    }

    @Test
    void refundWithSkippedTenderLeavesThePaymentVoidable() throws Exception {
        identifyMember();
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));  // POI-AW-1
        session.settle(SettlementOptions.builder()
                .disableRebates(true).disablePoints(true).build()).executeSync();
        drainRequests();

        // the tender refund is declined and the register skips it; only
        // the award is reversed — no money moved
        server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));
        assertTrue(session.refund(new BigDecimal("10.00"))
                .onError((step, error) -> step == ReversalStep.CARD
                        ? ReversalDecision.SKIP : ReversalDecision.ABORT)
                .get().isSuccess());
        drainRequests();

        // no money moved, so the payment stays voidable — and the void
        // reverses the card leg without re-crediting the reversed award
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
        assertTrue(session.voidTransaction().get().isSuccess());
        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(1, requests.size(),
                "one reversal, no second AwardRefund for the already-reversed award");
        assertEquals("POI-PAY-1", requests.get(0).getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
    }

    @Test
    void abortedAwardReversalAfterTheTenderRefundStillRaisesTheVoidGuard() throws Exception {
        identifyMember();
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));  // POI-AW-1
        session.settle(SettlementOptions.builder()
                .disableRebates(true).disablePoints(true).build()).executeSync();
        drainRequests();

        server.enqueue(new MockResponse().setBody(
                CheckoutSessionTest.refundOk(25.00)));                       // money moved
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_FAILED));   // award fails
        assertThrows(SessionException.class, () -> session.refund(new BigDecimal("25.00"))
                .onError((step, error) -> ReversalDecision.ABORT)
                .get());
        drainRequests();  // the tender refund and the failed award reversal

        // the refund flow aborted AFTER the tender refund completed: the
        // money moved, so a void would return the full amount on top of it
        SessionException e = assertThrows(SessionException.class,
                () -> session.voidTransaction().get());
        assertEquals(SessionErrorCode.INVALID_STATE, e.getError().getCode());
        assertTrue(e.getError().getMessage().contains("refund"),
                "the error must steer the register to further refunds: "
                        + e.getError().getMessage());
        assertEquals(0, drainRequests().size(), "the rejected void must not reach the wire");
    }

    @Test
    void failedVoidAfterPaymentDoesNotAllowRePayment() throws Exception {
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
        session.settle().executeSync();
        drainRequests();

        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"UnreachableHost\"}}}}"));
        assertThrows(SessionException.class, () -> session.voidTransaction().get());

        // the payment still stands: no second charge may be authorized
        assertEquals(SessionErrorCode.INVALID_STATE, assertThrows(SessionException.class,
                () -> session.settle().get()).getError().getCode());

        // but the void can be retried
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
        assertTrue(session.voidTransaction().get().isSuccess());
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
        session.settle().executeSync();
        drainRequests();

        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},\"ReversedAmount\":65.00}}}"));
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},\"ReversedAmount\":35.00}}}"));

        VoidResult voided = session.voidTransaction().get();

        assertEquals(0, new BigDecimal("100.00").compareTo(voided.getReversedAmount()),
                "both legs must be reversed");

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(2, requests.size(),
                "a guest checkout has no loyalty movements — two reversals only");
        assertEquals("POI-PAY-1", requests.get(0).getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
        assertEquals("POI-GC-1", requests.get(1).getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
    }

    @Test
    void voidOfGiftCardOnlyCheckoutSendsASingleReversal() throws Exception {
        addHundredDollarItem();
        session.setStoredValueCard("GC-1234-5678");
        server.enqueue(new MockResponse().setBody(paymentOk("POI-GC-1", 100.00)));
        session.settle().executeSync();
        drainRequests();

        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
        session.voidTransaction().executeSync();

        List<SaleToPOIRequest> requests = drainRequests();
        assertEquals(1, requests.size(), "one transaction, one reversal — no duplicate");
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
        session.settle().executeSync();
        drainRequests();

        server.enqueue(new MockResponse().setBody(REVERSAL_OK));  // card leg reversed
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"UnreachableHost\"}}}}"));

        SessionException e = assertThrows(SessionException.class,
                () -> session.voidTransaction().get());
        assertTrue(e.getError().getMessage().contains("POI-PAY-1 was reversed"));
        assertTrue(e.getError().getMessage().contains("POI-GC-1 was not"));
        drainRequests();

        IllegalStateException clearRefused = assertThrows(IllegalStateException.class,
                () -> session.basket().clear());
        assertTrue(clearRefused.getMessage().contains("voidTransaction"),
                "the error must steer the register to resuming the void: "
                        + clearRefused.getMessage());

        SessionException endRefused = assertThrows(SessionException.class,
                () -> session.end().get());
        assertEquals(SessionErrorCode.INVALID_STATE, endRefused.getError().getCode());
        assertTrue(endRefused.getError().getMessage().contains("voidTransaction"),
                "the session must retain the in-memory void progress: "
                        + endRefused.getError().getMessage());
        assertEquals(0, drainRequests().size(),
                "clear and end guards must not reach the wire");

        // the card leg is already reversed: a refund against it would
        // double-return the money, so refunds are refused mid-void
        SessionException refundRefused = assertThrows(SessionException.class,
                () -> session.refund(new BigDecimal("10.00")).get());
        assertEquals(SessionErrorCode.INVALID_STATE, refundRefused.getError().getCode());
        assertTrue(refundRefused.getError().getMessage().contains("voidTransaction"),
                "the error must steer the register to finishing the void: "
                        + refundRefused.getError().getMessage());
        assertEquals(0, drainRequests().size(), "the refused refund must not reach the wire");

        // the retry resumes at the outstanding gift card leg — the card
        // payment must not be reversed a second time
        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
        assertTrue(session.voidTransaction().get().isSuccess());

        List<SaleToPOIRequest> retryRequests = drainRequests();
        assertEquals(1, retryRequests.size());
        assertEquals("POI-GC-1", retryRequests.get(0).getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID(),
                "only the stored value leg remains to reverse");

        assertTrue(session.basket().clear().isEmpty(),
                "the basket can be replaced after the void finishes");
    }

    @Test
    void settlementCannotReplaceAPartiallyVoidedPayment() throws Exception {
        addHundredDollarItem();
        session.setStoredValueCard("GC-1234-5678");
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
                        + "\"Response\":{\"Result\":\"Partial\"},"
                        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-GC-1\"}},"
                        + "\"PaymentResult\":{\"AmountsResp\":{\"AuthorizedAmount\":35.00}}}}}"));
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 65.00)));
        session.settle().executeSync();
        drainRequests();

        session.basket().clear();
        addHundredDollarItem();

        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\","
                        + "\"ErrorCondition\":\"UnreachableHost\"}}}}"));
        assertThrows(SessionException.class, () -> session.voidTransaction().get());
        drainRequests();

        SessionException settlementRefused = assertThrows(SessionException.class,
                () -> session.settle().get());
        assertEquals(SessionErrorCode.INVALID_STATE, settlementRefused.getError().getCode());
        assertTrue(settlementRefused.getError().getMessage().contains("voidTransaction"),
                "the error must preserve the partial void target: "
                        + settlementRefused.getError().getMessage());
        assertEquals(0, drainRequests().size(),
                "the replacement settlement must not reach the wire");
    }

    @Test
    void voidAfterLoyaltyCheckoutReversesTheAwardByItsOwnReference() throws Exception {
        identifyMember();
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
        server.enqueue(new MockResponse().setBody(AWARD_OK));  // POI-AW-1
        session.settle(SettlementOptions.builder()
                .disableRebates(true).disablePoints(true).build()).executeSync();
        drainRequests();

        session.basket().clear();
        identifyMember("56789");
        assertEquals("56789", session.getMember().getMemberId());

        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
        server.enqueue(new MockResponse().setBody(LOYALTY_REFUND_OK));
        session.voidTransaction().executeSync();

        List<SaleToPOIRequest> requests = drainRequests();
        SaleToPOIRequest awardRefund = requests.get(1);
        assertEquals("AwardRefund", awardRefund.getLoyaltyRequest()
                .getLoyaltyTransaction().getLoyaltyTransactionType().toValue());
        assertEquals("POI-AW-1", awardRefund.getLoyaltyRequest().getLoyaltyTransaction()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID(),
                "the reversal must reference the award, not the payment");
        assertEquals("98234", awardRefund.getLoyaltyRequest().getLoyaltyData()[0]
                .getLoyaltyAccountID().getLoyaltyID(),
                "the reversal must carry the settled payment's member, not the live member");
    }

    @Test
    void voidAfterPaymentUsesLastTransactionReference() throws Exception {
        addHundredDollarItem();
        server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
        session.settle().executeSync();
        drainRequests();

        server.enqueue(new MockResponse().setBody(REVERSAL_OK));
        VoidResult voided = session.voidTransaction().get();

        assertTrue(voided.isSuccess());
        SaleToPOIRequest reversal = nextRequest();
        assertEquals("POI-PAY-1", reversal.getReversalRequest()
                .getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
    }
}
