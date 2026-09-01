package com.bilt.pos.session.settlement;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        OriginalSaleRecord cardSale = OriginalSaleRecord.builder()
                .cardPoiTransactionId("SHARED-POI")
                .build();
        OriginalSaleRecord storedValueLoadSale = OriginalSaleRecord.builder()
                .addStoredValueLoad(StoredValueLoadRecord.builder()
                        .basketReference("GIFT-CARD-LINE")
                        .amount(new BigDecimal("50.00"))
                        .poiTransactionId("SHARED-POI")
                        .build())
                .build();
        OriginalSaleRecord unrelated = OriginalSaleRecord.builder()
                .awardPoiTransactionId("OTHER-POI")
                .build();

        assertTrue(cardSale.sharesMovementWith(storedValueLoadSale));
        assertTrue(storedValueLoadSale.sharesMovementWith(cardSale));
        assertFalse(cardSale.sharesMovementWith(unrelated));
        assertThrows(NullPointerException.class,
                () -> cardSale.sharesMovementWith(null));
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
