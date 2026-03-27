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
 * Identifies the Key Encryption Key (KEK) used to encrypt the session key.
 */
public class KEKIdentifier {
    private String keyIdentifier;
    private String keyVersion;

    /**
     * Name of the KEK. Contains the suffix 'MACKey' for MAC keys or 'DATKey' for data
     * encryption keys.
     */
    @JsonProperty("KeyIdentifier")
    public String getKeyIdentifier() { return keyIdentifier; }
    @JsonProperty("KeyIdentifier")
    public void setKeyIdentifier(String value) { this.keyIdentifier = value; }

    /**
     * Version of the KEK, typically representing the key creation date.
     */
    @JsonProperty("KeyVersion")
    public String getKeyVersion() { return keyVersion; }
    @JsonProperty("KeyVersion")
    public void setKeyVersion(String value) { this.keyVersion = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String keyIdentifier;
        private String keyVersion;
        
        private Builder() {}
        
        public Builder keyIdentifier(String keyIdentifier) {
            this.keyIdentifier = keyIdentifier;
            return this;
        }
        
        public Builder keyVersion(String keyVersion) {
            this.keyVersion = keyVersion;
            return this;
        }
        
        public KEKIdentifier build() {
            KEKIdentifier result = new KEKIdentifier();
            result.setKeyIdentifier(this.keyIdentifier);
            result.setKeyVersion(this.keyVersion);
            return result;
        }
    }
}
