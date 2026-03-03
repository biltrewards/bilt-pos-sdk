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
 * Data specific to the payment transaction (as opposed to the loyalty part), including
 * payment type, split payment, and card data.
 */
public class PaymentData {
    private TransactionIdentificationType cardAcquisitionReference;
    private CustomerOrder customerOrder;
    private Instalment instalment;
    private PaymentInstrumentData paymentInstrumentData;
    private PaymentTypeEnum paymentType;
    private String requestedValidityDate;
    private Boolean splitPaymentFlag;

    /**
     * Reference to a previous CardAcquisition transaction to reuse its card data for this
     * payment.
     */
    @JsonProperty("CardAcquisitionReference")
    public TransactionIdentificationType getCardAcquisitionReference() { return cardAcquisitionReference; }
    @JsonProperty("CardAcquisitionReference")
    public void setCardAcquisitionReference(TransactionIdentificationType value) { this.cardAcquisitionReference = value; }

    @JsonProperty("CustomerOrder")
    public CustomerOrder getCustomerOrder() { return customerOrder; }
    @JsonProperty("CustomerOrder")
    public void setCustomerOrder(CustomerOrder value) { this.customerOrder = value; }

    @JsonProperty("Instalment")
    public Instalment getInstalment() { return instalment; }
    @JsonProperty("Instalment")
    public void setInstalment(Instalment value) { this.instalment = value; }

    @JsonProperty("PaymentInstrumentData")
    public PaymentInstrumentData getPaymentInstrumentData() { return paymentInstrumentData; }
    @JsonProperty("PaymentInstrumentData")
    public void setPaymentInstrumentData(PaymentInstrumentData value) { this.paymentInstrumentData = value; }

    @JsonProperty("PaymentType")
    public PaymentTypeEnum getPaymentType() { return paymentType; }
    @JsonProperty("PaymentType")
    public void setPaymentType(PaymentTypeEnum value) { this.paymentType = value; }

    /**
     * Requested validity date for a OneTimeReservation, FirstReservation, or UpdateReservation.
     */
    @JsonProperty("RequestedValidityDate")
    public String getRequestedValidityDate() { return requestedValidityDate; }
    @JsonProperty("RequestedValidityDate")
    public void setRequestedValidityDate(String value) { this.requestedValidityDate = value; }

    /**
     * When true, this payment is part of a split payment where the Sale transaction total is
     * paid in multiple transactions. Default false.
     */
    @JsonProperty("SplitPaymentFlag")
    public Boolean getSplitPaymentFlag() { return splitPaymentFlag; }
    @JsonProperty("SplitPaymentFlag")
    public void setSplitPaymentFlag(Boolean value) { this.splitPaymentFlag = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private TransactionIdentificationType cardAcquisitionReference;
        private CustomerOrder customerOrder;
        private Instalment instalment;
        private PaymentInstrumentData paymentInstrumentData;
        private PaymentTypeEnum paymentType;
        private String requestedValidityDate;
        private Boolean splitPaymentFlag;
        
        private Builder() {}
        
        public Builder cardAcquisitionReference(TransactionIdentificationType cardAcquisitionReference) {
            this.cardAcquisitionReference = cardAcquisitionReference;
            return this;
        }
        
        public Builder customerOrder(CustomerOrder customerOrder) {
            this.customerOrder = customerOrder;
            return this;
        }
        
        public Builder instalment(Instalment instalment) {
            this.instalment = instalment;
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
        
        public Builder requestedValidityDate(String requestedValidityDate) {
            this.requestedValidityDate = requestedValidityDate;
            return this;
        }
        
        public Builder splitPaymentFlag(Boolean splitPaymentFlag) {
            this.splitPaymentFlag = splitPaymentFlag;
            return this;
        }
        
        public PaymentData build() {
            PaymentData result = new PaymentData();
            result.setCardAcquisitionReference(this.cardAcquisitionReference);
            result.setCustomerOrder(this.customerOrder);
            result.setInstalment(this.instalment);
            result.setPaymentInstrumentData(this.paymentInstrumentData);
            result.setPaymentType(this.paymentType);
            result.setRequestedValidityDate(this.requestedValidityDate);
            result.setSplitPaymentFlag(this.splitPaymentFlag);
            return result;
        }
    }
}
