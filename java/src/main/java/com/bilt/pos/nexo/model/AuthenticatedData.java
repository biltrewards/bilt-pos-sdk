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
 * CMS AuthenticatedData — protects the whole message (header and body) with a Retail
 * CBC-MAC using SHA-256.
 */
public class AuthenticatedData {
    private EncapsulatedContent encapsulatedContent;
    private Kek kek;
    private String mac;
    private AlgorithmIdentifier macAlgorithm;
    private AuthenticatedDataVersion version;

    @JsonProperty("EncapsulatedContent")
    public EncapsulatedContent getEncapsulatedContent() { return encapsulatedContent; }
    @JsonProperty("EncapsulatedContent")
    public void setEncapsulatedContent(EncapsulatedContent value) { this.encapsulatedContent = value; }

    @JsonProperty("KEK")
    public Kek getKek() { return kek; }
    @JsonProperty("KEK")
    public void setKek(Kek value) { this.kek = value; }

    /**
     * Base64-encoded 8-byte Retail CBC-MAC computed over the SHA-256 digest of the concatenated
     * MessageHeader and MessageBody.
     */
    @JsonProperty("MAC")
    public String getMAC() { return mac; }
    @JsonProperty("MAC")
    public void setMAC(String value) { this.mac = value; }

    @JsonProperty("MACAlgorithm")
    public AlgorithmIdentifier getMACAlgorithm() { return macAlgorithm; }
    @JsonProperty("MACAlgorithm")
    public void setMACAlgorithm(AlgorithmIdentifier value) { this.macAlgorithm = value; }

    /**
     * Version of the AuthenticatedData structure, always 'v0'.
     */
    @JsonProperty("Version")
    public AuthenticatedDataVersion getVersion() { return version; }
    @JsonProperty("Version")
    public void setVersion(AuthenticatedDataVersion value) { this.version = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private EncapsulatedContent encapsulatedContent;
        private Kek kek;
        private String mac;
        private AlgorithmIdentifier macAlgorithm;
        private AuthenticatedDataVersion version;
        
        private Builder() {}
        
        public Builder encapsulatedContent(EncapsulatedContent encapsulatedContent) {
            this.encapsulatedContent = encapsulatedContent;
            return this;
        }
        
        public Builder kek(Kek kek) {
            this.kek = kek;
            return this;
        }
        
        public Builder mac(String mac) {
            this.mac = mac;
            return this;
        }
        
        public Builder macAlgorithm(AlgorithmIdentifier macAlgorithm) {
            this.macAlgorithm = macAlgorithm;
            return this;
        }
        
        public Builder version(AuthenticatedDataVersion version) {
            this.version = version;
            return this;
        }
        
        public AuthenticatedData build() {
            AuthenticatedData result = new AuthenticatedData();
            result.setEncapsulatedContent(this.encapsulatedContent);
            result.setKek(this.kek);
            result.setMAC(this.mac);
            result.setMACAlgorithm(this.macAlgorithm);
            result.setVersion(this.version);
            return result;
        }
    }
}
