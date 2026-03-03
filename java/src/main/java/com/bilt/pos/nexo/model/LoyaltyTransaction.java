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
 * Data related to a loyalty transaction, including type, amount, and conditions.
 */
public class LoyaltyTransaction {
    private String currency;
    private LoyaltyTransactionTypeEnum loyaltyTransactionType;
    private OriginalPOITransaction originalPOITransaction;
    private SaleItem[] saleItem;
    private Double totalAmount;
    private TransactionConditions transactionConditions;

    @JsonProperty("Currency")
    public String getCurrency() { return currency; }
    @JsonProperty("Currency")
    public void setCurrency(String value) { this.currency = value; }

    @JsonProperty("LoyaltyTransactionType")
    public LoyaltyTransactionTypeEnum getLoyaltyTransactionType() { return loyaltyTransactionType; }
    @JsonProperty("LoyaltyTransactionType")
    public void setLoyaltyTransactionType(LoyaltyTransactionTypeEnum value) { this.loyaltyTransactionType = value; }

    @JsonProperty("OriginalPOITransaction")
    public OriginalPOITransaction getOriginalPOITransaction() { return originalPOITransaction; }
    @JsonProperty("OriginalPOITransaction")
    public void setOriginalPOITransaction(OriginalPOITransaction value) { this.originalPOITransaction = value; }

    @JsonProperty("SaleItem")
    public SaleItem[] getSaleItem() { return saleItem; }
    @JsonProperty("SaleItem")
    public void setSaleItem(SaleItem[] value) { this.saleItem = value; }

    /**
     * Amount of the related payment transaction on which the loyalty transaction is based.
     */
    @JsonProperty("TotalAmount")
    public Double getTotalAmount() { return totalAmount; }
    @JsonProperty("TotalAmount")
    public void setTotalAmount(Double value) { this.totalAmount = value; }

    @JsonProperty("TransactionConditions")
    public TransactionConditions getTransactionConditions() { return transactionConditions; }
    @JsonProperty("TransactionConditions")
    public void setTransactionConditions(TransactionConditions value) { this.transactionConditions = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String currency;
        private LoyaltyTransactionTypeEnum loyaltyTransactionType;
        private OriginalPOITransaction originalPOITransaction;
        private SaleItem[] saleItem;
        private Double totalAmount;
        private TransactionConditions transactionConditions;
        
        private Builder() {}
        
        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }
        
        public Builder loyaltyTransactionType(LoyaltyTransactionTypeEnum loyaltyTransactionType) {
            this.loyaltyTransactionType = loyaltyTransactionType;
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
        
        public Builder totalAmount(Double totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }
        
        public Builder transactionConditions(TransactionConditions transactionConditions) {
            this.transactionConditions = transactionConditions;
            return this;
        }
        
        public LoyaltyTransaction build() {
            LoyaltyTransaction result = new LoyaltyTransaction();
            result.setCurrency(this.currency);
            result.setLoyaltyTransactionType(this.loyaltyTransactionType);
            result.setOriginalPOITransaction(this.originalPOITransaction);
            result.setSaleItem(this.saleItem);
            result.setTotalAmount(this.totalAmount);
            result.setTransactionConditions(this.transactionConditions);
            return result;
        }
    }
}
