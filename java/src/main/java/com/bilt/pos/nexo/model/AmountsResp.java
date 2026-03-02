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
 * Various amounts in the payment response approved by the POI and the Acquirer.
 */
public class AmountsResp {
    private double authorizedAmount;
    private Double cashBackAmount;
    private String currency;
    private Double tipAmount;
    private Double totalFeesAmount;
    private Double totalRebatesAmount;

    /**
     * Amount authorised by the Acquirer. Equals RequestedAmount + TotalFeesAmount for full
     * authorisation.
     */
    @JsonProperty("AuthorizedAmount")
    public double getAuthorizedAmount() { return authorizedAmount; }
    @JsonProperty("AuthorizedAmount")
    public void setAuthorizedAmount(double value) { this.authorizedAmount = value; }

    /**
     * Actual cashback amount performed with the payment.
     */
    @JsonProperty("CashBackAmount")
    public Double getCashBackAmount() { return cashBackAmount; }
    @JsonProperty("CashBackAmount")
    public void setCashBackAmount(Double value) { this.cashBackAmount = value; }

    /**
     * Currency of the response amounts. Mandatory for currency conversion.
     */
    @JsonProperty("Currency")
    public String getCurrency() { return currency; }
    @JsonProperty("Currency")
    public void setCurrency(String value) { this.currency = value; }

    /**
     * Actual tip amount included in the authorised amount.
     */
    @JsonProperty("TipAmount")
    public Double getTipAmount() { return tipAmount; }
    @JsonProperty("TipAmount")
    public void setTipAmount(Double value) { this.tipAmount = value; }

    /**
     * Total financial fees charged for the payment service.
     */
    @JsonProperty("TotalFeesAmount")
    public Double getTotalFeesAmount() { return totalFeesAmount; }
    @JsonProperty("TotalFeesAmount")
    public void setTotalFeesAmount(Double value) { this.totalFeesAmount = value; }

    /**
     * Total rebates amount across all loyalty programs.
     */
    @JsonProperty("TotalRebatesAmount")
    public Double getTotalRebatesAmount() { return totalRebatesAmount; }
    @JsonProperty("TotalRebatesAmount")
    public void setTotalRebatesAmount(Double value) { this.totalRebatesAmount = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private double authorizedAmount;
        private Double cashBackAmount;
        private String currency;
        private Double tipAmount;
        private Double totalFeesAmount;
        private Double totalRebatesAmount;
        
        private Builder() {}
        
        public Builder authorizedAmount(double authorizedAmount) {
            this.authorizedAmount = authorizedAmount;
            return this;
        }
        
        public Builder cashBackAmount(Double cashBackAmount) {
            this.cashBackAmount = cashBackAmount;
            return this;
        }
        
        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }
        
        public Builder tipAmount(Double tipAmount) {
            this.tipAmount = tipAmount;
            return this;
        }
        
        public Builder totalFeesAmount(Double totalFeesAmount) {
            this.totalFeesAmount = totalFeesAmount;
            return this;
        }
        
        public Builder totalRebatesAmount(Double totalRebatesAmount) {
            this.totalRebatesAmount = totalRebatesAmount;
            return this;
        }
        
        public AmountsResp build() {
            AmountsResp result = new AmountsResp();
            result.setAuthorizedAmount(this.authorizedAmount);
            result.setCashBackAmount(this.cashBackAmount);
            result.setCurrency(this.currency);
            result.setTipAmount(this.tipAmount);
            result.setTotalFeesAmount(this.totalFeesAmount);
            result.setTotalRebatesAmount(this.totalRebatesAmount);
            return result;
        }
    }
}
