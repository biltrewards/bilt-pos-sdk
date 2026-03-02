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
 * Specifies which customer orders the POI should include in a response: Open orders, Closed
 * orders, or Both.
 */
public enum CustomerOrderReqType {
    BOTH, CLOSED, OPEN;

    @JsonValue
    public String toValue() {
        switch (this) {
            case BOTH: return "Both";
            case CLOSED: return "Closed";
            case OPEN: return "Open";
        }
        return null;
    }

    @JsonCreator
    public static CustomerOrderReqType forValue(String value) throws IOException {
        if (value.equals("Both")) return BOTH;
        if (value.equals("Closed")) return CLOSED;
        if (value.equals("Open")) return OPEN;
        throw new IOException("Cannot deserialize CustomerOrderReqType");
    }
}
