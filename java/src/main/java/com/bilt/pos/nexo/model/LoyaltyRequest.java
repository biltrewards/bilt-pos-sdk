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
 * Content of the Loyalty Request message, conveying all information required to process a
 * standalone loyalty transaction.
 */
public class LoyaltyRequest {
    private LoyaltyData[] loyaltyData;
    private LoyaltyTransaction loyaltyTransaction;
    private SaleData saleData;

    @JsonProperty("LoyaltyData")
    public LoyaltyData[] getLoyaltyData() { return loyaltyData; }
    @JsonProperty("LoyaltyData")
    public void setLoyaltyData(LoyaltyData[] value) { this.loyaltyData = value; }

    @JsonProperty("LoyaltyTransaction")
    public LoyaltyTransaction getLoyaltyTransaction() { return loyaltyTransaction; }
    @JsonProperty("LoyaltyTransaction")
    public void setLoyaltyTransaction(LoyaltyTransaction value) { this.loyaltyTransaction = value; }

    @JsonProperty("SaleData")
    public SaleData getSaleData() { return saleData; }
    @JsonProperty("SaleData")
    public void setSaleData(SaleData value) { this.saleData = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private LoyaltyData[] loyaltyData;
        private LoyaltyTransaction loyaltyTransaction;
        private SaleData saleData;
        
        private Builder() {}
        
        public Builder loyaltyData(LoyaltyData[] loyaltyData) {
            this.loyaltyData = loyaltyData;
            return this;
        }
        
        public Builder loyaltyTransaction(LoyaltyTransaction loyaltyTransaction) {
            this.loyaltyTransaction = loyaltyTransaction;
            return this;
        }
        
        public Builder saleData(SaleData saleData) {
            this.saleData = saleData;
            return this;
        }
        
        public LoyaltyRequest build() {
            LoyaltyRequest result = new LoyaltyRequest();
            result.setLoyaltyData(this.loyaltyData);
            result.setLoyaltyTransaction(this.loyaltyTransaction);
            result.setSaleData(this.saleData);
            return result;
        }
    }
}
