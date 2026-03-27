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
 * Environment of the terminal: Attended (cashier present), SemiAttended (customer
 * self-service with optional cashier assistance), or Unattended (fully automated).
 */
public enum TerminalEnvironmentType {
    ATTENDED, SEMI_ATTENDED, UNATTENDED;

    @JsonValue
    public String toValue() {
        switch (this) {
            case ATTENDED: return "Attended";
            case SEMI_ATTENDED: return "SemiAttended";
            case UNATTENDED: return "Unattended";
        }
        return null;
    }

    @JsonCreator
    public static TerminalEnvironmentType forValue(String value) throws IOException {
        if (value.equals("Attended")) return ATTENDED;
        if (value.equals("SemiAttended")) return SEMI_ATTENDED;
        if (value.equals("Unattended")) return UNATTENDED;
        throw new IOException("Cannot deserialize TerminalEnvironmentType");
    }
}
