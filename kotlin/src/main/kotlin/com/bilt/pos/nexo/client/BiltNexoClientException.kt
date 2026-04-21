/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.nexo.client

/**
 * Thrown when the terminal client encounters an error during
 * serialization, HTTP transport, or deserialization.
 */
class BiltNexoClientException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
