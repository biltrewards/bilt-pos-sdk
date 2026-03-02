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
 * Identification of a stored value account or card.
 */
public class StoredValueAccountID {
    private EntryModeType[] entryMode;
    private String expiryDate;
    private IdentificationTypeEnum identificationType;
    private String ownerName;
    private StoredValueAccountTypeEnum storedValueAccountType;
    private String storedValueID;
    private String storedValueProvider;

    @JsonProperty("EntryMode")
    public EntryModeType[] getEntryMode() { return entryMode; }
    @JsonProperty("EntryMode")
    public void setEntryMode(EntryModeType[] value) { this.entryMode = value; }

    /**
     * Date after which the stored value account or card cannot be used. Format MMYY.
     */
    @JsonProperty("ExpiryDate")
    public String getExpiryDate() { return expiryDate; }
    @JsonProperty("ExpiryDate")
    public void setExpiryDate(String value) { this.expiryDate = value; }

    @JsonProperty("IdentificationType")
    public IdentificationTypeEnum getIdentificationType() { return identificationType; }
    @JsonProperty("IdentificationType")
    public void setIdentificationType(IdentificationTypeEnum value) { this.identificationType = value; }

    /**
     * Name of the owner of the stored value account.
     */
    @JsonProperty("OwnerName")
    public String getOwnerName() { return ownerName; }
    @JsonProperty("OwnerName")
    public void setOwnerName(String value) { this.ownerName = value; }

    @JsonProperty("StoredValueAccountType")
    public StoredValueAccountTypeEnum getStoredValueAccountType() { return storedValueAccountType; }
    @JsonProperty("StoredValueAccountType")
    public void setStoredValueAccountType(StoredValueAccountTypeEnum value) { this.storedValueAccountType = value; }

    /**
     * Stored value account identification conforming to the IdentificationType.
     */
    @JsonProperty("StoredValueID")
    public String getStoredValueID() { return storedValueID; }
    @JsonProperty("StoredValueID")
    public void setStoredValueID(String value) { this.storedValueID = value; }

    /**
     * Identification of the stored value account provider/host when the product code is
     * insufficient to identify it.
     */
    @JsonProperty("StoredValueProvider")
    public String getStoredValueProvider() { return storedValueProvider; }
    @JsonProperty("StoredValueProvider")
    public void setStoredValueProvider(String value) { this.storedValueProvider = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private EntryModeType[] entryMode;
        private String expiryDate;
        private IdentificationTypeEnum identificationType;
        private String ownerName;
        private StoredValueAccountTypeEnum storedValueAccountType;
        private String storedValueID;
        private String storedValueProvider;
        
        private Builder() {}
        
        public Builder entryMode(EntryModeType[] entryMode) {
            this.entryMode = entryMode;
            return this;
        }
        
        public Builder expiryDate(String expiryDate) {
            this.expiryDate = expiryDate;
            return this;
        }
        
        public Builder identificationType(IdentificationTypeEnum identificationType) {
            this.identificationType = identificationType;
            return this;
        }
        
        public Builder ownerName(String ownerName) {
            this.ownerName = ownerName;
            return this;
        }
        
        public Builder storedValueAccountType(StoredValueAccountTypeEnum storedValueAccountType) {
            this.storedValueAccountType = storedValueAccountType;
            return this;
        }
        
        public Builder storedValueID(String storedValueID) {
            this.storedValueID = storedValueID;
            return this;
        }
        
        public Builder storedValueProvider(String storedValueProvider) {
            this.storedValueProvider = storedValueProvider;
            return this;
        }
        
        public StoredValueAccountID build() {
            StoredValueAccountID result = new StoredValueAccountID();
            result.setEntryMode(this.entryMode);
            result.setExpiryDate(this.expiryDate);
            result.setIdentificationType(this.identificationType);
            result.setOwnerName(this.ownerName);
            result.setStoredValueAccountType(this.storedValueAccountType);
            result.setStoredValueID(this.storedValueID);
            result.setStoredValueProvider(this.storedValueProvider);
            return result;
        }
    }
}
