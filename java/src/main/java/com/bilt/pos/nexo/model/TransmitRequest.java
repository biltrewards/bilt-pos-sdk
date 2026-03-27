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
 * Content of the Transmit Request message, used to send a message to a remote host using
 * the other party as a communication gateway.
 */
public class TransmitRequest {
    private String destinationAddress;
    private long maximumTransmitTime;
    private String message;
    private Boolean waitResponseFlag;

    /**
     * Transport address of the destination host (IP address or DNS name, optionally followed by
     * ':' and port number).
     */
    @JsonProperty("DestinationAddress")
    public String getDestinationAddress() { return destinationAddress; }
    @JsonProperty("DestinationAddress")
    public void setDestinationAddress(String value) { this.destinationAddress = value; }

    /**
     * Maximum time in seconds for the transmission, including waiting for a response.
     */
    @JsonProperty("MaximumTransmitTime")
    public long getMaximumTransmitTime() { return maximumTransmitTime; }
    @JsonProperty("MaximumTransmitTime")
    public void setMaximumTransmitTime(long value) { this.maximumTransmitTime = value; }

    /**
     * Base64-encoded message content to transmit to the destination host.
     */
    @JsonProperty("Message")
    public String getMessage() { return message; }
    @JsonProperty("Message")
    public void setMessage(String value) { this.message = value; }

    /**
     * When true, waits for a response from the destination host before replying. Default true.
     */
    @JsonProperty("WaitResponseFlag")
    public Boolean getWaitResponseFlag() { return waitResponseFlag; }
    @JsonProperty("WaitResponseFlag")
    public void setWaitResponseFlag(Boolean value) { this.waitResponseFlag = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String destinationAddress;
        private long maximumTransmitTime;
        private String message;
        private Boolean waitResponseFlag;
        
        private Builder() {}
        
        public Builder destinationAddress(String destinationAddress) {
            this.destinationAddress = destinationAddress;
            return this;
        }
        
        public Builder maximumTransmitTime(long maximumTransmitTime) {
            this.maximumTransmitTime = maximumTransmitTime;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder waitResponseFlag(Boolean waitResponseFlag) {
            this.waitResponseFlag = waitResponseFlag;
            return this;
        }
        
        public TransmitRequest build() {
            TransmitRequest result = new TransmitRequest();
            result.setDestinationAddress(this.destinationAddress);
            result.setMaximumTransmitTime(this.maximumTransmitTime);
            result.setMessage(this.message);
            result.setWaitResponseFlag(this.waitResponseFlag);
            return result;
        }
    }
}
