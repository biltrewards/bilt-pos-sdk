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
 * Geographic location specified by latitude and longitude coordinates.
 */
public class GeographicCoordinates {
    private String latitude;
    private String longitude;

    /**
     * Angular distance north or south of the equator in degrees, minutes, and seconds, followed
     * by N or S.
     */
    @JsonProperty("Latitude")
    public String getLatitude() { return latitude; }
    @JsonProperty("Latitude")
    public void setLatitude(String value) { this.latitude = value; }

    /**
     * Angular distance east or west of the Greenwich meridian in degrees, minutes, and seconds,
     * followed by E or W.
     */
    @JsonProperty("Longitude")
    public String getLongitude() { return longitude; }
    @JsonProperty("Longitude")
    public void setLongitude(String value) { this.longitude = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String latitude;
        private String longitude;
        
        private Builder() {}
        
        public Builder latitude(String latitude) {
            this.latitude = latitude;
            return this;
        }
        
        public Builder longitude(String longitude) {
            this.longitude = longitude;
            return this;
        }
        
        public GeographicCoordinates build() {
            GeographicCoordinates result = new GeographicCoordinates();
            result.setLatitude(this.latitude);
            result.setLongitude(this.longitude);
            return result;
        }
    }
}
