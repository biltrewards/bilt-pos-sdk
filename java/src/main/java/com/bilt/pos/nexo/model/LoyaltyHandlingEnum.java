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
 * Type of loyalty processing requested by the Sale System: Forbidden (no loyalty),
 * Processed (already done), Allowed (optional), Proposed (POI asks customer), or Required
 * (mandatory).
 */
public enum LoyaltyHandlingEnum {
    ALLOWED, FORBIDDEN, PROCESSED, PROPOSED, REQUIRED;

    @JsonValue
    public String toValue() {
        switch (this) {
            case ALLOWED: return "Allowed";
            case FORBIDDEN: return "Forbidden";
            case PROCESSED: return "Processed";
            case PROPOSED: return "Proposed";
            case REQUIRED: return "Required";
        }
        return null;
    }

    @JsonCreator
    public static LoyaltyHandlingEnum forValue(String value) throws IOException {
        if (value.equals("Allowed")) return ALLOWED;
        if (value.equals("Forbidden")) return FORBIDDEN;
        if (value.equals("Processed")) return PROCESSED;
        if (value.equals("Proposed")) return PROPOSED;
        if (value.equals("Required")) return REQUIRED;
        throw new IOException("Cannot deserialize LoyaltyHandlingEnum");
    }
}
