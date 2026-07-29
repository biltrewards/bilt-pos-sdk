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
 *               ACTIVE ◄── retry pay() ── FAILED
 *                  │                        ▲
 *               pay().execute()             │
 *                  │                        │
 *               PAYING ── success ──► COMPLETED
 *                  └────── failure ─────────┘
 *
 * abort()            ──► ABORTED   (from any non-terminal state)
 * voidTransaction()  ──► VOIDING ──► VOIDED
 * </pre>
 */
public enum SessionState {

    /** Session created; no member identified and no items in the basket. */
    IDLE,

    /** A member was explicitly identified; basket is still empty. */
    IDENTIFIED,

    /** The basket contains at least one item. */
    ACTIVE,

    /** Payment orchestration is in progress; the basket is frozen. */
    PAYING,

    /** The payment sequence completed successfully. Terminal state. */
    COMPLETED,

    /** The payment sequence failed. Retry {@code pay()} or void. */
    FAILED,

    /** The session was aborted. Terminal state. */
    ABORTED,

    /** A void of the completed transaction is in progress. */
    VOIDING,

    /** The transaction was voided. Terminal state. */
    VOIDED;

    /** Returns {@code true} if no further operations are possible from this state. */
    public boolean isTerminal() {
        return this == COMPLETED || this == ABORTED || this == VOIDED;
    }
}
