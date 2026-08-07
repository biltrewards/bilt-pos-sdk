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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
