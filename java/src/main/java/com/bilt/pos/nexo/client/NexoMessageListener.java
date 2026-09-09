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
 * unsuccessful HTTP response bodies are reported as received. Successful
 * response JSON is delivered exactly as supplied by the terminal, without
 * normalization or re-serialization. Listener failures are logged and never
 * interrupt the terminal operation.</p>
 *
 * <p><strong>Security:</strong> Payloads may contain cardholder data,
 * including full PANs; do not persist or log them in production builds.</p>
 *
 * <p>The listener runs synchronously on the thread executing
 * {@code request(...)}. Keep the callback cheap and hand off UI or
 * long-running work; callback time extends the terminal operation.</p>
 */
@FunctionalInterface
public interface NexoMessageListener {

    enum Direction {
        REQUEST,
        RESPONSE
    }

    void onMessage(Direction direction, String json);
}
