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
 * Format of the PIN block before encryption, per ISO 9564.
 */
public enum PINFormatEnum {
    ISO0, ISO1, ISO2, ISO3;

    @JsonValue
    public String toValue() {
        switch (this) {
            case ISO0: return "ISO0";
            case ISO1: return "ISO1";
            case ISO2: return "ISO2";
            case ISO3: return "ISO3";
        }
        return null;
    }

    @JsonCreator
    public static PINFormatEnum forValue(String value) throws IOException {
        if (value.equals("ISO0")) return ISO0;
        if (value.equals("ISO1")) return ISO1;
        if (value.equals("ISO2")) return ISO2;
        if (value.equals("ISO3")) return ISO3;
        throw new IOException("Cannot deserialize PINFormatEnum");
    }
}
