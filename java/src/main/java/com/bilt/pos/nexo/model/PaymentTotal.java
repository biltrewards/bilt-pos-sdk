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
 * Totals of payment transactions of a specific type during the reconciliation period.
 */
public class PaymentTotal {
    private double transactionAmount;
    private long transactionCount;
    private TransactionTypeEnum transactionType;

    /**
     * Sum of amounts of processed transactions of this type during the period.
     */
    @JsonProperty("TransactionAmount")
    public double getTransactionAmount() { return transactionAmount; }
    @JsonProperty("TransactionAmount")
    public void setTransactionAmount(double value) { this.transactionAmount = value; }

    /**
     * Number of processed transactions of this type during the period.
     */
    @JsonProperty("TransactionCount")
    public long getTransactionCount() { return transactionCount; }
    @JsonProperty("TransactionCount")
    public void setTransactionCount(long value) { this.transactionCount = value; }

    @JsonProperty("TransactionType")
    public TransactionTypeEnum getTransactionType() { return transactionType; }
    @JsonProperty("TransactionType")
    public void setTransactionType(TransactionTypeEnum value) { this.transactionType = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private double transactionAmount;
        private long transactionCount;
        private TransactionTypeEnum transactionType;
        
        private Builder() {}
        
        public Builder transactionAmount(double transactionAmount) {
            this.transactionAmount = transactionAmount;
            return this;
        }
        
        public Builder transactionCount(long transactionCount) {
            this.transactionCount = transactionCount;
            return this;
        }
        
        public Builder transactionType(TransactionTypeEnum transactionType) {
            this.transactionType = transactionType;
            return this;
        }
        
        public PaymentTotal build() {
            PaymentTotal result = new PaymentTotal();
            result.setTransactionAmount(this.transactionAmount);
            result.setTransactionCount(this.transactionCount);
            result.setTransactionType(this.transactionType);
            return result;
        }
    }
}
