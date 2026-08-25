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

/**
 * Options controlling a settlement run.
 *
 * <p>Also serves as the return value of {@code SettlementFlow.onError}: the
 * options decide how the flow recovers — {@link #voidAndAbort()} reverses
 * everything committed so far and fails the settlement, while any other options
 * (e.g. {@link #retryWithoutLoyalty()}) reverse the committed steps and
 * restart the sequence with those options.</p>
 */
public final class SettlementOptions {

    private final boolean disableRebates;
    private final boolean disablePoints;
    private final boolean disableAward;
    private final BigDecimal cashback;
    private final DisplayPayload paymentProcessingDisplay;
    private final List<RefundAllocation> refundAllocations;
    private final boolean voidAndAbort;

    private SettlementOptions(Builder builder) {
        this.disableRebates = builder.disableRebates;
        this.disablePoints = builder.disablePoints;
        this.disableAward = builder.disableAward;
        this.cashback = builder.cashback;
        this.paymentProcessingDisplay = builder.paymentProcessingDisplay;
        this.refundAllocations = Collections.unmodifiableList(
                new ArrayList<>(builder.refundAllocations));
        this.voidAndAbort = builder.voidAndAbort;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Standard options: loyalty enabled, no cashback. */
    public static SettlementOptions defaults() {
        return builder().build();
    }

    /**
     * From {@code onError}: reverse everything committed so far and fail the
     * settlement.
     */
    public static SettlementOptions voidAndAbort() {
        return builder().voidAndAbort(true).build();
    }

    /**
     * From {@code onError}: reverse the committed steps and retry the
     * settlement with rebates and points disabled.
     */
    public static SettlementOptions retryWithoutLoyalty() {
        return builder().disableRebates(true).disablePoints(true).build();
    }

    public boolean isDisableRebates() {
        return disableRebates;
    }

    public boolean isDisablePoints() {
        return disablePoints;
    }

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

    /** Register-specified refund/restoration movements to run during settlement. */
    public List<RefundAllocation> getRefundAllocations() {
        return refundAllocations;
    }

    /** Whether these options mean "unwind and fail" when returned from {@code onError}. */
    public boolean isVoidAndAbort() {
        return voidAndAbort;
    }

    /** Builder for {@link SettlementOptions}. */
    public static final class Builder {

        private boolean disableRebates;
        private boolean disablePoints;
        private boolean disableAward;
        private BigDecimal cashback;
        private DisplayPayload paymentProcessingDisplay;
        private List<RefundAllocation> refundAllocations = new ArrayList<>();
        private boolean voidAndAbort;

        private Builder() {
        }

        public Builder disableRebates(boolean disableRebates) {
            this.disableRebates = disableRebates;
            return this;
        }

        public Builder disablePoints(boolean disablePoints) {
            this.disablePoints = disablePoints;
            return this;
        }

        /**
         * Skips the loyalty award step, so the member earns no points on
         * this checkout. Redemptions are unaffected — combine with
         * {@link #disableRebates(boolean)} and {@link #disablePoints(boolean)}
         * to run a fully loyalty-free charge sequence.
         */
        public Builder disableAward(boolean disableAward) {
            this.disableAward = disableAward;
            return this;
        }

        /**
         * Cashback to hand the customer, authorized on top of the sale
         * amount. Must be positive — a negative value would reduce the
         * amount charged and complete the checkout underpaid. Leave unset
         * for no cashback.
         */
        public Builder cashback(BigDecimal cashback) {
            if (cashback != null && cashback.signum() <= 0) {
                throw new IllegalArgumentException("cashback must be positive");
            }
            this.cashback = cashback;
            return this;
        }

        public Builder paymentProcessingDisplay(DisplayPayload paymentProcessingDisplay) {
            this.paymentProcessingDisplay = paymentProcessingDisplay;
            return this;
        }

        /** Replaces the settlement's refund/restoration allocations. */
        public Builder refundAllocations(List<RefundAllocation> refundAllocations) {
            this.refundAllocations = refundAllocations == null
                    ? new ArrayList<>() : new ArrayList<>(refundAllocations);
            return this;
        }

        /** Adds one register-selected refund/restoration allocation. */
        public Builder addRefundAllocation(RefundAllocation allocation) {
            if (allocation == null) {
                throw new NullPointerException("allocation");
            }
            this.refundAllocations.add(allocation);
            return this;
        }

        public Builder voidAndAbort(boolean voidAndAbort) {
            this.voidAndAbort = voidAndAbort;
            return this;
        }

        public SettlementOptions build() {
            return new SettlementOptions(this);
        }
    }
}
