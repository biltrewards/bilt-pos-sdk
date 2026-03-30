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
 * <p>Java class for TaxType complex type</p>.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.</p>
 * 
 * <pre>{@code
 * <complexType name="TaxType">
 *   <complexContent>
 *     <restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       <sequence>
 *         <element name="taxItem" type="{urn:bilt:display:v1}LabeledAmountType" maxOccurs="unbounded" minOccurs="0"/>
 *         <element name="totalDiscount" type="{urn:bilt:display:v1}LabeledAmountType" minOccurs="0"/>
 *         <element name="taxTotal" type="{urn:bilt:display:v1}LabeledAmountType" minOccurs="0"/>
 *       </sequence>
 *     </restriction>
 *   </complexContent>
 * </complexType>
 * }</pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "TaxType", propOrder = {
    "taxItem",
    "totalDiscount",
    "taxTotal"
})
public class TaxType {

    protected List<LabeledAmountType> taxItem;
    /**
     * @deprecated Use {@code <adjustments><adjustmentItem>} instead for order-level discounts.
     *             Retained for backwards compatibility only.
     */
    @Deprecated
    protected LabeledAmountType totalDiscount;
    protected LabeledAmountType taxTotal;

    /**
     * Gets the value of the taxItem property.
     * 
     * <p>This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the taxItem property.</p>
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * </p>
     * <pre>
     * getTaxItem().add(newItem);
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
     *     The value of the taxItem property.
     */
    public List<LabeledAmountType> getTaxItem() {
        if (taxItem == null) {
            taxItem = new ArrayList<>();
        }
        return this.taxItem;
    }

    /**
     * Gets the value of the totalDiscount property.
     *
     * @return
     *     possible object is
     *     {@link LabeledAmountType }
     *
     * @deprecated Use {@code <adjustments><adjustmentItem>} instead for order-level discounts.
     *             Retained for backwards compatibility only.
     */
    @Deprecated
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
     * @deprecated Use {@code <adjustments><adjustmentItem>} instead for order-level discounts.
     *             Retained for backwards compatibility only.
     */
    @Deprecated
    public void setTotalDiscount(LabeledAmountType value) {
        this.totalDiscount = value;
    }

    /**
     * Gets the value of the taxTotal property.
     *
     * @return
     *     possible object is
     *     {@link LabeledAmountType }
     *
     */
    public LabeledAmountType getTaxTotal() {
        return taxTotal;
    }

    /**
     * Sets the value of the taxTotal property.
     * 
     * @param value
     *     allowed object is
     *     {@link LabeledAmountType }
     *     
     */
    public void setTaxTotal(LabeledAmountType value) {
        this.taxTotal = value;
    }

}
