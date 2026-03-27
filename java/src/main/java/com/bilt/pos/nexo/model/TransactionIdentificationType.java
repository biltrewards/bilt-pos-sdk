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
 * Identification of a transaction for the Sale System or the POI System. The combination of
 * TransactionID and TimeStamp ensures uniqueness.
 *
 * Reference to a previous CardAcquisition transaction from which to reuse the loyalty
 * account identification.
 *
 * Reference to a previous CardAcquisition transaction to reuse its card data for this
 * payment.
 *
 * Unique identification of the transaction for the Sale System (e.g. ticket number).
 *
 * Identification of the transaction assigned by the Acquirer, when different from the
 * POITransactionID.
 *
 * Unique identification of the transaction assigned by the POI Terminal. Mandatory in all
 * response messages.
 */
public class TransactionIdentificationType {
    private String timeStamp;
    private String transactionID;

    /**
     * Date and time of the transaction, used together with TransactionID to ensure uniqueness
     * and allow log correlation.
     */
    @JsonProperty("TimeStamp")
    public String getTimeStamp() { return timeStamp; }
    @JsonProperty("TimeStamp")
    public void setTimeStamp(String value) { this.timeStamp = value; }

    /**
     * Unique identification of a transaction (e.g. ticket number).
     */
    @JsonProperty("TransactionID")
    public String getTransactionID() { return transactionID; }
    @JsonProperty("TransactionID")
    public void setTransactionID(String value) { this.transactionID = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String timeStamp;
        private String transactionID;
        
        private Builder() {}
        
        public Builder timeStamp(String timeStamp) {
            this.timeStamp = timeStamp;
            return this;
        }
        
        public Builder transactionID(String transactionID) {
            this.transactionID = transactionID;
            return this;
        }
        
        public TransactionIdentificationType build() {
            TransactionIdentificationType result = new TransactionIdentificationType();
            result.setTimeStamp(this.timeStamp);
            result.setTransactionID(this.transactionID);
            return result;
        }
    }
}
