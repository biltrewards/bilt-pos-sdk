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
 * Geographic location of a mobile phone, specified using geographic or UTM coordinates.
 */
public class Geolocation {
    private GeographicCoordinates geographicCoordinates;
    private UTMCoordinates utmCoordinates;

    @JsonProperty("GeographicCoordinates")
    public GeographicCoordinates getGeographicCoordinates() { return geographicCoordinates; }
    @JsonProperty("GeographicCoordinates")
    public void setGeographicCoordinates(GeographicCoordinates value) { this.geographicCoordinates = value; }

    @JsonProperty("UTMCoordinates")
    public UTMCoordinates getUtmCoordinates() { return utmCoordinates; }
    @JsonProperty("UTMCoordinates")
    public void setUtmCoordinates(UTMCoordinates value) { this.utmCoordinates = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private GeographicCoordinates geographicCoordinates;
        private UTMCoordinates utmCoordinates;
        
        private Builder() {}
        
        public Builder geographicCoordinates(GeographicCoordinates geographicCoordinates) {
            this.geographicCoordinates = geographicCoordinates;
            return this;
        }
        
        public Builder utmCoordinates(UTMCoordinates utmCoordinates) {
            this.utmCoordinates = utmCoordinates;
            return this;
        }
        
        public Geolocation build() {
            Geolocation result = new Geolocation();
            result.setGeographicCoordinates(this.geographicCoordinates);
            result.setUtmCoordinates(this.utmCoordinates);
            return result;
        }
    }
}
