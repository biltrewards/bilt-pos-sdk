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
 * Encrypted PIN block and related information, used for PIN entry and verification services.
 */
public class CardholderPIN {
    private String additionalInput;
    private ContentInformationType encrPINBlock;
    private PINFormatEnum pinFormat;

    /**
     * Additional information required for PIN verification, such as part of the PAN for ISO
     * format 0.
     */
    @JsonProperty("AdditionalInput")
    public String getAdditionalInput() { return additionalInput; }
    @JsonProperty("AdditionalInput")
    public void setAdditionalInput(String value) { this.additionalInput = value; }

    @JsonProperty("EncrPINBlock")
    public ContentInformationType getEncrPINBlock() { return encrPINBlock; }
    @JsonProperty("EncrPINBlock")
    public void setEncrPINBlock(ContentInformationType value) { this.encrPINBlock = value; }

    @JsonProperty("PINFormat")
    public PINFormatEnum getPinFormat() { return pinFormat; }
    @JsonProperty("PINFormat")
    public void setPinFormat(PINFormatEnum value) { this.pinFormat = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String additionalInput;
        private ContentInformationType encrPINBlock;
        private PINFormatEnum pinFormat;
        
        private Builder() {}
        
        public Builder additionalInput(String additionalInput) {
            this.additionalInput = additionalInput;
            return this;
        }
        
        public Builder encrPINBlock(ContentInformationType encrPINBlock) {
            this.encrPINBlock = encrPINBlock;
            return this;
        }
        
        public Builder pinFormat(PINFormatEnum pinFormat) {
            this.pinFormat = pinFormat;
            return this;
        }
        
        public CardholderPIN build() {
            CardholderPIN result = new CardholderPIN();
            result.setAdditionalInput(this.additionalInput);
            result.setEncrPINBlock(this.encrPINBlock);
            result.setPinFormat(this.pinFormat);
            return result;
        }
    }
}
