package com.bilt.pos.session;

import static org.junit.jupiter.api.Assertions.*;

import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.StoredValueData;
import com.bilt.pos.session.basket.BasketDiscount;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.settlement.ExternalPayment;
import com.bilt.pos.session.settlement.OriginalSaleRecord;
import com.bilt.pos.session.settlement.SettlementFailure;
import com.bilt.pos.session.settlement.SettlementMovement;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.bilt.pos.session.settlement.SettlementRecovery;
import com.bilt.pos.session.settlement.SettlementResult;
import com.bilt.pos.session.settlement.SettlementStep;
import com.bilt.pos.session.settlement.SettlementTarget;
import com.bilt.pos.session.settlement.StoredValueLoad;
import com.bilt.pos.session.settlement.StoredValueLoadRecord;
import com.bilt.pos.session.storedvalue.StoredValueCard;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TerminalShopperSessionStoredValuePurchaseTest {

  private static final String STORED_VALUE_FAILED =
      "{\"SaleToPOIResponse\":{\"StoredValueResponse\":{"
          + "\"Response\":{\"Result\":\"Failure\","
          + "\"ErrorCondition\":\"Refusal\"}}}}";

  private final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private MockWebServer server;
  private TerminalShopperSession session;

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
    server.takeRequest(5, TimeUnit.SECONDS);
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void settlementActivatesTheReferencedStoredValueLineThenCharges() throws Exception {
    session.basket().addItem(giftCard("gift-card-1", "Gift card", new BigDecimal("25.00")));
    server.enqueue(
        new MockResponse().setBody(storedValueOk("Activate", "POI-LOAD-1", 25.00, 25.00)));
    server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 25.00)));

    List<SettlementMovement> loadedCallbacks = new ArrayList<>();
    SettlementResult result =
        session
            .settle(
                SettlementOptions.builder()
                    .addFulfillment(
                        StoredValueLoad.activate("gift-card-1", StoredValueCard.scanned("GC-1")))
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
    assertEquals(SettlementTarget.basketLine("gift-card-1"), loadedCallbacks.get(0).getTarget());

    SaleToPOIRequest activation = recordedRequest();
    StoredValueData load = activation.getStoredValueRequest().getStoredValueData()[0];
    assertEquals("Activate", load.getStoredValueTransactionType().toValue());
    assertEquals(25.00, load.getItemAmount());
    assertEquals("GC-1", load.getStoredValueAccountID().getStoredValueID());
    SaleToPOIRequest charge = recordedRequest();
    assertEquals(
        25.00,
        charge.getPaymentRequest().getPaymentTransaction().getAmountsReq().getRequestedAmount());
    assertEquals(SettlementStep.STORED_VALUE_LOAD, result.getMovements().get(0).getStep());
    assertEquals(SettlementStep.CARD_CHARGE, result.getMovements().get(1).getStep());

    OriginalSaleRecord original = OriginalSaleRecord.from(result, null);
    assertEquals(result.getStoredValueLoads(), original.getStoredValueLoads());
  }

  @Test
  void fullyDiscountedStoredValueLineLoadsItsFaceValueWithoutACharge() throws Exception {
    session
        .basket()
        .addItem(
            giftCard("gift-card-1", "Promotional gift card", new BigDecimal("25.00"))
                .withDiscount(
                    BasketDiscount.offer(
                        "OFFER-1", "Complimentary card", new BigDecimal("25.00"))));
    server.enqueue(
        new MockResponse().setBody(storedValueOk("Activate", "POI-LOAD-1", 25.00, 25.00)));

    SettlementResult result =
        session
            .settle(
                SettlementOptions.builder()
                    .addFulfillment(
                        StoredValueLoad.activate("gift-card-1", StoredValueCard.number("GC-1")))
                    .build())
            .get();

    assertEquals(0, BigDecimal.ZERO.compareTo(result.getCardAmountCharged()));
    assertEquals(new BigDecimal("25.00"), result.getStoredValueLoadedAmount());
    assertEquals(new BigDecimal("0.00"), result.getFinalBasket().getGrandTotal());
    SaleToPOIRequest onlyRequest = recordedRequest();
    assertNull(onlyRequest.getPaymentRequest());
    StoredValueData load = onlyRequest.getStoredValueRequest().getStoredValueData()[0];
    assertEquals("Activate", load.getStoredValueTransactionType().toValue());
    assertEquals(25.00, load.getItemAmount(), "the terminal receives the pre-discount face value");
    assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS));

    server.enqueue(
        new MockResponse().setBody(storedValueOk("Reverse", "POI-REVERSE-LOAD-1", 25.00, 0.00)));
    VoidResult voided = session.voidTransaction().get();

    assertTrue(voided.isSuccess());
    assertEquals(0, new BigDecimal("25.00").compareTo(voided.getReversedAmount()));
    assertEquals("POI-REVERSE-LOAD-1", voided.getPoiTransactionId());
    assertEquals(
        "Reverse",
        recordedRequest()
            .getStoredValueRequest()
            .getStoredValueData()[0]
            .getStoredValueTransactionType()
            .toValue());
  }

  @Test
  void registerCreditCanFundAStoredValueLoadWithoutARefundAllocation() throws Exception {
    session
        .basket()
        .addItem(giftCard("gift-card-1", "Customer service gift card", new BigDecimal("50.00")));
    session
        .basket()
        .addItem(
            BasketItem.credit("GOODWILL", "Customer service credit", 1, new BigDecimal("50.00")));
    server.enqueue(
        new MockResponse().setBody(storedValueOk("Activate", "POI-LOAD-1", 50.00, 50.00)));

    SettlementResult result =
        session
            .settle(
                SettlementOptions.builder()
                    .addFulfillment(
                        StoredValueLoad.activate("gift-card-1", StoredValueCard.number("GC-1")))
                    .build())
            .get();

    assertEquals(0, BigDecimal.ZERO.compareTo(result.getCardAmountCharged()));
    assertEquals(new BigDecimal("50.00"), result.getStoredValueLoadedAmount());
    assertEquals(new BigDecimal("0.00"), result.getFinalBasket().getGrandTotal());
    assertTrue(result.getFinalBasket().getItem("2").isCredit());
    SaleToPOIRequest onlyRequest = recordedRequest();
    assertNull(onlyRequest.getPaymentRequest());
    assertEquals(
        "Activate",
        onlyRequest
            .getStoredValueRequest()
            .getStoredValueData()[0]
            .getStoredValueTransactionType()
            .toValue());
    assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS));
  }

  @Test
  void fulfillmentMustReferenceAnExistingSaleLine() {
    session.basket().addItem(giftCard("gift-card-1", "Gift card", new BigDecimal("25.00")));
    SessionException orphan =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle(
                        SettlementOptions.builder()
                            .addFulfillment(
                                StoredValueLoad.activate("missing", StoredValueCard.number("GC-1")))
                            .build())
                    .get());
    assertEquals(SessionErrorCode.INVALID_STATE, orphan.getError().getCode());

    session
        .basket()
        .addItem(
            BasketItem.credit("CREDIT", "Credit", 1, new BigDecimal("5.00"))
                .withReference("credit-1"));
    SessionException creditTarget =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle(
                        SettlementOptions.builder()
                            .addFulfillment(
                                StoredValueLoad.activate(
                                    "credit-1", StoredValueCard.number("GC-1")))
                            .build())
                    .get());
    assertTrue(creditTarget.getError().getMessage().contains("is not a sale"));

    session
        .basket()
        .addItem(
            BasketItem.sale("FREE", "Free item", 1, BigDecimal.ZERO).withReference("zero-sale"));
    SessionException zeroTarget =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle(
                        SettlementOptions.builder()
                            .addFulfillment(
                                StoredValueLoad.activate(
                                    "zero-sale", StoredValueCard.number("GC-1")))
                            .build())
                    .get());
    assertTrue(zeroTarget.getError().getMessage().contains("positive original total"));

    SessionException duplicate =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle(
                        SettlementOptions.builder()
                            .addFulfillment(
                                StoredValueLoad.activate(
                                    "gift-card-1", StoredValueCard.number("GC-1")))
                            .addFulfillment(
                                StoredValueLoad.reload(
                                    "gift-card-1", StoredValueCard.number("GC-1")))
                            .build())
                    .get());
    assertTrue(duplicate.getError().getMessage().contains("more than one"));
    assertEquals(
        1,
        server.getRequestCount(),
        "validation must not send anything after the session-start request");
  }

  @Test
  void referencedSaleDoesNotRequireFulfillment() throws Exception {
    session.basket().addItem(giftCard("gift-card-1", "Gift card", new BigDecimal("25.00")));
    server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 25.00)));

    SettlementResult result = session.settle().get();

    assertEquals(new BigDecimal("25.00"), result.getCardAmountCharged());
    assertEquals(0, BigDecimal.ZERO.compareTo(result.getStoredValueLoadedAmount()));
    SaleToPOIRequest onlyRequest = recordedRequest();
    assertNotNull(onlyRequest.getPaymentRequest());
    assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS));
  }

  @Test
  void failedLoadPreventsTheFundingCharge() throws Exception {
    session.basket().addItem(giftCard("gift-card-1", "Gift card", new BigDecimal("25.00")));
    server.enqueue(new MockResponse().setBody(STORED_VALUE_FAILED));

    assertThrows(SessionException.class, () -> session.settle(reloadOptions()).get());

    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(1, requests.size());
    assertEquals(
        "Load",
        requests
            .get(0)
            .getStoredValueRequest()
            .getStoredValueData()[0]
            .getStoredValueTransactionType()
            .toValue());
  }

  @Test
  void partialActivationIsReversedWithoutCharging() throws Exception {
    session.basket().addItem(giftCard("gift-card-1", "Gift card", new BigDecimal("25.00")));
    server.enqueue(
        new MockResponse().setBody(storedValueOk("Activate", "POI-LOAD-1", 20.00, 20.00)));
    server.enqueue(
        new MockResponse().setBody(storedValueOk("Reverse", "POI-REVERSE-LOAD-1", 20.00, 0.00)));

    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle(
                        SettlementOptions.builder()
                            .addFulfillment(
                                StoredValueLoad.activate(
                                    "gift-card-1", StoredValueCard.number("GC-1")))
                            .build())
                    .get());

    assertEquals(SessionErrorCode.DECLINED, failure.getError().getCode());
    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(2, requests.size());
    assertEquals(
        "Activate",
        requests
            .get(0)
            .getStoredValueRequest()
            .getStoredValueData()[0]
            .getStoredValueTransactionType()
            .toValue());
    assertEquals("POI-LOAD-1", originalStoredValueTransaction(requests.get(1)));
    assertEquals(
        "Reverse",
        requests
            .get(1)
            .getStoredValueRequest()
            .getStoredValueData()[0]
            .getStoredValueTransactionType()
            .toValue());
  }

  @Test
  void abandoningPartialActivationReportsTheStandingLoadHonestly() throws Exception {
    session.basket().addItem(giftCard("gift-card-1", "Gift card", new BigDecimal("25.00")));
    server.enqueue(
        new MockResponse().setBody(storedValueOk("Activate", "POI-LOAD-1", 20.00, 20.00)));

    SessionException abandoned =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle(
                        SettlementOptions.builder()
                            .addFulfillment(
                                StoredValueLoad.activate(
                                    "gift-card-1", StoredValueCard.number("GC-1")))
                            .build())
                    .onError(ignored -> SettlementRecovery.abandon())
                    .get());

    SettlementFailure failure = abandoned.getAbandonedSettlement().getFailure();
    assertEquals(SessionErrorCode.DECLINED, failure.getCode());
    assertEquals(
        "the stored value fulfillment loaded 20.00 instead of the basket line's 25.00",
        failure.getMessage());
    assertEquals(1, failure.getCommittedMovements().size());
    assertEquals(
        SettlementStep.STORED_VALUE_LOAD, failure.getCommittedMovements().get(0).getStep());
    assertEquals("POI-LOAD-1", failure.getCommittedMovements().get(0).getPoiTransactionId());
    assertEquals(
        1,
        drainRequests().size(),
        "abandon must leave only the partial load standing, without charging");
  }

  @Test
  void wholeSettlementVoidReversesTheLoadBeforeItsFundingCharge() throws Exception {
    session.basket().addItem(giftCard("gift-card-1", "Gift card", new BigDecimal("25.00")));
    server.enqueue(
        new MockResponse().setBody(storedValueOk("Activate", "POI-LOAD-1", 25.00, 25.00)));
    server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 25.00)));
    session
        .settle(
            SettlementOptions.builder()
                .addFulfillment(
                    StoredValueLoad.activate("gift-card-1", StoredValueCard.number("GC-1")))
                .build())
        .get();
    drainRequests();

    server.enqueue(
        new MockResponse().setBody(storedValueOk("Reverse", "POI-REVERSE-LOAD-1", 25.00, 0.00)));
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.REVERSAL_OK));

    assertTrue(session.voidTransaction().get().isSuccess());

    List<SaleToPOIRequest> voidRequests = drainRequests();
    assertEquals(2, voidRequests.size());
    StoredValueData reverse = voidRequests.get(0).getStoredValueRequest().getStoredValueData()[0];
    assertEquals("Reverse", reverse.getStoredValueTransactionType().toValue());
    assertEquals(
        "POI-LOAD-1", reverse.getOriginalPOITransaction().getPoiTransactionID().getTransactionID());
    assertNotNull(voidRequests.get(1).getReversalRequest());
  }

  @Test
  void multipleStoredValueLinesKeepIndependentFulfillmentAndVoidReferences() throws Exception {
    session.basket().addItem(giftCard("gift-card-1", "Gift card", new BigDecimal("10.00")));
    session.basket().addItem(giftCard("gift-card-2", "Gift card", new BigDecimal("15.00")));
    server.enqueue(
        new MockResponse().setBody(storedValueOk("Activate", "POI-LOAD-1", 10.00, 10.00)));
    server.enqueue(new MockResponse().setBody(storedValueOk("Load", "POI-LOAD-2", 15.00, 30.00)));
    server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 25.00)));

    SettlementResult result =
        session
            .settle(
                SettlementOptions.builder()
                    .addFulfillment(
                        StoredValueLoad.activate("gift-card-1", StoredValueCard.number("GC-1")))
                    .addFulfillment(
                        StoredValueLoad.reload("gift-card-2", StoredValueCard.number("GC-2")))
                    .build())
            .get();

    assertEquals(2, result.getStoredValueLoads().size());
    assertEquals("POI-LOAD-1", result.getStoredValueLoads().get(0).getPoiTransactionId());
    assertEquals("POI-LOAD-2", result.getStoredValueLoads().get(1).getPoiTransactionId());
    List<SaleToPOIRequest> purchaseRequests = drainRequests();
    assertEquals(3, purchaseRequests.size());
    assertEquals(
        "Activate",
        purchaseRequests
            .get(0)
            .getStoredValueRequest()
            .getStoredValueData()[0]
            .getStoredValueTransactionType()
            .toValue());
    assertEquals(
        "Load",
        purchaseRequests
            .get(1)
            .getStoredValueRequest()
            .getStoredValueData()[0]
            .getStoredValueTransactionType()
            .toValue());
    assertNotNull(purchaseRequests.get(2).getPaymentRequest());

    server.enqueue(
        new MockResponse().setBody(storedValueOk("Reverse", "POI-REVERSE-1", 10.00, 0.00)));
    server.enqueue(
        new MockResponse().setBody(storedValueOk("Reverse", "POI-REVERSE-2", 15.00, 15.00)));
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.REVERSAL_OK));
    assertTrue(session.voidTransaction().get().isSuccess());

    List<SaleToPOIRequest> voidRequests = drainRequests();
    assertEquals(3, voidRequests.size());
    assertEquals("POI-LOAD-1", originalStoredValueTransaction(voidRequests.get(0)));
    assertEquals("POI-LOAD-2", originalStoredValueTransaction(voidRequests.get(1)));
    assertNotNull(voidRequests.get(2).getReversalRequest());
  }

  @Test
  void abortedVoidReportsCompletedMovementsStructurally() throws Exception {
    OriginalSaleRecord original =
        OriginalSaleRecord.builder()
            .addStoredValueLoad(
                StoredValueLoadRecord.builder()
                    .basketReference("gift-card-10")
                    .amount(new BigDecimal("10.00"))
                    .poiTransactionId("POI-LOAD-10")
                    .build())
            .addStoredValueLoad(
                StoredValueLoadRecord.builder()
                    .basketReference("gift-card-1")
                    .amount(new BigDecimal("15.00"))
                    .poiTransactionId("POI-LOAD-1")
                    .build())
            .build();
    server.enqueue(
        new MockResponse().setBody(storedValueOk("Reverse", "POI-REVERSE-10", 10.00, 0.00)));
    server.enqueue(new MockResponse().setBody(STORED_VALUE_FAILED));

    List<ReversedMovement> reportedToHandler = new ArrayList<>();
    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .voidTransaction(original)
                    .onError(
                        (step, error) -> {
                          reportedToHandler.addAll(error.getReversedMovements());
                          return ReversalDecision.ABORT;
                        })
                    .get());

    List<ReversedMovement> expected =
        List.of(new ReversedMovement(ReversalStep.STORED_VALUE_LOAD, "POI-LOAD-10"));
    assertEquals(expected, reportedToHandler);
    assertEquals(expected, failure.getError().getReversedMovements());
  }

  @Test
  void declinedOrAbortedChargeReversesTheLoadedValue() throws Exception {
    session.basket().addItem(giftCard("gift-card-1", "Gift card", new BigDecimal("25.00")));
    for (String condition : List.of("Refusal", "Aborted")) {
      server.enqueue(new MockResponse().setBody(storedValueOk("Load", "POI-LOAD-1", 25.00, 25.00)));
      server.enqueue(new MockResponse().setBody(paymentFailure(condition)));
      server.enqueue(
          new MockResponse().setBody(storedValueOk("Reverse", "POI-REVERSE-1", 25.00, 0.00)));

      SessionException failure =
          assertThrows(SessionException.class, () -> session.settle(reloadOptions()).get());

      assertEquals(
          "Aborted".equals(condition) ? SessionErrorCode.ABORTED : SessionErrorCode.DECLINED,
          failure.getError().getCode());
      List<SaleToPOIRequest> requests = drainRequests();
      assertEquals(3, requests.size());
      assertEquals(
          "Load",
          requests
              .get(0)
              .getStoredValueRequest()
              .getStoredValueData()[0]
              .getStoredValueTransactionType()
              .toValue());
      assertNotNull(requests.get(1).getPaymentRequest());
      assertEquals("POI-LOAD-1", originalStoredValueTransaction(requests.get(2)));
      assertEquals(new BigDecimal("25.00"), session.basket().snapshot().getGrandTotal());
    }
  }

  @Test
  void abortAfterLoadingReversesTheLoadWithoutCharging() throws Exception {
    session.basket().addItem(giftCard("gift-card-1", "Gift card", new BigDecimal("25.00")));
    server.enqueue(new MockResponse().setBody(storedValueOk("Load", "POI-LOAD-1", 25.00, 25.00)));
    server.enqueue(
        new MockResponse().setBody(storedValueOk("Reverse", "POI-REVERSE-1", 25.00, 0.00)));

    SessionException failure =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle(reloadOptions())
                    .onStoredValueLoaded(ignored -> session.abort().get())
                    .get());

    assertEquals(SessionErrorCode.ABORTED, failure.getError().getCode());
    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(2, requests.size());
    assertEquals(
        "Load",
        requests
            .get(0)
            .getStoredValueRequest()
            .getStoredValueData()[0]
            .getStoredValueTransactionType()
            .toValue());
    assertEquals("POI-LOAD-1", originalStoredValueTransaction(requests.get(1)));
  }

  @Test
  void retryingTheChargeDoesNotRepeatTheLoad() throws Exception {
    session.basket().addItem(giftCard("gift-card-1", "Gift card", new BigDecimal("25.00")));
    server.enqueue(new MockResponse().setBody(storedValueOk("Load", "POI-LOAD-1", 25.00, 25.00)));
    server.enqueue(new MockResponse().setBody(paymentFailure("Refusal")));
    server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 25.00)));

    List<SettlementFailure> failures = new ArrayList<>();
    SettlementResult result =
        session
            .settle(reloadOptions())
            .onError(
                failure -> {
                  failures.add(failure);
                  return failures.size() == 1
                      ? SettlementRecovery.retry()
                      : SettlementRecovery.abort();
                })
            .get();

    assertTrue(result.isSuccess());
    assertEquals(1, result.getStoredValueLoads().size());
    assertEquals(1, failures.size());
    assertEquals(SettlementStep.CARD_CHARGE, failures.get(0).getStep());
    assertEquals(1, failures.get(0).getCommittedMovements().size());
    assertEquals(
        "POI-LOAD-1", failures.get(0).getCommittedMovements().get(0).getPoiTransactionId());
    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(3, requests.size());
    assertNotNull(requests.get(0).getStoredValueRequest());
    assertNotNull(requests.get(1).getPaymentRequest());
    assertNotNull(requests.get(2).getPaymentRequest());
  }

  @Test
  void failedSecondLoadReversesTheFirstWithoutCharging() throws Exception {
    session.basket().addItem(giftCard("gift-card-1", "Gift card", new BigDecimal("10.00")));
    session.basket().addItem(giftCard("gift-card-2", "Gift card", new BigDecimal("15.00")));
    server.enqueue(new MockResponse().setBody(storedValueOk("Load", "POI-LOAD-1", 10.00, 10.00)));
    server.enqueue(new MockResponse().setBody(STORED_VALUE_FAILED));
    server.enqueue(
        new MockResponse().setBody(storedValueOk("Reverse", "POI-REVERSE-1", 10.00, 0.00)));

    assertThrows(
        SessionException.class,
        () ->
            session
                .settle(
                    SettlementOptions.builder()
                        .addFulfillment(
                            StoredValueLoad.reload("gift-card-1", StoredValueCard.number("GC-1")))
                        .addFulfillment(
                            StoredValueLoad.reload("gift-card-2", StoredValueCard.number("GC-2")))
                        .build())
                .get());

    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(3, requests.size());
    assertEquals(
        "GC-1",
        requests
            .get(0)
            .getStoredValueRequest()
            .getStoredValueData()[0]
            .getStoredValueAccountID()
            .getStoredValueID());
    assertEquals(
        "GC-2",
        requests
            .get(1)
            .getStoredValueRequest()
            .getStoredValueData()[0]
            .getStoredValueAccountID()
            .getStoredValueID());
    assertEquals("POI-LOAD-1", originalStoredValueTransaction(requests.get(2)));
  }

  @Test
  void failedLoadRollbackMustFinishBeforeAnotherSettlement() throws Exception {
    session.basket().addItem(giftCard("gift-card-1", "Gift card", new BigDecimal("25.00")));
    server.enqueue(new MockResponse().setBody(storedValueOk("Load", "POI-LOAD-1", 25.00, 25.00)));
    server.enqueue(new MockResponse().setBody(paymentFailure("Refusal")));
    server.enqueue(new MockResponse().setBody(STORED_VALUE_FAILED));

    SessionException failure =
        assertThrows(SessionException.class, () -> session.settle(reloadOptions()).get());
    assertTrue(failure.getError().getMessage().contains("POI-LOAD-1"));
    List<SaleToPOIRequest> firstAttempt = drainRequests();
    assertEquals(3, firstAttempt.size());
    assertEquals("POI-LOAD-1", originalStoredValueTransaction(firstAttempt.get(2)));
    assertThrows(IllegalStateException.class, () -> session.basket().clear());
    assertThrows(SessionException.class, () -> session.end().get());

    server.enqueue(new MockResponse().setBody(STORED_VALUE_FAILED));
    SessionException retry =
        assertThrows(SessionException.class, () -> session.settle(reloadOptions()).get());
    assertTrue(retry.getError().getMessage().contains("retry did not start"));
    List<SaleToPOIRequest> blockedRetry = drainRequests();
    assertEquals(1, blockedRetry.size());
    assertEquals("POI-LOAD-1", originalStoredValueTransaction(blockedRetry.get(0)));

    server.enqueue(
        new MockResponse().setBody(storedValueOk("Reverse", "POI-REVERSE-1", 25.00, 0.00)));
    server.enqueue(new MockResponse().setBody(storedValueOk("Load", "POI-LOAD-2", 25.00, 25.00)));
    server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-2", 25.00)));
    SettlementResult result = session.settle(reloadOptions()).get();

    assertTrue(result.isSuccess());
    assertEquals("POI-LOAD-2", result.getStoredValueLoads().get(0).getPoiTransactionId());
    List<SaleToPOIRequest> recoveredRetry = drainRequests();
    assertEquals(3, recoveredRetry.size());
    assertEquals("POI-LOAD-1", originalStoredValueTransaction(recoveredRetry.get(0)));
    assertEquals(
        "Load",
        recoveredRetry
            .get(1)
            .getStoredValueRequest()
            .getStoredValueData()[0]
            .getStoredValueTransactionType()
            .toValue());
    assertNotNull(recoveredRetry.get(2).getPaymentRequest());
  }

  @Test
  void loadPrecedesBothSplitTenderCharges() throws Exception {
    session.basket().addItem(giftCard("gift-card-1", "Gift card", new BigDecimal("25.00")));
    session.setStoredValueCard(StoredValueCard.number("GC-TENDER"));
    server.enqueue(new MockResponse().setBody(storedValueOk("Load", "POI-LOAD-1", 25.00, 25.00)));
    server.enqueue(new MockResponse().setBody(paymentOk("POI-TENDER-1", 10.00)));
    server.enqueue(new MockResponse().setBody(paymentOk("POI-PAY-1", 15.00)));

    SettlementResult result = session.settle(reloadOptions()).get();

    assertEquals(new BigDecimal("25.00"), result.getStoredValueLoadedAmount());
    assertEquals(new BigDecimal("10.00"), result.getStoredValueAmountUsed());
    assertEquals(new BigDecimal("15.00"), result.getCardAmountCharged());
    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(3, requests.size());
    assertEquals(
        "Load",
        requests
            .get(0)
            .getStoredValueRequest()
            .getStoredValueData()[0]
            .getStoredValueTransactionType()
            .toValue());
    assertEquals(
        25.00,
        requests
            .get(1)
            .getPaymentRequest()
            .getPaymentTransaction()
            .getAmountsReq()
            .getRequestedAmount());
    assertEquals(
        15.00,
        requests
            .get(2)
            .getPaymentRequest()
            .getPaymentTransaction()
            .getAmountsReq()
            .getRequestedAmount());
  }

  @Test
  void externalFundingKeepsTheCompletedLoad() throws Exception {
    session.basket().addItem(giftCard("gift-card-1", "Gift card", new BigDecimal("25.00")));
    server.enqueue(new MockResponse().setBody(storedValueOk("Load", "POI-LOAD-1", 25.00, 25.00)));
    server.enqueue(new MockResponse().setBody(paymentFailure("Refusal")));

    SettlementResult result =
        session
            .settle(reloadOptions())
            .onError(
                failure ->
                    SettlementRecovery.external(
                        ExternalPayment.cash(failure.getAmountDue(), "DRAWER-1")))
            .get();

    assertTrue(result.isSuccess());
    assertEquals(1, result.getStoredValueLoads().size());
    assertEquals(new BigDecimal("25.00"), result.getStoredValueLoadedAmount());
    assertEquals(new BigDecimal("25.00"), result.getExternalPaymentAmount());
    assertEquals(0, BigDecimal.ZERO.compareTo(result.getCardAmountCharged()));
    List<SaleToPOIRequest> requests = drainRequests();
    assertEquals(2, requests.size());
    assertNotNull(requests.get(0).getStoredValueRequest());
    assertNotNull(requests.get(1).getPaymentRequest());
  }

  private static SettlementOptions reloadOptions() {
    return SettlementOptions.builder()
        .addFulfillment(StoredValueLoad.reload("gift-card-1", StoredValueCard.number("GC-1")))
        .build();
  }

  private static String paymentFailure(String condition) {
    return "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\""
        + condition
        + "\"}}}}";
  }

  private static String originalStoredValueTransaction(SaleToPOIRequest request) {
    return request
        .getStoredValueRequest()
        .getStoredValueData()[0]
        .getOriginalPOITransaction()
        .getPoiTransactionID()
        .getTransactionID();
  }

  private static BasketItem giftCard(String reference, String description, BigDecimal amount) {
    return BasketItem.sale("GIFT-CARD", description, 1, amount).withReference(reference);
  }

  private SaleToPOIRequest recordedRequest() throws Exception {
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
        + "\","
        + "\"TimeStamp\":\"2026-08-31T10:00:00Z\"}},"
        + "\"PaymentResult\":{\"AmountsResp\":{\"Currency\":\"USD\","
        + "\"AuthorizedAmount\":"
        + authorized
        + "}}}}}";
  }

  private static String storedValueOk(String type, String poiTxn, double amount, double balance) {
    return "{\"SaleToPOIResponse\":{\"StoredValueResponse\":{"
        + "\"Response\":{\"Result\":\"Success\"},"
        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\""
        + poiTxn
        + "\","
        + "\"TimeStamp\":\"2026-08-31T10:00:01Z\"}},"
        + "\"StoredValueResult\":[{"
        + "\"StoredValueTransactionType\":\""
        + type
        + "\","
        + "\"ItemAmount\":"
        + amount
        + ",\"Currency\":\"USD\","
        + "\"StoredValueAccountStatus\":{\"CurrentBalance\":"
        + balance
        + "}}]}}}";
  }
}
