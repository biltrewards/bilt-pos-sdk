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
 * Action to perform on a transaction via EnableService: StartTransaction (enable
 * swipe-ahead) or AbortTransaction (cancel a started swipe-ahead or CardAcquisition).
 */
public enum TransactionActionEnum {
    ABORT_TRANSACTION, START_TRANSACTION;

    @JsonValue
    public String toValue() {
        switch (this) {
            case ABORT_TRANSACTION: return "AbortTransaction";
            case START_TRANSACTION: return "StartTransaction";
        }
        return null;
    }

    @JsonCreator
    public static TransactionActionEnum forValue(String value) throws IOException {
        if (value.equals("AbortTransaction")) return ABORT_TRANSACTION;
        if (value.equals("StartTransaction")) return START_TRANSACTION;
        throw new IOException("Cannot deserialize TransactionActionEnum");
    }
}
