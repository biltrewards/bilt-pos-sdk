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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Persistable reference for reversing one fulfilled stored value basket line. */
public final class StoredValueLoadRecord {

    private final String basketReference;
    private final BigDecimal amount;
    private final String poiTransactionId;
    private final Instant poiTransactionTimestamp;

    private StoredValueLoadRecord(Builder builder) {
        this.basketReference = Objects.requireNonNull(builder.basketReference,
                "basketReference");
        this.amount = Objects.requireNonNull(builder.amount, "amount");
        this.poiTransactionId = Objects.requireNonNull(builder.poiTransactionId,
                "poiTransactionId");
        this.poiTransactionTimestamp = builder.poiTransactionTimestamp;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getBasketReference() {
        return basketReference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getPoiTransactionId() {
        return poiTransactionId;
    }

    public Instant getPoiTransactionTimestamp() {
        return poiTransactionTimestamp;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StoredValueLoadRecord)) {
            return false;
        }
        StoredValueLoadRecord that = (StoredValueLoadRecord) other;
        return amount.compareTo(that.amount) == 0
                && basketReference.equals(that.basketReference)
                && poiTransactionId.equals(that.poiTransactionId)
                && Objects.equals(poiTransactionTimestamp, that.poiTransactionTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(basketReference, amount.stripTrailingZeros(), poiTransactionId,
                poiTransactionTimestamp);
    }

    public static final class Builder {
        private String basketReference;
        private BigDecimal amount;
        private String poiTransactionId;
        private Instant poiTransactionTimestamp;

        private Builder() {
        }

        public Builder basketReference(String basketReference) {
            this.basketReference = basketReference;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
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

        public StoredValueLoadRecord build() {
            return new StoredValueLoadRecord(this);
        }
    }
}
