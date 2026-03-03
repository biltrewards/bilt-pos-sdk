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
 * Content of the Reconciliation Request message, specifying the type of reconciliation and
 * optionally targeting specific Acquirers or a previous period.
 */
public class ReconciliationRequest {
    private String[] acquirerID;
    private String poiReconciliationID;
    private ReconciliationTypeEnum reconciliationType;

    /**
     * Acquirers to include in AcquirerReconciliation or AcquirerSynchronisation. All connected
     * Acquirers are included when absent.
     */
    @JsonProperty("AcquirerID")
    public String[] getAcquirerID() { return acquirerID; }
    @JsonProperty("AcquirerID")
    public void setAcquirerID(String[] value) { this.acquirerID = value; }

    /**
     * Identification of a previous reconciliation period. Mandatory for PreviousReconciliation
     * type.
     */
    @JsonProperty("POIReconciliationID")
    public String getPoiReconciliationID() { return poiReconciliationID; }
    @JsonProperty("POIReconciliationID")
    public void setPoiReconciliationID(String value) { this.poiReconciliationID = value; }

    @JsonProperty("ReconciliationType")
    public ReconciliationTypeEnum getReconciliationType() { return reconciliationType; }
    @JsonProperty("ReconciliationType")
    public void setReconciliationType(ReconciliationTypeEnum value) { this.reconciliationType = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String[] acquirerID;
        private String poiReconciliationID;
        private ReconciliationTypeEnum reconciliationType;
        
        private Builder() {}
        
        public Builder acquirerID(String[] acquirerID) {
            this.acquirerID = acquirerID;
            return this;
        }
        
        public Builder poiReconciliationID(String poiReconciliationID) {
            this.poiReconciliationID = poiReconciliationID;
            return this;
        }
        
        public Builder reconciliationType(ReconciliationTypeEnum reconciliationType) {
            this.reconciliationType = reconciliationType;
            return this;
        }
        
        public ReconciliationRequest build() {
            ReconciliationRequest result = new ReconciliationRequest();
            result.setAcquirerID(this.acquirerID);
            result.setPoiReconciliationID(this.poiReconciliationID);
            result.setReconciliationType(this.reconciliationType);
            return result;
        }
    }
}
