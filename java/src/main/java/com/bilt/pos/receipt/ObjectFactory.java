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

import javax.xml.namespace.QName;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlElementDecl;
import jakarta.xml.bind.annotation.XmlRegistry;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.bilt.pos.receipt package. 
 * <p>An ObjectFactory allows you to programmatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {

    private static final QName _Receipt_QNAME = new QName("urn:bilt:receipt:v1", "receipt");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.bilt.pos.receipt
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link ReceiptType }
     * 
     * @return
     *     the new instance of {@link ReceiptType }
     */
    public ReceiptType createReceiptType() {
        return new ReceiptType();
    }

    /**
     * Create an instance of {@link ReceiptDataType }
     * 
     * @return
     *     the new instance of {@link ReceiptDataType }
     */
    public ReceiptDataType createReceiptDataType() {
        return new ReceiptDataType();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ReceiptType }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link ReceiptType }{@code >}
     */
    @XmlElementDecl(namespace = "urn:bilt:receipt:v1", name = "receipt")
    public JAXBElement<ReceiptType> createReceipt(ReceiptType value) {
        return new JAXBElement<>(_Receipt_QNAME, ReceiptType.class, null, value);
    }

}
