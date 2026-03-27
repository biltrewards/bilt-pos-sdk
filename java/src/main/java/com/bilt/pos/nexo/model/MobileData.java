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
 * Information related to the mobile phone used as a payment instrument for the transaction.
 */
public class MobileData {
    private Geolocation geolocation;
    private String maskedMSISDN;
    private String mobileCountryCode;
    private String mobileNetworkCode;
    private ContentInformationType protectedMobileData;
    private SensitiveMobileData sensitiveMobileData;

    @JsonProperty("Geolocation")
    public Geolocation getGeolocation() { return geolocation; }
    @JsonProperty("Geolocation")
    public void setGeolocation(Geolocation value) { this.geolocation = value; }

    /**
     * Masked MSISDN showing country/national destination code and end digits separated by '*'.
     */
    @JsonProperty("MaskedMSISDN")
    public String getMaskedMSISDN() { return maskedMSISDN; }
    @JsonProperty("MaskedMSISDN")
    public void setMaskedMSISDN(String value) { this.maskedMSISDN = value; }

    /**
     * 3-digit code identifying the country of the mobile operator per ITU-T E.212.
     */
    @JsonProperty("MobileCountryCode")
    public String getMobileCountryCode() { return mobileCountryCode; }
    @JsonProperty("MobileCountryCode")
    public void setMobileCountryCode(String value) { this.mobileCountryCode = value; }

    /**
     * 2–3 digit code identifying the mobile operator within a country per ITU-T E.212.
     */
    @JsonProperty("MobileNetworkCode")
    public String getMobileNetworkCode() { return mobileNetworkCode; }
    @JsonProperty("MobileNetworkCode")
    public void setMobileNetworkCode(String value) { this.mobileNetworkCode = value; }

    /**
     * CMS EnvelopedData containing the encrypted SensitiveMobileData structure.
     */
    @JsonProperty("ProtectedMobileData")
    public ContentInformationType getProtectedMobileData() { return protectedMobileData; }
    @JsonProperty("ProtectedMobileData")
    public void setProtectedMobileData(ContentInformationType value) { this.protectedMobileData = value; }

    @JsonProperty("SensitiveMobileData")
    public SensitiveMobileData getSensitiveMobileData() { return sensitiveMobileData; }
    @JsonProperty("SensitiveMobileData")
    public void setSensitiveMobileData(SensitiveMobileData value) { this.sensitiveMobileData = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private Geolocation geolocation;
        private String maskedMSISDN;
        private String mobileCountryCode;
        private String mobileNetworkCode;
        private ContentInformationType protectedMobileData;
        private SensitiveMobileData sensitiveMobileData;
        
        private Builder() {}
        
        public Builder geolocation(Geolocation geolocation) {
            this.geolocation = geolocation;
            return this;
        }
        
        public Builder maskedMSISDN(String maskedMSISDN) {
            this.maskedMSISDN = maskedMSISDN;
            return this;
        }
        
        public Builder mobileCountryCode(String mobileCountryCode) {
            this.mobileCountryCode = mobileCountryCode;
            return this;
        }
        
        public Builder mobileNetworkCode(String mobileNetworkCode) {
            this.mobileNetworkCode = mobileNetworkCode;
            return this;
        }
        
        public Builder protectedMobileData(ContentInformationType protectedMobileData) {
            this.protectedMobileData = protectedMobileData;
            return this;
        }
        
        public Builder sensitiveMobileData(SensitiveMobileData sensitiveMobileData) {
            this.sensitiveMobileData = sensitiveMobileData;
            return this;
        }
        
        public MobileData build() {
            MobileData result = new MobileData();
            result.setGeolocation(this.geolocation);
            result.setMaskedMSISDN(this.maskedMSISDN);
            result.setMobileCountryCode(this.mobileCountryCode);
            result.setMobileNetworkCode(this.mobileNetworkCode);
            result.setProtectedMobileData(this.protectedMobileData);
            result.setSensitiveMobileData(this.sensitiveMobileData);
            return result;
        }
    }
}
