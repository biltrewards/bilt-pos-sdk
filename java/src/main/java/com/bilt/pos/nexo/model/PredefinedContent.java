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
 * Reference to a predefined message stored on the receiving system, identified by
 * ReferenceID with an optional language.
 */
public class PredefinedContent {
    private String language;
    private String referenceID;

    /**
     * Language of the predefined message to retrieve.
     */
    @JsonProperty("Language")
    public String getLanguage() { return language; }
    @JsonProperty("Language")
    public void setLanguage(String value) { this.language = value; }

    /**
     * Identification of the predefined message to display, print, or play.
     */
    @JsonProperty("ReferenceID")
    public String getReferenceID() { return referenceID; }
    @JsonProperty("ReferenceID")
    public void setReferenceID(String value) { this.referenceID = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String language;
        private String referenceID;
        
        private Builder() {}
        
        public Builder language(String language) {
            this.language = language;
            return this;
        }
        
        public Builder referenceID(String referenceID) {
            this.referenceID = referenceID;
            return this;
        }
        
        public PredefinedContent build() {
            PredefinedContent result = new PredefinedContent();
            result.setLanguage(this.language);
            result.setReferenceID(this.referenceID);
            return result;
        }
    }
}
