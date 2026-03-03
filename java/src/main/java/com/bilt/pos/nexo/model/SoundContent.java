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
 * Content of the sound to play, in one of the supported formats.
 */
public class SoundContent {
    private String language;
    private String referenceID;
    private SoundFormatEnum soundFormat;
    private String text;

    @JsonProperty("Language")
    public String getLanguage() { return language; }
    @JsonProperty("Language")
    public void setLanguage(String value) { this.language = value; }

    /**
     * Identification of the preloaded sound file or text to play. Mandatory for SoundRef and
     * MessageRef formats.
     */
    @JsonProperty("ReferenceID")
    public String getReferenceID() { return referenceID; }
    @JsonProperty("ReferenceID")
    public void setReferenceID(String value) { this.referenceID = value; }

    @JsonProperty("SoundFormat")
    public SoundFormatEnum getSoundFormat() { return soundFormat; }
    @JsonProperty("SoundFormat")
    public void setSoundFormat(SoundFormatEnum value) { this.soundFormat = value; }

    /**
     * Text to synthesise as sound. Mandatory for Text format.
     */
    @JsonProperty("Text")
    public String getText() { return text; }
    @JsonProperty("Text")
    public void setText(String value) { this.text = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String language;
        private String referenceID;
        private SoundFormatEnum soundFormat;
        private String text;
        
        private Builder() {}
        
        public Builder language(String language) {
            this.language = language;
            return this;
        }
        
        public Builder referenceID(String referenceID) {
            this.referenceID = referenceID;
            return this;
        }
        
        public Builder soundFormat(SoundFormatEnum soundFormat) {
            this.soundFormat = soundFormat;
            return this;
        }
        
        public Builder text(String text) {
            this.text = text;
            return this;
        }
        
        public SoundContent build() {
            SoundContent result = new SoundContent();
            result.setLanguage(this.language);
            result.setReferenceID(this.referenceID);
            result.setSoundFormat(this.soundFormat);
            result.setText(this.text);
            return result;
        }
    }
}
