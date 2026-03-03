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
 * Content of the Print Response message, conveying the result of the print request.
 */
public class PrintResponse {
    private DocumentQualifierEnum documentQualifier;
    private Response response;

    @JsonProperty("DocumentQualifier")
    public DocumentQualifierEnum getDocumentQualifier() { return documentQualifier; }
    @JsonProperty("DocumentQualifier")
    public void setDocumentQualifier(DocumentQualifierEnum value) { this.documentQualifier = value; }

    @JsonProperty("Response")
    public Response getResponse() { return response; }
    @JsonProperty("Response")
    public void setResponse(Response value) { this.response = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private DocumentQualifierEnum documentQualifier;
        private Response response;
        
        private Builder() {}
        
        public Builder documentQualifier(DocumentQualifierEnum documentQualifier) {
            this.documentQualifier = documentQualifier;
            return this;
        }
        
        public Builder response(Response response) {
            this.response = response;
            return this;
        }
        
        public PrintResponse build() {
            PrintResponse result = new PrintResponse();
            result.setDocumentQualifier(this.documentQualifier);
            result.setResponse(this.response);
            return result;
        }
    }
}
