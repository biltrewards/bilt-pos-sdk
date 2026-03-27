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
 * Information related to a paper check used as a payment instrument.
 */
public class CheckData {
    private String accountNumber;
    private String bankID;
    private String checkCardNumber;
    private String checkNumber;
    private String country;
    private TrackData trackData;
    private TypeCode typeCode;

    /**
     * Customer account number. Mandatory when TrackData is absent.
     */
    @JsonProperty("AccountNumber")
    public String getAccountNumber() { return accountNumber; }
    @JsonProperty("AccountNumber")
    public void setAccountNumber(String value) { this.accountNumber = value; }

    /**
     * Identification of the bank. Mandatory when TrackData is absent.
     */
    @JsonProperty("BankID")
    public String getBankID() { return bankID; }
    @JsonProperty("BankID")
    public void setBankID(String value) { this.bankID = value; }

    /**
     * Check guarantee card number presented during the check tendering process.
     */
    @JsonProperty("CheckCardNumber")
    public String getCheckCardNumber() { return checkCardNumber; }
    @JsonProperty("CheckCardNumber")
    public void setCheckCardNumber(String value) { this.checkCardNumber = value; }

    /**
     * Identification of the bank check. Mandatory when TrackData is absent.
     */
    @JsonProperty("CheckNumber")
    public String getCheckNumber() { return checkNumber; }
    @JsonProperty("CheckNumber")
    public void setCheckNumber(String value) { this.checkNumber = value; }

    /**
     * Country of the bank check. Absent when it is the country of the Sale System.
     */
    @JsonProperty("Country")
    public String getCountry() { return country; }
    @JsonProperty("Country")
    public void setCountry(String value) { this.country = value; }

    @JsonProperty("TrackData")
    public TrackData getTrackData() { return trackData; }
    @JsonProperty("TrackData")
    public void setTrackData(TrackData value) { this.trackData = value; }

    /**
     * Type of bank check. Default Personal.
     */
    @JsonProperty("TypeCode")
    public TypeCode getTypeCode() { return typeCode; }
    @JsonProperty("TypeCode")
    public void setTypeCode(TypeCode value) { this.typeCode = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String accountNumber;
        private String bankID;
        private String checkCardNumber;
        private String checkNumber;
        private String country;
        private TrackData trackData;
        private TypeCode typeCode;
        
        private Builder() {}
        
        public Builder accountNumber(String accountNumber) {
            this.accountNumber = accountNumber;
            return this;
        }
        
        public Builder bankID(String bankID) {
            this.bankID = bankID;
            return this;
        }
        
        public Builder checkCardNumber(String checkCardNumber) {
            this.checkCardNumber = checkCardNumber;
            return this;
        }
        
        public Builder checkNumber(String checkNumber) {
            this.checkNumber = checkNumber;
            return this;
        }
        
        public Builder country(String country) {
            this.country = country;
            return this;
        }
        
        public Builder trackData(TrackData trackData) {
            this.trackData = trackData;
            return this;
        }
        
        public Builder typeCode(TypeCode typeCode) {
            this.typeCode = typeCode;
            return this;
        }
        
        public CheckData build() {
            CheckData result = new CheckData();
            result.setAccountNumber(this.accountNumber);
            result.setBankID(this.bankID);
            result.setCheckCardNumber(this.checkCardNumber);
            result.setCheckNumber(this.checkNumber);
            result.setCountry(this.country);
            result.setTrackData(this.trackData);
            result.setTypeCode(this.typeCode);
            return result;
        }
    }
}
