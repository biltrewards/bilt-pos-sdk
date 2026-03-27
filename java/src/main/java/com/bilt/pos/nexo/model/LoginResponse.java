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
 * Content of the Login Response message, conveying POI System identification, terminal
 * characteristics, and status.
 */
public class LoginResponse {
    private POISystemData poiSystemData;
    private Response response;

    /**
     * POI System information returned on successful login.
     */
    @JsonProperty("POISystemData")
    public POISystemData getPoiSystemData() { return poiSystemData; }
    @JsonProperty("POISystemData")
    public void setPoiSystemData(POISystemData value) { this.poiSystemData = value; }

    @JsonProperty("Response")
    public Response getResponse() { return response; }
    @JsonProperty("Response")
    public void setResponse(Response value) { this.response = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private POISystemData poiSystemData;
        private Response response;
        
        private Builder() {}
        
        public Builder poiSystemData(POISystemData poiSystemData) {
            this.poiSystemData = poiSystemData;
            return this;
        }
        
        public Builder response(Response response) {
            this.response = response;
            return this;
        }
        
        public LoginResponse build() {
            LoginResponse result = new LoginResponse();
            result.setPoiSystemData(this.poiSystemData);
            result.setResponse(this.response);
            return result;
        }
    }
}
