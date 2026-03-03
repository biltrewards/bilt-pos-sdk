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
 * CMS generic data structure used to protect data by encryption, MAC, or digest.
 *
 * Vendor-specific signature protecting the text to display or print.
 *
 * CMS EnvelopedData containing the encrypted SensitiveCardData structure.
 *
 * CMS EnvelopedData containing the encrypted SensitiveMobileData structure.
 *
 * CMS-encrypted handwritten signature captured on the POI.
 *
 * CMS-protected password. Mandatory for Password command when encryption is used.
 */
public class ContentInformationType {
    private AuthenticatedData authenticatedData;
    private SecurityTrailerContentType contentType;
    private DigestedData digestedData;
    private EnvelopedData envelopedData;

    @JsonProperty("AuthenticatedData")
    public AuthenticatedData getAuthenticatedData() { return authenticatedData; }
    @JsonProperty("AuthenticatedData")
    public void setAuthenticatedData(AuthenticatedData value) { this.authenticatedData = value; }

    /**
     * Identifies the type of CMS protection applied to the content.
     */
    @JsonProperty("ContentType")
    public SecurityTrailerContentType getContentType() { return contentType; }
    @JsonProperty("ContentType")
    public void setContentType(SecurityTrailerContentType value) { this.contentType = value; }

    @JsonProperty("DigestedData")
    public DigestedData getDigestedData() { return digestedData; }
    @JsonProperty("DigestedData")
    public void setDigestedData(DigestedData value) { this.digestedData = value; }

    @JsonProperty("EnvelopedData")
    public EnvelopedData getEnvelopedData() { return envelopedData; }
    @JsonProperty("EnvelopedData")
    public void setEnvelopedData(EnvelopedData value) { this.envelopedData = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private AuthenticatedData authenticatedData;
        private SecurityTrailerContentType contentType;
        private DigestedData digestedData;
        private EnvelopedData envelopedData;
        
        private Builder() {}
        
        public Builder authenticatedData(AuthenticatedData authenticatedData) {
            this.authenticatedData = authenticatedData;
            return this;
        }
        
        public Builder contentType(SecurityTrailerContentType contentType) {
            this.contentType = contentType;
            return this;
        }
        
        public Builder digestedData(DigestedData digestedData) {
            this.digestedData = digestedData;
            return this;
        }
        
        public Builder envelopedData(EnvelopedData envelopedData) {
            this.envelopedData = envelopedData;
            return this;
        }
        
        public ContentInformationType build() {
            ContentInformationType result = new ContentInformationType();
            result.setAuthenticatedData(this.authenticatedData);
            result.setContentType(this.contentType);
            result.setDigestedData(this.digestedData);
            result.setEnvelopedData(this.envelopedData);
            return result;
        }
    }
}
