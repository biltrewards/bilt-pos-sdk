/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.payment;

import com.bilt.pos.display.DisplayPayload;

import java.math.BigDecimal;

/**
 * Options controlling a payment run.
 *
 * <p>Also serves as the return value of {@code PaymentFlow.onError}: the
 * options decide how the flow recovers — {@link #voidAndAbort()} reverses
 * everything committed so far and fails the payment, while any other options
 * (e.g. {@link #retryWithoutLoyalty()}) reverse the committed steps and
 * restart the sequence with those options.</p>
 */
public final class PaymentOptions {

    private final boolean disableRebates;
    private final boolean disablePoints;
    private final BigDecimal cashback;
    private final DisplayPayload paymentProcessingDisplay;
    private final boolean voidAndAbort;

    private PaymentOptions(Builder builder) {
        this.disableRebates = builder.disableRebates;
        this.disablePoints = builder.disablePoints;
        this.cashback = builder.cashback;
        this.paymentProcessingDisplay = builder.paymentProcessingDisplay;
        this.voidAndAbort = builder.voidAndAbort;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Standard options: loyalty enabled, no cashback. */
    public static PaymentOptions defaults() {
        return builder().build();
    }

    /**
     * From {@code onError}: reverse everything committed so far and fail the
     * payment.
     */
    public static PaymentOptions voidAndAbort() {
        return builder().voidAndAbort(true).build();
    }

    /**
     * From {@code onError}: reverse the committed steps and retry the
     * payment with rebates and points disabled.
     */
    public static PaymentOptions retryWithoutLoyalty() {
        return builder().disableRebates(true).disablePoints(true).build();
    }

    public boolean isDisableRebates() {
        return disableRebates;
    }

    public boolean isDisablePoints() {
        return disablePoints;
    }

    /** Cashback to request with the card payment, or {@code null}. */
    public BigDecimal getCashback() {
        return cashback;
    }

    /** Display shown while the card payment is processing, or {@code null}. */
    public DisplayPayload getPaymentProcessingDisplay() {
        return paymentProcessingDisplay;
    }

    /** Whether these options mean "unwind and fail" when returned from {@code onError}. */
    public boolean isVoidAndAbort() {
        return voidAndAbort;
    }

    /** Builder for {@link PaymentOptions}. */
    public static final class Builder {

        private boolean disableRebates;
        private boolean disablePoints;
        private BigDecimal cashback;
        private DisplayPayload paymentProcessingDisplay;
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

        public Builder cashback(BigDecimal cashback) {
            this.cashback = cashback;
            return this;
        }

        public Builder paymentProcessingDisplay(DisplayPayload paymentProcessingDisplay) {
            this.paymentProcessingDisplay = paymentProcessingDisplay;
            return this;
        }

        public Builder voidAndAbort(boolean voidAndAbort) {
            this.voidAndAbort = voidAndAbort;
            return this;
        }

        public PaymentOptions build() {
            return new PaymentOptions(this);
        }
    }
}
