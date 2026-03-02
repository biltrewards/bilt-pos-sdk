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
 * Content of the Transaction Status Response message, conveying the status of the queried
 * transaction.
 */
public class TransactionStatusResponse {
    private MessageReference messageReference;
    private RepeatedMessageResponse repeatedMessageResponse;
    private Response response;

    @JsonProperty("MessageReference")
    public MessageReference getMessageReference() { return messageReference; }
    @JsonProperty("MessageReference")
    public void setMessageReference(MessageReference value) { this.messageReference = value; }

    /**
     * The original response message when the queried transaction has completed.
     */
    @JsonProperty("RepeatedMessageResponse")
    public RepeatedMessageResponse getRepeatedMessageResponse() { return repeatedMessageResponse; }
    @JsonProperty("RepeatedMessageResponse")
    public void setRepeatedMessageResponse(RepeatedMessageResponse value) { this.repeatedMessageResponse = value; }

    @JsonProperty("Response")
    public Response getResponse() { return response; }
    @JsonProperty("Response")
    public void setResponse(Response value) { this.response = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private MessageReference messageReference;
        private RepeatedMessageResponse repeatedMessageResponse;
        private Response response;
        
        private Builder() {}
        
        public Builder messageReference(MessageReference messageReference) {
            this.messageReference = messageReference;
            return this;
        }
        
        public Builder repeatedMessageResponse(RepeatedMessageResponse repeatedMessageResponse) {
            this.repeatedMessageResponse = repeatedMessageResponse;
            return this;
        }
        
        public Builder response(Response response) {
            this.response = response;
            return this;
        }
        
        public TransactionStatusResponse build() {
            TransactionStatusResponse result = new TransactionStatusResponse();
            result.setMessageReference(this.messageReference);
            result.setRepeatedMessageResponse(this.repeatedMessageResponse);
            result.setResponse(this.response);
            return result;
        }
    }
}
