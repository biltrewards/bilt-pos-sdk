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
 * Overall operational status of a POI Server or POI Terminal.
 */
public enum GlobalStatusEnum {
    BUSY, MAINTENANCE, OK, UNREACHABLE;

    @JsonValue
    public String toValue() {
        switch (this) {
            case BUSY: return "Busy";
            case MAINTENANCE: return "Maintenance";
            case OK: return "OK";
            case UNREACHABLE: return "Unreachable";
        }
        return null;
    }

    @JsonCreator
    public static GlobalStatusEnum forValue(String value) throws IOException {
        if (value.equals("Busy")) return BUSY;
        if (value.equals("Maintenance")) return MAINTENANCE;
        if (value.equals("OK")) return OK;
        if (value.equals("Unreachable")) return UNREACHABLE;
        throw new IOException("Cannot deserialize GlobalStatusEnum");
    }
}
