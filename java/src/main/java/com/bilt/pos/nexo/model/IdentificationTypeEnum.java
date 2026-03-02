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
 * Type of account identification used for loyalty or stored value: PAN, ISOTrack2, BarCode,
 * AccountNumber, or PhoneNumber.
 */
public enum IdentificationTypeEnum {
    ACCOUNT_NUMBER, BAR_CODE, ISO_TRACK2, PAN, PHONE_NUMBER;

    @JsonValue
    public String toValue() {
        switch (this) {
            case ACCOUNT_NUMBER: return "AccountNumber";
            case BAR_CODE: return "BarCode";
            case ISO_TRACK2: return "ISOTrack2";
            case PAN: return "PAN";
            case PHONE_NUMBER: return "PhoneNumber";
        }
        return null;
    }

    @JsonCreator
    public static IdentificationTypeEnum forValue(String value) throws IOException {
        if (value.equals("AccountNumber")) return ACCOUNT_NUMBER;
        if (value.equals("BarCode")) return BAR_CODE;
        if (value.equals("ISOTrack2")) return ISO_TRACK2;
        if (value.equals("PAN")) return PAN;
        if (value.equals("PhoneNumber")) return PHONE_NUMBER;
        throw new IOException("Cannot deserialize IdentificationTypeEnum");
    }
}
