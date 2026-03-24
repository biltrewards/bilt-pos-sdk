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
import jakarta.xml.bind.annotation.XmlSeeAlso;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ButtonsType complex type</p>.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.</p>
 * 
 * <pre>{@code
 * <complexType name="ButtonsType">
 *   <complexContent>
 *     <restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       <sequence>
 *         <element name="confirmButton" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         <element name="cancelButton" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *       </sequence>
 *     </restriction>
 *   </complexContent>
 * </complexType>
 * }</pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ButtonsType", namespace = "urn:bilt:input:v1", propOrder = {
    "confirmButton",
    "cancelButton"
})
@XmlSeeAlso({
    SignatureType.class,
    ConfirmationType.class
})
public class ButtonsType {

    protected String confirmButton;
    protected String cancelButton;

    /**
     * Gets the value of the confirmButton property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getConfirmButton() {
        return confirmButton;
    }

    /**
     * Sets the value of the confirmButton property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setConfirmButton(String value) {
        this.confirmButton = value;
    }

    /**
     * Gets the value of the cancelButton property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCancelButton() {
        return cancelButton;
    }

    /**
     * Sets the value of the cancelButton property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCancelButton(String value) {
        this.cancelButton = value;
    }

}
