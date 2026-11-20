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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Outcome of a refund.
 *
 * <p>Linked refunds also reverse loyalty points awarded on the original transaction; the reversal
 * outcome is reported via {@link #getPointsReversed()} (best-effort — a failed loyalty reversal
 * does not fail the refund).
 */
public final class RefundResult {

  private final boolean success;
  private final BigDecimal refundedAmount;
  private final List<TenderMovement> cardRefunds;
  private final List<TenderAmount> refundedAmountByTender;
  private final String approvalCode;
  private final String poiTransactionId;
  private final Instant poiTransactionTimestamp;
  private final Receipt customerReceipt;
  private final Receipt merchantReceipt;
  private final int pointsReversed;
  private final int remainingPointBalance;

  private RefundResult(Builder builder) {
    this.success = builder.success;
    this.refundedAmount = builder.refundedAmount;
    this.cardRefunds = Collections.unmodifiableList(new ArrayList<>(builder.cardRefunds));
    this.refundedAmountByTender =
        Collections.unmodifiableList(new ArrayList<>(builder.refundedAmountByTender));
    this.approvalCode = builder.approvalCode;
    this.poiTransactionId = builder.poiTransactionId;
    this.poiTransactionTimestamp = builder.poiTransactionTimestamp;
    this.customerReceipt = builder.customerReceipt;
    this.merchantReceipt = builder.merchantReceipt;
    this.pointsReversed = builder.pointsReversed;
    this.remainingPointBalance = builder.remainingPointBalance;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isSuccess() {
    return success;
  }

  /**
   * Amount refunded by this call's money tender legs: the sum of {@link
   * #getRefundedAmountByTender()}. A leg the terminal did not echo an amount for counts the
   * original card payment's amount when known; otherwise the total is {@code null} rather than
   * understated.
   */
  public BigDecimal getRefundedAmount() {
    return refundedAmount;
  }

  /** Approval code of the first refund this call sent; see {@link #getCardRefunds()}. */
  public String getApprovalCode() {
    return approvalCode;
  }

  /**
   * Terminal reference of the first refund this call sent (the newest card payment when there are
   * several), for later status checks; see {@link #getCardRefunds()} for every card payment's
   * reference and receipts.
   */
  public String getPoiTransactionId() {
    return poiTransactionId;
  }

  public Instant getPoiTransactionTimestamp() {
    return poiTransactionTimestamp;
  }

  /**
   * Receipt of the first leg this call sent; see {@link #getCardRefunds()} for every card
   * payment's.
   */
  public Receipt getCustomerReceipt() {
    return customerReceipt;
  }

  /**
   * Receipt of the first leg this call sent; see {@link #getCardRefunds()} for every card
   * payment's.
   */
  public Receipt getMerchantReceipt() {
    return merchantReceipt;
  }

  /**
   * Every card payment this call refunded, in the order the requests were sent (newest card payment
   * first), each with the terminal's own reference and receipts. Empty when no card payment was
   * refunded by this call. Every entry's {@link TenderMovement#getTenderType()} is {@code CARD}.
   */
  public List<TenderMovement> getCardRefunds() {
    return cardRefunds;
  }

  /**
   * The amount each money tender leg of this call returned, in the order the legs were sent: one
   * entry per card payment and one for a stored value payment. Loyalty movements are not tenders
   * and are not listed. When there is any entry, the entries sum to {@link #getRefundedAmount()};
   * when a leg's amount is unknown, that entry's amount and {@link #getRefundedAmount()} are both
   * {@code null}.
   */
  public List<TenderAmount> getRefundedAmountByTender() {
    return refundedAmountByTender;
  }

  /** Loyalty points reversed by a linked refund; {@code 0} for unlinked. */
  public int getPointsReversed() {
    return pointsReversed;
  }

  /** Member's balance after the reversal; {@code 0} when not reported. */
  public int getRemainingPointBalance() {
    return remainingPointBalance;
  }

  /** Builder for {@link RefundResult}. Intended for SDK use. */
  public static final class Builder {

    private boolean success;
    private BigDecimal refundedAmount;
    private List<TenderMovement> cardRefunds = new ArrayList<>();
    private List<TenderAmount> refundedAmountByTender = new ArrayList<>();
    private String approvalCode;
    private String poiTransactionId;
    private Instant poiTransactionTimestamp;
    private Receipt customerReceipt;
    private Receipt merchantReceipt;
    private int pointsReversed;
    private int remainingPointBalance;

    private Builder() {}

    public Builder cardRefunds(List<TenderMovement> cardRefunds) {
      this.cardRefunds = cardRefunds == null ? new ArrayList<>() : new ArrayList<>(cardRefunds);
      return this;
    }

    public Builder addCardRefund(TenderMovement cardRefund) {
      this.cardRefunds.add(Objects.requireNonNull(cardRefund, "cardRefund"));
      return this;
    }

    public Builder refundedAmountByTender(List<TenderAmount> refundedAmountByTender) {
      this.refundedAmountByTender =
          refundedAmountByTender == null
              ? new ArrayList<>()
              : new ArrayList<>(refundedAmountByTender);
      return this;
    }

    public Builder success(boolean success) {
      this.success = success;
      return this;
    }

    public Builder refundedAmount(BigDecimal refundedAmount) {
      this.refundedAmount = refundedAmount;
      return this;
    }

    public Builder approvalCode(String approvalCode) {
      this.approvalCode = approvalCode;
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

    public Builder customerReceipt(Receipt customerReceipt) {
      this.customerReceipt = customerReceipt;
      return this;
    }

    public Builder merchantReceipt(Receipt merchantReceipt) {
      this.merchantReceipt = merchantReceipt;
      return this;
    }

    public Builder pointsReversed(int pointsReversed) {
      this.pointsReversed = pointsReversed;
      return this;
    }

    public Builder remainingPointBalance(int remainingPointBalance) {
      this.remainingPointBalance = remainingPointBalance;
      return this;
    }

    public RefundResult build() {
      return new RefundResult(this);
    }
  }
}
