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
 * Operational status of the printer device.
 */
public enum PrinterStatusEnum {
    NO_PAPER, OK, OUT_OF_ORDER, PAPER_JAM, PAPER_LOW;

    @JsonValue
    public String toValue() {
        switch (this) {
            case NO_PAPER: return "NoPaper";
            case OK: return "OK";
            case OUT_OF_ORDER: return "OutOfOrder";
            case PAPER_JAM: return "PaperJam";
            case PAPER_LOW: return "PaperLow";
        }
        return null;
    }

    @JsonCreator
    public static PrinterStatusEnum forValue(String value) throws IOException {
        if (value.equals("NoPaper")) return NO_PAPER;
        if (value.equals("OK")) return OK;
        if (value.equals("OutOfOrder")) return OUT_OF_ORDER;
        if (value.equals("PaperJam")) return PAPER_JAM;
        if (value.equals("PaperLow")) return PAPER_LOW;
        throw new IOException("Cannot deserialize PrinterStatusEnum");
    }
}
