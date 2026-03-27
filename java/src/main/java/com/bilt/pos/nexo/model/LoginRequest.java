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
 * Content of the Login Request message, conveying Sale System identification, terminal
 * characteristics, and session defaults.
 */
public class LoginRequest {
    private CustomerOrderReqType[] customerOrderReq;
    private String dateTime;
    private String operatorID;
    private String operatorLanguage;
    private String poiSerialNumber;
    private SaleSoftware saleSoftware;
    private SaleTerminalData saleTerminalData;
    private String shiftNumber;
    private TokenRequestedTypeEnum tokenRequestedType;
    private Boolean trainingModeFlag;

    @JsonProperty("CustomerOrderReq")
    public CustomerOrderReqType[] getCustomerOrderReq() { return customerOrderReq; }
    @JsonProperty("CustomerOrderReq")
    public void setCustomerOrderReq(CustomerOrderReqType[] value) { this.customerOrderReq = value; }

    /**
     * Date and time of the Sale System or Sale Terminal, allowing the POI to synchronise its
     * clock.
     */
    @JsonProperty("DateTime")
    public String getDateTime() { return dateTime; }
    @JsonProperty("DateTime")
    public void setDateTime(String value) { this.dateTime = value; }

    /**
     * Identification of the Cashier driving the Sale Terminal. Sent for logging,
     * reconciliation, or acquirer requirements.
     */
    @JsonProperty("OperatorID")
    public String getOperatorID() { return operatorID; }
    @JsonProperty("OperatorID")
    public void setOperatorID(String value) { this.operatorID = value; }

    /**
     * Default Cashier language for device displays during this session.
     */
    @JsonProperty("OperatorLanguage")
    public String getOperatorLanguage() { return operatorLanguage; }
    @JsonProperty("OperatorLanguage")
    public void setOperatorLanguage(String value) { this.operatorLanguage = value; }

    /**
     * Serial number of the POI Terminal as received in the last Login Response, to detect
     * hardware changes.
     */
    @JsonProperty("POISerialNumber")
    public String getPoiSerialNumber() { return poiSerialNumber; }
    @JsonProperty("POISerialNumber")
    public void setPoiSerialNumber(String value) { this.poiSerialNumber = value; }

    @JsonProperty("SaleSoftware")
    public SaleSoftware getSaleSoftware() { return saleSoftware; }
    @JsonProperty("SaleSoftware")
    public void setSaleSoftware(SaleSoftware value) { this.saleSoftware = value; }

    @JsonProperty("SaleTerminalData")
    public SaleTerminalData getSaleTerminalData() { return saleTerminalData; }
    @JsonProperty("SaleTerminalData")
    public void setSaleTerminalData(SaleTerminalData value) { this.saleTerminalData = value; }

    /**
     * Shift number for the session. Sent for logging and reconciliation purposes.
     */
    @JsonProperty("ShiftNumber")
    public String getShiftNumber() { return shiftNumber; }
    @JsonProperty("ShiftNumber")
    public void setShiftNumber(String value) { this.shiftNumber = value; }

    @JsonProperty("TokenRequestedType")
    public TokenRequestedTypeEnum getTokenRequestedType() { return tokenRequestedType; }
    @JsonProperty("TokenRequestedType")
    public void setTokenRequestedType(TokenRequestedTypeEnum value) { this.tokenRequestedType = value; }

    /**
     * When true, the entire session is for training/testing and no real transactions are
     * processed. Default false.
     */
    @JsonProperty("TrainingModeFlag")
    public Boolean getTrainingModeFlag() { return trainingModeFlag; }
    @JsonProperty("TrainingModeFlag")
    public void setTrainingModeFlag(Boolean value) { this.trainingModeFlag = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private CustomerOrderReqType[] customerOrderReq;
        private String dateTime;
        private String operatorID;
        private String operatorLanguage;
        private String poiSerialNumber;
        private SaleSoftware saleSoftware;
        private SaleTerminalData saleTerminalData;
        private String shiftNumber;
        private TokenRequestedTypeEnum tokenRequestedType;
        private Boolean trainingModeFlag;
        
        private Builder() {}
        
        public Builder customerOrderReq(CustomerOrderReqType[] customerOrderReq) {
            this.customerOrderReq = customerOrderReq;
            return this;
        }
        
        public Builder dateTime(String dateTime) {
            this.dateTime = dateTime;
            return this;
        }
        
        public Builder operatorID(String operatorID) {
            this.operatorID = operatorID;
            return this;
        }
        
        public Builder operatorLanguage(String operatorLanguage) {
            this.operatorLanguage = operatorLanguage;
            return this;
        }
        
        public Builder poiSerialNumber(String poiSerialNumber) {
            this.poiSerialNumber = poiSerialNumber;
            return this;
        }
        
        public Builder saleSoftware(SaleSoftware saleSoftware) {
            this.saleSoftware = saleSoftware;
            return this;
        }
        
        public Builder saleTerminalData(SaleTerminalData saleTerminalData) {
            this.saleTerminalData = saleTerminalData;
            return this;
        }
        
        public Builder shiftNumber(String shiftNumber) {
            this.shiftNumber = shiftNumber;
            return this;
        }
        
        public Builder tokenRequestedType(TokenRequestedTypeEnum tokenRequestedType) {
            this.tokenRequestedType = tokenRequestedType;
            return this;
        }
        
        public Builder trainingModeFlag(Boolean trainingModeFlag) {
            this.trainingModeFlag = trainingModeFlag;
            return this;
        }
        
        public LoginRequest build() {
            LoginRequest result = new LoginRequest();
            result.setCustomerOrderReq(this.customerOrderReq);
            result.setDateTime(this.dateTime);
            result.setOperatorID(this.operatorID);
            result.setOperatorLanguage(this.operatorLanguage);
            result.setPoiSerialNumber(this.poiSerialNumber);
            result.setSaleSoftware(this.saleSoftware);
            result.setSaleTerminalData(this.saleTerminalData);
            result.setShiftNumber(this.shiftNumber);
            result.setTokenRequestedType(this.tokenRequestedType);
            result.setTrainingModeFlag(this.trainingModeFlag);
            return result;
        }
    }
}
