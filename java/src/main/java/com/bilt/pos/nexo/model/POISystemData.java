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
 * POI System information returned on successful login.
 */
public class POISystemData {
    private String dateTime;
    private SaleSoftware poiSoftware;
    private POIStatus poiStatus;
    private POITerminalData poiTerminalData;

    /**
     * Date and time of the POI System or POI Terminal for clock synchronisation.
     */
    @JsonProperty("DateTime")
    public String getDateTime() { return dateTime; }
    @JsonProperty("DateTime")
    public void setDateTime(String value) { this.dateTime = value; }

    @JsonProperty("POISoftware")
    public SaleSoftware getPoiSoftware() { return poiSoftware; }
    @JsonProperty("POISoftware")
    public void setPoiSoftware(SaleSoftware value) { this.poiSoftware = value; }

    @JsonProperty("POIStatus")
    public POIStatus getPoiStatus() { return poiStatus; }
    @JsonProperty("POIStatus")
    public void setPoiStatus(POIStatus value) { this.poiStatus = value; }

    /**
     * Characteristics of the POI Terminal attached to this Sale Terminal.
     */
    @JsonProperty("POITerminalData")
    public POITerminalData getPoiTerminalData() { return poiTerminalData; }
    @JsonProperty("POITerminalData")
    public void setPoiTerminalData(POITerminalData value) { this.poiTerminalData = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String dateTime;
        private SaleSoftware poiSoftware;
        private POIStatus poiStatus;
        private POITerminalData poiTerminalData;
        
        private Builder() {}
        
        public Builder dateTime(String dateTime) {
            this.dateTime = dateTime;
            return this;
        }
        
        public Builder poiSoftware(SaleSoftware poiSoftware) {
            this.poiSoftware = poiSoftware;
            return this;
        }
        
        public Builder poiStatus(POIStatus poiStatus) {
            this.poiStatus = poiStatus;
            return this;
        }
        
        public Builder poiTerminalData(POITerminalData poiTerminalData) {
            this.poiTerminalData = poiTerminalData;
            return this;
        }
        
        public POISystemData build() {
            POISystemData result = new POISystemData();
            result.setDateTime(this.dateTime);
            result.setPoiSoftware(this.poiSoftware);
            result.setPoiStatus(this.poiStatus);
            result.setPoiTerminalData(this.poiTerminalData);
            return result;
        }
    }
}
