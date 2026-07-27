package com.bilt.pos.session;

import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.basket.BasketLineItem;
import com.bilt.pos.session.internal.BasketEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BasketEngineTest {

    private static BasketItem candle(int quantity) {
        return BasketItem.of("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", quantity, "24.99");
    }

    private static BasketItem frame() {
        return BasketItem.of("KRK-FRAME-5X7-BLK", "5x7 Black Frame", 1, "14.99");
    }

    @Test
    void emptyBasketHasZeroTotals() {
        Basket basket = new BasketEngine().snapshot();
        assertTrue(basket.isEmpty());
        assertEquals(0, basket.getItemCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(basket.getOriginalTotal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(basket.getGrandTotal()));
        assertNotNull(basket.getCartId());
        assertNotNull(basket.getUpdatedAt());
    }

    @Test
    void addItemComputesLineAndBasketTotals() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(candle(2));
        engine.addItem(frame());

        Basket basket = engine.snapshot();
        assertEquals(2, basket.getItemCount());
        assertEquals(new BigDecimal("49.98"), basket.getItemBySku("KRK-CNDL-LRG-VAN").getOriginalTotal());
        assertEquals(new BigDecimal("64.97"), basket.getOriginalTotal());
        assertEquals(new BigDecimal("64.97"), basket.getGrandTotal());
    }

    @Test
    void addItemUpsertsBySku() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(candle(2));
        engine.addItem(frame());
        engine.addItem(candle(1));

        Basket basket = engine.snapshot();
        BasketLineItem line = basket.getItemBySku("KRK-CNDL-LRG-VAN");
        assertEquals(3, line.getQuantity());
        assertEquals(new BigDecimal("89.96"), basket.getOriginalTotal());
    }

    @Test
    void itemIdsAreSequentialAndStableAcrossUpserts() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(candle(1));
        engine.addItem(frame());
        engine.addItem(candle(1));

        Basket basket = engine.snapshot();
        assertEquals("1", basket.getItemBySku("KRK-CNDL-LRG-VAN").getItemId());
        assertEquals("2", basket.getItemBySku("KRK-FRAME-5X7-BLK").getItemId());
        assertSame(basket.getItem("1"), basket.getItemBySku("KRK-CNDL-LRG-VAN"));
    }

    @Test
    void explicitItemIdIsHonoredAndNotReused() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(candle(1), "7");
        engine.addItem(frame());

        Basket basket = engine.snapshot();
        assertEquals("7", basket.getItemBySku("KRK-CNDL-LRG-VAN").getItemId());
        assertEquals("8", basket.getItemBySku("KRK-FRAME-5X7-BLK").getItemId());
    }

    @Test
    void explicitItemIdMustBeNumericAndUnique() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(candle(1), "1");
        assertThrows(IllegalArgumentException.class, () -> engine.addItem(frame(), "1"));
        assertThrows(IllegalArgumentException.class, () -> engine.addItem(frame(), "abc"));
        assertThrows(IllegalArgumentException.class, () -> engine.addItem(frame(), "0"));
    }

    @Test
    void upsertWithDifferentUnitPriceThrows() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(candle(1));
        assertThrows(IllegalArgumentException.class, () -> engine.addItem(
                BasketItem.of("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 1, "19.99")));
    }

    @Test
    void removeByIdAndBySku() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(candle(1));
        engine.addItem(frame());

        engine.removeItem("1");
        assertNull(engine.snapshot().getItemBySku("KRK-CNDL-LRG-VAN"));
        engine.removeItemBySku("KRK-FRAME-5X7-BLK");
        assertTrue(engine.isEmpty());

        assertThrows(IllegalArgumentException.class, () -> engine.removeItem("1"));
        assertThrows(IllegalArgumentException.class, () -> engine.removeItemBySku("NOPE"));
    }

    @Test
    void updateQuantitySetsAbsoluteValueAndZeroRemoves() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(candle(3));

        engine.updateItemQuantityBySku("KRK-CNDL-LRG-VAN", 1);
        assertEquals(1, engine.snapshot().getItemBySku("KRK-CNDL-LRG-VAN").getQuantity());

        engine.updateItemQuantity("1", 0);
        assertTrue(engine.isEmpty());

        engine.addItem(candle(1));
        assertThrows(IllegalArgumentException.class,
                () -> engine.updateItemQuantityBySku("KRK-CNDL-LRG-VAN", -1));
    }

    // ─── Tax ───

    @Test
    void taxRateComputesFromLineTotal() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(candle(3));
        engine.addItem(frame());
        engine.setTaxRateBySku("KRK-CNDL-LRG-VAN", new BigDecimal("0.08875"));
        engine.setTaxRateBySku("KRK-FRAME-5X7-BLK", new BigDecimal("0.08875"));

        Basket basket = engine.snapshot();
        // 74.97 * 0.08875 = 6.6536 -> 6.65 ; 14.99 * 0.08875 = 1.3304 -> 1.33
        assertEquals(new BigDecimal("6.65"), basket.getItemBySku("KRK-CNDL-LRG-VAN").getTaxAmount());
        assertEquals(new BigDecimal("1.33"), basket.getItemBySku("KRK-FRAME-5X7-BLK").getTaxAmount());
        assertEquals(new BigDecimal("7.98"), basket.getTaxTotal());
        assertEquals(new BigDecimal("97.94"), basket.getGrandTotal());
    }

    @Test
    void explicitTaxAmountOverridesRate() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(candle(1));
        engine.setTaxRateBySku("KRK-CNDL-LRG-VAN", new BigDecimal("0.08875"));
        engine.setTaxAmount("1", new BigDecimal("2.50"));

        Basket basket = engine.snapshot();
        assertEquals(new BigDecimal("2.50"), basket.getItem("1").getTaxAmount());
        assertEquals(new BigDecimal("2.50"), basket.getTaxTotal());
    }

    @Test
    void setTaxRateClearsAPreviouslyFixedAmount() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(candle(1));
        engine.setTaxAmount("1", new BigDecimal("5.00"));
        engine.setTaxRateBySku("KRK-CNDL-LRG-VAN", new BigDecimal("0.08875"));

        // last write wins: the line is rate-based again (24.99 × 0.08875 = 2.22)
        assertEquals(new BigDecimal("2.22"), engine.snapshot().getItem("1").getTaxAmount());
        assertEquals(new BigDecimal("2.22"), engine.snapshot().getTaxTotal());
    }

    @Test
    void basketTaxTotalOverridesItemComputationAndCanBeCleared() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(candle(1));
        engine.setTaxRateBySku("KRK-CNDL-LRG-VAN", new BigDecimal("0.08875"));
        engine.setTaxTotal(new BigDecimal("7.43"));

        Basket basket = engine.snapshot();
        assertEquals(new BigDecimal("7.43"), basket.getTaxTotal());
        assertEquals(new BigDecimal("32.42"), basket.getGrandTotal());

        engine.setTaxTotal(null);
        assertEquals(new BigDecimal("2.22"), engine.snapshot().getTaxTotal());
    }

    @Test
    void untaxedItemContributesZero() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(candle(1));

        Basket basket = engine.snapshot();
        assertEquals(0, BigDecimal.ZERO.compareTo(basket.getItem("1").getTaxAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(basket.getTaxTotal()));
    }

    @Test
    void taxSettersOnMissingItemsThrow() {
        BasketEngine engine = new BasketEngine();
        assertThrows(IllegalArgumentException.class,
                () -> engine.setTaxRate("1", BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> engine.setTaxAmountBySku("NOPE", BigDecimal.ONE));
    }

    @Test
    void taxCarriedOnBasketItemIsApplied() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(BasketItem.builder()
                .sku("SKU-1").description("Item").quantity(2)
                .unitPrice(new BigDecimal("10.00"))
                .taxRate(new BigDecimal("0.10"))
                .build());

        assertEquals(new BigDecimal("2.00"), engine.snapshot().getTaxTotal());
    }

    @Test
    void snapshotsAreIndependent() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(candle(1));
        Basket before = engine.snapshot();

        engine.addItem(frame());

        assertEquals(1, before.getItemCount());
        assertEquals(2, engine.snapshot().getItemCount());
        assertThrows(UnsupportedOperationException.class,
                () -> before.getItems().add(before.getItems().get(0)));
    }

    @Test
    void rebateFieldsAreZeroDuringCartBuilding() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(candle(2));

        Basket basket = engine.snapshot();
        BasketLineItem line = basket.getItem("1");
        assertEquals(0, BigDecimal.ZERO.compareTo(line.getRebateAmount()));
        assertNull(line.getRebateLabel());
        assertEquals(line.getOriginalTotal(), line.getAdjustedTotal());
        assertEquals(0, BigDecimal.ZERO.compareTo(basket.getRebateTotal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(basket.getStoredValueTotal()));
    }
}
