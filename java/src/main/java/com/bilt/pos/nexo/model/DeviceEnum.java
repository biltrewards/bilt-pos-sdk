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
 * Logical device on a Sale or POI Terminal: CashierDisplay, CustomerDisplay, CashierInput,
 * or CustomerInput.
 */
public enum DeviceEnum {
    CASHIER_DISPLAY, CASHIER_INPUT, CUSTOMER_DISPLAY, CUSTOMER_INPUT;

    @JsonValue
    public String toValue() {
        switch (this) {
            case CASHIER_DISPLAY: return "CashierDisplay";
            case CASHIER_INPUT: return "CashierInput";
            case CUSTOMER_DISPLAY: return "CustomerDisplay";
            case CUSTOMER_INPUT: return "CustomerInput";
        }
        return null;
    }

    @JsonCreator
    public static DeviceEnum forValue(String value) throws IOException {
        if (value.equals("CashierDisplay")) return CASHIER_DISPLAY;
        if (value.equals("CashierInput")) return CASHIER_INPUT;
        if (value.equals("CustomerDisplay")) return CUSTOMER_DISPLAY;
        if (value.equals("CustomerInput")) return CUSTOMER_INPUT;
        throw new IOException("Cannot deserialize DeviceEnum");
    }
}
