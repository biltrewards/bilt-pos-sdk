/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session;

import static org.junit.jupiter.api.Assertions.*;

import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.settlement.OriginalSaleRecord;
import com.bilt.pos.session.settlement.RefundAllocation;
import com.bilt.pos.session.settlement.SettlementMovement;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.bilt.pos.session.settlement.SettlementRecovery;
import com.bilt.pos.session.settlement.SettlementResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The register-supplied {@link ServiceIdProvider} and {@link ServiceIdListener}. */
class ServiceIdTest {

  private static final String PAYMENT_DECLINED =
      "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
          + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Refusal\"}}}}";
  private static final String PAYMENT_ABORTED =
      "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
          + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Aborted\"}}}}";

  private final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private final AtomicInteger minted = new AtomicInteger();
  private final List<Map.Entry<String, MessageCategoryType>> assigned =
      new CopyOnWriteArrayList<>();

  private MockWebServer server;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.setDispatcher(TerminalShopperSessionTest.adminAnsweringDispatcher());
    server.start();
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  private String nextId() {
    return String.format("REG%07d", minted.incrementAndGet());
  }

  private TerminalShopperSession session(Duration readTimeout) throws Exception {
    BiltNexoTerminalClient.Builder client =
        BiltNexoTerminalClient.builder()
            .endpoint(server.url("/nexo").toString())
            .disableRecoveryOnNetworkError();
    if (readTimeout != null) {
      client.readTimeout(readTimeout);
    }
    TerminalShopperSession session =
        TerminalShopperSession.builder()
            .client(client.build())
            .saleId("POS-LANE-3")
            .poiId("VictaLane-275839164")
            .currency("USD")
            .autoDisplay(false)
            .serviceIdProvider(this::nextId)
            .serviceIdListener((id, category) -> assigned.add(Map.entry(id, category)))
            .start()
            .get();
    SaleToPOIRequest start = nextRequest();
    assertEquals("REG0000001", start.getMessageHeader().getServiceID());
    return session;
  }

  private SaleToPOIRequest nextRequest() throws Exception {
    RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
    assertNotNull(recorded, "expected another terminal request");
    return mapper
        .readValue(recorded.getBody().readUtf8(), NexoTerminalAPI.class)
        .getSaleToPOIRequest();
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

  private static String paymentOk(String poiTxn, double authorized) {
    return "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
        + "\"Response\":{\"Result\":\"Success\"},"
        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\""
        + poiTxn
        + "\",\"TimeStamp\":\"2026-07-20T10:00:03Z\"}},"
        + "\"PaymentResult\":{\"AmountsResp\":{\"Currency\":\"USD\",\"AuthorizedAmount\":"
        + authorized
        + "}}}}}";
  }

  private static String transactionStatusPaymentOk(String poiTxn, double authorized) {
    return "{\"SaleToPOIResponse\":{\"TransactionStatusResponse\":{"
        + "\"Response\":{\"Result\":\"Success\"},"
        + "\"RepeatedMessageResponse\":{"
        + "\"MessageHeader\":{\"MessageCategory\":\"Payment\"},"
        + "\"RepeatedResponseMessageBody\":"
        + paymentOk(poiTxn, authorized)
            .replace("{\"SaleToPOIResponse\":", "")
            .replaceFirst("}$", "")
        + "}}}}";
  }

  private static void addHundredDollarItem(TerminalShopperSession session) {
    session.basket().addItem(BasketItem.sale("SKU-1", "Item", 1, new BigDecimal("100.00")));
  }

  @Test
  void settleLegIsSentWithTheIdBeforeStepReported() throws Exception {
    TerminalShopperSession session = session(null);
    addHundredDollarItem(session);
    server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
    List<String> beforeStepIds = new ArrayList<>();

    SettlementResult result =
        session
            .settle()
            .beforeStep(
                ctx -> {
                  beforeStepIds.add(ctx.getServiceId());
                  return null;
                })
            .get();

    SaleToPOIRequest payment = nextRequest();
    assertNotNull(payment.getPaymentRequest());
    assertEquals(List.of("REG0000002"), beforeStepIds);
    assertEquals("REG0000002", payment.getMessageHeader().getServiceID());
    SettlementMovement movement = result.getMovements().get(0);
    assertEquals("REG0000002", movement.getServiceId());
    assertTrue(assigned.contains(Map.entry("REG0000002", MessageCategoryType.PAYMENT)));
  }

  @Test
  void eachRetryMintsAFreshIdAndCallsBeforeStepAgain() throws Exception {
    TerminalShopperSession session = session(null);
    addHundredDollarItem(session);
    server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
    server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-2", 100.00)));
    List<String> beforeStepIds = new ArrayList<>();
    AtomicInteger failures = new AtomicInteger();

    SettlementResult result =
        session
            .settle()
            .beforeStep(
                ctx -> {
                  beforeStepIds.add(ctx.getServiceId());
                  return null;
                })
            .onError(
                failure ->
                    failures.incrementAndGet() == 1
                        ? SettlementRecovery.retry()
                        : SettlementRecovery.abort())
            .get();

    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(2, requests.size());
    assertEquals(List.of("REG0000002", "REG0000003"), beforeStepIds);
    assertEquals("REG0000002", requests.get(0).getMessageHeader().getServiceID());
    assertEquals("REG0000003", requests.get(1).getMessageHeader().getServiceID());
    assertEquals("REG0000003", result.getMovements().get(0).getServiceId());
  }

  @Test
  void transactionStatusRecoveryPollsUseTheProviderAndReferenceTheLeg() throws Exception {
    TerminalShopperSession session = session(Duration.ofMillis(100));
    addHundredDollarItem(session);
    server.enqueue(
        new MockResponse()
            .setBodyDelay(500, TimeUnit.MILLISECONDS)
            .setBody(paymentOk("POI-PAY-3", 100.00)));
    server.enqueue(new MockResponse().setBody(transactionStatusPaymentOk("POI-PAY-3", 100.00)));
    List<String> beforeStepIds = new ArrayList<>();

    SettlementResult result =
        session
            .settle()
            .beforeStep(
                ctx -> {
                  beforeStepIds.add(ctx.getServiceId());
                  return null;
                })
            .get();

    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(2, requests.size());
    SaleToPOIRequest status = requests.get(1);
    assertEquals(List.of("REG0000002"), beforeStepIds);
    assertEquals("REG0000002", requests.get(0).getMessageHeader().getServiceID());
    assertEquals("REG0000003", status.getMessageHeader().getServiceID());
    assertEquals(
        "REG0000002", status.getTransactionStatusRequest().getMessageReference().getServiceID());
    assertEquals("REG0000002", result.getMovements().get(0).getServiceId());
    assertEquals(
        List.of(
            Map.entry("REG0000001", MessageCategoryType.ADMIN),
            Map.entry("REG0000002", MessageCategoryType.PAYMENT),
            Map.entry("REG0000003", MessageCategoryType.TRANSACTION_STATUS)),
        assigned);
  }

  @Test
  void abortIsSentWithAProviderId() throws Exception {
    TerminalShopperSession session = session(null);
    addHundredDollarItem(session);
    server.enqueue(new MockResponse().setBodyDelay(1, TimeUnit.SECONDS).setBody(PAYMENT_ABORTED));
    server.enqueue(new MockResponse());

    SettlementFlow settle = session.settle();
    Thread payer = new Thread(settle::getOrNull);
    payer.start();
    SaleToPOIRequest payment = nextRequest();
    session.abort().executeSync();
    SaleToPOIRequest abort = nextRequest();
    payer.join(5_000);

    assertEquals("REG0000002", payment.getMessageHeader().getServiceID());
    assertEquals("REG0000003", abort.getMessageHeader().getServiceID());
    assertEquals("REG0000002", abort.getAbortRequest().getMessageReference().getServiceID());
  }

  @Test
  void refundAllocationIsSentWithItsBeforeStepId() throws Exception {
    TerminalShopperSession session = session(null);
    session
        .basket()
        .addItem(BasketItem.returnItem("RET-1", "Returned item", 1, new BigDecimal("15.00")));
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.refundOk(15.00)));
    List<String> beforeStepIds = new ArrayList<>();

    SettlementResult result =
        session
            .settle(
                SettlementOptions.builder()
                    .addRefund(
                        RefundAllocation.card(
                            new BigDecimal("15.00"),
                            "POI-ORIG-CARD",
                            Instant.parse("2026-07-20T10:00:00Z")))
                    .build())
            .beforeStep(
                ctx -> {
                  beforeStepIds.add(ctx.getServiceId());
                  return null;
                })
            .get();

    SaleToPOIRequest refund = nextRequest();
    assertEquals("Refund", refund.getPaymentRequest().getPaymentData().getPaymentType().toValue());
    assertEquals(List.of("REG0000002"), beforeStepIds);
    assertEquals("REG0000002", refund.getMessageHeader().getServiceID());
    assertEquals("REG0000002", result.getMovements().get(0).getServiceId());
  }

  private static final Instant ORIGINAL_TIME = Instant.parse("2026-07-20T10:00:00Z");
  private static final String REVERSAL_UNREACHABLE =
      "{\"SaleToPOIResponse\":{\"ReversalResponse\":{"
          + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"UnreachableHost\"}}}}";

  private static OriginalSaleRecord cardAndGiftCardSale() {
    return OriginalSaleRecord.builder()
        .cardPoiTransactionId("POI-CARD-9")
        .cardPoiTransactionTimestamp(ORIGINAL_TIME)
        .storedValuePoiTransactionId("POI-SV-9")
        .storedValuePoiTransactionTimestamp(ORIGINAL_TIME)
        .build();
  }

  @Test
  void eachVoidLegIsSentWithTheIdBeforeStepReported() throws Exception {
    TerminalShopperSession session = session(null);
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.REVERSAL_OK));
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.REVERSAL_OK));
    List<ReversalContext> contexts = new ArrayList<>();

    VoidResult result =
        session.voidTransaction(cardAndGiftCardSale()).beforeStep(contexts::add).get();

    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(2, requests.size());
    assertEquals(
        List.of(ReversalStep.CARD, ReversalStep.STORED_VALUE),
        contexts.stream().map(ReversalContext::getStep).collect(Collectors.toList()));
    assertEquals(
        List.of("REG0000002", "REG0000003"),
        contexts.stream().map(ReversalContext::getServiceId).collect(Collectors.toList()));
    assertEquals("REG0000002", requests.get(0).getMessageHeader().getServiceID());
    assertEquals("REG0000003", requests.get(1).getMessageHeader().getServiceID());
    assertEquals("REG0000002", result.getServiceId());
    assertTrue(assigned.contains(Map.entry("REG0000003", MessageCategoryType.REVERSAL)));
  }

  @Test
  void retriedVoidLegMintsAFreshIdAndCallsBeforeStepAgain() throws Exception {
    TerminalShopperSession session = session(null);
    server.enqueue(new MockResponse().setBody(REVERSAL_UNREACHABLE));
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.REVERSAL_OK));
    List<String> beforeStepIds = new ArrayList<>();

    VoidResult result =
        session
            .voidTransaction(
                OriginalSaleRecord.builder()
                    .cardPoiTransactionId("POI-CARD-9")
                    .cardPoiTransactionTimestamp(ORIGINAL_TIME)
                    .build())
            .beforeStep(ctx -> beforeStepIds.add(ctx.getServiceId()))
            .onError((step, error) -> ReversalDecision.RETRY)
            .get();

    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(List.of("REG0000002", "REG0000003"), beforeStepIds);
    assertEquals(2, requests.size());
    assertEquals("REG0000002", requests.get(0).getMessageHeader().getServiceID());
    assertEquals("REG0000003", requests.get(1).getMessageHeader().getServiceID());
    assertEquals("REG0000003", result.getServiceId());
  }

  @Test
  void partialVoidReportsTheIdOfEachReversedMovement() throws Exception {
    TerminalShopperSession session = session(null);
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.REVERSAL_OK));
    server.enqueue(new MockResponse().setBody(REVERSAL_UNREACHABLE));

    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .voidTransaction(cardAndGiftCardSale())
                    .onError((step, error) -> ReversalDecision.ABORT)
                    .get());

    assertEquals(
        List.of(new ReversedMovement(ReversalStep.CARD, "POI-CARD-9", "REG0000002")),
        failure.getError().getReversedMovements());
  }

  @Test
  void retriedRefundMintsAFreshIdAndCallsBeforeStepAgain() throws Exception {
    TerminalShopperSession session = session(null);
    addHundredDollarItem(session);
    server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 100.00)));
    session.settle().get();
    nextRequest();
    server.enqueue(new MockResponse().setBody(PAYMENT_DECLINED));
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.refundOk(15.00)));
    List<ReversalContext> contexts = new ArrayList<>();

    session
        .refund(new BigDecimal("15.00"))
        .beforeStep(contexts::add)
        .onError((step, error) -> ReversalDecision.RETRY)
        .get();

    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(2, requests.size());
    assertEquals(
        List.of(ReversalStep.CARD, ReversalStep.CARD),
        contexts.stream().map(ReversalContext::getStep).collect(Collectors.toList()));
    assertEquals(
        List.of("REG0000003", "REG0000004"),
        contexts.stream().map(ReversalContext::getServiceId).collect(Collectors.toList()));
    assertEquals("REG0000003", requests.get(0).getMessageHeader().getServiceID());
    assertEquals("REG0000004", requests.get(1).getMessageHeader().getServiceID());
    assertTrue(assigned.contains(Map.entry("REG0000004", MessageCategoryType.PAYMENT)));
  }

  @Test
  void throwingBeforeStepSendsNoLegAndGoesToOnError() throws Exception {
    TerminalShopperSession session = session(null);
    List<ReversalStep> failedSteps = new ArrayList<>();

    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .voidTransaction(cardAndGiftCardSale())
                    .beforeStep(
                        ctx -> {
                          throw new IllegalStateException("register could not persist");
                        })
                    .onError(
                        (step, error) -> {
                          failedSteps.add(step);
                          return ReversalDecision.ABORT;
                        })
                    .get());

    assertEquals(SessionErrorCode.UNKNOWN, failure.getError().getCode());
    assertEquals(List.of(ReversalStep.CARD), failedSteps);
    assertEquals(0, drainRequests().size());
  }

  @Test
  void throwingBeforeStepOnALoyaltyLegAbortsUnderTheDefaultPolicy() throws Exception {
    TerminalShopperSession session = session(null);
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.REVERSAL_OK));

    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .voidTransaction(
                        OriginalSaleRecord.builder()
                            .cardPoiTransactionId("POI-CARD-9")
                            .cardPoiTransactionTimestamp(ORIGINAL_TIME)
                            .redemptionPoiTransactionId("POI-RED-9")
                            .redemptionPoiTransactionTimestamp(ORIGINAL_TIME)
                            .memberId("member-1")
                            .build())
                    .beforeStep(
                        ctx -> {
                          if (ctx.getStep() == ReversalStep.REDEMPTION) {
                            throw new IllegalStateException("register could not persist");
                          }
                        })
                    .get());

    assertEquals(SessionErrorCode.UNKNOWN, failure.getError().getCode());
    assertEquals(
        List.of(ReversalStep.CARD),
        failure.getError().getReversedMovements().stream()
            .map(ReversedMovement::getStep)
            .collect(Collectors.toList()));
    assertEquals(1, drainRequests().size());
  }

  @Test
  void retryAfterAThrowingBeforeStepMintsAFreshId() throws Exception {
    TerminalShopperSession session = session(null);
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.REVERSAL_OK));
    AtomicInteger calls = new AtomicInteger();

    session
        .voidTransaction(
            OriginalSaleRecord.builder()
                .cardPoiTransactionId("POI-CARD-9")
                .cardPoiTransactionTimestamp(ORIGINAL_TIME)
                .build())
        .beforeStep(
            ctx -> {
              if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("register could not persist");
              }
            })
        .onError((step, error) -> ReversalDecision.RETRY)
        .get();

    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(1, requests.size());
    assertEquals("REG0000003", requests.get(0).getMessageHeader().getServiceID());
  }

  @Test
  void invalidProviderIdSendsNothing() throws Exception {
    for (String invalid : new String[] {null, "", "ABCDEFGHIJK", "BAD-ID", "ID 1"}) {
      Terminal terminal = terminal(() -> invalid, null);

      SessionException e = assertThrows(SessionException.class, () -> terminal.diagnose().get());

      assertEquals(SessionErrorCode.INVALID_STATE, e.getError().getCode());
      terminal.close();
    }
    assertEquals(0, server.getRequestCount());
  }

  @Test
  void throwingListenerSendsNothing() throws Exception {
    Terminal terminal =
        terminal(
            () -> "ABC123",
            (id, category) -> {
              throw new IllegalStateException("register could not persist " + id);
            });

    SessionException e = assertThrows(SessionException.class, () -> terminal.diagnose().get());

    assertEquals(SessionErrorCode.INVALID_STATE, e.getError().getCode());
    assertInstanceOf(IllegalStateException.class, e.getError().getCause());
    assertEquals(0, server.getRequestCount());
  }

  @Test
  void invalidIdOnASettleLegSendsNoPayment() throws Exception {
    TerminalShopperSession session =
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
            .serviceIdProvider(() -> minted.incrementAndGet() == 1 ? "START1" : "NOT VALID")
            .start()
            .get();
    nextRequest();
    addHundredDollarItem(session);

    SessionException e = assertThrows(SessionException.class, () -> session.settle().get());

    assertEquals(SessionErrorCode.INVALID_STATE, e.getError().getCode());
    assertEquals(1, server.getRequestCount(), "only the session start reached the terminal");
  }

  @Test
  void terminalUsesTheProviderAndListener() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"DiagnosisResponse\":{"
                    + "\"Response\":{\"Result\":\"Success\"}}}}"));
    Terminal terminal =
        terminal(this::nextId, (id, category) -> assigned.add(Map.entry(id, category)));

    terminal.diagnose().get();

    assertEquals("REG0000001", nextRequest().getMessageHeader().getServiceID());
    assertEquals(List.of(Map.entry("REG0000001", MessageCategoryType.DIAGNOSIS)), assigned);
    terminal.close();
  }

  private Terminal terminal(ServiceIdProvider provider, ServiceIdListener listener) {
    return Terminal.builder()
        .client(
            BiltNexoTerminalClient.builder()
                .endpoint(server.url("/nexo").toString())
                .disableRecoveryOnNetworkError()
                .build())
        .saleId("POS-LANE-3")
        .poiId("VictaLane-275839164")
        .serviceIdProvider(provider)
        .serviceIdListener(listener)
        .build();
  }
}
