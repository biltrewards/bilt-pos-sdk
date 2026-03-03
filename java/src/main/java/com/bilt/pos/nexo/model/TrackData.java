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
 * Magnetic track or magnetic ink characters line from a card or bank check.
 */
public class TrackData {
    private TrackFormatEnum trackFormat;
    private Long trackNumb;
    private String trackValue;

    @JsonProperty("TrackFormat")
    public TrackFormatEnum getTrackFormat() { return trackFormat; }
    @JsonProperty("TrackFormat")
    public void setTrackFormat(TrackFormatEnum value) { this.trackFormat = value; }

    /**
     * ISO track number (1, 2, or 3). Default 2.
     */
    @JsonProperty("TrackNumb")
    public Long getTrackNumb() { return trackNumb; }
    @JsonProperty("TrackNumb")
    public void setTrackNumb(Long value) { this.trackNumb = value; }

    /**
     * Content of the card track or MICR line.
     */
    @JsonProperty("TrackValue")
    public String getTrackValue() { return trackValue; }
    @JsonProperty("TrackValue")
    public void setTrackValue(String value) { this.trackValue = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private TrackFormatEnum trackFormat;
        private Long trackNumb;
        private String trackValue;
        
        private Builder() {}
        
        public Builder trackFormat(TrackFormatEnum trackFormat) {
            this.trackFormat = trackFormat;
            return this;
        }
        
        public Builder trackNumb(Long trackNumb) {
            this.trackNumb = trackNumb;
            return this;
        }
        
        public Builder trackValue(String trackValue) {
            this.trackValue = trackValue;
            return this;
        }
        
        public TrackData build() {
            TrackData result = new TrackData();
            result.setTrackFormat(this.trackFormat);
            result.setTrackNumb(this.trackNumb);
            result.setTrackValue(this.trackValue);
            return result;
        }
    }
}
