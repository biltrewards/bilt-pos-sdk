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
 * Financial services enabled for swipe-ahead. Mandatory when TransactionAction is
 * StartTransaction.
 *
 * Financial services enabled by an EnableService request, allowing the POI to start one of
 * these services via the swipe-ahead mechanism before the Sale System sends the
 * corresponding service request.
 */
public enum ServicesEnabledType {
    CARD_ACQUISITION, LOYALTY, PAYMENT;

    @JsonValue
    public String toValue() {
        switch (this) {
            case CARD_ACQUISITION: return "CardAcquisition";
            case LOYALTY: return "Loyalty";
            case PAYMENT: return "Payment";
        }
        return null;
    }

    @JsonCreator
    public static ServicesEnabledType forValue(String value) throws IOException {
        if (value.equals("CardAcquisition")) return CARD_ACQUISITION;
        if (value.equals("Loyalty")) return LOYALTY;
        if (value.equals("Payment")) return PAYMENT;
        throw new IOException("Cannot deserialize ServicesEnabledType");
    }
}
