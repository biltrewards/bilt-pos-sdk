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
 * Content of the Card Acquisition Request message, used to read and analyse payment/loyalty
 * cards before a transaction.
 */
public class CardAcquisitionRequest {
    private CardAcquisitionTransaction cardAcquisitionTransaction;
    private SaleData saleData;

    @JsonProperty("CardAcquisitionTransaction")
    public CardAcquisitionTransaction getCardAcquisitionTransaction() { return cardAcquisitionTransaction; }
    @JsonProperty("CardAcquisitionTransaction")
    public void setCardAcquisitionTransaction(CardAcquisitionTransaction value) { this.cardAcquisitionTransaction = value; }

    @JsonProperty("SaleData")
    public SaleData getSaleData() { return saleData; }
    @JsonProperty("SaleData")
    public void setSaleData(SaleData value) { this.saleData = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private CardAcquisitionTransaction cardAcquisitionTransaction;
        private SaleData saleData;
        
        private Builder() {}
        
        public Builder cardAcquisitionTransaction(CardAcquisitionTransaction cardAcquisitionTransaction) {
            this.cardAcquisitionTransaction = cardAcquisitionTransaction;
            return this;
        }
        
        public Builder saleData(SaleData saleData) {
            this.saleData = saleData;
            return this;
        }
        
        public CardAcquisitionRequest build() {
            CardAcquisitionRequest result = new CardAcquisitionRequest();
            result.setCardAcquisitionTransaction(this.cardAcquisitionTransaction);
            result.setSaleData(this.saleData);
            return result;
        }
    }
}
