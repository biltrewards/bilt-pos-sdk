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

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One planned card payment of a split settlement: a fixed amount charged on its own card before the
 * next one. Plan them with {@link SettlementOptions.Builder#cardPayments(CardPayment...)}; the
 * settlement always follows the planned payments with one more card payment for the remainder.
 *
 * <pre>{@code
 * // $20 on the first card, the rest on the next
 * SettlementOptions.builder().cardPayments(CardPayment.of(new BigDecimal("20.00"))).build();
 * }</pre>
 */
public final class CardPayment {

  private final BigDecimal amount;

  private CardPayment(BigDecimal amount) {
    if (amount == null || amount.signum() <= 0) {
      throw new IllegalArgumentException("a planned card payment amount must be positive");
    }
    // the terminal authorizes whole cents and its replies are read as cents: a fraction of a cent
    // could never be authorized in full, and the payment would fail after it was charged
    if (amount.stripTrailingZeros().scale() > 2) {
      throw new IllegalArgumentException(
          "a planned card payment amount must be whole cents, not " + amount.toPlainString());
    }
    this.amount = amount;
  }

  /** A card payment of exactly {@code amount}, which must be positive and in whole cents. */
  public static CardPayment of(BigDecimal amount) {
    return new CardPayment(amount);
  }

  /** The amount this card payment requests. */
  public BigDecimal getAmount() {
    return amount;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof CardPayment)) {
      return false;
    }
    return amount.compareTo(((CardPayment) other).amount) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(amount.stripTrailingZeros());
  }

  @Override
  public String toString() {
    return "CardPayment(" + amount + ")";
  }
}
