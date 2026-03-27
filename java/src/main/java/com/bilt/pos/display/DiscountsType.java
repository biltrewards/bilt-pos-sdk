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

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DiscountsType complex type</p>.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.</p>
 * 
 * <pre>{@code
 * <complexType name="DiscountsType">
 *   <complexContent>
 *     <restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       <sequence>
 *         <element name="discountItem" type="{urn:bilt:display:v1}LabeledAmountType" maxOccurs="unbounded" minOccurs="0"/>
 *         <element name="totalDiscount" type="{urn:bilt:display:v1}LabeledAmountType" minOccurs="0"/>
 *       </sequence>
 *     </restriction>
 *   </complexContent>
 * </complexType>
 * }</pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DiscountsType", propOrder = {
    "discountItem",
    "totalDiscount"
})
public class DiscountsType {

    protected List<LabeledAmountType> discountItem;
    protected LabeledAmountType totalDiscount;

    /**
     * Gets the value of the discountItem property.
     * 
     * <p>This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the discountItem property.</p>
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * </p>
     * <pre>
     * getDiscountItem().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link LabeledAmountType }
     * </p>
     * 
     * 
     * @return
     *     The value of the discountItem property.
     */
    public List<LabeledAmountType> getDiscountItem() {
        if (discountItem == null) {
            discountItem = new ArrayList<>();
        }
        return this.discountItem;
    }

    /**
     * Gets the value of the totalDiscount property.
     * 
     * @return
     *     possible object is
     *     {@link LabeledAmountType }
     *     
     */
    public LabeledAmountType getTotalDiscount() {
        return totalDiscount;
    }

    /**
     * Sets the value of the totalDiscount property.
     * 
     * @param value
     *     allowed object is
     *     {@link LabeledAmountType }
     *     
     */
    public void setTotalDiscount(LabeledAmountType value) {
        this.totalDiscount = value;
    }

}
