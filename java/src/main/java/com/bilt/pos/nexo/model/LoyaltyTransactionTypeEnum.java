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
 * Type of loyalty transaction: award, rebate, redemption, or their respective refunds.
 */
public enum LoyaltyTransactionTypeEnum {
    AWARD, AWARD_REFUND, REBATE, REBATE_REFUND, REDEMPTION, REDEMPTION_REFUND;

    @JsonValue
    public String toValue() {
        switch (this) {
            case AWARD: return "Award";
            case AWARD_REFUND: return "AwardRefund";
            case REBATE: return "Rebate";
            case REBATE_REFUND: return "RebateRefund";
            case REDEMPTION: return "Redemption";
            case REDEMPTION_REFUND: return "RedemptionRefund";
        }
        return null;
    }

    @JsonCreator
    public static LoyaltyTransactionTypeEnum forValue(String value) throws IOException {
        if (value.equals("Award")) return AWARD;
        if (value.equals("AwardRefund")) return AWARD_REFUND;
        if (value.equals("Rebate")) return REBATE;
        if (value.equals("RebateRefund")) return REBATE_REFUND;
        if (value.equals("Redemption")) return REDEMPTION;
        if (value.equals("RedemptionRefund")) return REDEMPTION_REFUND;
        throw new IOException("Cannot deserialize LoyaltyTransactionTypeEnum");
    }
}
