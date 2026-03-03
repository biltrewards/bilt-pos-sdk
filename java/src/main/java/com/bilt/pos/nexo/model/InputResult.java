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
 * Result of an input command, including the entered data.
 */
public class InputResult {
    private DeviceEnum device;
    private InfoQualifyEnum infoQualify;
    private Input input;
    private Response response;

    @JsonProperty("Device")
    public DeviceEnum getDevice() { return device; }
    @JsonProperty("Device")
    public void setDevice(DeviceEnum value) { this.device = value; }

    @JsonProperty("InfoQualify")
    public InfoQualifyEnum getInfoQualify() { return infoQualify; }
    @JsonProperty("InfoQualify")
    public void setInfoQualify(InfoQualifyEnum value) { this.infoQualify = value; }

    /**
     * Data entered by the user in response to the input command.
     */
    @JsonProperty("Input")
    public Input getInput() { return input; }
    @JsonProperty("Input")
    public void setInput(Input value) { this.input = value; }

    @JsonProperty("Response")
    public Response getResponse() { return response; }
    @JsonProperty("Response")
    public void setResponse(Response value) { this.response = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private DeviceEnum device;
        private InfoQualifyEnum infoQualify;
        private Input input;
        private Response response;
        
        private Builder() {}
        
        public Builder device(DeviceEnum device) {
            this.device = device;
            return this;
        }
        
        public Builder infoQualify(InfoQualifyEnum infoQualify) {
            this.infoQualify = infoQualify;
            return this;
        }
        
        public Builder input(Input input) {
            this.input = input;
            return this;
        }
        
        public Builder response(Response response) {
            this.response = response;
            return this;
        }
        
        public InputResult build() {
            InputResult result = new InputResult();
            result.setDevice(this.device);
            result.setInfoQualify(this.infoQualify);
            result.setInput(this.input);
            result.setResponse(this.response);
            return result;
        }
    }
}
