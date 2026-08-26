package com.bilt.pos.session.settlement;

import com.bilt.pos.session.basket.Basket;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettlementContextTest {

    private static final Basket BASKET = Basket.builder()
            .cartId("cart-1")
            .grandTotal(new BigDecimal("10.00"))
            .build();

    @Test
    void resolveSaleTransactionIdUsesGeneratedDefaultWithoutHandler() {
        String id = SettlementContext.resolveSaleTransactionId(
                SettlementStep.CARD_CHARGE, BASKET, new BigDecimal("10.00"),
                List.of(), null);

        assertNotNull(id);
        assertFalse(id.isEmpty());
    }

    @Test
    void resolveSaleTransactionIdFallsBackWhenHandlerReturnsNothing() {
        AtomicReference<String> defaultId = new AtomicReference<>();

        String nullResult = SettlementContext.resolveSaleTransactionId(
                SettlementStep.CARD_CHARGE, BASKET, new BigDecimal("10.00"),
                List.of(), ctx -> {
                    defaultId.set(ctx.getDefaultTransactionId());
                    return null;
                });
        assertEquals(defaultId.get(), nullResult);

        String emptyResult = SettlementContext.resolveSaleTransactionId(
                SettlementStep.CARD_CHARGE, BASKET, new BigDecimal("10.00"),
                List.of(), ctx -> "");
        assertFalse(emptyResult.isEmpty());
        assertNotEquals(nullResult, emptyResult);
    }

    @Test
    void resolveSaleTransactionIdUsesHandlerOverrideAndContext() {
        CommittedStep priorStep = new CommittedStep(SettlementStep.CARD_REFUND,
                "sale-txn-1", "poi-txn-1",
                Instant.parse("2026-07-20T10:00:00Z"), true);

        String id = SettlementContext.resolveSaleTransactionId(
                SettlementStep.STORED_VALUE_CHARGE, BASKET, new BigDecimal("10.00"),
                List.of(priorStep), ctx -> {
                    assertEquals(SettlementStep.STORED_VALUE_CHARGE, ctx.getStep());
                    assertEquals(BASKET, ctx.getCurrentBasket());
                    assertEquals(new BigDecimal("10.00"), ctx.getCurrentTotal());
                    assertEquals(List.of(priorStep), ctx.getPriorSteps());
                    assertNotNull(ctx.getDefaultTransactionId());
                    return "register-txn-1";
                });

        assertEquals("register-txn-1", id);
    }

    @Test
    void contextCopiesAndFreezesPriorSteps() {
        CommittedStep priorStep = new CommittedStep(SettlementStep.CARD_REFUND,
                "sale-txn-1", "poi-txn-1",
                Instant.parse("2026-07-20T10:00:00Z"), true);
        List<CommittedStep> priorSteps = new ArrayList<>(List.of(priorStep));

        SettlementContext context = new SettlementContext(SettlementStep.CARD_CHARGE,
                BASKET, new BigDecimal("10.00"), "default-txn", priorSteps);
        priorSteps.clear();

        assertEquals(List.of(priorStep), context.getPriorSteps());
        assertThrows(UnsupportedOperationException.class,
                () -> context.getPriorSteps().add(priorStep));
    }
}
