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
import java.util.Objects;

/**
 * An item added to the basket by the register.
 *
 * <p>The SKU is the primary identifier: adding an item whose SKU is already
 * in the basket increments that line's quantity (upsert). A <em>credit</em>
 * item subtracts from the basket — a return or trade-in rung into a sale —
 * and is kept as its own line: the upsert matches on SKU <em>and</em>
 * direction, so selling and taking back the same SKU produces two lines.
 * Quantity and unit price are always positive; the direction carries the
 * sign.</p>
 *
 * <pre>{@code
 * session.basket().addItem(BasketItem.of("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 2, "24.99"));
 *
 * // a return in the same sale: displays and totals as -24.99
 * session.basket().addItem(BasketItem.credit("KRK-FRAME-5X7-BLK", "5x7 Black Frame", 1, "24.99"));
 *
 * session.basket().addItem(BasketItem.builder()
 *     .sku("KRK-FRAME-5X7-BLK")
 *     .description("5x7 Black Frame")
 *     .quantity(1)
 *     .unitPrice(new BigDecimal("14.99"))
 *     .taxRate(new BigDecimal("0.08875"))
 *     .build());
 * }</pre>
 */
public final class BasketItem {

    private final String sku;
    private final String description;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final boolean credit;
    private final String category;
    private final BigDecimal taxRate;
    private final BigDecimal taxAmount;
    private final Map<String, String> metadata;

    private BasketItem(Builder builder) {
        this.sku = builder.sku;
        this.description = builder.description;
        this.quantity = builder.quantity;
        this.unitPrice = builder.unitPrice;
        this.credit = builder.credit;
        this.category = builder.category;
        this.taxRate = builder.taxRate;
        this.taxAmount = builder.taxAmount;
        this.metadata = builder.metadata == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
    }

    /** Shorthand factory for the required fields. */
    public static BasketItem of(String sku, String description, int quantity, String unitPrice) {
        return builder()
                .sku(sku)
                .description(description)
                .quantity(quantity)
                .unitPrice(new BigDecimal(unitPrice))
                .build();
    }

    /** Shorthand factory for a credit (negative) line — a return or trade-in. */
    public static BasketItem credit(String sku, String description, int quantity,
                                    String unitPrice) {
        return builder()
                .sku(sku)
                .description(description)
                .quantity(quantity)
                .unitPrice(new BigDecimal(unitPrice))
                .credit(true)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Primary identifier of the product. */
    public String getSku() {
        return sku;
    }

    public String getDescription() {
        return description;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /** Whether this line subtracts from the basket (return, trade-in). */
    public boolean isCredit() {
        return credit;
    }

    /** Optional product category; aids terminal-side offer matching. */
    public String getCategory() {
        return category;
    }

    /** Optional tax rate, e.g. {@code 0.08875}. */
    public BigDecimal getTaxRate() {
        return taxRate;
    }

    /** Optional fixed tax amount; overrides {@link #getTaxRate()}. */
    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    /** Pass-through metadata. Never {@code null}. */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /** Builder for {@link BasketItem}. */
    public static final class Builder {

        private String sku;
        private String description;
        private int quantity = 1;
        private BigDecimal unitPrice;
        private boolean credit;
        private String category;
        private BigDecimal taxRate;
        private BigDecimal taxAmount;
        private Map<String, String> metadata;

        private Builder() {
        }

        public Builder sku(String sku) {
            this.sku = sku;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /** Default 1. */
        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder unitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
            return this;
        }

        /**
         * Marks the line as a credit: its totals (and tax) subtract from
         * the basket. Quantity and unit price stay positive — the
         * direction carries the sign. Default {@code false}.
         */
        public Builder credit(boolean credit) {
            this.credit = credit;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
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

        public BasketItem build() {
            Objects.requireNonNull(sku, "sku is required");
            Objects.requireNonNull(description, "description is required");
            Objects.requireNonNull(unitPrice, "unitPrice is required");
            if (sku.isEmpty()) {
                throw new IllegalArgumentException("sku must not be empty");
            }
            if (quantity < 1) {
                throw new IllegalArgumentException("quantity must be at least 1");
            }
            if (unitPrice.signum() < 0) {
                throw new IllegalArgumentException("unitPrice must not be negative");
            }
            return new BasketItem(this);
        }
    }
}
