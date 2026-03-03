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
 * Identifies the content type of the encapsulated data within a CMS structure.
 */
public class EncapsulatedContent {
    private EncapsulatedContentContentType contentType;
    private String digest;

    /**
     * Content type, always 'id-data'.
     */
    @JsonProperty("ContentType")
    public EncapsulatedContentContentType getContentType() { return contentType; }
    @JsonProperty("ContentType")
    public void setContentType(EncapsulatedContentContentType value) { this.contentType = value; }

    /**
     * Base64-encoded digest value used in DigestedData.
     */
    @JsonProperty("Digest")
    public String getDigest() { return digest; }
    @JsonProperty("Digest")
    public void setDigest(String value) { this.digest = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private EncapsulatedContentContentType contentType;
        private String digest;
        
        private Builder() {}
        
        public Builder contentType(EncapsulatedContentContentType contentType) {
            this.contentType = contentType;
            return this;
        }
        
        public Builder digest(String digest) {
            this.digest = digest;
            return this;
        }
        
        public EncapsulatedContent build() {
            EncapsulatedContent result = new EncapsulatedContent();
            result.setContentType(this.contentType);
            result.setDigest(this.digest);
            return result;
        }
    }
}
