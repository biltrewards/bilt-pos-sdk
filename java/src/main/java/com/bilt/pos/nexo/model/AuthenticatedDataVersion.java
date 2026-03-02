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
 * Version of the AuthenticatedData structure, always 'v0'.
 *
 * Version of the EnvelopedData structure, always 'v0'.
 */
public enum AuthenticatedDataVersion {
    V0;

    @JsonValue
    public String toValue() {
        switch (this) {
            case V0: return "v0";
        }
        return null;
    }

    @JsonCreator
    public static AuthenticatedDataVersion forValue(String value) throws IOException {
        if (value.equals("v0")) return V0;
        throw new IOException("Cannot deserialize AuthenticatedDataVersion");
    }
}
