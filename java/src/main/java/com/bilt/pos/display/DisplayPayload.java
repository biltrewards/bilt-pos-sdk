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
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type</p>.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.</p>
 * 
 * <pre>{@code
 * <complexType>
 *   <complexContent>
 *     <restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       <sequence>
 *         <choice>
 *           <element name="receipt" type="{urn:bilt:display:v1}ReceiptType"/>
 *           <element name="qrCode" type="{urn:bilt:display:v1}QrCodeType"/>
 *           <element name="image" type="{urn:bilt:display:v1}ImageType"/>
 *           <element name="standby" type="{urn:bilt:display:v1}StandbyType"/>
 *           <element name="waiting" type="{urn:bilt:display:v1}WaitingType"/>
 *         </choice>
 *       </sequence>
 *       <attribute name="layout" use="required" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       <attribute name="version" type="{http://www.w3.org/2001/XMLSchema}string" default="1.0" />
 *     </restriction>
 *   </complexContent>
 * </complexType>
 * }</pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "receipt",
    "qrCode",
    "image",
    "standby",
    "waiting"
})
@XmlRootElement(name = "displayPayload")
public class DisplayPayload {

    protected ReceiptType receipt;
    protected QrCodeType qrCode;
    protected ImageType image;
    protected StandbyType standby;
    protected WaitingType waiting;
    @XmlAttribute(name = "layout", required = true)
    protected String layout;
    @XmlAttribute(name = "version")
    protected String version;

    /**
     * Gets the value of the receipt property.
     * 
     * @return
     *     possible object is
     *     {@link ReceiptType }
     *     
     */
    public ReceiptType getReceipt() {
        return receipt;
    }

    /**
     * Sets the value of the receipt property.
     * 
     * @param value
     *     allowed object is
     *     {@link ReceiptType }
     *     
     */
    public void setReceipt(ReceiptType value) {
        this.receipt = value;
    }

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
     * Gets the value of the image property.
     * 
     * @return
     *     possible object is
     *     {@link ImageType }
     *     
     */
    public ImageType getImage() {
        return image;
    }

    /**
     * Sets the value of the image property.
     * 
     * @param value
     *     allowed object is
     *     {@link ImageType }
     *     
     */
    public void setImage(ImageType value) {
        this.image = value;
    }

    /**
     * Gets the value of the standby property.
     * 
     * @return
     *     possible object is
     *     {@link StandbyType }
     *     
     */
    public StandbyType getStandby() {
        return standby;
    }

    /**
     * Sets the value of the standby property.
     * 
     * @param value
     *     allowed object is
     *     {@link StandbyType }
     *     
     */
    public void setStandby(StandbyType value) {
        this.standby = value;
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
     * Gets the value of the layout property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getLayout() {
        return layout;
    }

    /**
     * Sets the value of the layout property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setLayout(String value) {
        this.layout = value;
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
        if (version == null) {
            return "1.0";
        } else {
            return version;
        }
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
