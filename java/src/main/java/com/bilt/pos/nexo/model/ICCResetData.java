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
 * Data from a chip card returned after reset during CardReaderInit, including the Answer To
 * Reset and status words.
 */
public class ICCResetData {
    private String atrValue;
    private String cardStatusWords;

    /**
     * Base64-encoded Answer To Reset (ATR) value from the chip card per ISO 7816-3.
     */
    @JsonProperty("ATRValue")
    public String getAtrValue() { return atrValue; }
    @JsonProperty("ATRValue")
    public void setAtrValue(String value) { this.atrValue = value; }

    /**
     * Base64-encoded status words (SW1-SW2) from the chip card per ISO 7816-4.
     */
    @JsonProperty("CardStatusWords")
    public String getCardStatusWords() { return cardStatusWords; }
    @JsonProperty("CardStatusWords")
    public void setCardStatusWords(String value) { this.cardStatusWords = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String atrValue;
        private String cardStatusWords;
        
        private Builder() {}
        
        public Builder atrValue(String atrValue) {
            this.atrValue = atrValue;
            return this;
        }
        
        public Builder cardStatusWords(String cardStatusWords) {
            this.cardStatusWords = cardStatusWords;
            return this;
        }
        
        public ICCResetData build() {
            ICCResetData result = new ICCResetData();
            result.setAtrValue(this.atrValue);
            result.setCardStatusWords(this.cardStatusWords);
            return result;
        }
    }
}
