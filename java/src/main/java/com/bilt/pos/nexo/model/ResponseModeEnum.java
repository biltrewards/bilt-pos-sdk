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
 * When the initiator expects a response: NotRequired (no response needed), Immediate
 * (acknowledge receipt), PrintEnd (after printing complete), or SoundEnd (after sound
 * complete).
 */
public enum ResponseModeEnum {
    IMMEDIATE, NOT_REQUIRED, PRINT_END, SOUND_END;

    @JsonValue
    public String toValue() {
        switch (this) {
            case IMMEDIATE: return "Immediate";
            case NOT_REQUIRED: return "NotRequired";
            case PRINT_END: return "PrintEnd";
            case SOUND_END: return "SoundEnd";
        }
        return null;
    }

    @JsonCreator
    public static ResponseModeEnum forValue(String value) throws IOException {
        if (value.equals("Immediate")) return IMMEDIATE;
        if (value.equals("NotRequired")) return NOT_REQUIRED;
        if (value.equals("PrintEnd")) return PRINT_END;
        if (value.equals("SoundEnd")) return SOUND_END;
        throw new IOException("Cannot deserialize ResponseModeEnum");
    }
}
