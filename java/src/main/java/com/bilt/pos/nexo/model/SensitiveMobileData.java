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
 * Sensitive mobile phone subscriber data that may be CMS-protected.
 */
public class SensitiveMobileData {
    private String imei;
    private String imsi;
    private String msisdn;

    /**
     * International Mobile Equipment Identity per ITU-T E.212 — unique number identifying the
     * mobile device.
     */
    @JsonProperty("IMEI")
    public String getImei() { return imei; }
    @JsonProperty("IMEI")
    public void setImei(String value) { this.imei = value; }

    /**
     * International Mobile Subscriber Identity per ITU-T E.212 — contains MCC, MNC, and MSIN.
     */
    @JsonProperty("IMSI")
    public String getImsi() { return imsi; }
    @JsonProperty("IMSI")
    public void setImsi(String value) { this.imsi = value; }

    /**
     * Mobile Subscriber Integrated Service Digital Network number (mobile phone number of the
     * SIM card).
     */
    @JsonProperty("MSISDN")
    public String getMsisdn() { return msisdn; }
    @JsonProperty("MSISDN")
    public void setMsisdn(String value) { this.msisdn = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String imei;
        private String imsi;
        private String msisdn;
        
        private Builder() {}
        
        public Builder imei(String imei) {
            this.imei = imei;
            return this;
        }
        
        public Builder imsi(String imsi) {
            this.imsi = imsi;
            return this;
        }
        
        public Builder msisdn(String msisdn) {
            this.msisdn = msisdn;
            return this;
        }
        
        public SensitiveMobileData build() {
            SensitiveMobileData result = new SensitiveMobileData();
            result.setImei(this.imei);
            result.setImsi(this.imsi);
            result.setMsisdn(this.msisdn);
            return result;
        }
    }
}
