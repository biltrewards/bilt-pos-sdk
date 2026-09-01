/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   Internal API — subject to change without notice.
 */
package com.bilt.pos.session.internal;

import com.bilt.pos.session.basket.BasketDiscount;

import java.math.BigDecimal;
import java.util.List;

/** Shared arithmetic and validation for register-applied line discounts. */
public final class BasketDiscountRules {

    private BasketDiscountRules() {
    }

    public static BigDecimal discountTotal(List<BasketDiscount> discounts) {
        BigDecimal total = BigDecimal.ZERO;
        for (BasketDiscount discount : discounts) {
            total = total.add(discount.getAmount());
        }
        return total;
    }

    public static void requireDiscountsWithinLineValue(BigDecimal discountTotal,
            BigDecimal unitPrice, int quantity, String sku) {
        BigDecimal lineValue = unitPrice.multiply(BigDecimal.valueOf(quantity));
        if (discountTotal.compareTo(lineValue) > 0) {
            throw new IllegalArgumentException("discounts total " + discountTotal
                    + " exceeds line value " + lineValue + " for SKU " + sku);
        }
    }
}
