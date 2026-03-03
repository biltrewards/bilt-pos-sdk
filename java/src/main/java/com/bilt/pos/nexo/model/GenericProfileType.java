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
 * Functional profile of the Sale to POI protocol indicating the group of messages
 * implemented: Basic (minimum), Standard (adds device sharing), or Extended (complete
 * interface).
 */
public enum GenericProfileType {
    BASIC, EXTENDED, STANDARD;

    @JsonValue
    public String toValue() {
        switch (this) {
            case BASIC: return "Basic";
            case EXTENDED: return "Extended";
            case STANDARD: return "Standard";
        }
        return null;
    }

    @JsonCreator
    public static GenericProfileType forValue(String value) throws IOException {
        if (value.equals("Basic")) return BASIC;
        if (value.equals("Extended")) return EXTENDED;
        if (value.equals("Standard")) return STANDARD;
        throw new IOException("Cannot deserialize GenericProfileType");
    }
}
