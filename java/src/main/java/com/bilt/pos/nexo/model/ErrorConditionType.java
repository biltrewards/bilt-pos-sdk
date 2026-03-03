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
 * Condition that produced the failure. Mandatory when Result is Failure.
 *
 * Condition that produced a failure, allowing the requestor to determine the appropriate
 * resolution action.
 *
 * Error condition for this Acquirer's reconciliation when Result is Partial.
 */
public enum ErrorConditionType {
    ABORTED, BUSY, CANCEL, DEVICE_OUT, INSERTED_CARD, INVALID_CARD, IN_PROGRESS, LOGGED_OUT, MESSAGE_FORMAT, NOT_ALLOWED, NOT_FOUND, PAYMENT_RESTRICTION, REFUSAL, UNAVAILABLE_DEVICE, UNAVAILABLE_SERVICE, UNREACHABLE_HOST, WRONG_PIN;

    @JsonValue
    public String toValue() {
        switch (this) {
            case ABORTED: return "Aborted";
            case BUSY: return "Busy";
            case CANCEL: return "Cancel";
            case DEVICE_OUT: return "DeviceOut";
            case INSERTED_CARD: return "InsertedCard";
            case INVALID_CARD: return "InvalidCard";
            case IN_PROGRESS: return "InProgress";
            case LOGGED_OUT: return "LoggedOut";
            case MESSAGE_FORMAT: return "MessageFormat";
            case NOT_ALLOWED: return "NotAllowed";
            case NOT_FOUND: return "NotFound";
            case PAYMENT_RESTRICTION: return "PaymentRestriction";
            case REFUSAL: return "Refusal";
            case UNAVAILABLE_DEVICE: return "UnavailableDevice";
            case UNAVAILABLE_SERVICE: return "UnavailableService";
            case UNREACHABLE_HOST: return "UnreachableHost";
            case WRONG_PIN: return "WrongPIN";
        }
        return null;
    }

    @JsonCreator
    public static ErrorConditionType forValue(String value) throws IOException {
        if (value.equals("Aborted")) return ABORTED;
        if (value.equals("Busy")) return BUSY;
        if (value.equals("Cancel")) return CANCEL;
        if (value.equals("DeviceOut")) return DEVICE_OUT;
        if (value.equals("InsertedCard")) return INSERTED_CARD;
        if (value.equals("InvalidCard")) return INVALID_CARD;
        if (value.equals("InProgress")) return IN_PROGRESS;
        if (value.equals("LoggedOut")) return LOGGED_OUT;
        if (value.equals("MessageFormat")) return MESSAGE_FORMAT;
        if (value.equals("NotAllowed")) return NOT_ALLOWED;
        if (value.equals("NotFound")) return NOT_FOUND;
        if (value.equals("PaymentRestriction")) return PAYMENT_RESTRICTION;
        if (value.equals("Refusal")) return REFUSAL;
        if (value.equals("UnavailableDevice")) return UNAVAILABLE_DEVICE;
        if (value.equals("UnavailableService")) return UNAVAILABLE_SERVICE;
        if (value.equals("UnreachableHost")) return UNREACHABLE_HOST;
        if (value.equals("WrongPIN")) return WRONG_PIN;
        throw new IOException("Cannot deserialize ErrorConditionType");
    }
}
