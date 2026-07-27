/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session;

import com.bilt.pos.receipt.ReceiptDataType;
import com.bilt.pos.receipt.ReceiptHelper;
import com.bilt.pos.receipt.ReceiptType;

import jakarta.xml.bind.JAXBException;

/**
 * A rendered receipt returned from a payment, refund, or void operation.
 *
 * <p>Thin, decoded view over the SDK's {@link ReceiptType}
 * ({@code receipt.xsd}, {@code urn:bilt:receipt:v1}) as carried
 * Base64-encoded in Nexo {@code PaymentReceipt} content.</p>
 */
public final class Receipt {

    private final String html;
    private final String plainText;
    private final ReceiptDataType receiptData;

    private Receipt(String html, String plainText, ReceiptDataType receiptData) {
        this.html = html;
        this.plainText = plainText;
        this.receiptData = receiptData;
    }

    /** Builds a {@code Receipt} from a parsed {@link ReceiptType}. */
    public static Receipt from(ReceiptType receipt) {
        if (receipt == null) {
            return null;
        }
        return new Receipt(
                ReceiptHelper.htmlAsString(receipt),
                receipt.getPlainTextReceipt(),
                receipt.getReceiptData());
    }

    /**
     * Builds a {@code Receipt} from a Base64-encoded receipt XML payload, as
     * found in {@code OutputContent.OutputXHTML}.
     *
     * @throws JAXBException if the payload cannot be parsed
     */
    public static Receipt fromBase64(String base64) throws JAXBException {
        if (base64 == null) {
            return null;
        }
        return from(ReceiptHelper.fromBase64(base64));
    }

    /** The rendered HTML receipt, or {@code null} if not present. */
    public String getHtml() {
        return html;
    }

    /** The plain-text receipt, or {@code null} if not present. */
    public String getPlainText() {
        return plainText;
    }

    /**
     * Structured receipt fields — merchant identity, amounts, card data, and
     * EMV tags — or {@code null} if not present.
     */
    public ReceiptDataType getReceiptData() {
        return receiptData;
    }
}
