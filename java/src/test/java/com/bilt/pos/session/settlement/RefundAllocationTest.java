package com.bilt.pos.session.settlement;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundAllocationTest {

    private static final Instant ORIGINAL_TIMESTAMP = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void linkedCardRefundRequiresOriginalTransactionId() {
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> RefundAllocation.card(new BigDecimal("10.00"), null,
                        ORIGINAL_TIMESTAMP));

        assertTrue(error.getMessage().contains("originalPoiTransactionId"));
    }

    @Test
    void linkedCardRefundFromOriginalSaleRequiresCardLeg() {
        OriginalSaleRecord originalSale = OriginalSaleRecord.builder()
                .storedValuePoiTransactionId("POI-SV-ORIG")
                .storedValuePoiTransactionTimestamp(ORIGINAL_TIMESTAMP)
                .build();

        NullPointerException error = assertThrows(NullPointerException.class,
                () -> RefundAllocation.card(new BigDecimal("10.00"), originalSale));

        assertTrue(error.getMessage().contains("card leg"));
        assertTrue(error.getMessage().contains("cardUnlinked"));
    }

    @Test
    void unlinkedCardRefundDoesNotRequireOriginalTransactionId() {
        assertDoesNotThrow(() -> RefundAllocation.cardUnlinked(new BigDecimal("10.00")));
    }
}
