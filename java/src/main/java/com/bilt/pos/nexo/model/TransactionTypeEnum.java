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
 * Type of transaction for grouping totals in reconciliation or GetTotals responses.
 */
public enum TransactionTypeEnum {
    AWARD, CASH_ADVANCE, COMPLETED_DEFFERED, COMPLETED_RESERVATION, CREDIT, DEBIT, DECLINED, FAILED, FIRST_RESERVATION, ISSUER_INSTALMENT, ONE_TIME_RESERVATION, REBATE, REDEMPTION, REVERSE_AWARD, REVERSE_CREDIT, REVERSE_DEBIT, REVERSE_REBATE, REVERSE_REDEMPTION, UPDATE_RESERVATION;

    @JsonValue
    public String toValue() {
        switch (this) {
            case AWARD: return "Award";
            case CASH_ADVANCE: return "CashAdvance";
            case COMPLETED_DEFFERED: return "CompletedDeffered";
            case COMPLETED_RESERVATION: return "CompletedReservation";
            case CREDIT: return "Credit";
            case DEBIT: return "Debit";
            case DECLINED: return "Declined";
            case FAILED: return "Failed";
            case FIRST_RESERVATION: return "FirstReservation";
            case ISSUER_INSTALMENT: return "IssuerInstalment";
            case ONE_TIME_RESERVATION: return "OneTimeReservation";
            case REBATE: return "Rebate";
            case REDEMPTION: return "Redemption";
            case REVERSE_AWARD: return "ReverseAward";
            case REVERSE_CREDIT: return "ReverseCredit";
            case REVERSE_DEBIT: return "ReverseDebit";
            case REVERSE_REBATE: return "ReverseRebate";
            case REVERSE_REDEMPTION: return "ReverseRedemption";
            case UPDATE_RESERVATION: return "UpdateReservation";
        }
        return null;
    }

    @JsonCreator
    public static TransactionTypeEnum forValue(String value) throws IOException {
        if (value.equals("Award")) return AWARD;
        if (value.equals("CashAdvance")) return CASH_ADVANCE;
        if (value.equals("CompletedDeffered")) return COMPLETED_DEFFERED;
        if (value.equals("CompletedReservation")) return COMPLETED_RESERVATION;
        if (value.equals("Credit")) return CREDIT;
        if (value.equals("Debit")) return DEBIT;
        if (value.equals("Declined")) return DECLINED;
        if (value.equals("Failed")) return FAILED;
        if (value.equals("FirstReservation")) return FIRST_RESERVATION;
        if (value.equals("IssuerInstalment")) return ISSUER_INSTALMENT;
        if (value.equals("OneTimeReservation")) return ONE_TIME_RESERVATION;
        if (value.equals("Rebate")) return REBATE;
        if (value.equals("Redemption")) return REDEMPTION;
        if (value.equals("ReverseAward")) return REVERSE_AWARD;
        if (value.equals("ReverseCredit")) return REVERSE_CREDIT;
        if (value.equals("ReverseDebit")) return REVERSE_DEBIT;
        if (value.equals("ReverseRebate")) return REVERSE_REBATE;
        if (value.equals("ReverseRedemption")) return REVERSE_REDEMPTION;
        if (value.equals("UpdateReservation")) return UPDATE_RESERVATION;
        throw new IOException("Cannot deserialize TransactionTypeEnum");
    }
}
