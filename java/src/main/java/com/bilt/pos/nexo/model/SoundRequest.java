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
 * Content of the Sound Request message, used to start or stop a sound, or to set the
 * default volume.
 */
public class SoundRequest {
    private ResponseModeEnum responseMode;
    private SoundActionEnum soundAction;
    private SoundContent soundContent;
    private Long soundVolume;

    @JsonProperty("ResponseMode")
    public ResponseModeEnum getResponseMode() { return responseMode; }
    @JsonProperty("ResponseMode")
    public void setResponseMode(ResponseModeEnum value) { this.responseMode = value; }

    @JsonProperty("SoundAction")
    public SoundActionEnum getSoundAction() { return soundAction; }
    @JsonProperty("SoundAction")
    public void setSoundAction(SoundActionEnum value) { this.soundAction = value; }

    @JsonProperty("SoundContent")
    public SoundContent getSoundContent() { return soundContent; }
    @JsonProperty("SoundContent")
    public void setSoundContent(SoundContent value) { this.soundContent = value; }

    /**
     * Volume as a percentage of maximum (0 = mute). Mandatory for SetDefaultVolume.
     */
    @JsonProperty("SoundVolume")
    public Long getSoundVolume() { return soundVolume; }
    @JsonProperty("SoundVolume")
    public void setSoundVolume(Long value) { this.soundVolume = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private ResponseModeEnum responseMode;
        private SoundActionEnum soundAction;
        private SoundContent soundContent;
        private Long soundVolume;
        
        private Builder() {}
        
        public Builder responseMode(ResponseModeEnum responseMode) {
            this.responseMode = responseMode;
            return this;
        }
        
        public Builder soundAction(SoundActionEnum soundAction) {
            this.soundAction = soundAction;
            return this;
        }
        
        public Builder soundContent(SoundContent soundContent) {
            this.soundContent = soundContent;
            return this;
        }
        
        public Builder soundVolume(Long soundVolume) {
            this.soundVolume = soundVolume;
            return this;
        }
        
        public SoundRequest build() {
            SoundRequest result = new SoundRequest();
            result.setResponseMode(this.responseMode);
            result.setSoundAction(this.soundAction);
            result.setSoundContent(this.soundContent);
            result.setSoundVolume(this.soundVolume);
            return result;
        }
    }
}
