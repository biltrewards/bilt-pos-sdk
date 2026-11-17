package com.bilt.pos.session;

import static org.junit.jupiter.api.Assertions.*;

import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.settlement.Authorization;
import com.bilt.pos.session.settlement.ReservationReference;
import com.bilt.pos.session.settlement.SettlementLoyalty;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.bilt.pos.session.settlement.SettlementRecovery;
import com.bilt.pos.session.settlement.SettlementResult;
import com.bilt.pos.session.settlement.SettlementStep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TerminalShopperSessionPreAuthTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private MockWebServer server;
  private TerminalShopperSession session;

  @BeforeEach
  void start() throws Exception {
    server = new MockWebServer();
    server.start();
    session = startSession(false);
  }

  private TerminalShopperSession startSession(boolean autoDisplay) throws Exception {
    reply(TerminalShopperSessionTest.ADMIN_OK);
    TerminalShopperSession result =
        TerminalShopperSession.builder()
            .client(
                BiltNexoTerminalClient.builder()
                    .endpoint(server.url("/nexo").toString())
                    .disableRecoveryOnNetworkError()
                    .build())
            .saleId("REGISTER-2")
            .poiId("POI-1")
            .currency("USD")
            .autoDisplay(autoDisplay)
            .start()
            .get();
    next();
    return result;
  }

  @AfterEach
  void stop() throws Exception {
    server.shutdown();
  }

  private void reply(String json) {
    server.enqueue(new MockResponse().setBody(json));
  }

  private JsonNode next() throws Exception {
    var request = server.takeRequest(5, TimeUnit.SECONDS);
    assertNotNull(request);
    return mapper.readTree(request.getBody().readUtf8()).path("SaleToPOIRequest");
  }

  private void basket(String amount) {
    session.basket().addItem(BasketItem.sale("SKU-1", "Cleaning", 1, new BigDecimal(amount)));
  }

  private static ReservationReference reference(String amount) {
    return ReservationReference.builder()
        .saleReferenceId("ORDER-1")
        .poiTransactionId("HOLD-1")
        .poiTransactionTimestamp(Instant.parse("2026-07-20T10:00:00Z"))
        .authorizedAmount(new BigDecimal(amount))
        .validityDate("2026-07-30")
        .saleId("REGISTER-1")
        .poiId("POI-1")
        .build();
  }

  private static SettlementOptions options(Authorization auth) {
    return SettlementOptions.builder().authorization(auth).build();
  }

  private static String payment(String result, String id, String amount) {
    return "{\"SaleToPOIResponse\":{\"PaymentResponse\":{\"Response\":{\"Result\":\""
        + result
        + "\"},\"POIData\":{\"POITransactionID\":{\"TransactionID\":\""
        + id
        + "\",\"TimeStamp\":\"2026-07-20T10:00:01Z\"}},\"PaymentResult\":{\"ValidityDate\":\"2026-07-30\","
        + "\"AmountsResp\":{\"Currency\":\"USD\",\"AuthorizedAmount\":"
        + amount
        + "}}}}}";
  }

  private static String failure(String error) {
    return "{\"SaleToPOIResponse\":{\"PaymentResponse\":{\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\""
        + error
        + "\"}}}}";
  }

  @Test
  void firstReservationIsCardOnlyAndEchoesReference() throws Exception {
    basket("100.00");
    reply(
        "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{\"Response\":{\"Result\":\"Success\"},\"LoyaltyAccount\":[{\"LoyaltyAccountID\":{\"LoyaltyID\":\"98234\"}}]}}}");
    session.identifyMember().get();
    next();
    session.setStoredValueCard("GC-1");
    reply(payment("Success", "NEW-HOLD", "100.00"));
    SettlementResult result =
        session
            .settle(
                options(
                    Authorization.builder()
                        .mode(Authorization.Mode.PRE_AUTH)
                        .saleReferenceId("ORDER-1")
                        .requestedValidityDate("2026-07-31")
                        .build()))
            .get();
    JsonNode body = next().path("PaymentRequest");
    assertEquals("FirstReservation", body.at("/PaymentData/PaymentType").asText());
    assertEquals("2026-07-31", body.at("/PaymentData/RequestedValidityDate").asText());
    assertEquals("ORDER-1", body.at("/SaleData/SaleReferenceID").asText());
    assertEquals(
        "Forbidden", body.at("/PaymentTransaction/TransactionConditions/LoyaltyHandling").asText());
    assertTrue(body.at("/PaymentTransaction/AmountsReq/CashBackAmount").isMissingNode());
    assertTrue(
        body.at("/PaymentTransaction/TransactionConditions/SplitPaymentFlag").isMissingNode());
    assertEquals(1, result.getMovements().size());
    var movement = result.getMovements().get(0);
    assertEquals("FirstReservation", movement.getPaymentType());
    assertEquals("NEW-HOLD", movement.getReservation().getPoiTransactionId());
    assertEquals("2026-07-30", movement.getReservation().getValidityDate());
    assertEquals("REGISTER-2", movement.getReservation().getSaleId());
    assertEquals(new BigDecimal("100.00"), movement.getReservation().getAuthorizedAmount());
    assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS));
  }

  @Test
  void partialHoldAndUpdateRecordActualAuthorizedAmount() throws Exception {
    for (Authorization auth :
        new Authorization[] {
          Authorization.preAuth("ORDER-1"), Authorization.update(reference("100.00"))
        }) {
      basket("100.00");
      reply(payment("Partial", "PART-HOLD", "60.00"));
      var result = session.settle(options(auth)).get();
      assertEquals(new BigDecimal("60.00"), result.getAuthorizedAmount());
      assertEquals(
          new BigDecimal("60.00"),
          result.getMovements().get(0).getReservation().getAuthorizedAmount());
      next();
      session.basket().clear();
    }
  }

  @Test
  void updateCarriesOriginalLaneAndPoiReference() throws Exception {
    basket("80.00");
    reply(payment("Success", "HOLD-2", "80.00"));
    session.settle(options(Authorization.update(reference("100.00")))).get();
    JsonNode body = next().path("PaymentRequest");
    assertEquals("UpdateReservation", body.at("/PaymentData/PaymentType").asText());
    assertEquals("ORDER-1", body.at("/SaleData/SaleReferenceID").asText());
    assertEquals(
        "HOLD-1",
        body.at("/PaymentTransaction/OriginalPOITransaction/POITransactionID/TransactionID")
            .asText());
    assertEquals(
        "REGISTER-1", body.at("/PaymentTransaction/OriginalPOITransaction/SaleID").asText());
  }

  @Test
  void completionAtOrBelowHoldDoesNotUpdate() throws Exception {
    for (String amount : new String[] {"100.00", "80.00"}) {
      basket(amount);
      reply(payment("Success", "CAPTURE", amount));
      session.settle(options(Authorization.complete(reference("100.00")))).get();
      JsonNode body = next().path("PaymentRequest");
      assertEquals("Completion", body.at("/PaymentData/PaymentType").asText());
      assertEquals(
          "HOLD-1",
          body.at("/PaymentTransaction/OriginalPOITransaction/POITransactionID/TransactionID")
              .asText());
      assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS));
      session.basket().clear();
    }
  }

  @Test
  void overAmountCompletionCommitsUpdateThenUsesItsReference() throws Exception {
    basket("120.00");
    reply(payment("Success", "RAISED-HOLD", "120.00"));
    reply(payment("Success", "CAPTURE", "120.00"));
    var result = session.settle(options(Authorization.complete(reference("100.00")))).get();
    JsonNode update = next();
    JsonNode complete = next();
    assertEquals(
        "UpdateReservation", update.at("/PaymentRequest/PaymentData/PaymentType").asText());
    assertEquals("Completion", complete.at("/PaymentRequest/PaymentData/PaymentType").asText());
    assertEquals(
        "RAISED-HOLD",
        complete
            .at(
                "/PaymentRequest/PaymentTransaction/OriginalPOITransaction/POITransactionID/TransactionID")
            .asText());
    assertNotEquals(
        update.at("/PaymentRequest/SaleData/SaleTransactionID/TransactionID"),
        complete.at("/PaymentRequest/SaleData/SaleTransactionID/TransactionID"));
    assertNotEquals(update.at("/MessageHeader/ServiceID"), complete.at("/MessageHeader/ServiceID"));
    assertEquals(2, result.getMovements().size());
    assertEquals(
        "RAISED-HOLD", result.getMovements().get(0).getReservation().getPoiTransactionId());
    assertEquals(new BigDecimal("120.00"), result.getAuthorizedAmount());
  }

  @Test
  void completionWithNoCardRemainderReleasesHoldAndReportsIt() throws Exception {
    basket("100.00");
    session.setStoredValueCard("GC-1");
    reply(payment("Success", "GIFT-1", "100.00"));
    reply(TerminalShopperSessionTest.REVERSAL_OK);
    var result = session.settle(options(Authorization.complete(reference("100.00")))).get();
    assertTrue(result.isSuccess());
    assertEquals(0, result.getCardAmountCharged().signum());
    assertEquals(new BigDecimal("100.00"), result.getStoredValueAmountUsed());
    assertEquals(
        java.util.List.of("Reservation HOLD-1 released because no card balance remains"),
        result.getWarnings());
    assertEquals(1, result.getMovements().size());
    assertEquals(
        "StoredValue",
        next()
            .at("/PaymentRequest/PaymentData/PaymentInstrumentData/PaymentInstrumentType")
            .asText());
    JsonNode release = next().path("ReversalRequest");
    assertEquals("MerchantCancel", release.path("ReversalReason").asText());
    assertEquals("ORDER-1", release.path("SaleReferenceID").asText());
    assertEquals(
        "HOLD-1", release.at("/OriginalPOITransaction/POITransactionID/TransactionID").asText());
    assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS));
  }

  @Test
  void failedZeroRemainderReleaseFailsSettlementAndUnwindsOtherTender() throws Exception {
    basket("100.00");
    session.setStoredValueCard("GC-1");
    reply(payment("Success", "GIFT-1", "100.00"));
    reply(
        "{\"SaleToPOIResponse\":{\"ReversalResponse\":{\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"NotAllowed\"}}}}");
    reply(TerminalShopperSessionTest.REVERSAL_OK);
    assertThrows(
        SessionException.class,
        () -> session.settle(options(Authorization.complete(reference("100.00")))).get());
    next();
    assertEquals(
        "HOLD-1",
        next()
            .at("/ReversalRequest/OriginalPOITransaction/POITransactionID/TransactionID")
            .asText());
    assertEquals(
        "GIFT-1",
        next()
            .at("/ReversalRequest/OriginalPOITransaction/POITransactionID/TransactionID")
            .asText());
    assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS));
  }

  @Test
  void indeterminateReleaseRecoversReversalBeforeRetryingOrRollingBack() throws Exception {
    reply(TerminalShopperSessionTest.ADMIN_OK);
    session =
        TerminalShopperSession.builder()
            .client(
                BiltNexoTerminalClient.builder()
                    .endpoint(server.url("/nexo").toString())
                    .readTimeout(Duration.ofMillis(100))
                    .disableRecoveryOnNetworkError()
                    .build())
            .saleId("REGISTER-2")
            .poiId("POI-1")
            .currency("USD")
            .autoDisplay(false)
            .start()
            .get();
    next();
    basket("100.00");
    session.setStoredValueCard("GC-1");
    reply(payment("Success", "GIFT-1", "100.00"));
    server.enqueue(
        new MockResponse()
            .setBodyDelay(500, TimeUnit.MILLISECONDS)
            .setBody(TerminalShopperSessionTest.REVERSAL_OK));
    reply(
        "{\"SaleToPOIResponse\":{\"TransactionStatusResponse\":{\"Response\":{\"Result\":\"Success\"}}}}");
    reply(
        "{\"SaleToPOIResponse\":{\"TransactionStatusResponse\":{\"Response\":{\"Result\":\"Success\"},\"RepeatedMessageResponse\":{\"MessageHeader\":{\"MessageCategory\":\"Reversal\"},\"RepeatedResponseMessageBody\":{\"ReversalResponse\":{\"Response\":{\"Result\":\"Success\"}}}}}}}");
    AtomicInteger errors = new AtomicInteger();
    var result =
        session
            .settle(options(Authorization.complete(reference("100.00"))))
            .onError(
                failure -> {
                  assertTrue(failure.isIndeterminate());
                  errors.incrementAndGet();
                  return SettlementRecovery.retry();
                })
            .get();
    assertTrue(result.isSuccess());
    assertEquals(1, errors.get());
    assertEquals(1, result.getWarnings().size());
    next();
    JsonNode release = next();
    for (int i = 0; i < 2; i++) {
      JsonNode status = next().at("/TransactionStatusRequest/MessageReference");
      assertEquals("Reversal", status.path("MessageCategory").asText());
      assertEquals(
          release.at("/MessageHeader/ServiceID").asText(), status.path("ServiceID").asText());
    }
    assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS));
  }

  @Test
  void awardFinishesBeforeZeroRemainderReleaseAndAbortKeepsHold() throws Exception {
    reply(
        "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{\"Response\":{\"Result\":\"Success\"},\"LoyaltyAccount\":[{\"LoyaltyAccountID\":{\"LoyaltyID\":\"98234\"}}]}}}");
    session.identifyMember().get();
    next();
    for (boolean abort : new boolean[] {false, true}) {
      session.basket().clear();
      basket("100.00");
      session.setStoredValueCard("GC-1");
      reply(payment("Success", "GIFT-1", "100.00"));
      reply(
          "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{\"Response\":{\"Result\":\"Success\"},\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"AWARD-1\"}}}}}");
      if (abort) {
        reply(TerminalShopperSessionTest.LOYALTY_REFUND_OK);
      }
      reply(TerminalShopperSessionTest.REVERSAL_OK);
      var flow =
          session
              .settle(
                  SettlementOptions.builder()
                      .authorization(Authorization.complete(reference("100.00")))
                      .loyalty(SettlementLoyalty.explicit(Set.of(SettlementLoyalty.Step.AWARD)))
                      .build())
              .onMovement(
                  movement -> {
                    if (abort && movement.getStep() == SettlementStep.AWARD) {
                      session.abort().executeSync();
                    }
                  });
      if (abort) {
        assertEquals(
            SessionErrorCode.ABORTED,
            assertThrows(SessionException.class, flow::get).getError().getCode());
      } else {
        assertTrue(flow.get().isSuccess());
      }
      assertTrue(next().has("PaymentRequest"));
      assertEquals(
          "Award", next().at("/LoyaltyRequest/LoyaltyTransaction/LoyaltyTransactionType").asText());
      if (abort) {
        assertEquals(
            "AwardRefund",
            next().at("/LoyaltyRequest/LoyaltyTransaction/LoyaltyTransactionType").asText());
      }
      assertEquals(
          abort ? "GIFT-1" : "HOLD-1",
          next()
              .at("/ReversalRequest/OriginalPOITransaction/POITransactionID/TransactionID")
              .asText());
      assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS));
    }
  }

  @Test
  void abortJustBeforeReleaseUnwindsEachCommittedLegExactlyOnce() throws Exception {
    reply(
        "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{\"Response\":{\"Result\":\"Success\"},\"LoyaltyAccount\":[{\"LoyaltyAccountID\":{\"LoyaltyID\":\"98234\"}}]}}}");
    session.identifyMember().get();
    next();
    basket("100.00");
    session.setStoredValueCard("GC-1");
    reply(payment("Success", "GIFT-1", "100.00"));
    reply(
        "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{\"Response\":{\"Result\":\"Success\"},\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"AWARD-1\"}}}}}");
    reply(TerminalShopperSessionTest.LOYALTY_REFUND_OK);
    reply(TerminalShopperSessionTest.REVERSAL_OK);
    var flow =
        session
            .settle(
                SettlementOptions.builder()
                    .authorization(Authorization.complete(reference("100.00")))
                    .loyalty(SettlementLoyalty.explicit(Set.of(SettlementLoyalty.Step.AWARD)))
                    .build())
            .beforeStep(
                context -> {
                  if (context.getStep() == SettlementStep.CARD_CHARGE) {
                    session.abort().executeSync();
                  }
                  return context.getDefaultTransactionId();
                })
            .onError(
                failure -> {
                  fail("intentional cancellation must not consult onError");
                  return SettlementRecovery.abort();
                });
    assertEquals(
        SessionErrorCode.ABORTED,
        assertThrows(SessionException.class, flow::get).getError().getCode());
    assertTrue(next().has("PaymentRequest"));
    assertEquals(
        "Award", next().at("/LoyaltyRequest/LoyaltyTransaction/LoyaltyTransactionType").asText());
    assertEquals(
        "AwardRefund",
        next().at("/LoyaltyRequest/LoyaltyTransaction/LoyaltyTransactionType").asText());
    assertEquals(
        "GIFT-1",
        next()
            .at("/ReversalRequest/OriginalPOITransaction/POITransactionID/TransactionID")
            .asText());
    assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS));
  }

  @Test
  void failedOrPartialCompletionDoesNotReverseHold() throws Exception {
    for (String response :
        new String[] {failure("NotAllowed"), payment("Partial", "PART-CAPTURE", "20.00")}) {
      if (!session.basket().snapshot().isEmpty()) {
        session.basket().clear();
      }
      basket("80.00");
      reply(response);
      assertThrows(
          SessionException.class,
          () -> session.settle(options(Authorization.complete(reference("100.00")))).get());
      assertEquals("Completion", next().at("/PaymentRequest/PaymentData/PaymentType").asText());
      assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS));
    }
  }

  @Test
  void failedRaiseDoesNotAttemptCompletion() throws Exception {
    basket("120.00");
    reply(payment("Partial", "SMALLER-HOLD", "110.00"));
    assertThrows(
        SessionException.class,
        () -> session.settle(options(Authorization.complete(reference("100.00")))).get());
    assertEquals(
        "UpdateReservation", next().at("/PaymentRequest/PaymentData/PaymentType").asText());
    assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS));
  }

  @Test
  void cashbackAndMissingReferenceAreRejectedBeforePayment() throws Exception {
    basket("100.00");
    var error =
        assertThrows(
            SessionException.class,
            () ->
                session
                    .settle(
                        SettlementOptions.builder()
                            .authorization(Authorization.preAuth("ORDER-1"))
                            .cashback(BigDecimal.ONE)
                            .build())
                    .get());
    assertEquals(SessionErrorCode.INVALID_STATE, error.getError().getCode());
    assertThrows(
        SessionException.class, () -> session.settle(options(Authorization.complete(null))).get());
    assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS));
  }

  @Test
  void emptyReservationBasketIsRejectedBeforePayment() throws Exception {
    assertThrows(
        SessionException.class,
        () -> session.settle(options(Authorization.preAuth("ORDER-1"))).get());
    assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS));
  }

  @Test
  void netRefundsAndNonpositiveBasketAreRejectedBeforeAnyMoneyMoves() throws Exception {
    basket("100.00");
    session.basket().addItem(BasketItem.returnItem("RET-1", "Return", 1, new BigDecimal("20.00")));
    assertThrows(
        SessionException.class,
        () ->
            session
                .settle(
                    SettlementOptions.builder()
                        .authorization(Authorization.preAuth("ORDER-1"))
                        .settlementType(com.bilt.pos.session.settlement.SettlementType.NET)
                        .build())
                .get());
    session.basket().addItem(BasketItem.returnItem("RET-2", "Return", 1, new BigDecimal("100.00")));
    assertThrows(
        SessionException.class,
        () -> session.settle(options(Authorization.preAuth("ORDER-1"))).get());
    assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS));
  }

  @Test
  void malformedHoldResponseDoesNotInventAuthorizedAmount() throws Exception {
    basket("100.00");
    reply(
        "{\"SaleToPOIResponse\":{\"PaymentResponse\":{\"Response\":{\"Result\":\"Partial\"},\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"HOLD-1\",\"TimeStamp\":\"2026-07-20T10:00:00Z\"}}}}}");
    reply(TerminalShopperSessionTest.REVERSAL_OK);
    assertThrows(
        SessionException.class,
        () -> session.settle(options(Authorization.preAuth("ORDER-1"))).get());
    next();
    assertEquals("ORDER-1", next().at("/ReversalRequest/SaleReferenceID").asText());
  }

  @Test
  void defaultHoldDoesNotPushFinalBasketWithAutoDisplayEnabled() throws Exception {
    session = startSession(true);
    server.setDispatcher(
        new okhttp3.mockwebserver.Dispatcher() {
          @Override
          public MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest request) {
            String body = request.getBody().clone().readUtf8();
            return new MockResponse()
                .setBody(
                    body.contains("PaymentRequest")
                        ? payment("Success", "HOLD-2", "100.00")
                        : TerminalShopperSessionTest.ADMIN_OK);
          }
        });
    basket("100.00");
    session.settle(options(Authorization.preAuth("ORDER-1"))).get();
    JsonNode request;
    do {
      request = next();
    } while (!request.has("PaymentRequest"));
    assertNull(
        server.takeRequest(100, TimeUnit.MILLISECONDS),
        "hold must not refresh the basket after authorization");
  }

  @Test
  void abortingNewHoldReleasesItWithOrderReference() throws Exception {
    basket("100.00");
    reply(payment("Success", "NEW-HOLD", "100.00"));
    reply(TerminalShopperSessionTest.REVERSAL_OK);
    assertThrows(
        SessionException.class,
        () ->
            session
                .settle(options(Authorization.preAuth("ORDER-1")))
                .onMovement(m -> session.abort().executeSync())
                .get());
    next();
    assertEquals("ORDER-1", next().at("/ReversalRequest/SaleReferenceID").asText());
  }

  @Test
  void followUpRetriesBusyThreeTimesWithNewServiceIds() throws Exception {
    basket("80.00");
    reply(failure("Busy"));
    reply(failure("Busy"));
    reply(failure("Busy"));
    reply(payment("Success", "HOLD-2", "80.00"));
    var result = session.settle(options(Authorization.update(reference("100.00")))).get();
    var ids = new java.util.HashSet<String>();
    var transactionIds = new java.util.HashSet<String>();
    for (int i = 0; i < 4; i++) {
      JsonNode request = next();
      ids.add(request.at("/MessageHeader/ServiceID").asText());
      transactionIds.add(
          request.at("/PaymentRequest/SaleData/SaleTransactionID/TransactionID").asText());
    }
    assertEquals(4, ids.size());
    assertEquals(4, transactionIds.size());
    assertEquals(1, result.getMovements().size());
  }

  @Test
  void holdDoesNotRetryBusyAsFollowUp() throws Exception {
    basket("100.00");
    reply(failure("Busy"));
    assertThrows(
        SessionException.class,
        () -> session.settle(options(Authorization.preAuth("ORDER-1"))).get());
    next();
    assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS));
  }

  @Test
  void releaseIsStandaloneMerchantCancelAndSupportsErrors() throws Exception {
    reply(TerminalShopperSessionTest.REVERSAL_OK);
    assertTrue(session.releaseReservation(reference("100.00")).get().isSuccess());
    JsonNode body = next().path("ReversalRequest");
    assertEquals("MerchantCancel", body.path("ReversalReason").asText());
    assertEquals("ORDER-1", body.path("SaleReferenceID").asText());
    assertEquals(
        "HOLD-1", body.at("/OriginalPOITransaction/POITransactionID/TransactionID").asText());
    reply(
        "{\"SaleToPOIResponse\":{\"ReversalResponse\":{\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"NotFound\"}}}}");
    assertThrows(
        SessionException.class, () -> session.releaseReservation(reference("100.00")).get());
    next();
    assertThrows(SessionException.class, () -> session.releaseReservation(null).get());
  }

  @Test
  void sameSessionVoidRetainsReservationSaleReference() throws Exception {
    basket("100.00");
    reply(payment("Success", "NEW-HOLD", "100.00"));
    session.settle(options(Authorization.preAuth("ORDER-1"))).get();
    next();
    reply(TerminalShopperSessionTest.REVERSAL_OK);
    assertTrue(session.voidTransaction().get().isSuccess());
    assertEquals("ORDER-1", next().at("/ReversalRequest/SaleReferenceID").asText());
  }
}
