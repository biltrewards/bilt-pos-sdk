/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.nexo.client;

/**
 * Observes the plaintext Nexo messages exchanged with a terminal.
 *
 * <p>The listener is intended for diagnostics. Requests are reported before
 * encryption and valid Nexo responses after decryption, as JSON. Malformed or
 * unsuccessful HTTP response bodies are reported as received. Listener
 * failures are logged and never interrupt the terminal operation.</p>
 */
@FunctionalInterface
public interface NexoMessageListener {

    enum Direction {
        REQUEST,
        RESPONSE
    }

    void onMessage(Direction direction, String json);
}
