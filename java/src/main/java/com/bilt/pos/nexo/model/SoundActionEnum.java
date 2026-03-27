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
 * Action to perform on sound: StartSound, StopSound, or SetDefaultVolume.
 */
public enum SoundActionEnum {
    SET_DEFAULT_VOLUME, START_SOUND, STOP_SOUND;

    @JsonValue
    public String toValue() {
        switch (this) {
            case SET_DEFAULT_VOLUME: return "SetDefaultVolume";
            case START_SOUND: return "StartSound";
            case STOP_SOUND: return "StopSound";
        }
        return null;
    }

    @JsonCreator
    public static SoundActionEnum forValue(String value) throws IOException {
        if (value.equals("SetDefaultVolume")) return SET_DEFAULT_VOLUME;
        if (value.equals("StartSound")) return START_SOUND;
        if (value.equals("StopSound")) return STOP_SOUND;
        throw new IOException("Cannot deserialize SoundActionEnum");
    }
}
