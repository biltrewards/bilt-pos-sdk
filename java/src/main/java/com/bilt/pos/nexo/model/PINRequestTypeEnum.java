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
 * Type of PIN service requested: PINVerify (enter and verify), PINVerifyOnly (verify a
 * pre-entered PIN block), or PINEnter (enter and encrypt only).
 */
public enum PINRequestTypeEnum {
    PIN_ENTER, PIN_VERIFY, PIN_VERIFY_ONLY;

    @JsonValue
    public String toValue() {
        switch (this) {
            case PIN_ENTER: return "PINEnter";
            case PIN_VERIFY: return "PINVerify";
            case PIN_VERIFY_ONLY: return "PINVerifyOnly";
        }
        return null;
    }

    @JsonCreator
    public static PINRequestTypeEnum forValue(String value) throws IOException {
        if (value.equals("PINEnter")) return PIN_ENTER;
        if (value.equals("PINVerify")) return PIN_VERIFY;
        if (value.equals("PINVerifyOnly")) return PIN_VERIFY_ONLY;
        throw new IOException("Cannot deserialize PINRequestTypeEnum");
    }
}
