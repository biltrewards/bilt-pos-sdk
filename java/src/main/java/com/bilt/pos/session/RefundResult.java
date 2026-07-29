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
 * Outcome of a refund.
 *
 * <p>Linked refunds also reverse loyalty points awarded on the original
 * transaction; the reversal outcome is reported via
 * {@link #getPointsReversed()} (best-effort — a failed loyalty reversal does
 * not fail the refund).</p>
 */
public final class RefundResult {

    private final boolean success;
    private final BigDecimal refundedAmount;
    private final String approvalCode;
    private final String poiTransactionId;
    private final Instant poiTransactionTimestamp;
    private final Receipt customerReceipt;
    private final Receipt merchantReceipt;
    private final int pointsReversed;
    private final int remainingPointBalance;

    private RefundResult(Builder builder) {
        this.success = builder.success;
        this.refundedAmount = builder.refundedAmount;
        this.approvalCode = builder.approvalCode;
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

    /** Amount refunded; {@code null} when the terminal did not echo it. */
    public BigDecimal getRefundedAmount() {
        return refundedAmount;
    }

    public String getApprovalCode() {
        return approvalCode;
    }

    /** Terminal reference of the refund, for later status checks. */
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

    /** Loyalty points reversed by a linked refund; {@code 0} for unlinked. */
    public int getPointsReversed() {
        return pointsReversed;
    }

    /** Member's balance after the reversal; {@code 0} when not reported. */
    public int getRemainingPointBalance() {
        return remainingPointBalance;
    }

    /** Builder for {@link RefundResult}. Intended for SDK use. */
    public static final class Builder {

        private boolean success;
        private BigDecimal refundedAmount;
        private String approvalCode;
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

        public Builder refundedAmount(BigDecimal refundedAmount) {
            this.refundedAmount = refundedAmount;
            return this;
        }

        public Builder approvalCode(String approvalCode) {
            this.approvalCode = approvalCode;
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

        public RefundResult build() {
            return new RefundResult(this);
        }
    }
}
