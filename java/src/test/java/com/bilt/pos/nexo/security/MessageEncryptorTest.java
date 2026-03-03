package com.bilt.pos.nexo.security;

import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.MessageClassType;
import com.bilt.pos.nexo.model.MessageHeader;
import com.bilt.pos.nexo.model.MessageTypeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageEncryptorTest {

    private MessageEncryptor encryptor;
    private MessageHeader header;

    @BeforeEach
    void setUp() {
        SecurityKey key = SecurityKey.builder()
                .passphrase("testPassphrase123")
                .keyIdentifier("testTerminal")
                .keyVersion(0)
                .cryptoVersion(1)
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
    }

    @Test
    void encryptAndDecryptRoundTrip() throws Exception {
        String original = "{\"MessageHeader\":{\"ProtocolVersion\":\"3.0\"},\"PaymentRequest\":{}}";

        SaleToPOISecuredMessage encrypted = encryptor.encrypt(original, header);
        String decrypted = encryptor.decrypt(encrypted);

        assertEquals(original, decrypted);
    }

    @Test
    void encryptedMessageHasExpectedStructure() throws Exception {
        String payload = "{\"test\":\"data\"}";

        SaleToPOISecuredMessage message = encryptor.encrypt(payload, header);

        assertNotNull(message.getMessageHeader());
        assertEquals("3.0", message.getMessageHeader().getProtocolVersion());

        assertNotNull(message.getNexoBlob());
        assertFalse(message.getNexoBlob().isEmpty());
        assertFalse(message.getNexoBlob().contains("test")); // not plaintext

        SecurityTrailer trailer = message.getSecurityTrailer();
        assertNotNull(trailer);
        assertEquals(1, trailer.getCryptoVersion());
        assertEquals("testTerminal", trailer.getKeyIdentifier());
        assertEquals(0, trailer.getKeyVersion());
        assertNotNull(trailer.getNonce());
        assertEquals(16, trailer.getNonce().length);
        assertNotNull(trailer.getHmac());
        assertEquals(32, trailer.getHmac().length);
    }

    @Test
    void eachEncryptionProducesDifferentCiphertext() throws Exception {
        String payload = "{\"same\":\"payload\"}";

        SaleToPOISecuredMessage first = encryptor.encrypt(payload, header);
        SaleToPOISecuredMessage second = encryptor.encrypt(payload, header);

        assertNotEquals(first.getNexoBlob(), second.getNexoBlob());
    }

    @Test
    void decryptFailsOnTamperedCiphertext() throws Exception {
        String payload = "{\"test\":\"data\"}";
        SaleToPOISecuredMessage message = encryptor.encrypt(payload, header);

        // Tamper with the blob
        byte[] blob = java.util.Base64.getDecoder().decode(message.getNexoBlob());
        blob[0] ^= 0xFF;
        message.setNexoBlob(java.util.Base64.getEncoder().encodeToString(blob));

        assertThrows(EncryptionException.class, () -> encryptor.decrypt(message));
    }

    @Test
    void decryptFailsOnTamperedHmac() throws Exception {
        String payload = "{\"test\":\"data\"}";
        SaleToPOISecuredMessage message = encryptor.encrypt(payload, header);

        // Tamper with HMAC
        byte[] hmac = message.getSecurityTrailer().getHmac();
        hmac[0] ^= 0xFF;

        assertThrows(EncryptionException.class, () -> encryptor.decrypt(message));
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

        assertThrows(EncryptionException.class, () -> otherEncryptor.decrypt(message));
    }

    @Test
    void handlesLargePayloads() throws Exception {
        StringBuilder sb = new StringBuilder("{\"data\":\"");
        for (int i = 0; i < 10_000; i++) sb.append('x');
        sb.append("\"}");
        String payload = sb.toString();

        SaleToPOISecuredMessage message = encryptor.encrypt(payload, header);
        String decrypted = encryptor.decrypt(message);

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
