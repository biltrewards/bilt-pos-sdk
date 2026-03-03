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
 * CMS EnvelopedData — protects sensitive data (e.g. card data) by encryption using a
 * session key transported by a KEK.
 */
public class EnvelopedData {
    private EncryptedContent encryptedContent;
    private Kek kek;
    private AuthenticatedDataVersion version;

    @JsonProperty("EncryptedContent")
    public EncryptedContent getEncryptedContent() { return encryptedContent; }
    @JsonProperty("EncryptedContent")
    public void setEncryptedContent(EncryptedContent value) { this.encryptedContent = value; }

    @JsonProperty("KEK")
    public Kek getKek() { return kek; }
    @JsonProperty("KEK")
    public void setKek(Kek value) { this.kek = value; }

    /**
     * Version of the EnvelopedData structure, always 'v0'.
     */
    @JsonProperty("Version")
    public AuthenticatedDataVersion getVersion() { return version; }
    @JsonProperty("Version")
    public void setVersion(AuthenticatedDataVersion value) { this.version = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private EncryptedContent encryptedContent;
        private Kek kek;
        private AuthenticatedDataVersion version;
        
        private Builder() {}
        
        public Builder encryptedContent(EncryptedContent encryptedContent) {
            this.encryptedContent = encryptedContent;
            return this;
        }
        
        public Builder kek(Kek kek) {
            this.kek = kek;
            return this;
        }
        
        public Builder version(AuthenticatedDataVersion version) {
            this.version = version;
            return this;
        }
        
        public EnvelopedData build() {
            EnvelopedData result = new EnvelopedData();
            result.setEncryptedContent(this.encryptedContent);
            result.setKek(this.kek);
            result.setVersion(this.version);
            return result;
        }
    }
}
