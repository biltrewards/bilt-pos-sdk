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
 * Content of the Display Response message, conveying the result of each display command
 * that required a response.
 */
public class DisplayResponse {
    private OutputResult[] outputResult;

    @JsonProperty("OutputResult")
    public OutputResult[] getOutputResult() { return outputResult; }
    @JsonProperty("OutputResult")
    public void setOutputResult(OutputResult[] value) { this.outputResult = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private OutputResult[] outputResult;
        
        private Builder() {}
        
        public Builder outputResult(OutputResult[] outputResult) {
            this.outputResult = outputResult;
            return this;
        }
        
        public DisplayResponse build() {
            DisplayResponse result = new DisplayResponse();
            result.setOutputResult(this.outputResult);
            return result;
        }
    }
}
