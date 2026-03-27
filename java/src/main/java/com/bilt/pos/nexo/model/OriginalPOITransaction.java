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
 * Reference to a previous POI transaction, used for reversals, completions, refunds, or to
 * reuse card data from a prior transaction.
 */
public class OriginalPOITransaction {
    private String acquirerID;
    private Double amountValue;
    private String approvalCode;
    private String customerLanguage;
    private TransactionIdentificationType hostTransactionID;
    private String poiid;
    private TransactionIdentificationType poiTransactionID;
    private Boolean reuseCardDataFlag;
    private String saleID;

    /**
     * Acquirer used for the original transaction, when the POI is multi-acquirer.
     */
    @JsonProperty("AcquirerID")
    public String getAcquirerID() { return acquirerID; }
    @JsonProperty("AcquirerID")
    public void setAcquirerID(String value) { this.acquirerID = value; }

    /**
     * Amount of the original transaction. Used in reversal when POITransactionID is absent.
     */
    @JsonProperty("AmountValue")
    public Double getAmountValue() { return amountValue; }
    @JsonProperty("AmountValue")
    public void setAmountValue(Double value) { this.amountValue = value; }

    /**
     * Approval code from the original transaction, used for voice authorisation referrals.
     */
    @JsonProperty("ApprovalCode")
    public String getApprovalCode() { return approvalCode; }
    @JsonProperty("ApprovalCode")
    public void setApprovalCode(String value) { this.approvalCode = value; }

    @JsonProperty("CustomerLanguage")
    public String getCustomerLanguage() { return customerLanguage; }
    @JsonProperty("CustomerLanguage")
    public void setCustomerLanguage(String value) { this.customerLanguage = value; }

    @JsonProperty("HostTransactionID")
    public TransactionIdentificationType getHostTransactionID() { return hostTransactionID; }
    @JsonProperty("HostTransactionID")
    public void setHostTransactionID(TransactionIdentificationType value) { this.hostTransactionID = value; }

    /**
     * Identification of the POI Terminal that performed the original transaction. Required when
     * the original transaction was on a different POI Terminal.
     */
    @JsonProperty("POIID")
    public String getPoiid() { return poiid; }
    @JsonProperty("POIID")
    public void setPoiid(String value) { this.poiid = value; }

    @JsonProperty("POITransactionID")
    public TransactionIdentificationType getPoiTransactionID() { return poiTransactionID; }
    @JsonProperty("POITransactionID")
    public void setPoiTransactionID(TransactionIdentificationType value) { this.poiTransactionID = value; }

    /**
     * When true, the POI reuses the card data from the original transaction without reading the
     * card again. Default true.
     */
    @JsonProperty("ReuseCardDataFlag")
    public Boolean getReuseCardDataFlag() { return reuseCardDataFlag; }
    @JsonProperty("ReuseCardDataFlag")
    public void setReuseCardDataFlag(Boolean value) { this.reuseCardDataFlag = value; }

    /**
     * Identification of the Sale Terminal that performed the original transaction. Required
     * when the reversal is sent from a different Sale Terminal.
     */
    @JsonProperty("SaleID")
    public String getSaleID() { return saleID; }
    @JsonProperty("SaleID")
    public void setSaleID(String value) { this.saleID = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String acquirerID;
        private Double amountValue;
        private String approvalCode;
        private String customerLanguage;
        private TransactionIdentificationType hostTransactionID;
        private String poiid;
        private TransactionIdentificationType poiTransactionID;
        private Boolean reuseCardDataFlag;
        private String saleID;
        
        private Builder() {}
        
        public Builder acquirerID(String acquirerID) {
            this.acquirerID = acquirerID;
            return this;
        }
        
        public Builder amountValue(Double amountValue) {
            this.amountValue = amountValue;
            return this;
        }
        
        public Builder approvalCode(String approvalCode) {
            this.approvalCode = approvalCode;
            return this;
        }
        
        public Builder customerLanguage(String customerLanguage) {
            this.customerLanguage = customerLanguage;
            return this;
        }
        
        public Builder hostTransactionID(TransactionIdentificationType hostTransactionID) {
            this.hostTransactionID = hostTransactionID;
            return this;
        }
        
        public Builder poiid(String poiid) {
            this.poiid = poiid;
            return this;
        }
        
        public Builder poiTransactionID(TransactionIdentificationType poiTransactionID) {
            this.poiTransactionID = poiTransactionID;
            return this;
        }
        
        public Builder reuseCardDataFlag(Boolean reuseCardDataFlag) {
            this.reuseCardDataFlag = reuseCardDataFlag;
            return this;
        }
        
        public Builder saleID(String saleID) {
            this.saleID = saleID;
            return this;
        }
        
        public OriginalPOITransaction build() {
            OriginalPOITransaction result = new OriginalPOITransaction();
            result.setAcquirerID(this.acquirerID);
            result.setAmountValue(this.amountValue);
            result.setApprovalCode(this.approvalCode);
            result.setCustomerLanguage(this.customerLanguage);
            result.setHostTransactionID(this.hostTransactionID);
            result.setPoiid(this.poiid);
            result.setPoiTransactionID(this.poiTransactionID);
            result.setReuseCardDataFlag(this.reuseCardDataFlag);
            result.setSaleID(this.saleID);
            return result;
        }
    }
}
