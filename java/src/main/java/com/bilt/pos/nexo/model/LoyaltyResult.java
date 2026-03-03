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
 * Result of a loyalty transaction, including account identification, amounts, acquirer
 * data, and any rebates.
 */
public class LoyaltyResult {
    private Double currentBalance;
    private LoyaltyAccount loyaltyAccount;
    private LoyaltyAcquirerData loyaltyAcquirerData;
    private LoyaltyAmount loyaltyAmount;
    private Rebates rebates;

    /**
     * Current balance of the loyalty account after the transaction, if provided by the card or
     * host.
     */
    @JsonProperty("CurrentBalance")
    public Double getCurrentBalance() { return currentBalance; }
    @JsonProperty("CurrentBalance")
    public void setCurrentBalance(Double value) { this.currentBalance = value; }

    @JsonProperty("LoyaltyAccount")
    public LoyaltyAccount getLoyaltyAccount() { return loyaltyAccount; }
    @JsonProperty("LoyaltyAccount")
    public void setLoyaltyAccount(LoyaltyAccount value) { this.loyaltyAccount = value; }

    @JsonProperty("LoyaltyAcquirerData")
    public LoyaltyAcquirerData getLoyaltyAcquirerData() { return loyaltyAcquirerData; }
    @JsonProperty("LoyaltyAcquirerData")
    public void setLoyaltyAcquirerData(LoyaltyAcquirerData value) { this.loyaltyAcquirerData = value; }

    @JsonProperty("LoyaltyAmount")
    public LoyaltyAmount getLoyaltyAmount() { return loyaltyAmount; }
    @JsonProperty("LoyaltyAmount")
    public void setLoyaltyAmount(LoyaltyAmount value) { this.loyaltyAmount = value; }

    @JsonProperty("Rebates")
    public Rebates getRebates() { return rebates; }
    @JsonProperty("Rebates")
    public void setRebates(Rebates value) { this.rebates = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private Double currentBalance;
        private LoyaltyAccount loyaltyAccount;
        private LoyaltyAcquirerData loyaltyAcquirerData;
        private LoyaltyAmount loyaltyAmount;
        private Rebates rebates;
        
        private Builder() {}
        
        public Builder currentBalance(Double currentBalance) {
            this.currentBalance = currentBalance;
            return this;
        }
        
        public Builder loyaltyAccount(LoyaltyAccount loyaltyAccount) {
            this.loyaltyAccount = loyaltyAccount;
            return this;
        }
        
        public Builder loyaltyAcquirerData(LoyaltyAcquirerData loyaltyAcquirerData) {
            this.loyaltyAcquirerData = loyaltyAcquirerData;
            return this;
        }
        
        public Builder loyaltyAmount(LoyaltyAmount loyaltyAmount) {
            this.loyaltyAmount = loyaltyAmount;
            return this;
        }
        
        public Builder rebates(Rebates rebates) {
            this.rebates = rebates;
            return this;
        }
        
        public LoyaltyResult build() {
            LoyaltyResult result = new LoyaltyResult();
            result.setCurrentBalance(this.currentBalance);
            result.setLoyaltyAccount(this.loyaltyAccount);
            result.setLoyaltyAcquirerData(this.loyaltyAcquirerData);
            result.setLoyaltyAmount(this.loyaltyAmount);
            result.setRebates(this.rebates);
            return result;
        }
    }
}
