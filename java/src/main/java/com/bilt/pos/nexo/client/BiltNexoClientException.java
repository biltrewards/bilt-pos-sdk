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
 * Exception thrown when communication with the Nexo terminal fails.
 *
 * <p>Wraps serialization errors, HTTP transport failures, non-2xx responses,
 * and deserialization errors into a single checked exception.</p>
 */
public class BiltNexoClientException extends Exception {

    public BiltNexoClientException(String message) {
        super(message);
    }

    public BiltNexoClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
