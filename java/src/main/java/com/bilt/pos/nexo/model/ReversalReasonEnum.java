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
 * Reason for reversing a payment or loyalty transaction.
 */
public enum ReversalReasonEnum {
    CUST_CANCEL, MALFUNCTION, MERCHANT_CANCEL, UNABLE2_COMPL;

    @JsonValue
    public String toValue() {
        switch (this) {
            case CUST_CANCEL: return "CustCancel";
            case MALFUNCTION: return "Malfunction";
            case MERCHANT_CANCEL: return "MerchantCancel";
            case UNABLE2_COMPL: return "Unable2Compl";
        }
        return null;
    }

    @JsonCreator
    public static ReversalReasonEnum forValue(String value) throws IOException {
        if (value.equals("CustCancel")) return CUST_CANCEL;
        if (value.equals("Malfunction")) return MALFUNCTION;
        if (value.equals("MerchantCancel")) return MERCHANT_CANCEL;
        if (value.equals("Unable2Compl")) return UNABLE2_COMPL;
        throw new IOException("Cannot deserialize ReversalReasonEnum");
    }
}
