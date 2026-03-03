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
 * Coordinates of a point where the pen changes direction or is lifted during signature
 * capture. Both X and Y equal 'FFFF' when the pen is lifted.
 */
public class SignaturePoint {
    private String x;
    private String y;

    /**
     * Hexadecimal abscissa value of the signature point (e.g. '3BC', '0', '1287').
     */
    @JsonProperty("X")
    public String getX() { return x; }
    @JsonProperty("X")
    public void setX(String value) { this.x = value; }

    /**
     * Hexadecimal ordinate value of the signature point.
     */
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
        
        public SignaturePoint build() {
            SignaturePoint result = new SignaturePoint();
            result.setX(this.x);
            result.setY(this.y);
            return result;
        }
    }
}
