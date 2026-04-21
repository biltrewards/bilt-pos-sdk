package com.bilt.pos.nexo.security

import com.bilt.pos.nexo.model.MessageCategoryType
import com.bilt.pos.nexo.model.MessageClassType
import com.bilt.pos.nexo.model.MessageHeader
import com.bilt.pos.nexo.model.MessageTypeType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldNotBeEmpty
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MessageEncryptorTest {

    private val json = Json { encodeDefaults = false }

    private lateinit var encryptor: MessageEncryptor
    private lateinit var header: MessageHeader
    private lateinit var headerBytes: ByteArray

    @BeforeEach
    fun setUp() {
        val key = SecurityKey(
            passphrase = "testPassphrase123",
            keyIdentifier = "testTerminal",
            keyVersion = 0
        )
        encryptor = MessageEncryptor(key)

        header = MessageHeader(
            protocolVersion = "3.0",
            messageClass = MessageClassType.Service,
            messageCategory = MessageCategoryType.Payment,
            messageType = MessageTypeType.Request,
            serviceID = "txn-001",
            saleID = "POS-1",
            poiid = "TERM-1"
        )
        headerBytes = json.encodeToString(MessageHeader.serializer(), header)
            .toByteArray(Charsets.UTF_8)
    }

    @Test
    fun `encrypt and decrypt round trip`() {
        val original = """{"MessageHeader":{"ProtocolVersion":"3.0"},"PaymentRequest":{}}"""

        val encrypted = encryptor.encrypt(original, header)
        val decrypted = encryptor.decrypt(encrypted, headerBytes)

        decrypted shouldBe original
    }

    @Test
    fun `encrypted message has expected CMS structure`() {
        val payload = """{"test":"data"}"""

        val message = encryptor.encrypt(payload, header)

        // MessageHeader preserved unencrypted
        message.messageHeader.protocolVersion shouldBe "3.0"

        // EnvelopedData present with encrypted body
        val enveloped = message.envelopedData.shouldNotBeNull()
        enveloped.encryptedContent.encryptedData.shouldNotBeEmpty()

        // Algorithm identifiers
        enveloped.encryptedContent.contentEncryptionAlgorithm.algorithm shouldBe
            MessageEncryptor.ALG_AES256_CBC
        enveloped.encryptedContent.contentEncryptionAlgorithm.parameter.shouldNotBeNull()
            .initialisationVector.shouldNotBeNull()

        // KEK with wrapped session key
        enveloped.kek.kekIdentifier.keyIdentifier shouldBe "testTerminal"
        enveloped.kek.kekIdentifier.keyVersion shouldBe "0"
        enveloped.kek.keyEncryptionAlgorithm.algorithm shouldBe
            MessageEncryptor.ALG_AES256_KEY_WRAP
        enveloped.kek.encryptedKey.shouldNotBeEmpty()

        // SecurityTrailer with AuthenticatedData
        val authData = message.securityTrailer.shouldNotBeNull().authenticatedData.shouldNotBeNull()
        authData.macAlgorithm.algorithm shouldBe MessageEncryptor.ALG_HMAC_SHA256
        authData.mac.length shouldBe 44 // HMAC-SHA256 = 32 bytes = 44 Base64 chars
    }

    @Test
    fun `each encryption produces different ciphertext`() {
        val payload = """{"same":"payload"}"""

        val first = encryptor.encrypt(payload, header)
        val second = encryptor.encrypt(payload, header)

        first.envelopedData!!.encryptedContent.encryptedData shouldNotBe
            second.envelopedData!!.encryptedContent.encryptedData
    }

    @Test
    fun `decrypt fails on tampered ciphertext`() {
        val payload = """{"test":"data"}"""
        val message = encryptor.encrypt(payload, header)

        val ciphertext = java.util.Base64.getDecoder()
            .decode(message.envelopedData!!.encryptedContent.encryptedData)
        ciphertext[0] = (ciphertext[0].toInt() xor 0xFF).toByte()
        val tampered = message.copy(
            envelopedData = message.envelopedData!!.copy(
                encryptedContent = message.envelopedData!!.encryptedContent.copy(
                    encryptedData = java.util.Base64.getEncoder().encodeToString(ciphertext)
                )
            )
        )

        assertThrows<EncryptionException> { encryptor.decrypt(tampered, headerBytes) }
    }

    @Test
    fun `decrypt fails on tampered HMAC`() {
        val payload = """{"test":"data"}"""
        val message = encryptor.encrypt(payload, header)

        val authData = message.securityTrailer!!.authenticatedData!!
        val hmac = java.util.Base64.getDecoder().decode(authData.mac)
        hmac[0] = (hmac[0].toInt() xor 0xFF).toByte()
        val tampered = message.copy(
            securityTrailer = message.securityTrailer!!.copy(
                authenticatedData = authData.copy(
                    mac = java.util.Base64.getEncoder().encodeToString(hmac)
                )
            )
        )

        assertThrows<EncryptionException> { encryptor.decrypt(tampered, headerBytes) }
    }

    @Test
    fun `decrypt fails on tampered header`() {
        val payload = """{"test":"data"}"""
        val message = encryptor.encrypt(payload, header)

        val tamperedHeader = header.copy(serviceID = "tampered")
        val tamperedHeaderBytes = json.encodeToString(MessageHeader.serializer(), tamperedHeader)
            .toByteArray(Charsets.UTF_8)

        assertThrows<EncryptionException> { encryptor.decrypt(message, tamperedHeaderBytes) }
    }

    @Test
    fun `different key cannot decrypt`() {
        val payload = """{"test":"data"}"""
        val message = encryptor.encrypt(payload, header)

        val otherEncryptor = MessageEncryptor(
            SecurityKey(
                passphrase = "differentPassphrase",
                keyIdentifier = "other",
                keyVersion = 0
            )
        )

        assertThrows<EncryptionException> { otherEncryptor.decrypt(message, headerBytes) }
    }

    @Test
    fun `handles large payloads`() {
        val payload = """{"data":"${"x".repeat(10_000)}"}"""

        val message = encryptor.encrypt(payload, header)
        val decrypted = encryptor.decrypt(message, headerBytes)

        decrypted shouldBe payload
    }

    @Test
    fun `SecurityKey requires passphrase`() {
        assertThrows<IllegalArgumentException> {
            SecurityKey(passphrase = "", keyIdentifier = "test")
        }
        assertThrows<IllegalArgumentException> {
            SecurityKey(passphrase = "  ", keyIdentifier = "test")
        }
    }

    @Test
    fun `SecurityKey requires keyIdentifier`() {
        assertThrows<IllegalArgumentException> {
            SecurityKey(passphrase = "test", keyIdentifier = "")
        }
        assertThrows<IllegalArgumentException> {
            SecurityKey(passphrase = "test", keyIdentifier = "  ")
        }
    }
}
