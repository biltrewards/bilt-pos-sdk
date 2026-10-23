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

import java.util.Objects;

/**
 * A movement that completed before a whole-sale void stopped.
 *
 * <p>Use the step and POI transaction ID to persist cross-session reversal progress. A later void
 * can omit these references from its {@code OriginalSaleRecord}, avoiding a second reversal of
 * money that has already moved.
 */
public final class ReversedMovement {

  private final ReversalStep step;
  private final String poiTransactionId;
  private final String serviceId;

  public ReversedMovement(ReversalStep step, String poiTransactionId, String serviceId) {
    this.step = Objects.requireNonNull(step, "step");
    this.poiTransactionId = Objects.requireNonNull(poiTransactionId, "poiTransactionId");
    this.serviceId = Objects.requireNonNull(serviceId, "serviceId");
  }

  /** The kind of sale movement that was reversed. */
  public ReversalStep getStep() {
    return step;
  }

  /** The original movement's POI transaction ID. */
  public String getPoiTransactionId() {
    return poiTransactionId;
  }

  /**
   * The Nexo {@code ServiceID} of the reversal request that reversed the movement — the id the
   * {@link ReversalFlow#beforeStep} context reported for that attempt.
   */
  public String getServiceId() {
    return serviceId;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ReversedMovement)) {
      return false;
    }
    ReversedMovement that = (ReversedMovement) other;
    return step == that.step
        && poiTransactionId.equals(that.poiTransactionId)
        && serviceId.equals(that.serviceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(step, poiTransactionId, serviceId);
  }

  @Override
  public String toString() {
    return "ReversedMovement[" + step + ": " + poiTransactionId + ", " + serviceId + ']';
  }
}
