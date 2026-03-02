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
 * CMS DigestedData — used for integrity protection via a digest of the content.
 */
public class DigestedData {
    private String digest;
    private AlgorithmIdentifier digestAlgorithm;
    private EncapsulatedContent encapsulatedContent;

    /**
     * Base64-encoded digest value.
     */
    @JsonProperty("Digest")
    public String getDigest() { return digest; }
    @JsonProperty("Digest")
    public void setDigest(String value) { this.digest = value; }

    @JsonProperty("DigestAlgorithm")
    public AlgorithmIdentifier getDigestAlgorithm() { return digestAlgorithm; }
    @JsonProperty("DigestAlgorithm")
    public void setDigestAlgorithm(AlgorithmIdentifier value) { this.digestAlgorithm = value; }

    @JsonProperty("EncapsulatedContent")
    public EncapsulatedContent getEncapsulatedContent() { return encapsulatedContent; }
    @JsonProperty("EncapsulatedContent")
    public void setEncapsulatedContent(EncapsulatedContent value) { this.encapsulatedContent = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String digest;
        private AlgorithmIdentifier digestAlgorithm;
        private EncapsulatedContent encapsulatedContent;
        
        private Builder() {}
        
        public Builder digest(String digest) {
            this.digest = digest;
            return this;
        }
        
        public Builder digestAlgorithm(AlgorithmIdentifier digestAlgorithm) {
            this.digestAlgorithm = digestAlgorithm;
            return this;
        }
        
        public Builder encapsulatedContent(EncapsulatedContent encapsulatedContent) {
            this.encapsulatedContent = encapsulatedContent;
            return this;
        }
        
        public DigestedData build() {
            DigestedData result = new DigestedData();
            result.setDigest(this.digest);
            result.setDigestAlgorithm(this.digestAlgorithm);
            result.setEncapsulatedContent(this.encapsulatedContent);
            return result;
        }
    }
}
