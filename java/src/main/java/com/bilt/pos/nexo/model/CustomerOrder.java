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
 * Customer order recorded in the POI System, used for multi-step or multi-channel sale
 * transactions such as click-and-collect.
 */
public class CustomerOrder {
    private String accessedBy;
    private String additionalInformation;
    private String currency;
    private double currentAmount;
    private String customerOrderID;
    private String endDate;
    private double forecastedAmount;
    private Boolean openOrderState;
    private String saleReferenceID;
    private String startDate;

    /**
     * Identification of the Sale entity currently processing this order, for synchronisation.
     */
    @JsonProperty("AccessedBy")
    public String getAccessedBy() { return accessedBy; }
    @JsonProperty("AccessedBy")
    public void setAccessedBy(String value) { this.accessedBy = value; }

    /**
     * Unqualified additional information about the customer order.
     */
    @JsonProperty("AdditionalInformation")
    public String getAdditionalInformation() { return additionalInformation; }
    @JsonProperty("AdditionalInformation")
    public void setAdditionalInformation(String value) { this.additionalInformation = value; }

    @JsonProperty("Currency")
    public String getCurrency() { return currency; }
    @JsonProperty("Currency")
    public void setCurrency(String value) { this.currency = value; }

    /**
     * Total amount of all completed transactions within this customer order.
     */
    @JsonProperty("CurrentAmount")
    public double getCurrentAmount() { return currentAmount; }
    @JsonProperty("CurrentAmount")
    public void setCurrentAmount(double value) { this.currentAmount = value; }

    /**
     * Additional optional identification of the customer order.
     */
    @JsonProperty("CustomerOrderID")
    public String getCustomerOrderID() { return customerOrderID; }
    @JsonProperty("CustomerOrderID")
    public void setCustomerOrderID(String value) { this.customerOrderID = value; }

    /**
     * Date and time when the customer order was closed. Present when OpenOrderState is false.
     */
    @JsonProperty("EndDate")
    public String getEndDate() { return endDate; }
    @JsonProperty("EndDate")
    public void setEndDate(String value) { this.endDate = value; }

    /**
     * Forecasted total amount of the order, set by the Sale System.
     */
    @JsonProperty("ForecastedAmount")
    public double getForecastedAmount() { return forecastedAmount; }
    @JsonProperty("ForecastedAmount")
    public void setForecastedAmount(double value) { this.forecastedAmount = value; }

    /**
     * When true, the order is still open and awaiting further operations. Default true.
     */
    @JsonProperty("OpenOrderState")
    public Boolean getOpenOrderState() { return openOrderState; }
    @JsonProperty("OpenOrderState")
    public void setOpenOrderState(Boolean value) { this.openOrderState = value; }

    /**
     * Sale System reference identifying this customer order.
     */
    @JsonProperty("SaleReferenceId")
    public String getSaleReferenceID() { return saleReferenceID; }
    @JsonProperty("SaleReferenceId")
    public void setSaleReferenceID(String value) { this.saleReferenceID = value; }

    /**
     * Date and time when the customer order was created.
     */
    @JsonProperty("StartDate")
    public String getStartDate() { return startDate; }
    @JsonProperty("StartDate")
    public void setStartDate(String value) { this.startDate = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String accessedBy;
        private String additionalInformation;
        private String currency;
        private double currentAmount;
        private String customerOrderID;
        private String endDate;
        private double forecastedAmount;
        private Boolean openOrderState;
        private String saleReferenceID;
        private String startDate;
        
        private Builder() {}
        
        public Builder accessedBy(String accessedBy) {
            this.accessedBy = accessedBy;
            return this;
        }
        
        public Builder additionalInformation(String additionalInformation) {
            this.additionalInformation = additionalInformation;
            return this;
        }
        
        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }
        
        public Builder currentAmount(double currentAmount) {
            this.currentAmount = currentAmount;
            return this;
        }
        
        public Builder customerOrderID(String customerOrderID) {
            this.customerOrderID = customerOrderID;
            return this;
        }
        
        public Builder endDate(String endDate) {
            this.endDate = endDate;
            return this;
        }
        
        public Builder forecastedAmount(double forecastedAmount) {
            this.forecastedAmount = forecastedAmount;
            return this;
        }
        
        public Builder openOrderState(Boolean openOrderState) {
            this.openOrderState = openOrderState;
            return this;
        }
        
        public Builder saleReferenceID(String saleReferenceID) {
            this.saleReferenceID = saleReferenceID;
            return this;
        }
        
        public Builder startDate(String startDate) {
            this.startDate = startDate;
            return this;
        }
        
        public CustomerOrder build() {
            CustomerOrder result = new CustomerOrder();
            result.setAccessedBy(this.accessedBy);
            result.setAdditionalInformation(this.additionalInformation);
            result.setCurrency(this.currency);
            result.setCurrentAmount(this.currentAmount);
            result.setCustomerOrderID(this.customerOrderID);
            result.setEndDate(this.endDate);
            result.setForecastedAmount(this.forecastedAmount);
            result.setOpenOrderState(this.openOrderState);
            result.setSaleReferenceID(this.saleReferenceID);
            result.setStartDate(this.startDate);
            return result;
        }
    }
}
