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
 * Unchecked exception thrown when a failed session operation is unwrapped
 * via {@link SessionResult#get()}.
 *
 * <p>Callers that registered an {@code onError} handler and use
 * {@code execute()} or {@code getOrNull()} never see this exception.</p>
 */
public final class SessionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient SessionError error;

    public SessionException(SessionError error) {
        super(error.toString(), error.getCause());
        this.error = error;
    }

    /** The error that caused the operation to fail. */
    public SessionError getError() {
        return error;
    }
}
