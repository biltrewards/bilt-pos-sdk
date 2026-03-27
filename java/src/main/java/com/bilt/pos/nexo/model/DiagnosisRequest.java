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
 * Content of the Diagnosis Request message, used to request the operational status of a POI
 * Terminal and its components.
 */
public class DiagnosisRequest {
    private String[] acquirerID;
    private Boolean hostDiagnosisFlag;
    private String poiid;

    /**
     * Specific Acquirer hosts to diagnose. All connected hosts are checked when absent and
     * HostDiagnosisFlag is true.
     */
    @JsonProperty("AcquirerID")
    public String[] getAcquirerID() { return acquirerID; }
    @JsonProperty("AcquirerID")
    public void setAcquirerID(String[] value) { this.acquirerID = value; }

    /**
     * When true, the POI also checks the reachability of all connected Acquirer hosts. Default
     * false.
     */
    @JsonProperty("HostDiagnosisFlag")
    public Boolean getHostDiagnosisFlag() { return hostDiagnosisFlag; }
    @JsonProperty("HostDiagnosisFlag")
    public void setHostDiagnosisFlag(Boolean value) { this.hostDiagnosisFlag = value; }

    /**
     * Identification of the POI Terminal to diagnose. Default is MessageHeader.POIID.
     */
    @JsonProperty("POIID")
    public String getPoiid() { return poiid; }
    @JsonProperty("POIID")
    public void setPoiid(String value) { this.poiid = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String[] acquirerID;
        private Boolean hostDiagnosisFlag;
        private String poiid;
        
        private Builder() {}
        
        public Builder acquirerID(String[] acquirerID) {
            this.acquirerID = acquirerID;
            return this;
        }
        
        public Builder hostDiagnosisFlag(Boolean hostDiagnosisFlag) {
            this.hostDiagnosisFlag = hostDiagnosisFlag;
            return this;
        }
        
        public Builder poiid(String poiid) {
            this.poiid = poiid;
            return this;
        }
        
        public DiagnosisRequest build() {
            DiagnosisRequest result = new DiagnosisRequest();
            result.setAcquirerID(this.acquirerID);
            result.setHostDiagnosisFlag(this.hostDiagnosisFlag);
            result.setPoiid(this.poiid);
            return result;
        }
    }
}
