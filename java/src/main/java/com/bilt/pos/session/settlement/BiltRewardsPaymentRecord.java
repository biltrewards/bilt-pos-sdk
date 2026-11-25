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

import com.bilt.pos.session.payment.BiltRewardsPaymentResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Persistable reference for voiding or refunding one Bilt rewards payment of an original sale: a
 * payment the terminal paid entirely with Bilt rewards. It is not a card payment; see {@link
 * OriginalSaleRecord#getBiltRewardsPayments()}.
 */
public final class BiltRewardsPaymentRecord {

  private final String poiTransactionId;
  private final Instant poiTransactionTimestamp;
  private final BigDecimal amount;

  private BiltRewardsPaymentRecord(Builder builder) {
    this.poiTransactionId = Objects.requireNonNull(builder.poiTransactionId, "poiTransactionId");
    this.poiTransactionTimestamp = builder.poiTransactionTimestamp;
    this.amount = builder.amount;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The persistable reference of a rewards payment the terminal reported a POI reference for. */
  public static BiltRewardsPaymentRecord from(BiltRewardsPaymentResult payment) {
    Objects.requireNonNull(payment, "payment");
    return builder()
        .poiTransactionId(payment.getPoiTransactionId())
        .poiTransactionTimestamp(payment.getPoiTransactionTimestamp())
        .amount(payment.getAmount())
        .build();
  }

  public String getPoiTransactionId() {
    return poiTransactionId;
  }

  public Instant getPoiTransactionTimestamp() {
    return poiTransactionTimestamp;
  }

  /** The value of the rewards the payment spent, or {@code null} when it was not recorded. */
  public BigDecimal getAmount() {
    return amount;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof BiltRewardsPaymentRecord)) {
      return false;
    }
    BiltRewardsPaymentRecord that = (BiltRewardsPaymentRecord) other;
    return poiTransactionId.equals(that.poiTransactionId)
        && Objects.equals(poiTransactionTimestamp, that.poiTransactionTimestamp)
        && (amount == null
            ? that.amount == null
            : that.amount != null && amount.compareTo(that.amount) == 0);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        poiTransactionId,
        poiTransactionTimestamp,
        amount == null ? null : amount.stripTrailingZeros());
  }

  public static final class Builder {
    private String poiTransactionId;
    private Instant poiTransactionTimestamp;
    private BigDecimal amount;

    private Builder() {}

    public Builder poiTransactionId(String poiTransactionId) {
      this.poiTransactionId = poiTransactionId;
      return this;
    }

    public Builder poiTransactionTimestamp(Instant poiTransactionTimestamp) {
      this.poiTransactionTimestamp = poiTransactionTimestamp;
      return this;
    }

    public Builder amount(BigDecimal amount) {
      this.amount = amount;
      return this;
    }

    public BiltRewardsPaymentRecord build() {
      return new BiltRewardsPaymentRecord(this);
    }
  }
}
