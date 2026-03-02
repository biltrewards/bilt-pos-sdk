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
 * Content of the Payment Request message, conveying all information required to process a
 * payment transaction.
 */
public class PaymentRequest {
    private LoyaltyData[] loyaltyData;
    private PaymentData paymentData;
    private PaymentTransaction paymentTransaction;
    private SaleData saleData;

    /**
     * Loyalty cards to process with the payment transaction, read by the Sale Terminal.
     */
    @JsonProperty("LoyaltyData")
    public LoyaltyData[] getLoyaltyData() { return loyaltyData; }
    @JsonProperty("LoyaltyData")
    public void setLoyaltyData(LoyaltyData[] value) { this.loyaltyData = value; }

    @JsonProperty("PaymentData")
    public PaymentData getPaymentData() { return paymentData; }
    @JsonProperty("PaymentData")
    public void setPaymentData(PaymentData value) { this.paymentData = value; }

    @JsonProperty("PaymentTransaction")
    public PaymentTransaction getPaymentTransaction() { return paymentTransaction; }
    @JsonProperty("PaymentTransaction")
    public void setPaymentTransaction(PaymentTransaction value) { this.paymentTransaction = value; }

    @JsonProperty("SaleData")
    public SaleData getSaleData() { return saleData; }
    @JsonProperty("SaleData")
    public void setSaleData(SaleData value) { this.saleData = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private LoyaltyData[] loyaltyData;
        private PaymentData paymentData;
        private PaymentTransaction paymentTransaction;
        private SaleData saleData;
        
        private Builder() {}
        
        public Builder loyaltyData(LoyaltyData[] loyaltyData) {
            this.loyaltyData = loyaltyData;
            return this;
        }
        
        public Builder paymentData(PaymentData paymentData) {
            this.paymentData = paymentData;
            return this;
        }
        
        public Builder paymentTransaction(PaymentTransaction paymentTransaction) {
            this.paymentTransaction = paymentTransaction;
            return this;
        }
        
        public Builder saleData(SaleData saleData) {
            this.saleData = saleData;
            return this;
        }
        
        public PaymentRequest build() {
            PaymentRequest result = new PaymentRequest();
            result.setLoyaltyData(this.loyaltyData);
            result.setPaymentData(this.paymentData);
            result.setPaymentTransaction(this.paymentTransaction);
            result.setSaleData(this.saleData);
            return result;
        }
    }
}
