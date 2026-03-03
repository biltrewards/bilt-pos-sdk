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
 * Content type, always 'id-data'.
 *
 * Content type of the encrypted data, always 'id-data'.
 */
public enum EncapsulatedContentContentType {
    ID_DATA;

    @JsonValue
    public String toValue() {
        switch (this) {
            case ID_DATA: return "id-data";
        }
        return null;
    }

    @JsonCreator
    public static EncapsulatedContentContentType forValue(String value) throws IOException {
        if (value.equals("id-data")) return ID_DATA;
        throw new IOException("Cannot deserialize EncapsulatedContentContentType");
    }
}
