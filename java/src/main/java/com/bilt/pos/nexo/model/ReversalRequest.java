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
 * Content of the Reversal Request message, used to cancel a previously completed payment or
 * loyalty transaction.
 */
public class ReversalRequest {
    private CustomerOrder customerOrder;
    private OriginalPOITransaction originalPOITransaction;
    private ReversalReasonEnum reversalReason;
    private Double reversedAmount;
    private SaleData saleData;
    private String saleReferenceID;

    @JsonProperty("CustomerOrder")
    public CustomerOrder getCustomerOrder() { return customerOrder; }
    @JsonProperty("CustomerOrder")
    public void setCustomerOrder(CustomerOrder value) { this.customerOrder = value; }

    @JsonProperty("OriginalPOITransaction")
    public OriginalPOITransaction getOriginalPOITransaction() { return originalPOITransaction; }
    @JsonProperty("OriginalPOITransaction")
    public void setOriginalPOITransaction(OriginalPOITransaction value) { this.originalPOITransaction = value; }

    @JsonProperty("ReversalReason")
    public ReversalReasonEnum getReversalReason() { return reversalReason; }
    @JsonProperty("ReversalReason")
    public void setReversalReason(ReversalReasonEnum value) { this.reversalReason = value; }

    /**
     * Amount to reverse for a partial reversal. Implicitly equals the AuthorizedAmount of the
     * original transaction when absent.
     */
    @JsonProperty("ReversedAmount")
    public Double getReversedAmount() { return reversedAmount; }
    @JsonProperty("ReversedAmount")
    public void setReversedAmount(Double value) { this.reversedAmount = value; }

    @JsonProperty("SaleData")
    public SaleData getSaleData() { return saleData; }
    @JsonProperty("SaleData")
    public void setSaleData(SaleData value) { this.saleData = value; }

    /**
     * Sale reference identifying the reservation transaction to cancel. Mandatory for
     * reservation reversals.
     */
    @JsonProperty("SaleReferenceID")
    public String getSaleReferenceID() { return saleReferenceID; }
    @JsonProperty("SaleReferenceID")
    public void setSaleReferenceID(String value) { this.saleReferenceID = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private CustomerOrder customerOrder;
        private OriginalPOITransaction originalPOITransaction;
        private ReversalReasonEnum reversalReason;
        private Double reversedAmount;
        private SaleData saleData;
        private String saleReferenceID;
        
        private Builder() {}
        
        public Builder customerOrder(CustomerOrder customerOrder) {
            this.customerOrder = customerOrder;
            return this;
        }
        
        public Builder originalPOITransaction(OriginalPOITransaction originalPOITransaction) {
            this.originalPOITransaction = originalPOITransaction;
            return this;
        }
        
        public Builder reversalReason(ReversalReasonEnum reversalReason) {
            this.reversalReason = reversalReason;
            return this;
        }
        
        public Builder reversedAmount(Double reversedAmount) {
            this.reversedAmount = reversedAmount;
            return this;
        }
        
        public Builder saleData(SaleData saleData) {
            this.saleData = saleData;
            return this;
        }
        
        public Builder saleReferenceID(String saleReferenceID) {
            this.saleReferenceID = saleReferenceID;
            return this;
        }
        
        public ReversalRequest build() {
            ReversalRequest result = new ReversalRequest();
            result.setCustomerOrder(this.customerOrder);
            result.setOriginalPOITransaction(this.originalPOITransaction);
            result.setReversalReason(this.reversalReason);
            result.setReversedAmount(this.reversedAmount);
            result.setSaleData(this.saleData);
            result.setSaleReferenceID(this.saleReferenceID);
            return result;
        }
    }
}
