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

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlEnumValue;
import jakarta.xml.bind.annotation.XmlType;


/**
 * 
 * 
 * <p>Java class for LineItemKindType</p>.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.</p>
 * <pre>{@code
 * <simpleType name="LineItemKindType">
 *   <restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     <enumeration value="item"/>
 *     <enumeration value="heading"/>
 *     <enumeration value="discount"/>
 *     <enumeration value="return"/>
 *     <enumeration value="void"/>
 *     <enumeration value="separator"/>
 *     <enumeration value="spacer"/>
 *   </restriction>
 * </simpleType>
 * }</pre>
 * 
 */
@XmlType(name = "LineItemKindType")
@XmlEnum
public enum LineItemKindType {

    @XmlEnumValue("item")
    ITEM("item"),
    @XmlEnumValue("heading")
    HEADING("heading"),
    @XmlEnumValue("discount")
    DISCOUNT("discount"),
    @XmlEnumValue("return")
    RETURN("return"),
    @XmlEnumValue("void")
    VOID("void"),
    @XmlEnumValue("separator")
    SEPARATOR("separator"),
    @XmlEnumValue("spacer")
    SPACER("spacer");
    private final String value;

    LineItemKindType(String v) {
        value = v;
    }

    /**
     * Gets the value associated to the enum constant.
     * 
     * @return
     *     The value linked to the enum.
     */
    public String value() {
        return value;
    }

    /**
     * Gets the enum associated to the value passed as parameter.
     * 
     * @param v
     *     The value to get the enum from.
     * @return
     *     The enum which corresponds to the value, if it exists.
     * @throws IllegalArgumentException
     *     If no value matches in the enum declaration.
     */
    public static LineItemKindType fromValue(String v) {
        for (LineItemKindType c: LineItemKindType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
