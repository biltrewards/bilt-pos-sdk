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
 * Type of unsolicited event the POI notifies to the Sale System.
 */
public enum EventToNotifyEnum {
    ABORT, BEGIN_MAINTENANCE, CARD_INSERTED, CARD_REMOVED, COMPLETED, CUSTOMER_LANGUAGE, END_MAINTENANCE, INITIALISED, KEY_PRESSED, OUT_OF_ORDER, REJECT, SALE_ADMIN, SALE_WAKE_UP, SECURITY_ALARM, SHUTDOWN, STOP_ASSISTANCE;

    @JsonValue
    public String toValue() {
        switch (this) {
            case ABORT: return "Abort";
            case BEGIN_MAINTENANCE: return "BeginMaintenance";
            case CARD_INSERTED: return "CardInserted";
            case CARD_REMOVED: return "CardRemoved";
            case COMPLETED: return "Completed";
            case CUSTOMER_LANGUAGE: return "CustomerLanguage";
            case END_MAINTENANCE: return "EndMaintenance";
            case INITIALISED: return "Initialised";
            case KEY_PRESSED: return "KeyPressed";
            case OUT_OF_ORDER: return "OutOfOrder";
            case REJECT: return "Reject";
            case SALE_ADMIN: return "SaleAdmin";
            case SALE_WAKE_UP: return "SaleWakeUp";
            case SECURITY_ALARM: return "SecurityAlarm";
            case SHUTDOWN: return "Shutdown";
            case STOP_ASSISTANCE: return "StopAssistance";
        }
        return null;
    }

    @JsonCreator
    public static EventToNotifyEnum forValue(String value) throws IOException {
        if (value.equals("Abort")) return ABORT;
        if (value.equals("BeginMaintenance")) return BEGIN_MAINTENANCE;
        if (value.equals("CardInserted")) return CARD_INSERTED;
        if (value.equals("CardRemoved")) return CARD_REMOVED;
        if (value.equals("Completed")) return COMPLETED;
        if (value.equals("CustomerLanguage")) return CUSTOMER_LANGUAGE;
        if (value.equals("EndMaintenance")) return END_MAINTENANCE;
        if (value.equals("Initialised")) return INITIALISED;
        if (value.equals("KeyPressed")) return KEY_PRESSED;
        if (value.equals("OutOfOrder")) return OUT_OF_ORDER;
        if (value.equals("Reject")) return REJECT;
        if (value.equals("SaleAdmin")) return SALE_ADMIN;
        if (value.equals("SaleWakeUp")) return SALE_WAKE_UP;
        if (value.equals("SecurityAlarm")) return SECURITY_ALARM;
        if (value.equals("Shutdown")) return SHUTDOWN;
        if (value.equals("StopAssistance")) return STOP_ASSISTANCE;
        throw new IOException("Cannot deserialize EventToNotifyEnum");
    }
}
