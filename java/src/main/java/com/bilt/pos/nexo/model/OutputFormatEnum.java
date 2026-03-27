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
 * Format of the content to display or print: MessageRef (predefined message), Text
 * (formatted text), XHTML (XHTML document), or BarCode.
 */
public enum OutputFormatEnum {
    BAR_CODE, MESSAGE_REF, TEXT, XHTML;

    @JsonValue
    public String toValue() {
        switch (this) {
            case BAR_CODE: return "BarCode";
            case MESSAGE_REF: return "MessageRef";
            case TEXT: return "Text";
            case XHTML: return "XHTML";
        }
        return null;
    }

    @JsonCreator
    public static OutputFormatEnum forValue(String value) throws IOException {
        if (value.equals("BarCode")) return BAR_CODE;
        if (value.equals("MessageRef")) return MESSAGE_REF;
        if (value.equals("Text")) return TEXT;
        if (value.equals("XHTML")) return XHTML;
        throw new IOException("Cannot deserialize OutputFormatEnum");
    }
}
