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
 * Describes why a session operation failed.
 *
 * <p>Delivered to {@code onError} handlers and carried by
 * {@link SessionException} when a failed result is unwrapped via
 * {@link SessionResult#get()}.</p>
 */
public final class SessionError {

    private final SessionErrorCode code;
    private final String message;
    private final String nexoErrorCondition;
    private final Exception cause;

    public SessionError(SessionErrorCode code, String message) {
        this(code, message, null, null);
    }

    public SessionError(SessionErrorCode code, String message,
                        String nexoErrorCondition, Exception cause) {
        this.code = code;
        this.message = message;
        this.nexoErrorCondition = nexoErrorCondition;
        this.cause = cause;
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
