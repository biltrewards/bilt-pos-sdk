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
 * Content of the Display Request message, conveying one or more display commands for output
 * devices.
 */
public class DisplayRequest {
    private DisplayOutput[] displayOutput;

    @JsonProperty("DisplayOutput")
    public DisplayOutput[] getDisplayOutput() { return displayOutput; }
    @JsonProperty("DisplayOutput")
    public void setDisplayOutput(DisplayOutput[] value) { this.displayOutput = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private DisplayOutput[] displayOutput;
        
        private Builder() {}
        
        public Builder displayOutput(DisplayOutput[] displayOutput) {
            this.displayOutput = displayOutput;
            return this;
        }
        
        public DisplayRequest build() {
            DisplayRequest result = new DisplayRequest();
            result.setDisplayOutput(this.displayOutput);
            return result;
        }
    }
}
