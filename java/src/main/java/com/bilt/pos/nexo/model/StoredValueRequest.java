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
 * Content of the Stored Value Request message, conveying information for loading,
 * reloading, or activating stored value cards.
 */
public class StoredValueRequest {
    private String customerLanguage;
    private SaleData saleData;
    private StoredValueData[] storedValueData;

    @JsonProperty("CustomerLanguage")
    public String getCustomerLanguage() { return customerLanguage; }
    @JsonProperty("CustomerLanguage")
    public void setCustomerLanguage(String value) { this.customerLanguage = value; }

    @JsonProperty("SaleData")
    public SaleData getSaleData() { return saleData; }
    @JsonProperty("SaleData")
    public void setSaleData(SaleData value) { this.saleData = value; }

    @JsonProperty("StoredValueData")
    public StoredValueData[] getStoredValueData() { return storedValueData; }
    @JsonProperty("StoredValueData")
    public void setStoredValueData(StoredValueData[] value) { this.storedValueData = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String customerLanguage;
        private SaleData saleData;
        private StoredValueData[] storedValueData;
        
        private Builder() {}
        
        public Builder customerLanguage(String customerLanguage) {
            this.customerLanguage = customerLanguage;
            return this;
        }
        
        public Builder saleData(SaleData saleData) {
            this.saleData = saleData;
            return this;
        }
        
        public Builder storedValueData(StoredValueData[] storedValueData) {
            this.storedValueData = storedValueData;
            return this;
        }
        
        public StoredValueRequest build() {
            StoredValueRequest result = new StoredValueRequest();
            result.setCustomerLanguage(this.customerLanguage);
            result.setSaleData(this.saleData);
            result.setStoredValueData(this.storedValueData);
            return result;
        }
    }
}
