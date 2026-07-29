/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.input;

import com.bilt.pos.nexo.model.CardholderPIN;

/**
 * Outcome of a PIN operation.
 *
 * <p>For {@link PinMode#PIN_ENTER} and {@link PinMode#PIN_VERIFY} the
 * encrypted PIN block is carried in the {@link CardholderPIN} structure
 * (a CMS envelope, never a raw PIN).</p>
 */
public final class PinResult {

    private final PinMode mode;
    private final boolean verified;
    private final CardholderPIN cardholderPin;

    public PinResult(PinMode mode, boolean verified, CardholderPIN cardholderPin) {
        this.mode = mode;
        this.verified = verified;
        this.cardholderPin = cardholderPin;
    }

    public PinMode getMode() {
        return mode;
    }

    /** For the verify modes: whether the PIN matched. */
    public boolean isVerified() {
        return verified;
    }

    /** The encrypted PIN block structure, or {@code null} for verify-only. */
    public CardholderPIN getCardholderPin() {
        return cardholderPin;
    }
}
