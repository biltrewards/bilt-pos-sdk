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
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An item added to the basket by the register.
 *
 * <p>Factories state the commercial intent directly: {@link #sale},
 * {@link #returnItem}, {@link #credit}, or {@link #storedValueLoad}. Ordinary merchandise is
 * upserted by SKU and direction. Stored value load lines are distinct
 * obligations keyed by their register reference, so buying two cards with
 * the same SKU produces two lines instead of quantity two.</p>
 *
 * <p>A stored value load line contains only receipt-safe commercial data.
 * The card and activation/reload instruction belong in the settlement
 * options and are joined to this line by {@link #getReference()}.</p>
 */
public final class BasketItem {

    private final String reference;
    private final String sku;
    private final String description;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final List<BasketDiscount> discounts;
    private final BasketItemDirection direction;
    private final BasketItemPurpose purpose;
    private final String category;
    private final BigDecimal taxRate;
    private final BigDecimal taxAmount;
    private final Map<String, String> metadata;

    private BasketItem(Builder builder) {
        this.reference = builder.reference;
        this.sku = builder.sku;
        this.description = builder.description;
        this.quantity = builder.quantity;
        this.unitPrice = builder.unitPrice;
        this.discounts = Collections.unmodifiableList(new ArrayList<>(builder.discounts));
        this.direction = builder.direction;
        this.purpose = builder.purpose;
        this.category = builder.category;
        this.taxRate = builder.taxRate;
        this.taxAmount = builder.taxAmount;
        this.metadata = builder.metadata == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
    }

    /** Shorthand factory for ordinary merchandise sold to the customer. */
    public static BasketItem sale(String sku, String description, int quantity,
                                  BigDecimal unitPrice) {
        return builder()
                .sku(sku)
                .description(description)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .build();
    }

    /** Shorthand factory for a merchandise return or trade-in. */
    public static BasketItem returnItem(String sku, String description, int quantity,
                                        BigDecimal unitPrice) {
        return builder()
                .sku(sku)
                .description(description)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .direction(BasketItemDirection.RETURN)
                .build();
    }

    /** Shorthand factory for an offer or other register-originated credit. */
    public static BasketItem credit(String sku, String description, int quantity,
                                    BigDecimal unitPrice) {
        return builder()
                .sku(sku)
                .description(description)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .direction(BasketItemDirection.CREDIT)
                .build();
    }

    /**
     * Value sold for activation or loading onto one stored value card.
     * Quantity is one and tax is zero; sell activation fees as separate
     * merchandise lines.
     *
     * @param reference register-stable reference used by the matching
     *                  settlement fulfillment
     */
    public static BasketItem storedValueLoad(String reference, String sku,
                                             String description, BigDecimal amount) {
        return builder()
                .reference(reference)
                .sku(sku)
                .description(description)
                .unitPrice(amount)
                .purpose(BasketItemPurpose.STORED_VALUE_LOAD)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Register-stable reference for settlement-time fulfillment, or {@code null}. */
    public String getReference() {
        return reference;
    }

    /** Primary catalog identifier of the product. */
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

    /** Register-applied discounts, in application order. */
    public List<BasketDiscount> getDiscounts() {
        return discounts;
    }

    /** Returns a copy carrying one additional register-applied discount. */
    public BasketItem withDiscount(BasketDiscount discount) {
        Builder copy = builder()
                .reference(reference)
                .sku(sku)
                .description(description)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .direction(direction)
                .purpose(purpose)
                .category(category)
                .taxRate(taxRate)
                .taxAmount(taxAmount)
                .metadata(metadata)
                .discounts(discounts);
        return copy.addDiscount(discount).build();
    }

    public BasketItemDirection getDirection() {
        return direction;
    }

    public BasketItemPurpose getPurpose() {
        return purpose;
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

        private String reference;
        private String sku;
        private String description;
        private int quantity = 1;
        private BigDecimal unitPrice;
        private List<BasketDiscount> discounts = new ArrayList<>();
        private BasketItemDirection direction = BasketItemDirection.SALE;
        private BasketItemPurpose purpose = BasketItemPurpose.MERCHANDISE;
        private String category;
        private BigDecimal taxRate;
        private BigDecimal taxAmount;
        private Map<String, String> metadata;

        private Builder() {
        }

        public Builder reference(String reference) {
            this.reference = reference;
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

        /** Default 1. */
        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder unitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
            return this;
        }

        public Builder discounts(List<BasketDiscount> discounts) {
            this.discounts = discounts == null
                    ? new ArrayList<>() : new ArrayList<>(discounts);
            return this;
        }

        public Builder addDiscount(BasketDiscount discount) {
            this.discounts.add(Objects.requireNonNull(discount, "discount"));
            return this;
        }

        /** Default {@link BasketItemDirection#SALE}. */
        public Builder direction(BasketItemDirection direction) {
            this.direction = direction;
            return this;
        }

        /** Default {@link BasketItemPurpose#MERCHANDISE}. */
        public Builder purpose(BasketItemPurpose purpose) {
            this.purpose = purpose;
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
            Objects.requireNonNull(direction, "direction is required");
            Objects.requireNonNull(purpose, "purpose is required");
            if (sku.isEmpty()) {
                throw new IllegalArgumentException("sku must not be empty");
            }
            if (reference != null && reference.isEmpty()) {
                throw new IllegalArgumentException("reference must not be empty");
            }
            if (quantity < 1) {
                throw new IllegalArgumentException("quantity must be at least 1");
            }
            if (unitPrice.signum() < 0) {
                throw new IllegalArgumentException("unitPrice must not be negative");
            }
            BigDecimal gross = unitPrice.multiply(BigDecimal.valueOf(quantity));
            BigDecimal discountTotal = discounts.stream()
                    .map(BasketDiscount::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (discountTotal.compareTo(gross) > 0) {
                throw new IllegalArgumentException("discounts total " + discountTotal
                        + " but line value is only " + gross);
            }
            validateStoredValueLoad();
            return new BasketItem(this);
        }

        private void validateStoredValueLoad() {
            if (purpose != BasketItemPurpose.STORED_VALUE_LOAD) {
                return;
            }
            if (direction != BasketItemDirection.SALE) {
                throw new IllegalArgumentException("stored value load lines must be sales");
            }
            if (reference == null) {
                throw new NullPointerException("stored value load reference is required");
            }
            if (quantity != 1) {
                throw new IllegalArgumentException("stored value load quantity must be 1");
            }
            if (unitPrice.signum() <= 0) {
                throw new IllegalArgumentException("stored value load amount must be positive");
            }
            if ((taxRate != null && taxRate.signum() != 0)
                    || (taxAmount != null && taxAmount.signum() != 0)) {
                throw new IllegalArgumentException("stored value load lines cannot be taxed; "
                        + "add fees as separate merchandise lines");
            }
        }
    }
}
