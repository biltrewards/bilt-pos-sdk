package com.bilt.pos.receipt;

import com.bilt.pos.nexo.model.*;
import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReceiptsTest {

    @Test
    void fromResponseShouldExtractCustomerReceipt() throws JAXBException {
        NexoTerminalAPI response = buildPaymentResponse(
                buildPaymentReceipt(DocumentQualifierEnum.CUSTOMER_RECEIPT, sampleReceiptBase64("CUSTOMER"))
        );

        Receipts receipts = Receipts.fromResponse(response);

        assertTrue(receipts.hasCustomerReceipt());
        assertFalse(receipts.hasMerchantReceipt());
        assertEquals(ReceiptCopyEnum.CUSTOMER, receipts.customerReceipt().getType());
    }

    @Test
    void fromResponseShouldExtractMerchantReceipt() throws JAXBException {
        NexoTerminalAPI response = buildPaymentResponse(
                buildPaymentReceipt(DocumentQualifierEnum.CASHIER_RECEIPT, sampleReceiptBase64("MERCHANT"))
        );

        Receipts receipts = Receipts.fromResponse(response);

        assertFalse(receipts.hasCustomerReceipt());
        assertTrue(receipts.hasMerchantReceipt());
        assertEquals(ReceiptCopyEnum.MERCHANT, receipts.merchantReceipt().getType());
    }

    @Test
    void fromResponseShouldExtractBothReceipts() throws JAXBException {
        PaymentReceipt customer = buildPaymentReceipt(
                DocumentQualifierEnum.CUSTOMER_RECEIPT, sampleReceiptBase64("CUSTOMER")
        );
        PaymentReceipt merchant = buildPaymentReceipt(
                DocumentQualifierEnum.CASHIER_RECEIPT, sampleReceiptBase64("MERCHANT")
        );

        NexoTerminalAPI response = NexoTerminalAPI.builder()
                .saleToPOIResponse(SaleToPOIResponse.builder()
                        .paymentResponse(PaymentResponse.builder()
                                .paymentReceipt(new PaymentReceipt[]{customer, merchant})
                                .build())
                        .build())
                .build();

        Receipts receipts = Receipts.fromResponse(response);

        assertTrue(receipts.hasCustomerReceipt());
        assertTrue(receipts.hasMerchantReceipt());
    }

    @Test
    void fromResponseShouldExtractReceiptsFromReversalResponse() throws JAXBException {
        NexoTerminalAPI response = NexoTerminalAPI.builder()
                .saleToPOIResponse(SaleToPOIResponse.builder()
                        .reversalResponse(ReversalResponse.builder()
                                .paymentReceipt(new PaymentReceipt[]{
                                        buildPaymentReceipt(DocumentQualifierEnum.CUSTOMER_RECEIPT, sampleReceiptBase64("CUSTOMER"))
                                })
                                .build())
                        .build())
                .build();

        Receipts receipts = Receipts.fromResponse(response);

        assertTrue(receipts.hasCustomerReceipt());
    }

    @Test
    void fromResponseWithNullReceiptsShouldReturnEmpty() throws JAXBException {
        NexoTerminalAPI response = NexoTerminalAPI.builder()
                .saleToPOIResponse(SaleToPOIResponse.builder()
                        .paymentResponse(PaymentResponse.builder().build())
                        .build())
                .build();

        Receipts receipts = Receipts.fromResponse(response);

        assertFalse(receipts.hasCustomerReceipt());
        assertFalse(receipts.hasMerchantReceipt());
        assertNull(receipts.customerReceipt());
        assertNull(receipts.merchantReceipt());
    }

    @Test
    void fromResponseShouldThrowOnNull() {
        assertThrows(IllegalArgumentException.class, () -> Receipts.fromResponse(null));
    }

    @Test
    void fromResponseShouldThrowOnNullSaleToPOIResponse() {
        NexoTerminalAPI response = NexoTerminalAPI.builder().build();
        assertThrows(IllegalArgumentException.class, () -> Receipts.fromResponse(response));
    }

    @Test
    void fromPaymentReceiptsShouldSkipEntriesWithNullQualifier() throws JAXBException {
        PaymentReceipt pr = PaymentReceipt.builder()
                .outputContent(OutputContent.builder()
                        .outputXHTML(sampleReceiptBase64("CUSTOMER"))
                        .build())
                .build();

        Receipts receipts = Receipts.fromPaymentReceipts(new PaymentReceipt[]{pr});

        assertFalse(receipts.hasCustomerReceipt());
        assertFalse(receipts.hasMerchantReceipt());
    }

    @Test
    void fromPaymentReceiptsShouldSkipEntriesWithNullOutputContent() throws JAXBException {
        PaymentReceipt pr = PaymentReceipt.builder()
                .documentQualifier(DocumentQualifierEnum.CUSTOMER_RECEIPT)
                .build();

        Receipts receipts = Receipts.fromPaymentReceipts(new PaymentReceipt[]{pr});

        assertFalse(receipts.hasCustomerReceipt());
    }

    @Test
    void fromPaymentReceiptsShouldHandleNullArray() throws JAXBException {
        Receipts receipts = Receipts.fromPaymentReceipts(null);

        assertFalse(receipts.hasCustomerReceipt());
        assertFalse(receipts.hasMerchantReceipt());
    }

    @Test
    void receiptDataShouldBeAccessibleFromExtractedReceipt() throws JAXBException {
        ReceiptType receipt = new ReceiptType();
        receipt.setType(ReceiptCopyEnum.CUSTOMER);
        receipt.setVersion("1.0");
        receipt.setPlainTextReceipt("SALE\nTOTAL $10.00");
        ReceiptDataType data = new ReceiptDataType();
        data.setCardBrand("VISA");
        data.setTotalAmount("10.00");
        data.setTransactionResult("APPROVED");
        receipt.setReceiptData(data);

        NexoTerminalAPI response = buildPaymentResponse(
                buildPaymentReceipt(DocumentQualifierEnum.CUSTOMER_RECEIPT, ReceiptHelper.toBase64(receipt))
        );

        Receipts receipts = Receipts.fromResponse(response);
        ReceiptType extracted = receipts.customerReceipt();

        assertEquals("SALE\nTOTAL $10.00", extracted.getPlainTextReceipt());
        assertEquals("VISA", extracted.getReceiptData().getCardBrand());
        assertEquals("10.00", extracted.getReceiptData().getTotalAmount());
        assertEquals("APPROVED", extracted.getReceiptData().getTransactionResult());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════

    private NexoTerminalAPI buildPaymentResponse(PaymentReceipt... receipts) {
        return NexoTerminalAPI.builder()
                .saleToPOIResponse(SaleToPOIResponse.builder()
                        .paymentResponse(PaymentResponse.builder()
                                .paymentReceipt(receipts)
                                .build())
                        .build())
                .build();
    }

    private PaymentReceipt buildPaymentReceipt(DocumentQualifierEnum qualifier, String base64) {
        return PaymentReceipt.builder()
                .documentQualifier(qualifier)
                .outputContent(OutputContent.builder()
                        .outputFormat(OutputFormatEnum.XHTML)
                        .outputXHTML(base64)
                        .build())
                .build();
    }

    private String sampleReceiptBase64(String copyType) {
        try {
            ReceiptType receipt = new ReceiptType();
            receipt.setType(ReceiptCopyEnum.valueOf(copyType));
            receipt.setVersion("1.0");
            receipt.setPlainTextReceipt("Sample receipt");
            return ReceiptHelper.toBase64(receipt);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }
}
