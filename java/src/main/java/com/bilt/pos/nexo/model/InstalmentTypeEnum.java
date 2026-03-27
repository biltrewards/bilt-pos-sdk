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
 * Type of instalment payment plan: DeferredInstalments, EqualInstalments, or
 * InequalInstalments.
 */
public enum InstalmentTypeEnum {
    DEFERRED_INSTALMENTS, EQUAL_INSTALMENTS, INEQUAL_INSTALMENTS;

    @JsonValue
    public String toValue() {
        switch (this) {
            case DEFERRED_INSTALMENTS: return "DeferredInstalments";
            case EQUAL_INSTALMENTS: return "EqualInstalments";
            case INEQUAL_INSTALMENTS: return "InequalInstalments";
        }
        return null;
    }

    @JsonCreator
    public static InstalmentTypeEnum forValue(String value) throws IOException {
        if (value.equals("DeferredInstalments")) return DEFERRED_INSTALMENTS;
        if (value.equals("EqualInstalments")) return EQUAL_INSTALMENTS;
        if (value.equals("InequalInstalments")) return INEQUAL_INSTALMENTS;
        throw new IOException("Cannot deserialize InstalmentTypeEnum");
    }
}
