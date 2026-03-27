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
 * Content of the Card Reader Init Request message, used to enable card insertion and
 * initialise a card in the POI card reader.
 */
public class CardReaderInitRequest {
    private DisplayOutput displayOutput;
    private ForceEntryModeType[] forceEntryMode;
    private Boolean leaveCardFlag;
    private Long maxWaitingTime;
    private Boolean warmResetFlag;

    @JsonProperty("DisplayOutput")
    public DisplayOutput getDisplayOutput() { return displayOutput; }
    @JsonProperty("DisplayOutput")
    public void setDisplayOutput(DisplayOutput value) { this.displayOutput = value; }

    @JsonProperty("ForceEntryMode")
    public ForceEntryModeType[] getForceEntryMode() { return forceEntryMode; }
    @JsonProperty("ForceEntryMode")
    public void setForceEntryMode(ForceEntryModeType[] value) { this.forceEntryMode = value; }

    /**
     * When true, keeps the card in the reader after magnetic stripe reading to allow subsequent
     * chip dialogue. Default true.
     */
    @JsonProperty("LeaveCardFlag")
    public Boolean getLeaveCardFlag() { return leaveCardFlag; }
    @JsonProperty("LeaveCardFlag")
    public void setLeaveCardFlag(Boolean value) { this.leaveCardFlag = value; }

    /**
     * Maximum time in seconds to wait for the card to be inserted.
     */
    @JsonProperty("MaxWaitingTime")
    public Long getMaxWaitingTime() { return maxWaitingTime; }
    @JsonProperty("MaxWaitingTime")
    public void setMaxWaitingTime(Long value) { this.maxWaitingTime = value; }

    /**
     * When true, performs a warm reset on an already-initialised chip card. Default false.
     */
    @JsonProperty("WarmResetFlag")
    public Boolean getWarmResetFlag() { return warmResetFlag; }
    @JsonProperty("WarmResetFlag")
    public void setWarmResetFlag(Boolean value) { this.warmResetFlag = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private DisplayOutput displayOutput;
        private ForceEntryModeType[] forceEntryMode;
        private Boolean leaveCardFlag;
        private Long maxWaitingTime;
        private Boolean warmResetFlag;
        
        private Builder() {}
        
        public Builder displayOutput(DisplayOutput displayOutput) {
            this.displayOutput = displayOutput;
            return this;
        }
        
        public Builder forceEntryMode(ForceEntryModeType[] forceEntryMode) {
            this.forceEntryMode = forceEntryMode;
            return this;
        }
        
        public Builder leaveCardFlag(Boolean leaveCardFlag) {
            this.leaveCardFlag = leaveCardFlag;
            return this;
        }
        
        public Builder maxWaitingTime(Long maxWaitingTime) {
            this.maxWaitingTime = maxWaitingTime;
            return this;
        }
        
        public Builder warmResetFlag(Boolean warmResetFlag) {
            this.warmResetFlag = warmResetFlag;
            return this;
        }
        
        public CardReaderInitRequest build() {
            CardReaderInitRequest result = new CardReaderInitRequest();
            result.setDisplayOutput(this.displayOutput);
            result.setForceEntryMode(this.forceEntryMode);
            result.setLeaveCardFlag(this.leaveCardFlag);
            result.setMaxWaitingTime(this.maxWaitingTime);
            result.setWarmResetFlag(this.warmResetFlag);
            return result;
        }
    }
}
