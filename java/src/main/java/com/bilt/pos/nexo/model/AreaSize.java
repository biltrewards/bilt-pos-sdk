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
 * Size of the pad area where the signature was written, given as maximum abscissa and
 * ordinate values (max 'FFFF').
 */
public class AreaSize {
    private String x;
    private String y;

    @JsonProperty("X")
    public String getX() { return x; }
    @JsonProperty("X")
    public void setX(String value) { this.x = value; }

    @JsonProperty("Y")
    public String getY() { return y; }
    @JsonProperty("Y")
    public void setY(String value) { this.y = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String x;
        private String y;
        
        private Builder() {}
        
        public Builder x(String x) {
            this.x = x;
            return this;
        }
        
        public Builder y(String y) {
            this.y = y;
            return this;
        }
        
        public AreaSize build() {
            AreaSize result = new AreaSize();
            result.setX(this.x);
            result.setY(this.y);
            return result;
        }
    }
}
