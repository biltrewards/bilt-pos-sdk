/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.basket;

import java.math.BigDecimal;
import java.util.List;

/**
 * Receiver for batched basket mutations via
 * {@code session.basket().mutate(...)}: all changes are applied atomically and
 * followed by a single display update.
 *
 * <pre>{@code
 * session.basket().mutate(basket -> basket
 *     .addItem(BasketItem.sale("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 2, "24.99"))
 *     .removeItemBySku("KRK-FRAME-5X7-BLK")
 *     .setTaxTotal(new BigDecimal("7.43")));
 * }</pre>
 */
public interface BasketMutation {

    /** Adds an item; increments quantity when the SKU already exists. */
    BasketMutation addItem(BasketItem item);

    /** Adds an item with an explicit item ID (numeric string, new SKUs only). */
    BasketMutation addItem(BasketItem item, String itemId);

    /** Removes the line with the given item ID. */
    BasketMutation removeItem(String itemId);

    /** Removes the line with the given SKU. */
    BasketMutation removeItemBySku(String sku);

    /** Sets an absolute quantity; {@code 0} removes the line. */
    BasketMutation updateItemQuantity(String itemId, int quantity);

    /** Sets an absolute quantity by SKU; {@code 0} removes the line. */
    BasketMutation updateItemQuantityBySku(String sku, int quantity);

    /** Replaces the register-applied discounts on a line; an empty list clears them. */
    BasketMutation setDiscounts(String itemId, List<BasketDiscount> discounts);

    /** Replaces the register-applied discounts on a line by SKU. */
    BasketMutation setDiscountsBySku(String sku, List<BasketDiscount> discounts);

    /** Sets the tax rate on a line ({@code taxAmount = subtotal × rate});
     * clears any explicit fixed tax amount previously set on it. */
    BasketMutation setTaxRate(String itemId, BigDecimal rate);

    /** Sets the tax rate on a line by SKU. */
    BasketMutation setTaxRateBySku(String sku, BigDecimal rate);

    /** Sets a fixed tax amount on a line, overriding any rate. */
    BasketMutation setTaxAmount(String itemId, BigDecimal amount);

    /** Sets a fixed tax amount on a line by SKU. */
    BasketMutation setTaxAmountBySku(String sku, BigDecimal amount);

    /** Overrides the basket's total tax; {@code null} restores item-level computation. */
    BasketMutation setTaxTotal(BigDecimal amount);
}
