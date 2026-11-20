package com.bilt.pos.session;

import static org.junit.jupiter.api.Assertions.*;

import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.settlement.ExternalPayment;
import com.bilt.pos.session.settlement.SettlementFailure;
import com.bilt.pos.session.settlement.SettlementLoyalty;
import com.bilt.pos.session.settlement.SettlementMovement;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.bilt.pos.session.settlement.SettlementRecovery;
import com.bilt.pos.session.settlement.SettlementResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Reversal notices: {@code onMovement} and {@code onCardChargeReversed} for every movement the
 * settlement itself reverses.
 */
class TerminalShopperSessionReversalNotificationTest {

  private static final String REWARDS_B64 =
      java.util.Base64.getEncoder()
          .encodeToString(
              ("{\"rewards\":[{\"rewardRef\":\"rwd:RWD-44021\",\"type\":\"reward\","
                      + "\"name\":\"$10 Off Purchase\"}],\"rewardCount\":1}")
                  .getBytes(java.nio.charset.StandardCharsets.UTF_8));

  private static final String IDENTIFY_OK =
      "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
          + "\"Response\":{\"Result\":\"Success\",\"AdditionalResponse\":\""
          + REWARDS_B64
          + "\"},"
          + "\"LoyaltyAccount\":[{\"LoyaltyAccountID\":{\"LoyaltyID\":\"98234\"},"
          + "\"LoyaltyBrand\":\"K-Club\"}]}}}";

  private static final String REBATE_OK =
      "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
          + "\"Response\":{\"Result\":\"Success\"},"
          + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-RB-1\","
          + "\"TimeStamp\":\"2026-07-20T10:00:01Z\"}},"
          + "\"LoyaltyResult\":[{\"Rebates\":{\"TotalRebate\":10.00,"
          + "\"SaleItemRebate\":[{\"ItemID\":1,\"ProductCode\":\"SKU-1\","
          + "\"ItemAmount\":10.00}]}}]}}}";

  private static final String REDEEM_OK =
      "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
          + "\"Response\":{\"Result\":\"Success\"},"
          + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-RD-1\","
          + "\"TimeStamp\":\"2026-07-20T10:00:02Z\"}},"
          + "\"LoyaltyResult\":[{\"CurrentBalance\":700,"
          + "\"LoyaltyAmount\":{\"AmountValue\":5.00,\"LoyaltyUnit\":\"Monetary\"}}]}}}";

  private static final String AWARD_OK =
      "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
          + "\"Response\":{\"Result\":\"Success\"},"
          + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-AW-1\"}},"
          + "\"LoyaltyResult\":[{\"CurrentBalance\":789,"
          + "\"LoyaltyAmount\":{\"AmountValue\":89,\"LoyaltyUnit\":\"Point\"}}]}}}";

  private static final String PAYMENT_DECLINED =
      "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
          + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Refusal\"}}}}";

  private static final String REVERSAL_UNREACHABLE =
      "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
          + "\"Response\":{\"Result\":\"Failure\","
          + "\"ErrorCondition\":\"UnreachableHost\"}}}}";

  private final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private MockWebServer server;
  private TerminalShopperSession session;
  private final List<SettlementMovement> notices = new ArrayList<>();
  private final List<SettlementMovement> cardReversals = new ArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.ADMIN_OK));
    session =
        TerminalShopperSession.builder()
            .client(
                BiltNexoTerminalClient.builder()
                    .endpoint(server.url("/nexo").toString())
                    .disableRecoveryOnNetworkError()
                    .build())
            .saleId("POS-LANE-3")
            .poiId("VictaLane-275839164")
            .currency("USD")
            .autoDisplay(false)
            .start()
            .get();
    server.takeRequest(5, TimeUnit.SECONDS); // the session-start Admin request
    session.basket().addItem(BasketItem.sale("SKU-1", "Item", 1, new BigDecimal("100.00")));
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  /** A settlement that records every reversal notice it receives. */
  private SettlementFlow settle(SettlementOptions options) {
    return session
        .settle(options)
        .onMovement(
            movement -> {
              if (movement.isReversal()) {
                notices.add(movement);
              }
            })
        .onCardChargeReversed(cardReversals::add);
  }

  private SettlementFlow settle() {
    return settle(SettlementOptions.builder().build());
  }

  private void identifyMember() throws Exception {
    server.enqueue(new MockResponse().setBody(IDENTIFY_OK));
    session.identifyMember().executeSync();
    server.takeRequest(5, TimeUnit.SECONDS);
  }

  private void enqueue(String... bodies) {
    for (String body : bodies) {
      server.enqueue(new MockResponse().setBody(body));
    }
  }

  private List<SaleToPOIRequest> drainRequests() throws Exception {
    List<SaleToPOIRequest> requests = new ArrayList<>();
    RecordedRequest recorded;
    while ((recorded = server.takeRequest(200, TimeUnit.MILLISECONDS)) != null) {
      requests.add(
          mapper
              .readValue(recorded.getBody().readUtf8(), NexoTerminalAPI.class)
              .getSaleToPOIRequest());
    }
    return requests;
  }

  private static String payment(String result, String poiTxn, double authorized) {
    return "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
        + "\"Response\":{\"Result\":\""
        + result
        + "\"},"
        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\""
        + poiTxn
        + "\",\"TimeStamp\":\"2026-07-20T10:00:03Z\"}},"
        + "\"PaymentResult\":{\"AmountsResp\":{\"Currency\":\"USD\",\"AuthorizedAmount\":"
        + authorized
        + "}}}}}";
  }

  private static String approved(String poiTxn, double authorized) {
    return payment("Success", poiTxn, authorized);
  }

  private static String partial(String poiTxn, double authorized) {
    return payment("Partial", poiTxn, authorized);
  }

  /** A successful reversal carrying its own POI reference. */
  private static String reversed(String poiTxn) {
    return "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
        + "\"Response\":{\"Result\":\"Success\"},"
        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\""
        + poiTxn
        + "\",\"TimeStamp\":\"2026-07-20T10:00:09Z\"}}}}}";
  }

  /** A successful loyalty refund carrying its own POI reference. */
  private static String loyaltyRefunded(String poiTxn) {
    return "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
        + "\"Response\":{\"Result\":\"Success\"},"
        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\""
        + poiTxn
        + "\",\"TimeStamp\":\"2026-07-20T10:00:09Z\"}}}}}";
  }

  /** Each notice as "step reversed-POI -> reversal-POI". */
  private static List<String> describe(List<SettlementMovement> movements) {
    return movements.stream()
        .map(
            m ->
                m.getStep()
                    + " "
                    + m.getReversedMovement().getPoiTransactionId()
                    + " -> "
                    + m.getPoiTransactionId())
        .collect(Collectors.toList());
  }

  @Test
  void abortReportsEachCardPaymentItReversesNewestFirst() throws Exception {
    // no onPartialCardApproval: the third partial request fails, and abort reverses all three
    enqueue(
        partial("POI-PAY-1", 60.00),
        partial("POI-PAY-2", 20.00),
        partial("POI-PAY-3", 10.00),
        reversed("POI-REV-3"),
        reversed("POI-REV-2"),
        reversed("POI-REV-1"));

    assertThrows(SessionException.class, () -> settle().get());

    List<String> expected =
        List.of(
            "CARD_CHARGE POI-PAY-3 -> POI-REV-3",
            "CARD_CHARGE POI-PAY-2 -> POI-REV-2",
            "CARD_CHARGE POI-PAY-1 -> POI-REV-1");
    assertEquals(expected, describe(cardReversals));
    assertEquals(expected, describe(notices), "onMovement receives the same notices");
    SettlementMovement first = cardReversals.get(0);
    assertTrue(first.isReversal());
    assertEquals(0, new BigDecimal("10.00").compareTo(first.getAmount()));
    assertEquals(first.getReversedMovement().getSaleTransactionId(), first.getSaleTransactionId());
    assertNotNull(first.getServiceId());
    assertNotEquals(first.getReversedMovement().getServiceId(), first.getServiceId());
    assertNotNull(first.getPoiTransactionTimestamp());
  }

  @Test
  void aReversalThatFailsGetsNoNotice() throws Exception {
    enqueue(
        partial("POI-PAY-1", 60.00),
        partial("POI-PAY-2", 20.00),
        partial("POI-PAY-3", 10.00),
        reversed("POI-REV-3"),
        REVERSAL_UNREACHABLE,
        reversed("POI-REV-1"));

    SessionException failure = assertThrows(SessionException.class, () -> settle().get());

    assertTrue(failure.getError().getMessage().contains("still standing"));
    assertEquals(
        List.of("CARD_CHARGE POI-PAY-3 -> POI-REV-3", "CARD_CHARGE POI-PAY-1 -> POI-REV-1"),
        describe(cardReversals));
  }

  @Test
  void retryAfterAThrowingOnCardChargedReportsTheReversedCharge() throws Exception {
    enqueue(approved("POI-PAY-1", 100.00), reversed("POI-REV-1"), approved("POI-PAY-2", 100.00));
    AtomicInteger charged = new AtomicInteger();
    List<SettlementFailure> failures = new ArrayList<>();

    SettlementResult result =
        settle()
            .onCardCharged(
                movement -> {
                  if (charged.incrementAndGet() == 1) {
                    throw new IllegalStateException("receipt printer offline");
                  }
                })
            .onError(
                failure -> {
                  failures.add(failure);
                  return SettlementRecovery.retry();
                })
            .get();

    assertEquals(1, failures.size());
    assertEquals(List.of("CARD_CHARGE POI-PAY-1 -> POI-REV-1"), describe(cardReversals));
    assertEquals(
        List.of("POI-PAY-2"),
        result.getMovements().stream()
            .map(SettlementMovement::getPoiTransactionId)
            .collect(Collectors.toList()),
        "the reversed charge and its notice stay out of the ledger");
  }

  @Test
  void retryAfterARecoveredChargeWhoseProcessingFailsReportsItReversed() throws Exception {
    enqueue(TerminalShopperSessionTest.ADMIN_OK);
    session =
        TerminalShopperSession.builder()
            .client(
                BiltNexoTerminalClient.builder()
                    .endpoint(server.url("/nexo").toString())
                    .readTimeout(java.time.Duration.ofMillis(300))
                    .disableRecoveryOnNetworkError()
                    .build())
            .saleId("POS-LANE-4")
            .poiId("VictaLane-275839164")
            .currency("USD")
            .autoDisplay(false)
            .start()
            .get();
    server.takeRequest(5, TimeUnit.SECONDS); // the session-start Admin request
    session.basket().addItem(BasketItem.sale("SKU-1", "Item", 1, new BigDecimal("100.00")));
    String payment = approved("POI-PAY-1", 100.00);
    // the payment times out; TransactionStatus recovers it, and processing the recovered
    // response fails in the register's onCardCharged
    server.enqueue(
        new MockResponse()
            .setBodyDelay(1000, TimeUnit.MILLISECONDS)
            .setBody(approved("POI-PAY-1", 100.00)));
    enqueue(
        "{\"SaleToPOIResponse\":{\"TransactionStatusResponse\":{"
            + "\"Response\":{\"Result\":\"Success\"},"
            + "\"RepeatedMessageResponse\":{"
            + "\"MessageHeader\":{\"MessageCategory\":\"Payment\"},"
            + "\"RepeatedResponseMessageBody\":"
            + payment.substring("{\"SaleToPOIResponse\":".length(), payment.length() - 1)
            + "}}}}",
        reversed("POI-REV-1"),
        approved("POI-PAY-2", 100.00));
    AtomicInteger charged = new AtomicInteger();

    SettlementResult result =
        settle()
            .onCardCharged(
                movement -> {
                  if (charged.incrementAndGet() == 1) {
                    throw new IllegalStateException("receipt printer offline");
                  }
                })
            .onError(failure -> SettlementRecovery.retry())
            .get();

    assertEquals(List.of("CARD_CHARGE POI-PAY-1 -> POI-REV-1"), describe(cardReversals));
    assertEquals("POI-PAY-2", result.getPoiTransactionId());
  }

  @Test
  void externalAfterTheAutomaticRequestsReportsTheLastPartialReversed() throws Exception {
    enqueue(
        partial("POI-PAY-1", 60.00),
        partial("POI-PAY-2", 20.00),
        partial("POI-PAY-3", 10.00),
        reversed("POI-REV-3"));

    SettlementResult result =
        settle()
            .onError(
                failure ->
                    SettlementRecovery.external(ExternalPayment.cash(failure.getAmountDue())))
            .get();

    assertEquals(List.of("CARD_CHARGE POI-PAY-3 -> POI-REV-3"), describe(cardReversals));
    assertTrue(result.getMovements().stream().noneMatch(SettlementMovement::isReversal));
  }

  @Test
  void abortReportsReversedRebateAndPointRedemptions() throws Exception {
    identifyMember();
    enqueue(
        REBATE_OK,
        REDEEM_OK,
        PAYMENT_DECLINED,
        loyaltyRefunded("POI-RD-REF"),
        loyaltyRefunded("POI-RB-REF"));

    assertThrows(SessionException.class, () -> settle().get());

    assertEquals(
        List.of(
            "POINT_REDEMPTION POI-RD-1 -> POI-RD-REF", "REBATE_REDEMPTION POI-RB-1 -> POI-RB-REF"),
        describe(notices));
    assertEquals("98234", notices.get(1).getMemberId());
    assertEquals(0, new BigDecimal("10.00").compareTo(notices.get(1).getAmount()));
    assertTrue(cardReversals.isEmpty(), "onCardChargeReversed is for card payments only");
  }

  @Test
  void abortReportsAReversedStoredValueCharge() throws Exception {
    session.setStoredValueCard("GC-1234-5678");
    enqueue(partial("POI-GC-1", 35.00), PAYMENT_DECLINED, reversed("POI-GC-REV"));

    assertThrows(SessionException.class, () -> settle().get());

    assertEquals(List.of("STORED_VALUE_CHARGE POI-GC-1 -> POI-GC-REV"), describe(notices));
    assertTrue(cardReversals.isEmpty());
  }

  @Test
  void abortAfterAThrowingOnAwardedReportsTheAwardAndTheCardReversed() throws Exception {
    identifyMember();
    enqueue(
        approved("POI-PAY-1", 100.00),
        AWARD_OK,
        loyaltyRefunded("POI-AW-REF"),
        reversed("POI-REV-1"));

    assertThrows(
        SessionException.class,
        () ->
            settle(
                    SettlementOptions.builder()
                        .loyalty(SettlementLoyalty.explicit(Set.of(SettlementLoyalty.Step.AWARD)))
                        .build())
                .onAwarded(
                    movement -> {
                      throw new IllegalStateException("loyalty display failed");
                    })
                .get());

    assertEquals(
        List.of("AWARD POI-AW-1 -> POI-AW-REF", "CARD_CHARGE POI-PAY-1 -> POI-REV-1"),
        describe(notices));
    assertEquals(Integer.valueOf(89), notices.get(0).getPoints());
    assertEquals(List.of("CARD_CHARGE POI-PAY-1 -> POI-REV-1"), describe(cardReversals));
  }

  @Test
  void aThrowingNotificationHandlerDoesNotStopTheUnwind() throws Exception {
    enqueue(
        partial("POI-PAY-1", 60.00),
        partial("POI-PAY-2", 20.00),
        partial("POI-PAY-3", 10.00),
        reversed("POI-REV-3"),
        reversed("POI-REV-2"),
        reversed("POI-REV-1"));

    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle()
                    .onCardChargeReversed(
                        movement -> {
                          throw new IllegalStateException("register UI gone");
                        })
                    .get());

    assertEquals(SessionErrorCode.DECLINED, failure.getError().getCode());
    assertFalse(failure.getError().getMessage().contains("still standing"));
    assertEquals(
        3,
        drainRequests().stream().filter(request -> request.getReversalRequest() != null).count(),
        "every card payment is still reversed");
  }

  @Test
  void aThrowingOnMovementDoesNotKeepTheNoticeFromOnCardChargeReversed() throws Exception {
    enqueue(approved("POI-PAY-1", 100.00), reversed("POI-REV-1"), approved("POI-PAY-2", 100.00));
    AtomicInteger charged = new AtomicInteger();

    SettlementResult result =
        session
            .settle()
            .onMovement(
                movement -> {
                  if (movement.isReversal()) {
                    throw new IllegalStateException("ledger view crashed");
                  }
                })
            .onCardCharged(
                movement -> {
                  if (charged.incrementAndGet() == 1) {
                    throw new IllegalStateException("receipt printer offline");
                  }
                })
            .onCardChargeReversed(cardReversals::add)
            .onError(failure -> SettlementRecovery.retry())
            .get();

    assertEquals(List.of("CARD_CHARGE POI-PAY-1 -> POI-REV-1"), describe(cardReversals));
    assertEquals("POI-PAY-2", result.getPoiTransactionId());
  }

  @Test
  void aMovementTheRegisterNeverSawGetsNoNotice() throws Exception {
    // the stored value charge's movement is published; a later abort reverses it. Without any
    // movement handler, nothing is delivered and the unwind is unchanged
    session.setStoredValueCard("GC-1234-5678");
    enqueue(partial("POI-GC-1", 35.00), PAYMENT_DECLINED, reversed("POI-GC-REV"));

    assertThrows(SessionException.class, () -> session.settle().get());

    assertTrue(notices.isEmpty());
    assertEquals(
        1,
        drainRequests().stream().filter(request -> request.getReversalRequest() != null).count());
  }
}
