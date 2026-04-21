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

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Container for key material derived from a passphrase via PBKDF2.
 *
 * The 80 bytes produced by PBKDF2 are split into:
 * - 32-byte HMAC key (HmacSHA256)
 * - 32-byte cipher key (AES-256, used as the KEK for wrapping session keys)
 * - 16-byte initialization vector (XORed with a per-message nonce)
 */
internal class DerivedKey(
    val hmacKey: ByteArray,
    val cipherKey: ByteArray,
    val iv: ByteArray
) {
    companion object {
        const val HMAC_KEY_LENGTH = 32
        const val CIPHER_KEY_LENGTH = 32
        const val IV_LENGTH = 16

        private val SALT = "BiltNexoSalt".toByteArray(Charsets.UTF_8)
        private const val ITERATIONS = 4000
        private const val TOTAL_LENGTH = HMAC_KEY_LENGTH + CIPHER_KEY_LENGTH + IV_LENGTH

        fun derive(passphrase: String): DerivedKey {
            try {
                val spec = PBEKeySpec(passphrase.toCharArray(), SALT, ITERATIONS, TOTAL_LENGTH * 8)
                val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                val raw = skf.generateSecret(spec).encoded

                val hmacKey = raw.copyOfRange(0, HMAC_KEY_LENGTH)
                val cipherKey = raw.copyOfRange(HMAC_KEY_LENGTH, HMAC_KEY_LENGTH + CIPHER_KEY_LENGTH)
                val iv = raw.copyOfRange(HMAC_KEY_LENGTH + CIPHER_KEY_LENGTH, TOTAL_LENGTH)

                return DerivedKey(hmacKey, cipherKey, iv)
            } catch (e: Exception) {
                throw EncryptionException("Failed to derive key material", e)
            }
        }
    }
}
