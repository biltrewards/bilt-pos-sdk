/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Outcome of {@code voidTransaction()}: the payment reversal plus a
 * best-effort loyalty award reversal.
 *
 * <p>The result describes the movements <em>this call</em> reversed. A
 * resumed void — a retry after a partial void aborted — does not restate
 * the movements earlier attempts reversed: its amount, transaction
 * reference, and receipts cover only the legs sent this time (and may all
 * be absent when the retry found nothing left to send). A register
 * printing a void receipt should print it from the attempt that reversed
 * the money leg, or reprint via the transaction status query.</p>
 */
public final class VoidResult {

    private final boolean success;
    private final BigDecimal reversedAmount;
    private final String poiTransactionId;
    private final Instant poiTransactionTimestamp;
    private final Receipt customerReceipt;
    private final Receipt merchantReceipt;
    private final int pointsReversed;
    private final int remainingPointBalance;

    private VoidResult(Builder builder) {
        this.success = builder.success;
        this.reversedAmount = builder.reversedAmount;
        this.poiTransactionId = builder.poiTransactionId;
        this.poiTransactionTimestamp = builder.poiTransactionTimestamp;
        this.customerReceipt = builder.customerReceipt;
        this.merchantReceipt = builder.merchantReceipt;
        this.pointsReversed = builder.pointsReversed;
        this.remainingPointBalance = builder.remainingPointBalance;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isSuccess() {
        return success;
    }

    /** Amount reversed; {@code null} when the terminal did not echo it. */
    public BigDecimal getReversedAmount() {
        return reversedAmount;
    }

    /** Terminal reference of the reversal. */
    public String getPoiTransactionId() {
        return poiTransactionId;
    }

    public Instant getPoiTransactionTimestamp() {
        return poiTransactionTimestamp;
    }

    public Receipt getCustomerReceipt() {
        return customerReceipt;
    }

    public Receipt getMerchantReceipt() {
        return merchantReceipt;
    }

    /** Loyalty points reversed alongside the void; best-effort. */
    public int getPointsReversed() {
        return pointsReversed;
    }

    /** Member's balance after the reversal; {@code 0} when not reported. */
    public int getRemainingPointBalance() {
        return remainingPointBalance;
    }

    /** Builder for {@link VoidResult}. Intended for SDK use. */
    public static final class Builder {

        private boolean success;
        private BigDecimal reversedAmount;
        private String poiTransactionId;
        private Instant poiTransactionTimestamp;
        private Receipt customerReceipt;
        private Receipt merchantReceipt;
        private int pointsReversed;
        private int remainingPointBalance;

        private Builder() {
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder reversedAmount(BigDecimal reversedAmount) {
            this.reversedAmount = reversedAmount;
            return this;
        }

        public Builder poiTransactionId(String poiTransactionId) {
            this.poiTransactionId = poiTransactionId;
            return this;
        }

        public Builder poiTransactionTimestamp(Instant poiTransactionTimestamp) {
            this.poiTransactionTimestamp = poiTransactionTimestamp;
            return this;
        }

        public Builder customerReceipt(Receipt customerReceipt) {
            this.customerReceipt = customerReceipt;
            return this;
        }

        public Builder merchantReceipt(Receipt merchantReceipt) {
            this.merchantReceipt = merchantReceipt;
            return this;
        }

        public Builder pointsReversed(int pointsReversed) {
            this.pointsReversed = pointsReversed;
            return this;
        }

        public Builder remainingPointBalance(int remainingPointBalance) {
            this.remainingPointBalance = remainingPointBalance;
            return this;
        }

        public VoidResult build() {
            return new VoidResult(this);
        }
    }
}
