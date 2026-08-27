package com.bilt.pos.session.settlement;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
