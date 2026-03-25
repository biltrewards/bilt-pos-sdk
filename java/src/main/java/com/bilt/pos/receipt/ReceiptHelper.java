/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   Helper utilities for serializing and deserializing receipt XML payloads.
 */
package com.bilt.pos.receipt;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility class for serializing and deserializing {@link ReceiptType} objects
 * to/from XML and Base64-encoded XML strings.
 *
 * <p>Receipt XML payloads conform to the {@code urn:bilt:receipt:v1} schema
 * and are carried inside {@code OutputContent.OutputXHTML} fields of Nexo
 * {@code PaymentReceipt} objects.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Parse a receipt from Base64 (as received in OutputXHTML)
 * ReceiptType receipt = ReceiptHelper.fromBase64(outputXhtml);
 * String plainText = receipt.getPlainTextReceipt();
 * String html = ReceiptHelper.htmlAsString(receipt);
 *
 * // Serialize a receipt to XML
 * String xml = ReceiptHelper.toXml(receipt);
 * }</pre>
 */
public final class ReceiptHelper {

    private static final JAXBContext CONTEXT;
    private static final ObjectFactory FACTORY = new ObjectFactory();

    static {
        try {
            CONTEXT = JAXBContext.newInstance(ObjectFactory.class);
        } catch (JAXBException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private ReceiptHelper() {
    }

    /**
     * Serializes a {@link ReceiptType} to an XML string.
     *
     * @param receipt the receipt to serialize
     * @return the XML string representation
     * @throws JAXBException if serialization fails
     */
    public static String toXml(ReceiptType receipt) throws JAXBException {
        Marshaller marshaller = CONTEXT.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.FALSE);
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.FALSE);
        StringWriter writer = new StringWriter();
        marshaller.marshal(FACTORY.createReceipt(receipt), writer);
        return writer.toString().replace(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        );
    }

    /**
     * Serializes a {@link ReceiptType} to a Base64-encoded XML string.
     *
     * @param receipt the receipt to serialize
     * @return the Base64-encoded XML string
     * @throws JAXBException if serialization fails
     */
    public static String toBase64(ReceiptType receipt) throws JAXBException {
        String xml = toXml(receipt);
        return Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Deserializes a {@link ReceiptType} from an XML string.
     *
     * @param xml the XML string to deserialize
     * @return the deserialized receipt
     * @throws JAXBException if deserialization fails
     */
    public static ReceiptType fromXml(String xml) throws JAXBException {
        Unmarshaller unmarshaller = CONTEXT.createUnmarshaller();
        JAXBElement<ReceiptType> element = unmarshaller.unmarshal(
                new StreamSource(new StringReader(xml)), ReceiptType.class
        );
        return element.getValue();
    }

    /**
     * Deserializes a {@link ReceiptType} from a Base64-encoded XML string.
     *
     * @param base64 the Base64-encoded XML string (as found in {@code OutputXHTML})
     * @return the deserialized receipt
     * @throws JAXBException if deserialization fails
     */
    public static ReceiptType fromBase64(String base64) throws JAXBException {
        String xml = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        return fromXml(xml);
    }

    /**
     * Returns the HTML receipt content as a String, or {@code null} if not present.
     *
     * <p>The schema stores HTML as {@code xs:base64Binary}, so JAXB represents it
     * as {@code byte[]}. This convenience method decodes it to a UTF-8 string.
     *
     * @param receipt the receipt
     * @return the HTML receipt as a string, or null
     */
    public static String htmlAsString(ReceiptType receipt) {
        byte[] html = receipt.getHtmlReceipt();
        if (html == null) {
            return null;
        }
        return new String(html, StandardCharsets.UTF_8);
    }
}
