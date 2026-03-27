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
 * Content of the Card Reader Init Response message, conveying the card entry mode and read
 * data.
 */
public class CardReaderInitResponse {
    private EntryModeType[] entryMode;
    private ICCResetData iccResetData;
    private Response response;
    private TrackData[] trackData;

    @JsonProperty("EntryMode")
    public EntryModeType[] getEntryMode() { return entryMode; }
    @JsonProperty("EntryMode")
    public void setEntryMode(EntryModeType[] value) { this.entryMode = value; }

    @JsonProperty("ICCResetData")
    public ICCResetData getIccResetData() { return iccResetData; }
    @JsonProperty("ICCResetData")
    public void setIccResetData(ICCResetData value) { this.iccResetData = value; }

    @JsonProperty("Response")
    public Response getResponse() { return response; }
    @JsonProperty("Response")
    public void setResponse(Response value) { this.response = value; }

    @JsonProperty("TrackData")
    public TrackData[] getTrackData() { return trackData; }
    @JsonProperty("TrackData")
    public void setTrackData(TrackData[] value) { this.trackData = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private EntryModeType[] entryMode;
        private ICCResetData iccResetData;
        private Response response;
        private TrackData[] trackData;
        
        private Builder() {}
        
        public Builder entryMode(EntryModeType[] entryMode) {
            this.entryMode = entryMode;
            return this;
        }
        
        public Builder iccResetData(ICCResetData iccResetData) {
            this.iccResetData = iccResetData;
            return this;
        }
        
        public Builder response(Response response) {
            this.response = response;
            return this;
        }
        
        public Builder trackData(TrackData[] trackData) {
            this.trackData = trackData;
            return this;
        }
        
        public CardReaderInitResponse build() {
            CardReaderInitResponse result = new CardReaderInitResponse();
            result.setEntryMode(this.entryMode);
            result.setIccResetData(this.iccResetData);
            result.setResponse(this.response);
            result.setTrackData(this.trackData);
            return result;
        }
    }
}
