/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.settlement;

import java.time.Instant;

/** A settlement step that has been committed on the terminal. */
public final class CommittedStep {

    private final SettlementStep step;
    private final String saleTransactionId;
    private final String poiTransactionId;
    private final Instant poiTransactionTimestamp;
    private final boolean success;

    public CommittedStep(SettlementStep step, String saleTransactionId,
                         String poiTransactionId, Instant poiTransactionTimestamp,
                         boolean success) {
        this.step = step;
        this.saleTransactionId = saleTransactionId;
        this.poiTransactionId = poiTransactionId;
        this.poiTransactionTimestamp = poiTransactionTimestamp;
        this.success = success;
    }

    public SettlementStep getStep() {
        return step;
    }

    /** The {@code SaleTransactionID} the step was sent with. */
    public String getSaleTransactionId() {
        return saleTransactionId;
    }

    /** The terminal's reference for the committed step. */
    public String getPoiTransactionId() {
        return poiTransactionId;
    }

    public Instant getPoiTransactionTimestamp() {
        return poiTransactionTimestamp;
    }

    public boolean isSuccess() {
        return success;
    }
}
