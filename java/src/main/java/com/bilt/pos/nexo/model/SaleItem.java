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
 * A sale item in the transaction basket, used for payment cards with product restrictions
 * or loyalty processing.
 */
public class SaleItem {
    private String additionalProductInfo;
    private String eanUpc;
    private double itemAmount;
    private long itemID;
    private String productCode;
    private String productLabel;
    private Double quantity;
    private String saleChannel;
    private String taxCode;
    private UnitOfMeasureEnum unitOfMeasure;
    private Double unitPrice;

    /**
     * Additional information related to the line item.
     */
    @JsonProperty("AdditionalProductInfo")
    public String getAdditionalProductInfo() { return additionalProductInfo; }
    @JsonProperty("AdditionalProductInfo")
    public void setAdditionalProductInfo(String value) { this.additionalProductInfo = value; }

    /**
     * Standard EAN/UPC product code. If sent, the POI stores and forwards it to the host when
     * the protocol allows.
     */
    @JsonProperty("EanUpc")
    public String getEanUpc() { return eanUpc; }
    @JsonProperty("EanUpc")
    public void setEanUpc(String value) { this.eanUpc = value; }

    /**
     * Total amount for this line item (quantity × unit price).
     */
    @JsonProperty("ItemAmount")
    public double getItemAmount() { return itemAmount; }
    @JsonProperty("ItemAmount")
    public void setItemAmount(double value) { this.itemAmount = value; }

    /**
     * Identification of the item within the transaction (0 to n).
     */
    @JsonProperty("ItemID")
    public long getItemID() { return itemID; }
    @JsonProperty("ItemID")
    public void setItemID(long value) { this.itemID = value; }

    /**
     * Product code of the purchased item (1–20 digits).
     */
    @JsonProperty("ProductCode")
    public String getProductCode() { return productCode; }
    @JsonProperty("ProductCode")
    public void setProductCode(String value) { this.productCode = value; }

    /**
     * Human-readable product name.
     */
    @JsonProperty("ProductLabel")
    public String getProductLabel() { return productLabel; }
    @JsonProperty("ProductLabel")
    public void setProductLabel(String value) { this.productLabel = value; }

    /**
     * Quantity of the product purchased. If sent, the POI stores and forwards it.
     */
    @JsonProperty("Quantity")
    public Double getQuantity() { return quantity; }
    @JsonProperty("Quantity")
    public void setQuantity(Double value) { this.quantity = value; }

    /**
     * Commercial or distribution channel associated with this line item.
     */
    @JsonProperty("SaleChannel")
    public String getSaleChannel() { return saleChannel; }
    @JsonProperty("SaleChannel")
    public void setSaleChannel(String value) { this.saleChannel = value; }

    /**
     * Tax type code associated with this line item.
     */
    @JsonProperty("TaxCode")
    public String getTaxCode() { return taxCode; }
    @JsonProperty("TaxCode")
    public void setTaxCode(String value) { this.taxCode = value; }

    @JsonProperty("UnitOfMeasure")
    public UnitOfMeasureEnum getUnitOfMeasure() { return unitOfMeasure; }
    @JsonProperty("UnitOfMeasure")
    public void setUnitOfMeasure(UnitOfMeasureEnum value) { this.unitOfMeasure = value; }

    /**
     * Price per unit of the product. Required when Quantity is present.
     */
    @JsonProperty("UnitPrice")
    public Double getUnitPrice() { return unitPrice; }
    @JsonProperty("UnitPrice")
    public void setUnitPrice(Double value) { this.unitPrice = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String additionalProductInfo;
        private String eanUpc;
        private double itemAmount;
        private long itemID;
        private String productCode;
        private String productLabel;
        private Double quantity;
        private String saleChannel;
        private String taxCode;
        private UnitOfMeasureEnum unitOfMeasure;
        private Double unitPrice;
        
        private Builder() {}
        
        public Builder additionalProductInfo(String additionalProductInfo) {
            this.additionalProductInfo = additionalProductInfo;
            return this;
        }
        
        public Builder eanUpc(String eanUpc) {
            this.eanUpc = eanUpc;
            return this;
        }
        
        public Builder itemAmount(double itemAmount) {
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
        
        public Builder productLabel(String productLabel) {
            this.productLabel = productLabel;
            return this;
        }
        
        public Builder quantity(Double quantity) {
            this.quantity = quantity;
            return this;
        }
        
        public Builder saleChannel(String saleChannel) {
            this.saleChannel = saleChannel;
            return this;
        }
        
        public Builder taxCode(String taxCode) {
            this.taxCode = taxCode;
            return this;
        }
        
        public Builder unitOfMeasure(UnitOfMeasureEnum unitOfMeasure) {
            this.unitOfMeasure = unitOfMeasure;
            return this;
        }
        
        public Builder unitPrice(Double unitPrice) {
            this.unitPrice = unitPrice;
            return this;
        }
        
        public SaleItem build() {
            SaleItem result = new SaleItem();
            result.setAdditionalProductInfo(this.additionalProductInfo);
            result.setEanUpc(this.eanUpc);
            result.setItemAmount(this.itemAmount);
            result.setItemID(this.itemID);
            result.setProductCode(this.productCode);
            result.setProductLabel(this.productLabel);
            result.setQuantity(this.quantity);
            result.setSaleChannel(this.saleChannel);
            result.setTaxCode(this.taxCode);
            result.setUnitOfMeasure(this.unitOfMeasure);
            result.setUnitPrice(this.unitPrice);
            return result;
        }
    }
}
