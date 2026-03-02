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
 * Data related to the payment and loyalty transaction that are global to both (amounts,
 * conditions, sold items).
 */
public class PaymentTransaction {
    private AmountsReq amountsReq;
    private OriginalPOITransaction originalPOITransaction;
    private SaleItem[] saleItem;
    private TransactionConditions transactionConditions;

    @JsonProperty("AmountsReq")
    public AmountsReq getAmountsReq() { return amountsReq; }
    @JsonProperty("AmountsReq")
    public void setAmountsReq(AmountsReq value) { this.amountsReq = value; }

    @JsonProperty("OriginalPOITransaction")
    public OriginalPOITransaction getOriginalPOITransaction() { return originalPOITransaction; }
    @JsonProperty("OriginalPOITransaction")
    public void setOriginalPOITransaction(OriginalPOITransaction value) { this.originalPOITransaction = value; }

    @JsonProperty("SaleItem")
    public SaleItem[] getSaleItem() { return saleItem; }
    @JsonProperty("SaleItem")
    public void setSaleItem(SaleItem[] value) { this.saleItem = value; }

    @JsonProperty("TransactionConditions")
    public TransactionConditions getTransactionConditions() { return transactionConditions; }
    @JsonProperty("TransactionConditions")
    public void setTransactionConditions(TransactionConditions value) { this.transactionConditions = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private AmountsReq amountsReq;
        private OriginalPOITransaction originalPOITransaction;
        private SaleItem[] saleItem;
        private TransactionConditions transactionConditions;
        
        private Builder() {}
        
        public Builder amountsReq(AmountsReq amountsReq) {
            this.amountsReq = amountsReq;
            return this;
        }
        
        public Builder originalPOITransaction(OriginalPOITransaction originalPOITransaction) {
            this.originalPOITransaction = originalPOITransaction;
            return this;
        }
        
        public Builder saleItem(SaleItem[] saleItem) {
            this.saleItem = saleItem;
            return this;
        }
        
        public Builder transactionConditions(TransactionConditions transactionConditions) {
            this.transactionConditions = transactionConditions;
            return this;
        }
        
        public PaymentTransaction build() {
            PaymentTransaction result = new PaymentTransaction();
            result.setAmountsReq(this.amountsReq);
            result.setOriginalPOITransaction(this.originalPOITransaction);
            result.setSaleItem(this.saleItem);
            result.setTransactionConditions(this.transactionConditions);
            return result;
        }
    }
}
