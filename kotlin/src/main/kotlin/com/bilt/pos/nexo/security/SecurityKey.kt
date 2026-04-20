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
 * Holds the shared secret and metadata required for Nexo message encryption.
 *
 * The terminal and the Sale System must be provisioned with the same
 * passphrase. The passphrase is used to derive AES and HMAC keys via PBKDF2.
 *
 * ```kotlin
 * val key = SecurityKey(
 *     passphrase = "mySharedSecret",
 *     keyIdentifier = "myTerminal",
 *     keyVersion = 0
 * )
 * ```
 */
data class SecurityKey(
    val passphrase: String,
    val keyIdentifier: String,
    val keyVersion: Int = 0
) {
    init {
        require(passphrase.isNotBlank()) { "passphrase is required" }
        require(keyIdentifier.isNotBlank()) { "keyIdentifier is required" }
    }
}
