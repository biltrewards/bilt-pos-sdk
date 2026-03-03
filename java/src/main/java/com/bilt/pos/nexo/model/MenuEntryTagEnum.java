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
 * Characteristics of this menu entry (selectable, non-selectable, sub-menu). Default
 * Selectable.
 *
 * Characteristics of a menu entry: Selectable, NonSelectable, SubMenu (selection shows
 * sub-menu), or NonSelectableSubMenu.
 */
public enum MenuEntryTagEnum {
    NON_SELECTABLE, NON_SELECTABLE_SUB_MENU, SELECTABLE, SUB_MENU;

    @JsonValue
    public String toValue() {
        switch (this) {
            case NON_SELECTABLE: return "NonSelectable";
            case NON_SELECTABLE_SUB_MENU: return "NonSelectableSubMenu";
            case SELECTABLE: return "Selectable";
            case SUB_MENU: return "SubMenu";
        }
        return null;
    }

    @JsonCreator
    public static MenuEntryTagEnum forValue(String value) throws IOException {
        if (value.equals("NonSelectable")) return NON_SELECTABLE;
        if (value.equals("NonSelectableSubMenu")) return NON_SELECTABLE_SUB_MENU;
        if (value.equals("Selectable")) return SELECTABLE;
        if (value.equals("SubMenu")) return SUB_MENU;
        throw new IOException("Cannot deserialize MenuEntryTagEnum");
    }
}
