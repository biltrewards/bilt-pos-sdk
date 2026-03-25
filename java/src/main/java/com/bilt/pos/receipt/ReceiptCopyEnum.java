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

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;


/**
 * 
 * 
 * <p>Java class for ReceiptCopyEnum</p>.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.</p>
 * <pre>{@code
 * <simpleType name="ReceiptCopyEnum">
 *   <restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     <enumeration value="CUSTOMER"/>
 *     <enumeration value="MERCHANT"/>
 *   </restriction>
 * </simpleType>
 * }</pre>
 * 
 */
@XmlType(name = "ReceiptCopyEnum")
@XmlEnum
public enum ReceiptCopyEnum {

    CUSTOMER,
    MERCHANT;

    public String value() {
        return name();
    }

    public static ReceiptCopyEnum fromValue(String v) {
        return valueOf(v);
    }

}
