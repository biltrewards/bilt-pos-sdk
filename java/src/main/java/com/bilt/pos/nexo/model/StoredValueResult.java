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
 * Result of a single stored value card operation within a StoredValue response.
 */
public class StoredValueResult {
    private String currency;
    private String eanUpc;
    private TransactionIdentificationType hostTransactionID;
    private double itemAmount;
    private String productCode;
    private StoredValueAccountStatus storedValueAccountStatus;
    private StoredValueTransactionTypeEnum storedValueTransactionType;

    @JsonProperty("Currency")
    public String getCurrency() { return currency; }
    @JsonProperty("Currency")
    public void setCurrency(String value) { this.currency = value; }

    /**
     * EAN/UPC code of the stored value product. Copy from request.
     */
    @JsonProperty("EanUpc")
    public String getEanUpc() { return eanUpc; }
    @JsonProperty("EanUpc")
    public void setEanUpc(String value) { this.eanUpc = value; }

    @JsonProperty("HostTransactionID")
    public TransactionIdentificationType getHostTransactionID() { return hostTransactionID; }
    @JsonProperty("HostTransactionID")
    public void setHostTransactionID(TransactionIdentificationType value) { this.hostTransactionID = value; }

    @JsonProperty("ItemAmount")
    public double getItemAmount() { return itemAmount; }
    @JsonProperty("ItemAmount")
    public void setItemAmount(double value) { this.itemAmount = value; }

    /**
     * Product code of the stored value product. Copy from request.
     */
    @JsonProperty("ProductCode")
    public String getProductCode() { return productCode; }
    @JsonProperty("ProductCode")
    public void setProductCode(String value) { this.productCode = value; }

    @JsonProperty("StoredValueAccountStatus")
    public StoredValueAccountStatus getStoredValueAccountStatus() { return storedValueAccountStatus; }
    @JsonProperty("StoredValueAccountStatus")
    public void setStoredValueAccountStatus(StoredValueAccountStatus value) { this.storedValueAccountStatus = value; }

    @JsonProperty("StoredValueTransactionType")
    public StoredValueTransactionTypeEnum getStoredValueTransactionType() { return storedValueTransactionType; }
    @JsonProperty("StoredValueTransactionType")
    public void setStoredValueTransactionType(StoredValueTransactionTypeEnum value) { this.storedValueTransactionType = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String currency;
        private String eanUpc;
        private TransactionIdentificationType hostTransactionID;
        private double itemAmount;
        private String productCode;
        private StoredValueAccountStatus storedValueAccountStatus;
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
        
        public Builder hostTransactionID(TransactionIdentificationType hostTransactionID) {
            this.hostTransactionID = hostTransactionID;
            return this;
        }
        
        public Builder itemAmount(double itemAmount) {
            this.itemAmount = itemAmount;
            return this;
        }
        
        public Builder productCode(String productCode) {
            this.productCode = productCode;
            return this;
        }
        
        public Builder storedValueAccountStatus(StoredValueAccountStatus storedValueAccountStatus) {
            this.storedValueAccountStatus = storedValueAccountStatus;
            return this;
        }
        
        public Builder storedValueTransactionType(StoredValueTransactionTypeEnum storedValueTransactionType) {
            this.storedValueTransactionType = storedValueTransactionType;
            return this;
        }
        
        public StoredValueResult build() {
            StoredValueResult result = new StoredValueResult();
            result.setCurrency(this.currency);
            result.setEanUpc(this.eanUpc);
            result.setHostTransactionID(this.hostTransactionID);
            result.setItemAmount(this.itemAmount);
            result.setProductCode(this.productCode);
            result.setStoredValueAccountStatus(this.storedValueAccountStatus);
            result.setStoredValueTransactionType(this.storedValueTransactionType);
            return result;
        }
    }
}
