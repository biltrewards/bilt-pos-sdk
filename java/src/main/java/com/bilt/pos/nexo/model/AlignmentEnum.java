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
 * Alignment of the text string on the display or print line.
 */
public enum AlignmentEnum {
    CENTRED, JUSTIFIED, LEFT, RIGHT;

    @JsonValue
    public String toValue() {
        switch (this) {
            case CENTRED: return "Centred";
            case JUSTIFIED: return "Justified";
            case LEFT: return "Left";
            case RIGHT: return "Right";
        }
        return null;
    }

    @JsonCreator
    public static AlignmentEnum forValue(String value) throws IOException {
        if (value.equals("Centred")) return CENTRED;
        if (value.equals("Justified")) return JUSTIFIED;
        if (value.equals("Left")) return LEFT;
        if (value.equals("Right")) return RIGHT;
        throw new IOException("Cannot deserialize AlignmentEnum");
    }
}
