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
 * Content of the Transmit Response message, conveying the result and optionally the
 * received response.
 */
public class TransmitResponse {
    private String message;
    private Response response;

    /**
     * Base64-encoded response message received from the destination host.
     */
    @JsonProperty("Message")
    public String getMessage() { return message; }
    @JsonProperty("Message")
    public void setMessage(String value) { this.message = value; }

    @JsonProperty("Response")
    public Response getResponse() { return response; }
    @JsonProperty("Response")
    public void setResponse(Response value) { this.response = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String message;
        private Response response;
        
        private Builder() {}
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder response(Response response) {
            this.response = response;
            return this;
        }
        
        public TransmitResponse build() {
            TransmitResponse result = new TransmitResponse();
            result.setMessage(this.message);
            result.setResponse(this.response);
            return result;
        }
    }
}
