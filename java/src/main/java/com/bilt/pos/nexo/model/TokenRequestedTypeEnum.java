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
 * Type of payment token requested to replace the PAN: Transaction (valid for one
 * transaction) or Customer (valid for a longer period to identify the customer).
 */
public enum TokenRequestedTypeEnum {
    CUSTOMER, TRANSACTION;

    @JsonValue
    public String toValue() {
        switch (this) {
            case CUSTOMER: return "Customer";
            case TRANSACTION: return "Transaction";
        }
        return null;
    }

    @JsonCreator
    public static TokenRequestedTypeEnum forValue(String value) throws IOException {
        if (value.equals("Customer")) return CUSTOMER;
        if (value.equals("Transaction")) return TRANSACTION;
        throw new IOException("Cannot deserialize TokenRequestedTypeEnum");
    }
}
