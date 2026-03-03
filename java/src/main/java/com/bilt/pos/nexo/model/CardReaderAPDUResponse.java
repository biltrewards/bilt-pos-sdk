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
 * Content of a single APDU response received from the smart card per ISO 7816.
 */
public class CardReaderAPDUResponse {
    private String apduData;
    private String cardStatusWords;
    private Response response;

    /**
     * Data field of the APDU response from the chip card.
     */
    @JsonProperty("APDUData")
    public String getApduData() { return apduData; }
    @JsonProperty("APDUData")
    public void setApduData(String value) { this.apduData = value; }

    /**
     * Status words (SW1-SW2) from the APDU response per ISO 7816-4. Base64-encoded 2 bytes.
     */
    @JsonProperty("CardStatusWords")
    public String getCardStatusWords() { return cardStatusWords; }
    @JsonProperty("CardStatusWords")
    public void setCardStatusWords(String value) { this.cardStatusWords = value; }

    @JsonProperty("Response")
    public Response getResponse() { return response; }
    @JsonProperty("Response")
    public void setResponse(Response value) { this.response = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String apduData;
        private String cardStatusWords;
        private Response response;
        
        private Builder() {}
        
        public Builder apduData(String apduData) {
            this.apduData = apduData;
            return this;
        }
        
        public Builder cardStatusWords(String cardStatusWords) {
            this.cardStatusWords = cardStatusWords;
            return this;
        }
        
        public Builder response(Response response) {
            this.response = response;
            return this;
        }
        
        public CardReaderAPDUResponse build() {
            CardReaderAPDUResponse result = new CardReaderAPDUResponse();
            result.setApduData(this.apduData);
            result.setCardStatusWords(this.cardStatusWords);
            result.setResponse(this.response);
            return result;
        }
    }
}
