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
 * Content of the GetTotals Response message, conveying current period transaction totals.
 */
public class GetTotalsResponse {
    private String poiReconciliationID;
    private Response response;
    private TransactionTotals[] transactionTotals;

    /**
     * Identification of the current reconciliation period for these totals.
     */
    @JsonProperty("POIReconciliationID")
    public String getPoiReconciliationID() { return poiReconciliationID; }
    @JsonProperty("POIReconciliationID")
    public void setPoiReconciliationID(String value) { this.poiReconciliationID = value; }

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
        private Response response;
        private TransactionTotals[] transactionTotals;
        
        private Builder() {}
        
        public Builder poiReconciliationID(String poiReconciliationID) {
            this.poiReconciliationID = poiReconciliationID;
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
        
        public GetTotalsResponse build() {
            GetTotalsResponse result = new GetTotalsResponse();
            result.setPoiReconciliationID(this.poiReconciliationID);
            result.setResponse(this.response);
            result.setTransactionTotals(this.transactionTotals);
            return result;
        }
    }
}
