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
import com.bilt.pos.session.settlement.StoredValueLoadRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One reversible movement of a prior sale: which leg it is and the POI
 * reference to reverse it by. The step determines the wire verb —
 * {@code StoredValueRequest(Reverse)} for fulfilled load lines,
 * {@code ReversalRequest} for tender legs, and the matching
 * {@code LoyaltyRequest} refund type for loyalty legs.
 */
public final class ReversalMovement {

    /** Stable resume identity; unlike a step enum, supports several load reversals. */
    public static final class Key {
        private final ReversalStep step;
        private final String poiTransactionId;

        private Key(ReversalStep step, String poiTransactionId) {
            this.step = step;
            this.poiTransactionId = poiTransactionId;
        }

        public ReversalStep getStep() {
            return step;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key)) {
                return false;
            }
            Key that = (Key) other;
            return step == that.step && poiTransactionId.equals(that.poiTransactionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(step, poiTransactionId);
        }
    }

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

    public Key key() {
        return new Key(step, poiTransactionId);
    }

    public static Key key(ReversalStep step, String poiTransactionId) {
        return new Key(Objects.requireNonNull(step, "step"),
                Objects.requireNonNull(poiTransactionId, "poiTransactionId"));
    }

    /**
     * The movements of a sale in reversal order — money legs first,
     * mirroring the payment sequence's reverse-commit unwind order — one
     * per non-null reference. Every reference is nullable by design:
     * {@code null} means the sale has no such leg (a guest sale has no
     * award, a card-only sale no rebate or redemption, a rewards-covered
     * sale no card leg), so no movement is produced for it. Whether the
     * resulting list may be empty is the caller's rule — the sessions
     * guard it. A gift-card-only sale reports the same reference for both
     * money legs; it yields one movement.
     */
    public static List<ReversalMovement> ofSale(PoiRef card, PoiRef storedValue,
                                                PoiRef redemption, PoiRef rebate,
                                                PoiRef award) {
        return ofSale(List.of(), card, storedValue, redemption, rebate, award);
    }

    /** A sale including stored value line fulfillments, reversed before its funding. */
    public static List<ReversalMovement> ofSale(List<StoredValueLoadRecord> storedValueLoads,
                                                PoiRef card, PoiRef storedValue,
                                                PoiRef redemption, PoiRef rebate,
                                                PoiRef award) {
        List<ReversalMovement> movements = new ArrayList<>();
        for (StoredValueLoadRecord load : storedValueLoads) {
            movements.add(new ReversalMovement(ReversalStep.STORED_VALUE_LOAD,
                    load.getPoiTransactionId(), load.getPoiTransactionTimestamp()));
        }
        add(movements, ReversalStep.CARD, card);
        if (card == null || storedValue == null || !storedValue.txnId.equals(card.txnId)) {
            add(movements, ReversalStep.STORED_VALUE, storedValue);
        }
        add(movements, ReversalStep.REDEMPTION, redemption);
        add(movements, ReversalStep.REBATE, rebate);
        add(movements, ReversalStep.AWARD, award);
        return movements;
    }

    /** Adds the leg's movement; a {@code null} reference means the sale has no such leg. */
    private static void add(List<ReversalMovement> movements, ReversalStep step, PoiRef ref) {
        if (ref != null) {
            movements.add(new ReversalMovement(step, ref.txnId, ref.timestamp));
        }
    }
}
