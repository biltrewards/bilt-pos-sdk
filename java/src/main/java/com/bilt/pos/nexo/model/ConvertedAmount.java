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
 * The payment amount expressed in the customer's home currency after conversion.
 */
public class ConvertedAmount {
    private double amountValue;
    private String currency;

    @JsonProperty("AmountValue")
    public double getAmountValue() { return amountValue; }
    @JsonProperty("AmountValue")
    public void setAmountValue(double value) { this.amountValue = value; }

    @JsonProperty("Currency")
    public String getCurrency() { return currency; }
    @JsonProperty("Currency")
    public void setCurrency(String value) { this.currency = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private double amountValue;
        private String currency;
        
        private Builder() {}
        
        public Builder amountValue(double amountValue) {
            this.amountValue = amountValue;
            return this;
        }
        
        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }
        
        public ConvertedAmount build() {
            ConvertedAmount result = new ConvertedAmount();
            result.setAmountValue(this.amountValue);
            result.setCurrency(this.currency);
            return result;
        }
    }
}
