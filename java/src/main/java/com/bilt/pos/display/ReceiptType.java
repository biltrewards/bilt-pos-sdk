/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   This file is auto-generated from the display.xsd and input.xsd schemas.
 *   Do not modify manually — re-run code generation instead.
 */

package com.bilt.pos.display;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ReceiptType complex type</p>.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.</p>
 * 
 * <pre>{@code
 * <complexType name="ReceiptType">
 *   <complexContent>
 *     <restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       <sequence>
 *         <element name="qrCode" type="{urn:bilt:display:v1}QrCodeType" minOccurs="0"/>
 *         <element name="waiting" type="{urn:bilt:display:v1}WaitingType" minOccurs="0"/>
 *         <element name="header" type="{urn:bilt:display:v1}HeaderFooterType" minOccurs="0"/>
 *         <element name="lineItems" type="{urn:bilt:display:v1}LineItemsType" minOccurs="0"/>
 *         <element name="subtotal" type="{urn:bilt:display:v1}LabeledAmountType" minOccurs="0"/>
 *         <element name="adjustments" type="{urn:bilt:display:v1}AdjustmentsType" minOccurs="0"/>
 *         <element name="tax" type="{urn:bilt:display:v1}TaxType" minOccurs="0"/>
 *         <element name="total" type="{urn:bilt:display:v1}LabeledAmountType" minOccurs="0"/>
 *         <element name="footer" type="{urn:bilt:display:v1}HeaderFooterType" minOccurs="0"/>
 *       </sequence>
 *     </restriction>
 *   </complexContent>
 * </complexType>
 * }</pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ReceiptType", propOrder = {
    "qrCode",
    "waiting",
    "header",
    "lineItems",
    "subtotal",
    "adjustments",
    "tax",
    "total",
    "footer"
})
public class ReceiptType {

    protected QrCodeType qrCode;
    protected WaitingType waiting;
    protected HeaderFooterType header;
    protected LineItemsType lineItems;
    protected LabeledAmountType subtotal;
    protected AdjustmentsType adjustments;
    protected TaxType tax;
    protected LabeledAmountType total;
    protected HeaderFooterType footer;

    /**
     * Gets the value of the qrCode property.
     * 
     * @return
     *     possible object is
     *     {@link QrCodeType }
     *     
     */
    public QrCodeType getQrCode() {
        return qrCode;
    }

    /**
     * Sets the value of the qrCode property.
     * 
     * @param value
     *     allowed object is
     *     {@link QrCodeType }
     *     
     */
    public void setQrCode(QrCodeType value) {
        this.qrCode = value;
    }

    /**
     * Gets the value of the waiting property.
     * 
     * @return
     *     possible object is
     *     {@link WaitingType }
     *     
     */
    public WaitingType getWaiting() {
        return waiting;
    }

    /**
     * Sets the value of the waiting property.
     * 
     * @param value
     *     allowed object is
     *     {@link WaitingType }
     *     
     */
    public void setWaiting(WaitingType value) {
        this.waiting = value;
    }

    /**
     * Gets the value of the header property.
     * 
     * @return
     *     possible object is
     *     {@link HeaderFooterType }
     *     
     */
    public HeaderFooterType getHeader() {
        return header;
    }

    /**
     * Sets the value of the header property.
     * 
     * @param value
     *     allowed object is
     *     {@link HeaderFooterType }
     *     
     */
    public void setHeader(HeaderFooterType value) {
        this.header = value;
    }

    /**
     * Gets the value of the lineItems property.
     * 
     * @return
     *     possible object is
     *     {@link LineItemsType }
     *     
     */
    public LineItemsType getLineItems() {
        return lineItems;
    }

    /**
     * Sets the value of the lineItems property.
     * 
     * @param value
     *     allowed object is
     *     {@link LineItemsType }
     *     
     */
    public void setLineItems(LineItemsType value) {
        this.lineItems = value;
    }

    /**
     * Gets the value of the subtotal property.
     * 
     * @return
     *     possible object is
     *     {@link LabeledAmountType }
     *     
     */
    public LabeledAmountType getSubtotal() {
        return subtotal;
    }

    /**
     * Sets the value of the subtotal property.
     * 
     * @param value
     *     allowed object is
     *     {@link LabeledAmountType }
     *     
     */
    public void setSubtotal(LabeledAmountType value) {
        this.subtotal = value;
    }

    /**
     * Gets the value of the adjustments property.
     *
     * @return
     *     possible object is
     *     {@link AdjustmentsType }
     *
     */
    public AdjustmentsType getAdjustments() {
        return adjustments;
    }

    /**
     * Sets the value of the adjustments property.
     *
     * @param value
     *     allowed object is
     *     {@link AdjustmentsType }
     *
     */
    public void setAdjustments(AdjustmentsType value) {
        this.adjustments = value;
    }

    /**
     * Gets the value of the tax property.
     * 
     * @return
     *     possible object is
     *     {@link TaxType }
     *     
     */
    public TaxType getTax() {
        return tax;
    }

    /**
     * Sets the value of the tax property.
     * 
     * @param value
     *     allowed object is
     *     {@link TaxType }
     *     
     */
    public void setTax(TaxType value) {
        this.tax = value;
    }

    /**
     * Gets the value of the total property.
     * 
     * @return
     *     possible object is
     *     {@link LabeledAmountType }
     *     
     */
    public LabeledAmountType getTotal() {
        return total;
    }

    /**
     * Sets the value of the total property.
     * 
     * @param value
     *     allowed object is
     *     {@link LabeledAmountType }
     *     
     */
    public void setTotal(LabeledAmountType value) {
        this.total = value;
    }

    /**
     * Gets the value of the footer property.
     * 
     * @return
     *     possible object is
     *     {@link HeaderFooterType }
     *     
     */
    public HeaderFooterType getFooter() {
        return footer;
    }

    /**
     * Sets the value of the footer property.
     * 
     * @param value
     *     allowed object is
     *     {@link HeaderFooterType }
     *     
     */
    public void setFooter(HeaderFooterType value) {
        this.footer = value;
    }

}
