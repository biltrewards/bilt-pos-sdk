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
 * Numeric value of a handwritten signature captured on the POI by a signature capture
 * device.
 */
public class CapturedSignature {
    private AreaSize areaSize;
    private SignaturePoint[] signaturePoint;

    /**
     * Size of the pad area where the signature was written, given as maximum abscissa and
     * ordinate values (max 'FFFF').
     */
    @JsonProperty("AreaSize")
    public AreaSize getAreaSize() { return areaSize; }
    @JsonProperty("AreaSize")
    public void setAreaSize(AreaSize value) { this.areaSize = value; }

    @JsonProperty("SignaturePoint")
    public SignaturePoint[] getSignaturePoint() { return signaturePoint; }
    @JsonProperty("SignaturePoint")
    public void setSignaturePoint(SignaturePoint[] value) { this.signaturePoint = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private AreaSize areaSize;
        private SignaturePoint[] signaturePoint;
        
        private Builder() {}
        
        public Builder areaSize(AreaSize areaSize) {
            this.areaSize = areaSize;
            return this;
        }
        
        public Builder signaturePoint(SignaturePoint[] signaturePoint) {
            this.signaturePoint = signaturePoint;
            return this;
        }
        
        public CapturedSignature build() {
            CapturedSignature result = new CapturedSignature();
            result.setAreaSize(this.areaSize);
            result.setSignaturePoint(this.signaturePoint);
            return result;
        }
    }
}
