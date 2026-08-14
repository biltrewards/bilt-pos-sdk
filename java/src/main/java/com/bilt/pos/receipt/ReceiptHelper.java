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

import com.bilt.pos.internal.XmlSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import jakarta.xml.bind.JAXBException;

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

    private static final XmlMapper MAPPER = XmlSupport.newMapper();

    /** Root element name, matching {@link ObjectFactory#createReceipt}'s QName. */
    private static final String ROOT_ELEMENT = "receipt";

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
        try {
            String xml = MAPPER.writer().withRootName(ROOT_ELEMENT).writeValueAsString(receipt);
            return XmlSupport.withDefaultNamespace(xml, ROOT_ELEMENT, "urn:bilt:receipt:v1");
        } catch (JsonProcessingException e) {
            throw new JAXBException("Failed to serialize ReceiptType", e);
        }
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
        try {
            return MAPPER.readValue(xml, ReceiptType.class);
        } catch (JsonProcessingException e) {
            throw new JAXBException("Failed to deserialize ReceiptType", e);
        }
    }

    /**
     * Deserializes a {@link ReceiptType} from a Base64-encoded XML string.
     *
     * <p>Some terminals put the receipt XML into {@code OutputXHTML} raw,
     * skipping the Base64 encoding the schema calls for. A payload opening
     * with {@code '<'} cannot be Base64 ({@code '<'} is outside the
     * alphabet), so it is unambiguously the raw form and parsed as XML
     * directly.
     *
     * @param base64 the Base64-encoded (or raw) XML string, as found in
     *        {@code OutputXHTML}
     * @return the deserialized receipt
     * @throws JAXBException if deserialization fails
     */
    public static ReceiptType fromBase64(String base64) throws JAXBException {
        String payload = base64.trim();
        if (payload.startsWith("<")) {
            return fromXml(payload);
        }
        String xml = new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
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
