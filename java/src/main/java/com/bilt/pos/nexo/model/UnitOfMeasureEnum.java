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
 * Unit of measure for a sale item quantity.
 */
public enum UnitOfMeasureEnum {
    CASE, CENTILITRE, CENTIMETRE, FOOT, GRAM, INCH, KILOGRAM, KILOMETRE, LITRE, METER, MILE, OTHER, OUNCE, PINT, POUND, QUART, UK_GALLON, US_GALLON, YARD;

    @JsonValue
    public String toValue() {
        switch (this) {
            case CASE: return "Case";
            case CENTILITRE: return "Centilitre";
            case CENTIMETRE: return "Centimetre";
            case FOOT: return "Foot";
            case GRAM: return "Gram";
            case INCH: return "Inch";
            case KILOGRAM: return "Kilogram";
            case KILOMETRE: return "Kilometre";
            case LITRE: return "Litre";
            case METER: return "Meter";
            case MILE: return "Mile";
            case OTHER: return "Other";
            case OUNCE: return "Ounce";
            case PINT: return "Pint";
            case POUND: return "Pound";
            case QUART: return "Quart";
            case UK_GALLON: return "UKGallon";
            case US_GALLON: return "USGallon";
            case YARD: return "Yard";
        }
        return null;
    }

    @JsonCreator
    public static UnitOfMeasureEnum forValue(String value) throws IOException {
        if (value.equals("Case")) return CASE;
        if (value.equals("Centilitre")) return CENTILITRE;
        if (value.equals("Centimetre")) return CENTIMETRE;
        if (value.equals("Foot")) return FOOT;
        if (value.equals("Gram")) return GRAM;
        if (value.equals("Inch")) return INCH;
        if (value.equals("Kilogram")) return KILOGRAM;
        if (value.equals("Kilometre")) return KILOMETRE;
        if (value.equals("Litre")) return LITRE;
        if (value.equals("Meter")) return METER;
        if (value.equals("Mile")) return MILE;
        if (value.equals("Other")) return OTHER;
        if (value.equals("Ounce")) return OUNCE;
        if (value.equals("Pint")) return PINT;
        if (value.equals("Pound")) return POUND;
        if (value.equals("Quart")) return QUART;
        if (value.equals("UKGallon")) return UK_GALLON;
        if (value.equals("USGallon")) return US_GALLON;
        if (value.equals("Yard")) return YARD;
        throw new IOException("Cannot deserialize UnitOfMeasureEnum");
    }
}
