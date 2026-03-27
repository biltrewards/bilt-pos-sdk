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
 * Information identifying the Sale System software product that manages the Sale to POI
 * protocol.
 */
public class SaleSoftware {
    private String applicationName;
    private String certificationCode;
    private String manufacturerID;
    private String softwareVersion;

    /**
     * Name of the Sale System software application.
     */
    @JsonProperty("ApplicationName")
    public String getApplicationName() { return applicationName; }
    @JsonProperty("ApplicationName")
    public void setApplicationName(String value) { this.applicationName = value; }

    /**
     * Certification code of the Sale System software (e.g. checksum or certification number).
     */
    @JsonProperty("CertificationCode")
    public String getCertificationCode() { return certificationCode; }
    @JsonProperty("CertificationCode")
    public void setCertificationCode(String value) { this.certificationCode = value; }

    /**
     * Name of the Sale System software manufacturer.
     */
    @JsonProperty("ManufacturerID")
    public String getManufacturerID() { return manufacturerID; }
    @JsonProperty("ManufacturerID")
    public void setManufacturerID(String value) { this.manufacturerID = value; }

    /**
     * Version of the Sale System software.
     */
    @JsonProperty("SoftwareVersion")
    public String getSoftwareVersion() { return softwareVersion; }
    @JsonProperty("SoftwareVersion")
    public void setSoftwareVersion(String value) { this.softwareVersion = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String applicationName;
        private String certificationCode;
        private String manufacturerID;
        private String softwareVersion;
        
        private Builder() {}
        
        public Builder applicationName(String applicationName) {
            this.applicationName = applicationName;
            return this;
        }
        
        public Builder certificationCode(String certificationCode) {
            this.certificationCode = certificationCode;
            return this;
        }
        
        public Builder manufacturerID(String manufacturerID) {
            this.manufacturerID = manufacturerID;
            return this;
        }
        
        public Builder softwareVersion(String softwareVersion) {
            this.softwareVersion = softwareVersion;
            return this;
        }
        
        public SaleSoftware build() {
            SaleSoftware result = new SaleSoftware();
            result.setApplicationName(this.applicationName);
            result.setCertificationCode(this.certificationCode);
            result.setManufacturerID(this.manufacturerID);
            result.setSoftwareVersion(this.softwareVersion);
            return result;
        }
    }
}
