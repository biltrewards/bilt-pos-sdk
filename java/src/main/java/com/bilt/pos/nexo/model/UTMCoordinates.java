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
 * Location on Earth specified by the Universal Transverse Mercator coordinate system using
 * the WGS84 ellipsoid.
 */
public class UTMCoordinates {
    private String utmEastward;
    private String utmNorthward;
    private String utmZone;

    /**
     * X-coordinate (easting) in the UTM system.
     */
    @JsonProperty("UTMEastward")
    public String getUtmEastward() { return utmEastward; }
    @JsonProperty("UTMEastward")
    public void setUtmEastward(String value) { this.utmEastward = value; }

    /**
     * Y-coordinate (northing) in the UTM system.
     */
    @JsonProperty("UTMNorthward")
    public String getUtmNorthward() { return utmNorthward; }
    @JsonProperty("UTMNorthward")
    public void setUtmNorthward(String value) { this.utmNorthward = value; }

    /**
     * UTM grid zone combining longitude zone (1–60) and latitude band (C–X, excluding I and O).
     */
    @JsonProperty("UTMZone")
    public String getUtmZone() { return utmZone; }
    @JsonProperty("UTMZone")
    public void setUtmZone(String value) { this.utmZone = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String utmEastward;
        private String utmNorthward;
        private String utmZone;
        
        private Builder() {}
        
        public Builder utmEastward(String utmEastward) {
            this.utmEastward = utmEastward;
            return this;
        }
        
        public Builder utmNorthward(String utmNorthward) {
            this.utmNorthward = utmNorthward;
            return this;
        }
        
        public Builder utmZone(String utmZone) {
            this.utmZone = utmZone;
            return this;
        }
        
        public UTMCoordinates build() {
            UTMCoordinates result = new UTMCoordinates();
            result.setUtmEastward(this.utmEastward);
            result.setUtmNorthward(this.utmNorthward);
            result.setUtmZone(this.utmZone);
            return result;
        }
    }
}
