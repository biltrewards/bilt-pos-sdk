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

import com.bilt.pos.session.storedvalue.StoredValueCard;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Register-selected refund/restoration movement for a settlement with returns.
 *
 * <p>The SDK does not infer tender allocation policy. For a mixed card/gift card/points/cash sale,
 * the register supplies one allocation per movement it wants recorded or executed, including split
 * refunds for a single return item.
 */
public final class RefundAllocation {

  private final RefundAllocationType type;
  private final BigDecimal amount;
  private final String originalPoiTransactionId;
  private final Instant originalPoiTransactionTimestamp;
  private final StoredValueCard storedValueCard;
  private final String memberId;

  private RefundAllocation(Builder builder) {
    this.type = Objects.requireNonNull(builder.type, "type");
    this.amount = builder.amount == null ? BigDecimal.ZERO : builder.amount;
    this.originalPoiTransactionId = builder.originalPoiTransactionId;
    this.originalPoiTransactionTimestamp = builder.originalPoiTransactionTimestamp;
    this.storedValueCard = builder.storedValueCard;
    this.memberId = builder.memberId;
    validate();
  }

  /**
   * Linked card refund. Requires an original POI transaction ID; use {@link
   * #cardUnlinked(BigDecimal)} when the refund should be issued against a card presented at the
   * terminal.
   */
  public static RefundAllocation card(
      BigDecimal amount, String originalPoiTransactionId, Instant originalPoiTransactionTimestamp) {
    return builder()
        .type(RefundAllocationType.CARD)
        .amount(amount)
        .originalPoiTransactionId(
            Objects.requireNonNull(originalPoiTransactionId, "originalPoiTransactionId"))
        .originalPoiTransactionTimestamp(originalPoiTransactionTimestamp)
        .build();
  }

  /**
   * Linked card refund against a persisted original sale record. The record must contain exactly
   * one card payment (its Bilt rewards payments are not card payments, so they do not count, and a
   * sale paid only with rewards has no card leg: use {@link #biltRewards(BigDecimal,
   * OriginalSaleRecord)}): with several, the refund cannot tell which one to return to, so pick one
   * of {@link OriginalSaleRecord#getCardPayments()} and use {@link #card(BigDecimal, String,
   * Instant)}, one allocation per card payment. Use {@link #cardUnlinked(BigDecimal)} when the
   * refund should be issued against a card presented at the terminal.
   *
   * @throws IllegalArgumentException if the record holds more than one card payment
   */
  public static RefundAllocation card(BigDecimal amount, OriginalSaleRecord originalSale) {
    Objects.requireNonNull(originalSale, "originalSale");
    int cardPayments = originalSale.getCardPayments().size();
    if (cardPayments > 1) {
      throw new IllegalArgumentException(
          "originalSale has "
              + cardPayments
              + " card payments; refund each with RefundAllocation.card(amount, "
              + "poiTransactionId, timestamp) from originalSale.getCardPayments()");
    }
    return card(
        amount,
        Objects.requireNonNull(
            originalSale.getCardPoiTransactionId(),
            "originalSale has no card leg; use cardUnlinked() " + "for an unlinked refund"),
        originalSale.getCardPoiTransactionTimestamp());
  }

  /**
   * Linked refund of a Bilt rewards payment of the original sale ({@link
   * OriginalSaleRecord#getBiltRewardsPayments()}): the terminal returns {@code amount} to the
   * member's rewards. It is the same Nexo PaymentRequest(Refund) as a linked card refund, but the
   * settlement reports it as rewards ({@code SettlementResult.getBiltRewardsRefundedAmount()}), not
   * as card money.
   */
  public static RefundAllocation biltRewards(
      BigDecimal amount, String originalPoiTransactionId, Instant originalPoiTransactionTimestamp) {
    return builder()
        .type(RefundAllocationType.BILT_REWARDS)
        .amount(amount)
        .originalPoiTransactionId(
            Objects.requireNonNull(originalPoiTransactionId, "originalPoiTransactionId"))
        .originalPoiTransactionTimestamp(originalPoiTransactionTimestamp)
        .build();
  }

  /** {@link #biltRewards(BigDecimal, String, Instant)} against one of a sale's rewards payments. */
  public static RefundAllocation biltRewards(BigDecimal amount, BiltRewardsPaymentRecord payment) {
    Objects.requireNonNull(payment, "payment");
    return biltRewards(amount, payment.getPoiTransactionId(), payment.getPoiTransactionTimestamp());
  }

  /**
   * {@link #biltRewards(BigDecimal, String, Instant)} against a persisted original sale record,
   * which must contain exactly one Bilt rewards payment; with several, pick one of {@link
   * OriginalSaleRecord#getBiltRewardsPayments()} and use {@link #biltRewards(BigDecimal,
   * BiltRewardsPaymentRecord)}.
   *
   * @throws IllegalArgumentException if the record holds no Bilt rewards payment or several
   */
  public static RefundAllocation biltRewards(BigDecimal amount, OriginalSaleRecord originalSale) {
    Objects.requireNonNull(originalSale, "originalSale");
    int payments = originalSale.getBiltRewardsPayments().size();
    if (payments != 1) {
      throw new IllegalArgumentException(
          payments == 0
              ? "originalSale has no Bilt rewards payment"
              : "originalSale has "
                  + payments
                  + " Bilt rewards payments; refund each with RefundAllocation.biltRewards("
                  + "amount, payment) from originalSale.getBiltRewardsPayments()");
    }
    return biltRewards(amount, originalSale.getBiltRewardsPayments().get(0));
  }

  /** Unlinked card refund. */
  public static RefundAllocation cardUnlinked(BigDecimal amount) {
    return builder().type(RefundAllocationType.CARD).amount(amount).build();
  }

  /** Linked stored value refund using a Nexo PaymentRequest(Refund). */
  public static RefundAllocation storedValue(
      BigDecimal amount, String originalPoiTransactionId, Instant originalPoiTransactionTimestamp) {
    return builder()
        .type(RefundAllocationType.STORED_VALUE)
        .amount(amount)
        .originalPoiTransactionId(originalPoiTransactionId)
        .originalPoiTransactionTimestamp(originalPoiTransactionTimestamp)
        .build();
  }

  /** Linked stored value refund against a persisted original sale record. */
  public static RefundAllocation storedValue(BigDecimal amount, OriginalSaleRecord originalSale) {
    Objects.requireNonNull(originalSale, "originalSale");
    return storedValue(
        amount,
        originalSale.getStoredValuePoiTransactionId(),
        originalSale.getStoredValuePoiTransactionTimestamp());
  }

  /** Store-credit refund/restoration as a load onto the supplied stored value card. */
  public static RefundAllocation storeCredit(StoredValueCard card, BigDecimal amount) {
    return builder()
        .type(RefundAllocationType.STORE_CREDIT)
        .storedValueCard(card)
        .amount(amount)
        .build();
  }

  /**
   * Register-managed refund/restoration outside the terminal, such as cash paid from the drawer.
   * Counts toward the return allocation total but sends no Nexo movement.
   */
  public static RefundAllocation external(BigDecimal amount) {
    return builder().type(RefundAllocationType.EXTERNAL).amount(amount).build();
  }

  /** Refund a prior points/rewards redemption by original POI transaction reference. */
  public static RefundAllocation pointRedemption(
      BigDecimal amount,
      String originalPoiTransactionId,
      Instant originalPoiTransactionTimestamp,
      String memberId) {
    return builder()
        .type(RefundAllocationType.POINT_REDEMPTION)
        .amount(amount)
        .originalPoiTransactionId(originalPoiTransactionId)
        .originalPoiTransactionTimestamp(originalPoiTransactionTimestamp)
        .memberId(memberId)
        .build();
  }

  /** Refund a prior points/rewards redemption from a persisted original sale record. */
  public static RefundAllocation pointRedemption(
      BigDecimal amount, OriginalSaleRecord originalSale) {
    Objects.requireNonNull(originalSale, "originalSale");
    return pointRedemption(
        amount,
        originalSale.getRedemptionPoiTransactionId(),
        originalSale.getRedemptionPoiTransactionTimestamp(),
        originalSale.getMemberId());
  }

  /** Refund a prior rebate/coupon redemption by original POI transaction reference. */
  public static RefundAllocation rebate(
      BigDecimal amount,
      String originalPoiTransactionId,
      Instant originalPoiTransactionTimestamp,
      String memberId) {
    return builder()
        .type(RefundAllocationType.REBATE)
        .amount(amount)
        .originalPoiTransactionId(originalPoiTransactionId)
        .originalPoiTransactionTimestamp(originalPoiTransactionTimestamp)
        .memberId(memberId)
        .build();
  }

  /** Refund a prior rebate/coupon redemption from a persisted original sale record. */
  public static RefundAllocation rebate(BigDecimal amount, OriginalSaleRecord originalSale) {
    Objects.requireNonNull(originalSale, "originalSale");
    return rebate(
        amount,
        originalSale.getRebatePoiTransactionId(),
        originalSale.getRebatePoiTransactionTimestamp(),
        originalSale.getMemberId());
  }

  /** Reverse a prior loyalty award by original POI transaction reference. */
  public static RefundAllocation award(
      String originalPoiTransactionId, Instant originalPoiTransactionTimestamp, String memberId) {
    return builder()
        .type(RefundAllocationType.AWARD)
        .amount(BigDecimal.ZERO)
        .originalPoiTransactionId(originalPoiTransactionId)
        .originalPoiTransactionTimestamp(originalPoiTransactionTimestamp)
        .memberId(memberId)
        .build();
  }

  /** Reverse a prior loyalty award from a persisted original sale record. */
  public static RefundAllocation award(OriginalSaleRecord originalSale) {
    Objects.requireNonNull(originalSale, "originalSale");
    return award(
        originalSale.getAwardPoiTransactionId(),
        originalSale.getAwardPoiTransactionTimestamp(),
        originalSale.getMemberId());
  }

  public static Builder builder() {
    return new Builder();
  }

  public RefundAllocationType getType() {
    return type;
  }

  /** Every refund allocation resolves the basket's aggregate return target. */
  public SettlementTarget getTarget() {
    return SettlementTarget.refunds();
  }

  /**
   * Monetary value this movement contributes to the merchandise return allocation. Award reversals
   * are bookkeeping and use zero.
   */
  public BigDecimal getAmount() {
    return amount;
  }

  public String getOriginalPoiTransactionId() {
    return originalPoiTransactionId;
  }

  public Instant getOriginalPoiTransactionTimestamp() {
    return originalPoiTransactionTimestamp;
  }

  public StoredValueCard getStoredValueCard() {
    return storedValueCard;
  }

  public String getMemberId() {
    return memberId;
  }

  /** Whether this allocation counts toward the monetary value of return items. */
  public boolean countsTowardRefundTotal() {
    return type != RefundAllocationType.AWARD;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof RefundAllocation)) {
      return false;
    }
    RefundAllocation that = (RefundAllocation) other;
    return type == that.type
        && amount.compareTo(that.amount) == 0
        && Objects.equals(originalPoiTransactionId, that.originalPoiTransactionId)
        && Objects.equals(originalPoiTransactionTimestamp, that.originalPoiTransactionTimestamp)
        && Objects.equals(storedValueCard, that.storedValueCard)
        && Objects.equals(memberId, that.memberId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        type,
        normalizedAmount(amount),
        originalPoiTransactionId,
        originalPoiTransactionTimestamp,
        storedValueCard,
        memberId);
  }

  private static BigDecimal normalizedAmount(BigDecimal value) {
    return value.stripTrailingZeros();
  }

  private void validate() {
    if (amount.signum() < 0 || (countsTowardRefundTotal() && amount.signum() == 0)) {
      throw new IllegalArgumentException(type + " refund allocation amount must be positive");
    }
    if (type == RefundAllocationType.STORED_VALUE || type == RefundAllocationType.BILT_REWARDS) {
      Objects.requireNonNull(originalPoiTransactionId, "originalPoiTransactionId");
    }
    if (type == RefundAllocationType.STORE_CREDIT) {
      Objects.requireNonNull(storedValueCard, "storedValueCard");
    }
    if (type == RefundAllocationType.POINT_REDEMPTION
        || type == RefundAllocationType.REBATE
        || type == RefundAllocationType.AWARD) {
      Objects.requireNonNull(originalPoiTransactionId, "originalPoiTransactionId");
      Objects.requireNonNull(memberId, "memberId");
    }
  }

  /** Builder for {@link RefundAllocation}. */
  public static final class Builder {

    private RefundAllocationType type;
    private BigDecimal amount;
    private String originalPoiTransactionId;
    private Instant originalPoiTransactionTimestamp;
    private StoredValueCard storedValueCard;
    private String memberId;

    private Builder() {}

    public Builder type(RefundAllocationType type) {
      this.type = type;
      return this;
    }

    public Builder amount(BigDecimal amount) {
      this.amount = amount;
      return this;
    }

    public Builder originalPoiTransactionId(String originalPoiTransactionId) {
      this.originalPoiTransactionId = originalPoiTransactionId;
      return this;
    }

    public Builder originalPoiTransactionTimestamp(Instant originalPoiTransactionTimestamp) {
      this.originalPoiTransactionTimestamp = originalPoiTransactionTimestamp;
      return this;
    }

    public Builder storedValueCard(StoredValueCard storedValueCard) {
      this.storedValueCard = storedValueCard;
      return this;
    }

    public Builder memberId(String memberId) {
      this.memberId = memberId;
      return this;
    }

    public RefundAllocation build() {
      return new RefundAllocation(this);
    }
  }
}
