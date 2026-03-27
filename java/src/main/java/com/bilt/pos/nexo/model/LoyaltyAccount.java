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
 * Data identifying a loyalty account and its associated loyalty program brand.
 */
public class LoyaltyAccount {
    private LoyaltyAccountID loyaltyAccountID;
    private String loyaltyBrand;

    @JsonProperty("LoyaltyAccountID")
    public LoyaltyAccountID getLoyaltyAccountID() { return loyaltyAccountID; }
    @JsonProperty("LoyaltyAccountID")
    public void setLoyaltyAccountID(LoyaltyAccountID value) { this.loyaltyAccountID = value; }

    /**
     * Name of the loyalty program brand as known by the Sale System.
     */
    @JsonProperty("LoyaltyBrand")
    public String getLoyaltyBrand() { return loyaltyBrand; }
    @JsonProperty("LoyaltyBrand")
    public void setLoyaltyBrand(String value) { this.loyaltyBrand = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private LoyaltyAccountID loyaltyAccountID;
        private String loyaltyBrand;
        
        private Builder() {}
        
        public Builder loyaltyAccountID(LoyaltyAccountID loyaltyAccountID) {
            this.loyaltyAccountID = loyaltyAccountID;
            return this;
        }
        
        public Builder loyaltyBrand(String loyaltyBrand) {
            this.loyaltyBrand = loyaltyBrand;
            return this;
        }
        
        public LoyaltyAccount build() {
            LoyaltyAccount result = new LoyaltyAccount();
            result.setLoyaltyAccountID(this.loyaltyAccountID);
            result.setLoyaltyBrand(this.loyaltyBrand);
            return result;
        }
    }
}
