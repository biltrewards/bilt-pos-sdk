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
 * Data related to a loyalty account used with a payment or loyalty transaction, provided by
 * the Sale System.
 */
public class LoyaltyData {
    private TransactionIdentificationType cardAcquisitionReference;
    private LoyaltyAccountID loyaltyAccountID;
    private LoyaltyAmount loyaltyAmount;

    /**
     * Reference to a previous CardAcquisition transaction from which to reuse the loyalty
     * account identification.
     */
    @JsonProperty("CardAcquisitionReference")
    public TransactionIdentificationType getCardAcquisitionReference() { return cardAcquisitionReference; }
    @JsonProperty("CardAcquisitionReference")
    public void setCardAcquisitionReference(TransactionIdentificationType value) { this.cardAcquisitionReference = value; }

    @JsonProperty("LoyaltyAccountID")
    public LoyaltyAccountID getLoyaltyAccountID() { return loyaltyAccountID; }
    @JsonProperty("LoyaltyAccountID")
    public void setLoyaltyAccountID(LoyaltyAccountID value) { this.loyaltyAccountID = value; }

    @JsonProperty("LoyaltyAmount")
    public LoyaltyAmount getLoyaltyAmount() { return loyaltyAmount; }
    @JsonProperty("LoyaltyAmount")
    public void setLoyaltyAmount(LoyaltyAmount value) { this.loyaltyAmount = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private TransactionIdentificationType cardAcquisitionReference;
        private LoyaltyAccountID loyaltyAccountID;
        private LoyaltyAmount loyaltyAmount;
        
        private Builder() {}
        
        public Builder cardAcquisitionReference(TransactionIdentificationType cardAcquisitionReference) {
            this.cardAcquisitionReference = cardAcquisitionReference;
            return this;
        }
        
        public Builder loyaltyAccountID(LoyaltyAccountID loyaltyAccountID) {
            this.loyaltyAccountID = loyaltyAccountID;
            return this;
        }
        
        public Builder loyaltyAmount(LoyaltyAmount loyaltyAmount) {
            this.loyaltyAmount = loyaltyAmount;
            return this;
        }
        
        public LoyaltyData build() {
            LoyaltyData result = new LoyaltyData();
            result.setCardAcquisitionReference(this.cardAcquisitionReference);
            result.setLoyaltyAccountID(this.loyaltyAccountID);
            result.setLoyaltyAmount(this.loyaltyAmount);
            return result;
        }
    }
}
