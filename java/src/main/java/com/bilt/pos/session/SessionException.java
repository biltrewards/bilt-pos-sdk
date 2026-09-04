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

import com.bilt.pos.session.settlement.AbandonedSettlementRecord;

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
    private final transient AbandonedSettlementRecord abandonedSettlement;

    public SessionException(SessionError error) {
        this(error, null);
    }

    /** Creates a settlement-abandonment failure carrying its manual-takeover record. */
    public SessionException(SessionError error, AbandonedSettlementRecord abandonedSettlement) {
        super(error.toString(), error.getCause());
        this.error = error;
        this.abandonedSettlement = abandonedSettlement;
    }

    /** The error that caused the operation to fail. */
    public SessionError getError() {
        return error;
    }

    /** Manual-takeover record, or {@code null} when this is not an abandonment. */
    public AbandonedSettlementRecord getAbandonedSettlement() {
        return abandonedSettlement;
    }
}
