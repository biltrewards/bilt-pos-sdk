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

/**
 * Container for key material derived from a passphrase via PBKDF2.
 *
 * <p>The 80 bytes produced by PBKDF2 are split into:</p>
 * <ul>
 *   <li>32-byte HMAC key (HmacSHA256)</li>
 *   <li>32-byte cipher key (AES-256)</li>
 *   <li>16-byte initialization vector (XORed with a per-message nonce)</li>
 * </ul>
 */
final class DerivedKey {

    static final int HMAC_KEY_LENGTH = 32;
    static final int CIPHER_KEY_LENGTH = 32;
    static final int IV_LENGTH = 16;

    private final byte[] hmacKey;
    private final byte[] cipherKey;
    private final byte[] iv;

    DerivedKey(byte[] hmacKey, byte[] cipherKey, byte[] iv) {
        this.hmacKey = hmacKey;
        this.cipherKey = cipherKey;
        this.iv = iv;
    }

    byte[] getHmacKey() { return hmacKey; }
    byte[] getCipherKey() { return cipherKey; }
    byte[] getIv() { return iv; }

    /**
     * Derive key material from a passphrase using PBKDF2WithHmacSHA1.
     */
    static DerivedKey derive(String passphrase) throws EncryptionException {
        try {
            byte[] salt = "BiltNexoSalt".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            int iterations = 4000;
            int totalLength = HMAC_KEY_LENGTH + CIPHER_KEY_LENGTH + IV_LENGTH;

            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                    passphrase.toCharArray(), salt, iterations, totalLength * 8);
            javax.crypto.SecretKeyFactory skf =
                    javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            byte[] raw = skf.generateSecret(spec).getEncoded();

            byte[] hmacKey = new byte[HMAC_KEY_LENGTH];
            byte[] cipherKey = new byte[CIPHER_KEY_LENGTH];
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(raw, 0, hmacKey, 0, HMAC_KEY_LENGTH);
            System.arraycopy(raw, HMAC_KEY_LENGTH, cipherKey, 0, CIPHER_KEY_LENGTH);
            System.arraycopy(raw, HMAC_KEY_LENGTH + CIPHER_KEY_LENGTH, iv, 0, IV_LENGTH);

            return new DerivedKey(hmacKey, cipherKey, iv);
        } catch (Exception e) {
            throw new EncryptionException("Failed to derive key material", e);
        }
    }
}
