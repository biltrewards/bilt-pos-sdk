/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   Helper utilities for serializing display and input payloads to Base64-encoded XML.
 */
package com.bilt.pos.display;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility class for serializing {@link DisplayPayload} and {@link InputPayload} objects
 * to Base64-encoded XML strings suitable for use in {@code OutputXHTML} fields.
 *
 * <p>Example usage:
 * <pre>{@code
 * DisplayPayload payload = DisplayPayloadHelper.standby("standby.xslt");
 * String base64 = DisplayPayloadHelper.toBase64(payload);
 * // Use base64 in OutputContent.outputXHTML
 * }</pre>
 */
public final class DisplayPayloadHelper {

    private static final JAXBContext DISPLAY_CONTEXT;
    private static final JAXBContext INPUT_CONTEXT;

    static {
        try {
            DISPLAY_CONTEXT = JAXBContext.newInstance(DisplayPayload.class);
            INPUT_CONTEXT = JAXBContext.newInstance(InputPayload.class);
        } catch (JAXBException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private DisplayPayloadHelper() {
        // Utility class
    }

    // ═══════════════════════════════════════════════════════════════════
    // Serialization
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Serializes a {@link DisplayPayload} to an XML string.
     *
     * @param payload the display payload to serialize
     * @return the XML string representation
     * @throws JAXBException if serialization fails
     */
    public static String toXml(DisplayPayload payload) throws JAXBException {
        Marshaller marshaller = DISPLAY_CONTEXT.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.FALSE);
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.FALSE);
        StringWriter writer = new StringWriter();
        marshaller.marshal(payload, writer);
        return writer.toString();
    }

    /**
     * Serializes a {@link DisplayPayload} to a Base64-encoded XML string.
     *
     * @param payload the display payload to serialize
     * @return the Base64-encoded XML string, ready for use in {@code OutputXHTML}
     * @throws JAXBException if serialization fails
     */
    public static String toBase64(DisplayPayload payload) throws JAXBException {
        String xml = toXml(payload);
        return Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Deserializes a {@link DisplayPayload} from an XML string.
     *
     * @param xml the XML string to deserialize
     * @return the deserialized display payload
     * @throws JAXBException if deserialization fails
     */
    public static DisplayPayload fromXml(String xml) throws JAXBException {
        Unmarshaller unmarshaller = DISPLAY_CONTEXT.createUnmarshaller();
        return (DisplayPayload) unmarshaller.unmarshal(new StringReader(xml));
    }

    /**
     * Deserializes a {@link DisplayPayload} from a Base64-encoded XML string.
     *
     * @param base64 the Base64-encoded XML string
     * @return the deserialized display payload
     * @throws JAXBException if deserialization fails
     */
    public static DisplayPayload fromBase64(String base64) throws JAXBException {
        String xml = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        return fromXml(xml);
    }

    /**
     * Serializes an {@link InputPayload} to an XML string.
     *
     * @param payload the input payload to serialize
     * @return the XML string representation
     * @throws JAXBException if serialization fails
     */
    public static String toXml(InputPayload payload) throws JAXBException {
        Marshaller marshaller = INPUT_CONTEXT.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.FALSE);
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.FALSE);
        StringWriter writer = new StringWriter();
        marshaller.marshal(payload, writer);
        return writer.toString();
    }

    /**
     * Serializes an {@link InputPayload} to a Base64-encoded XML string.
     *
     * @param payload the input payload to serialize
     * @return the Base64-encoded XML string, ready for use in {@code OutputXHTML}
     * @throws JAXBException if serialization fails
     */
    public static String toBase64(InputPayload payload) throws JAXBException {
        String xml = toXml(payload);
        return Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Deserializes an {@link InputPayload} from an XML string.
     *
     * @param xml the XML string to deserialize
     * @return the deserialized input payload
     * @throws JAXBException if deserialization fails
     */
    public static InputPayload inputFromXml(String xml) throws JAXBException {
        Unmarshaller unmarshaller = INPUT_CONTEXT.createUnmarshaller();
        return (InputPayload) unmarshaller.unmarshal(new StringReader(xml));
    }

    /**
     * Deserializes an {@link InputPayload} from a Base64-encoded XML string.
     *
     * @param base64 the Base64-encoded XML string
     * @return the deserialized input payload
     * @throws JAXBException if deserialization fails
     */
    public static InputPayload inputFromBase64(String base64) throws JAXBException {
        String xml = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        return inputFromXml(xml);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Factory methods for common payloads
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a standby display payload.
     *
     * @param layout the layout to use (e.g., "standby.xslt")
     * @return a new display payload configured for standby
     */
    public static DisplayPayload standby(String layout) {
        DisplayPayload payload = new DisplayPayload();
        payload.setLayout(layout);
        payload.setStandby(new StandbyType());
        return payload;
    }

    /**
     * Creates a standby display payload with a theme.
     *
     * @param layout the layout to use (e.g., "standby.xslt")
     * @param theme  the theme to apply (e.g., "christmas", "halloween")
     * @return a new display payload configured for themed standby
     */
    public static DisplayPayload standby(String layout, String theme) {
        DisplayPayload payload = new DisplayPayload();
        payload.setLayout(layout);
        StandbyType standby = new StandbyType();
        standby.setTheme(theme);
        payload.setStandby(standby);
        return payload;
    }

    /**
     * Creates a QR code display payload.
     *
     * @param layout the layout to use (e.g., "qr.xslt")
     * @param data   the data to encode in the QR code
     * @return a new display payload configured for QR code
     */
    public static DisplayPayload qrCode(String layout, String data) {
        DisplayPayload payload = new DisplayPayload();
        payload.setLayout(layout);
        QrCodeType qrCode = new QrCodeType();
        qrCode.setData(data);
        payload.setQrCode(qrCode);
        return payload;
    }

    /**
     * Creates a QR code display payload with header text and call to action.
     *
     * @param layout       the layout to use (e.g., "qr.xslt")
     * @param data         the data to encode in the QR code
     * @param headerText   optional header text (may be null)
     * @param callToAction optional call to action text (may be null)
     * @return a new display payload configured for QR code
     */
    public static DisplayPayload qrCode(String layout, String data, String headerText, String callToAction) {
        DisplayPayload payload = new DisplayPayload();
        payload.setLayout(layout);
        QrCodeType qrCode = new QrCodeType();
        qrCode.setData(data);
        if (headerText != null) {
            HeaderFooterType header = new HeaderFooterType();
            header.setText(headerText);
            qrCode.setHeader(header);
        }
        if (callToAction != null) {
            qrCode.setCallToAction(callToAction);
        }
        payload.setQrCode(qrCode);
        return payload;
    }

    /**
     * Creates a confirmation input payload.
     *
     * @param title the title/prompt to display
     * @return a new input payload configured for confirmation
     */
    public static InputPayload confirmation(String title) {
        InputPayload payload = new InputPayload();
        DisplayType display = new DisplayType();
        display.setTitle(title);
        payload.setDisplay(display);
        payload.setConfirmation(new ConfirmationType());
        return payload;
    }

    /**
     * Creates a signature input payload.
     *
     * @param title the title/prompt to display
     * @return a new input payload configured for signature capture
     */
    public static InputPayload signature(String title) {
        InputPayload payload = new InputPayload();
        DisplayType display = new DisplayType();
        display.setTitle(title);
        payload.setDisplay(display);
        payload.setSignature(new SignatureType());
        return payload;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Builder helpers for common types
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a money amount.
     *
     * @param currency the currency symbol or code (e.g., "$" or "USD")
     * @param value    the monetary value
     * @return a new MoneyType instance
     */
    public static MoneyType money(String currency, BigDecimal value) {
        MoneyType money = new MoneyType();
        money.setCurrency(currency);
        money.setValue(value);
        return money;
    }

    /**
     * Creates a money amount from a double value.
     *
     * @param currency the currency symbol or code (e.g., "$" or "USD")
     * @param value    the monetary value
     * @return a new MoneyType instance
     */
    public static MoneyType money(String currency, double value) {
        return money(currency, BigDecimal.valueOf(value));
    }

    /**
     * Creates a labeled amount (used for subtotal, total, tax items).
     *
     * @param description the label/description
     * @param currency    the currency symbol or code
     * @param value       the monetary value
     * @return a new LabeledAmountType instance
     */
    public static LabeledAmountType labeledAmount(String description, String currency, BigDecimal value) {
        LabeledAmountType amount = new LabeledAmountType();
        amount.setDescription(description);
        amount.setAmount(money(currency, value));
        return amount;
    }

    /**
     * Creates a labeled amount from a double value.
     *
     * @param description the label/description
     * @param currency    the currency symbol or code
     * @param value       the monetary value
     * @return a new LabeledAmountType instance
     */
    public static LabeledAmountType labeledAmount(String description, String currency, double value) {
        return labeledAmount(description, currency, BigDecimal.valueOf(value));
    }

    /**
     * Creates a line item for a receipt.
     *
     * @param kind        the line item kind
     * @param description the item description
     * @return a new LineItemType instance
     */
    public static LineItemType lineItem(LineItemKindType kind, String description) {
        LineItemType item = new LineItemType();
        item.setKind(kind);
        item.setDescription(description);
        return item;
    }

    /**
     * Creates a product line item for a receipt.
     *
     * @param description the product description
     * @param quantity    the quantity
     * @param currency    the currency symbol or code
     * @param unitPrice   the unit price
     * @param amount      the total amount
     * @return a new LineItemType instance
     */
    public static LineItemType productItem(String description, BigDecimal quantity,
                                           String currency, BigDecimal unitPrice, BigDecimal amount) {
        LineItemType item = new LineItemType();
        item.setKind(LineItemKindType.ITEM);
        item.setDescription(description);
        item.setQuantity(quantity);
        item.setUnitPrice(money(currency, unitPrice));
        item.setAmount(money(currency, amount));
        return item;
    }

    /**
     * Creates a header/footer with text.
     *
     * @param text the text content
     * @return a new HeaderFooterType instance
     */
    public static HeaderFooterType headerFooter(String text) {
        HeaderFooterType hf = new HeaderFooterType();
        hf.setText(text);
        return hf;
    }
}
