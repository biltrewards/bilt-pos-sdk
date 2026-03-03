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
 * Format of a magnetic card track or MICR line.
 */
public enum TrackFormatEnum {
    AAMVA, CMC_7, E_13_B, ISO, JIS_I, JIS_II;

    @JsonValue
    public String toValue() {
        switch (this) {
            case AAMVA: return "AAMVA";
            case CMC_7: return "CMC-7";
            case E_13_B: return "E-13B";
            case ISO: return "ISO";
            case JIS_I: return "JIS-I";
            case JIS_II: return "JIS-II";
        }
        return null;
    }

    @JsonCreator
    public static TrackFormatEnum forValue(String value) throws IOException {
        if (value.equals("AAMVA")) return AAMVA;
        if (value.equals("CMC-7")) return CMC_7;
        if (value.equals("E-13B")) return E_13_B;
        if (value.equals("ISO")) return ISO;
        if (value.equals("JIS-I")) return JIS_I;
        if (value.equals("JIS-II")) return JIS_II;
        throw new IOException("Cannot deserialize TrackFormatEnum");
    }
}
