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

    public AbandonedSettlementRecord(String settlementId, Instant abandonedAt, Basket basket,
                                     SettlementOptions options, String memberId,
                                     SettlementFailure failure, BigDecimal outstandingAmount,
                                     List<SettlementMovement> committedMovements) {
        this.settlementId = Objects.requireNonNull(settlementId, "settlementId");
        this.abandonedAt = Objects.requireNonNull(abandonedAt, "abandonedAt");
        this.basket = Objects.requireNonNull(basket, "basket");
        this.options = Objects.requireNonNull(options, "options");
        this.memberId = memberId;
        this.failure = Objects.requireNonNull(failure, "failure");
        this.outstandingAmount = outstandingAmount == null
                ? BigDecimal.ZERO : outstandingAmount;
        this.committedMovements = Collections.unmodifiableList(new ArrayList<>(
                committedMovements == null ? List.of() : committedMovements));
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
}
