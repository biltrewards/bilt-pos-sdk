/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.display;

import com.bilt.pos.session.SessionState;

/**
 * Context handed to a {@link DisplayRenderer} alongside the basket.
 */
public final class DisplayContext {

    private final SessionState state;
    private final DisplayTarget target;
    private final String currency;

    public DisplayContext(SessionState state, DisplayTarget target, String currency) {
        this.state = state;
        this.target = target;
        this.currency = currency;
    }

    /** The session state at render time. */
    public SessionState getState() {
        return state;
    }

    /** The device the payload will be rendered on. */
    public DisplayTarget getTarget() {
        return target;
    }

    /** The session's ISO 4217 currency code. */
    public String getCurrency() {
        return currency;
    }
}
