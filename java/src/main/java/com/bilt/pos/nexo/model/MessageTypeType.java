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
 * Type of message: Request (requires a response), Response (answers a request), or
 * Notification (unsolicited, no response required).
 */
public enum MessageTypeType {
    NOTIFICATION, REQUEST, RESPONSE;

    @JsonValue
    public String toValue() {
        switch (this) {
            case NOTIFICATION: return "Notification";
            case REQUEST: return "Request";
            case RESPONSE: return "Response";
        }
        return null;
    }

    @JsonCreator
    public static MessageTypeType forValue(String value) throws IOException {
        if (value.equals("Notification")) return NOTIFICATION;
        if (value.equals("Request")) return REQUEST;
        if (value.equals("Response")) return RESPONSE;
        throw new IOException("Cannot deserialize MessageTypeType");
    }
}
