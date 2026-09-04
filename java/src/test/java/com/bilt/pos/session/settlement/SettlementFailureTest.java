package com.bilt.pos.session.settlement;

import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.SessionErrorCode;
import com.bilt.pos.session.basket.Basket;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettlementFailureTest {

    @Test
    void builderKeepsCategoryAndServiceIdDistinct() {
        SettlementFailure failure = validFailure()
                .messageCategory("Payment")
                .serviceId("SERVICE-1")
                .build();

        assertEquals("Payment", failure.getMessageCategory());
        assertEquals("SERVICE-1", failure.getServiceId());
    }

    @Test
    void builderRejectsMissingFailureContext() {
        assertThrows(NullPointerException.class,
                () -> validFailure().amountDue(null).build());
        assertThrows(NullPointerException.class,
                () -> validFailure().committedMovements(null).build());
        assertThrows(NullPointerException.class,
                () -> validFailure().outcomeCertainty(null).build());
    }

    @Test
    void abandonedRecordRejectsMissingMoneyContext() {
        assertThrows(NullPointerException.class,
                () -> validAbandonedRecord().outstandingAmount(null).build());
        assertThrows(NullPointerException.class,
                () -> validAbandonedRecord().committedMovements(null).build());
    }

    private static SettlementFailure.Builder validFailure() {
        return SettlementFailure.builder()
                .step(SettlementStep.CARD_CHARGE)
                .error(new SessionError(SessionErrorCode.DECLINED, "declined"))
                .amountDue(new BigDecimal("25.00"))
                .committedMovements(List.of())
                .outcomeCertainty(SettlementFailure.OutcomeCertainty.DEFINITIVE);
    }

    private static AbandonedSettlementRecord.Builder validAbandonedRecord() {
        return AbandonedSettlementRecord.builder()
                .settlementId("settlement-1")
                .abandonedAt(Instant.EPOCH)
                .basket(Basket.builder()
                        .cartId("cart-1")
                        .grandTotal(new BigDecimal("25.00"))
                        .build())
                .options(SettlementOptions.builder().build())
                .failure(validFailure().build())
                .outstandingAmount(new BigDecimal("25.00"))
                .committedMovements(List.of());
    }
}
