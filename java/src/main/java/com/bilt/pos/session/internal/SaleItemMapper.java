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

import java.util.ArrayList;
import java.util.List;

/**
 * Maps {@link Basket} snapshots to the Nexo {@link SaleItem} wire model.
 *
 * <p>Session item IDs are numeric strings ({@code SaleItem.ItemID} is a
 * number on the wire); amounts are converted from {@code BigDecimal} to the
 * wire's floating-point representation at the last possible moment.</p>
 */
public final class SaleItemMapper {

    private SaleItemMapper() {
    }

    /** Maps the basket at original (pre-rebate) amounts. */
    public static List<SaleItem> toSaleItems(Basket basket) {
        return map(basket, false);
    }

    /** Maps the basket at adjusted (post-rebate) amounts. */
    public static List<SaleItem> toAdjustedSaleItems(Basket basket) {
        return map(basket, true);
    }

    private static List<SaleItem> map(Basket basket, boolean adjusted) {
        List<SaleItem> items = new ArrayList<>(basket.getItemCount());
        for (BasketLineItem line : basket.getItems()) {
            items.add(SaleItem.builder()
                    .itemID(Long.parseLong(line.getItemId()))
                    .productCode(line.getSku())
                    .productLabel(line.getDescription())
                    .additionalProductInfo(line.getCategory())
                    .quantity((double) line.getQuantity())
                    .unitPrice(line.getUnitPrice().doubleValue())
                    .itemAmount((adjusted ? line.getAdjustedTotal() : line.getOriginalTotal())
                            .doubleValue())
                    .unitOfMeasure(UnitOfMeasureEnum.OTHER)
                    .build());
        }
        return items;
    }
}
