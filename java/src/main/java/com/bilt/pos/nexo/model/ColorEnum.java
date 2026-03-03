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
 * Color of the text string to display or print.
 */
public enum ColorEnum {
    BLACK, BLUE, CYAN, GREEN, MAGENTA, RED, WHITE, YELLOW;

    @JsonValue
    public String toValue() {
        switch (this) {
            case BLACK: return "Black";
            case BLUE: return "Blue";
            case CYAN: return "Cyan";
            case GREEN: return "Green";
            case MAGENTA: return "Magenta";
            case RED: return "Red";
            case WHITE: return "White";
            case YELLOW: return "Yellow";
        }
        return null;
    }

    @JsonCreator
    public static ColorEnum forValue(String value) throws IOException {
        if (value.equals("Black")) return BLACK;
        if (value.equals("Blue")) return BLUE;
        if (value.equals("Cyan")) return CYAN;
        if (value.equals("Green")) return GREEN;
        if (value.equals("Magenta")) return MAGENTA;
        if (value.equals("Red")) return RED;
        if (value.equals("White")) return WHITE;
        if (value.equals("Yellow")) return YELLOW;
        throw new IOException("Cannot deserialize ColorEnum");
    }
}
