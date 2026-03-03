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
 * A complete print operation, including the document type, response mode, and content to
 * print.
 */
public class PrintOutput {
    private DocumentQualifierEnum documentQualifier;
    private Boolean integratedPrintFlag;
    private OutputContent outputContent;
    private ContentInformationType outputSignature;
    private Boolean requiredSignatureFlag;
    private ResponseModeEnum responseMode;

    @JsonProperty("DocumentQualifier")
    public DocumentQualifierEnum getDocumentQualifier() { return documentQualifier; }
    @JsonProperty("DocumentQualifier")
    public void setDocumentQualifier(DocumentQualifierEnum value) { this.documentQualifier = value; }

    /**
     * When true, this print is integrated into another receipt rather than printed separately.
     * Forces Immediate response mode. Default false.
     */
    @JsonProperty("IntegratedPrintFlag")
    public Boolean getIntegratedPrintFlag() { return integratedPrintFlag; }
    @JsonProperty("IntegratedPrintFlag")
    public void setIntegratedPrintFlag(Boolean value) { this.integratedPrintFlag = value; }

    @JsonProperty("OutputContent")
    public OutputContent getOutputContent() { return outputContent; }
    @JsonProperty("OutputContent")
    public void setOutputContent(OutputContent value) { this.outputContent = value; }

    @JsonProperty("OutputSignature")
    public ContentInformationType getOutputSignature() { return outputSignature; }
    @JsonProperty("OutputSignature")
    public void setOutputSignature(ContentInformationType value) { this.outputSignature = value; }

    /**
     * When true, a physical cardholder signature is required on this printed document. Default
     * false.
     */
    @JsonProperty("RequiredSignatureFlag")
    public Boolean getRequiredSignatureFlag() { return requiredSignatureFlag; }
    @JsonProperty("RequiredSignatureFlag")
    public void setRequiredSignatureFlag(Boolean value) { this.requiredSignatureFlag = value; }

    @JsonProperty("ResponseMode")
    public ResponseModeEnum getResponseMode() { return responseMode; }
    @JsonProperty("ResponseMode")
    public void setResponseMode(ResponseModeEnum value) { this.responseMode = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private DocumentQualifierEnum documentQualifier;
        private Boolean integratedPrintFlag;
        private OutputContent outputContent;
        private ContentInformationType outputSignature;
        private Boolean requiredSignatureFlag;
        private ResponseModeEnum responseMode;
        
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
        
        public Builder outputSignature(ContentInformationType outputSignature) {
            this.outputSignature = outputSignature;
            return this;
        }
        
        public Builder requiredSignatureFlag(Boolean requiredSignatureFlag) {
            this.requiredSignatureFlag = requiredSignatureFlag;
            return this;
        }
        
        public Builder responseMode(ResponseModeEnum responseMode) {
            this.responseMode = responseMode;
            return this;
        }
        
        public PrintOutput build() {
            PrintOutput result = new PrintOutput();
            result.setDocumentQualifier(this.documentQualifier);
            result.setIntegratedPrintFlag(this.integratedPrintFlag);
            result.setOutputContent(this.outputContent);
            result.setOutputSignature(this.outputSignature);
            result.setRequiredSignatureFlag(this.requiredSignatureFlag);
            result.setResponseMode(this.responseMode);
            return result;
        }
    }
}
