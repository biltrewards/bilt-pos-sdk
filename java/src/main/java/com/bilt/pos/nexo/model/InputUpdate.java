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
 * Content of the Input Update message, used to update the display of an Input request in
 * progress when an event requires a change.
 */
public class InputUpdate {
    private Long maxDecimalLength;
    private Long maxLength;
    private MenuEntry[] menuEntry;
    private MessageReference messageReference;
    private Long minLength;
    private OutputContent outputContent;
    private ContentInformationType outputSignature;

    /**
     * Updated maximum decimal length. Must be present if it was in the original Input request.
     */
    @JsonProperty("MaxDecimalLength")
    public Long getMaxDecimalLength() { return maxDecimalLength; }
    @JsonProperty("MaxDecimalLength")
    public void setMaxDecimalLength(Long value) { this.maxDecimalLength = value; }

    /**
     * Updated maximum input length. Must be present if it was in the original Input request.
     */
    @JsonProperty("MaxLength")
    public Long getMaxLength() { return maxLength; }
    @JsonProperty("MaxLength")
    public void setMaxLength(Long value) { this.maxLength = value; }

    @JsonProperty("MenuEntry")
    public MenuEntry[] getMenuEntry() { return menuEntry; }
    @JsonProperty("MenuEntry")
    public void setMenuEntry(MenuEntry[] value) { this.menuEntry = value; }

    @JsonProperty("MessageReference")
    public MessageReference getMessageReference() { return messageReference; }
    @JsonProperty("MessageReference")
    public void setMessageReference(MessageReference value) { this.messageReference = value; }

    /**
     * Updated minimum input length. Must be present if it was in the original Input request.
     */
    @JsonProperty("MinLength")
    public Long getMinLength() { return minLength; }
    @JsonProperty("MinLength")
    public void setMinLength(Long value) { this.minLength = value; }

    @JsonProperty("OutputContent")
    public OutputContent getOutputContent() { return outputContent; }
    @JsonProperty("OutputContent")
    public void setOutputContent(OutputContent value) { this.outputContent = value; }

    @JsonProperty("OutputSignature")
    public ContentInformationType getOutputSignature() { return outputSignature; }
    @JsonProperty("OutputSignature")
    public void setOutputSignature(ContentInformationType value) { this.outputSignature = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private Long maxDecimalLength;
        private Long maxLength;
        private MenuEntry[] menuEntry;
        private MessageReference messageReference;
        private Long minLength;
        private OutputContent outputContent;
        private ContentInformationType outputSignature;
        
        private Builder() {}
        
        public Builder maxDecimalLength(Long maxDecimalLength) {
            this.maxDecimalLength = maxDecimalLength;
            return this;
        }
        
        public Builder maxLength(Long maxLength) {
            this.maxLength = maxLength;
            return this;
        }
        
        public Builder menuEntry(MenuEntry[] menuEntry) {
            this.menuEntry = menuEntry;
            return this;
        }
        
        public Builder messageReference(MessageReference messageReference) {
            this.messageReference = messageReference;
            return this;
        }
        
        public Builder minLength(Long minLength) {
            this.minLength = minLength;
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
        
        public InputUpdate build() {
            InputUpdate result = new InputUpdate();
            result.setMaxDecimalLength(this.maxDecimalLength);
            result.setMaxLength(this.maxLength);
            result.setMenuEntry(this.menuEntry);
            result.setMessageReference(this.messageReference);
            result.setMinLength(this.minLength);
            result.setOutputContent(this.outputContent);
            result.setOutputSignature(this.outputSignature);
            return result;
        }
    }
}
