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
 * Type of input requested from the user: GetAnyKey (read confirmation), GetConfirmation
 * (yes/no), SiteManager (site manager confirmation), TextString, DigitString,
 * DecimalString, GetFunctionKey, GetMenuEntry, or Password.
 */
public enum InputCommandEnum {
    DECIMAL_STRING, DIGIT_STRING, GET_ANY_KEY, GET_CONFIRMATION, GET_FUNCTION_KEY, GET_MENU_ENTRY, PASSWORD, SITE_MANAGER, TEXT_STRING;

    @JsonValue
    public String toValue() {
        switch (this) {
            case DECIMAL_STRING: return "DecimalString";
            case DIGIT_STRING: return "DigitString";
            case GET_ANY_KEY: return "GetAnyKey";
            case GET_CONFIRMATION: return "GetConfirmation";
            case GET_FUNCTION_KEY: return "GetFunctionKey";
            case GET_MENU_ENTRY: return "GetMenuEntry";
            case PASSWORD: return "Password";
            case SITE_MANAGER: return "SiteManager";
            case TEXT_STRING: return "TextString";
        }
        return null;
    }

    @JsonCreator
    public static InputCommandEnum forValue(String value) throws IOException {
        if (value.equals("DecimalString")) return DECIMAL_STRING;
        if (value.equals("DigitString")) return DIGIT_STRING;
        if (value.equals("GetAnyKey")) return GET_ANY_KEY;
        if (value.equals("GetConfirmation")) return GET_CONFIRMATION;
        if (value.equals("GetFunctionKey")) return GET_FUNCTION_KEY;
        if (value.equals("GetMenuEntry")) return GET_MENU_ENTRY;
        if (value.equals("Password")) return PASSWORD;
        if (value.equals("SiteManager")) return SITE_MANAGER;
        if (value.equals("TextString")) return TEXT_STRING;
        throw new IOException("Cannot deserialize InputCommandEnum");
    }
}
