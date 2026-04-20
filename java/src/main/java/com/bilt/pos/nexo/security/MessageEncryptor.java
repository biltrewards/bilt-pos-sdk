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

import com.bilt.pos.nexo.model.AlgorithmIdentifier;
import com.bilt.pos.nexo.model.AuthenticatedData;
import com.bilt.pos.nexo.model.AuthenticatedDataVersion;
import com.bilt.pos.nexo.model.ContentInformationType;
import com.bilt.pos.nexo.model.EncapsulatedContent;
import com.bilt.pos.nexo.model.EncapsulatedContentContentType;
import com.bilt.pos.nexo.model.EncryptedContent;
import com.bilt.pos.nexo.model.EnvelopedData;
import com.bilt.pos.nexo.model.KEKIdentifier;
import com.bilt.pos.nexo.model.KEKVersion;
import com.bilt.pos.nexo.model.Kek;
import com.bilt.pos.nexo.model.MessageHeader;
import com.bilt.pos.nexo.model.Parameter;
import com.bilt.pos.nexo.model.SecurityTrailerContentType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts and decrypts Nexo Sale to POI messages using CMS structures
 * with AES-256-CBC encryption and HMAC-SHA256 authentication.
 *
 * <p>Key material is derived from the {@link SecurityKey} passphrase via
 * PBKDF2. The derived cipher key acts as the KEK (Key Encryption Key) for
 * wrapping per-message session keys. The derived HMAC key is used directly
 * for message authentication.</p>
 *
 * <p>Each message uses a fresh random AES-256 session key, wrapped by the
 * KEK using AES Key Wrap (RFC 3394). The HMAC covers both the serialized
 * message header and the plaintext body.</p>
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
    private static final String KEY_WRAP_ALGORITHM = "AESWrap";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SESSION_KEY_LENGTH = 32;
    private static final int IV_LENGTH = DerivedKey.IV_LENGTH;

    static final String ALG_AES256_CBC = "aes256-cbc";
    static final String ALG_AES256_KEY_WRAP = "aes256-key-wrap";
    static final String ALG_HMAC_SHA256 = "id-hmac-sha256";

    private final SecurityKey securityKey;
    private final ObjectMapper objectMapper;
    private volatile DerivedKey derivedKey;

    public MessageEncryptor(SecurityKey securityKey) {
        this.securityKey = securityKey;
        this.objectMapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * Encrypt a JSON payload into a secured Nexo message using CMS structures.
     *
     * @param jsonPayload the plaintext JSON to encrypt
     * @param messageHeader the message header (included unencrypted in the result)
     * @return the encrypted message with CMS EnvelopedData and SecurityTrailer
     * @throws EncryptionException if encryption fails
     */
    public SaleToPOISecuredMessage encrypt(String jsonPayload, MessageHeader messageHeader)
            throws EncryptionException {
        try {
            DerivedKey dk = getDerivedKey();
            byte[] plaintext = jsonPayload.getBytes(StandardCharsets.UTF_8);

            // Generate per-message session key and IV
            byte[] sessionKeyBytes = generateRandom(SESSION_KEY_LENGTH);
            byte[] nonce = generateRandom(IV_LENGTH);
            byte[] actualIV = xorBytes(dk.getIv(), nonce);

            // Encrypt plaintext with session key
            byte[] ciphertext = encrypt(plaintext, sessionKeyBytes, actualIV);

            // Wrap session key with derived cipher key (KEK)
            byte[] wrappedSessionKey = wrapKey(sessionKeyBytes, dk.getCipherKey());

            // Compute HMAC over header + body
            byte[] headerBytes = objectMapper.writeValueAsBytes(messageHeader);
            byte[] hmacValue = hmac(headerBytes, plaintext, dk.getHmacKey());

            // Build CMS structures
            KEKIdentifier kekId = KEKIdentifier.builder()
                    .keyIdentifier(securityKey.getKeyIdentifier())
                    .keyVersion(String.valueOf(securityKey.getKeyVersion()))
                    .build();

            AlgorithmIdentifier keyWrapAlg = AlgorithmIdentifier.builder()
                    .algorithm(ALG_AES256_KEY_WRAP)
                    .build();

            // EnvelopedData — encrypted body
            EnvelopedData envelopedData = EnvelopedData.builder()
                    .version(AuthenticatedDataVersion.V0)
                    .kek(buildKek(kekId, keyWrapAlg,
                            Base64.getEncoder().encodeToString(wrappedSessionKey)))
                    .encryptedContent(EncryptedContent.builder()
                            .contentType(EncapsulatedContentContentType.ID_DATA)
                            .contentEncryptionAlgorithm(AlgorithmIdentifier.builder()
                                    .algorithm(ALG_AES256_CBC)
                                    .parameter(Parameter.builder()
                                            .initialisationVector(
                                                    Base64.getEncoder().encodeToString(actualIV))
                                            .build())
                                    .build())
                            .encryptedData(Base64.getEncoder().encodeToString(ciphertext))
                            .build())
                    .build();

            // SecurityTrailer — AuthenticatedData with HMAC
            ContentInformationType securityTrailer = ContentInformationType.builder()
                    .contentType(SecurityTrailerContentType.ID_CT_AUTH_DATA)
                    .authenticatedData(AuthenticatedData.builder()
                            .version(AuthenticatedDataVersion.V0)
                            .kek(buildKek(kekId, keyWrapAlg, ""))
                            .macAlgorithm(AlgorithmIdentifier.builder()
                                    .algorithm(ALG_HMAC_SHA256)
                                    .build())
                            .encapsulatedContent(EncapsulatedContent.builder()
                                    .contentType(EncapsulatedContentContentType.ID_DATA)
                                    .build())
                            .mac(Base64.getEncoder().encodeToString(hmacValue))
                            .build())
                    .build();

            SaleToPOISecuredMessage message = new SaleToPOISecuredMessage();
            message.setMessageHeader(messageHeader);
            message.setEnvelopedData(envelopedData);
            message.setSecurityTrailer(securityTrailer);

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
     * <p>The {@code rawHeaderBytes} must be the exact bytes of the
     * {@code MessageHeader} JSON as received on the wire (before deserialization),
     * since the HMAC is computed over the raw header bytes concatenated with the
     * plaintext body. Re-serializing the deserialized Java object could produce
     * different byte output if the sender's JSON library uses a different field
     * ordering, which would cause HMAC verification to fail.</p>
     *
     * @param message the encrypted message
     * @param rawHeaderBytes the raw MessageHeader JSON bytes from the wire
     * @return the decrypted JSON string
     * @throws EncryptionException if decryption or HMAC validation fails
     */
    public String decrypt(SaleToPOISecuredMessage message, byte[] rawHeaderBytes)
            throws EncryptionException {
        try {
            DerivedKey dk = getDerivedKey();
            EnvelopedData envelopedData = message.getEnvelopedData();

            // Unwrap session key
            byte[] wrappedSessionKey = Base64.getDecoder().decode(
                    envelopedData.getKek().getEncryptedKey());
            byte[] sessionKeyBytes = unwrapKey(wrappedSessionKey, dk.getCipherKey());

            // Extract IV and ciphertext
            byte[] iv = Base64.getDecoder().decode(
                    envelopedData.getEncryptedContent()
                            .getContentEncryptionAlgorithm()
                            .getParameter()
                            .getInitialisationVector());
            byte[] ciphertext = Base64.getDecoder().decode(
                    envelopedData.getEncryptedContent().getEncryptedData());

            // Decrypt
            byte[] plaintext = decrypt(ciphertext, sessionKeyBytes, iv);

            // Verify HMAC over raw header bytes + decrypted body
            byte[] computedHmac = hmac(rawHeaderBytes, plaintext, dk.getHmacKey());
            byte[] receivedHmac = Base64.getDecoder().decode(
                    message.getSecurityTrailer().getAuthenticatedData().getMAC());

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

    // -----------------------------------------------------------------------
    // CMS structure helpers
    // -----------------------------------------------------------------------

    private static Kek buildKek(KEKIdentifier kekId, AlgorithmIdentifier keyWrapAlg,
                                String encryptedKey) {
        return Kek.builder()
                .version(KEKVersion.V4)
                .kekIdentifier(kekId)
                .keyEncryptionAlgorithm(keyWrapAlg)
                .encryptedKey(encryptedKey)
                .build();
    }

    // -----------------------------------------------------------------------
    // Crypto primitives
    // -----------------------------------------------------------------------

    private byte[] encrypt(byte[] data, byte[] keyBytes, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(keyBytes, "AES"),
                new IvParameterSpec(iv));
        return cipher.doFinal(data);
    }

    private byte[] decrypt(byte[] data, byte[] keyBytes, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(keyBytes, "AES"),
                new IvParameterSpec(iv));
        return cipher.doFinal(data);
    }

    private byte[] wrapKey(byte[] sessionKey, byte[] kekBytes) throws Exception {
        Cipher cipher = Cipher.getInstance(KEY_WRAP_ALGORITHM);
        cipher.init(Cipher.WRAP_MODE, new SecretKeySpec(kekBytes, "AES"));
        return cipher.wrap(new SecretKeySpec(sessionKey, "AES"));
    }

    private byte[] unwrapKey(byte[] wrappedKey, byte[] kekBytes) throws Exception {
        Cipher cipher = Cipher.getInstance(KEY_WRAP_ALGORITHM);
        cipher.init(Cipher.UNWRAP_MODE, new SecretKeySpec(kekBytes, "AES"));
        SecretKey key = (SecretKey) cipher.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY);
        return key.getEncoded();
    }

    private byte[] hmac(byte[] header, byte[] body, byte[] hmacKey) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(hmacKey, HMAC_ALGORITHM));
        mac.update(header);
        return mac.doFinal(body);
    }

    private static byte[] xorBytes(byte[] a, byte[] b) {
        byte[] result = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = (byte) (a[i] ^ b[i]);
        }
        return result;
    }

    private byte[] generateRandom(int length) {
        byte[] bytes = new byte[length];
        SecureRandom random;
        try {
            random = SecureRandom.getInstance("NativePRNGNonBlocking");
        } catch (Exception e) {
            random = new SecureRandom();
        }
        random.nextBytes(bytes);
        return bytes;
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
