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
 * Character height of the text string to display or print.
 */
public enum CharacterHeightEnum {
    DOUBLE_HEIGHT, HALF_HEIGHT, SINGLE_HEIGHT;

    @JsonValue
    public String toValue() {
        switch (this) {
            case DOUBLE_HEIGHT: return "DoubleHeight";
            case HALF_HEIGHT: return "HalfHeight";
            case SINGLE_HEIGHT: return "SingleHeight";
        }
        return null;
    }

    @JsonCreator
    public static CharacterHeightEnum forValue(String value) throws IOException {
        if (value.equals("DoubleHeight")) return DOUBLE_HEIGHT;
        if (value.equals("HalfHeight")) return HALF_HEIGHT;
        if (value.equals("SingleHeight")) return SINGLE_HEIGHT;
        throw new IOException("Cannot deserialize CharacterHeightEnum");
    }
}
