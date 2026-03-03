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
 * Optional parameters for the algorithm.
 */
public class Parameter {
    private String initialisationVector;

    /**
     * Base64-encoded 8-byte IV for CBC mode.
     */
    @JsonProperty("InitialisationVector")
    public String getInitialisationVector() { return initialisationVector; }
    @JsonProperty("InitialisationVector")
    public void setInitialisationVector(String value) { this.initialisationVector = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String initialisationVector;
        
        private Builder() {}
        
        public Builder initialisationVector(String initialisationVector) {
            this.initialisationVector = initialisationVector;
            return this;
        }
        
        public Parameter build() {
            Parameter result = new Parameter();
            result.setInitialisationVector(this.initialisationVector);
            return result;
        }
    }
}
