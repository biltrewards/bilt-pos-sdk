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

import com.bilt.pos.nexo.model.AlgorithmIdentifier
import com.bilt.pos.nexo.model.AuthenticatedData
import com.bilt.pos.nexo.model.AuthenticatedDataVersion
import com.bilt.pos.nexo.model.ContentInformationType
import com.bilt.pos.nexo.model.EncapsulatedContent
import com.bilt.pos.nexo.model.EncapsulatedContentContentType
import com.bilt.pos.nexo.model.EncryptedContent
import com.bilt.pos.nexo.model.EnvelopedData
import com.bilt.pos.nexo.model.KEKIdentifier
import com.bilt.pos.nexo.model.KEKVersion
import com.bilt.pos.nexo.model.Kek
import com.bilt.pos.nexo.model.MessageHeader
import com.bilt.pos.nexo.model.Parameter
import com.bilt.pos.nexo.model.SecurityTrailerContentType
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts and decrypts Nexo Sale to POI messages using CMS structures
 * with AES-256-CBC encryption and HMAC-SHA256 authentication.
 *
 * Key material is derived from the [SecurityKey] passphrase via PBKDF2.
 * The derived cipher key acts as the KEK (Key Encryption Key) for wrapping
 * per-message session keys. The derived HMAC key is used directly for
 * message authentication.
 *
 * Each message uses a fresh random AES-256 session key, wrapped by the
 * KEK using AES Key Wrap (RFC 3394). The HMAC covers both the serialized
 * message header and the plaintext body.
 *
 * Instances are thread-safe and should be reused across requests.
 *
 * ```kotlin
 * val key = SecurityKey(
 *     passphrase = "mySecret",
 *     keyIdentifier = "myTerminal",
 *     keyVersion = 0
 * )
 * val crypto = MessageEncryptor(key)
 *
 * val encrypted = crypto.encrypt(jsonPayload, messageHeader)
 * val decrypted = crypto.decrypt(encrypted, rawHeaderBytes)
 * ```
 */
class MessageEncryptor(private val securityKey: SecurityKey) {

    private val json = Json { encodeDefaults = false }

    @Volatile
    private var derivedKey: DerivedKey? = null

    /**
     * Encrypt a JSON payload into a secured Nexo message using CMS structures.
     *
     * @param jsonPayload the plaintext JSON to encrypt
     * @param messageHeader the message header (included unencrypted in the result)
     * @return the encrypted message with CMS EnvelopedData and SecurityTrailer
     * @throws EncryptionException if encryption fails
     */
    fun encrypt(jsonPayload: String, messageHeader: MessageHeader): SaleToPOISecuredMessage {
        try {
            val dk = getDerivedKey()
            val plaintext = jsonPayload.toByteArray(Charsets.UTF_8)

            // Generate per-message session key and IV
            val sessionKeyBytes = generateRandom(SESSION_KEY_LENGTH)
            val nonce = generateRandom(IV_LENGTH)
            val actualIV = xorBytes(dk.iv, nonce)

            // Encrypt plaintext with session key
            val ciphertext = encrypt(plaintext, sessionKeyBytes, actualIV)

            // Wrap session key with derived cipher key (KEK)
            val wrappedSessionKey = wrapKey(sessionKeyBytes, dk.cipherKey)

            // Compute HMAC over header + body
            val headerBytes = json.encodeToString(MessageHeader.serializer(), messageHeader)
                .toByteArray(Charsets.UTF_8)
            val hmacValue = hmac(headerBytes, plaintext, dk.hmacKey)

            // Build CMS structures
            val kekId = KEKIdentifier(
                keyIdentifier = securityKey.keyIdentifier,
                keyVersion = securityKey.keyVersion.toString()
            )
            val keyWrapAlg = AlgorithmIdentifier(algorithm = ALG_AES256_KEY_WRAP)

            val envelopedData = EnvelopedData(
                version = AuthenticatedDataVersion.V0,
                kek = buildKek(kekId, keyWrapAlg, encode64(wrappedSessionKey)),
                encryptedContent = EncryptedContent(
                    contentType = EncapsulatedContentContentType.IDData,
                    contentEncryptionAlgorithm = AlgorithmIdentifier(
                        algorithm = ALG_AES256_CBC,
                        parameter = Parameter(initialisationVector = encode64(actualIV))
                    ),
                    encryptedData = encode64(ciphertext)
                )
            )

            val securityTrailer = ContentInformationType(
                contentType = SecurityTrailerContentType.IDCTAuthData,
                authenticatedData = AuthenticatedData(
                    version = AuthenticatedDataVersion.V0,
                    kek = buildKek(kekId, keyWrapAlg, ""),
                    macAlgorithm = AlgorithmIdentifier(algorithm = ALG_HMAC_SHA256),
                    encapsulatedContent = EncapsulatedContent(
                        contentType = EncapsulatedContentContentType.IDData
                    ),
                    mac = encode64(hmacValue)
                )
            )

            return SaleToPOISecuredMessage(
                messageHeader = messageHeader,
                envelopedData = envelopedData,
                securityTrailer = securityTrailer
            )
        } catch (e: EncryptionException) {
            throw e
        } catch (e: Exception) {
            throw EncryptionException("Encryption failed", e)
        }
    }

    /**
     * Decrypt a secured Nexo message back to its JSON payload.
     *
     * The [rawHeaderBytes] must be the exact bytes of the `MessageHeader` JSON
     * as received on the wire (before deserialization), since the HMAC is computed
     * over the raw header bytes concatenated with the plaintext body. Re-serializing
     * the deserialized object could produce different byte output if the sender's
     * JSON library uses a different field ordering, which would cause HMAC
     * verification to fail.
     *
     * @param message the encrypted message
     * @param rawHeaderBytes the raw MessageHeader JSON bytes from the wire
     * @return the decrypted JSON string
     * @throws EncryptionException if decryption or HMAC validation fails
     */
    fun decrypt(message: SaleToPOISecuredMessage, rawHeaderBytes: ByteArray): String {
        try {
            val dk = getDerivedKey()
            val envelopedData = message.envelopedData

            // Unwrap session key
            val wrappedSessionKey = decode64(envelopedData.kek.encryptedKey)
            val sessionKeyBytes = unwrapKey(wrappedSessionKey, dk.cipherKey)

            // Extract IV and ciphertext
            val iv = decode64(
                envelopedData.encryptedContent
                    .contentEncryptionAlgorithm.parameter!!.initialisationVector!!
            )
            val ciphertext = decode64(envelopedData.encryptedContent.encryptedData)

            // Decrypt
            val plaintext = decrypt(ciphertext, sessionKeyBytes, iv)

            // Verify HMAC over raw header bytes + decrypted body
            val computedHmac = hmac(rawHeaderBytes, plaintext, dk.hmacKey)
            val receivedHmac = decode64(message.securityTrailer.authenticatedData!!.mac)

            if (!MessageDigest.isEqual(computedHmac, receivedHmac)) {
                throw EncryptionException("HMAC validation failed — message may be tampered")
            }

            return plaintext.toString(Charsets.UTF_8)
        } catch (e: EncryptionException) {
            throw e
        } catch (e: Exception) {
            throw EncryptionException("Decryption failed", e)
        }
    }

    // -----------------------------------------------------------------------
    // CMS structure helpers
    // -----------------------------------------------------------------------

    private fun buildKek(
        kekId: KEKIdentifier,
        keyWrapAlg: AlgorithmIdentifier,
        encryptedKey: String
    ) = Kek(
        version = KEKVersion.V4,
        kekIdentifier = kekId,
        keyEncryptionAlgorithm = keyWrapAlg,
        encryptedKey = encryptedKey
    )

    // -----------------------------------------------------------------------
    // Crypto primitives
    // -----------------------------------------------------------------------

    private fun encrypt(data: ByteArray, keyBytes: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    private fun decrypt(data: ByteArray, keyBytes: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    private fun wrapKey(sessionKey: ByteArray, kekBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(KEY_WRAP_ALGORITHM)
        cipher.init(Cipher.WRAP_MODE, SecretKeySpec(kekBytes, "AES"))
        return cipher.wrap(SecretKeySpec(sessionKey, "AES"))
    }

    private fun unwrapKey(wrappedKey: ByteArray, kekBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(KEY_WRAP_ALGORITHM)
        cipher.init(Cipher.UNWRAP_MODE, SecretKeySpec(kekBytes, "AES"))
        val key: SecretKey = cipher.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY) as SecretKey
        return key.encoded
    }

    private fun hmac(header: ByteArray, body: ByteArray, hmacKey: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(hmacKey, HMAC_ALGORITHM))
        mac.update(header)
        return mac.doFinal(body)
    }

    private fun getDerivedKey(): DerivedKey {
        derivedKey?.let { return it }
        synchronized(this) {
            derivedKey?.let { return it }
            return DerivedKey.derive(securityKey.passphrase).also { derivedKey = it }
        }
    }

    companion object {
        private const val CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding"
        private const val KEY_WRAP_ALGORITHM = "AESWrap"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val SESSION_KEY_LENGTH = 32
        private val IV_LENGTH = DerivedKey.IV_LENGTH

        internal const val ALG_AES256_CBC = "aes256-cbc"
        internal const val ALG_AES256_KEY_WRAP = "aes256-key-wrap"
        internal const val ALG_HMAC_SHA256 = "id-hmac-sha256"

        private val secureRandom: SecureRandom = try {
            SecureRandom.getInstance("NativePRNGNonBlocking")
        } catch (_: Exception) {
            SecureRandom()
        }

        private fun generateRandom(length: Int): ByteArray =
            ByteArray(length).also { secureRandom.nextBytes(it) }

        private fun xorBytes(a: ByteArray, b: ByteArray): ByteArray =
            ByteArray(a.size) { i -> (a[i].toInt() xor b[i].toInt()).toByte() }

        private fun encode64(data: ByteArray): String =
            java.util.Base64.getEncoder().encodeToString(data)

        private fun decode64(data: String): ByteArray =
            java.util.Base64.getDecoder().decode(data)
    }
}
