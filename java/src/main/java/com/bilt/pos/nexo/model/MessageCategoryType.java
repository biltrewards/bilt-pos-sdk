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
 * Category of message identifying the type of service or operation being requested or
 * responded to.
 */
public enum MessageCategoryType {
    ABORT, ADMIN, BALANCE_INQUIRY, BATCH, CARD_ACQUISITION, CARD_READER_APDU, CARD_READER_INIT, CARD_READER_POWER_OFF, DIAGNOSIS, DISPLAY, ENABLE_SERVICE, EVENT, GET_TOTALS, INPUT, INPUT_UPDATE, LOGIN, LOGOUT, LOYALTY, PAYMENT, PIN, PRINT, RECONCILIATION, REVERSAL, SOUND, STORED_VALUE, TRANSACTION_STATUS, TRANSMIT;

    @JsonValue
    public String toValue() {
        switch (this) {
            case ABORT: return "Abort";
            case ADMIN: return "Admin";
            case BALANCE_INQUIRY: return "BalanceInquiry";
            case BATCH: return "Batch";
            case CARD_ACQUISITION: return "CardAcquisition";
            case CARD_READER_APDU: return "CardReaderAPDU";
            case CARD_READER_INIT: return "CardReaderInit";
            case CARD_READER_POWER_OFF: return "CardReaderPowerOff";
            case DIAGNOSIS: return "Diagnosis";
            case DISPLAY: return "Display";
            case ENABLE_SERVICE: return "EnableService";
            case EVENT: return "Event";
            case GET_TOTALS: return "GetTotals";
            case INPUT: return "Input";
            case INPUT_UPDATE: return "InputUpdate";
            case LOGIN: return "Login";
            case LOGOUT: return "Logout";
            case LOYALTY: return "Loyalty";
            case PAYMENT: return "Payment";
            case PIN: return "PIN";
            case PRINT: return "Print";
            case RECONCILIATION: return "Reconciliation";
            case REVERSAL: return "Reversal";
            case SOUND: return "Sound";
            case STORED_VALUE: return "StoredValue";
            case TRANSACTION_STATUS: return "TransactionStatus";
            case TRANSMIT: return "Transmit";
        }
        return null;
    }

    @JsonCreator
    public static MessageCategoryType forValue(String value) throws IOException {
        if (value.equals("Abort")) return ABORT;
        if (value.equals("Admin")) return ADMIN;
        if (value.equals("BalanceInquiry")) return BALANCE_INQUIRY;
        if (value.equals("Batch")) return BATCH;
        if (value.equals("CardAcquisition")) return CARD_ACQUISITION;
        if (value.equals("CardReaderAPDU")) return CARD_READER_APDU;
        if (value.equals("CardReaderInit")) return CARD_READER_INIT;
        if (value.equals("CardReaderPowerOff")) return CARD_READER_POWER_OFF;
        if (value.equals("Diagnosis")) return DIAGNOSIS;
        if (value.equals("Display")) return DISPLAY;
        if (value.equals("EnableService")) return ENABLE_SERVICE;
        if (value.equals("Event")) return EVENT;
        if (value.equals("GetTotals")) return GET_TOTALS;
        if (value.equals("Input")) return INPUT;
        if (value.equals("InputUpdate")) return INPUT_UPDATE;
        if (value.equals("Login")) return LOGIN;
        if (value.equals("Logout")) return LOGOUT;
        if (value.equals("Loyalty")) return LOYALTY;
        if (value.equals("Payment")) return PAYMENT;
        if (value.equals("PIN")) return PIN;
        if (value.equals("Print")) return PRINT;
        if (value.equals("Reconciliation")) return RECONCILIATION;
        if (value.equals("Reversal")) return REVERSAL;
        if (value.equals("Sound")) return SOUND;
        if (value.equals("StoredValue")) return STORED_VALUE;
        if (value.equals("TransactionStatus")) return TRANSACTION_STATUS;
        if (value.equals("Transmit")) return TRANSMIT;
        throw new IOException("Cannot deserialize MessageCategoryType");
    }
}
