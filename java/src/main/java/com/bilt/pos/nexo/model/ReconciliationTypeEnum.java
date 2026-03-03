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
 * Type of reconciliation: SaleReconciliation (close current period without acquirer sync),
 * AcquirerSynchronisation (close with acquirer sync), AcquirerReconciliation (acquirer
 * only), or PreviousReconciliation (result of a previous period).
 */
public enum ReconciliationTypeEnum {
    ACQUIRER_RECONCILIATION, ACQUIRER_SYNCHRONISATION, PREVIOUS_RECONCILIATION, SALE_RECONCILIATION;

    @JsonValue
    public String toValue() {
        switch (this) {
            case ACQUIRER_RECONCILIATION: return "AcquirerReconciliation";
            case ACQUIRER_SYNCHRONISATION: return "AcquirerSynchronisation";
            case PREVIOUS_RECONCILIATION: return "PreviousReconciliation";
            case SALE_RECONCILIATION: return "SaleReconciliation";
        }
        return null;
    }

    @JsonCreator
    public static ReconciliationTypeEnum forValue(String value) throws IOException {
        if (value.equals("AcquirerReconciliation")) return ACQUIRER_RECONCILIATION;
        if (value.equals("AcquirerSynchronisation")) return ACQUIRER_SYNCHRONISATION;
        if (value.equals("PreviousReconciliation")) return PREVIOUS_RECONCILIATION;
        if (value.equals("SaleReconciliation")) return SALE_RECONCILIATION;
        throw new IOException("Cannot deserialize ReconciliationTypeEnum");
    }
}
