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
 * Support medium of the loyalty account identification: NoCard (not on a card), LoyaltyCard
 * (dedicated loyalty card), HybridCard (combined payment/loyalty), or LinkedCard
 * (implicitly linked to payment card).
 */
public enum IdentificationSupportEnum {
    HYBRID_CARD, LINKED_CARD, LOYALTY_CARD, NO_CARD;

    @JsonValue
    public String toValue() {
        switch (this) {
            case HYBRID_CARD: return "HybridCard";
            case LINKED_CARD: return "LinkedCard";
            case LOYALTY_CARD: return "LoyaltyCard";
            case NO_CARD: return "NoCard";
        }
        return null;
    }

    @JsonCreator
    public static IdentificationSupportEnum forValue(String value) throws IOException {
        if (value.equals("HybridCard")) return HYBRID_CARD;
        if (value.equals("LinkedCard")) return LINKED_CARD;
        if (value.equals("LoyaltyCard")) return LOYALTY_CARD;
        if (value.equals("NoCard")) return NO_CARD;
        throw new IOException("Cannot deserialize IdentificationSupportEnum");
    }
}
