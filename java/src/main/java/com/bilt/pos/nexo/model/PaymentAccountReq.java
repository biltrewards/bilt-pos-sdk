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
 * Data related to the payment account for which a balance inquiry is requested.
 */
public class PaymentAccountReq {
    private AccountTypeEnum accountType;
    private TransactionIdentificationType cardAcquisitionReference;
    private PaymentInstrumentData paymentInstrumentData;

    @JsonProperty("AccountType")
    public AccountTypeEnum getAccountType() { return accountType; }
    @JsonProperty("AccountType")
    public void setAccountType(AccountTypeEnum value) { this.accountType = value; }

    @JsonProperty("CardAcquisitionReference")
    public TransactionIdentificationType getCardAcquisitionReference() { return cardAcquisitionReference; }
    @JsonProperty("CardAcquisitionReference")
    public void setCardAcquisitionReference(TransactionIdentificationType value) { this.cardAcquisitionReference = value; }

    @JsonProperty("PaymentInstrumentData")
    public PaymentInstrumentData getPaymentInstrumentData() { return paymentInstrumentData; }
    @JsonProperty("PaymentInstrumentData")
    public void setPaymentInstrumentData(PaymentInstrumentData value) { this.paymentInstrumentData = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private AccountTypeEnum accountType;
        private TransactionIdentificationType cardAcquisitionReference;
        private PaymentInstrumentData paymentInstrumentData;
        
        private Builder() {}
        
        public Builder accountType(AccountTypeEnum accountType) {
            this.accountType = accountType;
            return this;
        }
        
        public Builder cardAcquisitionReference(TransactionIdentificationType cardAcquisitionReference) {
            this.cardAcquisitionReference = cardAcquisitionReference;
            return this;
        }
        
        public Builder paymentInstrumentData(PaymentInstrumentData paymentInstrumentData) {
            this.paymentInstrumentData = paymentInstrumentData;
            return this;
        }
        
        public PaymentAccountReq build() {
            PaymentAccountReq result = new PaymentAccountReq();
            result.setAccountType(this.accountType);
            result.setCardAcquisitionReference(this.cardAcquisitionReference);
            result.setPaymentInstrumentData(this.paymentInstrumentData);
            return result;
        }
    }
}
