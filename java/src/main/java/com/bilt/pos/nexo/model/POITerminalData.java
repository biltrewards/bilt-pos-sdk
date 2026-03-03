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
 * Characteristics of the POI Terminal attached to this Sale Terminal.
 */
public class POITerminalData {
    private POICapabilitiesType[] poiCapabilities;
    private SaleProfile poiProfile;
    private String poiSerialNumber;
    private TerminalEnvironmentType terminalEnvironment;

    @JsonProperty("POICapabilities")
    public POICapabilitiesType[] getPoiCapabilities() { return poiCapabilities; }
    @JsonProperty("POICapabilities")
    public void setPoiCapabilities(POICapabilitiesType[] value) { this.poiCapabilities = value; }

    /**
     * Functional profile of the POI Terminal for this session.
     */
    @JsonProperty("POIProfile")
    public SaleProfile getPoiProfile() { return poiProfile; }
    @JsonProperty("POIProfile")
    public void setPoiProfile(SaleProfile value) { this.poiProfile = value; }

    /**
     * Serial number of the POI Terminal, used by the Sale to detect hardware changes.
     */
    @JsonProperty("POISerialNumber")
    public String getPoiSerialNumber() { return poiSerialNumber; }
    @JsonProperty("POISerialNumber")
    public void setPoiSerialNumber(String value) { this.poiSerialNumber = value; }

    @JsonProperty("TerminalEnvironment")
    public TerminalEnvironmentType getTerminalEnvironment() { return terminalEnvironment; }
    @JsonProperty("TerminalEnvironment")
    public void setTerminalEnvironment(TerminalEnvironmentType value) { this.terminalEnvironment = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private POICapabilitiesType[] poiCapabilities;
        private SaleProfile poiProfile;
        private String poiSerialNumber;
        private TerminalEnvironmentType terminalEnvironment;
        
        private Builder() {}
        
        public Builder poiCapabilities(POICapabilitiesType[] poiCapabilities) {
            this.poiCapabilities = poiCapabilities;
            return this;
        }
        
        public Builder poiProfile(SaleProfile poiProfile) {
            this.poiProfile = poiProfile;
            return this;
        }
        
        public Builder poiSerialNumber(String poiSerialNumber) {
            this.poiSerialNumber = poiSerialNumber;
            return this;
        }
        
        public Builder terminalEnvironment(TerminalEnvironmentType terminalEnvironment) {
            this.terminalEnvironment = terminalEnvironment;
            return this;
        }
        
        public POITerminalData build() {
            POITerminalData result = new POITerminalData();
            result.setPoiCapabilities(this.poiCapabilities);
            result.setPoiProfile(this.poiProfile);
            result.setPoiSerialNumber(this.poiSerialNumber);
            result.setTerminalEnvironment(this.terminalEnvironment);
            return result;
        }
    }
}
