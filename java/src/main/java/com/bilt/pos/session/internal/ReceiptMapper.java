/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   Internal API — subject to change without notice.
 */
package com.bilt.pos.session.internal;

import com.bilt.pos.nexo.model.DocumentQualifierEnum;
import com.bilt.pos.nexo.model.PaymentReceipt;
import com.bilt.pos.session.Receipt;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extracts session {@link Receipt}s from Nexo {@code PaymentReceipt} arrays.
 * Best-effort: an unparsable receipt payload yields {@code null} and a WARN
 * log rather than failing the transaction that produced it.
 */
public final class ReceiptMapper {

    private static final Logger LOGGER = Logger.getLogger(ReceiptMapper.class.getName());

    private ReceiptMapper() {
    }

    /** The customer copy, or {@code null}. */
    public static Receipt customerReceipt(PaymentReceipt[] receipts) {
        return extract(receipts, DocumentQualifierEnum.CUSTOMER_RECEIPT);
    }

    /** The merchant/cashier copy, or {@code null}. */
    public static Receipt merchantReceipt(PaymentReceipt[] receipts) {
        Receipt cashier = extract(receipts, DocumentQualifierEnum.CASHIER_RECEIPT);
        return cashier != null ? cashier : extract(receipts, DocumentQualifierEnum.SALE_RECEIPT);
    }

    private static Receipt extract(PaymentReceipt[] receipts, DocumentQualifierEnum qualifier) {
        if (receipts == null) {
            return null;
        }
        for (PaymentReceipt receipt : receipts) {
            if (receipt.getDocumentQualifier() == qualifier
                    && receipt.getOutputContent() != null
                    && receipt.getOutputContent().getOutputXHTML() != null) {
                try {
                    return Receipt.fromBase64(receipt.getOutputContent().getOutputXHTML());
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "unparsable receipt payload", e);
                    return null;
                }
            }
        }
        return null;
    }
}
