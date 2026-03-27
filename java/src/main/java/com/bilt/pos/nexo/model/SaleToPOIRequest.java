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

public class SaleToPOIRequest {
    private MessageHeader messageHeader;
    private ContentInformationType securityTrailer;
    private AbortRequest abortRequest;
    private AdminRequest adminRequest;
    private BalanceInquiryRequest balanceInquiryRequest;
    private BatchRequest batchRequest;
    private CardAcquisitionRequest cardAcquisitionRequest;
    private CardReaderAPDURequest[] cardReaderAPDURequest;
    private CardReaderInitRequest cardReaderInitRequest;
    private CardReaderPowerOffRequest cardReaderPowerOffRequest;
    private DiagnosisRequest diagnosisRequest;
    private DisplayRequest displayRequest;
    private EnableServiceRequest enableServiceRequest;
    private EventNotification eventNotification;
    private GetTotalsRequest getTotalsRequest;
    private InputRequest inputRequest;
    private InputUpdate inputUpdate;
    private LoginRequest loginRequest;
    private LogoutRequest logoutRequest;
    private LoyaltyRequest loyaltyRequest;
    private PaymentRequest paymentRequest;
    private PINRequest pinRequest;
    private PrintRequest printRequest;
    private ReconciliationRequest reconciliationRequest;
    private ReversalRequest reversalRequest;
    private SoundRequest soundRequest;
    private StoredValueRequest storedValueRequest;
    private TransactionStatusRequest transactionStatusRequest;
    private TransmitRequest transmitRequest;

    @JsonProperty("MessageHeader")
    public MessageHeader getMessageHeader() { return messageHeader; }
    @JsonProperty("MessageHeader")
    public void setMessageHeader(MessageHeader value) { this.messageHeader = value; }

    @JsonProperty("SecurityTrailer")
    public ContentInformationType getSecurityTrailer() { return securityTrailer; }
    @JsonProperty("SecurityTrailer")
    public void setSecurityTrailer(ContentInformationType value) { this.securityTrailer = value; }

    @JsonProperty("AbortRequest")
    public AbortRequest getAbortRequest() { return abortRequest; }
    @JsonProperty("AbortRequest")
    public void setAbortRequest(AbortRequest value) { this.abortRequest = value; }

    @JsonProperty("AdminRequest")
    public AdminRequest getAdminRequest() { return adminRequest; }
    @JsonProperty("AdminRequest")
    public void setAdminRequest(AdminRequest value) { this.adminRequest = value; }

    @JsonProperty("BalanceInquiryRequest")
    public BalanceInquiryRequest getBalanceInquiryRequest() { return balanceInquiryRequest; }
    @JsonProperty("BalanceInquiryRequest")
    public void setBalanceInquiryRequest(BalanceInquiryRequest value) { this.balanceInquiryRequest = value; }

    @JsonProperty("BatchRequest")
    public BatchRequest getBatchRequest() { return batchRequest; }
    @JsonProperty("BatchRequest")
    public void setBatchRequest(BatchRequest value) { this.batchRequest = value; }

    @JsonProperty("CardAcquisitionRequest")
    public CardAcquisitionRequest getCardAcquisitionRequest() { return cardAcquisitionRequest; }
    @JsonProperty("CardAcquisitionRequest")
    public void setCardAcquisitionRequest(CardAcquisitionRequest value) { this.cardAcquisitionRequest = value; }

    @JsonProperty("CardReaderAPDURequest")
    public CardReaderAPDURequest[] getCardReaderAPDURequest() { return cardReaderAPDURequest; }
    @JsonProperty("CardReaderAPDURequest")
    public void setCardReaderAPDURequest(CardReaderAPDURequest[] value) { this.cardReaderAPDURequest = value; }

    @JsonProperty("CardReaderInitRequest")
    public CardReaderInitRequest getCardReaderInitRequest() { return cardReaderInitRequest; }
    @JsonProperty("CardReaderInitRequest")
    public void setCardReaderInitRequest(CardReaderInitRequest value) { this.cardReaderInitRequest = value; }

    @JsonProperty("CardReaderPowerOffRequest")
    public CardReaderPowerOffRequest getCardReaderPowerOffRequest() { return cardReaderPowerOffRequest; }
    @JsonProperty("CardReaderPowerOffRequest")
    public void setCardReaderPowerOffRequest(CardReaderPowerOffRequest value) { this.cardReaderPowerOffRequest = value; }

    @JsonProperty("DiagnosisRequest")
    public DiagnosisRequest getDiagnosisRequest() { return diagnosisRequest; }
    @JsonProperty("DiagnosisRequest")
    public void setDiagnosisRequest(DiagnosisRequest value) { this.diagnosisRequest = value; }

    @JsonProperty("DisplayRequest")
    public DisplayRequest getDisplayRequest() { return displayRequest; }
    @JsonProperty("DisplayRequest")
    public void setDisplayRequest(DisplayRequest value) { this.displayRequest = value; }

    @JsonProperty("EnableServiceRequest")
    public EnableServiceRequest getEnableServiceRequest() { return enableServiceRequest; }
    @JsonProperty("EnableServiceRequest")
    public void setEnableServiceRequest(EnableServiceRequest value) { this.enableServiceRequest = value; }

    @JsonProperty("EventNotification")
    public EventNotification getEventNotification() { return eventNotification; }
    @JsonProperty("EventNotification")
    public void setEventNotification(EventNotification value) { this.eventNotification = value; }

    @JsonProperty("GetTotalsRequest")
    public GetTotalsRequest getGetTotalsRequest() { return getTotalsRequest; }
    @JsonProperty("GetTotalsRequest")
    public void setGetTotalsRequest(GetTotalsRequest value) { this.getTotalsRequest = value; }

    @JsonProperty("InputRequest")
    public InputRequest getInputRequest() { return inputRequest; }
    @JsonProperty("InputRequest")
    public void setInputRequest(InputRequest value) { this.inputRequest = value; }

    @JsonProperty("InputUpdate")
    public InputUpdate getInputUpdate() { return inputUpdate; }
    @JsonProperty("InputUpdate")
    public void setInputUpdate(InputUpdate value) { this.inputUpdate = value; }

    @JsonProperty("LoginRequest")
    public LoginRequest getLoginRequest() { return loginRequest; }
    @JsonProperty("LoginRequest")
    public void setLoginRequest(LoginRequest value) { this.loginRequest = value; }

    @JsonProperty("LogoutRequest")
    public LogoutRequest getLogoutRequest() { return logoutRequest; }
    @JsonProperty("LogoutRequest")
    public void setLogoutRequest(LogoutRequest value) { this.logoutRequest = value; }

    @JsonProperty("LoyaltyRequest")
    public LoyaltyRequest getLoyaltyRequest() { return loyaltyRequest; }
    @JsonProperty("LoyaltyRequest")
    public void setLoyaltyRequest(LoyaltyRequest value) { this.loyaltyRequest = value; }

    @JsonProperty("PaymentRequest")
    public PaymentRequest getPaymentRequest() { return paymentRequest; }
    @JsonProperty("PaymentRequest")
    public void setPaymentRequest(PaymentRequest value) { this.paymentRequest = value; }

    @JsonProperty("PINRequest")
    public PINRequest getPinRequest() { return pinRequest; }
    @JsonProperty("PINRequest")
    public void setPinRequest(PINRequest value) { this.pinRequest = value; }

    @JsonProperty("PrintRequest")
    public PrintRequest getPrintRequest() { return printRequest; }
    @JsonProperty("PrintRequest")
    public void setPrintRequest(PrintRequest value) { this.printRequest = value; }

    @JsonProperty("ReconciliationRequest")
    public ReconciliationRequest getReconciliationRequest() { return reconciliationRequest; }
    @JsonProperty("ReconciliationRequest")
    public void setReconciliationRequest(ReconciliationRequest value) { this.reconciliationRequest = value; }

    @JsonProperty("ReversalRequest")
    public ReversalRequest getReversalRequest() { return reversalRequest; }
    @JsonProperty("ReversalRequest")
    public void setReversalRequest(ReversalRequest value) { this.reversalRequest = value; }

    @JsonProperty("SoundRequest")
    public SoundRequest getSoundRequest() { return soundRequest; }
    @JsonProperty("SoundRequest")
    public void setSoundRequest(SoundRequest value) { this.soundRequest = value; }

    @JsonProperty("StoredValueRequest")
    public StoredValueRequest getStoredValueRequest() { return storedValueRequest; }
    @JsonProperty("StoredValueRequest")
    public void setStoredValueRequest(StoredValueRequest value) { this.storedValueRequest = value; }

    @JsonProperty("TransactionStatusRequest")
    public TransactionStatusRequest getTransactionStatusRequest() { return transactionStatusRequest; }
    @JsonProperty("TransactionStatusRequest")
    public void setTransactionStatusRequest(TransactionStatusRequest value) { this.transactionStatusRequest = value; }

    @JsonProperty("TransmitRequest")
    public TransmitRequest getTransmitRequest() { return transmitRequest; }
    @JsonProperty("TransmitRequest")
    public void setTransmitRequest(TransmitRequest value) { this.transmitRequest = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private MessageHeader messageHeader;
        private ContentInformationType securityTrailer;
        private AbortRequest abortRequest;
        private AdminRequest adminRequest;
        private BalanceInquiryRequest balanceInquiryRequest;
        private BatchRequest batchRequest;
        private CardAcquisitionRequest cardAcquisitionRequest;
        private CardReaderAPDURequest[] cardReaderAPDURequest;
        private CardReaderInitRequest cardReaderInitRequest;
        private CardReaderPowerOffRequest cardReaderPowerOffRequest;
        private DiagnosisRequest diagnosisRequest;
        private DisplayRequest displayRequest;
        private EnableServiceRequest enableServiceRequest;
        private EventNotification eventNotification;
        private GetTotalsRequest getTotalsRequest;
        private InputRequest inputRequest;
        private InputUpdate inputUpdate;
        private LoginRequest loginRequest;
        private LogoutRequest logoutRequest;
        private LoyaltyRequest loyaltyRequest;
        private PaymentRequest paymentRequest;
        private PINRequest pinRequest;
        private PrintRequest printRequest;
        private ReconciliationRequest reconciliationRequest;
        private ReversalRequest reversalRequest;
        private SoundRequest soundRequest;
        private StoredValueRequest storedValueRequest;
        private TransactionStatusRequest transactionStatusRequest;
        private TransmitRequest transmitRequest;
        
        private Builder() {}
        
        public Builder messageHeader(MessageHeader messageHeader) {
            this.messageHeader = messageHeader;
            return this;
        }
        
        public Builder securityTrailer(ContentInformationType securityTrailer) {
            this.securityTrailer = securityTrailer;
            return this;
        }
        
        public Builder abortRequest(AbortRequest abortRequest) {
            this.abortRequest = abortRequest;
            return this;
        }
        
        public Builder adminRequest(AdminRequest adminRequest) {
            this.adminRequest = adminRequest;
            return this;
        }
        
        public Builder balanceInquiryRequest(BalanceInquiryRequest balanceInquiryRequest) {
            this.balanceInquiryRequest = balanceInquiryRequest;
            return this;
        }
        
        public Builder batchRequest(BatchRequest batchRequest) {
            this.batchRequest = batchRequest;
            return this;
        }
        
        public Builder cardAcquisitionRequest(CardAcquisitionRequest cardAcquisitionRequest) {
            this.cardAcquisitionRequest = cardAcquisitionRequest;
            return this;
        }
        
        public Builder cardReaderAPDURequest(CardReaderAPDURequest[] cardReaderAPDURequest) {
            this.cardReaderAPDURequest = cardReaderAPDURequest;
            return this;
        }
        
        public Builder cardReaderInitRequest(CardReaderInitRequest cardReaderInitRequest) {
            this.cardReaderInitRequest = cardReaderInitRequest;
            return this;
        }
        
        public Builder cardReaderPowerOffRequest(CardReaderPowerOffRequest cardReaderPowerOffRequest) {
            this.cardReaderPowerOffRequest = cardReaderPowerOffRequest;
            return this;
        }
        
        public Builder diagnosisRequest(DiagnosisRequest diagnosisRequest) {
            this.diagnosisRequest = diagnosisRequest;
            return this;
        }
        
        public Builder displayRequest(DisplayRequest displayRequest) {
            this.displayRequest = displayRequest;
            return this;
        }
        
        public Builder enableServiceRequest(EnableServiceRequest enableServiceRequest) {
            this.enableServiceRequest = enableServiceRequest;
            return this;
        }
        
        public Builder eventNotification(EventNotification eventNotification) {
            this.eventNotification = eventNotification;
            return this;
        }
        
        public Builder getTotalsRequest(GetTotalsRequest getTotalsRequest) {
            this.getTotalsRequest = getTotalsRequest;
            return this;
        }
        
        public Builder inputRequest(InputRequest inputRequest) {
            this.inputRequest = inputRequest;
            return this;
        }
        
        public Builder inputUpdate(InputUpdate inputUpdate) {
            this.inputUpdate = inputUpdate;
            return this;
        }
        
        public Builder loginRequest(LoginRequest loginRequest) {
            this.loginRequest = loginRequest;
            return this;
        }
        
        public Builder logoutRequest(LogoutRequest logoutRequest) {
            this.logoutRequest = logoutRequest;
            return this;
        }
        
        public Builder loyaltyRequest(LoyaltyRequest loyaltyRequest) {
            this.loyaltyRequest = loyaltyRequest;
            return this;
        }
        
        public Builder paymentRequest(PaymentRequest paymentRequest) {
            this.paymentRequest = paymentRequest;
            return this;
        }
        
        public Builder pinRequest(PINRequest pinRequest) {
            this.pinRequest = pinRequest;
            return this;
        }
        
        public Builder printRequest(PrintRequest printRequest) {
            this.printRequest = printRequest;
            return this;
        }
        
        public Builder reconciliationRequest(ReconciliationRequest reconciliationRequest) {
            this.reconciliationRequest = reconciliationRequest;
            return this;
        }
        
        public Builder reversalRequest(ReversalRequest reversalRequest) {
            this.reversalRequest = reversalRequest;
            return this;
        }
        
        public Builder soundRequest(SoundRequest soundRequest) {
            this.soundRequest = soundRequest;
            return this;
        }
        
        public Builder storedValueRequest(StoredValueRequest storedValueRequest) {
            this.storedValueRequest = storedValueRequest;
            return this;
        }
        
        public Builder transactionStatusRequest(TransactionStatusRequest transactionStatusRequest) {
            this.transactionStatusRequest = transactionStatusRequest;
            return this;
        }
        
        public Builder transmitRequest(TransmitRequest transmitRequest) {
            this.transmitRequest = transmitRequest;
            return this;
        }
        
        public SaleToPOIRequest build() {
            SaleToPOIRequest result = new SaleToPOIRequest();
            result.setMessageHeader(this.messageHeader);
            result.setSecurityTrailer(this.securityTrailer);
            result.setAbortRequest(this.abortRequest);
            result.setAdminRequest(this.adminRequest);
            result.setBalanceInquiryRequest(this.balanceInquiryRequest);
            result.setBatchRequest(this.batchRequest);
            result.setCardAcquisitionRequest(this.cardAcquisitionRequest);
            result.setCardReaderAPDURequest(this.cardReaderAPDURequest);
            result.setCardReaderInitRequest(this.cardReaderInitRequest);
            result.setCardReaderPowerOffRequest(this.cardReaderPowerOffRequest);
            result.setDiagnosisRequest(this.diagnosisRequest);
            result.setDisplayRequest(this.displayRequest);
            result.setEnableServiceRequest(this.enableServiceRequest);
            result.setEventNotification(this.eventNotification);
            result.setGetTotalsRequest(this.getTotalsRequest);
            result.setInputRequest(this.inputRequest);
            result.setInputUpdate(this.inputUpdate);
            result.setLoginRequest(this.loginRequest);
            result.setLogoutRequest(this.logoutRequest);
            result.setLoyaltyRequest(this.loyaltyRequest);
            result.setPaymentRequest(this.paymentRequest);
            result.setPinRequest(this.pinRequest);
            result.setPrintRequest(this.printRequest);
            result.setReconciliationRequest(this.reconciliationRequest);
            result.setReversalRequest(this.reversalRequest);
            result.setSoundRequest(this.soundRequest);
            result.setStoredValueRequest(this.storedValueRequest);
            result.setTransactionStatusRequest(this.transactionStatusRequest);
            result.setTransmitRequest(this.transmitRequest);
            return result;
        }
    }
}
