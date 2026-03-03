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
 * Customer or merchant payment receipt, included in the response when the POI does not
 * implement the Print message exchange (Basic profile).
 */
public class PaymentReceipt {
    private DocumentQualifierEnum documentQualifier;
    private Boolean integratedPrintFlag;
    private OutputContent outputContent;
    private Boolean requiredSignatureFlag;

    @JsonProperty("DocumentQualifier")
    public DocumentQualifierEnum getDocumentQualifier() { return documentQualifier; }
    @JsonProperty("DocumentQualifier")
    public void setDocumentQualifier(DocumentQualifierEnum value) { this.documentQualifier = value; }

    /**
     * When true, this receipt is to be integrated into the Sale receipt rather than printed
     * separately. Default false.
     */
    @JsonProperty("IntegratedPrintFlag")
    public Boolean getIntegratedPrintFlag() { return integratedPrintFlag; }
    @JsonProperty("IntegratedPrintFlag")
    public void setIntegratedPrintFlag(Boolean value) { this.integratedPrintFlag = value; }

    @JsonProperty("OutputContent")
    public OutputContent getOutputContent() { return outputContent; }
    @JsonProperty("OutputContent")
    public void setOutputContent(OutputContent value) { this.outputContent = value; }

    /**
     * When true, a physical cardholder signature is required on this receipt. Default false.
     */
    @JsonProperty("RequiredSignatureFlag")
    public Boolean getRequiredSignatureFlag() { return requiredSignatureFlag; }
    @JsonProperty("RequiredSignatureFlag")
    public void setRequiredSignatureFlag(Boolean value) { this.requiredSignatureFlag = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private DocumentQualifierEnum documentQualifier;
        private Boolean integratedPrintFlag;
        private OutputContent outputContent;
        private Boolean requiredSignatureFlag;
        
        private Builder() {}
        
        public Builder documentQualifier(DocumentQualifierEnum documentQualifier) {
            this.documentQualifier = documentQualifier;
            return this;
        }
        
        public Builder integratedPrintFlag(Boolean integratedPrintFlag) {
            this.integratedPrintFlag = integratedPrintFlag;
            return this;
        }
        
        public Builder outputContent(OutputContent outputContent) {
            this.outputContent = outputContent;
            return this;
        }
        
        public Builder requiredSignatureFlag(Boolean requiredSignatureFlag) {
            this.requiredSignatureFlag = requiredSignatureFlag;
            return this;
        }
        
        public PaymentReceipt build() {
            PaymentReceipt result = new PaymentReceipt();
            result.setDocumentQualifier(this.documentQualifier);
            result.setIntegratedPrintFlag(this.integratedPrintFlag);
            result.setOutputContent(this.outputContent);
            result.setRequiredSignatureFlag(this.requiredSignatureFlag);
            return result;
        }
    }
}
