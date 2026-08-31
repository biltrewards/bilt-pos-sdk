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

import com.bilt.pos.session.settlement.SettlementType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
    private final BigDecimal discountTotal;
    private final BigDecimal subtotal;
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
        this.discountTotal = builder.discountTotal;
        this.subtotal = builder.subtotal == null
                ? builder.originalTotal.subtract(builder.discountTotal) : builder.subtotal;
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

    /** Signed gross item value before register discounts and tax. */
    public BigDecimal getOriginalTotal() {
        return originalTotal;
    }

    /** Signed total of register-applied line discounts. */
    public BigDecimal getDiscountTotal() {
        return discountTotal;
    }

    /** Signed item value after register discounts and before tax. */
    public BigDecimal getSubtotal() {
        return subtotal;
    }

    /** {@code subtotal + taxTotal}. */
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

    /** The line with the given register reference, or {@code null}. */
    public BasketLineItem getItemByReference(String reference) {
        Objects.requireNonNull(reference, "reference");
        for (BasketLineItem item : items) {
            if (reference.equals(item.getReference())) {
                return item;
            }
        }
        return null;
    }

    /**
     * The line with the given SKU, or {@code null}. When the SKU is present
     * in multiple directions, the sale line is returned. Return and credit
     * lines can always be addressed by itemId.
     */
    public BasketLineItem getItemBySku(String sku) {
        BasketLineItem returnLine = null;
        for (BasketLineItem item : items) {
            if (item.getSku().equals(sku)) {
                if (item.isSale()) {
                    return item;
                }
                if (returnLine == null) {
                    returnLine = item;
                }
            }
        }
        return returnLine;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** Number of lines (not total quantity). */
    public int getItemCount() {
        return items.size();
    }

    /** Whether the basket contains at least one sale line. */
    public boolean hasSaleLines() {
        return hasLines(BasketItemDirection.SALE);
    }

    /** Whether the basket contains at least one return line. */
    public boolean hasReturnLines() {
        return hasLines(BasketItemDirection.RETURN);
    }

    /** Whether the basket contains at least one register-originated credit line. */
    public boolean hasCreditLines() {
        return hasLines(BasketItemDirection.CREDIT);
    }

    /** Stored value load obligations, in basket order. */
    public List<BasketLineItem> getStoredValueLoadItems() {
        List<BasketLineItem> loads = new ArrayList<>();
        for (BasketLineItem item : items) {
            if (item.getPurpose() == BasketItemPurpose.STORED_VALUE_LOAD) {
                loads.add(item);
            }
        }
        return Collections.unmodifiableList(loads);
    }

    /**
     * Sale lines in this basket, with returns and register-originated credits
     * removed.
     */
    public Basket salePortion() {
        if (!hasReturnLines() && !hasCreditLines()) {
            return this;
        }
        return filteredPortion(EnumSet.of(BasketItemDirection.SALE));
    }

    /**
     * The amount collected from the customer under separate settlement:
     * sale lines reduced by register-originated credits, with returns removed.
     */
    public Basket chargePortion() {
        if (!hasReturnLines()) {
            return this;
        }
        return filteredPortion(EnumSet.of(
                BasketItemDirection.SALE, BasketItemDirection.CREDIT));
    }

    /**
     * Merchandise return lines in this basket. Register-originated credits are
     * available separately through {@link #creditPortion()}.
     */
    public Basket returnPortion() {
        return filteredPortion(EnumSet.of(BasketItemDirection.RETURN));
    }

    /** Register-originated credits in this basket. */
    public Basket creditPortion() {
        return filteredPortion(EnumSet.of(BasketItemDirection.CREDIT));
    }

    /** Positive amount represented by all return lines. */
    public BigDecimal returnTotal() {
        return returnPortion().getGrandTotal().abs();
    }

    /**
     * Positive monetary refund allocation required by the selected settlement
     * mode. {@link SettlementType#REFUND_THEN_CHARGE} returns the full value
     * of the return lines. {@link SettlementType#NET} returns only a negative
     * basket's refund difference, or zero when the charge side covers it.
     */
    public BigDecimal getRefundAmount(SettlementType settlementType) {
        Objects.requireNonNull(settlementType, "settlementType");
        if (settlementType == SettlementType.NET) {
            return grandTotal.signum() < 0 ? grandTotal.abs() : BigDecimal.ZERO;
        }
        return returnTotal();
    }

    /**
     * Returns a full-basket snapshot whose sale and credit lines and payment
     * breakdown totals come from {@code settledChargePortion}, while this
     * basket's return lines remain present. Used after a separate settlement,
     * where only the charge portion went through payment orchestration but the
     * final result still needs to describe the whole register basket.
     */
    public Basket withSettledChargePortion(Basket settledChargePortion) {
        Objects.requireNonNull(settledChargePortion, "settledChargePortion");
        if (!hasReturnLines()) {
            return settledChargePortion;
        }
        List<BasketLineItem> mergedItems = new ArrayList<>();
        for (BasketLineItem line : items) {
            BasketLineItem settledLine = settledChargePortion.getItem(line.getItemId());
            mergedItems.add(settledLine != null ? settledLine : line);
        }
        Basket returns = returnPortion();
        BigDecimal mergedOriginalTotal = settledChargePortion.getOriginalTotal()
                .add(returns.getOriginalTotal());
        BigDecimal mergedDiscountTotal = settledChargePortion.getDiscountTotal()
                .add(returns.getDiscountTotal());
        BigDecimal mergedSubtotal = settledChargePortion.getSubtotal()
                .add(returns.getSubtotal());
        BigDecimal mergedTaxTotal = settledChargePortion.getTaxTotal()
                .add(returns.getTaxTotal());
        return Basket.builder()
                .cartId(cartId)
                .items(mergedItems)
                .originalTotal(mergedOriginalTotal)
                .discountTotal(mergedDiscountTotal)
                .subtotal(mergedSubtotal)
                .taxTotal(mergedTaxTotal)
                .grandTotal(mergedSubtotal.add(mergedTaxTotal))
                .rebateTotal(settledChargePortion.getRebateTotal())
                .pointDiscountTotal(settledChargePortion.getPointDiscountTotal())
                .storedValueTotal(settledChargePortion.getStoredValueTotal())
                .cardPaymentTotal(settledChargePortion.getCardPaymentTotal())
                .build();
    }

    private Basket filteredPortion(Set<BasketItemDirection> directions) {
        List<BasketLineItem> filteredItems = new ArrayList<>();
        BigDecimal filteredOriginalTotal = BigDecimal.ZERO;
        BigDecimal filteredDiscountTotal = BigDecimal.ZERO;
        BigDecimal filteredSubtotal = BigDecimal.ZERO;
        BigDecimal filteredLineTaxTotal = BigDecimal.ZERO;
        for (BasketLineItem line : items) {
            if (directions.contains(line.getDirection())) {
                filteredItems.add(line);
                filteredOriginalTotal = filteredOriginalTotal.add(line.getOriginalTotal());
                filteredDiscountTotal = filteredDiscountTotal.add(line.getDiscountTotal());
                filteredSubtotal = filteredSubtotal.add(line.getSubtotal());
                filteredLineTaxTotal = filteredLineTaxTotal.add(line.getTaxAmount());
            }
        }
        BigDecimal filteredTaxTotal = filteredTaxTotal(directions, filteredLineTaxTotal,
                !filteredItems.isEmpty());
        return Basket.builder()
                .cartId(cartId)
                .items(filteredItems)
                .originalTotal(filteredOriginalTotal)
                .discountTotal(filteredDiscountTotal)
                .subtotal(filteredSubtotal)
                .taxTotal(filteredTaxTotal)
                .grandTotal(filteredSubtotal.add(filteredTaxTotal))
                .updatedAt(updatedAt)
                .build();
    }

    private BigDecimal filteredTaxTotal(Set<BasketItemDirection> directions,
            BigDecimal filteredLineTaxTotal,
            boolean hasFilteredItems) {
        if (!hasFilteredItems) {
            return BigDecimal.ZERO;
        }
        if (taxTotal.compareTo(lineTaxTotal()) == 0) {
            return filteredLineTaxTotal;
        }
        if (directionsCoverBasket(directions)) {
            return taxTotal;
        }

        // A basket-level override supersedes item tax, so splitting the
        // basket must preserve that override instead of falling back to
        // per-line taxes. Treat it as net basket tax and split by signed
        // subtotals; the refund side therefore receives a negative share.
        if (subtotal.compareTo(BigDecimal.ZERO) == 0) {
            return directions.contains(BasketItemDirection.SALE) ? taxTotal : BigDecimal.ZERO;
        }
        BigDecimal filteredSubtotal = BigDecimal.ZERO;
        for (BasketItemDirection direction : directions) {
            filteredSubtotal = filteredSubtotal.add(subtotal(direction));
        }
        return taxTotal.multiply(filteredSubtotal)
                .divide(subtotal, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private boolean directionsCoverBasket(Set<BasketItemDirection> directions) {
        for (BasketLineItem line : items) {
            if (!directions.contains(line.getDirection())) {
                return false;
            }
        }
        return true;
    }

    private BigDecimal lineTaxTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (BasketLineItem line : items) {
            total = total.add(line.getTaxAmount());
        }
        return total;
    }

    private BigDecimal subtotal(BasketItemDirection direction) {
        BigDecimal total = BigDecimal.ZERO;
        for (BasketLineItem line : items) {
            if (line.getDirection() == direction) {
                total = total.add(line.getSubtotal());
            }
        }
        return total;
    }

    private boolean hasLines(BasketItemDirection direction) {
        for (BasketLineItem line : items) {
            if (line.getDirection() == direction) {
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
        private BigDecimal discountTotal = BigDecimal.ZERO;
        private BigDecimal subtotal;
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

        public Builder discountTotal(BigDecimal discountTotal) {
            this.discountTotal = discountTotal;
            return this;
        }

        public Builder subtotal(BigDecimal subtotal) {
            this.subtotal = subtotal;
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
