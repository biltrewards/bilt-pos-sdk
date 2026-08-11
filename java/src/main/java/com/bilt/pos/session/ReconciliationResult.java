/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session;

import com.bilt.pos.nexo.model.TransactionTotals;

import java.util.Collections;
import java.util.List;

/**
 * Outcome of {@link Terminal#reconcile()} and {@link Terminal#getTotals()}.
 */
public final class ReconciliationResult {

    private final String poiReconciliationId;
    private final List<TransactionTotals> transactionTotals;

    public ReconciliationResult(String poiReconciliationId,
                                List<TransactionTotals> transactionTotals) {
        this.poiReconciliationId = poiReconciliationId;
        this.transactionTotals = transactionTotals == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(transactionTotals);
    }

    /** Terminal-assigned identifier for this reconciliation period, or {@code null}. */
    public String getPoiReconciliationId() {
        return poiReconciliationId;
    }

    /** Totals per payment instrument / card brand; empty if not reported. */
    public List<TransactionTotals> getTransactionTotals() {
        return transactionTotals;
    }
}
