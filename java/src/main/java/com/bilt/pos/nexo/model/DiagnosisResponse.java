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
 * Content of the Diagnosis Response message, conveying the POI Terminal status and
 * optionally host reachability.
 */
public class DiagnosisResponse {
    private HostStatus[] hostStatus;
    private String[] loggedSaleID;
    private POIStatus poiStatus;
    private Response response;

    @JsonProperty("HostStatus")
    public HostStatus[] getHostStatus() { return hostStatus; }
    @JsonProperty("HostStatus")
    public void setHostStatus(HostStatus[] value) { this.hostStatus = value; }

    /**
     * Identifications of Sale Terminals currently logged to this POI Terminal.
     */
    @JsonProperty("LoggedSaleID")
    public String[] getLoggedSaleID() { return loggedSaleID; }
    @JsonProperty("LoggedSaleID")
    public void setLoggedSaleID(String[] value) { this.loggedSaleID = value; }

    @JsonProperty("POIStatus")
    public POIStatus getPoiStatus() { return poiStatus; }
    @JsonProperty("POIStatus")
    public void setPoiStatus(POIStatus value) { this.poiStatus = value; }

    @JsonProperty("Response")
    public Response getResponse() { return response; }
    @JsonProperty("Response")
    public void setResponse(Response value) { this.response = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private HostStatus[] hostStatus;
        private String[] loggedSaleID;
        private POIStatus poiStatus;
        private Response response;
        
        private Builder() {}
        
        public Builder hostStatus(HostStatus[] hostStatus) {
            this.hostStatus = hostStatus;
            return this;
        }
        
        public Builder loggedSaleID(String[] loggedSaleID) {
            this.loggedSaleID = loggedSaleID;
            return this;
        }
        
        public Builder poiStatus(POIStatus poiStatus) {
            this.poiStatus = poiStatus;
            return this;
        }
        
        public Builder response(Response response) {
            this.response = response;
            return this;
        }
        
        public DiagnosisResponse build() {
            DiagnosisResponse result = new DiagnosisResponse();
            result.setHostStatus(this.hostStatus);
            result.setLoggedSaleID(this.loggedSaleID);
            result.setPoiStatus(this.poiStatus);
            result.setResponse(this.response);
            return result;
        }
    }
}
