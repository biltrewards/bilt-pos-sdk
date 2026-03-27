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
 * Class of message: Service (transaction request/response initiated by Sale), Device
 * (device operation), or Event (unsolicited notification from POI).
 */
public enum MessageClassType {
    DEVICE, EVENT, SERVICE;

    @JsonValue
    public String toValue() {
        switch (this) {
            case DEVICE: return "Device";
            case EVENT: return "Event";
            case SERVICE: return "Service";
        }
        return null;
    }

    @JsonCreator
    public static MessageClassType forValue(String value) throws IOException {
        if (value.equals("Device")) return DEVICE;
        if (value.equals("Event")) return EVENT;
        if (value.equals("Service")) return SERVICE;
        throw new IOException("Cannot deserialize MessageClassType");
    }
}
