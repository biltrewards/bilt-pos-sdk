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
 * Format of the sound content: SoundRef (preloaded sound file), MessageRef (reference to a
 * preloaded text to play), or Text (text to synthesise).
 */
public enum SoundFormatEnum {
    MESSAGE_REF, SOUND_REF, TEXT;

    @JsonValue
    public String toValue() {
        switch (this) {
            case MESSAGE_REF: return "MessageRef";
            case SOUND_REF: return "SoundRef";
            case TEXT: return "Text";
        }
        return null;
    }

    @JsonCreator
    public static SoundFormatEnum forValue(String value) throws IOException {
        if (value.equals("MessageRef")) return MESSAGE_REF;
        if (value.equals("SoundRef")) return SOUND_REF;
        if (value.equals("Text")) return TEXT;
        throw new IOException("Cannot deserialize SoundFormatEnum");
    }
}
