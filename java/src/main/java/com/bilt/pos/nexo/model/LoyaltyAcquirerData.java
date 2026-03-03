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
 * Data related to the loyalty Acquirer's response for a loyalty transaction.
 */
public class LoyaltyAcquirerData {
    private String approvalCode;
    private String hostReconciliationID;
    private String loyaltyAcquirerID;
    private TransactionIdentificationType loyaltyTransactionID;

    /**
     * Code assigned to the loyalty transaction by the Acquirer upon approval.
     */
    @JsonProperty("ApprovalCode")
    public String getApprovalCode() { return approvalCode; }
    @JsonProperty("ApprovalCode")
    public void setApprovalCode(String value) { this.approvalCode = value; }

    /**
     * Identification of the loyalty Acquirer reconciliation period for this transaction.
     */
    @JsonProperty("HostReconciliationID")
    public String getHostReconciliationID() { return hostReconciliationID; }
    @JsonProperty("HostReconciliationID")
    public void setHostReconciliationID(String value) { this.hostReconciliationID = value; }

    /**
     * Identification of the loyalty Acquirer. Present in multi-acquirer environments.
     */
    @JsonProperty("LoyaltyAcquirerID")
    public String getLoyaltyAcquirerID() { return loyaltyAcquirerID; }
    @JsonProperty("LoyaltyAcquirerID")
    public void setLoyaltyAcquirerID(String value) { this.loyaltyAcquirerID = value; }

    @JsonProperty("LoyaltyTransactionID")
    public TransactionIdentificationType getLoyaltyTransactionID() { return loyaltyTransactionID; }
    @JsonProperty("LoyaltyTransactionID")
    public void setLoyaltyTransactionID(TransactionIdentificationType value) { this.loyaltyTransactionID = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String approvalCode;
        private String hostReconciliationID;
        private String loyaltyAcquirerID;
        private TransactionIdentificationType loyaltyTransactionID;
        
        private Builder() {}
        
        public Builder approvalCode(String approvalCode) {
            this.approvalCode = approvalCode;
            return this;
        }
        
        public Builder hostReconciliationID(String hostReconciliationID) {
            this.hostReconciliationID = hostReconciliationID;
            return this;
        }
        
        public Builder loyaltyAcquirerID(String loyaltyAcquirerID) {
            this.loyaltyAcquirerID = loyaltyAcquirerID;
            return this;
        }
        
        public Builder loyaltyTransactionID(TransactionIdentificationType loyaltyTransactionID) {
            this.loyaltyTransactionID = loyaltyTransactionID;
            return this;
        }
        
        public LoyaltyAcquirerData build() {
            LoyaltyAcquirerData result = new LoyaltyAcquirerData();
            result.setApprovalCode(this.approvalCode);
            result.setHostReconciliationID(this.hostReconciliationID);
            result.setLoyaltyAcquirerID(this.loyaltyAcquirerID);
            result.setLoyaltyTransactionID(this.loyaltyTransactionID);
            return result;
        }
    }
}
