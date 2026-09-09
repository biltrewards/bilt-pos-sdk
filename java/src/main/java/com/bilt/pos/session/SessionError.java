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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Describes why a session operation failed.
 *
 * <p>Delivered directly to ordinary {@code SessionResult.onError} handlers,
 * wrapped by {@code SettlementFailure} for {@code SettlementFlow.onError},
 * and carried by {@link SessionException} when a failed result is unwrapped
 * via {@link SessionResult#get()}.</p>
 */
public final class SessionError {

    private final SessionErrorCode code;
    private final String message;
    private final String nexoErrorCondition;
    private final Exception cause;
    private final List<ReversedMovement> reversedMovements;

    public SessionError(SessionErrorCode code, String message) {
        this(code, message, null, null, Collections.emptyList());
    }

    public SessionError(SessionErrorCode code, String message,
                        String nexoErrorCondition, Exception cause) {
        this(code, message, nexoErrorCondition, cause, Collections.emptyList());
    }

    /**
     * Creates an error with structured progress from a stopped reversal.
     * Ordinary operation failures should use one of the shorter constructors.
     */
    public SessionError(SessionErrorCode code, String message,
                        String nexoErrorCondition, Exception cause,
                        List<ReversedMovement> reversedMovements) {
        this.code = code;
        this.message = message;
        this.nexoErrorCondition = nexoErrorCondition;
        this.cause = cause;
        this.reversedMovements = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(reversedMovements, "reversedMovements")));
    }

    /** High-level error category. */
    public SessionErrorCode getCode() {
        return code;
    }

    /** Human-readable description of the failure. */
    public String getMessage() {
        return message;
    }

    /**
     * The raw Nexo {@code ErrorCondition} string from the terminal response,
     * or {@code null} if the failure did not originate from a terminal reply.
     */
    public String getNexoErrorCondition() {
        return nexoErrorCondition;
    }

    /** The underlying exception, or {@code null} if none. */
    public Exception getCause() {
        return cause;
    }

    /**
     * Immutable movements that completed before this void failure. Available
     * both to the failing step's {@link ReversalFlow#onError} handler and on
     * the final error when that handler aborts. Empty for ordinary failures
     * and when the failed void made no progress.
     */
    public List<ReversedMovement> getReversedMovements() {
        return reversedMovements;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SessionError[").append(code);
        if (message != null) {
            sb.append(": ").append(message);
        }
        if (nexoErrorCondition != null) {
            sb.append(" (ErrorCondition=").append(nexoErrorCondition).append(')');
        }
        return sb.append(']').toString();
    }
}
