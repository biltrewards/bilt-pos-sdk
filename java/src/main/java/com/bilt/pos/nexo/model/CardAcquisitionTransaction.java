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
 * Data related to the card acquisition transaction, defining conditions and restrictions
 * for card reading.
 */
public class CardAcquisitionTransaction {
    private String[] allowedLoyaltyBrand;
    private String[] allowedPaymentBrand;
    private Boolean cashBackFlag;
    private String customerLanguage;
    private Boolean forceCustomerSelectionFlag;
    private ForceEntryModeType[] forceEntryMode;
    private LoyaltyHandlingEnum loyaltyHandling;
    private PaymentTypeEnum paymentType;
    private Double totalAmount;

    /**
     * Loyalty brands allowed for this card acquisition.
     */
    @JsonProperty("AllowedLoyaltyBrand")
    public String[] getAllowedLoyaltyBrand() { return allowedLoyaltyBrand; }
    @JsonProperty("AllowedLoyaltyBrand")
    public void setAllowedLoyaltyBrand(String[] value) { this.allowedLoyaltyBrand = value; }

    /**
     * Payment brands allowed for this card acquisition.
     */
    @JsonProperty("AllowedPaymentBrand")
    public String[] getAllowedPaymentBrand() { return allowedPaymentBrand; }
    @JsonProperty("AllowedPaymentBrand")
    public void setAllowedPaymentBrand(String[] value) { this.allowedPaymentBrand = value; }

    /**
     * For contactless cards, true if cash back was requested. Default false.
     */
    @JsonProperty("CashBackFlag")
    public Boolean getCashBackFlag() { return cashBackFlag; }
    @JsonProperty("CashBackFlag")
    public void setCashBackFlag(Boolean value) { this.cashBackFlag = value; }

    @JsonProperty("CustomerLanguage")
    public String getCustomerLanguage() { return customerLanguage; }
    @JsonProperty("CustomerLanguage")
    public void setCustomerLanguage(String value) { this.customerLanguage = value; }

    /**
     * When true, the customer must select the card application on a multi-application
     * smartcard. Default false.
     */
    @JsonProperty("ForceCustomerSelectionFlag")
    public Boolean getForceCustomerSelectionFlag() { return forceCustomerSelectionFlag; }
    @JsonProperty("ForceCustomerSelectionFlag")
    public void setForceCustomerSelectionFlag(Boolean value) { this.forceCustomerSelectionFlag = value; }

    @JsonProperty("ForceEntryMode")
    public ForceEntryModeType[] getForceEntryMode() { return forceEntryMode; }
    @JsonProperty("ForceEntryMode")
    public void setForceEntryMode(ForceEntryModeType[] value) { this.forceEntryMode = value; }

    @JsonProperty("LoyaltyHandling")
    public LoyaltyHandlingEnum getLoyaltyHandling() { return loyaltyHandling; }
    @JsonProperty("LoyaltyHandling")
    public void setLoyaltyHandling(LoyaltyHandlingEnum value) { this.loyaltyHandling = value; }

    /**
     * Type of payment service, mandatory for contactless card processing.
     */
    @JsonProperty("PaymentType")
    public PaymentTypeEnum getPaymentType() { return paymentType; }
    @JsonProperty("PaymentType")
    public void setPaymentType(PaymentTypeEnum value) { this.paymentType = value; }

    /**
     * Transaction amount, mandatory for contactless card processing.
     */
    @JsonProperty("TotalAmount")
    public Double getTotalAmount() { return totalAmount; }
    @JsonProperty("TotalAmount")
    public void setTotalAmount(Double value) { this.totalAmount = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String[] allowedLoyaltyBrand;
        private String[] allowedPaymentBrand;
        private Boolean cashBackFlag;
        private String customerLanguage;
        private Boolean forceCustomerSelectionFlag;
        private ForceEntryModeType[] forceEntryMode;
        private LoyaltyHandlingEnum loyaltyHandling;
        private PaymentTypeEnum paymentType;
        private Double totalAmount;
        
        private Builder() {}
        
        public Builder allowedLoyaltyBrand(String[] allowedLoyaltyBrand) {
            this.allowedLoyaltyBrand = allowedLoyaltyBrand;
            return this;
        }
        
        public Builder allowedPaymentBrand(String[] allowedPaymentBrand) {
            this.allowedPaymentBrand = allowedPaymentBrand;
            return this;
        }
        
        public Builder cashBackFlag(Boolean cashBackFlag) {
            this.cashBackFlag = cashBackFlag;
            return this;
        }
        
        public Builder customerLanguage(String customerLanguage) {
            this.customerLanguage = customerLanguage;
            return this;
        }
        
        public Builder forceCustomerSelectionFlag(Boolean forceCustomerSelectionFlag) {
            this.forceCustomerSelectionFlag = forceCustomerSelectionFlag;
            return this;
        }
        
        public Builder forceEntryMode(ForceEntryModeType[] forceEntryMode) {
            this.forceEntryMode = forceEntryMode;
            return this;
        }
        
        public Builder loyaltyHandling(LoyaltyHandlingEnum loyaltyHandling) {
            this.loyaltyHandling = loyaltyHandling;
            return this;
        }
        
        public Builder paymentType(PaymentTypeEnum paymentType) {
            this.paymentType = paymentType;
            return this;
        }
        
        public Builder totalAmount(Double totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }
        
        public CardAcquisitionTransaction build() {
            CardAcquisitionTransaction result = new CardAcquisitionTransaction();
            result.setAllowedLoyaltyBrand(this.allowedLoyaltyBrand);
            result.setAllowedPaymentBrand(this.allowedPaymentBrand);
            result.setCashBackFlag(this.cashBackFlag);
            result.setCustomerLanguage(this.customerLanguage);
            result.setForceCustomerSelectionFlag(this.forceCustomerSelectionFlag);
            result.setForceEntryMode(this.forceEntryMode);
            result.setLoyaltyHandling(this.loyaltyHandling);
            result.setPaymentType(this.paymentType);
            result.setTotalAmount(this.totalAmount);
            return result;
        }
    }
}
