package com.bilt.pos.session.settlement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bilt.pos.session.storedvalue.StoredValueCard;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefundAllocationTest {

  private static final Instant ORIGINAL_TIMESTAMP = Instant.parse("2026-01-02T03:04:05Z");

  @Test
  void linkedCardRefundRequiresOriginalTransactionId() {
    NullPointerException error =
        assertThrows(
            NullPointerException.class,
            () -> RefundAllocation.card(new BigDecimal("10.00"), null, ORIGINAL_TIMESTAMP));

    assertTrue(error.getMessage().contains("originalPoiTransactionId"));
  }

  @Test
  void linkedCardRefundFromOriginalSaleRequiresCardLeg() {
    OriginalSaleRecord originalSale =
        OriginalSaleRecord.builder()
            .storedValuePoiTransactionId("POI-SV-ORIG")
            .storedValuePoiTransactionTimestamp(ORIGINAL_TIMESTAMP)
            .build();

    NullPointerException error =
        assertThrows(
            NullPointerException.class,
            () -> RefundAllocation.card(new BigDecimal("10.00"), originalSale));

    assertTrue(error.getMessage().contains("card leg"));
    assertTrue(error.getMessage().contains("cardUnlinked"));
  }

  @Test
  void linkedCardRefundFromOriginalSaleTargetsItsOnlyCardPayment() {
    OriginalSaleRecord originalSale =
        OriginalSaleRecord.builder()
            .addCardPayment(
                CardPaymentRecord.builder()
                    .poiTransactionId("POI-CARD-ORIG")
                    .poiTransactionTimestamp(ORIGINAL_TIMESTAMP)
                    .amount(new BigDecimal("40.00"))
                    .build())
            .build();

    RefundAllocation allocation = RefundAllocation.card(new BigDecimal("10.00"), originalSale);

    assertEquals("POI-CARD-ORIG", allocation.getOriginalPoiTransactionId());
    assertEquals(ORIGINAL_TIMESTAMP, allocation.getOriginalPoiTransactionTimestamp());
  }

  @Test
  void linkedCardRefundFromOriginalSaleWithSeveralCardPaymentsIsRefused() {
    OriginalSaleRecord originalSale =
        OriginalSaleRecord.builder()
            .addCardPayment(CardPaymentRecord.builder().poiTransactionId("POI-CARD-1").build())
            .addCardPayment(CardPaymentRecord.builder().poiTransactionId("POI-CARD-2").build())
            .build();

    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> RefundAllocation.card(new BigDecimal("10.00"), originalSale));

    assertTrue(error.getMessage().contains("2 card payments"));
    assertTrue(error.getMessage().contains("getCardPayments()"));
  }

  @Test
  void unlinkedCardRefundDoesNotRequireOriginalTransactionId() {
    assertDoesNotThrow(() -> RefundAllocation.cardUnlinked(new BigDecimal("10.00")));
  }

  @Test
  void externalRefundCountsTowardRefundTotalWithoutOriginalTransactionId() {
    RefundAllocation allocation = RefundAllocation.external(new BigDecimal("10.00"));

    assertEquals(RefundAllocationType.EXTERNAL, allocation.getType());
    assertTrue(allocation.countsTowardRefundTotal());
    assertEquals(new BigDecimal("10.00"), allocation.getAmount());
    assertNull(allocation.getOriginalPoiTransactionId());
  }

  @Test
  void equalityComparesAmountsByNumericValue() {
    RefundAllocation oneDecimalPlace =
        RefundAllocation.card(new BigDecimal("10.0"), "POI-ORIG", ORIGINAL_TIMESTAMP);
    RefundAllocation twoDecimalPlaces =
        RefundAllocation.card(new BigDecimal("10.00"), "POI-ORIG", ORIGINAL_TIMESTAMP);

    assertEquals(oneDecimalPlace, twoDecimalPlaces);
    assertEquals(oneDecimalPlace.hashCode(), twoDecimalPlaces.hashCode());
  }

  @Test
  void equalityIncludesOriginalReferenceAndMemberId() {
    RefundAllocation original =
        RefundAllocation.pointRedemption(
            new BigDecimal("10.00"), "POI-ORIG", ORIGINAL_TIMESTAMP, "member-1");
    RefundAllocation differentReference =
        RefundAllocation.pointRedemption(
            new BigDecimal("10.00"), "POI-OTHER", ORIGINAL_TIMESTAMP, "member-1");
    RefundAllocation differentMember =
        RefundAllocation.pointRedemption(
            new BigDecimal("10.00"), "POI-ORIG", ORIGINAL_TIMESTAMP, "member-2");

    assertNotEquals(original, differentReference);
    assertNotEquals(original, differentMember);
  }

  @Test
  void equalityIncludesStoredValueCard() {
    RefundAllocation original =
        RefundAllocation.storeCredit(
            StoredValueCard.number("GC-1").withProvider("svs"), new BigDecimal("10.00"));
    RefundAllocation same =
        RefundAllocation.storeCredit(
            StoredValueCard.number("GC-1").withProvider("svs"), new BigDecimal("10.0"));
    RefundAllocation differentCard =
        RefundAllocation.storeCredit(
            StoredValueCard.number("GC-2").withProvider("svs"), new BigDecimal("10.00"));

    assertEquals(original, same);
    assertEquals(original.hashCode(), same.hashCode());
    assertNotEquals(original, differentCard);
  }
}
