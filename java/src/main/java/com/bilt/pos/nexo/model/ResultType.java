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
 * Global result of the processing of a message request: Success, Failure, or Partial (e.g.
 * only partial amount authorised).
 */
public enum ResultType {
    FAILURE, PARTIAL, SUCCESS;

    @JsonValue
    public String toValue() {
        switch (this) {
            case FAILURE: return "Failure";
            case PARTIAL: return "Partial";
            case SUCCESS: return "Success";
        }
        return null;
    }

    @JsonCreator
    public static ResultType forValue(String value) throws IOException {
        if (value.equals("Failure")) return FAILURE;
        if (value.equals("Partial")) return PARTIAL;
        if (value.equals("Success")) return SUCCESS;
        throw new IOException("Cannot deserialize ResultType");
    }
}
