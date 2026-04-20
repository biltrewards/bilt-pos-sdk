package com.bilt.pos.nexo.security;

import com.bilt.pos.nexo.model.ContentInformationType;
import com.bilt.pos.nexo.model.EnvelopedData;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.MessageClassType;
import com.bilt.pos.nexo.model.MessageHeader;
import com.bilt.pos.nexo.model.MessageTypeType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class MessageEncryptorTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private MessageEncryptor encryptor;
    private MessageHeader header;
    private byte[] headerBytes;

    @BeforeEach
    void setUp() throws Exception {
        SecurityKey key = SecurityKey.builder()
                .passphrase("testPassphrase123")
                .keyIdentifier("testTerminal")
                .keyVersion(0)
                .build();
        encryptor = new MessageEncryptor(key);

        header = MessageHeader.builder()
                .protocolVersion("3.0")
                .messageClass(MessageClassType.SERVICE)
                .messageCategory(MessageCategoryType.PAYMENT)
                .messageType(MessageTypeType.REQUEST)
                .serviceID("txn-001")
                .saleID("POS-1")
                .poiid("TERM-1")
                .build();
        headerBytes = mapper.writeValueAsBytes(header);
    }

    @Test
    void encryptAndDecryptRoundTrip() throws Exception {
        String original = "{\"MessageHeader\":{\"ProtocolVersion\":\"3.0\"},\"PaymentRequest\":{}}";

        SaleToPOISecuredMessage encrypted = encryptor.encrypt(original, header);
        String decrypted = encryptor.decrypt(encrypted, headerBytes);

        assertEquals(original, decrypted);
    }

    @Test
    void encryptedMessageHasExpectedCmsStructure() throws Exception {
        String payload = "{\"test\":\"data\"}";

        SaleToPOISecuredMessage message = encryptor.encrypt(payload, header);

        // MessageHeader preserved unencrypted
        assertNotNull(message.getMessageHeader());
        assertEquals("3.0", message.getMessageHeader().getProtocolVersion());

        // EnvelopedData present with encrypted body
        EnvelopedData enveloped = message.getEnvelopedData();
        assertNotNull(enveloped);
        assertNotNull(enveloped.getEncryptedContent());
        assertNotNull(enveloped.getEncryptedContent().getEncryptedData());
        assertFalse(enveloped.getEncryptedContent().getEncryptedData().isEmpty());

        // Algorithm identifiers
        assertEquals(MessageEncryptor.ALG_AES256_CBC,
                enveloped.getEncryptedContent().getContentEncryptionAlgorithm().getAlgorithm());
        assertNotNull(enveloped.getEncryptedContent()
                .getContentEncryptionAlgorithm().getParameter().getInitialisationVector());

        // KEK with wrapped session key
        assertNotNull(enveloped.getKek());
        assertEquals("testTerminal", enveloped.getKek().getKekIdentifier().getKeyIdentifier());
        assertEquals("0", enveloped.getKek().getKekIdentifier().getKeyVersion());
        assertEquals(MessageEncryptor.ALG_AES256_KEY_WRAP,
                enveloped.getKek().getKeyEncryptionAlgorithm().getAlgorithm());
        assertNotNull(enveloped.getKek().getEncryptedKey());
        // AES Key Wrap of 32 bytes = 40 bytes = 56 Base64 chars (with padding)
        assertFalse(enveloped.getKek().getEncryptedKey().isEmpty());

        // SecurityTrailer with AuthenticatedData
        ContentInformationType trailer = message.getSecurityTrailer();
        assertNotNull(trailer);
        assertNotNull(trailer.getAuthenticatedData());
        assertEquals(MessageEncryptor.ALG_HMAC_SHA256,
                trailer.getAuthenticatedData().getMACAlgorithm().getAlgorithm());
        assertNotNull(trailer.getAuthenticatedData().getMAC());
        // HMAC-SHA256 = 32 bytes = 44 Base64 chars
        assertEquals(44, trailer.getAuthenticatedData().getMAC().length());
    }

    @Test
    void eachEncryptionProducesDifferentCiphertext() throws Exception {
        String payload = "{\"same\":\"payload\"}";

        SaleToPOISecuredMessage first = encryptor.encrypt(payload, header);
        SaleToPOISecuredMessage second = encryptor.encrypt(payload, header);

        assertNotEquals(
                first.getEnvelopedData().getEncryptedContent().getEncryptedData(),
                second.getEnvelopedData().getEncryptedContent().getEncryptedData());
    }

    @Test
    void decryptFailsOnTamperedCiphertext() throws Exception {
        String payload = "{\"test\":\"data\"}";
        SaleToPOISecuredMessage message = encryptor.encrypt(payload, header);

        // Tamper with the encrypted data
        byte[] ciphertext = Base64.getDecoder().decode(
                message.getEnvelopedData().getEncryptedContent().getEncryptedData());
        ciphertext[0] ^= 0xFF;
        message.getEnvelopedData().getEncryptedContent()
                .setEncryptedData(Base64.getEncoder().encodeToString(ciphertext));

        assertThrows(EncryptionException.class,
                () -> encryptor.decrypt(message, headerBytes));
    }

    @Test
    void decryptFailsOnTamperedHmac() throws Exception {
        String payload = "{\"test\":\"data\"}";
        SaleToPOISecuredMessage message = encryptor.encrypt(payload, header);

        // Tamper with HMAC
        byte[] hmac = Base64.getDecoder().decode(
                message.getSecurityTrailer().getAuthenticatedData().getMAC());
        hmac[0] ^= 0xFF;
        message.getSecurityTrailer().getAuthenticatedData()
                .setMAC(Base64.getEncoder().encodeToString(hmac));

        assertThrows(EncryptionException.class,
                () -> encryptor.decrypt(message, headerBytes));
    }

    @Test
    void decryptFailsOnTamperedHeader() throws Exception {
        String payload = "{\"test\":\"data\"}";
        SaleToPOISecuredMessage message = encryptor.encrypt(payload, header);

        // Pass tampered header bytes — HMAC should catch this
        MessageHeader tampered = MessageHeader.builder()
                .protocolVersion("3.0")
                .messageClass(MessageClassType.SERVICE)
                .messageCategory(MessageCategoryType.PAYMENT)
                .messageType(MessageTypeType.REQUEST)
                .serviceID("tampered")
                .saleID("POS-1")
                .poiid("TERM-1")
                .build();
        byte[] tamperedHeaderBytes = mapper.writeValueAsBytes(tampered);

        assertThrows(EncryptionException.class,
                () -> encryptor.decrypt(message, tamperedHeaderBytes));
    }

    @Test
    void differentKeyCannotDecrypt() throws Exception {
        String payload = "{\"test\":\"data\"}";
        SaleToPOISecuredMessage message = encryptor.encrypt(payload, header);

        SecurityKey otherKey = SecurityKey.builder()
                .passphrase("differentPassphrase")
                .keyIdentifier("other")
                .keyVersion(0)
                .build();
        MessageEncryptor otherEncryptor = new MessageEncryptor(otherKey);

        assertThrows(EncryptionException.class,
                () -> otherEncryptor.decrypt(message, headerBytes));
    }

    @Test
    void handlesLargePayloads() throws Exception {
        StringBuilder sb = new StringBuilder("{\"data\":\"");
        for (int i = 0; i < 10_000; i++) sb.append('x');
        sb.append("\"}");
        String payload = sb.toString();

        SaleToPOISecuredMessage message = encryptor.encrypt(payload, header);
        String decrypted = encryptor.decrypt(message, headerBytes);

        assertEquals(payload, decrypted);
    }

    @Test
    void securityKeyRequiresPassphrase() {
        assertThrows(IllegalStateException.class, () ->
                SecurityKey.builder()
                        .keyIdentifier("test")
                        .build());
    }

    @Test
    void securityKeyRequiresKeyIdentifier() {
        assertThrows(IllegalStateException.class, () ->
                SecurityKey.builder()
                        .passphrase("test")
                        .build());
    }
}
