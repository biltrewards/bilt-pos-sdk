package com.bilt.pos.session;

import com.bilt.pos.display.DisplayPayload;
import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketLineItem;
import com.bilt.pos.session.display.DisplayContext;
import com.bilt.pos.session.display.DisplayTarget;
import com.bilt.pos.session.internal.BasketDisplayRenderer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BasketDisplayRendererTest {

    private static final DisplayContext CONTEXT =
            new DisplayContext(SessionState.PAYING, DisplayTarget.TERMINAL, "USD");

    private static BasketLineItem line(String id, BigDecimal original, BigDecimal rebate) {
        return BasketLineItem.builder()
                .itemId(id).sku("SKU-" + id).description("Item " + id)
                .quantity(1).unitPrice(original)
                .originalTotal(original)
                .rebateAmount(rebate)
                .adjustedTotal(original.subtract(rebate))
                .taxAmount(BigDecimal.ZERO)
                .build();
    }

    @Test
    void cartBuildingReceiptUsesRawTotals() {
        Basket basket = Basket.builder()
                .items(List.of(line("1", new BigDecimal("50.00"), BigDecimal.ZERO)))
                .originalTotal(new BigDecimal("50.00"))
                .taxTotal(new BigDecimal("4.00"))
                .grandTotal(new BigDecimal("54.00"))
                .build();

        DisplayPayload payload = new BasketDisplayRenderer().render(basket, CONTEXT);

        assertEquals(0, new BigDecimal("50.00").compareTo(
                payload.getReceipt().getSubtotal().getAmount().getValue()));
        assertEquals(0, new BigDecimal("54.00").compareTo(
                payload.getReceipt().getTotal().getAmount().getValue()));
        assertNull(payload.getReceipt().getAdjustments());
    }

    @Test
    void discountedReceiptTotalsMatchTheDiscountedLines() {
        // 100 gross, 10 rebate (in the lines), 4 tax, 5 in points
        Basket basket = Basket.builder()
                .items(List.of(line("1", new BigDecimal("100.00"), new BigDecimal("10.00"))))
                .originalTotal(new BigDecimal("100.00"))
                .taxTotal(new BigDecimal("4.00"))
                .grandTotal(new BigDecimal("104.00"))
                .rebateTotal(new BigDecimal("10.00"))
                .pointDiscountTotal(new BigDecimal("5.00"))
                .build();

        DisplayPayload payload = new BasketDisplayRenderer().render(basket, CONTEXT);

        // line renders at 90.00; subtotal must match the line sum
        assertEquals(0, new BigDecimal("90.00").compareTo(
                payload.getReceipt().getLineItems().getLineItem().get(0)
                        .getAmount().getValue()));
        assertEquals(0, new BigDecimal("90.00").compareTo(
                payload.getReceipt().getSubtotal().getAmount().getValue()));
        // points shown as a negative order-level adjustment
        assertEquals(0, new BigDecimal("-5.00").compareTo(
                payload.getReceipt().getAdjustments().getAdjustmentItem().get(0)
                        .getAmount().getValue()));
        // total = 90 + 4 tax - 5 points
        assertEquals(0, new BigDecimal("89.00").compareTo(
                payload.getReceipt().getTotal().getAmount().getValue()));
    }

    @Test
    void receiptAmountsUseTheCurrencySymbolNotTheIsoCode() {
        Basket basket = Basket.builder()
                .items(List.of(line("1", new BigDecimal("50.00"), BigDecimal.ZERO)))
                .originalTotal(new BigDecimal("50.00"))
                .taxTotal(new BigDecimal("4.00"))
                .grandTotal(new BigDecimal("54.00"))
                .build();

        DisplayPayload payload = new BasketDisplayRenderer().render(basket, CONTEXT);

        assertEquals("$", payload.getReceipt().getTotal().getAmount().getCurrency());
        assertEquals("$", payload.getReceipt().getSubtotal().getAmount().getCurrency());
        assertEquals("$", payload.getReceipt().getLineItems().getLineItem().get(0)
                .getAmount().getCurrency());
        assertEquals("$", payload.getReceipt().getTax().getTaxTotal()
                .getAmount().getCurrency());
    }

    @Test
    void unknownCurrencyCodeFallsBackToTheCodeItself() {
        Basket basket = Basket.builder()
                .items(List.of(line("1", new BigDecimal("10.00"), BigDecimal.ZERO)))
                .originalTotal(new BigDecimal("10.00"))
                .grandTotal(new BigDecimal("10.00"))
                .build();
        DisplayContext context =
                new DisplayContext(SessionState.PAYING, DisplayTarget.TERMINAL, "ZZZ");

        DisplayPayload payload = new BasketDisplayRenderer().render(basket, context);

        assertEquals("ZZZ", payload.getReceipt().getTotal().getAmount().getCurrency());
    }
}
