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
 * Result of processing a message request, included as the first element of every response
 * message body.
 */
public class Response {
    private String additionalResponse;
    private ErrorConditionType errorCondition;
    private ResultType result;

    /**
     * Additional information about the processing result for logging and further analysis.
     * Mandatory when Result is Failure.
     */
    @JsonProperty("AdditionalResponse")
    public String getAdditionalResponse() { return additionalResponse; }
    @JsonProperty("AdditionalResponse")
    public void setAdditionalResponse(String value) { this.additionalResponse = value; }

    /**
     * Condition that produced the failure. Mandatory when Result is Failure.
     */
    @JsonProperty("ErrorCondition")
    public ErrorConditionType getErrorCondition() { return errorCondition; }
    @JsonProperty("ErrorCondition")
    public void setErrorCondition(ErrorConditionType value) { this.errorCondition = value; }

    @JsonProperty("Result")
    public ResultType getResult() { return result; }
    @JsonProperty("Result")
    public void setResult(ResultType value) { this.result = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String additionalResponse;
        private ErrorConditionType errorCondition;
        private ResultType result;
        
        private Builder() {}
        
        public Builder additionalResponse(String additionalResponse) {
            this.additionalResponse = additionalResponse;
            return this;
        }
        
        public Builder errorCondition(ErrorConditionType errorCondition) {
            this.errorCondition = errorCondition;
            return this;
        }
        
        public Builder result(ResultType result) {
            this.result = result;
            return this;
        }
        
        public Response build() {
            Response result = new Response();
            result.setAdditionalResponse(this.additionalResponse);
            result.setErrorCondition(this.errorCondition);
            result.setResult(this.result);
            return result;
        }
    }
}
