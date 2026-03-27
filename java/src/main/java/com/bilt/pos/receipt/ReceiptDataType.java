/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   This file is auto-generated from the receipt.xsd schema.
 *   Do not modify manually — re-run code generation instead.
 */

package com.bilt.pos.receipt;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ReceiptDataType complex type</p>.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.</p>
 * 
 * <pre>{@code
 * <complexType name="ReceiptDataType">
 *   <complexContent>
 *     <restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       <all>
 *         <element name="receiptCopy" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="offlineDeletedReceipt" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="offlineDeletedTimeStamp" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="transactionType" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="transactionTimeStamp" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="transactionResult" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="transactionResultCode" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="rejectionReason" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="merchantName" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="merchantAddress1" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="merchantAddress2" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="merchantAddress3" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="merchantAddress4" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="merchantPhoneNumber" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="merchantRegNumber" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="merchantID" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="currency" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="transactionAmount" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="totalAmount" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="taxAmount" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="cashbackAmount" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="surchargeAmount" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="tipsAmount" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="cardBrand" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="maskedPAN" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="psn" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="expiryDate" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="paymentInstrument" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="apmName" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="cvmType" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="encryptedPan" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="encryptedKsn" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="terminalID" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="poiSerialNumber" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="acquirerName" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="acquirerID" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="authCode" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="authSource" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="refNumber" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="atc" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="aed" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="aid" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="acType" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="acValue" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="arc" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="tvr" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="tsi" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *       </all>
 *     </restriction>
 *   </complexContent>
 * </complexType>
 * }</pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ReceiptDataType", propOrder = {

})
public class ReceiptDataType {

    protected String receiptCopy;
    protected String offlineDeletedReceipt;
    protected String offlineDeletedTimeStamp;
    protected String transactionType;
    protected String transactionTimeStamp;
    protected String transactionResult;
    protected String transactionResultCode;
    protected String rejectionReason;
    protected String merchantName;
    protected String merchantAddress1;
    protected String merchantAddress2;
    protected String merchantAddress3;
    protected String merchantAddress4;
    protected String merchantPhoneNumber;
    protected String merchantRegNumber;
    protected String merchantID;
    protected String currency;
    protected String transactionAmount;
    protected String totalAmount;
    protected String taxAmount;
    protected String cashbackAmount;
    protected String surchargeAmount;
    protected String tipsAmount;
    protected String cardBrand;
    protected String maskedPAN;
    protected String psn;
    protected String expiryDate;
    protected String paymentInstrument;
    protected String apmName;
    protected String cvmType;
    protected String encryptedPan;
    protected String encryptedKsn;
    protected String terminalID;
    protected String poiSerialNumber;
    protected String acquirerName;
    protected String acquirerID;
    protected String authCode;
    protected String authSource;
    protected String refNumber;
    protected String atc;
    protected String aed;
    protected String aid;
    protected String acType;
    protected String acValue;
    protected String arc;
    protected String tvr;
    protected String tsi;

    /**
     * Gets the value of the receiptCopy property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getReceiptCopy() {
        return receiptCopy;
    }

    /**
     * Sets the value of the receiptCopy property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setReceiptCopy(String value) {
        this.receiptCopy = value;
    }

    /**
     * Gets the value of the offlineDeletedReceipt property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getOfflineDeletedReceipt() {
        return offlineDeletedReceipt;
    }

    /**
     * Sets the value of the offlineDeletedReceipt property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setOfflineDeletedReceipt(String value) {
        this.offlineDeletedReceipt = value;
    }

    /**
     * Gets the value of the offlineDeletedTimeStamp property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getOfflineDeletedTimeStamp() {
        return offlineDeletedTimeStamp;
    }

    /**
     * Sets the value of the offlineDeletedTimeStamp property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setOfflineDeletedTimeStamp(String value) {
        this.offlineDeletedTimeStamp = value;
    }

    /**
     * Gets the value of the transactionType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTransactionType() {
        return transactionType;
    }

    /**
     * Sets the value of the transactionType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTransactionType(String value) {
        this.transactionType = value;
    }

    /**
     * Gets the value of the transactionTimeStamp property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTransactionTimeStamp() {
        return transactionTimeStamp;
    }

    /**
     * Sets the value of the transactionTimeStamp property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTransactionTimeStamp(String value) {
        this.transactionTimeStamp = value;
    }

    /**
     * Gets the value of the transactionResult property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTransactionResult() {
        return transactionResult;
    }

    /**
     * Sets the value of the transactionResult property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTransactionResult(String value) {
        this.transactionResult = value;
    }

    /**
     * Gets the value of the transactionResultCode property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTransactionResultCode() {
        return transactionResultCode;
    }

    /**
     * Sets the value of the transactionResultCode property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTransactionResultCode(String value) {
        this.transactionResultCode = value;
    }

    /**
     * Gets the value of the rejectionReason property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRejectionReason() {
        return rejectionReason;
    }

    /**
     * Sets the value of the rejectionReason property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRejectionReason(String value) {
        this.rejectionReason = value;
    }

    /**
     * Gets the value of the merchantName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMerchantName() {
        return merchantName;
    }

    /**
     * Sets the value of the merchantName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMerchantName(String value) {
        this.merchantName = value;
    }

    /**
     * Gets the value of the merchantAddress1 property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMerchantAddress1() {
        return merchantAddress1;
    }

    /**
     * Sets the value of the merchantAddress1 property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMerchantAddress1(String value) {
        this.merchantAddress1 = value;
    }

    /**
     * Gets the value of the merchantAddress2 property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMerchantAddress2() {
        return merchantAddress2;
    }

    /**
     * Sets the value of the merchantAddress2 property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMerchantAddress2(String value) {
        this.merchantAddress2 = value;
    }

    /**
     * Gets the value of the merchantAddress3 property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMerchantAddress3() {
        return merchantAddress3;
    }

    /**
     * Sets the value of the merchantAddress3 property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMerchantAddress3(String value) {
        this.merchantAddress3 = value;
    }

    /**
     * Gets the value of the merchantAddress4 property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMerchantAddress4() {
        return merchantAddress4;
    }

    /**
     * Sets the value of the merchantAddress4 property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMerchantAddress4(String value) {
        this.merchantAddress4 = value;
    }

    /**
     * Gets the value of the merchantPhoneNumber property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMerchantPhoneNumber() {
        return merchantPhoneNumber;
    }

    /**
     * Sets the value of the merchantPhoneNumber property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMerchantPhoneNumber(String value) {
        this.merchantPhoneNumber = value;
    }

    /**
     * Gets the value of the merchantRegNumber property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMerchantRegNumber() {
        return merchantRegNumber;
    }

    /**
     * Sets the value of the merchantRegNumber property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMerchantRegNumber(String value) {
        this.merchantRegNumber = value;
    }

    /**
     * Gets the value of the merchantID property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMerchantID() {
        return merchantID;
    }

    /**
     * Sets the value of the merchantID property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMerchantID(String value) {
        this.merchantID = value;
    }

    /**
     * Gets the value of the currency property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * Sets the value of the currency property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCurrency(String value) {
        this.currency = value;
    }

    /**
     * Gets the value of the transactionAmount property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTransactionAmount() {
        return transactionAmount;
    }

    /**
     * Sets the value of the transactionAmount property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTransactionAmount(String value) {
        this.transactionAmount = value;
    }

    /**
     * Gets the value of the totalAmount property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTotalAmount() {
        return totalAmount;
    }

    /**
     * Sets the value of the totalAmount property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTotalAmount(String value) {
        this.totalAmount = value;
    }

    /**
     * Gets the value of the taxAmount property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTaxAmount() {
        return taxAmount;
    }

    /**
     * Sets the value of the taxAmount property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTaxAmount(String value) {
        this.taxAmount = value;
    }

    /**
     * Gets the value of the cashbackAmount property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCashbackAmount() {
        return cashbackAmount;
    }

    /**
     * Sets the value of the cashbackAmount property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCashbackAmount(String value) {
        this.cashbackAmount = value;
    }

    /**
     * Gets the value of the surchargeAmount property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSurchargeAmount() {
        return surchargeAmount;
    }

    /**
     * Sets the value of the surchargeAmount property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSurchargeAmount(String value) {
        this.surchargeAmount = value;
    }

    /**
     * Gets the value of the tipsAmount property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTipsAmount() {
        return tipsAmount;
    }

    /**
     * Sets the value of the tipsAmount property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTipsAmount(String value) {
        this.tipsAmount = value;
    }

    /**
     * Gets the value of the cardBrand property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCardBrand() {
        return cardBrand;
    }

    /**
     * Sets the value of the cardBrand property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCardBrand(String value) {
        this.cardBrand = value;
    }

    /**
     * Gets the value of the maskedPAN property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMaskedPAN() {
        return maskedPAN;
    }

    /**
     * Sets the value of the maskedPAN property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMaskedPAN(String value) {
        this.maskedPAN = value;
    }

    /**
     * Gets the value of the psn property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPsn() {
        return psn;
    }

    /**
     * Sets the value of the psn property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPsn(String value) {
        this.psn = value;
    }

    /**
     * Gets the value of the expiryDate property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getExpiryDate() {
        return expiryDate;
    }

    /**
     * Sets the value of the expiryDate property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setExpiryDate(String value) {
        this.expiryDate = value;
    }

    /**
     * Gets the value of the paymentInstrument property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPaymentInstrument() {
        return paymentInstrument;
    }

    /**
     * Sets the value of the paymentInstrument property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPaymentInstrument(String value) {
        this.paymentInstrument = value;
    }

    /**
     * Gets the value of the apmName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getApmName() {
        return apmName;
    }

    /**
     * Sets the value of the apmName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setApmName(String value) {
        this.apmName = value;
    }

    /**
     * Gets the value of the cvmType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCvmType() {
        return cvmType;
    }

    /**
     * Sets the value of the cvmType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCvmType(String value) {
        this.cvmType = value;
    }

    /**
     * Gets the value of the encryptedPan property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getEncryptedPan() {
        return encryptedPan;
    }

    /**
     * Sets the value of the encryptedPan property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setEncryptedPan(String value) {
        this.encryptedPan = value;
    }

    /**
     * Gets the value of the encryptedKsn property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getEncryptedKsn() {
        return encryptedKsn;
    }

    /**
     * Sets the value of the encryptedKsn property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setEncryptedKsn(String value) {
        this.encryptedKsn = value;
    }

    /**
     * Gets the value of the terminalID property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTerminalID() {
        return terminalID;
    }

    /**
     * Sets the value of the terminalID property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTerminalID(String value) {
        this.terminalID = value;
    }

    /**
     * Gets the value of the poiSerialNumber property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPoiSerialNumber() {
        return poiSerialNumber;
    }

    /**
     * Sets the value of the poiSerialNumber property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPoiSerialNumber(String value) {
        this.poiSerialNumber = value;
    }

    /**
     * Gets the value of the acquirerName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAcquirerName() {
        return acquirerName;
    }

    /**
     * Sets the value of the acquirerName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAcquirerName(String value) {
        this.acquirerName = value;
    }

    /**
     * Gets the value of the acquirerID property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAcquirerID() {
        return acquirerID;
    }

    /**
     * Sets the value of the acquirerID property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAcquirerID(String value) {
        this.acquirerID = value;
    }

    /**
     * Gets the value of the authCode property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAuthCode() {
        return authCode;
    }

    /**
     * Sets the value of the authCode property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAuthCode(String value) {
        this.authCode = value;
    }

    /**
     * Gets the value of the authSource property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAuthSource() {
        return authSource;
    }

    /**
     * Sets the value of the authSource property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAuthSource(String value) {
        this.authSource = value;
    }

    /**
     * Gets the value of the refNumber property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRefNumber() {
        return refNumber;
    }

    /**
     * Sets the value of the refNumber property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRefNumber(String value) {
        this.refNumber = value;
    }

    /**
     * Gets the value of the atc property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAtc() {
        return atc;
    }

    /**
     * Sets the value of the atc property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAtc(String value) {
        this.atc = value;
    }

    /**
     * Gets the value of the aed property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAed() {
        return aed;
    }

    /**
     * Sets the value of the aed property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAed(String value) {
        this.aed = value;
    }

    /**
     * Gets the value of the aid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAid() {
        return aid;
    }

    /**
     * Sets the value of the aid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAid(String value) {
        this.aid = value;
    }

    /**
     * Gets the value of the acType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAcType() {
        return acType;
    }

    /**
     * Sets the value of the acType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAcType(String value) {
        this.acType = value;
    }

    /**
     * Gets the value of the acValue property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAcValue() {
        return acValue;
    }

    /**
     * Sets the value of the acValue property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAcValue(String value) {
        this.acValue = value;
    }

    /**
     * Gets the value of the arc property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getArc() {
        return arc;
    }

    /**
     * Sets the value of the arc property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setArc(String value) {
        this.arc = value;
    }

    /**
     * Gets the value of the tvr property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTvr() {
        return tvr;
    }

    /**
     * Sets the value of the tvr property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTvr(String value) {
        this.tvr = value;
    }

    /**
     * Gets the value of the tsi property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTsi() {
        return tsi;
    }

    /**
     * Sets the value of the tsi property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTsi(String value) {
        this.tsi = value;
    }

}
