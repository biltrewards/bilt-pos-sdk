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
 * Transaction totals for a specific payment instrument type and set of grouping criteria,
 * returned in Reconciliation and GetTotals responses.
 */
public class TransactionTotals {
    private String acquirerID;
    private String cardBrand;
    private ErrorConditionType errorCondition;
    private String hostReconciliationID;
    private String loyaltyCurrency;
    private LoyaltyTotal[] loyaltyTotals;
    private LoyaltyUnitEnum loyaltyUnit;
    private String operatorID;
    private String paymentCurrency;
    private PaymentInstrumentTypeEnum paymentInstrumentType;
    private PaymentTotal[] paymentTotals;
    private String poiid;
    private String saleID;
    private String shiftNumber;
    private String totalsGroupID;

    /**
     * Identification of the Acquirer for these totals.
     */
    @JsonProperty("AcquirerID")
    public String getAcquirerID() { return acquirerID; }
    @JsonProperty("AcquirerID")
    public void setAcquirerID(String value) { this.acquirerID = value; }

    /**
     * Payment or loyalty card brand for these totals. Present when configured to break down
     * totals per card brand.
     */
    @JsonProperty("CardBrand")
    public String getCardBrand() { return cardBrand; }
    @JsonProperty("CardBrand")
    public void setCardBrand(String value) { this.cardBrand = value; }

    /**
     * Error condition for this Acquirer's reconciliation when Result is Partial.
     */
    @JsonProperty("ErrorCondition")
    public ErrorConditionType getErrorCondition() { return errorCondition; }
    @JsonProperty("ErrorCondition")
    public void setErrorCondition(ErrorConditionType value) { this.errorCondition = value; }

    /**
     * Identification of the Acquirer reconciliation period for these totals.
     */
    @JsonProperty("HostReconciliationID")
    public String getHostReconciliationID() { return hostReconciliationID; }
    @JsonProperty("HostReconciliationID")
    public void setHostReconciliationID(String value) { this.hostReconciliationID = value; }

    @JsonProperty("LoyaltyCurrency")
    public String getLoyaltyCurrency() { return loyaltyCurrency; }
    @JsonProperty("LoyaltyCurrency")
    public void setLoyaltyCurrency(String value) { this.loyaltyCurrency = value; }

    @JsonProperty("LoyaltyTotals")
    public LoyaltyTotal[] getLoyaltyTotals() { return loyaltyTotals; }
    @JsonProperty("LoyaltyTotals")
    public void setLoyaltyTotals(LoyaltyTotal[] value) { this.loyaltyTotals = value; }

    @JsonProperty("LoyaltyUnit")
    public LoyaltyUnitEnum getLoyaltyUnit() { return loyaltyUnit; }
    @JsonProperty("LoyaltyUnit")
    public void setLoyaltyUnit(LoyaltyUnitEnum value) { this.loyaltyUnit = value; }

    /**
     * Cashier/operator identification for these totals. Present when requested.
     */
    @JsonProperty("OperatorID")
    public String getOperatorID() { return operatorID; }
    @JsonProperty("OperatorID")
    public void setOperatorID(String value) { this.operatorID = value; }

    @JsonProperty("PaymentCurrency")
    public String getPaymentCurrency() { return paymentCurrency; }
    @JsonProperty("PaymentCurrency")
    public void setPaymentCurrency(String value) { this.paymentCurrency = value; }

    @JsonProperty("PaymentInstrumentType")
    public PaymentInstrumentTypeEnum getPaymentInstrumentType() { return paymentInstrumentType; }
    @JsonProperty("PaymentInstrumentType")
    public void setPaymentInstrumentType(PaymentInstrumentTypeEnum value) { this.paymentInstrumentType = value; }

    @JsonProperty("PaymentTotals")
    public PaymentTotal[] getPaymentTotals() { return paymentTotals; }
    @JsonProperty("PaymentTotals")
    public void setPaymentTotals(PaymentTotal[] value) { this.paymentTotals = value; }

    /**
     * POI Terminal identification for these totals. Present when requested.
     */
    @JsonProperty("POIID")
    public String getPoiid() { return poiid; }
    @JsonProperty("POIID")
    public void setPoiid(String value) { this.poiid = value; }

    /**
     * Sale Terminal identification for these totals. Present when requested.
     */
    @JsonProperty("SaleID")
    public String getSaleID() { return saleID; }
    @JsonProperty("SaleID")
    public void setSaleID(String value) { this.saleID = value; }

    /**
     * Shift number for these totals. Present when requested.
     */
    @JsonProperty("ShiftNumber")
    public String getShiftNumber() { return shiftNumber; }
    @JsonProperty("ShiftNumber")
    public void setShiftNumber(String value) { this.shiftNumber = value; }

    /**
     * Sale group identification for these totals. Present when requested.
     */
    @JsonProperty("TotalsGroupID")
    public String getTotalsGroupID() { return totalsGroupID; }
    @JsonProperty("TotalsGroupID")
    public void setTotalsGroupID(String value) { this.totalsGroupID = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String acquirerID;
        private String cardBrand;
        private ErrorConditionType errorCondition;
        private String hostReconciliationID;
        private String loyaltyCurrency;
        private LoyaltyTotal[] loyaltyTotals;
        private LoyaltyUnitEnum loyaltyUnit;
        private String operatorID;
        private String paymentCurrency;
        private PaymentInstrumentTypeEnum paymentInstrumentType;
        private PaymentTotal[] paymentTotals;
        private String poiid;
        private String saleID;
        private String shiftNumber;
        private String totalsGroupID;
        
        private Builder() {}
        
        public Builder acquirerID(String acquirerID) {
            this.acquirerID = acquirerID;
            return this;
        }
        
        public Builder cardBrand(String cardBrand) {
            this.cardBrand = cardBrand;
            return this;
        }
        
        public Builder errorCondition(ErrorConditionType errorCondition) {
            this.errorCondition = errorCondition;
            return this;
        }
        
        public Builder hostReconciliationID(String hostReconciliationID) {
            this.hostReconciliationID = hostReconciliationID;
            return this;
        }
        
        public Builder loyaltyCurrency(String loyaltyCurrency) {
            this.loyaltyCurrency = loyaltyCurrency;
            return this;
        }
        
        public Builder loyaltyTotals(LoyaltyTotal[] loyaltyTotals) {
            this.loyaltyTotals = loyaltyTotals;
            return this;
        }
        
        public Builder loyaltyUnit(LoyaltyUnitEnum loyaltyUnit) {
            this.loyaltyUnit = loyaltyUnit;
            return this;
        }
        
        public Builder operatorID(String operatorID) {
            this.operatorID = operatorID;
            return this;
        }
        
        public Builder paymentCurrency(String paymentCurrency) {
            this.paymentCurrency = paymentCurrency;
            return this;
        }
        
        public Builder paymentInstrumentType(PaymentInstrumentTypeEnum paymentInstrumentType) {
            this.paymentInstrumentType = paymentInstrumentType;
            return this;
        }
        
        public Builder paymentTotals(PaymentTotal[] paymentTotals) {
            this.paymentTotals = paymentTotals;
            return this;
        }
        
        public Builder poiid(String poiid) {
            this.poiid = poiid;
            return this;
        }
        
        public Builder saleID(String saleID) {
            this.saleID = saleID;
            return this;
        }
        
        public Builder shiftNumber(String shiftNumber) {
            this.shiftNumber = shiftNumber;
            return this;
        }
        
        public Builder totalsGroupID(String totalsGroupID) {
            this.totalsGroupID = totalsGroupID;
            return this;
        }
        
        public TransactionTotals build() {
            TransactionTotals result = new TransactionTotals();
            result.setAcquirerID(this.acquirerID);
            result.setCardBrand(this.cardBrand);
            result.setErrorCondition(this.errorCondition);
            result.setHostReconciliationID(this.hostReconciliationID);
            result.setLoyaltyCurrency(this.loyaltyCurrency);
            result.setLoyaltyTotals(this.loyaltyTotals);
            result.setLoyaltyUnit(this.loyaltyUnit);
            result.setOperatorID(this.operatorID);
            result.setPaymentCurrency(this.paymentCurrency);
            result.setPaymentInstrumentType(this.paymentInstrumentType);
            result.setPaymentTotals(this.paymentTotals);
            result.setPoiid(this.poiid);
            result.setSaleID(this.saleID);
            result.setShiftNumber(this.shiftNumber);
            result.setTotalsGroupID(this.totalsGroupID);
            return result;
        }
    }
}
