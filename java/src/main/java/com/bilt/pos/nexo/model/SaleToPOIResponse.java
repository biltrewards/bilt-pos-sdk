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

public class SaleToPOIResponse {
    private MessageHeader messageHeader;
    private ContentInformationType securityTrailer;
    private AdminResponse adminResponse;
    private BalanceInquiryResponse balanceInquiryResponse;
    private BatchResponse batchResponse;
    private CardAcquisitionResponse cardAcquisitionResponse;
    private CardReaderAPDUResponse[] cardReaderAPDUResponse;
    private CardReaderInitResponse cardReaderInitResponse;
    private CardReaderPowerOffResponse cardReaderPowerOffResponse;
    private DiagnosisResponse diagnosisResponse;
    private DisplayResponse displayResponse;
    private EnableServiceResponse enableServiceResponse;
    private GetTotalsResponse getTotalsResponse;
    private InputResponse inputResponse;
    private LoginResponse loginResponse;
    private LogoutResponse logoutResponse;
    private LoyaltyResponse loyaltyResponse;
    private PaymentResponse paymentResponse;
    private PINResponse pinResponse;
    private PrintResponse printResponse;
    private ReconciliationResponse reconciliationResponse;
    private ReversalResponse reversalResponse;
    private SoundResponse soundResponse;
    private StoredValueResponse storedValueResponse;
    private TransactionStatusResponse transactionStatusResponse;
    private TransmitResponse transmitResponse;

    @JsonProperty("MessageHeader")
    public MessageHeader getMessageHeader() { return messageHeader; }
    @JsonProperty("MessageHeader")
    public void setMessageHeader(MessageHeader value) { this.messageHeader = value; }

    @JsonProperty("SecurityTrailer")
    public ContentInformationType getSecurityTrailer() { return securityTrailer; }
    @JsonProperty("SecurityTrailer")
    public void setSecurityTrailer(ContentInformationType value) { this.securityTrailer = value; }

    @JsonProperty("AdminResponse")
    public AdminResponse getAdminResponse() { return adminResponse; }
    @JsonProperty("AdminResponse")
    public void setAdminResponse(AdminResponse value) { this.adminResponse = value; }

    @JsonProperty("BalanceInquiryResponse")
    public BalanceInquiryResponse getBalanceInquiryResponse() { return balanceInquiryResponse; }
    @JsonProperty("BalanceInquiryResponse")
    public void setBalanceInquiryResponse(BalanceInquiryResponse value) { this.balanceInquiryResponse = value; }

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

    @JsonProperty("CardReaderInitResponse")
    public CardReaderInitResponse getCardReaderInitResponse() { return cardReaderInitResponse; }
    @JsonProperty("CardReaderInitResponse")
    public void setCardReaderInitResponse(CardReaderInitResponse value) { this.cardReaderInitResponse = value; }

    @JsonProperty("CardReaderPowerOffResponse")
    public CardReaderPowerOffResponse getCardReaderPowerOffResponse() { return cardReaderPowerOffResponse; }
    @JsonProperty("CardReaderPowerOffResponse")
    public void setCardReaderPowerOffResponse(CardReaderPowerOffResponse value) { this.cardReaderPowerOffResponse = value; }

    @JsonProperty("DiagnosisResponse")
    public DiagnosisResponse getDiagnosisResponse() { return diagnosisResponse; }
    @JsonProperty("DiagnosisResponse")
    public void setDiagnosisResponse(DiagnosisResponse value) { this.diagnosisResponse = value; }

    @JsonProperty("DisplayResponse")
    public DisplayResponse getDisplayResponse() { return displayResponse; }
    @JsonProperty("DisplayResponse")
    public void setDisplayResponse(DisplayResponse value) { this.displayResponse = value; }

    @JsonProperty("EnableServiceResponse")
    public EnableServiceResponse getEnableServiceResponse() { return enableServiceResponse; }
    @JsonProperty("EnableServiceResponse")
    public void setEnableServiceResponse(EnableServiceResponse value) { this.enableServiceResponse = value; }

    @JsonProperty("GetTotalsResponse")
    public GetTotalsResponse getGetTotalsResponse() { return getTotalsResponse; }
    @JsonProperty("GetTotalsResponse")
    public void setGetTotalsResponse(GetTotalsResponse value) { this.getTotalsResponse = value; }

    @JsonProperty("InputResponse")
    public InputResponse getInputResponse() { return inputResponse; }
    @JsonProperty("InputResponse")
    public void setInputResponse(InputResponse value) { this.inputResponse = value; }

    @JsonProperty("LoginResponse")
    public LoginResponse getLoginResponse() { return loginResponse; }
    @JsonProperty("LoginResponse")
    public void setLoginResponse(LoginResponse value) { this.loginResponse = value; }

    @JsonProperty("LogoutResponse")
    public LogoutResponse getLogoutResponse() { return logoutResponse; }
    @JsonProperty("LogoutResponse")
    public void setLogoutResponse(LogoutResponse value) { this.logoutResponse = value; }

    @JsonProperty("LoyaltyResponse")
    public LoyaltyResponse getLoyaltyResponse() { return loyaltyResponse; }
    @JsonProperty("LoyaltyResponse")
    public void setLoyaltyResponse(LoyaltyResponse value) { this.loyaltyResponse = value; }

    @JsonProperty("PaymentResponse")
    public PaymentResponse getPaymentResponse() { return paymentResponse; }
    @JsonProperty("PaymentResponse")
    public void setPaymentResponse(PaymentResponse value) { this.paymentResponse = value; }

    @JsonProperty("PINResponse")
    public PINResponse getPinResponse() { return pinResponse; }
    @JsonProperty("PINResponse")
    public void setPinResponse(PINResponse value) { this.pinResponse = value; }

    @JsonProperty("PrintResponse")
    public PrintResponse getPrintResponse() { return printResponse; }
    @JsonProperty("PrintResponse")
    public void setPrintResponse(PrintResponse value) { this.printResponse = value; }

    @JsonProperty("ReconciliationResponse")
    public ReconciliationResponse getReconciliationResponse() { return reconciliationResponse; }
    @JsonProperty("ReconciliationResponse")
    public void setReconciliationResponse(ReconciliationResponse value) { this.reconciliationResponse = value; }

    @JsonProperty("ReversalResponse")
    public ReversalResponse getReversalResponse() { return reversalResponse; }
    @JsonProperty("ReversalResponse")
    public void setReversalResponse(ReversalResponse value) { this.reversalResponse = value; }

    @JsonProperty("SoundResponse")
    public SoundResponse getSoundResponse() { return soundResponse; }
    @JsonProperty("SoundResponse")
    public void setSoundResponse(SoundResponse value) { this.soundResponse = value; }

    @JsonProperty("StoredValueResponse")
    public StoredValueResponse getStoredValueResponse() { return storedValueResponse; }
    @JsonProperty("StoredValueResponse")
    public void setStoredValueResponse(StoredValueResponse value) { this.storedValueResponse = value; }

    @JsonProperty("TransactionStatusResponse")
    public TransactionStatusResponse getTransactionStatusResponse() { return transactionStatusResponse; }
    @JsonProperty("TransactionStatusResponse")
    public void setTransactionStatusResponse(TransactionStatusResponse value) { this.transactionStatusResponse = value; }

    @JsonProperty("TransmitResponse")
    public TransmitResponse getTransmitResponse() { return transmitResponse; }
    @JsonProperty("TransmitResponse")
    public void setTransmitResponse(TransmitResponse value) { this.transmitResponse = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private MessageHeader messageHeader;
        private ContentInformationType securityTrailer;
        private AdminResponse adminResponse;
        private BalanceInquiryResponse balanceInquiryResponse;
        private BatchResponse batchResponse;
        private CardAcquisitionResponse cardAcquisitionResponse;
        private CardReaderAPDUResponse[] cardReaderAPDUResponse;
        private CardReaderInitResponse cardReaderInitResponse;
        private CardReaderPowerOffResponse cardReaderPowerOffResponse;
        private DiagnosisResponse diagnosisResponse;
        private DisplayResponse displayResponse;
        private EnableServiceResponse enableServiceResponse;
        private GetTotalsResponse getTotalsResponse;
        private InputResponse inputResponse;
        private LoginResponse loginResponse;
        private LogoutResponse logoutResponse;
        private LoyaltyResponse loyaltyResponse;
        private PaymentResponse paymentResponse;
        private PINResponse pinResponse;
        private PrintResponse printResponse;
        private ReconciliationResponse reconciliationResponse;
        private ReversalResponse reversalResponse;
        private SoundResponse soundResponse;
        private StoredValueResponse storedValueResponse;
        private TransactionStatusResponse transactionStatusResponse;
        private TransmitResponse transmitResponse;
        
        private Builder() {}
        
        public Builder messageHeader(MessageHeader messageHeader) {
            this.messageHeader = messageHeader;
            return this;
        }
        
        public Builder securityTrailer(ContentInformationType securityTrailer) {
            this.securityTrailer = securityTrailer;
            return this;
        }
        
        public Builder adminResponse(AdminResponse adminResponse) {
            this.adminResponse = adminResponse;
            return this;
        }
        
        public Builder balanceInquiryResponse(BalanceInquiryResponse balanceInquiryResponse) {
            this.balanceInquiryResponse = balanceInquiryResponse;
            return this;
        }
        
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
        
        public Builder cardReaderInitResponse(CardReaderInitResponse cardReaderInitResponse) {
            this.cardReaderInitResponse = cardReaderInitResponse;
            return this;
        }
        
        public Builder cardReaderPowerOffResponse(CardReaderPowerOffResponse cardReaderPowerOffResponse) {
            this.cardReaderPowerOffResponse = cardReaderPowerOffResponse;
            return this;
        }
        
        public Builder diagnosisResponse(DiagnosisResponse diagnosisResponse) {
            this.diagnosisResponse = diagnosisResponse;
            return this;
        }
        
        public Builder displayResponse(DisplayResponse displayResponse) {
            this.displayResponse = displayResponse;
            return this;
        }
        
        public Builder enableServiceResponse(EnableServiceResponse enableServiceResponse) {
            this.enableServiceResponse = enableServiceResponse;
            return this;
        }
        
        public Builder getTotalsResponse(GetTotalsResponse getTotalsResponse) {
            this.getTotalsResponse = getTotalsResponse;
            return this;
        }
        
        public Builder inputResponse(InputResponse inputResponse) {
            this.inputResponse = inputResponse;
            return this;
        }
        
        public Builder loginResponse(LoginResponse loginResponse) {
            this.loginResponse = loginResponse;
            return this;
        }
        
        public Builder logoutResponse(LogoutResponse logoutResponse) {
            this.logoutResponse = logoutResponse;
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
        
        public Builder pinResponse(PINResponse pinResponse) {
            this.pinResponse = pinResponse;
            return this;
        }
        
        public Builder printResponse(PrintResponse printResponse) {
            this.printResponse = printResponse;
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
        
        public Builder soundResponse(SoundResponse soundResponse) {
            this.soundResponse = soundResponse;
            return this;
        }
        
        public Builder storedValueResponse(StoredValueResponse storedValueResponse) {
            this.storedValueResponse = storedValueResponse;
            return this;
        }
        
        public Builder transactionStatusResponse(TransactionStatusResponse transactionStatusResponse) {
            this.transactionStatusResponse = transactionStatusResponse;
            return this;
        }
        
        public Builder transmitResponse(TransmitResponse transmitResponse) {
            this.transmitResponse = transmitResponse;
            return this;
        }
        
        public SaleToPOIResponse build() {
            SaleToPOIResponse result = new SaleToPOIResponse();
            result.setMessageHeader(this.messageHeader);
            result.setSecurityTrailer(this.securityTrailer);
            result.setAdminResponse(this.adminResponse);
            result.setBalanceInquiryResponse(this.balanceInquiryResponse);
            result.setBatchResponse(this.batchResponse);
            result.setCardAcquisitionResponse(this.cardAcquisitionResponse);
            result.setCardReaderAPDUResponse(this.cardReaderAPDUResponse);
            result.setCardReaderInitResponse(this.cardReaderInitResponse);
            result.setCardReaderPowerOffResponse(this.cardReaderPowerOffResponse);
            result.setDiagnosisResponse(this.diagnosisResponse);
            result.setDisplayResponse(this.displayResponse);
            result.setEnableServiceResponse(this.enableServiceResponse);
            result.setGetTotalsResponse(this.getTotalsResponse);
            result.setInputResponse(this.inputResponse);
            result.setLoginResponse(this.loginResponse);
            result.setLogoutResponse(this.logoutResponse);
            result.setLoyaltyResponse(this.loyaltyResponse);
            result.setPaymentResponse(this.paymentResponse);
            result.setPinResponse(this.pinResponse);
            result.setPrintResponse(this.printResponse);
            result.setReconciliationResponse(this.reconciliationResponse);
            result.setReversalResponse(this.reversalResponse);
            result.setSoundResponse(this.soundResponse);
            result.setStoredValueResponse(this.storedValueResponse);
            result.setTransactionStatusResponse(this.transactionStatusResponse);
            result.setTransmitResponse(this.transmitResponse);
            return result;
        }
    }
}
