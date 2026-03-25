package com.bilt.pos.receipt;

import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class ReceiptHelperTest {

    @Test
    void toXmlShouldProduceValidXmlWithNamespace() throws JAXBException {
        ReceiptType receipt = createSampleReceipt();

        String xml = ReceiptHelper.toXml(receipt);

        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\""));
        assertTrue(xml.contains("receipt"));
        assertTrue(xml.contains("urn:bilt:receipt:v1"));
    }

    @Test
    void toXmlShouldNotContainStandaloneYes() throws JAXBException {
        ReceiptType receipt = createSampleReceipt();

        String xml = ReceiptHelper.toXml(receipt);

        assertFalse(xml.contains("standalone=\"yes\""));
    }

    @Test
    void toBase64ShouldProduceDecodableString() throws JAXBException {
        ReceiptType receipt = createSampleReceipt();

        String base64 = ReceiptHelper.toBase64(receipt);

        assertFalse(base64.contains("<"));
        assertDoesNotThrow(() -> Base64.getDecoder().decode(base64));
    }

    @Test
    void roundTripXmlShouldPreserveData() throws JAXBException {
        ReceiptType original = createSampleReceipt();

        String xml = ReceiptHelper.toXml(original);
        ReceiptType parsed = ReceiptHelper.fromXml(xml);

        assertEquals("Test plain text receipt", parsed.getPlainTextReceipt());
        assertEquals(ReceiptCopyEnum.CUSTOMER, parsed.getType());
        assertEquals("1.0", parsed.getVersion());
        assertNotNull(parsed.getReceiptData());
        assertEquals("VISA", parsed.getReceiptData().getCardBrand());
        assertEquals("****1234", parsed.getReceiptData().getMaskedPAN());
    }

    @Test
    void roundTripBase64ShouldPreserveData() throws JAXBException {
        ReceiptType original = createSampleReceipt();

        String base64 = ReceiptHelper.toBase64(original);
        ReceiptType parsed = ReceiptHelper.fromBase64(base64);

        assertEquals(original.getPlainTextReceipt(), parsed.getPlainTextReceipt());
        assertEquals(original.getType(), parsed.getType());
        assertEquals(original.getReceiptData().getCardBrand(), parsed.getReceiptData().getCardBrand());
    }

    @Test
    void htmlReceiptShouldRoundTripAsBase64Binary() throws JAXBException {
        ReceiptType original = new ReceiptType();
        original.setType(ReceiptCopyEnum.CUSTOMER);
        original.setVersion("1.0");
        original.setHtmlReceipt("<html><body>SALE $0.01</body></html>".getBytes(StandardCharsets.UTF_8));

        String xml = ReceiptHelper.toXml(original);
        ReceiptType parsed = ReceiptHelper.fromXml(xml);

        assertNotNull(parsed.getHtmlReceipt());
        assertEquals(
                "<html><body>SALE $0.01</body></html>",
                new String(parsed.getHtmlReceipt(), StandardCharsets.UTF_8)
        );
    }

    @Test
    void htmlAsStringShouldDecodeHtmlReceipt() {
        ReceiptType receipt = new ReceiptType();
        receipt.setHtmlReceipt("<html><body>Test</body></html>".getBytes(StandardCharsets.UTF_8));

        assertEquals("<html><body>Test</body></html>", ReceiptHelper.htmlAsString(receipt));
    }

    @Test
    void htmlAsStringShouldReturnNullWhenNoHtml() {
        ReceiptType receipt = new ReceiptType();

        assertNull(ReceiptHelper.htmlAsString(receipt));
    }

    @Test
    void receiptWithAllFieldsShouldRoundTrip() throws JAXBException {
        ReceiptType original = new ReceiptType();
        original.setType(ReceiptCopyEnum.MERCHANT);
        original.setVersion("1.0");
        original.setPlainTextReceipt("Verifone\nSALE\nTOTAL $10.00");
        original.setHtmlReceipt("<html>receipt</html>".getBytes(StandardCharsets.UTF_8));

        ReceiptDataType data = new ReceiptDataType();
        data.setTransactionType("SALE");
        data.setTransactionResult("APPROVED");
        data.setTransactionResultCode("0000");
        data.setMerchantName("Verifone");
        data.setMerchantID("****0002");
        data.setTerminalID("**0004");
        data.setCardBrand("VISA");
        data.setMaskedPAN("************9791");
        data.setTotalAmount("10.00");
        data.setCurrency("USD");
        data.setAuthCode("789DE");
        data.setAid("A0000000031010");
        data.setTvr("0000000000");
        original.setReceiptData(data);

        String xml = ReceiptHelper.toXml(original);
        ReceiptType parsed = ReceiptHelper.fromXml(xml);

        assertEquals(ReceiptCopyEnum.MERCHANT, parsed.getType());
        assertEquals("Verifone\nSALE\nTOTAL $10.00", parsed.getPlainTextReceipt());
        assertEquals("SALE", parsed.getReceiptData().getTransactionType());
        assertEquals("APPROVED", parsed.getReceiptData().getTransactionResult());
        assertEquals("****0002", parsed.getReceiptData().getMerchantID());
        assertEquals("789DE", parsed.getReceiptData().getAuthCode());
        assertEquals("A0000000031010", parsed.getReceiptData().getAid());
    }

    private ReceiptType createSampleReceipt() {
        ReceiptType receipt = new ReceiptType();
        receipt.setType(ReceiptCopyEnum.CUSTOMER);
        receipt.setVersion("1.0");
        receipt.setPlainTextReceipt("Test plain text receipt");

        ReceiptDataType data = new ReceiptDataType();
        data.setCardBrand("VISA");
        data.setMaskedPAN("****1234");
        data.setTotalAmount("10.00");
        receipt.setReceiptData(data);

        return receipt;
    }
}
