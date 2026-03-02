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
 * Body of a repeated response message returned within a TransactionStatus response.
 */
public class RepeatedResponseMessageBody {
    private BatchResponse batchResponse;
    private CardAcquisitionResponse cardAcquisitionResponse;
    private CardReaderAPDUResponse[] cardReaderAPDUResponse;
    private LoyaltyResponse loyaltyResponse;
    private PaymentResponse paymentResponse;
    private ReconciliationResponse reconciliationResponse;
    private ReversalResponse reversalResponse;
    private StoredValueResponse storedValueResponse;

    @JsonProperty("BatchResponse")
    public BatchResponse getBatchResponse() { return batchResponse; }
    @JsonProperty("BatchResponse")
    public void setBatchResponse(BatchResponse value) { this.batchResponse = value; }

    @JsonProperty("CardAcquisitionResponse")
    public CardAcquisitionResponse getCardAcquisitionResponse() { return cardAcquisitionResponse; }
    @JsonProperty("CardAcquisitionResponse")
    public void setCardAcquisitionResponse(CardAcquisitionResponse value) { this.cardAcquisitionResponse = value; }

    @JsonProperty("CardReaderAPDUResponse")
    public CardReaderAPDUResponse[] getCardReaderAPDUResponse() { return cardReaderAPDUResponse; }
    @JsonProperty("CardReaderAPDUResponse")
    public void setCardReaderAPDUResponse(CardReaderAPDUResponse[] value) { this.cardReaderAPDUResponse = value; }

    @JsonProperty("LoyaltyResponse")
    public LoyaltyResponse getLoyaltyResponse() { return loyaltyResponse; }
    @JsonProperty("LoyaltyResponse")
    public void setLoyaltyResponse(LoyaltyResponse value) { this.loyaltyResponse = value; }

    @JsonProperty("PaymentResponse")
    public PaymentResponse getPaymentResponse() { return paymentResponse; }
    @JsonProperty("PaymentResponse")
    public void setPaymentResponse(PaymentResponse value) { this.paymentResponse = value; }

    @JsonProperty("ReconciliationResponse")
    public ReconciliationResponse getReconciliationResponse() { return reconciliationResponse; }
    @JsonProperty("ReconciliationResponse")
    public void setReconciliationResponse(ReconciliationResponse value) { this.reconciliationResponse = value; }

    @JsonProperty("ReversalResponse")
    public ReversalResponse getReversalResponse() { return reversalResponse; }
    @JsonProperty("ReversalResponse")
    public void setReversalResponse(ReversalResponse value) { this.reversalResponse = value; }

    @JsonProperty("StoredValueResponse")
    public StoredValueResponse getStoredValueResponse() { return storedValueResponse; }
    @JsonProperty("StoredValueResponse")
    public void setStoredValueResponse(StoredValueResponse value) { this.storedValueResponse = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private BatchResponse batchResponse;
        private CardAcquisitionResponse cardAcquisitionResponse;
        private CardReaderAPDUResponse[] cardReaderAPDUResponse;
        private LoyaltyResponse loyaltyResponse;
        private PaymentResponse paymentResponse;
        private ReconciliationResponse reconciliationResponse;
        private ReversalResponse reversalResponse;
        private StoredValueResponse storedValueResponse;
        
        private Builder() {}
        
        public Builder batchResponse(BatchResponse batchResponse) {
            this.batchResponse = batchResponse;
            return this;
        }
        
        public Builder cardAcquisitionResponse(CardAcquisitionResponse cardAcquisitionResponse) {
            this.cardAcquisitionResponse = cardAcquisitionResponse;
            return this;
        }
        
        public Builder cardReaderAPDUResponse(CardReaderAPDUResponse[] cardReaderAPDUResponse) {
            this.cardReaderAPDUResponse = cardReaderAPDUResponse;
            return this;
        }
        
        public Builder loyaltyResponse(LoyaltyResponse loyaltyResponse) {
            this.loyaltyResponse = loyaltyResponse;
            return this;
        }
        
        public Builder paymentResponse(PaymentResponse paymentResponse) {
            this.paymentResponse = paymentResponse;
            return this;
        }
        
        public Builder reconciliationResponse(ReconciliationResponse reconciliationResponse) {
            this.reconciliationResponse = reconciliationResponse;
            return this;
        }
        
        public Builder reversalResponse(ReversalResponse reversalResponse) {
            this.reversalResponse = reversalResponse;
            return this;
        }
        
        public Builder storedValueResponse(StoredValueResponse storedValueResponse) {
            this.storedValueResponse = storedValueResponse;
            return this;
        }
        
        public RepeatedResponseMessageBody build() {
            RepeatedResponseMessageBody result = new RepeatedResponseMessageBody();
            result.setBatchResponse(this.batchResponse);
            result.setCardAcquisitionResponse(this.cardAcquisitionResponse);
            result.setCardReaderAPDUResponse(this.cardReaderAPDUResponse);
            result.setLoyaltyResponse(this.loyaltyResponse);
            result.setPaymentResponse(this.paymentResponse);
            result.setReconciliationResponse(this.reconciliationResponse);
            result.setReversalResponse(this.reversalResponse);
            result.setStoredValueResponse(this.storedValueResponse);
            return result;
        }
    }
}
