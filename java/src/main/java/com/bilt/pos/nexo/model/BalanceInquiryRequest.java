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
 * Content of the Balance Inquiry Request message, used to query the balance of a payment,
 * loyalty, or stored value account.
 */
public class BalanceInquiryRequest {
    private LoyaltyAccountReq loyaltyAccountReq;
    private PaymentAccountReq paymentAccountReq;

    @JsonProperty("LoyaltyAccountReq")
    public LoyaltyAccountReq getLoyaltyAccountReq() { return loyaltyAccountReq; }
    @JsonProperty("LoyaltyAccountReq")
    public void setLoyaltyAccountReq(LoyaltyAccountReq value) { this.loyaltyAccountReq = value; }

    @JsonProperty("PaymentAccountReq")
    public PaymentAccountReq getPaymentAccountReq() { return paymentAccountReq; }
    @JsonProperty("PaymentAccountReq")
    public void setPaymentAccountReq(PaymentAccountReq value) { this.paymentAccountReq = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private LoyaltyAccountReq loyaltyAccountReq;
        private PaymentAccountReq paymentAccountReq;
        
        private Builder() {}
        
        public Builder loyaltyAccountReq(LoyaltyAccountReq loyaltyAccountReq) {
            this.loyaltyAccountReq = loyaltyAccountReq;
            return this;
        }
        
        public Builder paymentAccountReq(PaymentAccountReq paymentAccountReq) {
            this.paymentAccountReq = paymentAccountReq;
            return this;
        }
        
        public BalanceInquiryRequest build() {
            BalanceInquiryRequest result = new BalanceInquiryRequest();
            result.setLoyaltyAccountReq(this.loyaltyAccountReq);
            result.setPaymentAccountReq(this.paymentAccountReq);
            return result;
        }
    }
}
