/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * One payment a settlement made entirely with Bilt rewards: the terminal paid it from the member's
 * rewards (implicit loyalty) and reported {@code biltRewardsAmount} equal to its authorized amount.
 * With multi-tender on the terminal, it is the points leg of a sale, answered as a partial approval
 * before the card payments collect the rest; without it, it is a sale the rewards covered whole.
 *
 * <p>It is not a card payment, so it is never in {@code SettlementResult.getCardPayments()}: see
 * {@code SettlementResult.getBiltRewardsPayments()}. A void or a refund returns the rewards by its
 * POI reference. It has no card data, acquirer data or receipts.
 */
public final class BiltRewardsPaymentResult {

  private final BigDecimal amount;
  private final String poiTransactionId;
  private final Instant poiTransactionTimestamp;

  private BiltRewardsPaymentResult(Builder builder) {
    this.amount = Objects.requireNonNull(builder.amount, "amount");
    this.poiTransactionId = builder.poiTransactionId;
    this.poiTransactionTimestamp = builder.poiTransactionTimestamp;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The value of the rewards spent, in the payment's currency: what the terminal authorized. */
  public BigDecimal getAmount() {
    return amount;
  }

  /** Terminal reference of this payment, for void and refund, or {@code null} if not reported. */
  public String getPoiTransactionId() {
    return poiTransactionId;
  }

  public Instant getPoiTransactionTimestamp() {
    return poiTransactionTimestamp;
  }

  @Override
  public String toString() {
    return "BiltRewardsPaymentResult(" + amount + ", " + poiTransactionId + ")";
  }

  /** Builder for {@link BiltRewardsPaymentResult}. Intended for SDK use. */
  public static final class Builder {

    private BigDecimal amount;
    private String poiTransactionId;
    private Instant poiTransactionTimestamp;

    private Builder() {}

    public Builder amount(BigDecimal amount) {
      this.amount = amount;
      return this;
    }

    public Builder poiTransactionId(String poiTransactionId) {
      this.poiTransactionId = poiTransactionId;
      return this;
    }

    public Builder poiTransactionTimestamp(Instant poiTransactionTimestamp) {
      this.poiTransactionTimestamp = poiTransactionTimestamp;
      return this;
    }

    public BiltRewardsPaymentResult build() {
      return new BiltRewardsPaymentResult(this);
    }
  }
}
