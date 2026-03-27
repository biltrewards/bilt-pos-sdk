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
 * Content of the Enable Service Request message, used to enable swipe-ahead transactions or
 * abort a previously started one.
 */
public class EnableServiceRequest {
    private DisplayOutput displayOutput;
    private ServicesEnabledType[] servicesEnabled;
    private TransactionActionEnum transactionAction;

    /**
     * Optional prompt or welcome message to display on the CustomerDisplay of the POI Terminal.
     */
    @JsonProperty("DisplayOutput")
    public DisplayOutput getDisplayOutput() { return displayOutput; }
    @JsonProperty("DisplayOutput")
    public void setDisplayOutput(DisplayOutput value) { this.displayOutput = value; }

    /**
     * Financial services enabled for swipe-ahead. Mandatory when TransactionAction is
     * StartTransaction.
     */
    @JsonProperty("ServicesEnabled")
    public ServicesEnabledType[] getServicesEnabled() { return servicesEnabled; }
    @JsonProperty("ServicesEnabled")
    public void setServicesEnabled(ServicesEnabledType[] value) { this.servicesEnabled = value; }

    @JsonProperty("TransactionAction")
    public TransactionActionEnum getTransactionAction() { return transactionAction; }
    @JsonProperty("TransactionAction")
    public void setTransactionAction(TransactionActionEnum value) { this.transactionAction = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private DisplayOutput displayOutput;
        private ServicesEnabledType[] servicesEnabled;
        private TransactionActionEnum transactionAction;
        
        private Builder() {}
        
        public Builder displayOutput(DisplayOutput displayOutput) {
            this.displayOutput = displayOutput;
            return this;
        }
        
        public Builder servicesEnabled(ServicesEnabledType[] servicesEnabled) {
            this.servicesEnabled = servicesEnabled;
            return this;
        }
        
        public Builder transactionAction(TransactionActionEnum transactionAction) {
            this.transactionAction = transactionAction;
            return this;
        }
        
        public EnableServiceRequest build() {
            EnableServiceRequest result = new EnableServiceRequest();
            result.setDisplayOutput(this.displayOutput);
            result.setServicesEnabled(this.servicesEnabled);
            result.setTransactionAction(this.transactionAction);
            return result;
        }
    }
}
