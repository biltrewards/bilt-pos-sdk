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
 * Content of the Balance Inquiry Response message, conveying account balances and
 * identification.
 */
public class BalanceInquiryResponse {
    private LoyaltyAccountStatus loyaltyAccountStatus;
    private PaymentAccountStatus paymentAccountStatus;
    private PaymentReceipt[] paymentReceipt;
    private Response response;

    @JsonProperty("LoyaltyAccountStatus")
    public LoyaltyAccountStatus getLoyaltyAccountStatus() { return loyaltyAccountStatus; }
    @JsonProperty("LoyaltyAccountStatus")
    public void setLoyaltyAccountStatus(LoyaltyAccountStatus value) { this.loyaltyAccountStatus = value; }

    @JsonProperty("PaymentAccountStatus")
    public PaymentAccountStatus getPaymentAccountStatus() { return paymentAccountStatus; }
    @JsonProperty("PaymentAccountStatus")
    public void setPaymentAccountStatus(PaymentAccountStatus value) { this.paymentAccountStatus = value; }

    @JsonProperty("PaymentReceipt")
    public PaymentReceipt[] getPaymentReceipt() { return paymentReceipt; }
    @JsonProperty("PaymentReceipt")
    public void setPaymentReceipt(PaymentReceipt[] value) { this.paymentReceipt = value; }

    @JsonProperty("Response")
    public Response getResponse() { return response; }
    @JsonProperty("Response")
    public void setResponse(Response value) { this.response = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private LoyaltyAccountStatus loyaltyAccountStatus;
        private PaymentAccountStatus paymentAccountStatus;
        private PaymentReceipt[] paymentReceipt;
        private Response response;
        
        private Builder() {}
        
        public Builder loyaltyAccountStatus(LoyaltyAccountStatus loyaltyAccountStatus) {
            this.loyaltyAccountStatus = loyaltyAccountStatus;
            return this;
        }
        
        public Builder paymentAccountStatus(PaymentAccountStatus paymentAccountStatus) {
            this.paymentAccountStatus = paymentAccountStatus;
            return this;
        }
        
        public Builder paymentReceipt(PaymentReceipt[] paymentReceipt) {
            this.paymentReceipt = paymentReceipt;
            return this;
        }
        
        public Builder response(Response response) {
            this.response = response;
            return this;
        }
        
        public BalanceInquiryResponse build() {
            BalanceInquiryResponse result = new BalanceInquiryResponse();
            result.setLoyaltyAccountStatus(this.loyaltyAccountStatus);
            result.setPaymentAccountStatus(this.paymentAccountStatus);
            result.setPaymentReceipt(this.paymentReceipt);
            result.setResponse(this.response);
            return result;
        }
    }
}
