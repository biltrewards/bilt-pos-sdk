/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.nexo.security

/**
 * Thrown when message encryption or decryption fails.
 */
class EncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)
