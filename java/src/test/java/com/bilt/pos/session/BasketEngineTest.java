package com.bilt.pos.session;

import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.basket.BasketLineItem;
import com.bilt.pos.session.internal.BasketEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

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
    void rateOnlyUpsertClearsAPreviouslyFixedAmount() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(BasketItem.builder()
                .sku("KRK-CNDL-LRG-VAN").description("Large Vanilla Candle")
                .quantity(1).unitPrice(new BigDecimal("24.99"))
                .taxAmount(new BigDecimal("5.00"))
                .build());
        engine.addItem(BasketItem.builder()
                .sku("KRK-CNDL-LRG-VAN").description("Large Vanilla Candle")
                .quantity(1).unitPrice(new BigDecimal("24.99"))
                .taxRate(new BigDecimal("0.08875"))
                .build());

        // qty 2 → 49.98 × 0.08875 = 4.44; the stale fixed amount is gone
        assertEquals(new BigDecimal("4.44"), engine.snapshot().getItem("1").getTaxAmount());
    }

    @Test
    void upsertWithBothRateAndAmountKeepsAmountPrecedence() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(candle(1));
        engine.addItem(BasketItem.builder()
                .sku("KRK-CNDL-LRG-VAN").description("Large Vanilla Candle")
                .quantity(1).unitPrice(new BigDecimal("24.99"))
                .taxRate(new BigDecimal("0.08875"))
                .taxAmount(new BigDecimal("3.00"))
                .build());

        assertEquals(new BigDecimal("3.00"), engine.snapshot().getItem("1").getTaxAmount());
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

    // ─── Credit lines ───

    @Test
    void creditLineNegatesTotalsAndTax() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(BasketItem.builder()
                .sku("KRK-CNDL-LRG-VAN").description("Large Vanilla Candle")
                .quantity(2).unitPrice(new BigDecimal("24.99"))
                .taxRate(new BigDecimal("0.08875"))
                .credit(true)
                .build());

        Basket basket = engine.snapshot();
        BasketLineItem line = basket.getItem("1");
        assertTrue(line.isCredit());
        assertEquals(2, line.getQuantity(), "quantity stays a positive count");
        assertEquals(new BigDecimal("24.99"), line.getUnitPrice(),
                "unit price stays the catalog price");
        assertEquals(new BigDecimal("-49.98"), line.getOriginalTotal());
        assertEquals(new BigDecimal("-4.44"), line.getTaxAmount(),
                "tax follows the line's direction");
        assertEquals(new BigDecimal("-54.42"), basket.getGrandTotal());
    }

    @Test
    void creditLineWithFixedTaxAmountNegatesTheMagnitude() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(BasketItem.credit("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 1, "24.99"));
        engine.setTaxAmountBySku("KRK-CNDL-LRG-VAN", new BigDecimal("2.00"));

        Basket basket = engine.snapshot();
        assertEquals(new BigDecimal("-2.00"), basket.getItem("1").getTaxAmount(),
                "tax amounts are set as magnitudes; the direction supplies the sign");
        assertEquals(new BigDecimal("-26.99"), basket.getGrandTotal());
    }

    @Test
    void saleAndCreditLinesOfTheSameSkuStaySeparate() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(candle(2));
        engine.addItem(BasketItem.credit("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 1, "24.99"));

        Basket basket = engine.snapshot();
        assertEquals(2, basket.getItemCount(),
                "opposite directions never upsert into each other");
        assertEquals(new BigDecimal("24.99"), basket.getGrandTotal());

        // the upsert keys on (SKU, direction): another credit of the same
        // SKU lands on the credit line
        engine.addItem(BasketItem.credit("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 1, "24.99"));
        basket = engine.snapshot();
        assertEquals(2, basket.getItemCount());
        assertEquals(new BigDecimal("0.00"), basket.getGrandTotal());
    }

    @Test
    void basketPortionsSplitSaleAndCreditLinesAndLineTaxes() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(BasketItem.builder()
                .sku("SKU-SALE").description("Sale Item")
                .quantity(2).unitPrice(new BigDecimal("10.00"))
                .taxAmount(new BigDecimal("1.50"))
                .build());
        engine.addItem(BasketItem.builder()
                .sku("SKU-RETURN").description("Return Item")
                .quantity(1).unitPrice(new BigDecimal("4.00"))
                .taxAmount(new BigDecimal("0.32"))
                .credit(true)
                .build());

        Basket basket = engine.snapshot();
        Basket sale = basket.salePortion();
        Basket returns = basket.returnPortion();

        assertTrue(basket.hasSaleLines());
        assertTrue(basket.hasCreditLines());
        assertEquals(1, sale.getItemCount());
        assertFalse(sale.hasCreditLines());
        assertEquals(new BigDecimal("20.00"), sale.getOriginalTotal());
        assertEquals(new BigDecimal("1.50"), sale.getTaxTotal());
        assertEquals(new BigDecimal("21.50"), sale.getGrandTotal());
        assertEquals(1, returns.getItemCount());
        assertFalse(returns.hasSaleLines());
        assertEquals(new BigDecimal("-4.00"), returns.getOriginalTotal());
        assertEquals(new BigDecimal("-0.32"), returns.getTaxTotal());
        assertEquals(new BigDecimal("-4.32"), returns.getGrandTotal());
        assertEquals(new BigDecimal("4.32"), basket.returnTotal());
    }

    @Test
    void basketPortionsPreserveTaxTotalOverride() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(BasketItem.of("SKU-SALE", "Sale Item", 1, "100.00"));
        engine.addItem(BasketItem.credit("SKU-RETURN", "Return Item", 1, "25.00"));
        engine.setTaxTotal(new BigDecimal("6.00"));

        Basket basket = engine.snapshot();
        Basket sale = basket.salePortion();
        Basket returns = basket.returnPortion();

        assertEquals(new BigDecimal("0.00"), basket.getItemBySku("SKU-SALE").getTaxAmount());
        assertEquals(new BigDecimal("8.00"), sale.getTaxTotal());
        assertEquals(new BigDecimal("108.00"), sale.getGrandTotal());
        assertEquals(new BigDecimal("-2.00"), returns.getTaxTotal());
        assertEquals(new BigDecimal("-27.00"), returns.getGrandTotal());
        assertEquals(new BigDecimal("27.00"), basket.returnTotal());
    }

    @Test
    void salePortionReturnsSameSnapshotWhenBasketHasOnlySaleLines() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(candle(1));

        Basket basket = engine.snapshot();

        assertSame(basket, basket.salePortion());
        assertFalse(basket.hasCreditLines());
        assertTrue(basket.returnPortion().isEmpty());
    }

    @Test
    void settledSalePortionIsMergedBackIntoFullBasket() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(BasketItem.builder()
                .sku("SKU-SALE").description("Sale Item")
                .quantity(2).unitPrice(new BigDecimal("10.00"))
                .taxAmount(new BigDecimal("1.50"))
                .build());
        engine.addItem(BasketItem.builder()
                .sku("SKU-RETURN").description("Return Item")
                .quantity(1).unitPrice(new BigDecimal("4.00"))
                .taxAmount(new BigDecimal("0.32"))
                .credit(true)
                .build());

        Basket full = engine.snapshot();
        BasketLineItem saleLine = full.getItemBySku("SKU-SALE");
        BasketLineItem settledSaleLine = BasketLineItem.builder()
                .itemId(saleLine.getItemId())
                .sku(saleLine.getSku())
                .description(saleLine.getDescription())
                .category(saleLine.getCategory())
                .quantity(saleLine.getQuantity())
                .unitPrice(saleLine.getUnitPrice())
                .originalTotal(saleLine.getOriginalTotal())
                .rebateAmount(new BigDecimal("5.00"))
                .rebateLabel("Offer")
                .adjustedTotal(new BigDecimal("15.00"))
                .taxRate(saleLine.getTaxRate())
                .taxAmount(saleLine.getTaxAmount())
                .metadata(saleLine.getMetadata())
                .build();
        Basket settledSale = Basket.builder()
                .cartId(full.getCartId())
                .items(List.of(settledSaleLine))
                .originalTotal(new BigDecimal("20.00"))
                .taxTotal(new BigDecimal("1.00"))
                .grandTotal(new BigDecimal("21.00"))
                .rebateTotal(new BigDecimal("5.00"))
                .pointDiscountTotal(new BigDecimal("3.00"))
                .storedValueTotal(new BigDecimal("8.00"))
                .cardPaymentTotal(new BigDecimal("5.00"))
                .build();

        Basket merged = full.withSettledSalePortion(settledSale);

        assertEquals(2, merged.getItemCount());
        assertSame(settledSaleLine, merged.getItem("1"));
        assertSame(full.getItem("2"), merged.getItem("2"));
        assertEquals(new BigDecimal("16.00"), merged.getOriginalTotal());
        assertEquals(new BigDecimal("0.68"), merged.getTaxTotal());
        assertEquals(new BigDecimal("16.68"), merged.getGrandTotal());
        assertEquals(new BigDecimal("5.00"), merged.getRebateTotal());
        assertEquals(new BigDecimal("3.00"), merged.getPointDiscountTotal());
        assertEquals(new BigDecimal("8.00"), merged.getStoredValueTotal());
        assertEquals(new BigDecimal("5.00"), merged.getCardPaymentTotal());
    }

    @Test
    void bySkuAddressingPrefersTheSaleLine() {
        BasketEngine engine = new BasketEngine();
        engine.addItem(candle(2));                                             // itemId 1
        engine.addItem(BasketItem.credit(
                "KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 1, "24.99"));      // itemId 2

        engine.updateItemQuantityBySku("KRK-CNDL-LRG-VAN", 5);

        Basket basket = engine.snapshot();
        assertEquals(5, basket.getItem("1").getQuantity(),
                "BySku targets the sale line when both directions exist");
        assertEquals(1, basket.getItem("2").getQuantity());
        assertEquals("1", basket.getItemBySku("KRK-CNDL-LRG-VAN").getItemId());

        // with only the credit line left, the SKU alone is unambiguous
        engine.removeItem("1");
        engine.updateItemQuantityBySku("KRK-CNDL-LRG-VAN", 3);
        assertEquals(3, engine.snapshot().getItem("2").getQuantity());
    }

    @Test
    void creditCartNegatesTheTaxTotalOverride() {
        BasketEngine cart = new BasketEngine(true);
        cart.addItem(candle(1));
        cart.setTaxTotal(new BigDecimal("2.00"));

        Basket basket = cart.snapshot();
        assertEquals(new BigDecimal("-2.00"), basket.getTaxTotal(),
                "the override is a magnitude; the cart's direction supplies the sign");
        assertEquals(new BigDecimal("-26.99"), basket.getGrandTotal(),
                "tax returned with the merchandise, not netted against it");
    }

    @Test
    void creditCartForcesEveryLineToTheCreditSide() {
        BasketEngine cart = new BasketEngine(true);
        cart.addItem(candle(1));   // a plain sale item — the cart flips it

        Basket basket = cart.snapshot();
        assertTrue(basket.getItem("1").isCredit());
        assertEquals(new BigDecimal("-24.99"), basket.getGrandTotal());

        cart.clear();
        assertTrue(cart.snapshot().isEmpty());
    }
}
