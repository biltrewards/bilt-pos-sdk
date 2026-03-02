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
 * Conditions and restrictions on the payment or loyalty transaction requested by the Sale
 * System.
 */
public class TransactionConditions {
    private String[] acquirerID;
    private String[] allowedLoyaltyBrand;
    private String[] allowedPaymentBrand;
    private String customerLanguage;
    private Boolean debitPreferredFlag;
    private ForceEntryModeType[] forceEntryMode;
    private Boolean forceOnlineFlag;
    private LoyaltyHandlingEnum loyaltyHandling;
    private String merchantCategoryCode;

    /**
     * Preferred Acquirers for this transaction. The POI must use one of these if present.
     */
    @JsonProperty("AcquirerID")
    public String[] getAcquirerID() { return acquirerID; }
    @JsonProperty("AcquirerID")
    public void setAcquirerID(String[] value) { this.acquirerID = value; }

    /**
     * Loyalty brands allowed for this transaction. Restricts the loyalty brand if present.
     */
    @JsonProperty("AllowedLoyaltyBrand")
    public String[] getAllowedLoyaltyBrand() { return allowedLoyaltyBrand; }
    @JsonProperty("AllowedLoyaltyBrand")
    public void setAllowedLoyaltyBrand(String[] value) { this.allowedLoyaltyBrand = value; }

    /**
     * Payment card brands allowed for this transaction. Restricts the brand if present.
     */
    @JsonProperty("AllowedPaymentBrand")
    public String[] getAllowedPaymentBrand() { return allowedPaymentBrand; }
    @JsonProperty("AllowedPaymentBrand")
    public void setAllowedPaymentBrand(String[] value) { this.allowedPaymentBrand = value; }

    @JsonProperty("CustomerLanguage")
    public String getCustomerLanguage() { return customerLanguage; }
    @JsonProperty("CustomerLanguage")
    public void setCustomerLanguage(String value) { this.customerLanguage = value; }

    /**
     * When true, a debit transaction is preferred over a credit transaction. Default false.
     */
    @JsonProperty("DebitPreferredFlag")
    public Boolean getDebitPreferredFlag() { return debitPreferredFlag; }
    @JsonProperty("DebitPreferredFlag")
    public void setDebitPreferredFlag(Boolean value) { this.debitPreferredFlag = value; }

    @JsonProperty("ForceEntryMode")
    public ForceEntryModeType[] getForceEntryMode() { return forceEntryMode; }
    @JsonProperty("ForceEntryMode")
    public void setForceEntryMode(ForceEntryModeType[] value) { this.forceEntryMode = value; }

    /**
     * When true, forces the POI to go online for authorisation regardless of card rules.
     * Default false.
     */
    @JsonProperty("ForceOnlineFlag")
    public Boolean getForceOnlineFlag() { return forceOnlineFlag; }
    @JsonProperty("ForceOnlineFlag")
    public void setForceOnlineFlag(Boolean value) { this.forceOnlineFlag = value; }

    @JsonProperty("LoyaltyHandling")
    public LoyaltyHandlingEnum getLoyaltyHandling() { return loyaltyHandling; }
    @JsonProperty("LoyaltyHandling")
    public void setLoyaltyHandling(LoyaltyHandlingEnum value) { this.loyaltyHandling = value; }

    /**
     * ISO 18245 Merchant Category Code when the Sale Terminal has multiple MCCs for different
     * goods/services.
     */
    @JsonProperty("MerchantCategoryCode")
    public String getMerchantCategoryCode() { return merchantCategoryCode; }
    @JsonProperty("MerchantCategoryCode")
    public void setMerchantCategoryCode(String value) { this.merchantCategoryCode = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String[] acquirerID;
        private String[] allowedLoyaltyBrand;
        private String[] allowedPaymentBrand;
        private String customerLanguage;
        private Boolean debitPreferredFlag;
        private ForceEntryModeType[] forceEntryMode;
        private Boolean forceOnlineFlag;
        private LoyaltyHandlingEnum loyaltyHandling;
        private String merchantCategoryCode;
        
        private Builder() {}
        
        public Builder acquirerID(String[] acquirerID) {
            this.acquirerID = acquirerID;
            return this;
        }
        
        public Builder allowedLoyaltyBrand(String[] allowedLoyaltyBrand) {
            this.allowedLoyaltyBrand = allowedLoyaltyBrand;
            return this;
        }
        
        public Builder allowedPaymentBrand(String[] allowedPaymentBrand) {
            this.allowedPaymentBrand = allowedPaymentBrand;
            return this;
        }
        
        public Builder customerLanguage(String customerLanguage) {
            this.customerLanguage = customerLanguage;
            return this;
        }
        
        public Builder debitPreferredFlag(Boolean debitPreferredFlag) {
            this.debitPreferredFlag = debitPreferredFlag;
            return this;
        }
        
        public Builder forceEntryMode(ForceEntryModeType[] forceEntryMode) {
            this.forceEntryMode = forceEntryMode;
            return this;
        }
        
        public Builder forceOnlineFlag(Boolean forceOnlineFlag) {
            this.forceOnlineFlag = forceOnlineFlag;
            return this;
        }
        
        public Builder loyaltyHandling(LoyaltyHandlingEnum loyaltyHandling) {
            this.loyaltyHandling = loyaltyHandling;
            return this;
        }
        
        public Builder merchantCategoryCode(String merchantCategoryCode) {
            this.merchantCategoryCode = merchantCategoryCode;
            return this;
        }
        
        public TransactionConditions build() {
            TransactionConditions result = new TransactionConditions();
            result.setAcquirerID(this.acquirerID);
            result.setAllowedLoyaltyBrand(this.allowedLoyaltyBrand);
            result.setAllowedPaymentBrand(this.allowedPaymentBrand);
            result.setCustomerLanguage(this.customerLanguage);
            result.setDebitPreferredFlag(this.debitPreferredFlag);
            result.setForceEntryMode(this.forceEntryMode);
            result.setForceOnlineFlag(this.forceOnlineFlag);
            result.setLoyaltyHandling(this.loyaltyHandling);
            result.setMerchantCategoryCode(this.merchantCategoryCode);
            return result;
        }
    }
}
