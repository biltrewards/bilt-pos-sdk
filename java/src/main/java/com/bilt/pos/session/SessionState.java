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
 * Lifecycle state of a {@link CheckoutSession}.
 *
 * <pre>
 * IDLE ── identifyMember() ──► IDENTIFIED
 *   │                              │
 *   └────────── addItem() ◄────────┘
 *                  │
 *               ACTIVE ◄── retry settle() ── FAILED
 *                  │                        ▲
 *               settle().execute()          │
 *                  │                        │
 *               SETTLING ── success ──► COMPLETED
 *                  └────── failure ─────────┘
 *
 * abort()            interrupts the in-flight operation only — an aborted
 *                    settlement unwinds and settles FAILED; the session state
 *                    is otherwise unchanged
 * voidTransaction()  ──► VOIDING ──► VOIDED
 * end()              ──► ENDED     (from any state except SETTLING/VOIDING)
 * </pre>
 */
public enum SessionState {

    /** Session created; no member identified and no items in the basket. */
    IDLE,

    /** A member was explicitly identified; basket is still empty. */
    IDENTIFIED,

    /** The basket contains at least one item. */
    ACTIVE,

    /** Settlement orchestration is in progress; the basket is frozen. */
    SETTLING,

    /** The settlement sequence completed successfully. Terminal state. */
    COMPLETED,

    /** The settlement sequence failed or was aborted. Retry {@code settle()} or void. */
    FAILED,

    /** A void of the completed transaction is in progress. */
    VOIDING,

    /** The transaction was voided. Terminal state. */
    VOIDED,

    /**
     * The session was ended: the terminal was told to discard its
     * session-scoped data, and no further operation of any kind is allowed
     * — create a new session for the next checkout. Terminal state.
     */
    ENDED;

    /** Returns {@code true} if no further operations are possible from this state. */
    public boolean isTerminal() {
        return this == COMPLETED || this == VOIDED || this == ENDED;
    }
}
