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
 * Type of payment instrument used for the transaction.
 */
public enum PaymentInstrumentTypeEnum {
    CARD, CASH, CHECK, MOBILE, STORED_VALUE;

    @JsonValue
    public String toValue() {
        switch (this) {
            case CARD: return "Card";
            case CASH: return "Cash";
            case CHECK: return "Check";
            case MOBILE: return "Mobile";
            case STORED_VALUE: return "StoredValue";
        }
        return null;
    }

    @JsonCreator
    public static PaymentInstrumentTypeEnum forValue(String value) throws IOException {
        if (value.equals("Card")) return CARD;
        if (value.equals("Cash")) return CASH;
        if (value.equals("Check")) return CHECK;
        if (value.equals("Mobile")) return MOBILE;
        if (value.equals("StoredValue")) return STORED_VALUE;
        throw new IOException("Cannot deserialize PaymentInstrumentTypeEnum");
    }
}
