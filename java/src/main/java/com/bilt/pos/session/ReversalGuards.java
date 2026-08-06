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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The mutual-exclusion guards between the refunds and voids of one sale,
 * shared by the session types. Money decides both directions:
 *
 * <ul>
 *   <li>once a refund has returned money ({@link #markRefunded}, raised
 *       the moment the tender refund completes, even if a later step
 *       aborts the flow), a void is refused — it would return the full
 *       amount on top of the refund;</li>
 *   <li>once a void has reversed a money leg, refunds are refused until
 *       the void is finished — a refund against the reversed leg would be
 *       a double return. A reversed loyalty movement alone (e.g. the
 *       award, after a refund whose tender was skipped by decision)
 *       blocks nothing: the tender stays refundable, and the recorded
 *       progress keeps a void or a retried refund from re-crediting the
 *       award.</li>
 * </ul>
 *
 * <p>{@link #reversedSteps()} doubles as the void's resume state — the
 * sale's movements already reversed, fed to and updated by
 * {@code ReversalManager.voidMovements}.</p>
 */
final class ReversalGuards {

    private final Set<ReversalStep> reversedSteps = ConcurrentHashMap.newKeySet();
    private volatile boolean refundIssued;
    private final String subject;

    /** @param subject the sale as the session names it: "payment" or "sale" */
    ReversalGuards(String subject) {
        this.subject = subject;
    }

    /** The sale's movements already reversed, by a void or a refund's award reversal. */
    Set<ReversalStep> reversedSteps() {
        return reversedSteps;
    }

    /** Guards {@code voidTransaction()}: refused once a refund returned money. */
    void requireNotRefunded() {
        if (refundIssued) {
            throw new SessionException(new SessionError(SessionErrorCode.INVALID_STATE,
                    "the " + subject + " was already refunded from this session; a void "
                            + "would return the full amount on top of the refund — use "
                            + "refund(amount) for further returns"));
        }
    }

    /** Guards {@code refund()}: refused while a void has a money leg reversed. */
    void requireNoReversedMoneyLeg() {
        if (reversedSteps.contains(ReversalStep.CARD)
                || reversedSteps.contains(ReversalStep.STORED_VALUE)) {
            throw new SessionException(new SessionError(SessionErrorCode.INVALID_STATE,
                    "a void of this " + subject + " is partially complete; finish it "
                            + "with voidTransaction() — a refund cannot mix with a "
                            + "half-reversed sale"));
        }
    }

    /** A refund moved money; the sale can no longer be voided. */
    void markRefunded() {
        refundIssued = true;
    }

    /** A new payment replaced the guarded sale; the refund guard lifts. */
    void clearRefundIssued() {
        refundIssued = false;
    }

    /** Whether a refund flow already reversed the award. */
    boolean awardReversed() {
        return reversedSteps.contains(ReversalStep.AWARD);
    }

    /** A refund flow reversed the award; nothing may re-credit it. */
    void markAwardReversed() {
        reversedSteps.add(ReversalStep.AWARD);
    }
}
