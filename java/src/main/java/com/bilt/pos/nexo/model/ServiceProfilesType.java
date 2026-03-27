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
 * List of optional service profiles supported by the Sale or POI Terminal. Sent in Login
 * Request/Response to declare which additional services may be requested or provided during
 * the session.
 */
public enum ServiceProfilesType {
    BATCH, CARD_READER, COMMUNICATION, LOYALTY, ONE_TIME_RES, PIN, RESERVATION, SOUND, STORED_VALUE, SYNCHRO;

    @JsonValue
    public String toValue() {
        switch (this) {
            case BATCH: return "Batch";
            case CARD_READER: return "CardReader";
            case COMMUNICATION: return "Communication";
            case LOYALTY: return "Loyalty";
            case ONE_TIME_RES: return "OneTimeRes";
            case PIN: return "PIN";
            case RESERVATION: return "Reservation";
            case SOUND: return "Sound";
            case STORED_VALUE: return "StoredValue";
            case SYNCHRO: return "Synchro";
        }
        return null;
    }

    @JsonCreator
    public static ServiceProfilesType forValue(String value) throws IOException {
        if (value.equals("Batch")) return BATCH;
        if (value.equals("CardReader")) return CARD_READER;
        if (value.equals("Communication")) return COMMUNICATION;
        if (value.equals("Loyalty")) return LOYALTY;
        if (value.equals("OneTimeRes")) return ONE_TIME_RES;
        if (value.equals("PIN")) return PIN;
        if (value.equals("Reservation")) return RESERVATION;
        if (value.equals("Sound")) return SOUND;
        if (value.equals("StoredValue")) return STORED_VALUE;
        if (value.equals("Synchro")) return SYNCHRO;
        throw new IOException("Cannot deserialize ServiceProfilesType");
    }
}
