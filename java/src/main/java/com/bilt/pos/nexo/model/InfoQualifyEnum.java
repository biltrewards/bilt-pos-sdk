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
 * Qualification of information sent to an output device: Status (state change), Error
 * (error situation), Display (standard display), Sound, Input (entry requested),
 * POIReplication (mirror of POI customer display), CustomerAssistance (cashier assisting
 * customer), Receipt, Document, or Voucher.
 */
public enum InfoQualifyEnum {
    CUSTOMER_ASSISTANCE, DISPLAY, DOCUMENT, ERROR, INPUT, POI_REPLICATION, RECEIPT, SOUND, STATUS, VOUCHER;

    @JsonValue
    public String toValue() {
        switch (this) {
            case CUSTOMER_ASSISTANCE: return "CustomerAssistance";
            case DISPLAY: return "Display";
            case DOCUMENT: return "Document";
            case ERROR: return "Error";
            case INPUT: return "Input";
            case POI_REPLICATION: return "POIReplication";
            case RECEIPT: return "Receipt";
            case SOUND: return "Sound";
            case STATUS: return "Status";
            case VOUCHER: return "Voucher";
        }
        return null;
    }

    @JsonCreator
    public static InfoQualifyEnum forValue(String value) throws IOException {
        if (value.equals("CustomerAssistance")) return CUSTOMER_ASSISTANCE;
        if (value.equals("Display")) return DISPLAY;
        if (value.equals("Document")) return DOCUMENT;
        if (value.equals("Error")) return ERROR;
        if (value.equals("Input")) return INPUT;
        if (value.equals("POIReplication")) return POI_REPLICATION;
        if (value.equals("Receipt")) return RECEIPT;
        if (value.equals("Sound")) return SOUND;
        if (value.equals("Status")) return STATUS;
        if (value.equals("Voucher")) return VOUCHER;
        throw new IOException("Cannot deserialize InfoQualifyEnum");
    }
}
