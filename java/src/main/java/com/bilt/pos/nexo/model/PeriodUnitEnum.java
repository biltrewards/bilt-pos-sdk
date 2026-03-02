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
 * Unit of the period between consecutive instalment payments.
 */
public enum PeriodUnitEnum {
    ANNUAL, DAILY, MONTHLY, WEEKLY;

    @JsonValue
    public String toValue() {
        switch (this) {
            case ANNUAL: return "Annual";
            case DAILY: return "Daily";
            case MONTHLY: return "Monthly";
            case WEEKLY: return "Weekly";
        }
        return null;
    }

    @JsonCreator
    public static PeriodUnitEnum forValue(String value) throws IOException {
        if (value.equals("Annual")) return ANNUAL;
        if (value.equals("Daily")) return DAILY;
        if (value.equals("Monthly")) return MONTHLY;
        if (value.equals("Weekly")) return WEEKLY;
        throw new IOException("Cannot deserialize PeriodUnitEnum");
    }
}
