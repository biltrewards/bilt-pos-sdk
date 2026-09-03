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
 * High-level error category for a failed session operation.
 *
 * <p>Derived from the transport outcome and the Nexo
 * {@code Response.ErrorCondition} of the terminal reply; the raw condition is
 * preserved on {@link SessionError#getNexoErrorCondition()}.</p>
 */
public enum SessionErrorCode {

    /** The terminal could not be reached or the connection failed mid-exchange. */
    NETWORK,

    /** The operation did not complete within the configured timeout. */
    TIMEOUT,

    /** The terminal or acquirer declined the operation. */
    DECLINED,

    /** The customer cancelled on the terminal. */
    CANCELLED,

    /** The operation was aborted (by {@code abort()} or the terminal). */
    ABORTED,

    /** Settlement recovery was explicitly transferred to the register. */
    ABANDONED,

    /** The loyalty host is unavailable; the failed optional step may be skipped. */
    LOYALTY_UNAVAILABLE,

    /** The stored value card balance does not cover the requested amount. */
    STORED_VALUE_INSUFFICIENT,

    /** The operation is not valid in the session's current state. */
    INVALID_STATE,

    /** The terminal reported a device or service error. */
    TERMINAL_ERROR,

    /** The failure could not be classified. */
    UNKNOWN
}
