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
 * Information related to the payment card used for the transaction, including
 * identification, entry mode, and optionally sensitive data.
 */
public class CardData {
    private AllowedProduct[] allowedProduct;
    private String[] allowedProductCode;
    private String cardCountryCode;
    private EntryModeType[] entryMode;
    private String maskedPAN;
    private String paymentAccountRef;
    private String paymentBrand;
    private PaymentToken paymentToken;
    private ContentInformationType protectedCardData;
    private SensitiveCardData sensitiveCardData;

    @JsonProperty("AllowedProduct")
    public AllowedProduct[] getAllowedProduct() { return allowedProduct; }
    @JsonProperty("AllowedProduct")
    public void setAllowedProduct(AllowedProduct[] value) { this.allowedProduct = value; }

    /**
     * Product codes payable by this card. Present when ErrorCondition is PaymentRestriction.
     */
    @JsonProperty("AllowedProductCode")
    public String[] getAllowedProductCode() { return allowedProductCode; }
    @JsonProperty("AllowedProductCode")
    public void setAllowedProductCode(String[] value) { this.allowedProductCode = value; }

    /**
     * 3-digit ISO 3166-1 country code attached to the card, used to determine local vs
     * international transactions.
     */
    @JsonProperty("CardCountryCode")
    public String getCardCountryCode() { return cardCountryCode; }
    @JsonProperty("CardCountryCode")
    public void setCardCountryCode(String value) { this.cardCountryCode = value; }

    @JsonProperty("EntryMode")
    public EntryModeType[] getEntryMode() { return entryMode; }
    @JsonProperty("EntryMode")
    public void setEntryMode(EntryModeType[] value) { this.entryMode = value; }

    /**
     * Partially masked PAN with '*' characters, used when SensitiveCardData is protected by
     * ProtectedCardData.
     */
    @JsonProperty("MaskedPAN")
    public String getMaskedPAN() { return maskedPAN; }
    @JsonProperty("MaskedPAN")
    public void setMaskedPAN(String value) { this.maskedPAN = value; }

    /**
     * Payment Account Reference (PAR) — identifies the PAN without being usable for a
     * transaction. Mandatory when available.
     */
    @JsonProperty("PaymentAccountRef")
    public String getPaymentAccountRef() { return paymentAccountRef; }
    @JsonProperty("PaymentAccountRef")
    public void setPaymentAccountRef(String value) { this.paymentAccountRef = value; }

    /**
     * Type/brand of the payment card (e.g. VISA, Mastercard). Mandatory when PAN is readable.
     */
    @JsonProperty("PaymentBrand")
    public String getPaymentBrand() { return paymentBrand; }
    @JsonProperty("PaymentBrand")
    public void setPaymentBrand(String value) { this.paymentBrand = value; }

    @JsonProperty("PaymentToken")
    public PaymentToken getPaymentToken() { return paymentToken; }
    @JsonProperty("PaymentToken")
    public void setPaymentToken(PaymentToken value) { this.paymentToken = value; }

    /**
     * CMS EnvelopedData containing the encrypted SensitiveCardData structure.
     */
    @JsonProperty("ProtectedCardData")
    public ContentInformationType getProtectedCardData() { return protectedCardData; }
    @JsonProperty("ProtectedCardData")
    public void setProtectedCardData(ContentInformationType value) { this.protectedCardData = value; }

    @JsonProperty("SensitiveCardData")
    public SensitiveCardData getSensitiveCardData() { return sensitiveCardData; }
    @JsonProperty("SensitiveCardData")
    public void setSensitiveCardData(SensitiveCardData value) { this.sensitiveCardData = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private AllowedProduct[] allowedProduct;
        private String[] allowedProductCode;
        private String cardCountryCode;
        private EntryModeType[] entryMode;
        private String maskedPAN;
        private String paymentAccountRef;
        private String paymentBrand;
        private PaymentToken paymentToken;
        private ContentInformationType protectedCardData;
        private SensitiveCardData sensitiveCardData;
        
        private Builder() {}
        
        public Builder allowedProduct(AllowedProduct[] allowedProduct) {
            this.allowedProduct = allowedProduct;
            return this;
        }
        
        public Builder allowedProductCode(String[] allowedProductCode) {
            this.allowedProductCode = allowedProductCode;
            return this;
        }
        
        public Builder cardCountryCode(String cardCountryCode) {
            this.cardCountryCode = cardCountryCode;
            return this;
        }
        
        public Builder entryMode(EntryModeType[] entryMode) {
            this.entryMode = entryMode;
            return this;
        }
        
        public Builder maskedPAN(String maskedPAN) {
            this.maskedPAN = maskedPAN;
            return this;
        }
        
        public Builder paymentAccountRef(String paymentAccountRef) {
            this.paymentAccountRef = paymentAccountRef;
            return this;
        }
        
        public Builder paymentBrand(String paymentBrand) {
            this.paymentBrand = paymentBrand;
            return this;
        }
        
        public Builder paymentToken(PaymentToken paymentToken) {
            this.paymentToken = paymentToken;
            return this;
        }
        
        public Builder protectedCardData(ContentInformationType protectedCardData) {
            this.protectedCardData = protectedCardData;
            return this;
        }
        
        public Builder sensitiveCardData(SensitiveCardData sensitiveCardData) {
            this.sensitiveCardData = sensitiveCardData;
            return this;
        }
        
        public CardData build() {
            CardData result = new CardData();
            result.setAllowedProduct(this.allowedProduct);
            result.setAllowedProductCode(this.allowedProductCode);
            result.setCardCountryCode(this.cardCountryCode);
            result.setEntryMode(this.entryMode);
            result.setMaskedPAN(this.maskedPAN);
            result.setPaymentAccountRef(this.paymentAccountRef);
            result.setPaymentBrand(this.paymentBrand);
            result.setPaymentToken(this.paymentToken);
            result.setProtectedCardData(this.protectedCardData);
            result.setSensitiveCardData(this.sensitiveCardData);
            return result;
        }
    }
}
