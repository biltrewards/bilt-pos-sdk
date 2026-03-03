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
 * Data related to the payment Acquirer's response, including merchant and terminal
 * identification and transaction approval details.
 */
public class PaymentAcquirerData {
    private String acquirerID;
    private String acquirerPOIID;
    private TransactionIdentificationType acquirerTransactionID;
    private String approvalCode;
    private String hostReconciliationID;
    private String merchantID;

    /**
     * Identification of the Acquirer. Present when the POI System is multi-acquirer.
     */
    @JsonProperty("AcquirerID")
    public String getAcquirerID() { return acquirerID; }
    @JsonProperty("AcquirerID")
    public void setAcquirerID(String value) { this.acquirerID = value; }

    /**
     * Identification of the POI System or Terminal for the Acquirer.
     */
    @JsonProperty("AcquirerPOIID")
    public String getAcquirerPOIID() { return acquirerPOIID; }
    @JsonProperty("AcquirerPOIID")
    public void setAcquirerPOIID(String value) { this.acquirerPOIID = value; }

    /**
     * Identification of the transaction assigned by the Acquirer, when different from the
     * POITransactionID.
     */
    @JsonProperty("AcquirerTransactionID")
    public TransactionIdentificationType getAcquirerTransactionID() { return acquirerTransactionID; }
    @JsonProperty("AcquirerTransactionID")
    public void setAcquirerTransactionID(TransactionIdentificationType value) { this.acquirerTransactionID = value; }

    /**
     * Code assigned to the transaction by the Acquirer upon approval per ISO 8583 element 38.
     */
    @JsonProperty("ApprovalCode")
    public String getApprovalCode() { return approvalCode; }
    @JsonProperty("ApprovalCode")
    public void setApprovalCode(String value) { this.approvalCode = value; }

    /**
     * Identification of the Acquirer reconciliation period to which this transaction belongs.
     */
    @JsonProperty("HostReconciliationID")
    public String getHostReconciliationID() { return hostReconciliationID; }
    @JsonProperty("HostReconciliationID")
    public void setHostReconciliationID(String value) { this.hostReconciliationID = value; }

    /**
     * Identification of the merchant for the Acquirer per ISO 8583 element 42.
     */
    @JsonProperty("MerchantID")
    public String getMerchantID() { return merchantID; }
    @JsonProperty("MerchantID")
    public void setMerchantID(String value) { this.merchantID = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String acquirerID;
        private String acquirerPOIID;
        private TransactionIdentificationType acquirerTransactionID;
        private String approvalCode;
        private String hostReconciliationID;
        private String merchantID;
        
        private Builder() {}
        
        public Builder acquirerID(String acquirerID) {
            this.acquirerID = acquirerID;
            return this;
        }
        
        public Builder acquirerPOIID(String acquirerPOIID) {
            this.acquirerPOIID = acquirerPOIID;
            return this;
        }
        
        public Builder acquirerTransactionID(TransactionIdentificationType acquirerTransactionID) {
            this.acquirerTransactionID = acquirerTransactionID;
            return this;
        }
        
        public Builder approvalCode(String approvalCode) {
            this.approvalCode = approvalCode;
            return this;
        }
        
        public Builder hostReconciliationID(String hostReconciliationID) {
            this.hostReconciliationID = hostReconciliationID;
            return this;
        }
        
        public Builder merchantID(String merchantID) {
            this.merchantID = merchantID;
            return this;
        }
        
        public PaymentAcquirerData build() {
            PaymentAcquirerData result = new PaymentAcquirerData();
            result.setAcquirerID(this.acquirerID);
            result.setAcquirerPOIID(this.acquirerPOIID);
            result.setAcquirerTransactionID(this.acquirerTransactionID);
            result.setApprovalCode(this.approvalCode);
            result.setHostReconciliationID(this.hostReconciliationID);
            result.setMerchantID(this.merchantID);
            return result;
        }
    }
}
