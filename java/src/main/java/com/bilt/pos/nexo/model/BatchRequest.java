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
 * Content of the Batch Request message, used to send transactions for later execution or to
 * retrieve results of transactions performed without the Sale System.
 */
public class BatchRequest {
    private Boolean removeAllFlag;
    private TransactionToPerform[] transactionToPerform;

    /**
     * When true, transactions not yet performed are removed from the POI. Default false.
     */
    @JsonProperty("RemoveAllFlag")
    public Boolean getRemoveAllFlag() { return removeAllFlag; }
    @JsonProperty("RemoveAllFlag")
    public void setRemoveAllFlag(Boolean value) { this.removeAllFlag = value; }

    @JsonProperty("TransactionToPerform")
    public TransactionToPerform[] getTransactionToPerform() { return transactionToPerform; }
    @JsonProperty("TransactionToPerform")
    public void setTransactionToPerform(TransactionToPerform[] value) { this.transactionToPerform = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private Boolean removeAllFlag;
        private TransactionToPerform[] transactionToPerform;
        
        private Builder() {}
        
        public Builder removeAllFlag(Boolean removeAllFlag) {
            this.removeAllFlag = removeAllFlag;
            return this;
        }
        
        public Builder transactionToPerform(TransactionToPerform[] transactionToPerform) {
            this.transactionToPerform = transactionToPerform;
            return this;
        }
        
        public BatchRequest build() {
            BatchRequest result = new BatchRequest();
            result.setRemoveAllFlag(this.removeAllFlag);
            result.setTransactionToPerform(this.transactionToPerform);
            return result;
        }
    }
}
