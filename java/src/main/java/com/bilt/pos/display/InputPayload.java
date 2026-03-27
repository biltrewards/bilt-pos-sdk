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
 *         <element name="display" type="{urn:bilt:common:v1}DisplayType" minOccurs="0"/>
 *         <choice>
 *           <element name="signature" type="{urn:bilt:input:v1}SignatureType"/>
 *           <element name="confirmation" type="{urn:bilt:input:v1}ConfirmationType"/>
 *         </choice>
 *       </sequence>
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
    "display",
    "signature",
    "confirmation"
})
@XmlRootElement(name = "inputPayload", namespace = "urn:bilt:input:v1")
public class InputPayload {

    @XmlElement(namespace = "urn:bilt:input:v1")
    protected DisplayType display;
    @XmlElement(namespace = "urn:bilt:input:v1")
    protected SignatureType signature;
    @XmlElement(namespace = "urn:bilt:input:v1")
    protected ConfirmationType confirmation;
    @XmlAttribute(name = "version")
    protected String version;

    /**
     * Gets the value of the display property.
     * 
     * @return
     *     possible object is
     *     {@link DisplayType }
     *     
     */
    public DisplayType getDisplay() {
        return display;
    }

    /**
     * Sets the value of the display property.
     * 
     * @param value
     *     allowed object is
     *     {@link DisplayType }
     *     
     */
    public void setDisplay(DisplayType value) {
        this.display = value;
    }

    /**
     * Gets the value of the signature property.
     * 
     * @return
     *     possible object is
     *     {@link SignatureType }
     *     
     */
    public SignatureType getSignature() {
        return signature;
    }

    /**
     * Sets the value of the signature property.
     * 
     * @param value
     *     allowed object is
     *     {@link SignatureType }
     *     
     */
    public void setSignature(SignatureType value) {
        this.signature = value;
    }

    /**
     * Gets the value of the confirmation property.
     * 
     * @return
     *     possible object is
     *     {@link ConfirmationType }
     *     
     */
    public ConfirmationType getConfirmation() {
        return confirmation;
    }

    /**
     * Sets the value of the confirmation property.
     * 
     * @param value
     *     allowed object is
     *     {@link ConfirmationType }
     *     
     */
    public void setConfirmation(ConfirmationType value) {
        this.confirmation = value;
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
