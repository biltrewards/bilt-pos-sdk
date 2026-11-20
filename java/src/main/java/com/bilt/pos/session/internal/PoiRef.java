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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A POI transaction reference: the id plus the timestamp the terminal reported with it. Pairs the
 * two values that always travel together, so a movement list is assembled from five references
 * instead of ten alternating positional arguments.
 */
public final class PoiRef {

  final String txnId;
  final Instant timestamp;
  // what the original movement moved, when known: a full reversal or refund returns this much
  final BigDecimal amount;

  private PoiRef(String txnId, Instant timestamp, BigDecimal amount) {
    this.txnId = txnId;
    this.timestamp = timestamp;
    this.amount = amount;
  }

  /** The reference, or {@code null} when the sale has no such leg. */
  public static PoiRef ofNullable(String txnId, Instant timestamp) {
    return ofNullable(txnId, timestamp, null);
  }

  /** The reference with the original movement's amount, or {@code null} without a reference. */
  public static PoiRef ofNullable(String txnId, Instant timestamp, BigDecimal amount) {
    return txnId == null ? null : new PoiRef(txnId, timestamp, amount);
  }
}
