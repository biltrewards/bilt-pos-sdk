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
 * Content of the Abort Request message, used by the Sale System to halt and terminate the
 * processing of a message in progress.
 */
public class AbortRequest {
    private String abortReason;
    private DisplayOutput displayOutput;
    private MessageReference messageReference;

    /**
     * Free text reason for aborting the transaction, for logging purposes.
     */
    @JsonProperty("AbortReason")
    public String getAbortReason() { return abortReason; }
    @JsonProperty("AbortReason")
    public void setAbortReason(String value) { this.abortReason = value; }

    /**
     * Optional message to display to the Customer on the POI during the abort.
     */
    @JsonProperty("DisplayOutput")
    public DisplayOutput getDisplayOutput() { return displayOutput; }
    @JsonProperty("DisplayOutput")
    public void setDisplayOutput(DisplayOutput value) { this.displayOutput = value; }

    @JsonProperty("MessageReference")
    public MessageReference getMessageReference() { return messageReference; }
    @JsonProperty("MessageReference")
    public void setMessageReference(MessageReference value) { this.messageReference = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String abortReason;
        private DisplayOutput displayOutput;
        private MessageReference messageReference;
        
        private Builder() {}
        
        public Builder abortReason(String abortReason) {
            this.abortReason = abortReason;
            return this;
        }
        
        public Builder displayOutput(DisplayOutput displayOutput) {
            this.displayOutput = displayOutput;
            return this;
        }
        
        public Builder messageReference(MessageReference messageReference) {
            this.messageReference = messageReference;
            return this;
        }
        
        public AbortRequest build() {
            AbortRequest result = new AbortRequest();
            result.setAbortReason(this.abortReason);
            result.setDisplayOutput(this.displayOutput);
            result.setMessageReference(this.messageReference);
            return result;
        }
    }
}
