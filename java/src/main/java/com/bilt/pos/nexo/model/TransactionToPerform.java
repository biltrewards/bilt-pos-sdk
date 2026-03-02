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
 * A single transaction to perform in the batch.
 */
public class TransactionToPerform {
    private PaymentRequest paymentRequest;
    private LoyaltyRequest loyaltyRequest;
    private ReversalRequest reversalRequest;

    @JsonProperty("PaymentRequest")
    public PaymentRequest getPaymentRequest() { return paymentRequest; }
    @JsonProperty("PaymentRequest")
    public void setPaymentRequest(PaymentRequest value) { this.paymentRequest = value; }

    @JsonProperty("LoyaltyRequest")
    public LoyaltyRequest getLoyaltyRequest() { return loyaltyRequest; }
    @JsonProperty("LoyaltyRequest")
    public void setLoyaltyRequest(LoyaltyRequest value) { this.loyaltyRequest = value; }

    @JsonProperty("ReversalRequest")
    public ReversalRequest getReversalRequest() { return reversalRequest; }
    @JsonProperty("ReversalRequest")
    public void setReversalRequest(ReversalRequest value) { this.reversalRequest = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private PaymentRequest paymentRequest;
        private LoyaltyRequest loyaltyRequest;
        private ReversalRequest reversalRequest;
        
        private Builder() {}
        
        public Builder paymentRequest(PaymentRequest paymentRequest) {
            this.paymentRequest = paymentRequest;
            return this;
        }
        
        public Builder loyaltyRequest(LoyaltyRequest loyaltyRequest) {
            this.loyaltyRequest = loyaltyRequest;
            return this;
        }
        
        public Builder reversalRequest(ReversalRequest reversalRequest) {
            this.reversalRequest = reversalRequest;
            return this;
        }
        
        public TransactionToPerform build() {
            TransactionToPerform result = new TransactionToPerform();
            result.setPaymentRequest(this.paymentRequest);
            result.setLoyaltyRequest(this.loyaltyRequest);
            result.setReversalRequest(this.reversalRequest);
            return result;
        }
    }
}
