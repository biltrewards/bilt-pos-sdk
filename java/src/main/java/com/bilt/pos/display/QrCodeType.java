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
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for QrCodeType complex type</p>.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.</p>
 * 
 * <pre>{@code
 * <complexType name="QrCodeType">
 *   <complexContent>
 *     <restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       <sequence>
 *         <element name="header" type="{urn:bilt:display:v1}HeaderFooterType" minOccurs="0"/>
 *         <element name="data" type="{http://www.w3.org/2001/XMLSchema}anyURI"/>
 *         <element name="callToAction" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="footer" type="{urn:bilt:display:v1}HeaderFooterType" minOccurs="0"/>
 *       </sequence>
 *       <attribute name="type" type="{http://www.w3.org/2001/XMLSchema}string" default="qr" />
 *     </restriction>
 *   </complexContent>
 * </complexType>
 * }</pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "QrCodeType", propOrder = {
    "header",
    "data",
    "callToAction",
    "footer"
})
public class QrCodeType {

    protected HeaderFooterType header;
    @XmlElement(required = true)
    @XmlSchemaType(name = "anyURI")
    protected String data;
    protected String callToAction;
    protected HeaderFooterType footer;
    @XmlAttribute(name = "type")
    protected String type;

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
     * Gets the value of the data property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getData() {
        return data;
    }

    /**
     * Sets the value of the data property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setData(String value) {
        this.data = value;
    }

    /**
     * Gets the value of the callToAction property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCallToAction() {
        return callToAction;
    }

    /**
     * Sets the value of the callToAction property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCallToAction(String value) {
        this.callToAction = value;
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

    /**
     * Gets the value of the type property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getType() {
        if (type == null) {
            return "qr";
        } else {
            return type;
        }
    }

    /**
     * Sets the value of the type property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setType(String value) {
        this.type = value;
    }

}
