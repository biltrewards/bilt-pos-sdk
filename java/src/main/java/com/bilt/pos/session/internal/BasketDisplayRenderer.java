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
import com.bilt.pos.display.DisplayPayloadHelper;
import com.bilt.pos.display.LineItemsType;
import com.bilt.pos.display.ReceiptType;
import com.bilt.pos.display.TaxType;
import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketLineItem;
import com.bilt.pos.session.display.DisplayContext;
import com.bilt.pos.session.display.DisplayRenderer;

import java.math.BigDecimal;

/**
 * Default {@link DisplayRenderer}: renders the basket as the standard
 * itemised virtual receipt ({@code receipt.xslt} layout).
 */
public final class BasketDisplayRenderer implements DisplayRenderer {

    private static final String LAYOUT = "receipt.xslt";
    private static final String VERSION = "1.0";

    @Override
    public DisplayPayload render(Basket basket, DisplayContext context) {
        String currency = context.getCurrency();

        LineItemsType lineItems = new LineItemsType();
        for (BasketLineItem line : basket.getItems()) {
            lineItems.getLineItem().add(DisplayPayloadHelper.productItem(
                    line.getDescription(),
                    BigDecimal.valueOf(line.getQuantity()),
                    currency,
                    line.getUnitPrice(),
                    line.getAdjustedTotal()));
        }

        ReceiptType receipt = new ReceiptType();
        receipt.setLineItems(lineItems);
        receipt.setSubtotal(DisplayPayloadHelper.labeledAmount(
                "Subtotal", currency, basket.getOriginalTotal()));
        if (basket.getTaxTotal().signum() != 0) {
            TaxType tax = new TaxType();
            tax.setTaxTotal(DisplayPayloadHelper.labeledAmount(
                    "Tax", currency, basket.getTaxTotal()));
            receipt.setTax(tax);
        }
        receipt.setTotal(DisplayPayloadHelper.labeledAmount(
                "Total", currency, basket.getGrandTotal()));

        DisplayPayload payload = new DisplayPayload();
        payload.setLayout(LAYOUT);
        payload.setVersion(VERSION);
        payload.setReceipt(receipt);
        return payload;
    }
}
