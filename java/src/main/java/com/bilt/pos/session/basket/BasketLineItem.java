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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An immutable line of a {@link Basket} snapshot.
 *
 * <p>{@code rebateAmount} and {@code rebateLabel} are zero/{@code null} while
 * the cart is being built; they are populated on the baskets delivered during
 * payment orchestration, after the terminal commits offers.</p>
 */
public final class BasketLineItem {

    private final String itemId;
    private final String sku;
    private final String description;
    private final String category;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal originalTotal;
    private final BigDecimal rebateAmount;
    private final String rebateLabel;
    private final BigDecimal adjustedTotal;
    private final BigDecimal taxRate;
    private final BigDecimal taxAmount;
    private final Map<String, String> metadata;

    private BasketLineItem(Builder builder) {
        this.itemId = builder.itemId;
        this.sku = builder.sku;
        this.description = builder.description;
        this.category = builder.category;
        this.quantity = builder.quantity;
        this.unitPrice = builder.unitPrice;
        this.originalTotal = builder.originalTotal;
        this.rebateAmount = builder.rebateAmount;
        this.rebateLabel = builder.rebateLabel;
        this.adjustedTotal = builder.adjustedTotal;
        this.taxRate = builder.taxRate;
        this.taxAmount = builder.taxAmount;
        this.metadata = builder.metadata == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
    }

    /** Builder for SDK-internal construction of snapshots. */
    public static Builder builder() {
        return new Builder();
    }

    /** Session-assigned identifier: {@code "1"}, {@code "2"}, ... */
    public String getItemId() {
        return itemId;
    }

    public String getSku() {
        return sku;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /** {@code unitPrice × quantity}. */
    public BigDecimal getOriginalTotal() {
        return originalTotal;
    }

    /** Rebate applied to this line; zero during cart-building. */
    public BigDecimal getRebateAmount() {
        return rebateAmount;
    }

    /** Label of the applied rebate, or {@code null} during cart-building. */
    public String getRebateLabel() {
        return rebateLabel;
    }

    /** {@code originalTotal − rebateAmount}. */
    public BigDecimal getAdjustedTotal() {
        return adjustedTotal;
    }

    /** Tax rate for this line, or {@code null} if not set. */
    public BigDecimal getTaxRate() {
        return taxRate;
    }

    /** Tax amount, from the rate or an explicit override; zero if untaxed. */
    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    /** Pass-through metadata. Never {@code null}. */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /** Builder for {@link BasketLineItem}. Intended for SDK use. */
    public static final class Builder {

        private String itemId;
        private String sku;
        private String description;
        private String category;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal originalTotal;
        private BigDecimal rebateAmount = BigDecimal.ZERO;
        private String rebateLabel;
        private BigDecimal adjustedTotal;
        private BigDecimal taxRate;
        private BigDecimal taxAmount = BigDecimal.ZERO;
        private Map<String, String> metadata;

        private Builder() {
        }

        public Builder itemId(String itemId) {
            this.itemId = itemId;
            return this;
        }

        public Builder sku(String sku) {
            this.sku = sku;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder unitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
            return this;
        }

        public Builder originalTotal(BigDecimal originalTotal) {
            this.originalTotal = originalTotal;
            return this;
        }

        public Builder rebateAmount(BigDecimal rebateAmount) {
            this.rebateAmount = rebateAmount;
            return this;
        }

        public Builder rebateLabel(String rebateLabel) {
            this.rebateLabel = rebateLabel;
            return this;
        }

        public Builder adjustedTotal(BigDecimal adjustedTotal) {
            this.adjustedTotal = adjustedTotal;
            return this;
        }

        public Builder taxRate(BigDecimal taxRate) {
            this.taxRate = taxRate;
            return this;
        }

        public Builder taxAmount(BigDecimal taxAmount) {
            this.taxAmount = taxAmount;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public BasketLineItem build() {
            return new BasketLineItem(this);
        }
    }
}
