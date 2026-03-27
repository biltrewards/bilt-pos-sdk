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
 * Result of a loyalty account balance inquiry, including account identification, balance,
 * and unit.
 */
public class LoyaltyAccountStatus {
    private String currency;
    private Double currentBalance;
    private LoyaltyAccount loyaltyAccount;
    private LoyaltyUnitEnum loyaltyUnit;

    @JsonProperty("Currency")
    public String getCurrency() { return currency; }
    @JsonProperty("Currency")
    public void setCurrency(String value) { this.currency = value; }

    /**
     * Current balance of the loyalty account.
     */
    @JsonProperty("CurrentBalance")
    public Double getCurrentBalance() { return currentBalance; }
    @JsonProperty("CurrentBalance")
    public void setCurrentBalance(Double value) { this.currentBalance = value; }

    @JsonProperty("LoyaltyAccount")
    public LoyaltyAccount getLoyaltyAccount() { return loyaltyAccount; }
    @JsonProperty("LoyaltyAccount")
    public void setLoyaltyAccount(LoyaltyAccount value) { this.loyaltyAccount = value; }

    @JsonProperty("LoyaltyUnit")
    public LoyaltyUnitEnum getLoyaltyUnit() { return loyaltyUnit; }
    @JsonProperty("LoyaltyUnit")
    public void setLoyaltyUnit(LoyaltyUnitEnum value) { this.loyaltyUnit = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String currency;
        private Double currentBalance;
        private LoyaltyAccount loyaltyAccount;
        private LoyaltyUnitEnum loyaltyUnit;
        
        private Builder() {}
        
        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }
        
        public Builder currentBalance(Double currentBalance) {
            this.currentBalance = currentBalance;
            return this;
        }
        
        public Builder loyaltyAccount(LoyaltyAccount loyaltyAccount) {
            this.loyaltyAccount = loyaltyAccount;
            return this;
        }
        
        public Builder loyaltyUnit(LoyaltyUnitEnum loyaltyUnit) {
            this.loyaltyUnit = loyaltyUnit;
            return this;
        }
        
        public LoyaltyAccountStatus build() {
            LoyaltyAccountStatus result = new LoyaltyAccountStatus();
            result.setCurrency(this.currency);
            result.setCurrentBalance(this.currentBalance);
            result.setLoyaltyAccount(this.loyaltyAccount);
            result.setLoyaltyUnit(this.loyaltyUnit);
            return result;
        }
    }
}
