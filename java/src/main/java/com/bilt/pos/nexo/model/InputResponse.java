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
 * Content of the Input Response message, conveying the display result and the entered data.
 */
public class InputResponse {
    private InputResult inputResult;
    private OutputResult outputResult;

    @JsonProperty("InputResult")
    public InputResult getInputResult() { return inputResult; }
    @JsonProperty("InputResult")
    public void setInputResult(InputResult value) { this.inputResult = value; }

    @JsonProperty("OutputResult")
    public OutputResult getOutputResult() { return outputResult; }
    @JsonProperty("OutputResult")
    public void setOutputResult(OutputResult value) { this.outputResult = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private InputResult inputResult;
        private OutputResult outputResult;
        
        private Builder() {}
        
        public Builder inputResult(InputResult inputResult) {
            this.inputResult = inputResult;
            return this;
        }
        
        public Builder outputResult(OutputResult outputResult) {
            this.outputResult = outputResult;
            return this;
        }
        
        public InputResponse build() {
            InputResponse result = new InputResponse();
            result.setInputResult(this.inputResult);
            result.setOutputResult(this.outputResult);
            return result;
        }
    }
}
