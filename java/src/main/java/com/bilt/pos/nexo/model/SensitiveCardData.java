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
 * Sensitive payment card data that may be CMS-protected (replaced by ProtectedCardData when
 * encrypted).
 */
public class SensitiveCardData {
    private String cardSeqNumb;
    private String expiryDate;
    private String pan;
    private TrackData[] trackData;

    /**
     * Card Sequence Number per EMV tag 5F34 — distinguishes cards with the same PAN.
     */
    @JsonProperty("CardSeqNumb")
    public String getCardSeqNumb() { return cardSeqNumb; }
    @JsonProperty("CardSeqNumb")
    public void setCardSeqNumb(String value) { this.cardSeqNumb = value; }

    /**
     * Date after which the card cannot be used. Format MMYY.
     */
    @JsonProperty("ExpiryDate")
    public String getExpiryDate() { return expiryDate; }
    @JsonProperty("ExpiryDate")
    public void setExpiryDate(String value) { this.expiryDate = value; }

    /**
     * Primary Account Number — identifies the customer account or relationship. 8 to 28 digits.
     */
    @JsonProperty("PAN")
    public String getPan() { return pan; }
    @JsonProperty("PAN")
    public void setPan(String value) { this.pan = value; }

    @JsonProperty("TrackData")
    public TrackData[] getTrackData() { return trackData; }
    @JsonProperty("TrackData")
    public void setTrackData(TrackData[] value) { this.trackData = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String cardSeqNumb;
        private String expiryDate;
        private String pan;
        private TrackData[] trackData;
        
        private Builder() {}
        
        public Builder cardSeqNumb(String cardSeqNumb) {
            this.cardSeqNumb = cardSeqNumb;
            return this;
        }
        
        public Builder expiryDate(String expiryDate) {
            this.expiryDate = expiryDate;
            return this;
        }
        
        public Builder pan(String pan) {
            this.pan = pan;
            return this;
        }
        
        public Builder trackData(TrackData[] trackData) {
            this.trackData = trackData;
            return this;
        }
        
        public SensitiveCardData build() {
            SensitiveCardData result = new SensitiveCardData();
            result.setCardSeqNumb(this.cardSeqNumb);
            result.setExpiryDate(this.expiryDate);
            result.setPan(this.pan);
            result.setTrackData(this.trackData);
            return result;
        }
    }
}
