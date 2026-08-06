/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   Internal API — subject to change without notice.
 */
package com.bilt.pos.session.internal;

import java.time.Instant;

/**
 * A POI transaction reference: the id plus the timestamp the terminal
 * reported with it. Pairs the two values that always travel together, so
 * a movement list is assembled from five references instead of ten
 * alternating positional arguments.
 */
public final class PoiRef {

    final String txnId;
    final Instant timestamp;

    private PoiRef(String txnId, Instant timestamp) {
        this.txnId = txnId;
        this.timestamp = timestamp;
    }

    /** The reference, or {@code null} when the sale has no such leg. */
    public static PoiRef ofNullable(String txnId, Instant timestamp) {
        return txnId == null ? null : new PoiRef(txnId, timestamp);
    }
}
