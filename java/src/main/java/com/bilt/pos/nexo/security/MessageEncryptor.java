/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.nexo.security;

import com.bilt.pos.nexo.model.MessageHeader;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts and decrypts Nexo Sale to POI messages using AES-256-CBC with
 * HMAC-SHA256 authentication.
 *
 * <p>Key material is derived from the {@link SecurityKey} passphrase via
 * PBKDF2. Each message uses a fresh random nonce XORed with the derived IV.</p>
 *
 * <p>Instances are thread-safe and should be reused across requests.</p>
 *
 * <pre>{@code
 * SecurityKey key = SecurityKey.builder()
 *     .passphrase("mySecret")
 *     .keyIdentifier("myTerminal")
 *     .keyVersion(0)
 *     .build();
 *
 * MessageEncryptor crypto = new MessageEncryptor(key);
 *
 * SaleToPOISecuredMessage encrypted = crypto.encrypt(jsonPayload, messageHeader);
 * String decrypted = crypto.decrypt(encrypted);
 * }</pre>
 */
public final class MessageEncryptor {

    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int IV_LENGTH = DerivedKey.IV_LENGTH;

    private final SecurityKey securityKey;
    private volatile DerivedKey derivedKey;

    public MessageEncryptor(SecurityKey securityKey) {
        this.securityKey = securityKey;
    }

    /**
     * Encrypt a JSON payload into a secured Nexo message.
     *
     * @param jsonPayload the plaintext JSON to encrypt
     * @param messageHeader the message header (included unencrypted in the result)
     * @return the encrypted message with security trailer
     * @throws EncryptionException if encryption fails
     */
    public SaleToPOISecuredMessage encrypt(String jsonPayload, MessageHeader messageHeader)
            throws EncryptionException {
        try {
            DerivedKey dk = getDerivedKey();
            byte[] plaintext = jsonPayload.getBytes(StandardCharsets.UTF_8);

            byte[] nonce = generateNonce();
            byte[] ciphertext = crypt(plaintext, dk, nonce, Cipher.ENCRYPT_MODE);
            byte[] hmac = hmac(plaintext, dk);

            SecurityTrailer trailer = new SecurityTrailer();
            trailer.setCryptoVersion(securityKey.getCryptoVersion());
            trailer.setKeyIdentifier(securityKey.getKeyIdentifier());
            trailer.setKeyVersion(securityKey.getKeyVersion());
            trailer.setNonce(nonce);
            trailer.setHmac(hmac);

            SaleToPOISecuredMessage message = new SaleToPOISecuredMessage();
            message.setMessageHeader(messageHeader);
            message.setNexoBlob(Base64.getEncoder().encodeToString(ciphertext));
            message.setSecurityTrailer(trailer);

            return message;
        } catch (EncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new EncryptionException("Encryption failed", e);
        }
    }

    /**
     * Decrypt a secured Nexo message back to its JSON payload.
     *
     * @param message the encrypted message
     * @return the decrypted JSON string
     * @throws EncryptionException if decryption or HMAC validation fails
     */
    public String decrypt(SaleToPOISecuredMessage message) throws EncryptionException {
        try {
            DerivedKey dk = getDerivedKey();

            byte[] ciphertext = Base64.getDecoder().decode(message.getNexoBlob());
            byte[] nonce = message.getSecurityTrailer().getNonce();

            byte[] plaintext = crypt(ciphertext, dk, nonce, Cipher.DECRYPT_MODE);

            byte[] computedHmac = hmac(plaintext, dk);
            byte[] receivedHmac = message.getSecurityTrailer().getHmac();
            if (!MessageDigest.isEqual(computedHmac, receivedHmac)) {
                throw new EncryptionException("HMAC validation failed — message may be tampered");
            }

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (EncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new EncryptionException("Decryption failed", e);
        }
    }

    private byte[] crypt(byte[] data, DerivedKey dk, byte[] nonce, int mode) throws Exception {
        byte[] actualIV = new byte[IV_LENGTH];
        byte[] derivedIV = dk.getIv();
        for (int i = 0; i < IV_LENGTH; i++) {
            actualIV[i] = (byte) (derivedIV[i] ^ nonce[i]);
        }

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(mode,
                new SecretKeySpec(dk.getCipherKey(), "AES"),
                new IvParameterSpec(actualIV));
        return cipher.doFinal(data);
    }

    private byte[] hmac(byte[] data, DerivedKey dk) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(dk.getHmacKey(), HMAC_ALGORITHM));
        return mac.doFinal(data);
    }

    private byte[] generateNonce() {
        byte[] nonce = new byte[IV_LENGTH];
        SecureRandom random;
        try {
            random = SecureRandom.getInstance("NativePRNGNonBlocking");
        } catch (Exception e) {
            random = new SecureRandom();
        }
        random.nextBytes(nonce);
        return nonce;
    }

    private DerivedKey getDerivedKey() throws EncryptionException {
        DerivedKey dk = derivedKey;
        if (dk == null) {
            synchronized (this) {
                dk = derivedKey;
                if (dk == null) {
                    dk = DerivedKey.derive(securityKey.getPassphrase());
                    derivedKey = dk;
                }
            }
        }
        return dk;
    }
}
