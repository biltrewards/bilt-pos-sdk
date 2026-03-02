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
 * Hardware capabilities of the Sale Terminal that the POI System is allowed to use. Sent in
 * the Login Request to identify available Sale Terminal devices. In subsequent messages,
 * updated when a device becomes unavailable.
 */
public enum SaleCapabilitiesType {
    CASHIER_DISPLAY, CASHIER_ERROR, CASHIER_INPUT, CASHIER_STATUS, CUSTOMER_ASSISTANCE, CUSTOMER_DISPLAY, CUSTOMER_ERROR, CUSTOMER_INPUT, EMV_CONTACTLESS, ICC, MAG_STRIPE, POI_REPLICATION, PRINTER_DOCUMENT, PRINTER_RECEIPT, PRINTER_VOUCHER;

    @JsonValue
    public String toValue() {
        switch (this) {
            case CASHIER_DISPLAY: return "CashierDisplay";
            case CASHIER_ERROR: return "CashierError";
            case CASHIER_INPUT: return "CashierInput";
            case CASHIER_STATUS: return "CashierStatus";
            case CUSTOMER_ASSISTANCE: return "CustomerAssistance";
            case CUSTOMER_DISPLAY: return "CustomerDisplay";
            case CUSTOMER_ERROR: return "CustomerError";
            case CUSTOMER_INPUT: return "CustomerInput";
            case EMV_CONTACTLESS: return "EMVContactless";
            case ICC: return "ICC";
            case MAG_STRIPE: return "MagStripe";
            case POI_REPLICATION: return "POIReplication";
            case PRINTER_DOCUMENT: return "PrinterDocument";
            case PRINTER_RECEIPT: return "PrinterReceipt";
            case PRINTER_VOUCHER: return "PrinterVoucher";
        }
        return null;
    }

    @JsonCreator
    public static SaleCapabilitiesType forValue(String value) throws IOException {
        if (value.equals("CashierDisplay")) return CASHIER_DISPLAY;
        if (value.equals("CashierError")) return CASHIER_ERROR;
        if (value.equals("CashierInput")) return CASHIER_INPUT;
        if (value.equals("CashierStatus")) return CASHIER_STATUS;
        if (value.equals("CustomerAssistance")) return CUSTOMER_ASSISTANCE;
        if (value.equals("CustomerDisplay")) return CUSTOMER_DISPLAY;
        if (value.equals("CustomerError")) return CUSTOMER_ERROR;
        if (value.equals("CustomerInput")) return CUSTOMER_INPUT;
        if (value.equals("EMVContactless")) return EMV_CONTACTLESS;
        if (value.equals("ICC")) return ICC;
        if (value.equals("MagStripe")) return MAG_STRIPE;
        if (value.equals("POIReplication")) return POI_REPLICATION;
        if (value.equals("PrinterDocument")) return PRINTER_DOCUMENT;
        if (value.equals("PrinterReceipt")) return PRINTER_RECEIPT;
        if (value.equals("PrinterVoucher")) return PRINTER_VOUCHER;
        throw new IOException("Cannot deserialize SaleCapabilitiesType");
    }
}
