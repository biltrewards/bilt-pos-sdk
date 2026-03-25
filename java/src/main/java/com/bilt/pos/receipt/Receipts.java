/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   High-level API for extracting parsed receipts from Nexo payment responses.
 */
package com.bilt.pos.receipt;

import com.bilt.pos.nexo.model.BalanceInquiryResponse;
import com.bilt.pos.nexo.model.DocumentQualifierEnum;
import com.bilt.pos.nexo.model.LoyaltyResponse;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.PaymentReceipt;
import com.bilt.pos.nexo.model.PaymentResponse;
import com.bilt.pos.nexo.model.ReversalResponse;
import com.bilt.pos.nexo.model.SaleToPOIResponse;

import jakarta.xml.bind.JAXBException;

/**
 * Extracts and parses receipt XML payloads from a Nexo {@link NexoTerminalAPI} response.
 *
 * <p>After a payment, reversal, loyalty, or balance inquiry, the terminal returns
 * receipts inside {@code PaymentReceipt[]} with Base64-encoded XML in
 * {@code OutputContent.OutputXHTML}. This class locates the customer and merchant
 * receipt entries, decodes them, and provides typed access to the receipt content.
 *
 * <p>Example usage:
 * <pre>{@code
 * NexoTerminalAPI response = client.request(paymentRequest);
 * Receipts receipts = Receipts.fromResponse(response);
 *
 * if (receipts.hasCustomerReceipt()) {
 *     ReceiptType customer = receipts.customerReceipt();
 *     String plainText = customer.getPlainTextReceipt();
 *     String html = ReceiptHelper.htmlAsString(customer);
 *     ReceiptDataType data = customer.getReceiptData();
 * }
 * }</pre>
 */
public final class Receipts {

    private final ReceiptType customerReceipt;
    private final ReceiptType merchantReceipt;

    private Receipts(ReceiptType customerReceipt, ReceiptType merchantReceipt) {
        this.customerReceipt = customerReceipt;
        this.merchantReceipt = merchantReceipt;
    }

    /**
     * Extracts receipts from a {@link NexoTerminalAPI} response.
     *
     * <p>Searches for {@code PaymentReceipt} entries across payment, reversal,
     * loyalty, and balance inquiry responses.
     *
     * @param response the Nexo terminal API response
     * @return a {@link Receipts} instance (receipts may be absent if the response
     *         does not contain them)
     * @throws JAXBException if receipt XML parsing fails
     * @throws IllegalArgumentException if the response or its SaleToPOIResponse is null
     */
    public static Receipts fromResponse(NexoTerminalAPI response) throws JAXBException {
        if (response == null || response.getSaleToPOIResponse() == null) {
            throw new IllegalArgumentException("Response and SaleToPOIResponse must not be null");
        }
        PaymentReceipt[] receipts = findPaymentReceipts(response.getSaleToPOIResponse());
        return fromPaymentReceipts(receipts);
    }

    /**
     * Extracts receipts directly from a {@link PaymentReceipt} array.
     *
     * @param paymentReceipts the array of payment receipts (may be null)
     * @return a {@link Receipts} instance
     * @throws JAXBException if receipt XML parsing fails
     */
    public static Receipts fromPaymentReceipts(PaymentReceipt[] paymentReceipts) throws JAXBException {
        ReceiptType customer = null;
        ReceiptType merchant = null;

        if (paymentReceipts != null) {
            for (PaymentReceipt pr : paymentReceipts) {
                if (pr.getDocumentQualifier() == null || pr.getOutputContent() == null) {
                    continue;
                }
                String xhtml = pr.getOutputContent().getOutputXHTML();
                if (xhtml == null || xhtml.isEmpty()) {
                    continue;
                }
                ReceiptType parsed = ReceiptHelper.fromBase64(xhtml);
                if (pr.getDocumentQualifier() == DocumentQualifierEnum.CUSTOMER_RECEIPT) {
                    customer = parsed;
                } else if (pr.getDocumentQualifier() == DocumentQualifierEnum.CASHIER_RECEIPT) {
                    merchant = parsed;
                }
            }
        }

        return new Receipts(customer, merchant);
    }

    /**
     * Returns the parsed customer receipt, or {@code null} if not present.
     */
    public ReceiptType customerReceipt() {
        return customerReceipt;
    }

    /**
     * Returns the parsed merchant receipt, or {@code null} if not present.
     */
    public ReceiptType merchantReceipt() {
        return merchantReceipt;
    }

    /**
     * Returns {@code true} if a customer receipt is present.
     */
    public boolean hasCustomerReceipt() {
        return customerReceipt != null;
    }

    /**
     * Returns {@code true} if a merchant receipt is present.
     */
    public boolean hasMerchantReceipt() {
        return merchantReceipt != null;
    }

    private static PaymentReceipt[] findPaymentReceipts(SaleToPOIResponse response) {
        PaymentResponse paymentResponse = response.getPaymentResponse();
        if (paymentResponse != null && paymentResponse.getPaymentReceipt() != null) {
            return paymentResponse.getPaymentReceipt();
        }
        ReversalResponse reversalResponse = response.getReversalResponse();
        if (reversalResponse != null && reversalResponse.getPaymentReceipt() != null) {
            return reversalResponse.getPaymentReceipt();
        }
        LoyaltyResponse loyaltyResponse = response.getLoyaltyResponse();
        if (loyaltyResponse != null && loyaltyResponse.getPaymentReceipt() != null) {
            return loyaltyResponse.getPaymentReceipt();
        }
        BalanceInquiryResponse balanceResponse = response.getBalanceInquiryResponse();
        if (balanceResponse != null && balanceResponse.getPaymentReceipt() != null) {
            return balanceResponse.getPaymentReceipt();
        }
        return null;
    }
}
