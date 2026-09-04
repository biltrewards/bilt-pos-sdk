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

import com.bilt.pos.session.basket.Basket;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable manual-takeover record produced when the register abandons recovery.
 * The SDK performs no status check or unwind and places no further guard on the
 * basket; every committed movement in this record becomes the register's
 * reconciliation responsibility.
 */
public final class AbandonedSettlementRecord {

    private final String settlementId;
    private final Instant abandonedAt;
    private final Basket basket;
    private final SettlementOptions options;
    private final String memberId;
    private final SettlementFailure failure;
    private final BigDecimal outstandingAmount;
    private final List<SettlementMovement> committedMovements;

    private AbandonedSettlementRecord(Builder builder) {
        this.settlementId = Objects.requireNonNull(builder.settlementId, "settlementId");
        this.abandonedAt = Objects.requireNonNull(builder.abandonedAt, "abandonedAt");
        this.basket = Objects.requireNonNull(builder.basket, "basket");
        this.options = Objects.requireNonNull(builder.options, "options");
        this.memberId = builder.memberId;
        this.failure = Objects.requireNonNull(builder.failure, "failure");
        this.outstandingAmount = Objects.requireNonNull(
                builder.outstandingAmount, "outstandingAmount");
        this.committedMovements = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(builder.committedMovements, "committedMovements")));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSettlementId() {
        return settlementId;
    }

    public Instant getAbandonedAt() {
        return abandonedAt;
    }

    public Basket getBasket() {
        return basket;
    }

    public SettlementOptions getOptions() {
        return options;
    }

    /** Identified member ID, or {@code null} when no member was identified. */
    public String getMemberId() {
        return memberId;
    }

    public SettlementFailure getFailure() {
        return failure;
    }

    public BigDecimal getOutstandingAmount() {
        return outstandingAmount;
    }

    public List<SettlementMovement> getCommittedMovements() {
        return committedMovements;
    }

    /** Builder for {@link AbandonedSettlementRecord}. */
    public static final class Builder {

        private String settlementId;
        private Instant abandonedAt;
        private Basket basket;
        private SettlementOptions options;
        private String memberId;
        private SettlementFailure failure;
        private BigDecimal outstandingAmount;
        private List<SettlementMovement> committedMovements;

        private Builder() {
        }

        public Builder settlementId(String settlementId) {
            this.settlementId = settlementId;
            return this;
        }

        public Builder abandonedAt(Instant abandonedAt) {
            this.abandonedAt = abandonedAt;
            return this;
        }

        public Builder basket(Basket basket) {
            this.basket = basket;
            return this;
        }

        public Builder options(SettlementOptions options) {
            this.options = options;
            return this;
        }

        public Builder memberId(String memberId) {
            this.memberId = memberId;
            return this;
        }

        public Builder failure(SettlementFailure failure) {
            this.failure = failure;
            return this;
        }

        public Builder outstandingAmount(BigDecimal outstandingAmount) {
            this.outstandingAmount = outstandingAmount;
            return this;
        }

        public Builder committedMovements(List<SettlementMovement> committedMovements) {
            this.committedMovements = committedMovements;
            return this;
        }

        public AbandonedSettlementRecord build() {
            return new AbandonedSettlementRecord(this);
        }
    }
}
