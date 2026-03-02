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
 * Type of operation to perform on a stored value account: Reserve, Activate, Load, Unload,
 * Reverse, or Duplicate.
 */
public enum StoredValueTransactionTypeEnum {
    ACTIVATE, DUPLICATE, LOAD, RESERVE, REVERSE, UNLOAD;

    @JsonValue
    public String toValue() {
        switch (this) {
            case ACTIVATE: return "Activate";
            case DUPLICATE: return "Duplicate";
            case LOAD: return "Load";
            case RESERVE: return "Reserve";
            case REVERSE: return "Reverse";
            case UNLOAD: return "Unload";
        }
        return null;
    }

    @JsonCreator
    public static StoredValueTransactionTypeEnum forValue(String value) throws IOException {
        if (value.equals("Activate")) return ACTIVATE;
        if (value.equals("Duplicate")) return DUPLICATE;
        if (value.equals("Load")) return LOAD;
        if (value.equals("Reserve")) return RESERVE;
        if (value.equals("Reverse")) return REVERSE;
        if (value.equals("Unload")) return UNLOAD;
        throw new IOException("Cannot deserialize StoredValueTransactionTypeEnum");
    }
}
