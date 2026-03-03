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
 * A product that is payable by the presented payment card, used when the card has product
 * restrictions.
 */
public class AllowedProduct {
    private String additionalProductInfo;
    private String eanUpc;
    private String productCode;
    private String productLabel;

    /**
     * Additional information related to the product.
     */
    @JsonProperty("AdditionalProductInfo")
    public String getAdditionalProductInfo() { return additionalProductInfo; }
    @JsonProperty("AdditionalProductInfo")
    public void setAdditionalProductInfo(String value) { this.additionalProductInfo = value; }

    /**
     * Standard EAN/UPC product code.
     */
    @JsonProperty("EanUpc")
    public String getEanUpc() { return eanUpc; }
    @JsonProperty("EanUpc")
    public void setEanUpc(String value) { this.eanUpc = value; }

    /**
     * Product code of a payable item.
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
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String additionalProductInfo;
        private String eanUpc;
        private String productCode;
        private String productLabel;
        
        private Builder() {}
        
        public Builder additionalProductInfo(String additionalProductInfo) {
            this.additionalProductInfo = additionalProductInfo;
            return this;
        }
        
        public Builder eanUpc(String eanUpc) {
            this.eanUpc = eanUpc;
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
        
        public AllowedProduct build() {
            AllowedProduct result = new AllowedProduct();
            result.setAdditionalProductInfo(this.additionalProductInfo);
            result.setEanUpc(this.eanUpc);
            result.setProductCode(this.productCode);
            result.setProductLabel(this.productLabel);
            return result;
        }
    }
}
