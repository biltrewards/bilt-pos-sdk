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
 * Data related to the loyalty account for which a balance inquiry is requested.
 */
public class LoyaltyAccountReq {
    private TransactionIdentificationType cardAcquisitionReference;
    private LoyaltyAccountID loyaltyAccountID;

    @JsonProperty("CardAcquisitionReference")
    public TransactionIdentificationType getCardAcquisitionReference() { return cardAcquisitionReference; }
    @JsonProperty("CardAcquisitionReference")
    public void setCardAcquisitionReference(TransactionIdentificationType value) { this.cardAcquisitionReference = value; }

    @JsonProperty("LoyaltyAccountID")
    public LoyaltyAccountID getLoyaltyAccountID() { return loyaltyAccountID; }
    @JsonProperty("LoyaltyAccountID")
    public void setLoyaltyAccountID(LoyaltyAccountID value) { this.loyaltyAccountID = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private TransactionIdentificationType cardAcquisitionReference;
        private LoyaltyAccountID loyaltyAccountID;
        
        private Builder() {}
        
        public Builder cardAcquisitionReference(TransactionIdentificationType cardAcquisitionReference) {
            this.cardAcquisitionReference = cardAcquisitionReference;
            return this;
        }
        
        public Builder loyaltyAccountID(LoyaltyAccountID loyaltyAccountID) {
            this.loyaltyAccountID = loyaltyAccountID;
            return this;
        }
        
        public LoyaltyAccountReq build() {
            LoyaltyAccountReq result = new LoyaltyAccountReq();
            result.setCardAcquisitionReference(this.cardAcquisitionReference);
            result.setLoyaltyAccountID(this.loyaltyAccountID);
            return result;
        }
    }
}
