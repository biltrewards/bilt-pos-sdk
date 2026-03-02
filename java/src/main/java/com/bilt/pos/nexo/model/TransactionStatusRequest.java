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
 * Content of the Transaction Status Request message, used to query the result of a previous
 * transaction when no response was received.
 */
public class TransactionStatusRequest {
    private DocumentQualifierEnum[] documentQualifier;
    private MessageReference messageReference;
    private Boolean receiptReprintFlag;

    @JsonProperty("DocumentQualifier")
    public DocumentQualifierEnum[] getDocumentQualifier() { return documentQualifier; }
    @JsonProperty("DocumentQualifier")
    public void setDocumentQualifier(DocumentQualifierEnum[] value) { this.documentQualifier = value; }

    @JsonProperty("MessageReference")
    public MessageReference getMessageReference() { return messageReference; }
    @JsonProperty("MessageReference")
    public void setMessageReference(MessageReference value) { this.messageReference = value; }

    /**
     * When true, requests the POI to reprint the receipt of the original transaction. Default
     * false.
     */
    @JsonProperty("ReceiptReprintFlag")
    public Boolean getReceiptReprintFlag() { return receiptReprintFlag; }
    @JsonProperty("ReceiptReprintFlag")
    public void setReceiptReprintFlag(Boolean value) { this.receiptReprintFlag = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private DocumentQualifierEnum[] documentQualifier;
        private MessageReference messageReference;
        private Boolean receiptReprintFlag;
        
        private Builder() {}
        
        public Builder documentQualifier(DocumentQualifierEnum[] documentQualifier) {
            this.documentQualifier = documentQualifier;
            return this;
        }
        
        public Builder messageReference(MessageReference messageReference) {
            this.messageReference = messageReference;
            return this;
        }
        
        public Builder receiptReprintFlag(Boolean receiptReprintFlag) {
            this.receiptReprintFlag = receiptReprintFlag;
            return this;
        }
        
        public TransactionStatusRequest build() {
            TransactionStatusRequest result = new TransactionStatusRequest();
            result.setDocumentQualifier(this.documentQualifier);
            result.setMessageReference(this.messageReference);
            result.setReceiptReprintFlag(this.receiptReprintFlag);
            return result;
        }
    }
}
