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
 * Restricts which card reader entry modes the POI may use for this transaction. Useful to
 * avoid retry on a known out-of-order device. The order of values gives priority among
 * modes.
 */
public enum ForceEntryModeType {
    CHECK_READER, CONTACTLESS, FILE, ICC, KEYED, MAG_STRIPE, MANUAL, RFID, SCANNED, SYNCHRONOUS_ICC, TAPPED;

    @JsonValue
    public String toValue() {
        switch (this) {
            case CHECK_READER: return "CheckReader";
            case CONTACTLESS: return "Contactless";
            case FILE: return "File";
            case ICC: return "ICC";
            case KEYED: return "Keyed";
            case MAG_STRIPE: return "MagStripe";
            case MANUAL: return "Manual";
            case RFID: return "RFID";
            case SCANNED: return "Scanned";
            case SYNCHRONOUS_ICC: return "SynchronousICC";
            case TAPPED: return "Tapped";
        }
        return null;
    }

    @JsonCreator
    public static ForceEntryModeType forValue(String value) throws IOException {
        if (value.equals("CheckReader")) return CHECK_READER;
        if (value.equals("Contactless")) return CONTACTLESS;
        if (value.equals("File")) return FILE;
        if (value.equals("ICC")) return ICC;
        if (value.equals("Keyed")) return KEYED;
        if (value.equals("MagStripe")) return MAG_STRIPE;
        if (value.equals("Manual")) return MANUAL;
        if (value.equals("RFID")) return RFID;
        if (value.equals("Scanned")) return SCANNED;
        if (value.equals("SynchronousICC")) return SYNCHRONOUS_ICC;
        if (value.equals("Tapped")) return TAPPED;
        throw new IOException("Cannot deserialize ForceEntryModeType");
    }
}
