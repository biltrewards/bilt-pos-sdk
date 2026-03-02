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
 * Content of the Payment Response message, conveying the result of the payment transaction.
 */
public class PaymentResponse {
    private CustomerOrder[] customerOrder;
    private LoyaltyResult[] loyaltyResult;
    private PaymentReceipt[] paymentReceipt;
    private PaymentResult paymentResult;
    private POIData poiData;
    private Response response;
    private SaleData saleData;

    @JsonProperty("CustomerOrder")
    public CustomerOrder[] getCustomerOrder() { return customerOrder; }
    @JsonProperty("CustomerOrder")
    public void setCustomerOrder(CustomerOrder[] value) { this.customerOrder = value; }

    @JsonProperty("LoyaltyResult")
    public LoyaltyResult[] getLoyaltyResult() { return loyaltyResult; }
    @JsonProperty("LoyaltyResult")
    public void setLoyaltyResult(LoyaltyResult[] value) { this.loyaltyResult = value; }

    @JsonProperty("PaymentReceipt")
    public PaymentReceipt[] getPaymentReceipt() { return paymentReceipt; }
    @JsonProperty("PaymentReceipt")
    public void setPaymentReceipt(PaymentReceipt[] value) { this.paymentReceipt = value; }

    @JsonProperty("PaymentResult")
    public PaymentResult getPaymentResult() { return paymentResult; }
    @JsonProperty("PaymentResult")
    public void setPaymentResult(PaymentResult value) { this.paymentResult = value; }

    @JsonProperty("POIData")
    public POIData getPoiData() { return poiData; }
    @JsonProperty("POIData")
    public void setPoiData(POIData value) { this.poiData = value; }

    @JsonProperty("Response")
    public Response getResponse() { return response; }
    @JsonProperty("Response")
    public void setResponse(Response value) { this.response = value; }

    @JsonProperty("SaleData")
    public SaleData getSaleData() { return saleData; }
    @JsonProperty("SaleData")
    public void setSaleData(SaleData value) { this.saleData = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private CustomerOrder[] customerOrder;
        private LoyaltyResult[] loyaltyResult;
        private PaymentReceipt[] paymentReceipt;
        private PaymentResult paymentResult;
        private POIData poiData;
        private Response response;
        private SaleData saleData;
        
        private Builder() {}
        
        public Builder customerOrder(CustomerOrder[] customerOrder) {
            this.customerOrder = customerOrder;
            return this;
        }
        
        public Builder loyaltyResult(LoyaltyResult[] loyaltyResult) {
            this.loyaltyResult = loyaltyResult;
            return this;
        }
        
        public Builder paymentReceipt(PaymentReceipt[] paymentReceipt) {
            this.paymentReceipt = paymentReceipt;
            return this;
        }
        
        public Builder paymentResult(PaymentResult paymentResult) {
            this.paymentResult = paymentResult;
            return this;
        }
        
        public Builder poiData(POIData poiData) {
            this.poiData = poiData;
            return this;
        }
        
        public Builder response(Response response) {
            this.response = response;
            return this;
        }
        
        public Builder saleData(SaleData saleData) {
            this.saleData = saleData;
            return this;
        }
        
        public PaymentResponse build() {
            PaymentResponse result = new PaymentResponse();
            result.setCustomerOrder(this.customerOrder);
            result.setLoyaltyResult(this.loyaltyResult);
            result.setPaymentReceipt(this.paymentReceipt);
            result.setPaymentResult(this.paymentResult);
            result.setPoiData(this.poiData);
            result.setResponse(this.response);
            result.setSaleData(this.saleData);
            return result;
        }
    }
}
