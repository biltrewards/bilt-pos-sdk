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
 * Type of payment transaction identifying the specific payment service requested.
 *
 * Type of payment service, mandatory for contactless card processing.
 */
public enum PaymentTypeEnum {
    CASH_ADVANCE, CASH_DEPOSIT, COMPLETION, FIRST_RESERVATION, INSTALMENT, ISSUER_INSTALMENT, NORMAL, ONE_TIME_RESERVATION, PAID_OUT, RECURRING, REFUND, UPDATE_RESERVATION;

    @JsonValue
    public String toValue() {
        switch (this) {
            case CASH_ADVANCE: return "CashAdvance";
            case CASH_DEPOSIT: return "CashDeposit";
            case COMPLETION: return "Completion";
            case FIRST_RESERVATION: return "FirstReservation";
            case INSTALMENT: return "Instalment";
            case ISSUER_INSTALMENT: return "IssuerInstalment";
            case NORMAL: return "Normal";
            case ONE_TIME_RESERVATION: return "OneTimeReservation";
            case PAID_OUT: return "PaidOut";
            case RECURRING: return "Recurring";
            case REFUND: return "Refund";
            case UPDATE_RESERVATION: return "UpdateReservation";
        }
        return null;
    }

    @JsonCreator
    public static PaymentTypeEnum forValue(String value) throws IOException {
        if (value.equals("CashAdvance")) return CASH_ADVANCE;
        if (value.equals("CashDeposit")) return CASH_DEPOSIT;
        if (value.equals("Completion")) return COMPLETION;
        if (value.equals("FirstReservation")) return FIRST_RESERVATION;
        if (value.equals("Instalment")) return INSTALMENT;
        if (value.equals("IssuerInstalment")) return ISSUER_INSTALMENT;
        if (value.equals("Normal")) return NORMAL;
        if (value.equals("OneTimeReservation")) return ONE_TIME_RESERVATION;
        if (value.equals("PaidOut")) return PAID_OUT;
        if (value.equals("Recurring")) return RECURRING;
        if (value.equals("Refund")) return REFUND;
        if (value.equals("UpdateReservation")) return UPDATE_RESERVATION;
        throw new IOException("Cannot deserialize PaymentTypeEnum");
    }
}
