package com.bilt.pos.session.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bilt.pos.session.payment.CardPaymentResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OriginalSaleRecordTest {

  private static final Instant ORIGINAL_TIMESTAMP = Instant.parse("2026-01-02T03:04:05Z");

  @Test
  void equalityIncludesReferencesAndMemberId() {
    OriginalSaleRecord original = record("CARD-POI", "member-1");
    OriginalSaleRecord same = record("CARD-POI", "member-1");
    OriginalSaleRecord differentCard = record("CARD-OTHER", "member-1");
    OriginalSaleRecord differentMember = record("CARD-POI", "member-2");

    assertEquals(original, same);
    assertEquals(original.hashCode(), same.hashCode());
    assertNotEquals(original, differentCard);
    assertNotEquals(original, differentMember);
  }

  @Test
  void movementOverlapMatchesTransactionIdsAcrossLegTypes() {
    OriginalSaleRecord cardSale =
        OriginalSaleRecord.builder().cardPoiTransactionId("SHARED-POI").build();
    OriginalSaleRecord storedValueLoadSale =
        OriginalSaleRecord.builder()
            .addStoredValueLoad(
                StoredValueLoadRecord.builder()
                    .basketReference("GIFT-CARD-LINE")
                    .amount(new BigDecimal("50.00"))
                    .poiTransactionId("SHARED-POI")
                    .build())
            .build();
    OriginalSaleRecord unrelated =
        OriginalSaleRecord.builder().awardPoiTransactionId("OTHER-POI").build();

    assertTrue(cardSale.sharesMovementWith(storedValueLoadSale));
    assertTrue(storedValueLoadSale.sharesMovementWith(cardSale));
    assertFalse(cardSale.sharesMovementWith(unrelated));
    assertThrows(NullPointerException.class, () -> cardSale.sharesMovementWith(null));
  }

  @Test
  void singleCardBuilderPathRecordsOneCardPaymentWithoutAmount() {
    OriginalSaleRecord record =
        OriginalSaleRecord.builder()
            .cardPoiTransactionId("CARD-POI")
            .cardPoiTransactionTimestamp(ORIGINAL_TIMESTAMP)
            .build();

    assertEquals(List.of(card("CARD-POI", null)), record.getCardPayments());
    assertEquals("CARD-POI", record.getCardPoiTransactionId());
    assertEquals(ORIGINAL_TIMESTAMP, record.getCardPoiTransactionTimestamp());
    assertTrue(record.hasMovement());
  }

  @Test
  void cardPaymentListKeepsOrderAndTheSingleCardAccessorsDescribeTheFirst() {
    OriginalSaleRecord record =
        OriginalSaleRecord.builder()
            .addCardPayment(card("CARD-1", "20.00"))
            .addCardPayment(card("CARD-2", "30.00"))
            .build();

    assertEquals(
        List.of(card("CARD-1", "20.00"), card("CARD-2", "30.00")), record.getCardPayments());
    assertEquals("CARD-1", record.getCardPoiTransactionId());
    assertEquals(ORIGINAL_TIMESTAMP, record.getCardPoiTransactionTimestamp());
    assertThrows(
        UnsupportedOperationException.class,
        () -> record.getCardPayments().add(card("CARD-3", "1.00")));
  }

  @Test
  void singleCardSettersReplaceTheFirstListedCardPaymentsReference() {
    OriginalSaleRecord record =
        OriginalSaleRecord.builder()
            .cardPoiTransactionId("CARD-NEW")
            .cardPayments(List.of(card("CARD-1", "20.00"), card("CARD-2", "30.00")))
            .build();

    assertEquals(
        List.of(
            CardPaymentRecord.builder()
                .poiTransactionId("CARD-NEW")
                .amount(new BigDecimal("20.00"))
                .build(),
            card("CARD-2", "30.00")),
        record.getCardPayments());
  }

  @Test
  void bothBuilderPathsBuildEqualRecordsForOneCardPayment() {
    OriginalSaleRecord legacy =
        OriginalSaleRecord.builder()
            .cardPoiTransactionId("CARD-POI")
            .cardPoiTransactionTimestamp(ORIGINAL_TIMESTAMP)
            .build();
    OriginalSaleRecord listed =
        OriginalSaleRecord.builder().addCardPayment(card("CARD-POI", null)).build();

    assertEquals(legacy, listed);
    assertEquals(legacy.hashCode(), listed.hashCode());
  }

  @Test
  void equalityCoversEveryCardPaymentAndItsOrder() {
    OriginalSaleRecord twoCards =
        OriginalSaleRecord.builder()
            .addCardPayment(card("CARD-1", "20.00"))
            .addCardPayment(card("CARD-2", "30.00"))
            .build();
    OriginalSaleRecord sameScale =
        OriginalSaleRecord.builder()
            .addCardPayment(card("CARD-1", "20.0"))
            .addCardPayment(card("CARD-2", "30"))
            .build();
    OriginalSaleRecord otherSecond =
        OriginalSaleRecord.builder()
            .addCardPayment(card("CARD-1", "20.00"))
            .addCardPayment(card("CARD-3", "30.00"))
            .build();
    OriginalSaleRecord swapped =
        OriginalSaleRecord.builder()
            .addCardPayment(card("CARD-2", "30.00"))
            .addCardPayment(card("CARD-1", "20.00"))
            .build();
    OriginalSaleRecord firstOnly =
        OriginalSaleRecord.builder().addCardPayment(card("CARD-1", "20.00")).build();

    assertEquals(twoCards, sameScale);
    assertEquals(twoCards.hashCode(), sameScale.hashCode());
    assertNotEquals(twoCards, otherSecond);
    assertNotEquals(twoCards, swapped);
    assertNotEquals(twoCards, firstOnly);
  }

  @Test
  void movementOverlapCoversEveryCardPayment() {
    OriginalSaleRecord twoCards =
        OriginalSaleRecord.builder()
            .addCardPayment(card("CARD-1", "20.00"))
            .addCardPayment(card("CARD-2", "30.00"))
            .build();
    OriginalSaleRecord secondCard =
        OriginalSaleRecord.builder().cardPoiTransactionId("CARD-2").build();
    OriginalSaleRecord awardReusingSecond =
        OriginalSaleRecord.builder().awardPoiTransactionId("CARD-2").build();
    OriginalSaleRecord unrelated =
        OriginalSaleRecord.builder().addCardPayment(card("CARD-9", "1.00")).build();

    assertTrue(twoCards.sharesMovementWith(secondCard));
    assertTrue(secondCard.sharesMovementWith(twoCards));
    assertTrue(awardReusingSecond.sharesMovementWith(twoCards));
    assertFalse(twoCards.sharesMovementWith(unrelated));
    assertFalse(OriginalSaleRecord.builder().build().hasMovement());
  }

  @Test
  void fromCopiesEveryCardPaymentWithAReference() {
    SettlementResult result =
        SettlementResult.builder()
            .addCardPayment(payment("CARD-1", "20.00"))
            .addCardPayment(CardPaymentResult.builder().amount(new BigDecimal("5.00")).build())
            .addCardPayment(payment("CARD-2", "30.00"))
            .poiTransactionId("CARD-1")
            .poiTransactionTimestamp(ORIGINAL_TIMESTAMP)
            .storedValuePoiTransactionId("SV-POI")
            .build();

    OriginalSaleRecord record = OriginalSaleRecord.from(result, "member-1");

    assertEquals(
        List.of(card("CARD-1", "20.00"), card("CARD-2", "30.00")), record.getCardPayments());
    assertEquals("SV-POI", record.getStoredValuePoiTransactionId());
    assertEquals("member-1", record.getMemberId());
  }

  @Test
  void fromLeavesAGiftCardOnlySaleWithoutCardPayments() {
    // a gift-card-only sale: the stored value payment is the result's payment reference
    SettlementResult giftCardOnly =
        SettlementResult.builder()
            .poiTransactionId("SV-POI")
            .poiTransactionTimestamp(ORIGINAL_TIMESTAMP)
            .storedValuePoiTransactionId("SV-POI")
            .storedValuePoiTransactionTimestamp(ORIGINAL_TIMESTAMP)
            .build();

    OriginalSaleRecord record = OriginalSaleRecord.from(giftCardOnly, null);

    assertTrue(record.getCardPayments().isEmpty(), "a gift card payment is not a card payment");
    assertEquals("SV-POI", record.getStoredValuePoiTransactionId());
    assertEquals("SV-POI", record.getCardPoiTransactionId(), "the single-card getter is unchanged");
    assertEquals(ORIGINAL_TIMESTAMP, record.getCardPoiTransactionTimestamp());
    assertTrue(record.hasMovement());
  }

  @Test
  void fromKeepsASinglePaymentReferenceThatIsNotTheStoredValuePayment() {
    SettlementResult handBuilt =
        SettlementResult.builder()
            .poiTransactionId("CARD-POI")
            .poiTransactionTimestamp(ORIGINAL_TIMESTAMP)
            .build();

    assertEquals(
        List.of(card("CARD-POI", null)),
        OriginalSaleRecord.from(handBuilt, null).getCardPayments());
    assertTrue(
        OriginalSaleRecord.from(SettlementResult.builder().build(), null)
            .getCardPayments()
            .isEmpty());
  }

  @Test
  void aLegacyGiftCardOnlyRecordIsReadAsItsStoredValuePaymentOnly() {
    // how a gift-card-only record was persisted before card payment lists
    OriginalSaleRecord legacy =
        OriginalSaleRecord.builder()
            .cardPoiTransactionId("SV-POI")
            .cardPoiTransactionTimestamp(ORIGINAL_TIMESTAMP)
            .storedValuePoiTransactionId("SV-POI")
            .storedValuePoiTransactionTimestamp(ORIGINAL_TIMESTAMP)
            .build();
    OriginalSaleRecord listed =
        OriginalSaleRecord.builder()
            .addCardPayment(card("SV-POI", "15.00"))
            .addCardPayment(card("CARD-POI", "5.00"))
            .storedValuePoiTransactionId("SV-POI")
            .build();

    assertTrue(legacy.getCardPayments().isEmpty());
    assertEquals("SV-POI", legacy.getCardPoiTransactionId());
    assertEquals("SV-POI", legacy.getStoredValuePoiTransactionId());
    assertEquals(
        legacy,
        OriginalSaleRecord.from(
            SettlementResult.builder()
                .poiTransactionId("SV-POI")
                .poiTransactionTimestamp(ORIGINAL_TIMESTAMP)
                .storedValuePoiTransactionId("SV-POI")
                .storedValuePoiTransactionTimestamp(ORIGINAL_TIMESTAMP)
                .build(),
            null),
        "a legacy record and one built from the same result are the same record");
    assertEquals(List.of(card("CARD-POI", "5.00")), listed.getCardPayments());
    assertEquals("CARD-POI", listed.getCardPoiTransactionId());
  }

  @Test
  void cardPaymentRecordRequiresAReference() {
    assertThrows(NullPointerException.class, () -> CardPaymentRecord.builder().build());
    assertThrows(
        NullPointerException.class,
        () ->
            CardPaymentRecord.from(
                CardPaymentResult.builder().amount(new BigDecimal("1.00")).build()));
    assertThrows(
        NullPointerException.class, () -> OriginalSaleRecord.builder().addCardPayment(null));
  }

  private static CardPaymentRecord card(String poiTransactionId, String amount) {
    return CardPaymentRecord.builder()
        .poiTransactionId(poiTransactionId)
        .poiTransactionTimestamp(ORIGINAL_TIMESTAMP)
        .amount(amount == null ? null : new BigDecimal(amount))
        .build();
  }

  private static CardPaymentResult payment(String poiTransactionId, String amount) {
    return CardPaymentResult.builder()
        .amount(new BigDecimal(amount))
        .poiTransactionId(poiTransactionId)
        .poiTransactionTimestamp(ORIGINAL_TIMESTAMP)
        .approvalCode("APPR-" + poiTransactionId)
        .build();
  }

  private static OriginalSaleRecord record(String cardPoiTransactionId, String memberId) {
    return OriginalSaleRecord.builder()
        .cardPoiTransactionId(cardPoiTransactionId)
        .cardPoiTransactionTimestamp(ORIGINAL_TIMESTAMP)
        .storedValuePoiTransactionId("SV-POI")
        .storedValuePoiTransactionTimestamp(ORIGINAL_TIMESTAMP.plusSeconds(1))
        .rebatePoiTransactionId("REBATE-POI")
        .rebatePoiTransactionTimestamp(ORIGINAL_TIMESTAMP.plusSeconds(2))
        .redemptionPoiTransactionId("REDEMPTION-POI")
        .redemptionPoiTransactionTimestamp(ORIGINAL_TIMESTAMP.plusSeconds(3))
        .awardPoiTransactionId("AWARD-POI")
        .awardPoiTransactionTimestamp(ORIGINAL_TIMESTAMP.plusSeconds(4))
        .memberId(memberId)
        .build();
  }
}
