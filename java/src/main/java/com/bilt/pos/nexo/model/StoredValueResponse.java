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
 * Content of the Stored Value Response message, conveying the result of each stored value
 * card operation.
 */
public class StoredValueResponse {
    private POIData poiData;
    private Response response;
    private SaleData saleData;
    private StoredValueResult[] storedValueResult;

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

    @JsonProperty("StoredValueResult")
    public StoredValueResult[] getStoredValueResult() { return storedValueResult; }
    @JsonProperty("StoredValueResult")
    public void setStoredValueResult(StoredValueResult[] value) { this.storedValueResult = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private POIData poiData;
        private Response response;
        private SaleData saleData;
        private StoredValueResult[] storedValueResult;
        
        private Builder() {}
        
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
        
        public Builder storedValueResult(StoredValueResult[] storedValueResult) {
            this.storedValueResult = storedValueResult;
            return this;
        }
        
        public StoredValueResponse build() {
            StoredValueResponse result = new StoredValueResponse();
            result.setPoiData(this.poiData);
            result.setResponse(this.response);
            result.setSaleData(this.saleData);
            result.setStoredValueResult(this.storedValueResult);
            return result;
        }
    }
}
