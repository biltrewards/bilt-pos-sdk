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
 * Content of the Reconciliation Response message, conveying transaction totals for the
 * reconciliation period.
 */
public class ReconciliationResponse {
    private String poiReconciliationID;
    private ReconciliationTypeEnum reconciliationType;
    private Response response;
    private TransactionTotals[] transactionTotals;

    /**
     * Identification of the reconciliation period covered by the totals. Absent for
     * AcquirerReconciliation type.
     */
    @JsonProperty("POIReconciliationID")
    public String getPoiReconciliationID() { return poiReconciliationID; }
    @JsonProperty("POIReconciliationID")
    public void setPoiReconciliationID(String value) { this.poiReconciliationID = value; }

    @JsonProperty("ReconciliationType")
    public ReconciliationTypeEnum getReconciliationType() { return reconciliationType; }
    @JsonProperty("ReconciliationType")
    public void setReconciliationType(ReconciliationTypeEnum value) { this.reconciliationType = value; }

    @JsonProperty("Response")
    public Response getResponse() { return response; }
    @JsonProperty("Response")
    public void setResponse(Response value) { this.response = value; }

    @JsonProperty("TransactionTotals")
    public TransactionTotals[] getTransactionTotals() { return transactionTotals; }
    @JsonProperty("TransactionTotals")
    public void setTransactionTotals(TransactionTotals[] value) { this.transactionTotals = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String poiReconciliationID;
        private ReconciliationTypeEnum reconciliationType;
        private Response response;
        private TransactionTotals[] transactionTotals;
        
        private Builder() {}
        
        public Builder poiReconciliationID(String poiReconciliationID) {
            this.poiReconciliationID = poiReconciliationID;
            return this;
        }
        
        public Builder reconciliationType(ReconciliationTypeEnum reconciliationType) {
            this.reconciliationType = reconciliationType;
            return this;
        }
        
        public Builder response(Response response) {
            this.response = response;
            return this;
        }
        
        public Builder transactionTotals(TransactionTotals[] transactionTotals) {
            this.transactionTotals = transactionTotals;
            return this;
        }
        
        public ReconciliationResponse build() {
            ReconciliationResponse result = new ReconciliationResponse();
            result.setPoiReconciliationID(this.poiReconciliationID);
            result.setReconciliationType(this.reconciliationType);
            result.setResponse(this.response);
            result.setTransactionTotals(this.transactionTotals);
            return result;
        }
    }
}
