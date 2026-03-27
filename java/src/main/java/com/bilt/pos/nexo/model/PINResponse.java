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
 * Content of the PIN Response message, conveying the result and optionally the encrypted
 * PIN block.
 */
public class PINResponse {
    private CardholderPIN cardholderPIN;
    private Response response;

    @JsonProperty("CardholderPIN")
    public CardholderPIN getCardholderPIN() { return cardholderPIN; }
    @JsonProperty("CardholderPIN")
    public void setCardholderPIN(CardholderPIN value) { this.cardholderPIN = value; }

    @JsonProperty("Response")
    public Response getResponse() { return response; }
    @JsonProperty("Response")
    public void setResponse(Response value) { this.response = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private CardholderPIN cardholderPIN;
        private Response response;
        
        private Builder() {}
        
        public Builder cardholderPIN(CardholderPIN cardholderPIN) {
            this.cardholderPIN = cardholderPIN;
            return this;
        }
        
        public Builder response(Response response) {
            this.response = response;
            return this;
        }
        
        public PINResponse build() {
            PINResponse result = new PINResponse();
            result.setCardholderPIN(this.cardholderPIN);
            result.setResponse(this.response);
            return result;
        }
    }
}
