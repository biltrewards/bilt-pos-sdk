/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.storedvalue;

import com.bilt.pos.nexo.model.StoredValueTransactionTypeEnum;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Outcome of a stored value operation (activate, load, unload, reserve,
 * reverse, duplicate).
 */
public final class StoredValueOperationResult {

    private final StoredValueTransactionTypeEnum transactionType;
    private final BigDecimal amount;
    private final BigDecimal currentBalance;
    private final String currency;
    private final String poiTransactionId;
    private final Instant poiTransactionTimestamp;
    private final String hostTransactionId;

    private StoredValueOperationResult(Builder builder) {
        this.transactionType = builder.transactionType;
        this.amount = builder.amount;
        this.currentBalance = builder.currentBalance;
        this.currency = builder.currency;
        this.poiTransactionId = builder.poiTransactionId;
        this.poiTransactionTimestamp = builder.poiTransactionTimestamp;
        this.hostTransactionId = builder.hostTransactionId;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The operation the terminal performed. */
    public StoredValueTransactionTypeEnum getTransactionType() {
        return transactionType;
    }

    /** Amount moved by the operation; {@code null} when not echoed. */
    public BigDecimal getAmount() {
        return amount;
    }

    /** Balance on the card after the operation, or {@code null} if not reported. */
    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public String getCurrency() {
        return currency;
    }

    /** Terminal reference — needed for {@code storedValueReverse(...)}. */
    public String getPoiTransactionId() {
        return poiTransactionId;
    }

    public Instant getPoiTransactionTimestamp() {
        return poiTransactionTimestamp;
    }

    /** Stored value provider's transaction reference, or {@code null}. */
    public String getHostTransactionId() {
        return hostTransactionId;
    }

    /** Builder for {@link StoredValueOperationResult}. Intended for SDK use. */
    public static final class Builder {

        private StoredValueTransactionTypeEnum transactionType;
        private BigDecimal amount;
        private BigDecimal currentBalance;
        private String currency;
        private String poiTransactionId;
        private Instant poiTransactionTimestamp;
        private String hostTransactionId;

        private Builder() {
        }

        public Builder transactionType(StoredValueTransactionTypeEnum transactionType) {
            this.transactionType = transactionType;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder currentBalance(BigDecimal currentBalance) {
            this.currentBalance = currentBalance;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
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

        public Builder hostTransactionId(String hostTransactionId) {
            this.hostTransactionId = hostTransactionId;
            return this;
        }

        public StoredValueOperationResult build() {
            return new StoredValueOperationResult(this);
        }
    }
}
