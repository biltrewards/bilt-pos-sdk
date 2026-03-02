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
 * Result of a payment account balance inquiry, including instrument data, balance,
 * currency, and acquirer information.
 */
public class PaymentAccountStatus {
    private String currency;
    private Double currentBalance;
    private PaymentAcquirerData paymentAcquirerData;
    private PaymentInstrumentData paymentInstrumentData;

    @JsonProperty("Currency")
    public String getCurrency() { return currency; }
    @JsonProperty("Currency")
    public void setCurrency(String value) { this.currency = value; }

    /**
     * Current balance of the payment account.
     */
    @JsonProperty("CurrentBalance")
    public Double getCurrentBalance() { return currentBalance; }
    @JsonProperty("CurrentBalance")
    public void setCurrentBalance(Double value) { this.currentBalance = value; }

    @JsonProperty("PaymentAcquirerData")
    public PaymentAcquirerData getPaymentAcquirerData() { return paymentAcquirerData; }
    @JsonProperty("PaymentAcquirerData")
    public void setPaymentAcquirerData(PaymentAcquirerData value) { this.paymentAcquirerData = value; }

    @JsonProperty("PaymentInstrumentData")
    public PaymentInstrumentData getPaymentInstrumentData() { return paymentInstrumentData; }
    @JsonProperty("PaymentInstrumentData")
    public void setPaymentInstrumentData(PaymentInstrumentData value) { this.paymentInstrumentData = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String currency;
        private Double currentBalance;
        private PaymentAcquirerData paymentAcquirerData;
        private PaymentInstrumentData paymentInstrumentData;
        
        private Builder() {}
        
        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }
        
        public Builder currentBalance(Double currentBalance) {
            this.currentBalance = currentBalance;
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
        
        public PaymentAccountStatus build() {
            PaymentAccountStatus result = new PaymentAccountStatus();
            result.setCurrency(this.currency);
            result.setCurrentBalance(this.currentBalance);
            result.setPaymentAcquirerData(this.paymentAcquirerData);
            result.setPaymentInstrumentData(this.paymentInstrumentData);
            return result;
        }
    }
}
