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
 * Amount associated with a loyalty transaction, expressed in points or a monetary value.
 */
public class LoyaltyAmount {
    private double amountValue;
    private String currency;
    private LoyaltyUnitEnum loyaltyUnit;

    @JsonProperty("AmountValue")
    public double getAmountValue() { return amountValue; }
    @JsonProperty("AmountValue")
    public void setAmountValue(double value) { this.amountValue = value; }

    @JsonProperty("Currency")
    public String getCurrency() { return currency; }
    @JsonProperty("Currency")
    public void setCurrency(String value) { this.currency = value; }

    @JsonProperty("LoyaltyUnit")
    public LoyaltyUnitEnum getLoyaltyUnit() { return loyaltyUnit; }
    @JsonProperty("LoyaltyUnit")
    public void setLoyaltyUnit(LoyaltyUnitEnum value) { this.loyaltyUnit = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private double amountValue;
        private String currency;
        private LoyaltyUnitEnum loyaltyUnit;
        
        private Builder() {}
        
        public Builder amountValue(double amountValue) {
            this.amountValue = amountValue;
            return this;
        }
        
        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }
        
        public Builder loyaltyUnit(LoyaltyUnitEnum loyaltyUnit) {
            this.loyaltyUnit = loyaltyUnit;
            return this;
        }
        
        public LoyaltyAmount build() {
            LoyaltyAmount result = new LoyaltyAmount();
            result.setAmountValue(this.amountValue);
            result.setCurrency(this.currency);
            result.setLoyaltyUnit(this.loyaltyUnit);
            return result;
        }
    }
}
