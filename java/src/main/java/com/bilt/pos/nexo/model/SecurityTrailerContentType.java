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
 * Identifies the type of CMS protection applied to the content.
 */
public enum SecurityTrailerContentType {
    ID_CT_AUTH_DATA, ID_DATA, ID_DIGESTED_DATA, ID_ENVELOPED_DATA;

    @JsonValue
    public String toValue() {
        switch (this) {
            case ID_CT_AUTH_DATA: return "id-ct-authData";
            case ID_DATA: return "id-data";
            case ID_DIGESTED_DATA: return "id-digestedData";
            case ID_ENVELOPED_DATA: return "id-envelopedData";
        }
        return null;
    }

    @JsonCreator
    public static SecurityTrailerContentType forValue(String value) throws IOException {
        if (value.equals("id-ct-authData")) return ID_CT_AUTH_DATA;
        if (value.equals("id-data")) return ID_DATA;
        if (value.equals("id-digestedData")) return ID_DIGESTED_DATA;
        if (value.equals("id-envelopedData")) return ID_ENVELOPED_DATA;
        throw new IOException("Cannot deserialize SecurityTrailerContentType");
    }
}
