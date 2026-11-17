package com.bilt.pos.host.internal;

import static org.junit.jupiter.api.Assertions.*;

import com.bilt.pos.session.settlement.Authorization;
import com.bilt.pos.session.settlement.SettlementMovement;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.bilt.pos.session.settlement.SettlementStep;
import org.junit.jupiter.api.Test;

class AuthorizationJsonTest {
  @Test
  void omittedAuthorizationRemainsSale() throws Exception {
    assertEquals(
        Authorization.Mode.SALE,
        Parsers.settlementOptions(Json.MAPPER.readTree("{}")).getAuthorization().getMode());
  }

  @Test
  void malformedAuthorizationNeverFallsBackToSale() throws Exception {
    for (String authorization :
        new String[] {
          "null",
          "{}",
          "\"PRE_AUTH\"",
          "{\"mode\":\"UNKNOWN\"}",
          "{\"mode\":\"PRE_AUTH\"}",
          "{\"mode\":\"PRE_AUTH_COMPLETE\"}"
        }) {
      var options = Json.MAPPER.readTree("{\"authorization\":" + authorization + "}");
      assertThrows(HostError.class, () -> Parsers.settlementOptions(options));
    }
  }

  @Test
  void holdAndFollowUpRoundTripThroughHostViews() throws Exception {
    var hold =
        Parsers.settlementOptions(
            Json.MAPPER.readTree(
                "{\"authorization\":{\"mode\":\"PRE_AUTH\",\"saleReferenceId\":\"ORDER-1\"}}"));
    assertEquals(Authorization.Mode.PRE_AUTH, hold.getAuthorization().getMode());
    assertFalse(hold.getAuthorization().isDisplayCart());
    var reference =
        Parsers.reservationReference(
            Json.MAPPER.readTree(
                "{\"saleReferenceId\":\"ORDER-1\",\"poiTransactionId\":\"HOLD-1\",\"poiTransactionTimestamp\":\"2026-07-20T10:00:00Z\",\"authorizedAmount\":\"100.00\",\"saleId\":\"LANE-1\"}"));
    for (Authorization auth :
        new Authorization[] {
          hold.getAuthorization(),
          Authorization.update(reference),
          Authorization.complete(reference)
        }) {
      var result =
          Parsers.settlementOptions(
              Views.settlementOptions(SettlementOptions.builder().authorization(auth).build()));
      assertEquals(auth.getMode(), result.getAuthorization().getMode());
      if (auth.getReference() != null) {
        assertEquals(
            reference.getPoiTransactionId(),
            result.getAuthorization().getReference().getPoiTransactionId());
        assertEquals(
            reference.getAuthorizedAmount(),
            result.getAuthorization().getReference().getAuthorizedAmount());
      }
    }
    var movement =
        Views.movement(
            SettlementMovement.builder()
                .step(SettlementStep.CARD_CHARGE)
                .paymentType("FirstReservation")
                .reservation(reference)
                .build());
    assertEquals("FirstReservation", movement.path("paymentType").asText());
    assertEquals("100.00", movement.at("/reservation/authorizedAmount").asText());
    assertEquals("LANE-1", movement.at("/reservation/saleId").asText());
  }

  @Test
  void invalidReleaseReferenceIsRejected() {
    assertThrows(HostError.class, () -> Parsers.reservationReference(Json.object()));
    assertThrows(HostError.class, () -> Parsers.reservationReference(null));
  }
}
