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
 * POI System transaction identification data returned in payment, loyalty, and related
 * response messages.
 */
public class POIData {
    private String poiReconciliationID;
    private TransactionIdentificationType poiTransactionID;

    /**
     * Identification of the reconciliation period to which this transaction belongs. Present
     * when Result is Success or Partial.
     */
    @JsonProperty("POIReconciliationID")
    public String getPoiReconciliationID() { return poiReconciliationID; }
    @JsonProperty("POIReconciliationID")
    public void setPoiReconciliationID(String value) { this.poiReconciliationID = value; }

    /**
     * Unique identification of the transaction assigned by the POI Terminal. Mandatory in all
     * response messages.
     */
    @JsonProperty("POITransactionID")
    public TransactionIdentificationType getPoiTransactionID() { return poiTransactionID; }
    @JsonProperty("POITransactionID")
    public void setPoiTransactionID(TransactionIdentificationType value) { this.poiTransactionID = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String poiReconciliationID;
        private TransactionIdentificationType poiTransactionID;
        
        private Builder() {}
        
        public Builder poiReconciliationID(String poiReconciliationID) {
            this.poiReconciliationID = poiReconciliationID;
            return this;
        }
        
        public Builder poiTransactionID(TransactionIdentificationType poiTransactionID) {
            this.poiTransactionID = poiTransactionID;
            return this;
        }
        
        public POIData build() {
            POIData result = new POIData();
            result.setPoiReconciliationID(this.poiReconciliationID);
            result.setPoiTransactionID(this.poiTransactionID);
            return result;
        }
    }
}
