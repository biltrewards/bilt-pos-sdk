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
 * Type of barcode encoding used for display or print.
 */
public enum BarcodeTypeEnum {
    CODE128, CODE25, EAN13, EAN8, PDF417, QR_CODE, UPCA;

    @JsonValue
    public String toValue() {
        switch (this) {
            case CODE128: return "Code128";
            case CODE25: return "Code25";
            case EAN13: return "EAN13";
            case EAN8: return "EAN8";
            case PDF417: return "PDF417";
            case QR_CODE: return "QRCode";
            case UPCA: return "UPCA";
        }
        return null;
    }

    @JsonCreator
    public static BarcodeTypeEnum forValue(String value) throws IOException {
        if (value.equals("Code128")) return CODE128;
        if (value.equals("Code25")) return CODE25;
        if (value.equals("EAN13")) return EAN13;
        if (value.equals("EAN8")) return EAN8;
        if (value.equals("PDF417")) return PDF417;
        if (value.equals("QRCode")) return QR_CODE;
        if (value.equals("UPCA")) return UPCA;
        throw new IOException("Cannot deserialize BarcodeTypeEnum");
    }
}
