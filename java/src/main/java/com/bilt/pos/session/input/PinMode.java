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

/** Kind of PIN operation. Maps to the Nexo {@code PINRequestType}. */
public enum PinMode {

    /** Capture and encrypt a PIN. */
    PIN_ENTER,

    /** Capture a PIN and verify it, returning the encrypted block. */
    PIN_VERIFY,

    /** Verify a PIN without returning the block. */
    PIN_VERIFY_ONLY
}
