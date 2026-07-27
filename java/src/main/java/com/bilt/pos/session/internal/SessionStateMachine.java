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

import static com.bilt.pos.session.SessionState.ABORTED;
import static com.bilt.pos.session.SessionState.ACTIVE;
import static com.bilt.pos.session.SessionState.COMPLETED;
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
        TRANSITIONS.put(IDLE, EnumSet.of(IDENTIFIED, ACTIVE, VOIDING, ABORTED));
        TRANSITIONS.put(IDENTIFIED, EnumSet.of(ACTIVE, IDLE, ABORTED));
        TRANSITIONS.put(ACTIVE, EnumSet.of(PAYING, IDLE, IDENTIFIED, ABORTED));
        TRANSITIONS.put(PAYING, EnumSet.of(COMPLETED, FAILED, ABORTED));
        TRANSITIONS.put(COMPLETED, EnumSet.of(VOIDING));
        TRANSITIONS.put(FAILED, EnumSet.of(PAYING, VOIDING, ABORTED));
        TRANSITIONS.put(VOIDING, EnumSet.of(VOIDED, FAILED));
        TRANSITIONS.put(ABORTED, EnumSet.noneOf(SessionState.class));
        TRANSITIONS.put(VOIDED, EnumSet.noneOf(SessionState.class));
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
