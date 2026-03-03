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
 * Content of the Card Reader Power-Off Request message, used to power off a smart card chip
 * and request the customer to remove the card.
 */
public class CardReaderPowerOffRequest {
    private DisplayOutput displayOutput;
    private Long maxWaitingTime;

    @JsonProperty("DisplayOutput")
    public DisplayOutput getDisplayOutput() { return displayOutput; }
    @JsonProperty("DisplayOutput")
    public void setDisplayOutput(DisplayOutput value) { this.displayOutput = value; }

    /**
     * Maximum time in seconds to wait for the card to be removed.
     */
    @JsonProperty("MaxWaitingTime")
    public Long getMaxWaitingTime() { return maxWaitingTime; }
    @JsonProperty("MaxWaitingTime")
    public void setMaxWaitingTime(Long value) { this.maxWaitingTime = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private DisplayOutput displayOutput;
        private Long maxWaitingTime;
        
        private Builder() {}
        
        public Builder displayOutput(DisplayOutput displayOutput) {
            this.displayOutput = displayOutput;
            return this;
        }
        
        public Builder maxWaitingTime(Long maxWaitingTime) {
            this.maxWaitingTime = maxWaitingTime;
            return this;
        }
        
        public CardReaderPowerOffRequest build() {
            CardReaderPowerOffRequest result = new CardReaderPowerOffRequest();
            result.setDisplayOutput(this.displayOutput);
            result.setMaxWaitingTime(this.maxWaitingTime);
            return result;
        }
    }
}
