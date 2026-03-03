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
 * Filter criteria for GetTotals, restricting the totals to a specific POI Terminal, Sale
 * Terminal, Cashier, Shift, or Sale group.
 */
public class TotalFilter {
    private String operatorID;
    private String poiid;
    private String saleID;
    private String shiftNumber;
    private String totalsGroupID;

    /**
     * Filter totals to this specific Cashier/Operator only.
     */
    @JsonProperty("OperatorID")
    public String getOperatorID() { return operatorID; }
    @JsonProperty("OperatorID")
    public void setOperatorID(String value) { this.operatorID = value; }

    /**
     * Filter totals to this specific POI Terminal only.
     */
    @JsonProperty("POIID")
    public String getPoiid() { return poiid; }
    @JsonProperty("POIID")
    public void setPoiid(String value) { this.poiid = value; }

    /**
     * Filter totals to this specific Sale Terminal only.
     */
    @JsonProperty("SaleID")
    public String getSaleID() { return saleID; }
    @JsonProperty("SaleID")
    public void setSaleID(String value) { this.saleID = value; }

    /**
     * Filter totals to this specific shift only.
     */
    @JsonProperty("ShiftNumber")
    public String getShiftNumber() { return shiftNumber; }
    @JsonProperty("ShiftNumber")
    public void setShiftNumber(String value) { this.shiftNumber = value; }

    /**
     * Filter totals to this specific Sale group only.
     */
    @JsonProperty("TotalsGroupID")
    public String getTotalsGroupID() { return totalsGroupID; }
    @JsonProperty("TotalsGroupID")
    public void setTotalsGroupID(String value) { this.totalsGroupID = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String operatorID;
        private String poiid;
        private String saleID;
        private String shiftNumber;
        private String totalsGroupID;
        
        private Builder() {}
        
        public Builder operatorID(String operatorID) {
            this.operatorID = operatorID;
            return this;
        }
        
        public Builder poiid(String poiid) {
            this.poiid = poiid;
            return this;
        }
        
        public Builder saleID(String saleID) {
            this.saleID = saleID;
            return this;
        }
        
        public Builder shiftNumber(String shiftNumber) {
            this.shiftNumber = shiftNumber;
            return this;
        }
        
        public Builder totalsGroupID(String totalsGroupID) {
            this.totalsGroupID = totalsGroupID;
            return this;
        }
        
        public TotalFilter build() {
            TotalFilter result = new TotalFilter();
            result.setOperatorID(this.operatorID);
            result.setPoiid(this.poiid);
            result.setSaleID(this.saleID);
            result.setShiftNumber(this.shiftNumber);
            result.setTotalsGroupID(this.totalsGroupID);
            return result;
        }
    }
}
