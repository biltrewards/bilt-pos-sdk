/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.payment;

import java.math.BigDecimal;

/** A single rebate the terminal committed during the rebate step. */
public final class RedeemedRebate {

    private final String itemId;
    private final String sku;
    private final BigDecimal amount;
    private final String label;
    private final String promotionRef;

    public RedeemedRebate(String itemId, String sku, BigDecimal amount,
                          String label, String promotionRef) {
        this.itemId = itemId;
        this.sku = sku;
        this.amount = amount;
        this.label = label;
        this.promotionRef = promotionRef;
    }

    /** The basket line the rebate applies to, or {@code null} for cart-level. */
    public String getItemId() {
        return itemId;
    }

    /** SKU of the rebated line, or {@code null} for cart-level. */
    public String getSku() {
        return sku;
    }

    /** Dollar amount subtracted from the line (or cart). */
    public BigDecimal getAmount() {
        return amount;
    }

    /** Display label, e.g. {@code "Gold Member: 20% off candles"}. */
    public String getLabel() {
        return label;
    }

    /** Promotion reference, when reported by the terminal. */
    public String getPromotionRef() {
        return promotionRef;
    }
}
