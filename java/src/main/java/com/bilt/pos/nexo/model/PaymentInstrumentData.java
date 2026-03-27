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
 * Data related to the instrument of payment for the transaction (card, check, mobile,
 * stored value, or cash).
 */
public class PaymentInstrumentData {
    private CardData cardData;
    private CheckData checkData;
    private MobileData mobileData;
    private PaymentInstrumentTypeEnum paymentInstrumentType;
    private StoredValueAccountID storedValueAccountID;

    @JsonProperty("CardData")
    public CardData getCardData() { return cardData; }
    @JsonProperty("CardData")
    public void setCardData(CardData value) { this.cardData = value; }

    @JsonProperty("CheckData")
    public CheckData getCheckData() { return checkData; }
    @JsonProperty("CheckData")
    public void setCheckData(CheckData value) { this.checkData = value; }

    @JsonProperty("MobileData")
    public MobileData getMobileData() { return mobileData; }
    @JsonProperty("MobileData")
    public void setMobileData(MobileData value) { this.mobileData = value; }

    @JsonProperty("PaymentInstrumentType")
    public PaymentInstrumentTypeEnum getPaymentInstrumentType() { return paymentInstrumentType; }
    @JsonProperty("PaymentInstrumentType")
    public void setPaymentInstrumentType(PaymentInstrumentTypeEnum value) { this.paymentInstrumentType = value; }

    @JsonProperty("StoredValueAccountID")
    public StoredValueAccountID getStoredValueAccountID() { return storedValueAccountID; }
    @JsonProperty("StoredValueAccountID")
    public void setStoredValueAccountID(StoredValueAccountID value) { this.storedValueAccountID = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private CardData cardData;
        private CheckData checkData;
        private MobileData mobileData;
        private PaymentInstrumentTypeEnum paymentInstrumentType;
        private StoredValueAccountID storedValueAccountID;
        
        private Builder() {}
        
        public Builder cardData(CardData cardData) {
            this.cardData = cardData;
            return this;
        }
        
        public Builder checkData(CheckData checkData) {
            this.checkData = checkData;
            return this;
        }
        
        public Builder mobileData(MobileData mobileData) {
            this.mobileData = mobileData;
            return this;
        }
        
        public Builder paymentInstrumentType(PaymentInstrumentTypeEnum paymentInstrumentType) {
            this.paymentInstrumentType = paymentInstrumentType;
            return this;
        }
        
        public Builder storedValueAccountID(StoredValueAccountID storedValueAccountID) {
            this.storedValueAccountID = storedValueAccountID;
            return this;
        }
        
        public PaymentInstrumentData build() {
            PaymentInstrumentData result = new PaymentInstrumentData();
            result.setCardData(this.cardData);
            result.setCheckData(this.checkData);
            result.setMobileData(this.mobileData);
            result.setPaymentInstrumentType(this.paymentInstrumentType);
            result.setStoredValueAccountID(this.storedValueAccountID);
            return result;
        }
    }
}
