/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.settlement;

import com.bilt.pos.display.DisplayPayload;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The register's complete plan for resolving a basket during settlement.
 *
 * <p>The basket declares commercial obligations. These options supply the
 * execution choices that cannot be inferred from it: how the aggregate return
 * value is allocated and which card fulfills each referenced stored value load
 * line. Retry decisions are deliberately separate in {@link SettlementRecovery}
 * so recovery never replaces or drops these instructions.</p>
 */
public final class SettlementOptions {

    private final boolean disableRebates;
    private final boolean disablePoints;
    private final boolean disableAward;
    private final BigDecimal cashback;
    private final DisplayPayload paymentProcessingDisplay;
    private final List<RefundAllocation> refunds;
    private final List<StoredValueLoad> fulfillments;
    private final SettlementType settlementType;

    private SettlementOptions(Builder builder) {
        this.disableRebates = builder.disableRebates;
        this.disablePoints = builder.disablePoints;
        this.disableAward = builder.disableAward;
        this.cashback = builder.cashback;
        this.paymentProcessingDisplay = builder.paymentProcessingDisplay;
        this.refunds = Collections.unmodifiableList(new ArrayList<>(builder.refunds));
        this.fulfillments = Collections.unmodifiableList(new ArrayList<>(builder.fulfillments));
        this.settlementType = builder.settlementType;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Standard options: loyalty enabled, no cashback, refunds, or fulfillments. */
    public static SettlementOptions defaults() {
        return builder().build();
    }

    /** Whether terminal offer/rebate redemption is disabled. */
    public boolean isDisableRebates() {
        return disableRebates;
    }

    /** Whether point/reward redemption is disabled. */
    public boolean isDisablePoints() {
        return disablePoints;
    }

    /** Whether the post-payment loyalty award is disabled. */
    public boolean isDisableAward() {
        return disableAward;
    }

    /** Cashback to request with the card payment, or {@code null}. */
    public BigDecimal getCashback() {
        return cashback;
    }

    /** Display shown while the card payment is processing, or {@code null}. */
    public DisplayPayload getPaymentProcessingDisplay() {
        return paymentProcessingDisplay;
    }

    /** Allocations resolving the basket's aggregate return target. */
    public List<RefundAllocation> getRefunds() {
        return refunds;
    }

    /** Stored value operations fulfilling referenced basket lines. */
    public List<StoredValueLoad> getFulfillments() {
        return fulfillments;
    }

    /** How a mixed sale/return basket moves money. */
    public SettlementType getSettlementType() {
        return settlementType;
    }

    /** Builder for {@link SettlementOptions}. */
    public static final class Builder {

        private boolean disableRebates;
        private boolean disablePoints;
        private boolean disableAward;
        private BigDecimal cashback;
        private DisplayPayload paymentProcessingDisplay;
        private List<RefundAllocation> refunds = new ArrayList<>();
        private List<StoredValueLoad> fulfillments = new ArrayList<>();
        private SettlementType settlementType = SettlementType.REFUND_THEN_CHARGE;

        private Builder() {
        }

        /** Disables terminal offer/rebate redemption for this settlement. */
        public Builder disableRebates(boolean disableRebates) {
            this.disableRebates = disableRebates;
            return this;
        }

        /** Disables point/reward redemption for this settlement. */
        public Builder disablePoints(boolean disablePoints) {
            this.disablePoints = disablePoints;
            return this;
        }

        /** Disables the post-payment loyalty award for this settlement. */
        public Builder disableAward(boolean disableAward) {
            this.disableAward = disableAward;
            return this;
        }

        /** Requests positive cashback in addition to the card charge. */
        public Builder cashback(BigDecimal cashback) {
            if (cashback != null && cashback.signum() <= 0) {
                throw new IllegalArgumentException("cashback must be positive");
            }
            this.cashback = cashback;
            return this;
        }

        /** Overrides the display shown while the card payment is processing. */
        public Builder paymentProcessingDisplay(DisplayPayload paymentProcessingDisplay) {
            this.paymentProcessingDisplay = paymentProcessingDisplay;
            return this;
        }

        /** Replaces the allocations resolving the basket's return target. */
        public Builder refunds(List<RefundAllocation> refunds) {
            this.refunds = refunds == null ? new ArrayList<>() : new ArrayList<>(refunds);
            return this;
        }

        /** Adds one register-selected refund allocation. */
        public Builder addRefund(RefundAllocation refund) {
            this.refunds.add(Objects.requireNonNull(refund, "refund"));
            return this;
        }

        /** Replaces the stored value line fulfillments. */
        public Builder fulfillments(List<StoredValueLoad> fulfillments) {
            this.fulfillments = fulfillments == null
                    ? new ArrayList<>() : new ArrayList<>(fulfillments);
            return this;
        }

        /** Adds one activation or reload instruction for a referenced basket line. */
        public Builder addFulfillment(StoredValueLoad fulfillment) {
            this.fulfillments.add(Objects.requireNonNull(fulfillment, "fulfillment"));
            return this;
        }

        /** Default {@link SettlementType#REFUND_THEN_CHARGE}. */
        public Builder settlementType(SettlementType settlementType) {
            this.settlementType = Objects.requireNonNull(settlementType, "settlementType");
            return this;
        }

        public SettlementOptions build() {
            return new SettlementOptions(this);
        }
    }
}
