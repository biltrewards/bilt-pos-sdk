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
 * Operational status of a POI Terminal and its hardware components.
 */
public class POIStatus {
    private Boolean cardReaderOKFlag;
    private CashHandlingDevice[] cashHandlingDevice;
    private Boolean communicationOKFlag;
    private Boolean fraudPreventionFlag;
    private GlobalStatusEnum globalStatus;
    private Boolean pedokFlag;
    private PrinterStatusEnum printerStatus;
    private Boolean securityOKFlag;

    /**
     * When true, the card reader is operational. Absent if no card reader is present.
     */
    @JsonProperty("CardReaderOKFlag")
    public Boolean getCardReaderOKFlag() { return cardReaderOKFlag; }
    @JsonProperty("CardReaderOKFlag")
    public void setCardReaderOKFlag(Boolean value) { this.cardReaderOKFlag = value; }

    @JsonProperty("CashHandlingDevice")
    public CashHandlingDevice[] getCashHandlingDevice() { return cashHandlingDevice; }
    @JsonProperty("CashHandlingDevice")
    public void setCashHandlingDevice(CashHandlingDevice[] value) { this.cashHandlingDevice = value; }

    /**
     * When true, the communication infrastructure is operational. Absent if no communication
     * module is present.
     */
    @JsonProperty("CommunicationOKFlag")
    public Boolean getCommunicationOKFlag() { return communicationOKFlag; }
    @JsonProperty("CommunicationOKFlag")
    public void setCommunicationOKFlag(Boolean value) { this.communicationOKFlag = value; }

    /**
     * When true, the POI has detected a fraud suspicion (e.g. unexpected reboot). Default false.
     */
    @JsonProperty("FraudPreventionFlag")
    public Boolean getFraudPreventionFlag() { return fraudPreventionFlag; }
    @JsonProperty("FraudPreventionFlag")
    public void setFraudPreventionFlag(Boolean value) { this.fraudPreventionFlag = value; }

    @JsonProperty("GlobalStatus")
    public GlobalStatusEnum getGlobalStatus() { return globalStatus; }
    @JsonProperty("GlobalStatus")
    public void setGlobalStatus(GlobalStatusEnum value) { this.globalStatus = value; }

    /**
     * When true, the PIN Entry Device is operational. Absent if no PED is present.
     */
    @JsonProperty("PEDOKFlag")
    public Boolean getPedokFlag() { return pedokFlag; }
    @JsonProperty("PEDOKFlag")
    public void setPedokFlag(Boolean value) { this.pedokFlag = value; }

    @JsonProperty("PrinterStatus")
    public PrinterStatusEnum getPrinterStatus() { return printerStatus; }
    @JsonProperty("PrinterStatus")
    public void setPrinterStatus(PrinterStatusEnum value) { this.printerStatus = value; }

    /**
     * When true, the security module is operational. Absent if no security module is present.
     */
    @JsonProperty("SecurityOKFlag")
    public Boolean getSecurityOKFlag() { return securityOKFlag; }
    @JsonProperty("SecurityOKFlag")
    public void setSecurityOKFlag(Boolean value) { this.securityOKFlag = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private Boolean cardReaderOKFlag;
        private CashHandlingDevice[] cashHandlingDevice;
        private Boolean communicationOKFlag;
        private Boolean fraudPreventionFlag;
        private GlobalStatusEnum globalStatus;
        private Boolean pedokFlag;
        private PrinterStatusEnum printerStatus;
        private Boolean securityOKFlag;
        
        private Builder() {}
        
        public Builder cardReaderOKFlag(Boolean cardReaderOKFlag) {
            this.cardReaderOKFlag = cardReaderOKFlag;
            return this;
        }
        
        public Builder cashHandlingDevice(CashHandlingDevice[] cashHandlingDevice) {
            this.cashHandlingDevice = cashHandlingDevice;
            return this;
        }
        
        public Builder communicationOKFlag(Boolean communicationOKFlag) {
            this.communicationOKFlag = communicationOKFlag;
            return this;
        }
        
        public Builder fraudPreventionFlag(Boolean fraudPreventionFlag) {
            this.fraudPreventionFlag = fraudPreventionFlag;
            return this;
        }
        
        public Builder globalStatus(GlobalStatusEnum globalStatus) {
            this.globalStatus = globalStatus;
            return this;
        }
        
        public Builder pedokFlag(Boolean pedokFlag) {
            this.pedokFlag = pedokFlag;
            return this;
        }
        
        public Builder printerStatus(PrinterStatusEnum printerStatus) {
            this.printerStatus = printerStatus;
            return this;
        }
        
        public Builder securityOKFlag(Boolean securityOKFlag) {
            this.securityOKFlag = securityOKFlag;
            return this;
        }
        
        public POIStatus build() {
            POIStatus result = new POIStatus();
            result.setCardReaderOKFlag(this.cardReaderOKFlag);
            result.setCashHandlingDevice(this.cashHandlingDevice);
            result.setCommunicationOKFlag(this.communicationOKFlag);
            result.setFraudPreventionFlag(this.fraudPreventionFlag);
            result.setGlobalStatus(this.globalStatus);
            result.setPedokFlag(this.pedokFlag);
            result.setPrinterStatus(this.printerStatus);
            result.setSecurityOKFlag(this.securityOKFlag);
            return result;
        }
    }
}
