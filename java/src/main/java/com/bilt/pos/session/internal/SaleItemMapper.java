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

import com.bilt.pos.nexo.model.SaleItem;
import com.bilt.pos.nexo.model.UnitOfMeasureEnum;
import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketLineItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps {@link Basket} snapshots to the Nexo {@link SaleItem} wire model.
 *
 * <p>Session item IDs are numeric strings ({@code SaleItem.ItemID} is a
 * number on the wire); amounts are converted from {@code BigDecimal} to the
 * wire's floating-point representation at the last possible moment.</p>
 *
 * <p>Credit lines carry their sign onto the wire in a normal (sale)
 * request: negative {@code Quantity} and {@code ItemAmount}, positive
 * {@code UnitPrice} — the mixed-basket return-line convention. In a refund
 * request every line is a return and {@code PaymentType=Refund} already
 * carries the direction, so {@link #toRefundSaleItems} emits magnitudes
 * only; signed items there would risk double negation on the terminal.</p>
 */
public final class SaleItemMapper {

    private SaleItemMapper() {
    }

    /** Maps the basket at original (pre-rebate) amounts. */
    public static List<SaleItem> toSaleItems(Basket basket) {
        return map(basket, false, false);
    }

    /** Maps the basket at adjusted (post-rebate) amounts. */
    public static List<SaleItem> toAdjustedSaleItems(Basket basket) {
        return map(basket, true, false);
    }

    /**
     * Maps a refund cart for a {@code PaymentRequest(Refund)}: all
     * magnitudes positive, the payment type carrying the direction.
     */
    public static List<SaleItem> toRefundSaleItems(Basket basket) {
        return map(basket, true, true);
    }

    private static List<SaleItem> map(Basket basket, boolean adjusted, boolean magnitudes) {
        List<SaleItem> items = new ArrayList<>(basket.getItemCount());
        for (BasketLineItem line : basket.getItems()) {
            BigDecimal amount = adjusted ? line.getAdjustedTotal() : line.getOriginalTotal();
            double quantity = line.getQuantity();
            if (magnitudes) {
                amount = amount.abs();
            } else if (line.isCredit()) {
                quantity = -quantity;
            }
            items.add(SaleItem.builder()
                    .itemID(Long.parseLong(line.getItemId()))
                    .productCode(line.getSku())
                    .productLabel(line.getDescription())
                    .additionalProductInfo(line.getCategory())
                    .quantity(quantity)
                    .unitPrice(line.getUnitPrice().doubleValue())
                    .itemAmount(amount.doubleValue())
                    .unitOfMeasure(UnitOfMeasureEnum.OTHER)
                    .build());
        }
        return items;
    }
}
