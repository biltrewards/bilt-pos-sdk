/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   This file is auto-generated from the Nexo Sale to POI v3.0 JSON Schema.
 *   Do not modify manually — re-run code generation instead.
 */
package com.bilt.pos.nexo.model;

import com.fasterxml.jackson.annotation.*;

/**
 * Key Encryption Key structure — carries the session key encrypted by the KEK using
 * Triple-DES ECB.
 */
public class Kek {
    private String encryptedKey;
    private KEKIdentifier kekIdentifier;
    private AlgorithmIdentifier keyEncryptionAlgorithm;
    private KEKVersion version;

    /**
     * Base64-encoded session key encrypted by the KEK using Triple-DES in CBC mode.
     */
    @JsonProperty("EncryptedKey")
    public String getEncryptedKey() { return encryptedKey; }
    @JsonProperty("EncryptedKey")
    public void setEncryptedKey(String value) { this.encryptedKey = value; }

    @JsonProperty("KEKIdentifier")
    public KEKIdentifier getKekIdentifier() { return kekIdentifier; }
    @JsonProperty("KEKIdentifier")
    public void setKekIdentifier(KEKIdentifier value) { this.kekIdentifier = value; }

    @JsonProperty("KeyEncryptionAlgorithm")
    public AlgorithmIdentifier getKeyEncryptionAlgorithm() { return keyEncryptionAlgorithm; }
    @JsonProperty("KeyEncryptionAlgorithm")
    public void setKeyEncryptionAlgorithm(AlgorithmIdentifier value) { this.keyEncryptionAlgorithm = value; }

    /**
     * Version of the KEK structure, always 'v4'.
     */
    @JsonProperty("Version")
    public KEKVersion getVersion() { return version; }
    @JsonProperty("Version")
    public void setVersion(KEKVersion value) { this.version = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String encryptedKey;
        private KEKIdentifier kekIdentifier;
        private AlgorithmIdentifier keyEncryptionAlgorithm;
        private KEKVersion version;
        
        private Builder() {}
        
        public Builder encryptedKey(String encryptedKey) {
            this.encryptedKey = encryptedKey;
            return this;
        }
        
        public Builder kekIdentifier(KEKIdentifier kekIdentifier) {
            this.kekIdentifier = kekIdentifier;
            return this;
        }
        
        public Builder keyEncryptionAlgorithm(AlgorithmIdentifier keyEncryptionAlgorithm) {
            this.keyEncryptionAlgorithm = keyEncryptionAlgorithm;
            return this;
        }
        
        public Builder version(KEKVersion version) {
            this.version = version;
            return this;
        }
        
        public Kek build() {
            Kek result = new Kek();
            result.setEncryptedKey(this.encryptedKey);
            result.setKekIdentifier(this.kekIdentifier);
            result.setKeyEncryptionAlgorithm(this.keyEncryptionAlgorithm);
            result.setVersion(this.version);
            return result;
        }
    }
}
