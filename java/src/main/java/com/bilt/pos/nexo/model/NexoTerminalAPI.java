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
 * Request message of the EPAS Sale To POI protocol.
 *
 * Response message of the EPAS Sale To POI protocol.
 */
public class NexoTerminalAPI {
    private SaleToPOIRequest saleToPOIRequest;
    private SaleToPOIResponse saleToPOIResponse;

    @JsonProperty("SaleToPOIRequest")
    public SaleToPOIRequest getSaleToPOIRequest() { return saleToPOIRequest; }
    @JsonProperty("SaleToPOIRequest")
    public void setSaleToPOIRequest(SaleToPOIRequest value) { this.saleToPOIRequest = value; }

    @JsonProperty("SaleToPOIResponse")
    public SaleToPOIResponse getSaleToPOIResponse() { return saleToPOIResponse; }
    @JsonProperty("SaleToPOIResponse")
    public void setSaleToPOIResponse(SaleToPOIResponse value) { this.saleToPOIResponse = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private SaleToPOIRequest saleToPOIRequest;
        private SaleToPOIResponse saleToPOIResponse;
        
        private Builder() {}
        
        public Builder saleToPOIRequest(SaleToPOIRequest saleToPOIRequest) {
            this.saleToPOIRequest = saleToPOIRequest;
            return this;
        }
        
        public Builder saleToPOIResponse(SaleToPOIResponse saleToPOIResponse) {
            this.saleToPOIResponse = saleToPOIResponse;
            return this;
        }
        
        public NexoTerminalAPI build() {
            NexoTerminalAPI result = new NexoTerminalAPI();
            result.setSaleToPOIRequest(this.saleToPOIRequest);
            result.setSaleToPOIResponse(this.saleToPOIResponse);
            return result;
        }
    }
}
