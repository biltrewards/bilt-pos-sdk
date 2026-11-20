package com.bilt.pos.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TenderAmountTest {

  @Test
  void equalityIgnoresScaleAndCoversTenderAndReference() {
    TenderAmount card = new TenderAmount(TenderType.CARD, "POI-1", new BigDecimal("20.00"));

    assertEquals(card, new TenderAmount(TenderType.CARD, "POI-1", new BigDecimal("20")));
    assertEquals(
        card.hashCode(),
        new TenderAmount(TenderType.CARD, "POI-1", new BigDecimal("20")).hashCode());
    assertNotEquals(card, new TenderAmount(TenderType.STORED_VALUE, "POI-1", new BigDecimal("20")));
    assertNotEquals(card, new TenderAmount(TenderType.CARD, "POI-2", new BigDecimal("20")));
    assertNotEquals(card, new TenderAmount(TenderType.CARD, "POI-1", null));
  }

  @Test
  void onlyTheTenderIsRequired() {
    TenderAmount external = new TenderAmount(TenderType.EXTERNAL, null, null);

    assertNull(external.getPoiTransactionId());
    assertNull(external.getAmount());
    assertThrows(NullPointerException.class, () -> new TenderAmount(null, "POI-1", BigDecimal.ONE));
  }
}
