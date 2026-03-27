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
 * Identification of a loyalty account, including how the identification was obtained.
 */
public class LoyaltyAccountID {
    private EntryModeType[] entryMode;
    private IdentificationSupportEnum identificationSupport;
    private IdentificationTypeEnum identificationType;
    private String loyaltyID;

    @JsonProperty("EntryMode")
    public EntryModeType[] getEntryMode() { return entryMode; }
    @JsonProperty("EntryMode")
    public void setEntryMode(EntryModeType[] value) { this.entryMode = value; }

    @JsonProperty("IdentificationSupport")
    public IdentificationSupportEnum getIdentificationSupport() { return identificationSupport; }
    @JsonProperty("IdentificationSupport")
    public void setIdentificationSupport(IdentificationSupportEnum value) { this.identificationSupport = value; }

    @JsonProperty("IdentificationType")
    public IdentificationTypeEnum getIdentificationType() { return identificationType; }
    @JsonProperty("IdentificationType")
    public void setIdentificationType(IdentificationTypeEnum value) { this.identificationType = value; }

    /**
     * Loyalty account identification conforming to the IdentificationType.
     */
    @JsonProperty("LoyaltyID")
    public String getLoyaltyID() { return loyaltyID; }
    @JsonProperty("LoyaltyID")
    public void setLoyaltyID(String value) { this.loyaltyID = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private EntryModeType[] entryMode;
        private IdentificationSupportEnum identificationSupport;
        private IdentificationTypeEnum identificationType;
        private String loyaltyID;
        
        private Builder() {}
        
        public Builder entryMode(EntryModeType[] entryMode) {
            this.entryMode = entryMode;
            return this;
        }
        
        public Builder identificationSupport(IdentificationSupportEnum identificationSupport) {
            this.identificationSupport = identificationSupport;
            return this;
        }
        
        public Builder identificationType(IdentificationTypeEnum identificationType) {
            this.identificationType = identificationType;
            return this;
        }
        
        public Builder loyaltyID(String loyaltyID) {
            this.loyaltyID = loyaltyID;
            return this;
        }
        
        public LoyaltyAccountID build() {
            LoyaltyAccountID result = new LoyaltyAccountID();
            result.setEntryMode(this.entryMode);
            result.setIdentificationSupport(this.identificationSupport);
            result.setIdentificationType(this.identificationType);
            result.setLoyaltyID(this.loyaltyID);
            return result;
        }
    }
}
