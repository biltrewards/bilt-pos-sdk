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

import com.bilt.pos.display.DisplayPayload;
import com.bilt.pos.display.AdjustmentsType;
import com.bilt.pos.display.DisplayPayloadHelper;
import com.bilt.pos.display.LineItemKindType;
import com.bilt.pos.display.LineItemType;
import com.bilt.pos.display.LineItemsType;
import com.bilt.pos.display.ReceiptType;
import com.bilt.pos.display.TaxType;
import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketLineItem;
import com.bilt.pos.session.display.DisplayContext;
import com.bilt.pos.session.display.DisplayRenderer;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;

/**
 * Default {@link DisplayRenderer}: renders the basket as the standard
 * itemised virtual receipt ({@code receipt.xslt} layout).
 */
public final class BasketDisplayRenderer implements DisplayRenderer {

    private static final String LAYOUT = "receipt.xslt";
    private static final String VERSION = "1.0";

    @Override
    public DisplayPayload render(Basket basket, DisplayContext context) {
        String currency = displaySymbol(context.getCurrency());

        LineItemsType lineItems = new LineItemsType();
        for (BasketLineItem line : basket.getItems()) {
            LineItemType item = DisplayPayloadHelper.productItem(
                    line.getDescription(),
                    BigDecimal.valueOf(line.getQuantity()),
                    currency,
                    line.getUnitPrice(),
                    line.getAdjustedTotal());
            if (!line.isSale()) {
                item.setKind(LineItemKindType.RETURN);
            }
            lineItems.getLineItem().add(item);
        }

        // line items render at adjustedTotal (post-rebate), so the printed
        // totals must add up on the same basis: subtotal is the discounted
        // line sum, point redemptions show as an order-level adjustment, and
        // the total is what the customer actually pays. During cart-building
        // all breakdown fields are zero and this matches the raw totals.
        BigDecimal subtotal = basket.getSubtotal().subtract(basket.getRebateTotal());
        BigDecimal total = basket.getGrandTotal()
                .subtract(basket.getRebateTotal())
                .subtract(basket.getPointDiscountTotal());

        ReceiptType receipt = new ReceiptType();
        receipt.setLineItems(lineItems);
        receipt.setSubtotal(DisplayPayloadHelper.labeledAmount(
                "Subtotal", currency, subtotal));
        if (basket.getPointDiscountTotal().signum() != 0) {
            AdjustmentsType adjustments = new AdjustmentsType();
            adjustments.getAdjustmentItem().add(DisplayPayloadHelper.labeledAmount(
                    "Points", currency, basket.getPointDiscountTotal().negate()));
            receipt.setAdjustments(adjustments);
        }
        if (basket.getTaxTotal().signum() != 0) {
            TaxType tax = new TaxType();
            tax.setTaxTotal(DisplayPayloadHelper.labeledAmount(
                    "Tax", currency, basket.getTaxTotal()));
            receipt.setTax(tax);
        }
        receipt.setTotal(DisplayPayloadHelper.labeledAmount(
                "Total", currency, total));

        DisplayPayload payload = new DisplayPayload();
        payload.setLayout(LAYOUT);
        payload.setVersion(VERSION);
        payload.setReceipt(receipt);
        return payload;
    }

    /**
     * The session currency is an ISO 4217 code by contract, but the receipt
     * layout prints {@code MoneyType.currency} verbatim next to every amount
     * — customers should see {@code $79.99}, not {@code USD 79.99}. Codes
     * without a known symbol fall back to the code itself.
     */
    private static String displaySymbol(String isoCode) {
        try {
            return Currency.getInstance(isoCode).getSymbol(Locale.US);
        } catch (IllegalArgumentException | NullPointerException e) {
            return isoCode;
        }
    }
}
