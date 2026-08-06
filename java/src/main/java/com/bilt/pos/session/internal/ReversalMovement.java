/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   Internal API — subject to change without notice.
 */
package com.bilt.pos.session.internal;

import com.bilt.pos.session.ReversalStep;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One reversible movement of a prior sale: which leg it is and the POI
 * reference to reverse it by. The step determines the wire verb —
 * {@code ReversalRequest} for the money legs, the matching
 * {@code LoyaltyRequest} refund type for the loyalty legs.
 */
public final class ReversalMovement {

    private final ReversalStep step;
    final String poiTransactionId;
    final Instant poiTransactionTimestamp;

    public ReversalMovement(ReversalStep step, String poiTransactionId,
                            Instant poiTransactionTimestamp) {
        this.step = step;
        this.poiTransactionId = poiTransactionId;
        this.poiTransactionTimestamp = poiTransactionTimestamp;
    }

    public ReversalStep getStep() {
        return step;
    }

    /**
     * The movements of a sale in reversal order — money legs first,
     * mirroring the payment sequence's reverse-commit unwind order — one
     * per non-null reference. Every parameter is nullable by design: a
     * {@code null} reference means the sale has no such leg (a guest sale
     * has no award, a card-only sale no rebate or redemption, a
     * rewards-covered sale no card leg), so no movement is produced for
     * it. Whether the resulting list may be empty is the caller's rule —
     * the sessions guard it ({@code ReversalSession} requires a reference
     * at build time, {@code CheckoutSession} a completed payment).
     * A gift-card-only sale reports the same reference for both money
     * legs; it yields one movement.
     */
    public static List<ReversalMovement> ofSale(
            String cardPoiTxnId, Instant cardPoiTimestamp,
            String storedValuePoiTxnId, Instant storedValuePoiTimestamp,
            String redemptionPoiTxnId, Instant redemptionPoiTimestamp,
            String rebatePoiTxnId, Instant rebatePoiTimestamp,
            String awardPoiTxnId, Instant awardPoiTimestamp) {
        List<ReversalMovement> movements = new ArrayList<>();
        add(movements, ReversalStep.CARD, cardPoiTxnId, cardPoiTimestamp);
        if (!Objects.equals(storedValuePoiTxnId, cardPoiTxnId)) {
            add(movements, ReversalStep.STORED_VALUE,
                    storedValuePoiTxnId, storedValuePoiTimestamp);
        }
        add(movements, ReversalStep.REDEMPTION, redemptionPoiTxnId, redemptionPoiTimestamp);
        add(movements, ReversalStep.REBATE, rebatePoiTxnId, rebatePoiTimestamp);
        add(movements, ReversalStep.AWARD, awardPoiTxnId, awardPoiTimestamp);
        return movements;
    }

    /** Adds the leg's movement; a {@code null} reference means the sale has no such leg. */
    private static void add(List<ReversalMovement> movements, ReversalStep step,
                            String poiTransactionId, Instant timestamp) {
        if (poiTransactionId != null) {
            movements.add(new ReversalMovement(step, poiTransactionId, timestamp));
        }
    }
}
