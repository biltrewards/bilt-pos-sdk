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
 * Various amounts requested by the Sale System for the payment transaction.
 */
public class AmountsReq {
    private Double cashBackAmount;
    private String currency;
    private Double maximumCashBackAmount;
    private Double minimumAmountToDeliver;
    private Double minimumSplitAmount;
    private Double paidAmount;
    private Double requestedAmount;
    private Double tipAmount;

    /**
     * Requested cashback amount as part of the payment. The POI must perform the cashback.
     */
    @JsonProperty("CashBackAmount")
    public Double getCashBackAmount() { return cashBackAmount; }
    @JsonProperty("CashBackAmount")
    public void setCashBackAmount(Double value) { this.cashBackAmount = value; }

    @JsonProperty("Currency")
    public String getCurrency() { return currency; }
    @JsonProperty("Currency")
    public void setCurrency(String value) { this.currency = value; }

    /**
     * Maximum cashback amount the merchant allows for this transaction.
     */
    @JsonProperty("MaximumCashBackAmount")
    public Double getMaximumCashBackAmount() { return maximumCashBackAmount; }
    @JsonProperty("MaximumCashBackAmount")
    public void setMaximumCashBackAmount(Double value) { this.maximumCashBackAmount = value; }

    /**
     * Minimum amount the Sale System allows to deliver, used for OneTimeReservation when the
     * maximum is unknown.
     */
    @JsonProperty("MinimumAmountToDeliver")
    public Double getMinimumAmountToDeliver() { return minimumAmountToDeliver; }
    @JsonProperty("MinimumAmountToDeliver")
    public void setMinimumAmountToDeliver(Double value) { this.minimumAmountToDeliver = value; }

    /**
     * Minimum amount per split payment transaction, used to limit the number of splits.
     */
    @JsonProperty("MinimumSplitAmount")
    public Double getMinimumSplitAmount() { return minimumSplitAmount; }
    @JsonProperty("MinimumSplitAmount")
    public void setMinimumSplitAmount(Double value) { this.minimumSplitAmount = value; }

    /**
     * Amount already paid in previous split payment transactions for this Sale transaction.
     */
    @JsonProperty("PaidAmount")
    public Double getPaidAmount() { return paidAmount; }
    @JsonProperty("PaidAmount")
    public void setPaidAmount(Double value) { this.paidAmount = value; }

    /**
     * Total amount requested for payment, including cashback and tip. Must be greater than 0.
     */
    @JsonProperty("RequestedAmount")
    public Double getRequestedAmount() { return requestedAmount; }
    @JsonProperty("RequestedAmount")
    public void setRequestedAmount(Double value) { this.requestedAmount = value; }

    /**
     * Proposed tip amount. The POI asks the customer to validate or modify it.
     */
    @JsonProperty("TipAmount")
    public Double getTipAmount() { return tipAmount; }
    @JsonProperty("TipAmount")
    public void setTipAmount(Double value) { this.tipAmount = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private Double cashBackAmount;
        private String currency;
        private Double maximumCashBackAmount;
        private Double minimumAmountToDeliver;
        private Double minimumSplitAmount;
        private Double paidAmount;
        private Double requestedAmount;
        private Double tipAmount;
        
        private Builder() {}
        
        public Builder cashBackAmount(Double cashBackAmount) {
            this.cashBackAmount = cashBackAmount;
            return this;
        }
        
        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }
        
        public Builder maximumCashBackAmount(Double maximumCashBackAmount) {
            this.maximumCashBackAmount = maximumCashBackAmount;
            return this;
        }
        
        public Builder minimumAmountToDeliver(Double minimumAmountToDeliver) {
            this.minimumAmountToDeliver = minimumAmountToDeliver;
            return this;
        }
        
        public Builder minimumSplitAmount(Double minimumSplitAmount) {
            this.minimumSplitAmount = minimumSplitAmount;
            return this;
        }
        
        public Builder paidAmount(Double paidAmount) {
            this.paidAmount = paidAmount;
            return this;
        }
        
        public Builder requestedAmount(Double requestedAmount) {
            this.requestedAmount = requestedAmount;
            return this;
        }
        
        public Builder tipAmount(Double tipAmount) {
            this.tipAmount = tipAmount;
            return this;
        }
        
        public AmountsReq build() {
            AmountsReq result = new AmountsReq();
            result.setCashBackAmount(this.cashBackAmount);
            result.setCurrency(this.currency);
            result.setMaximumCashBackAmount(this.maximumCashBackAmount);
            result.setMinimumAmountToDeliver(this.minimumAmountToDeliver);
            result.setMinimumSplitAmount(this.minimumSplitAmount);
            result.setPaidAmount(this.paidAmount);
            result.setRequestedAmount(this.requestedAmount);
            result.setTipAmount(this.tipAmount);
            return result;
        }
    }
}
