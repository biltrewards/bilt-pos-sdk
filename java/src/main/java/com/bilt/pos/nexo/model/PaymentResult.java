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
 * Result of a processed payment transaction, including instrument data, amounts,
 * authentication, and acquirer information.
 */
public class PaymentResult {
    private AmountsResp amountsResp;
    private AuthenticationMethodType[] authenticationMethod;
    private CapturedSignature capturedSignature;
    private CurrencyConversion[] currencyConversion;
    private String customerLanguage;
    private Instalment instalment;
    private Boolean merchantOverrideFlag;
    private Boolean onlineFlag;
    private PaymentAcquirerData paymentAcquirerData;
    private PaymentInstrumentData paymentInstrumentData;
    private PaymentTypeEnum paymentType;
    private ContentInformationType protectedSignature;
    private String validityDate;

    @JsonProperty("AmountsResp")
    public AmountsResp getAmountsResp() { return amountsResp; }
    @JsonProperty("AmountsResp")
    public void setAmountsResp(AmountsResp value) { this.amountsResp = value; }

    @JsonProperty("AuthenticationMethod")
    public AuthenticationMethodType[] getAuthenticationMethod() { return authenticationMethod; }
    @JsonProperty("AuthenticationMethod")
    public void setAuthenticationMethod(AuthenticationMethodType[] value) { this.authenticationMethod = value; }

    @JsonProperty("CapturedSignature")
    public CapturedSignature getCapturedSignature() { return capturedSignature; }
    @JsonProperty("CapturedSignature")
    public void setCapturedSignature(CapturedSignature value) { this.capturedSignature = value; }

    @JsonProperty("CurrencyConversion")
    public CurrencyConversion[] getCurrencyConversion() { return currencyConversion; }
    @JsonProperty("CurrencyConversion")
    public void setCurrencyConversion(CurrencyConversion[] value) { this.currencyConversion = value; }

    @JsonProperty("CustomerLanguage")
    public String getCustomerLanguage() { return customerLanguage; }
    @JsonProperty("CustomerLanguage")
    public void setCustomerLanguage(String value) { this.customerLanguage = value; }

    @JsonProperty("Instalment")
    public Instalment getInstalment() { return instalment; }
    @JsonProperty("Instalment")
    public void setInstalment(Instalment value) { this.instalment = value; }

    /**
     * When true, the Merchant forced the transaction to be accepted (e.g. via SiteManager
     * confirmation). Default false.
     */
    @JsonProperty("MerchantOverrideFlag")
    public Boolean getMerchantOverrideFlag() { return merchantOverrideFlag; }
    @JsonProperty("MerchantOverrideFlag")
    public void setMerchantOverrideFlag(Boolean value) { this.merchantOverrideFlag = value; }

    /**
     * When true, the transaction required online approval from a host. Default true.
     */
    @JsonProperty("OnlineFlag")
    public Boolean getOnlineFlag() { return onlineFlag; }
    @JsonProperty("OnlineFlag")
    public void setOnlineFlag(Boolean value) { this.onlineFlag = value; }

    @JsonProperty("PaymentAcquirerData")
    public PaymentAcquirerData getPaymentAcquirerData() { return paymentAcquirerData; }
    @JsonProperty("PaymentAcquirerData")
    public void setPaymentAcquirerData(PaymentAcquirerData value) { this.paymentAcquirerData = value; }

    @JsonProperty("PaymentInstrumentData")
    public PaymentInstrumentData getPaymentInstrumentData() { return paymentInstrumentData; }
    @JsonProperty("PaymentInstrumentData")
    public void setPaymentInstrumentData(PaymentInstrumentData value) { this.paymentInstrumentData = value; }

    @JsonProperty("PaymentType")
    public PaymentTypeEnum getPaymentType() { return paymentType; }
    @JsonProperty("PaymentType")
    public void setPaymentType(PaymentTypeEnum value) { this.paymentType = value; }

    /**
     * CMS-encrypted handwritten signature captured on the POI.
     */
    @JsonProperty("ProtectedSignature")
    public ContentInformationType getProtectedSignature() { return protectedSignature; }
    @JsonProperty("ProtectedSignature")
    public void setProtectedSignature(ContentInformationType value) { this.protectedSignature = value; }

    /**
     * End of the validity period for a reservation (OneTimeReservation, FirstReservation,
     * UpdateReservation).
     */
    @JsonProperty("ValidityDate")
    public String getValidityDate() { return validityDate; }
    @JsonProperty("ValidityDate")
    public void setValidityDate(String value) { this.validityDate = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private AmountsResp amountsResp;
        private AuthenticationMethodType[] authenticationMethod;
        private CapturedSignature capturedSignature;
        private CurrencyConversion[] currencyConversion;
        private String customerLanguage;
        private Instalment instalment;
        private Boolean merchantOverrideFlag;
        private Boolean onlineFlag;
        private PaymentAcquirerData paymentAcquirerData;
        private PaymentInstrumentData paymentInstrumentData;
        private PaymentTypeEnum paymentType;
        private ContentInformationType protectedSignature;
        private String validityDate;
        
        private Builder() {}
        
        public Builder amountsResp(AmountsResp amountsResp) {
            this.amountsResp = amountsResp;
            return this;
        }
        
        public Builder authenticationMethod(AuthenticationMethodType[] authenticationMethod) {
            this.authenticationMethod = authenticationMethod;
            return this;
        }
        
        public Builder capturedSignature(CapturedSignature capturedSignature) {
            this.capturedSignature = capturedSignature;
            return this;
        }
        
        public Builder currencyConversion(CurrencyConversion[] currencyConversion) {
            this.currencyConversion = currencyConversion;
            return this;
        }
        
        public Builder customerLanguage(String customerLanguage) {
            this.customerLanguage = customerLanguage;
            return this;
        }
        
        public Builder instalment(Instalment instalment) {
            this.instalment = instalment;
            return this;
        }
        
        public Builder merchantOverrideFlag(Boolean merchantOverrideFlag) {
            this.merchantOverrideFlag = merchantOverrideFlag;
            return this;
        }
        
        public Builder onlineFlag(Boolean onlineFlag) {
            this.onlineFlag = onlineFlag;
            return this;
        }
        
        public Builder paymentAcquirerData(PaymentAcquirerData paymentAcquirerData) {
            this.paymentAcquirerData = paymentAcquirerData;
            return this;
        }
        
        public Builder paymentInstrumentData(PaymentInstrumentData paymentInstrumentData) {
            this.paymentInstrumentData = paymentInstrumentData;
            return this;
        }
        
        public Builder paymentType(PaymentTypeEnum paymentType) {
            this.paymentType = paymentType;
            return this;
        }
        
        public Builder protectedSignature(ContentInformationType protectedSignature) {
            this.protectedSignature = protectedSignature;
            return this;
        }
        
        public Builder validityDate(String validityDate) {
            this.validityDate = validityDate;
            return this;
        }
        
        public PaymentResult build() {
            PaymentResult result = new PaymentResult();
            result.setAmountsResp(this.amountsResp);
            result.setAuthenticationMethod(this.authenticationMethod);
            result.setCapturedSignature(this.capturedSignature);
            result.setCurrencyConversion(this.currencyConversion);
            result.setCustomerLanguage(this.customerLanguage);
            result.setInstalment(this.instalment);
            result.setMerchantOverrideFlag(this.merchantOverrideFlag);
            result.setOnlineFlag(this.onlineFlag);
            result.setPaymentAcquirerData(this.paymentAcquirerData);
            result.setPaymentInstrumentData(this.paymentInstrumentData);
            result.setPaymentType(this.paymentType);
            result.setProtectedSignature(this.protectedSignature);
            result.setValidityDate(this.validityDate);
            return result;
        }
    }
}
