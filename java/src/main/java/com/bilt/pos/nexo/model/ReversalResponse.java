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
 * Content of the Reversal Response message, conveying the result of the reversal.
 */
public class ReversalResponse {
    private CustomerOrder[] customerOrder;
    private OriginalPOITransaction originalPOITransaction;
    private PaymentReceipt[] paymentReceipt;
    private POIData poiData;
    private Response response;
    private Double reversedAmount;

    @JsonProperty("CustomerOrder")
    public CustomerOrder[] getCustomerOrder() { return customerOrder; }
    @JsonProperty("CustomerOrder")
    public void setCustomerOrder(CustomerOrder[] value) { this.customerOrder = value; }

    @JsonProperty("OriginalPOITransaction")
    public OriginalPOITransaction getOriginalPOITransaction() { return originalPOITransaction; }
    @JsonProperty("OriginalPOITransaction")
    public void setOriginalPOITransaction(OriginalPOITransaction value) { this.originalPOITransaction = value; }

    @JsonProperty("PaymentReceipt")
    public PaymentReceipt[] getPaymentReceipt() { return paymentReceipt; }
    @JsonProperty("PaymentReceipt")
    public void setPaymentReceipt(PaymentReceipt[] value) { this.paymentReceipt = value; }

    @JsonProperty("POIData")
    public POIData getPoiData() { return poiData; }
    @JsonProperty("POIData")
    public void setPoiData(POIData value) { this.poiData = value; }

    @JsonProperty("Response")
    public Response getResponse() { return response; }
    @JsonProperty("Response")
    public void setResponse(Response value) { this.response = value; }

    @JsonProperty("ReversedAmount")
    public Double getReversedAmount() { return reversedAmount; }
    @JsonProperty("ReversedAmount")
    public void setReversedAmount(Double value) { this.reversedAmount = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private CustomerOrder[] customerOrder;
        private OriginalPOITransaction originalPOITransaction;
        private PaymentReceipt[] paymentReceipt;
        private POIData poiData;
        private Response response;
        private Double reversedAmount;
        
        private Builder() {}
        
        public Builder customerOrder(CustomerOrder[] customerOrder) {
            this.customerOrder = customerOrder;
            return this;
        }
        
        public Builder originalPOITransaction(OriginalPOITransaction originalPOITransaction) {
            this.originalPOITransaction = originalPOITransaction;
            return this;
        }
        
        public Builder paymentReceipt(PaymentReceipt[] paymentReceipt) {
            this.paymentReceipt = paymentReceipt;
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
        
        public Builder reversedAmount(Double reversedAmount) {
            this.reversedAmount = reversedAmount;
            return this;
        }
        
        public ReversalResponse build() {
            ReversalResponse result = new ReversalResponse();
            result.setCustomerOrder(this.customerOrder);
            result.setOriginalPOITransaction(this.originalPOITransaction);
            result.setPaymentReceipt(this.paymentReceipt);
            result.setPoiData(this.poiData);
            result.setResponse(this.response);
            result.setReversedAmount(this.reversedAmount);
            return result;
        }
    }
}
