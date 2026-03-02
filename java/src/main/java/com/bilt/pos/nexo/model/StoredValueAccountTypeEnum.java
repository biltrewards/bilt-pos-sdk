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
 * Type of stored value account instrument: GiftCard, PhoneCard, or Other.
 */
public enum StoredValueAccountTypeEnum {
    GIFT_CARD, OTHER, PHONE_CARD;

    @JsonValue
    public String toValue() {
        switch (this) {
            case GIFT_CARD: return "GiftCard";
            case OTHER: return "Other";
            case PHONE_CARD: return "PhoneCard";
        }
        return null;
    }

    @JsonCreator
    public static StoredValueAccountTypeEnum forValue(String value) throws IOException {
        if (value.equals("GiftCard")) return GIFT_CARD;
        if (value.equals("Other")) return OTHER;
        if (value.equals("PhoneCard")) return PHONE_CARD;
        throw new IOException("Cannot deserialize StoredValueAccountTypeEnum");
    }
}
