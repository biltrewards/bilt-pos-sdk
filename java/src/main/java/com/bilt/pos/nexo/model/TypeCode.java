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
 * Type of bank check. Default Personal.
 */
public enum TypeCode {
    COMPANY, PERSONAL;

    @JsonValue
    public String toValue() {
        switch (this) {
            case COMPANY: return "Company";
            case PERSONAL: return "Personal";
        }
        return null;
    }

    @JsonCreator
    public static TypeCode forValue(String value) throws IOException {
        if (value.equals("Company")) return COMPANY;
        if (value.equals("Personal")) return PERSONAL;
        throw new IOException("Cannot deserialize TypeCode");
    }
}
