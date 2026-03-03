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
 * Typographic style of the text to display or print.
 */
public enum CharacterStyleEnum {
    BOLD, ITALIC, NORMAL, UNDERLINED;

    @JsonValue
    public String toValue() {
        switch (this) {
            case BOLD: return "Bold";
            case ITALIC: return "Italic";
            case NORMAL: return "Normal";
            case UNDERLINED: return "Underlined";
        }
        return null;
    }

    @JsonCreator
    public static CharacterStyleEnum forValue(String value) throws IOException {
        if (value.equals("Bold")) return BOLD;
        if (value.equals("Italic")) return ITALIC;
        if (value.equals("Normal")) return NORMAL;
        if (value.equals("Underlined")) return UNDERLINED;
        throw new IOException("Cannot deserialize CharacterStyleEnum");
    }
}
