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
 * Character width of the text string to display or print.
 */
public enum CharacterWidthEnum {
    DOUBLE_WIDTH, SINGLE_WIDTH;

    @JsonValue
    public String toValue() {
        switch (this) {
            case DOUBLE_WIDTH: return "DoubleWidth";
            case SINGLE_WIDTH: return "SingleWidth";
        }
        return null;
    }

    @JsonCreator
    public static CharacterWidthEnum forValue(String value) throws IOException {
        if (value.equals("DoubleWidth")) return DOUBLE_WIDTH;
        if (value.equals("SingleWidth")) return SINGLE_WIDTH;
        throw new IOException("Cannot deserialize CharacterWidthEnum");
    }
}
