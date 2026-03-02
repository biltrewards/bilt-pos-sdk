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
 * Optional message to display to the Customer on the POI during the abort.
 *
 * A complete display operation for a logical device, including the content to display and
 * processing parameters.
 *
 * Optional prompt or welcome message to display on the CustomerDisplay of the POI Terminal.
 */
public class DisplayOutput {
    private DeviceEnum device;
    private InfoQualifyEnum infoQualify;
    private MenuEntry[] menuEntry;
    private Long minimumDisplayTime;
    private OutputContent outputContent;
    private ContentInformationType outputSignature;
    private Boolean responseRequiredFlag;

    @JsonProperty("Device")
    public DeviceEnum getDevice() { return device; }
    @JsonProperty("Device")
    public void setDevice(DeviceEnum value) { this.device = value; }

    @JsonProperty("InfoQualify")
    public InfoQualifyEnum getInfoQualify() { return infoQualify; }
    @JsonProperty("InfoQualify")
    public void setInfoQualify(InfoQualifyEnum value) { this.infoQualify = value; }

    @JsonProperty("MenuEntry")
    public MenuEntry[] getMenuEntry() { return menuEntry; }
    @JsonProperty("MenuEntry")
    public void setMenuEntry(MenuEntry[] value) { this.menuEntry = value; }

    /**
     * Minimum number of seconds the message must remain displayed. Response is sent immediately
     * regardless.
     */
    @JsonProperty("MinimumDisplayTime")
    public Long getMinimumDisplayTime() { return minimumDisplayTime; }
    @JsonProperty("MinimumDisplayTime")
    public void setMinimumDisplayTime(Long value) { this.minimumDisplayTime = value; }

    @JsonProperty("OutputContent")
    public OutputContent getOutputContent() { return outputContent; }
    @JsonProperty("OutputContent")
    public void setOutputContent(OutputContent value) { this.outputContent = value; }

    /**
     * Vendor-specific signature protecting the text to display or print.
     */
    @JsonProperty("OutputSignature")
    public ContentInformationType getOutputSignature() { return outputSignature; }
    @JsonProperty("OutputSignature")
    public void setOutputSignature(ContentInformationType value) { this.outputSignature = value; }

    /**
     * When true, the receiver must send a Display Response for this output. Default true.
     */
    @JsonProperty("ResponseRequiredFlag")
    public Boolean getResponseRequiredFlag() { return responseRequiredFlag; }
    @JsonProperty("ResponseRequiredFlag")
    public void setResponseRequiredFlag(Boolean value) { this.responseRequiredFlag = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private DeviceEnum device;
        private InfoQualifyEnum infoQualify;
        private MenuEntry[] menuEntry;
        private Long minimumDisplayTime;
        private OutputContent outputContent;
        private ContentInformationType outputSignature;
        private Boolean responseRequiredFlag;
        
        private Builder() {}
        
        public Builder device(DeviceEnum device) {
            this.device = device;
            return this;
        }
        
        public Builder infoQualify(InfoQualifyEnum infoQualify) {
            this.infoQualify = infoQualify;
            return this;
        }
        
        public Builder menuEntry(MenuEntry[] menuEntry) {
            this.menuEntry = menuEntry;
            return this;
        }
        
        public Builder minimumDisplayTime(Long minimumDisplayTime) {
            this.minimumDisplayTime = minimumDisplayTime;
            return this;
        }
        
        public Builder outputContent(OutputContent outputContent) {
            this.outputContent = outputContent;
            return this;
        }
        
        public Builder outputSignature(ContentInformationType outputSignature) {
            this.outputSignature = outputSignature;
            return this;
        }
        
        public Builder responseRequiredFlag(Boolean responseRequiredFlag) {
            this.responseRequiredFlag = responseRequiredFlag;
            return this;
        }
        
        public DisplayOutput build() {
            DisplayOutput result = new DisplayOutput();
            result.setDevice(this.device);
            result.setInfoQualify(this.infoQualify);
            result.setMenuEntry(this.menuEntry);
            result.setMinimumDisplayTime(this.minimumDisplayTime);
            result.setOutputContent(this.outputContent);
            result.setOutputSignature(this.outputSignature);
            result.setResponseRequiredFlag(this.responseRequiredFlag);
            return result;
        }
    }
}
