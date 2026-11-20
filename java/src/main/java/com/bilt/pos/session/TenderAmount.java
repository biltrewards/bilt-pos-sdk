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

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The amount one tender leg of a void or refund returned: which tender, the POI transaction it
 * reversed or refunded, and how much. Each card payment is its own entry.
 */
public final class TenderAmount {

  private final TenderType tender;
  private final String poiTransactionId;
  private final BigDecimal amount;

  /**
   * @param tender the tender the money went back to
   * @param poiTransactionId the original POI transaction reversed or refunded; {@code null} for an
   *     external tender or an unlinked refund
   * @param amount the amount returned, or {@code null} when it is not known
   */
  public TenderAmount(TenderType tender, String poiTransactionId, BigDecimal amount) {
    this.tender = Objects.requireNonNull(tender, "tender");
    this.poiTransactionId = poiTransactionId;
    this.amount = amount;
  }

  public TenderType getTender() {
    return tender;
  }

  /**
   * The original POI transaction this leg reversed or refunded; {@code null} when there is none.
   */
  public String getPoiTransactionId() {
    return poiTransactionId;
  }

  /**
   * Amount returned to this tender; {@code null} when the terminal did not report it and the SDK
   * had no amount for the original payment.
   */
  public BigDecimal getAmount() {
    return amount;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof TenderAmount)) {
      return false;
    }
    TenderAmount that = (TenderAmount) other;
    return tender == that.tender
        && Objects.equals(poiTransactionId, that.poiTransactionId)
        && (amount == null
            ? that.amount == null
            : that.amount != null && amount.compareTo(that.amount) == 0);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        tender, poiTransactionId, amount == null ? null : amount.stripTrailingZeros());
  }

  @Override
  public String toString() {
    return tender + " " + poiTransactionId + " " + amount;
  }
}
