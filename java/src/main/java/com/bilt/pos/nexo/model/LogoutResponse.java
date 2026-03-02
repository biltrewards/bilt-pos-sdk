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
 * Content of the Logout Response message.
 */
public class LogoutResponse {
    private Response response;

    @JsonProperty("Response")
    public Response getResponse() { return response; }
    @JsonProperty("Response")
    public void setResponse(Response value) { this.response = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private Response response;
        
        private Builder() {}
        
        public Builder response(Response response) {
            this.response = response;
            return this;
        }
        
        public LogoutResponse build() {
            LogoutResponse result = new LogoutResponse();
            result.setResponse(this.response);
            return result;
        }
    }
}
