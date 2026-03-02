/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   This file is auto-generated from the Nexo Sale to POI v3.0 JSON Schema.
 *   Do not modify manually — re-run code generation instead.
 */
package com.bilt.pos.nexo.model;

import java.io.IOException;
import com.fasterxml.jackson.annotation.*;

/**
 * Qualification of the document to print: SaleReceipt, CashierReceipt, CustomerReceipt,
 * Document, Voucher, or Journal.
 */
public enum DocumentQualifierEnum {
    CASHIER_RECEIPT, CUSTOMER_RECEIPT, DOCUMENT, JOURNAL, SALE_RECEIPT, VOUCHER;

    @JsonValue
    public String toValue() {
        switch (this) {
            case CASHIER_RECEIPT: return "CashierReceipt";
            case CUSTOMER_RECEIPT: return "CustomerReceipt";
            case DOCUMENT: return "Document";
            case JOURNAL: return "Journal";
            case SALE_RECEIPT: return "SaleReceipt";
            case VOUCHER: return "Voucher";
        }
        return null;
    }

    @JsonCreator
    public static DocumentQualifierEnum forValue(String value) throws IOException {
        if (value.equals("CashierReceipt")) return CASHIER_RECEIPT;
        if (value.equals("CustomerReceipt")) return CUSTOMER_RECEIPT;
        if (value.equals("Document")) return DOCUMENT;
        if (value.equals("Journal")) return JOURNAL;
        if (value.equals("SaleReceipt")) return SALE_RECEIPT;
        if (value.equals("Voucher")) return VOUCHER;
        throw new IOException("Cannot deserialize DocumentQualifierEnum");
    }
}
