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
 * Unit of a loyalty amount: Point (numeric points) or Monetary (amount in a currency).
 */
public enum LoyaltyUnitEnum {
    MONETARY, POINT;

    @JsonValue
    public String toValue() {
        switch (this) {
            case MONETARY: return "Monetary";
            case POINT: return "Point";
        }
        return null;
    }

    @JsonCreator
    public static LoyaltyUnitEnum forValue(String value) throws IOException {
        if (value.equals("Monetary")) return MONETARY;
        if (value.equals("Point")) return POINT;
        throw new IOException("Cannot deserialize LoyaltyUnitEnum");
    }
}
