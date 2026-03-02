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
 * Status and contents of a cash handling device managed by the POI System.
 */
public class CashHandlingDevice {
    private boolean cashHandlingOKFlag;
    private CoinsOrBills[] coinsOrBills;
    private String currency;

    /**
     * When true, the cash handling device is operational.
     */
    @JsonProperty("CashHandlingOKFlag")
    public boolean getCashHandlingOKFlag() { return cashHandlingOKFlag; }
    @JsonProperty("CashHandlingOKFlag")
    public void setCashHandlingOKFlag(boolean value) { this.cashHandlingOKFlag = value; }

    @JsonProperty("CoinsOrBills")
    public CoinsOrBills[] getCoinsOrBills() { return coinsOrBills; }
    @JsonProperty("CoinsOrBills")
    public void setCoinsOrBills(CoinsOrBills[] value) { this.coinsOrBills = value; }

    @JsonProperty("Currency")
    public String getCurrency() { return currency; }
    @JsonProperty("Currency")
    public void setCurrency(String value) { this.currency = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private boolean cashHandlingOKFlag;
        private CoinsOrBills[] coinsOrBills;
        private String currency;
        
        private Builder() {}
        
        public Builder cashHandlingOKFlag(boolean cashHandlingOKFlag) {
            this.cashHandlingOKFlag = cashHandlingOKFlag;
            return this;
        }
        
        public Builder coinsOrBills(CoinsOrBills[] coinsOrBills) {
            this.coinsOrBills = coinsOrBills;
            return this;
        }
        
        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }
        
        public CashHandlingDevice build() {
            CashHandlingDevice result = new CashHandlingDevice();
            result.setCashHandlingOKFlag(this.cashHandlingOKFlag);
            result.setCoinsOrBills(this.coinsOrBills);
            result.setCurrency(this.currency);
            return result;
        }
    }
}
