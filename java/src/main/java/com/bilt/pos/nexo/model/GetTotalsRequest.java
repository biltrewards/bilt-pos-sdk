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
 * Content of the GetTotals Request message, used to retrieve current period transaction
 * totals without closing the reconciliation period.
 */
public class GetTotalsRequest {
    private TotalDetailsType[] totalDetails;
    private TotalFilter totalFilter;

    @JsonProperty("TotalDetails")
    public TotalDetailsType[] getTotalDetails() { return totalDetails; }
    @JsonProperty("TotalDetails")
    public void setTotalDetails(TotalDetailsType[] value) { this.totalDetails = value; }

    @JsonProperty("TotalFilter")
    public TotalFilter getTotalFilter() { return totalFilter; }
    @JsonProperty("TotalFilter")
    public void setTotalFilter(TotalFilter value) { this.totalFilter = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private TotalDetailsType[] totalDetails;
        private TotalFilter totalFilter;
        
        private Builder() {}
        
        public Builder totalDetails(TotalDetailsType[] totalDetails) {
            this.totalDetails = totalDetails;
            return this;
        }
        
        public Builder totalFilter(TotalFilter totalFilter) {
            this.totalFilter = totalFilter;
            return this;
        }
        
        public GetTotalsRequest build() {
            GetTotalsRequest result = new GetTotalsRequest();
            result.setTotalDetails(this.totalDetails);
            result.setTotalFilter(this.totalFilter);
            return result;
        }
    }
}
