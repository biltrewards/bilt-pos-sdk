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
 * Content of the PIN Request message, used to request PIN entry, encryption, or
 * verification from the POI Terminal.
 */
public class PINRequest {
    private String additionalInput;
    private Boolean beepKeyFlag;
    private CardholderPIN cardholderPIN;
    private String keyReference;
    private Long maxWaitingTime;
    private String pinEncAlgorithm;
    private PINFormatEnum pinFormat;
    private PINRequestTypeEnum pinRequestType;
    private String pinVerifMethod;

    /**
     * Additional data required for PIN verification (e.g. part of PAN for ISO format 0).
     * Optional for PINEnter and PINVerify.
     */
    @JsonProperty("AdditionalInput")
    public String getAdditionalInput() { return additionalInput; }
    @JsonProperty("AdditionalInput")
    public void setAdditionalInput(String value) { this.additionalInput = value; }

    /**
     * When true, a beep is generated for each key pressed during PIN entry. Default false.
     */
    @JsonProperty("BeepKeyFlag")
    public Boolean getBEEPKeyFlag() { return beepKeyFlag; }
    @JsonProperty("BeepKeyFlag")
    public void setBEEPKeyFlag(Boolean value) { this.beepKeyFlag = value; }

    @JsonProperty("CardholderPIN")
    public CardholderPIN getCardholderPIN() { return cardholderPIN; }
    @JsonProperty("CardholderPIN")
    public void setCardholderPIN(CardholderPIN value) { this.cardholderPIN = value; }

    /**
     * Identifies the key to use to encrypt the PIN block. Optional for PINEnter.
     */
    @JsonProperty("KeyReference")
    public String getKeyReference() { return keyReference; }
    @JsonProperty("KeyReference")
    public void setKeyReference(String value) { this.keyReference = value; }

    /**
     * Maximum time in seconds to wait for PIN entry. Optional for PINEnter.
     */
    @JsonProperty("MaxWaitingTime")
    public Long getMaxWaitingTime() { return maxWaitingTime; }
    @JsonProperty("MaxWaitingTime")
    public void setMaxWaitingTime(Long value) { this.maxWaitingTime = value; }

    /**
     * Identifies the PIN block encryption algorithm. Optional for PINEnter.
     */
    @JsonProperty("PINEncAlgorithm")
    public String getPinEncAlgorithm() { return pinEncAlgorithm; }
    @JsonProperty("PINEncAlgorithm")
    public void setPinEncAlgorithm(String value) { this.pinEncAlgorithm = value; }

    @JsonProperty("PINFormat")
    public PINFormatEnum getPinFormat() { return pinFormat; }
    @JsonProperty("PINFormat")
    public void setPinFormat(PINFormatEnum value) { this.pinFormat = value; }

    @JsonProperty("PINRequestType")
    public PINRequestTypeEnum getPinRequestType() { return pinRequestType; }
    @JsonProperty("PINRequestType")
    public void setPinRequestType(PINRequestTypeEnum value) { this.pinRequestType = value; }

    /**
     * Identifies the PIN verification method and keys. Optional for PINVerify and PINVerifyOnly.
     */
    @JsonProperty("PINVerifMethod")
    public String getPinVerifMethod() { return pinVerifMethod; }
    @JsonProperty("PINVerifMethod")
    public void setPinVerifMethod(String value) { this.pinVerifMethod = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String additionalInput;
        private Boolean beepKeyFlag;
        private CardholderPIN cardholderPIN;
        private String keyReference;
        private Long maxWaitingTime;
        private String pinEncAlgorithm;
        private PINFormatEnum pinFormat;
        private PINRequestTypeEnum pinRequestType;
        private String pinVerifMethod;
        
        private Builder() {}
        
        public Builder additionalInput(String additionalInput) {
            this.additionalInput = additionalInput;
            return this;
        }
        
        public Builder beepKeyFlag(Boolean beepKeyFlag) {
            this.beepKeyFlag = beepKeyFlag;
            return this;
        }
        
        public Builder cardholderPIN(CardholderPIN cardholderPIN) {
            this.cardholderPIN = cardholderPIN;
            return this;
        }
        
        public Builder keyReference(String keyReference) {
            this.keyReference = keyReference;
            return this;
        }
        
        public Builder maxWaitingTime(Long maxWaitingTime) {
            this.maxWaitingTime = maxWaitingTime;
            return this;
        }
        
        public Builder pinEncAlgorithm(String pinEncAlgorithm) {
            this.pinEncAlgorithm = pinEncAlgorithm;
            return this;
        }
        
        public Builder pinFormat(PINFormatEnum pinFormat) {
            this.pinFormat = pinFormat;
            return this;
        }
        
        public Builder pinRequestType(PINRequestTypeEnum pinRequestType) {
            this.pinRequestType = pinRequestType;
            return this;
        }
        
        public Builder pinVerifMethod(String pinVerifMethod) {
            this.pinVerifMethod = pinVerifMethod;
            return this;
        }
        
        public PINRequest build() {
            PINRequest result = new PINRequest();
            result.setAdditionalInput(this.additionalInput);
            result.setBEEPKeyFlag(this.beepKeyFlag);
            result.setCardholderPIN(this.cardholderPIN);
            result.setKeyReference(this.keyReference);
            result.setMaxWaitingTime(this.maxWaitingTime);
            result.setPinEncAlgorithm(this.pinEncAlgorithm);
            result.setPinFormat(this.pinFormat);
            result.setPinRequestType(this.pinRequestType);
            result.setPinVerifMethod(this.pinVerifMethod);
            return result;
        }
    }
}
