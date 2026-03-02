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
 * Data related to a stored value card operation (activate, load, unload, etc.) for one card
 * in a StoredValue request.
 */
public class StoredValueData {
    private String currency;
    private String eanUpc;
    private double itemAmount;
    private OriginalPOITransaction originalPOITransaction;
    private String productCode;
    private StoredValueAccountID storedValueAccountID;
    private String storedValueProvider;
    private StoredValueTransactionTypeEnum storedValueTransactionType;

    @JsonProperty("Currency")
    public String getCurrency() { return currency; }
    @JsonProperty("Currency")
    public void setCurrency(String value) { this.currency = value; }

    /**
     * EAN/UPC code of the stored value product.
     */
    @JsonProperty("EanUpc")
    public String getEanUpc() { return eanUpc; }
    @JsonProperty("EanUpc")
    public void setEanUpc(String value) { this.eanUpc = value; }

    @JsonProperty("ItemAmount")
    public double getItemAmount() { return itemAmount; }
    @JsonProperty("ItemAmount")
    public void setItemAmount(double value) { this.itemAmount = value; }

    @JsonProperty("OriginalPOITransaction")
    public OriginalPOITransaction getOriginalPOITransaction() { return originalPOITransaction; }
    @JsonProperty("OriginalPOITransaction")
    public void setOriginalPOITransaction(OriginalPOITransaction value) { this.originalPOITransaction = value; }

    /**
     * Product code identifying the stored value product (e.g. gift card type, phone top-up
     * operator).
     */
    @JsonProperty("ProductCode")
    public String getProductCode() { return productCode; }
    @JsonProperty("ProductCode")
    public void setProductCode(String value) { this.productCode = value; }

    @JsonProperty("StoredValueAccountID")
    public StoredValueAccountID getStoredValueAccountID() { return storedValueAccountID; }
    @JsonProperty("StoredValueAccountID")
    public void setStoredValueAccountID(StoredValueAccountID value) { this.storedValueAccountID = value; }

    /**
     * Identification of the stored value provider/host when not identifiable from the product
     * code alone.
     */
    @JsonProperty("StoredValueProvider")
    public String getStoredValueProvider() { return storedValueProvider; }
    @JsonProperty("StoredValueProvider")
    public void setStoredValueProvider(String value) { this.storedValueProvider = value; }

    @JsonProperty("StoredValueTransactionType")
    public StoredValueTransactionTypeEnum getStoredValueTransactionType() { return storedValueTransactionType; }
    @JsonProperty("StoredValueTransactionType")
    public void setStoredValueTransactionType(StoredValueTransactionTypeEnum value) { this.storedValueTransactionType = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String currency;
        private String eanUpc;
        private double itemAmount;
        private OriginalPOITransaction originalPOITransaction;
        private String productCode;
        private StoredValueAccountID storedValueAccountID;
        private String storedValueProvider;
        private StoredValueTransactionTypeEnum storedValueTransactionType;
        
        private Builder() {}
        
        public Builder currency(String currency) {
            this.currency = currency;
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
        
        public Builder originalPOITransaction(OriginalPOITransaction originalPOITransaction) {
            this.originalPOITransaction = originalPOITransaction;
            return this;
        }
        
        public Builder productCode(String productCode) {
            this.productCode = productCode;
            return this;
        }
        
        public Builder storedValueAccountID(StoredValueAccountID storedValueAccountID) {
            this.storedValueAccountID = storedValueAccountID;
            return this;
        }
        
        public Builder storedValueProvider(String storedValueProvider) {
            this.storedValueProvider = storedValueProvider;
            return this;
        }
        
        public Builder storedValueTransactionType(StoredValueTransactionTypeEnum storedValueTransactionType) {
            this.storedValueTransactionType = storedValueTransactionType;
            return this;
        }
        
        public StoredValueData build() {
            StoredValueData result = new StoredValueData();
            result.setCurrency(this.currency);
            result.setEanUpc(this.eanUpc);
            result.setItemAmount(this.itemAmount);
            result.setOriginalPOITransaction(this.originalPOITransaction);
            result.setProductCode(this.productCode);
            result.setStoredValueAccountID(this.storedValueAccountID);
            result.setStoredValueProvider(this.storedValueProvider);
            result.setStoredValueTransactionType(this.storedValueTransactionType);
            return result;
        }
    }
}
