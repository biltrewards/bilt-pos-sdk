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
 * Information related to a dynamic currency conversion performed during the payment
 * transaction.
 */
public class CurrencyConversion {
    private Double commission;
    private ConvertedAmount convertedAmount;
    private Boolean customerApprovedFlag;
    private String declaration;
    private Double markup;
    private Double rate;

    /**
     * Commission amount charged for the currency conversion.
     */
    @JsonProperty("Commission")
    public Double getCommission() { return commission; }
    @JsonProperty("Commission")
    public void setCommission(Double value) { this.commission = value; }

    /**
     * The payment amount expressed in the customer's home currency after conversion.
     */
    @JsonProperty("ConvertedAmount")
    public ConvertedAmount getConvertedAmount() { return convertedAmount; }
    @JsonProperty("ConvertedAmount")
    public void setConvertedAmount(ConvertedAmount value) { this.convertedAmount = value; }

    /**
     * Indicates whether the customer approved the currency conversion. Default true.
     */
    @JsonProperty("CustomerApprovedFlag")
    public Boolean getCustomerApprovedFlag() { return customerApprovedFlag; }
    @JsonProperty("CustomerApprovedFlag")
    public void setCustomerApprovedFlag(Boolean value) { this.customerApprovedFlag = value; }

    /**
     * Declaration text to be presented to and printed for the customer.
     */
    @JsonProperty("Declaration")
    public String getDeclaration() { return declaration; }
    @JsonProperty("Declaration")
    public void setDeclaration(String value) { this.declaration = value; }

    /**
     * Markup percentage applied to the conversion.
     */
    @JsonProperty("Markup")
    public Double getMarkup() { return markup; }
    @JsonProperty("Markup")
    public void setMarkup(Double value) { this.markup = value; }

    /**
     * Conversion rate from the source currency (AmountsResp.Currency) to the target currency
     * (ConvertedAmount.Currency).
     */
    @JsonProperty("Rate")
    public Double getRate() { return rate; }
    @JsonProperty("Rate")
    public void setRate(Double value) { this.rate = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private Double commission;
        private ConvertedAmount convertedAmount;
        private Boolean customerApprovedFlag;
        private String declaration;
        private Double markup;
        private Double rate;
        
        private Builder() {}
        
        public Builder commission(Double commission) {
            this.commission = commission;
            return this;
        }
        
        public Builder convertedAmount(ConvertedAmount convertedAmount) {
            this.convertedAmount = convertedAmount;
            return this;
        }
        
        public Builder customerApprovedFlag(Boolean customerApprovedFlag) {
            this.customerApprovedFlag = customerApprovedFlag;
            return this;
        }
        
        public Builder declaration(String declaration) {
            this.declaration = declaration;
            return this;
        }
        
        public Builder markup(Double markup) {
            this.markup = markup;
            return this;
        }
        
        public Builder rate(Double rate) {
            this.rate = rate;
            return this;
        }
        
        public CurrencyConversion build() {
            CurrencyConversion result = new CurrencyConversion();
            result.setCommission(this.commission);
            result.setConvertedAmount(this.convertedAmount);
            result.setCustomerApprovedFlag(this.customerApprovedFlag);
            result.setDeclaration(this.declaration);
            result.setMarkup(this.markup);
            result.setRate(this.rate);
            return result;
        }
    }
}
