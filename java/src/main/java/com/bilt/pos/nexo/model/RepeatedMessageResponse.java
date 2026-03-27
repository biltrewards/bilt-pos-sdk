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
 * The original response message when the queried transaction has completed.
 */
public class RepeatedMessageResponse {
    private MessageHeader messageHeader;
    private RepeatedResponseMessageBody repeatedResponseMessageBody;

    @JsonProperty("MessageHeader")
    public MessageHeader getMessageHeader() { return messageHeader; }
    @JsonProperty("MessageHeader")
    public void setMessageHeader(MessageHeader value) { this.messageHeader = value; }

    @JsonProperty("RepeatedResponseMessageBody")
    public RepeatedResponseMessageBody getRepeatedResponseMessageBody() { return repeatedResponseMessageBody; }
    @JsonProperty("RepeatedResponseMessageBody")
    public void setRepeatedResponseMessageBody(RepeatedResponseMessageBody value) { this.repeatedResponseMessageBody = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private MessageHeader messageHeader;
        private RepeatedResponseMessageBody repeatedResponseMessageBody;
        
        private Builder() {}
        
        public Builder messageHeader(MessageHeader messageHeader) {
            this.messageHeader = messageHeader;
            return this;
        }
        
        public Builder repeatedResponseMessageBody(RepeatedResponseMessageBody repeatedResponseMessageBody) {
            this.repeatedResponseMessageBody = repeatedResponseMessageBody;
            return this;
        }
        
        public RepeatedMessageResponse build() {
            RepeatedMessageResponse result = new RepeatedMessageResponse();
            result.setMessageHeader(this.messageHeader);
            result.setRepeatedResponseMessageBody(this.repeatedResponseMessageBody);
            return result;
        }
    }
}
