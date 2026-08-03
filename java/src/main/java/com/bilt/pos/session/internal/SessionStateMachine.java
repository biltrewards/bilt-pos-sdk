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

import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.SessionErrorCode;
import com.bilt.pos.session.SessionState;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.bilt.pos.session.SessionState.ACTIVE;
import static com.bilt.pos.session.SessionState.COMPLETED;
import static com.bilt.pos.session.SessionState.ENDED;
import static com.bilt.pos.session.SessionState.FAILED;
import static com.bilt.pos.session.SessionState.IDENTIFIED;
import static com.bilt.pos.session.SessionState.IDLE;
import static com.bilt.pos.session.SessionState.PAYING;
import static com.bilt.pos.session.SessionState.VOIDED;
import static com.bilt.pos.session.SessionState.VOIDING;

/**
 * Tracks the {@link SessionState} of a checkout session and enforces the
 * legal transition table. Callers are expected to guard access with the
 * session lock; this class itself performs no synchronization.
 */
public final class SessionStateMachine {

    private static final Map<SessionState, Set<SessionState>> TRANSITIONS =
            new EnumMap<>(SessionState.class);

    static {
        // ENDED — the register said goodbye and the terminal discarded its
        // session data — is reachable from every state except the two with
        // money in flight (PAYING, VOIDING); the other terminal states allow
        // it so a finished checkout can still be ended, but nothing leaves it
        TRANSITIONS.put(IDLE, EnumSet.of(IDENTIFIED, ACTIVE, VOIDING, ENDED));
        TRANSITIONS.put(IDENTIFIED, EnumSet.of(ACTIVE, IDLE, ENDED));
        TRANSITIONS.put(ACTIVE, EnumSet.of(PAYING, IDLE, IDENTIFIED, ENDED));
        // an aborted payment lands in FAILED like any other unwound failure
        TRANSITIONS.put(PAYING, EnumSet.of(COMPLETED, FAILED));
        TRANSITIONS.put(COMPLETED, EnumSet.of(VOIDING, ENDED));
        // FAILED -> ACTIVE: the tender was fully unwound, so the register
        // may adjust the basket before retrying pay()
        TRANSITIONS.put(FAILED, EnumSet.of(PAYING, ACTIVE, VOIDING, ENDED));
        // a failed void restores the pre-void state (IDLE/COMPLETED/FAILED):
        // the referenced transaction still stands, so e.g. a COMPLETED
        // payment must not degrade to FAILED where pay() could re-charge
        TRANSITIONS.put(VOIDING, EnumSet.of(VOIDED, IDLE, COMPLETED, FAILED));
        TRANSITIONS.put(VOIDED, EnumSet.of(ENDED));
        TRANSITIONS.put(ENDED, EnumSet.noneOf(SessionState.class));
    }

    private volatile SessionState current = IDLE;

    /** The current session state. */
    public SessionState current() {
        return current;
    }

    /**
     * Moves to {@code target}, or throws if the transition is not legal.
     *
     * @throws IllegalStateException on an illegal transition — this indicates
     *         a bug in the session, not a caller error
     */
    public void transitionTo(SessionState target) {
        if (!TRANSITIONS.get(current).contains(target)) {
            throw new IllegalStateException(
                    "illegal session state transition " + current + " -> " + target);
        }
        current = target;
    }

    /** Whether the transition {@code current -> target} is legal. */
    public boolean canTransitionTo(SessionState target) {
        return TRANSITIONS.get(current).contains(target);
    }

    /**
     * Returns {@code null} when {@code operation} may run in the current
     * state, or an {@link SessionErrorCode#INVALID_STATE} error describing
     * the violation otherwise.
     */
    public SessionError requireState(Set<SessionState> allowed, String operation) {
        SessionState state = current;
        if (allowed.contains(state)) {
            return null;
        }
        return new SessionError(SessionErrorCode.INVALID_STATE,
                operation + " is not allowed in state " + state
                        + " (allowed: " + allowed + ")");
    }
}
