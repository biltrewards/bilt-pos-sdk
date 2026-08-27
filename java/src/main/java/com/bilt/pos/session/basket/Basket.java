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
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An immutable snapshot of the session's basket.
 *
 * <p>Basket mutators on {@code SessionBasket} return a fresh snapshot;
 * instances never change and can be held freely by register code.</p>
 *
 * <p>The payment breakdown fields ({@code rebateTotal},
 * {@code pointDiscountTotal}, {@code storedValueTotal},
 * {@code cardPaymentTotal}) are zero during cart-building and populated on
 * the snapshots produced during and after payment orchestration.</p>
 */
public final class Basket {

    private static final int MONEY_SCALE = 2;

    private final String cartId;
    private final List<BasketLineItem> items;
    private final BigDecimal taxTotal;
    private final BigDecimal originalTotal;
    private final BigDecimal grandTotal;
    private final BigDecimal rebateTotal;
    private final BigDecimal pointDiscountTotal;
    private final BigDecimal storedValueTotal;
    private final BigDecimal cardPaymentTotal;
    private final Instant updatedAt;

    private Basket(Builder builder) {
        this.cartId = builder.cartId;
        this.items = Collections.unmodifiableList(new ArrayList<>(builder.items));
        this.taxTotal = builder.taxTotal;
        this.originalTotal = builder.originalTotal;
        this.grandTotal = builder.grandTotal;
        this.rebateTotal = builder.rebateTotal;
        this.pointDiscountTotal = builder.pointDiscountTotal;
        this.storedValueTotal = builder.storedValueTotal;
        this.cardPaymentTotal = builder.cardPaymentTotal;
        this.updatedAt = builder.updatedAt;
    }

    /** Builder for SDK-internal construction of snapshots. */
    public static Builder builder() {
        return new Builder();
    }

    /** Stable identifier of this cart within the session. */
    public String getCartId() {
        return cartId;
    }

    /** Line items, in insertion order. Never {@code null}. */
    public List<BasketLineItem> getItems() {
        return items;
    }

    /** Total tax across the basket. */
    public BigDecimal getTaxTotal() {
        return taxTotal;
    }

    /** Sum of {@code unitPrice × quantity} across items, before tax. */
    public BigDecimal getOriginalTotal() {
        return originalTotal;
    }

    /** {@code originalTotal + taxTotal}. */
    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    /** Total of committed rebates; populated during payment. */
    public BigDecimal getRebateTotal() {
        return rebateTotal;
    }

    /** Monetary value of redeemed points; populated during payment. */
    public BigDecimal getPointDiscountTotal() {
        return pointDiscountTotal;
    }

    /** Amount charged to a stored value card; populated during payment. */
    public BigDecimal getStoredValueTotal() {
        return storedValueTotal;
    }

    /** Amount charged to the payment card; populated during payment. */
    public BigDecimal getCardPaymentTotal() {
        return cardPaymentTotal;
    }

    /** When this snapshot was produced. */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** The line with the given session-assigned item ID, or {@code null}. */
    public BasketLineItem getItem(String itemId) {
        for (BasketLineItem item : items) {
            if (item.getItemId().equals(itemId)) {
                return item;
            }
        }
        return null;
    }

    /**
     * The line with the given SKU, or {@code null}. When the SKU is present
     * in both directions (a sale line and a credit line), the sale line is
     * returned — credit lines are addressed by itemId.
     */
    public BasketLineItem getItemBySku(String sku) {
        BasketLineItem creditLine = null;
        for (BasketLineItem item : items) {
            if (item.getSku().equals(sku)) {
                if (!item.isCredit()) {
                    return item;
                }
                if (creditLine == null) {
                    creditLine = item;
                }
            }
        }
        return creditLine;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** Number of lines (not total quantity). */
    public int getItemCount() {
        return items.size();
    }

    /** Whether the basket contains at least one sale/non-credit line. */
    public boolean hasSaleLines() {
        return hasLines(false);
    }

    /** Whether the basket contains at least one credit/return line. */
    public boolean hasCreditLines() {
        return hasLines(true);
    }

    /**
     * The sale side of this basket, with credit lines removed. If this
     * basket has no credit lines, the current snapshot is already the sale
     * portion and is returned unchanged.
     */
    public Basket salePortion() {
        if (!hasCreditLines()) {
            return this;
        }
        return filteredPortion(false);
    }

    /**
     * The return side of this basket, with sale lines removed. Totals keep
     * their credit-line sign, so {@link #returnTotal()} exposes the
     * positive refund magnitude.
     */
    public Basket returnPortion() {
        return filteredPortion(true);
    }

    /** Positive amount that must be returned for the credit lines. */
    public BigDecimal returnTotal() {
        return returnPortion().getGrandTotal().abs();
    }

    /**
     * Returns a full-basket snapshot whose sale lines and payment
     * breakdown totals come from {@code settledSalePortion}, while this
     * basket's credit lines remain present. Used after a mixed sale/return
     * settlement, where only the sale portion went through payment
     * orchestration but the final result still needs to describe the whole
     * register basket.
     */
    public Basket withSettledSalePortion(Basket settledSalePortion) {
        Objects.requireNonNull(settledSalePortion, "settledSalePortion");
        if (!hasCreditLines()) {
            return settledSalePortion;
        }
        List<BasketLineItem> mergedItems = new ArrayList<>();
        for (BasketLineItem line : items) {
            BasketLineItem settledLine = settledSalePortion.getItem(line.getItemId());
            mergedItems.add(settledLine != null ? settledLine : line);
        }
        Basket returns = returnPortion();
        BigDecimal mergedOriginalTotal = settledSalePortion.getOriginalTotal()
                .add(returns.getOriginalTotal());
        BigDecimal mergedTaxTotal = settledSalePortion.getTaxTotal()
                .add(returns.getTaxTotal());
        return Basket.builder()
                .cartId(cartId)
                .items(mergedItems)
                .originalTotal(mergedOriginalTotal)
                .taxTotal(mergedTaxTotal)
                .grandTotal(mergedOriginalTotal.add(mergedTaxTotal))
                .rebateTotal(settledSalePortion.getRebateTotal())
                .pointDiscountTotal(settledSalePortion.getPointDiscountTotal())
                .storedValueTotal(settledSalePortion.getStoredValueTotal())
                .cardPaymentTotal(settledSalePortion.getCardPaymentTotal())
                .build();
    }

    private Basket filteredPortion(boolean credit) {
        List<BasketLineItem> filteredItems = new ArrayList<>();
        BigDecimal filteredOriginalTotal = BigDecimal.ZERO;
        BigDecimal filteredLineTaxTotal = BigDecimal.ZERO;
        for (BasketLineItem line : items) {
            if (line.isCredit() == credit) {
                filteredItems.add(line);
                filteredOriginalTotal = filteredOriginalTotal.add(line.getOriginalTotal());
                filteredLineTaxTotal = filteredLineTaxTotal.add(line.getTaxAmount());
            }
        }
        BigDecimal filteredTaxTotal = filteredTaxTotal(credit, filteredLineTaxTotal,
                !filteredItems.isEmpty());
        return Basket.builder()
                .cartId(cartId)
                .items(filteredItems)
                .originalTotal(filteredOriginalTotal)
                .taxTotal(filteredTaxTotal)
                .grandTotal(filteredOriginalTotal.add(filteredTaxTotal))
                .updatedAt(updatedAt)
                .build();
    }

    private BigDecimal filteredTaxTotal(boolean credit, BigDecimal filteredLineTaxTotal,
            boolean hasFilteredItems) {
        if (!hasFilteredItems) {
            return BigDecimal.ZERO;
        }
        if (taxTotal.compareTo(lineTaxTotal()) == 0) {
            return filteredLineTaxTotal;
        }
        if (!(hasSaleLines() && hasCreditLines())) {
            return taxTotal;
        }

        // A basket-level override supersedes item tax, so splitting the
        // basket must preserve that override instead of falling back to
        // per-line taxes. Treat it as net basket tax and split by signed
        // subtotals; the return side therefore receives a negative share.
        if (originalTotal.compareTo(BigDecimal.ZERO) == 0) {
            return credit ? BigDecimal.ZERO : taxTotal;
        }
        BigDecimal saleTaxTotal = taxTotal
                .multiply(originalTotal(false))
                .divide(originalTotal, MONEY_SCALE, RoundingMode.HALF_UP);
        return credit ? taxTotal.subtract(saleTaxTotal) : saleTaxTotal;
    }

    private BigDecimal lineTaxTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (BasketLineItem line : items) {
            total = total.add(line.getTaxAmount());
        }
        return total;
    }

    private BigDecimal originalTotal(boolean credit) {
        BigDecimal total = BigDecimal.ZERO;
        for (BasketLineItem line : items) {
            if (line.isCredit() == credit) {
                total = total.add(line.getOriginalTotal());
            }
        }
        return total;
    }

    private boolean hasLines(boolean credit) {
        for (BasketLineItem line : items) {
            if (line.isCredit() == credit) {
                return true;
            }
        }
        return false;
    }

    /** Builder for {@link Basket}. Intended for SDK use. */
    public static final class Builder {

        private String cartId;
        private List<BasketLineItem> items = new ArrayList<>();
        private BigDecimal taxTotal = BigDecimal.ZERO;
        private BigDecimal originalTotal = BigDecimal.ZERO;
        private BigDecimal grandTotal = BigDecimal.ZERO;
        private BigDecimal rebateTotal = BigDecimal.ZERO;
        private BigDecimal pointDiscountTotal = BigDecimal.ZERO;
        private BigDecimal storedValueTotal = BigDecimal.ZERO;
        private BigDecimal cardPaymentTotal = BigDecimal.ZERO;
        private Instant updatedAt = Instant.now();

        private Builder() {
        }

        public Builder cartId(String cartId) {
            this.cartId = cartId;
            return this;
        }

        public Builder items(List<BasketLineItem> items) {
            this.items = items;
            return this;
        }

        public Builder taxTotal(BigDecimal taxTotal) {
            this.taxTotal = taxTotal;
            return this;
        }

        public Builder originalTotal(BigDecimal originalTotal) {
            this.originalTotal = originalTotal;
            return this;
        }

        public Builder grandTotal(BigDecimal grandTotal) {
            this.grandTotal = grandTotal;
            return this;
        }

        public Builder rebateTotal(BigDecimal rebateTotal) {
            this.rebateTotal = rebateTotal;
            return this;
        }

        public Builder pointDiscountTotal(BigDecimal pointDiscountTotal) {
            this.pointDiscountTotal = pointDiscountTotal;
            return this;
        }

        public Builder storedValueTotal(BigDecimal storedValueTotal) {
            this.storedValueTotal = storedValueTotal;
            return this;
        }

        public Builder cardPaymentTotal(BigDecimal cardPaymentTotal) {
            this.cardPaymentTotal = cardPaymentTotal;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Basket build() {
            return new Basket(this);
        }
    }
}
