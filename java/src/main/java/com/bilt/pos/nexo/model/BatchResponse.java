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
 * Content of the Batch Response message, conveying the global result and results of
 * performed transactions.
 */
public class BatchResponse {
    private PerformedTransaction[] performedTransaction;
    private Response response;

    @JsonProperty("PerformedTransaction")
    public PerformedTransaction[] getPerformedTransaction() { return performedTransaction; }
    @JsonProperty("PerformedTransaction")
    public void setPerformedTransaction(PerformedTransaction[] value) { this.performedTransaction = value; }

    @JsonProperty("Response")
    public Response getResponse() { return response; }
    @JsonProperty("Response")
    public void setResponse(Response value) { this.response = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private PerformedTransaction[] performedTransaction;
        private Response response;
        
        private Builder() {}
        
        public Builder performedTransaction(PerformedTransaction[] performedTransaction) {
            this.performedTransaction = performedTransaction;
            return this;
        }
        
        public Builder response(Response response) {
            this.response = response;
            return this;
        }
        
        public BatchResponse build() {
            BatchResponse result = new BatchResponse();
            result.setPerformedTransaction(this.performedTransaction);
            result.setResponse(this.response);
            return result;
        }
    }
}
