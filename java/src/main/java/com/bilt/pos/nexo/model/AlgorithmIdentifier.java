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
 * Identifies a cryptographic algorithm and its parameters.
 */
public class AlgorithmIdentifier {
    private String algorithm;
    private Parameter parameter;

    /**
     * Name of the cryptographic algorithm (e.g. 'des-ede3-cbc', 'id-retail-cbc-mac-sha-256').
     */
    @JsonProperty("Algorithm")
    public String getAlgorithm() { return algorithm; }
    @JsonProperty("Algorithm")
    public void setAlgorithm(String value) { this.algorithm = value; }

    /**
     * Optional parameters for the algorithm.
     */
    @JsonProperty("Parameter")
    public Parameter getParameter() { return parameter; }
    @JsonProperty("Parameter")
    public void setParameter(Parameter value) { this.parameter = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String algorithm;
        private Parameter parameter;
        
        private Builder() {}
        
        public Builder algorithm(String algorithm) {
            this.algorithm = algorithm;
            return this;
        }
        
        public Builder parameter(Parameter parameter) {
            this.parameter = parameter;
            return this;
        }
        
        public AlgorithmIdentifier build() {
            AlgorithmIdentifier result = new AlgorithmIdentifier();
            result.setAlgorithm(this.algorithm);
            result.setParameter(this.parameter);
            return result;
        }
    }
}
