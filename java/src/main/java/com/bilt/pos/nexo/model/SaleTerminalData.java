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
 * Information about the Sale Terminal software and hardware characteristics, sent in Login
 * and updated in subsequent messages when devices change.
 */
public class SaleTerminalData {
    private SaleCapabilitiesType[] saleCapabilities;
    private SaleProfile saleProfile;
    private TerminalEnvironmentType terminalEnvironment;
    private String totalsGroupID;

    @JsonProperty("SaleCapabilities")
    public SaleCapabilitiesType[] getSaleCapabilities() { return saleCapabilities; }
    @JsonProperty("SaleCapabilities")
    public void setSaleCapabilities(SaleCapabilitiesType[] value) { this.saleCapabilities = value; }

    @JsonProperty("SaleProfile")
    public SaleProfile getSaleProfile() { return saleProfile; }
    @JsonProperty("SaleProfile")
    public void setSaleProfile(SaleProfile value) { this.saleProfile = value; }

    @JsonProperty("TerminalEnvironment")
    public TerminalEnvironmentType getTerminalEnvironment() { return terminalEnvironment; }
    @JsonProperty("TerminalEnvironment")
    public void setTerminalEnvironment(TerminalEnvironmentType value) { this.terminalEnvironment = value; }

    /**
     * Identification of a group of transactions on a POI Terminal sharing the same Sale
     * features, used for reconciliation grouping.
     */
    @JsonProperty("TotalsGroupID")
    public String getTotalsGroupID() { return totalsGroupID; }
    @JsonProperty("TotalsGroupID")
    public void setTotalsGroupID(String value) { this.totalsGroupID = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private SaleCapabilitiesType[] saleCapabilities;
        private SaleProfile saleProfile;
        private TerminalEnvironmentType terminalEnvironment;
        private String totalsGroupID;
        
        private Builder() {}
        
        public Builder saleCapabilities(SaleCapabilitiesType[] saleCapabilities) {
            this.saleCapabilities = saleCapabilities;
            return this;
        }
        
        public Builder saleProfile(SaleProfile saleProfile) {
            this.saleProfile = saleProfile;
            return this;
        }
        
        public Builder terminalEnvironment(TerminalEnvironmentType terminalEnvironment) {
            this.terminalEnvironment = terminalEnvironment;
            return this;
        }
        
        public Builder totalsGroupID(String totalsGroupID) {
            this.totalsGroupID = totalsGroupID;
            return this;
        }
        
        public SaleTerminalData build() {
            SaleTerminalData result = new SaleTerminalData();
            result.setSaleCapabilities(this.saleCapabilities);
            result.setSaleProfile(this.saleProfile);
            result.setTerminalEnvironment(this.terminalEnvironment);
            result.setTotalsGroupID(this.totalsGroupID);
            return result;
        }
    }
}
