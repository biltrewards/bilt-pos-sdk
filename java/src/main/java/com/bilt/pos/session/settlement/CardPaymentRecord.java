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

import com.bilt.pos.session.payment.CardPaymentResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Persistable reference for voiding or refunding one card payment of an original sale. */
public final class CardPaymentRecord {

  private final String poiTransactionId;
  private final Instant poiTransactionTimestamp;
  private final BigDecimal amount;
  private final BigDecimal biltRewardsAmount;

  private CardPaymentRecord(Builder builder) {
    this.poiTransactionId = Objects.requireNonNull(builder.poiTransactionId, "poiTransactionId");
    this.poiTransactionTimestamp = builder.poiTransactionTimestamp;
    this.amount = builder.amount;
    this.biltRewardsAmount = builder.biltRewardsAmount;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The persistable reference of a card payment the terminal reported a POI reference for. */
  public static CardPaymentRecord from(CardPaymentResult payment) {
    Objects.requireNonNull(payment, "payment");
    return builder()
        .poiTransactionId(payment.getPoiTransactionId())
        .poiTransactionTimestamp(payment.getPoiTransactionTimestamp())
        .amount(payment.getAmount())
        .biltRewardsAmount(
            payment.getBiltRewardsAmount().signum() > 0 ? payment.getBiltRewardsAmount() : null)
        .build();
  }

  public String getPoiTransactionId() {
    return poiTransactionId;
  }

  public Instant getPoiTransactionTimestamp() {
    return poiTransactionTimestamp;
  }

  /**
   * Amount the payment authorized, or {@code null} when the record was built from a bare POI
   * reference (the single-card {@code OriginalSaleRecord.Builder.cardPoiTransactionId} path).
   */
  public BigDecimal getAmount() {
    return amount;
  }

  /**
   * The part of {@link #getAmount()} Bilt rewards paid ({@link
   * CardPaymentResult#getBiltRewardsAmount()}), or {@code null} when the card paid it all or it was
   * not recorded. A void or full refund reports this part under the Bilt rewards tender.
   */
  public BigDecimal getBiltRewardsAmount() {
    return biltRewardsAmount;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof CardPaymentRecord)) {
      return false;
    }
    CardPaymentRecord that = (CardPaymentRecord) other;
    return poiTransactionId.equals(that.poiTransactionId)
        && Objects.equals(poiTransactionTimestamp, that.poiTransactionTimestamp)
        && (amount == null
            ? that.amount == null
            : that.amount != null && amount.compareTo(that.amount) == 0)
        && (biltRewardsAmount == null
            ? that.biltRewardsAmount == null
            : that.biltRewardsAmount != null
                && biltRewardsAmount.compareTo(that.biltRewardsAmount) == 0);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        poiTransactionId,
        poiTransactionTimestamp,
        amount == null ? null : amount.stripTrailingZeros(),
        biltRewardsAmount == null ? null : biltRewardsAmount.stripTrailingZeros());
  }

  public static final class Builder {
    private String poiTransactionId;
    private Instant poiTransactionTimestamp;
    private BigDecimal amount;
    private BigDecimal biltRewardsAmount;

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

    public Builder biltRewardsAmount(BigDecimal biltRewardsAmount) {
      this.biltRewardsAmount = biltRewardsAmount;
      return this;
    }

    public CardPaymentRecord build() {
      return new CardPaymentRecord(this);
    }
  }
}
