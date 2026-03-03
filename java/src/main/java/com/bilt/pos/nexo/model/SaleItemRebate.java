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
 * Rebate awarded on a specific sale item as part of a loyalty transaction.
 */
public class SaleItemRebate {
    private String eanUpc;
    private Double itemAmount;
    private long itemID;
    private String productCode;
    private Double quantity;
    private String rebateLabel;
    private UnitOfMeasureEnum unitOfMeasure;

    /**
     * EAN/UPC code of the rebated item, if present in the corresponding SaleItem.
     */
    @JsonProperty("EanUpc")
    public String getEanUpc() { return eanUpc; }
    @JsonProperty("EanUpc")
    public void setEanUpc(String value) { this.eanUpc = value; }

    /**
     * Rebate amount on the line item.
     */
    @JsonProperty("ItemAmount")
    public Double getItemAmount() { return itemAmount; }
    @JsonProperty("ItemAmount")
    public void setItemAmount(Double value) { this.itemAmount = value; }

    /**
     * Identification of the sale item within the transaction (links to the corresponding
     * SaleItem).
     */
    @JsonProperty("ItemID")
    public long getItemID() { return itemID; }
    @JsonProperty("ItemID")
    public void setItemID(long value) { this.itemID = value; }

    /**
     * Product code of the rebated item.
     */
    @JsonProperty("ProductCode")
    public String getProductCode() { return productCode; }
    @JsonProperty("ProductCode")
    public void setProductCode(String value) { this.productCode = value; }

    /**
     * Quantity of additional free units awarded as rebate.
     */
    @JsonProperty("Quantity")
    public Double getQuantity() { return quantity; }
    @JsonProperty("Quantity")
    public void setQuantity(Double value) { this.quantity = value; }

    /**
     * Short text to print on the receipt in front of the rebate, provided by the Acquirer.
     */
    @JsonProperty("RebateLabel")
    public String getRebateLabel() { return rebateLabel; }
    @JsonProperty("RebateLabel")
    public void setRebateLabel(String value) { this.rebateLabel = value; }

    @JsonProperty("UnitOfMeasure")
    public UnitOfMeasureEnum getUnitOfMeasure() { return unitOfMeasure; }
    @JsonProperty("UnitOfMeasure")
    public void setUnitOfMeasure(UnitOfMeasureEnum value) { this.unitOfMeasure = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String eanUpc;
        private Double itemAmount;
        private long itemID;
        private String productCode;
        private Double quantity;
        private String rebateLabel;
        private UnitOfMeasureEnum unitOfMeasure;
        
        private Builder() {}
        
        public Builder eanUpc(String eanUpc) {
            this.eanUpc = eanUpc;
            return this;
        }
        
        public Builder itemAmount(Double itemAmount) {
            this.itemAmount = itemAmount;
            return this;
        }
        
        public Builder itemID(long itemID) {
            this.itemID = itemID;
            return this;
        }
        
        public Builder productCode(String productCode) {
            this.productCode = productCode;
            return this;
        }
        
        public Builder quantity(Double quantity) {
            this.quantity = quantity;
            return this;
        }
        
        public Builder rebateLabel(String rebateLabel) {
            this.rebateLabel = rebateLabel;
            return this;
        }
        
        public Builder unitOfMeasure(UnitOfMeasureEnum unitOfMeasure) {
            this.unitOfMeasure = unitOfMeasure;
            return this;
        }
        
        public SaleItemRebate build() {
            SaleItemRebate result = new SaleItemRebate();
            result.setEanUpc(this.eanUpc);
            result.setItemAmount(this.itemAmount);
            result.setItemID(this.itemID);
            result.setProductCode(this.productCode);
            result.setQuantity(this.quantity);
            result.setRebateLabel(this.rebateLabel);
            result.setUnitOfMeasure(this.unitOfMeasure);
            return result;
        }
    }
}
