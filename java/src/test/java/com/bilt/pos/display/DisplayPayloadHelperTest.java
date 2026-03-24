package com.bilt.pos.display;

import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class DisplayPayloadHelperTest {

    // ═══════════════════════════════════════════════════════════════════
    // Encoding / Decoding
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void toBase64ShouldProduceValidBase64String() throws JAXBException {
        DisplayPayload payload = createSampleReceipt();

        String base64 = DisplayPayloadHelper.toBase64(payload);

        // Base64 strings don't contain XML characters
        assertFalse(base64.contains("<"));
        assertFalse(base64.contains(">"));
        // Should be decodable
        assertDoesNotThrow(() -> Base64.getDecoder().decode(base64));
    }

    @Test
    void toXmlShouldProduceValidXmlWithNamespace() throws JAXBException {
        DisplayPayload payload = createSampleReceipt();

        String xml = DisplayPayloadHelper.toXml(payload);

        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\""));
        assertTrue(xml.contains("displayPayload"));
        assertTrue(xml.contains("receipt.xslt"));
        assertTrue(xml.contains("urn:bilt:display:v1"));
    }

    @Test
    void toXmlShouldIncludeVersionAttribute() throws JAXBException {
        DisplayPayload payload = DisplayPayloadHelper.standby("standby.xslt");

        String xml = DisplayPayloadHelper.toXml(payload);

        assertTrue(xml.contains("version=\"1.0\""), "XML should contain version attribute");
    }

    @Test
    void inputPayloadToXmlShouldIncludeVersionAttribute() throws JAXBException {
        InputPayload payload = DisplayPayloadHelper.confirmation("Test");

        String xml = DisplayPayloadHelper.toXml(payload);

        assertTrue(xml.contains("version=\"1.0\""), "XML should contain version attribute");
    }

    @Test
    void encodeAndDecodeShouldRoundTripCorrectly() throws JAXBException {
        DisplayPayload original = createSampleReceipt();

        String base64 = DisplayPayloadHelper.toBase64(original);
        DisplayPayload decoded = DisplayPayloadHelper.fromBase64(base64);

        assertEquals(original.getLayout(), decoded.getLayout());
        assertNotNull(decoded.getReceipt());
        assertNotNull(decoded.getReceipt().getLineItems());
        assertEquals(1, decoded.getReceipt().getLineItems().getLineItem().size());
        assertEquals("Running shoes", decoded.getReceipt().getLineItems().getLineItem().get(0).getDescription());
    }

    @Test
    void toXmlAndFromXmlShouldRoundTripCorrectly() throws JAXBException {
        DisplayPayload original = createSampleReceipt();

        String xml = DisplayPayloadHelper.toXml(original);
        DisplayPayload parsed = DisplayPayloadHelper.fromXml(xml);

        assertEquals(original.getLayout(), parsed.getLayout());
        assertEquals(
                new BigDecimal("79.99"),
                parsed.getReceipt().getLineItems().getLineItem().get(0).getAmount().getValue()
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // Input Payload Encoding
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void inputPayloadToBase64ShouldProduceValidBase64() throws JAXBException {
        InputPayload payload = DisplayPayloadHelper.confirmation("Test prompt");

        String base64 = DisplayPayloadHelper.toBase64(payload);

        assertFalse(base64.contains("<"));
        assertDoesNotThrow(() -> Base64.getDecoder().decode(base64));
    }

    @Test
    void inputPayloadToXmlShouldContainNamespace() throws JAXBException {
        InputPayload payload = DisplayPayloadHelper.confirmation("Would you like a receipt?");

        String xml = DisplayPayloadHelper.toXml(payload);

        assertTrue(xml.contains("inputPayload"));
        assertTrue(xml.contains("xmlns=\"urn:bilt:input:v1\""));
        assertTrue(xml.contains("confirmation"));
        // Verify all elements are in the default namespace (no prefixes)
        assertFalse(xml.contains("ns2:"), "Should not have namespace prefixes");
        assertFalse(xml.contains("urn:bilt:common"), "Should not reference common namespace");
    }

    @Test
    void inputPayloadToBase64ShouldProduceDecodableXml() throws JAXBException {
        InputPayload original = DisplayPayloadHelper.confirmation("Would you like a receipt?");

        String base64 = DisplayPayloadHelper.toBase64(original);
        String xml = new String(Base64.getDecoder().decode(base64), java.nio.charset.StandardCharsets.UTF_8);

        // Verify the decoded XML has the expected structure
        assertTrue(xml.contains("<inputPayload"));
        assertTrue(xml.contains("xmlns=\"urn:bilt:input:v1\""));
        assertTrue(xml.contains("<display>"));
        assertTrue(xml.contains("<title>Would you like a receipt?</title>"));
        assertTrue(xml.contains("<confirmation/>"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Factory Methods
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void standbyShouldCreateValidPayload() throws JAXBException {
        DisplayPayload payload = DisplayPayloadHelper.standby("standby.xslt");

        assertEquals("standby.xslt", payload.getLayout());
        assertNotNull(payload.getStandby());
        assertNull(payload.getReceipt());

        // Should serialize without error
        String xml = DisplayPayloadHelper.toXml(payload);
        assertTrue(xml.contains("<standby"));
    }

    @Test
    void standbyWithThemeShouldIncludeTheme() throws JAXBException {
        DisplayPayload payload = DisplayPayloadHelper.standby("standby.xslt", "christmas");

        assertEquals("christmas", payload.getStandby().getTheme());
    }

    @Test
    void qrCodeShouldCreateValidPayload() throws JAXBException {
        DisplayPayload payload = DisplayPayloadHelper.qrCode("qr.xslt", "https://example.com");

        assertEquals("qr.xslt", payload.getLayout());
        assertNotNull(payload.getQrCode());
        assertEquals("https://example.com", payload.getQrCode().getData());
    }

    @Test
    void qrCodeWithHeaderAndCtaShouldIncludeBoth() throws JAXBException {
        DisplayPayload payload = DisplayPayloadHelper.qrCode(
                "qr.xslt",
                "https://example.com",
                "Scan me",
                "Scan now"
        );

        assertEquals("Scan me", payload.getQrCode().getHeader().getText());
        assertEquals("Scan now", payload.getQrCode().getCallToAction());
    }

    @Test
    void confirmationShouldCreateValidInputPayload() throws JAXBException {
        InputPayload payload = DisplayPayloadHelper.confirmation("Confirm?");

        assertNotNull(payload.getDisplay());
        assertEquals("Confirm?", payload.getDisplay().getTitle());
        assertNotNull(payload.getConfirmation());
        assertNull(payload.getSignature());
    }

    @Test
    void signatureShouldCreateValidInputPayload() throws JAXBException {
        InputPayload payload = DisplayPayloadHelper.signature("Please sign");

        assertNotNull(payload.getDisplay());
        assertEquals("Please sign", payload.getDisplay().getTitle());
        assertNotNull(payload.getSignature());
        assertNull(payload.getConfirmation());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Builder Helpers
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void moneyShouldCreateCorrectType() {
        MoneyType money = DisplayPayloadHelper.money("$", 99.99);

        assertEquals("$", money.getCurrency());
        assertEquals(new BigDecimal("99.99"), money.getValue());
    }

    @Test
    void moneyWithBigDecimalShouldPreservePrecision() {
        MoneyType money = DisplayPayloadHelper.money("USD", new BigDecimal("123.456"));

        assertEquals(new BigDecimal("123.456"), money.getValue());
    }

    @Test
    void labeledAmountShouldCreateCorrectType() {
        LabeledAmountType amount = DisplayPayloadHelper.labeledAmount("Total", "$", 50.00);

        assertEquals("Total", amount.getDescription());
        assertEquals("$", amount.getAmount().getCurrency());
        assertEquals(new BigDecimal("50.0"), amount.getAmount().getValue());
    }

    @Test
    void lineItemShouldCreateCorrectType() {
        LineItemType item = DisplayPayloadHelper.lineItem(LineItemKindType.HEADING, "SALES");

        assertEquals(LineItemKindType.HEADING, item.getKind());
        assertEquals("SALES", item.getDescription());
    }

    @Test
    void productItemShouldCreateCompleteLineItem() {
        LineItemType item = DisplayPayloadHelper.productItem(
                "Widget",
                new BigDecimal("2"),
                "$",
                new BigDecimal("10.00"),
                new BigDecimal("20.00")
        );

        assertEquals(LineItemKindType.ITEM, item.getKind());
        assertEquals("Widget", item.getDescription());
        assertEquals(new BigDecimal("2"), item.getQuantity());
        assertEquals(new BigDecimal("10.00"), item.getUnitPrice().getValue());
        assertEquals(new BigDecimal("20.00"), item.getAmount().getValue());
    }

    @Test
    void headerShouldCreateCorrectType() {
        HeaderFooterType hf = DisplayPayloadHelper.header("Your items");

        assertEquals("Your items", hf.getText());
    }

    @Test
    void footerShouldCreateCorrectType() {
        HeaderFooterType hf = DisplayPayloadHelper.footer("Thank you!");

        assertEquals("Thank you!", hf.getText());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Complex Receipt Serialization
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void complexReceiptShouldSerializeCorrectly() throws JAXBException {
        DisplayPayload payload = new DisplayPayload();
        payload.setLayout("receipt.xslt");

        ReceiptType receipt = new ReceiptType();
        receipt.setHeader(DisplayPayloadHelper.header("Your items"));

        LineItemsType lineItems = new LineItemsType();
        lineItems.getLineItem().add(DisplayPayloadHelper.lineItem(LineItemKindType.HEADING, "SALES"));
        lineItems.getLineItem().add(DisplayPayloadHelper.productItem(
                "Running shoes", BigDecimal.ONE, "$", new BigDecimal("79.99"), new BigDecimal("79.99")
        ));
        lineItems.getLineItem().add(DisplayPayloadHelper.lineItem(LineItemKindType.SEPARATOR, null));
        receipt.setLineItems(lineItems);

        TaxType tax = new TaxType();
        tax.getTaxItem().add(DisplayPayloadHelper.labeledAmount("State tax", "$", 6.40));
        tax.setTaxTotal(DisplayPayloadHelper.labeledAmount("Total tax", "$", 6.40));
        receipt.setTax(tax);

        receipt.setTotal(DisplayPayloadHelper.labeledAmount("Total", "$", 86.39));
        receipt.setFooter(DisplayPayloadHelper.footer("Thank you!"));

        payload.setReceipt(receipt);

        String xml = DisplayPayloadHelper.toXml(payload);

        assertTrue(xml.contains("SALES"));
        assertTrue(xml.contains("Running shoes"));
        assertTrue(xml.contains("79.99"));
        assertTrue(xml.contains("State tax"));
        assertTrue(xml.contains("Thank you!"));
    }

    @Test
    void receiptWithAllLineItemKindsShouldSerialize() throws JAXBException {
        DisplayPayload payload = new DisplayPayload();
        payload.setLayout("receipt.xslt");

        ReceiptType receipt = new ReceiptType();
        LineItemsType lineItems = new LineItemsType();

        // Add all kinds of line items
        lineItems.getLineItem().add(DisplayPayloadHelper.lineItem(LineItemKindType.HEADING, "HEADING"));
        lineItems.getLineItem().add(DisplayPayloadHelper.lineItem(LineItemKindType.ITEM, "ITEM"));
        lineItems.getLineItem().add(DisplayPayloadHelper.lineItem(LineItemKindType.DISCOUNT, "DISCOUNT"));
        lineItems.getLineItem().add(DisplayPayloadHelper.lineItem(LineItemKindType.RETURN, "RETURN"));
        lineItems.getLineItem().add(DisplayPayloadHelper.lineItem(LineItemKindType.VOID, "VOID"));
        lineItems.getLineItem().add(DisplayPayloadHelper.lineItem(LineItemKindType.SEPARATOR, null));
        lineItems.getLineItem().add(DisplayPayloadHelper.lineItem(LineItemKindType.SPACER, null));

        receipt.setLineItems(lineItems);
        payload.setReceipt(receipt);

        String xml = DisplayPayloadHelper.toXml(payload);

        assertTrue(xml.contains("heading"));
        assertTrue(xml.contains("item"));
        assertTrue(xml.contains("discount"));
        assertTrue(xml.contains("return"));
        assertTrue(xml.contains("void"));
        assertTrue(xml.contains("separator"));
        assertTrue(xml.contains("spacer"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════

    private DisplayPayload createSampleReceipt() {
        DisplayPayload payload = new DisplayPayload();
        payload.setLayout("receipt.xslt");

        ReceiptType receipt = new ReceiptType();
        LineItemsType lineItems = new LineItemsType();

        LineItemType item = new LineItemType();
        item.setKind(LineItemKindType.ITEM);
        item.setDescription("Running shoes");
        item.setAmount(DisplayPayloadHelper.money("USD", new BigDecimal("79.99")));

        lineItems.getLineItem().add(item);
        receipt.setLineItems(lineItems);
        payload.setReceipt(receipt);

        return payload;
    }
}
