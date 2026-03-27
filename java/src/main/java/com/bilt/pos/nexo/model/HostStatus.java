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
 * Reachability status of a payment or loyalty host, returned in Diagnosis responses when
 * HostDiagnosisFlag is true.
 */
public class HostStatus {
    private String acquirerID;
    private Boolean isReachableFlag;

    /**
     * Identification of the Acquirer or host.
     */
    @JsonProperty("AcquirerID")
    public String getAcquirerID() { return acquirerID; }
    @JsonProperty("AcquirerID")
    public void setAcquirerID(String value) { this.acquirerID = value; }

    /**
     * When true, the host is reachable from the POI. Default true.
     */
    @JsonProperty("IsReachableFlag")
    public Boolean getIsReachableFlag() { return isReachableFlag; }
    @JsonProperty("IsReachableFlag")
    public void setIsReachableFlag(Boolean value) { this.isReachableFlag = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String acquirerID;
        private Boolean isReachableFlag;
        
        private Builder() {}
        
        public Builder acquirerID(String acquirerID) {
            this.acquirerID = acquirerID;
            return this;
        }
        
        public Builder isReachableFlag(Boolean isReachableFlag) {
            this.isReachableFlag = isReachableFlag;
            return this;
        }
        
        public HostStatus build() {
            HostStatus result = new HostStatus();
            result.setAcquirerID(this.acquirerID);
            result.setIsReachableFlag(this.isReachableFlag);
            return result;
        }
    }
}
