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
 * Contains the encrypted data and the algorithm used to encrypt it.
 */
public class EncryptedContent {
    private AlgorithmIdentifier contentEncryptionAlgorithm;
    private EncapsulatedContentContentType contentType;
    private String encryptedData;

    @JsonProperty("ContentEncryptionAlgorithm")
    public AlgorithmIdentifier getContentEncryptionAlgorithm() { return contentEncryptionAlgorithm; }
    @JsonProperty("ContentEncryptionAlgorithm")
    public void setContentEncryptionAlgorithm(AlgorithmIdentifier value) { this.contentEncryptionAlgorithm = value; }

    /**
     * Content type of the encrypted data, always 'id-data'.
     */
    @JsonProperty("ContentType")
    public EncapsulatedContentContentType getContentType() { return contentType; }
    @JsonProperty("ContentType")
    public void setContentType(EncapsulatedContentContentType value) { this.contentType = value; }

    /**
     * Base64-encoded result of the Triple-DES CBC encryption of the padded data.
     */
    @JsonProperty("EncryptedData")
    public String getEncryptedData() { return encryptedData; }
    @JsonProperty("EncryptedData")
    public void setEncryptedData(String value) { this.encryptedData = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private AlgorithmIdentifier contentEncryptionAlgorithm;
        private EncapsulatedContentContentType contentType;
        private String encryptedData;
        
        private Builder() {}
        
        public Builder contentEncryptionAlgorithm(AlgorithmIdentifier contentEncryptionAlgorithm) {
            this.contentEncryptionAlgorithm = contentEncryptionAlgorithm;
            return this;
        }
        
        public Builder contentType(EncapsulatedContentContentType contentType) {
            this.contentType = contentType;
            return this;
        }
        
        public Builder encryptedData(String encryptedData) {
            this.encryptedData = encryptedData;
            return this;
        }
        
        public EncryptedContent build() {
            EncryptedContent result = new EncryptedContent();
            result.setContentEncryptionAlgorithm(this.contentEncryptionAlgorithm);
            result.setContentType(this.contentType);
            result.setEncryptedData(this.encryptedData);
            return result;
        }
    }
}
