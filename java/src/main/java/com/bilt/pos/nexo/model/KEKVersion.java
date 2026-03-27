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
 * Version of the KEK structure, always 'v4'.
 */
public enum KEKVersion {
    V4;

    @JsonValue
    public String toValue() {
        switch (this) {
            case V4: return "v4";
        }
        return null;
    }

    @JsonCreator
    public static KEKVersion forValue(String value) throws IOException {
        if (value.equals("v4")) return V4;
        throw new IOException("Cannot deserialize KEKVersion");
    }
}
