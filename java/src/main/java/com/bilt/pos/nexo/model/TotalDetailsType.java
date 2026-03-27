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
 * Indicates the criteria by which transaction totals should be broken down in a GetTotals
 * response. Each value in the cluster requests a separate grouping dimension.
 */
public enum TotalDetailsType {
    OPERATOR_ID, POIID, SALE_ID, SHIFT_NUMBER, TOTALS_GROUP_ID;

    @JsonValue
    public String toValue() {
        switch (this) {
            case OPERATOR_ID: return "OperatorID";
            case POIID: return "POIID";
            case SALE_ID: return "SaleID";
            case SHIFT_NUMBER: return "ShiftNumber";
            case TOTALS_GROUP_ID: return "TotalsGroupID";
        }
        return null;
    }

    @JsonCreator
    public static TotalDetailsType forValue(String value) throws IOException {
        if (value.equals("OperatorID")) return OPERATOR_ID;
        if (value.equals("POIID")) return POIID;
        if (value.equals("SaleID")) return SALE_ID;
        if (value.equals("ShiftNumber")) return SHIFT_NUMBER;
        if (value.equals("TotalsGroupID")) return TOTALS_GROUP_ID;
        throw new IOException("Cannot deserialize TotalDetailsType");
    }
}
