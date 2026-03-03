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
 * Rebates awarded as part of a loyalty transaction, either on the total amount or on
 * individual sale items.
 */
public class Rebates {
    private String rebateLabel;
    private SaleItemRebate[] saleItemRebate;
    private Double totalRebate;

    /**
     * Short text to print on the receipt for the total rebate, provided by the Acquirer.
     */
    @JsonProperty("RebateLabel")
    public String getRebateLabel() { return rebateLabel; }
    @JsonProperty("RebateLabel")
    public void setRebateLabel(String value) { this.rebateLabel = value; }

    @JsonProperty("SaleItemRebate")
    public SaleItemRebate[] getSaleItemRebate() { return saleItemRebate; }
    @JsonProperty("SaleItemRebate")
    public void setSaleItemRebate(SaleItemRebate[] value) { this.saleItemRebate = value; }

    /**
     * Global rebate amount not attached to a specific item.
     */
    @JsonProperty("TotalRebate")
    public Double getTotalRebate() { return totalRebate; }
    @JsonProperty("TotalRebate")
    public void setTotalRebate(Double value) { this.totalRebate = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String rebateLabel;
        private SaleItemRebate[] saleItemRebate;
        private Double totalRebate;
        
        private Builder() {}
        
        public Builder rebateLabel(String rebateLabel) {
            this.rebateLabel = rebateLabel;
            return this;
        }
        
        public Builder saleItemRebate(SaleItemRebate[] saleItemRebate) {
            this.saleItemRebate = saleItemRebate;
            return this;
        }
        
        public Builder totalRebate(Double totalRebate) {
            this.totalRebate = totalRebate;
            return this;
        }
        
        public Rebates build() {
            Rebates result = new Rebates();
            result.setRebateLabel(this.rebateLabel);
            result.setSaleItemRebate(this.saleItemRebate);
            result.setTotalRebate(this.totalRebate);
            return result;
        }
    }
}
