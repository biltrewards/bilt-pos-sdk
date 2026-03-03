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
 * Content of the Input Request message, used to request information from the Cashier or
 * Customer through a display and input device.
 */
public class InputRequest {
    private DisplayOutput displayOutput;
    private InputData inputData;

    @JsonProperty("DisplayOutput")
    public DisplayOutput getDisplayOutput() { return displayOutput; }
    @JsonProperty("DisplayOutput")
    public void setDisplayOutput(DisplayOutput value) { this.displayOutput = value; }

    @JsonProperty("InputData")
    public InputData getInputData() { return inputData; }
    @JsonProperty("InputData")
    public void setInputData(InputData value) { this.inputData = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private DisplayOutput displayOutput;
        private InputData inputData;
        
        private Builder() {}
        
        public Builder displayOutput(DisplayOutput displayOutput) {
            this.displayOutput = displayOutput;
            return this;
        }
        
        public Builder inputData(InputData inputData) {
            this.inputData = inputData;
            return this;
        }
        
        public InputRequest build() {
            InputRequest result = new InputRequest();
            result.setDisplayOutput(this.displayOutput);
            result.setInputData(this.inputData);
            return result;
        }
    }
}
