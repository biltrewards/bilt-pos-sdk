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
 * Type of cardholder account to use for a balance inquiry transaction.
 */
public enum AccountTypeEnum {
    CARD_TOTALS, CHECKING, CREDIT_CARD, DEFAULT, EPURSE_CARD, INVESTMENT, SAVINGS, UNIVERSAL;

    @JsonValue
    public String toValue() {
        switch (this) {
            case CARD_TOTALS: return "CardTotals";
            case CHECKING: return "Checking";
            case CREDIT_CARD: return "CreditCard";
            case DEFAULT: return "Default";
            case EPURSE_CARD: return "EpurseCard";
            case INVESTMENT: return "Investment";
            case SAVINGS: return "Savings";
            case UNIVERSAL: return "Universal";
        }
        return null;
    }

    @JsonCreator
    public static AccountTypeEnum forValue(String value) throws IOException {
        if (value.equals("CardTotals")) return CARD_TOTALS;
        if (value.equals("Checking")) return CHECKING;
        if (value.equals("CreditCard")) return CREDIT_CARD;
        if (value.equals("Default")) return DEFAULT;
        if (value.equals("EpurseCard")) return EPURSE_CARD;
        if (value.equals("Investment")) return INVESTMENT;
        if (value.equals("Savings")) return SAVINGS;
        if (value.equals("Universal")) return UNIVERSAL;
        throw new IOException("Cannot deserialize AccountTypeEnum");
    }
}
