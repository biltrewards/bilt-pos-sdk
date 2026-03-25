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
import jakarta.xml.bind.annotation.XmlAttribute;
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
 *       <all>
 *         <element name="htmlReceipt" type="{http://www.w3.org/2001/XMLSchema}base64Binary" minOccurs="0"/>
 *         <element name="plainTextReceipt" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="receiptData" type="{urn:bilt:receipt:v1}ReceiptDataType" minOccurs="0"/>
 *       </all>
 *       <attribute name="type" use="required" type="{urn:bilt:receipt:v1}ReceiptCopyEnum" />
 *       <attribute name="version" use="required" type="{http://www.w3.org/2001/XMLSchema}string" />
 *     </restriction>
 *   </complexContent>
 * </complexType>
 * }</pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ReceiptType", propOrder = {

})
public class ReceiptType {

    protected byte[] htmlReceipt;
    protected String plainTextReceipt;
    protected ReceiptDataType receiptData;
    @XmlAttribute(name = "type", required = true)
    protected ReceiptCopyEnum type;
    @XmlAttribute(name = "version", required = true)
    protected String version;

    /**
     * Gets the value of the htmlReceipt property.
     * 
     * @return
     *     possible object is
     *     byte[]
     */
    public byte[] getHtmlReceipt() {
        return htmlReceipt;
    }

    /**
     * Sets the value of the htmlReceipt property.
     * 
     * @param value
     *     allowed object is
     *     byte[]
     */
    public void setHtmlReceipt(byte[] value) {
        this.htmlReceipt = value;
    }

    /**
     * Gets the value of the plainTextReceipt property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPlainTextReceipt() {
        return plainTextReceipt;
    }

    /**
     * Sets the value of the plainTextReceipt property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPlainTextReceipt(String value) {
        this.plainTextReceipt = value;
    }

    /**
     * Gets the value of the receiptData property.
     * 
     * @return
     *     possible object is
     *     {@link ReceiptDataType }
     *     
     */
    public ReceiptDataType getReceiptData() {
        return receiptData;
    }

    /**
     * Sets the value of the receiptData property.
     * 
     * @param value
     *     allowed object is
     *     {@link ReceiptDataType }
     *     
     */
    public void setReceiptData(ReceiptDataType value) {
        this.receiptData = value;
    }

    /**
     * Gets the value of the type property.
     * 
     * @return
     *     possible object is
     *     {@link ReceiptCopyEnum }
     *     
     */
    public ReceiptCopyEnum getType() {
        return type;
    }

    /**
     * Sets the value of the type property.
     * 
     * @param value
     *     allowed object is
     *     {@link ReceiptCopyEnum }
     *     
     */
    public void setType(ReceiptCopyEnum value) {
        this.type = value;
    }

    /**
     * Gets the value of the version property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVersion() {
        return version;
    }

    /**
     * Sets the value of the version property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVersion(String value) {
        this.version = value;
    }

}
