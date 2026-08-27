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

/**
 * How a reversal flow proceeds after one of its steps failed, returned by
 * the handler registered with {@link ReversalFlow#onError}.
 *
 * <p>Already-reversed steps always stand — there is no compensating
 * re-commit. A step left standing by {@link #SKIP} or {@link #ABORT} is
 * retried by the next {@code voidTransaction()} on the same session, which
 * resumes at the first step still standing.</p>
 */
public enum ReversalDecision {

    /** Re-send the failed step's request and continue from its outcome. */
    RETRY,

    /**
     * Leave this movement standing (the terminal may retry loyalty
     * movements via store-and-forward) and continue with the remaining
     * steps. A void honors a skipped money leg the same way — the
     * remaining steps still run — but then fails as incomplete: a standing
     * card or stored value leg has no
     * terminal-side retry, so the reversed movements stay recorded and a
     * retried {@code voidTransaction()} sends only the legs still
     * standing. A refund flow's tender step may be skipped outright: no
     * money moves and the sale stays voidable.
     */
    SKIP,

    /**
     * Stop the flow. The failure is thrown, already-reversed steps stand,
     * so the operation can be retried.
     */
    ABORT
}
