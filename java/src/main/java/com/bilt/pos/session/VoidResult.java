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
 * Outcome of {@code voidTransaction()}: the payment reversal plus a best-effort loyalty award
 * reversal.
 *
 * <p>The result describes the movements <em>this call</em> reversed. A resumed void — a retry after
 * a partial void aborted — does not restate the movements earlier attempts reversed: its amount,
 * transaction reference, and receipts cover only the legs sent this time (and may all be absent
 * when the retry found nothing left to send). A register printing a void receipt should print it
 * from the attempt that reversed the money leg, or reprint via the transaction status query.
 */
public final class VoidResult {

  private final boolean success;
  private final BigDecimal reversedAmount;
  private final List<TenderMovement> cardReversals;
  private final List<TenderMovement> biltRewardsReversals;
  private final TenderMovement storedValueReversal;
  private final List<TenderAmount> reversedAmountByTender;
  private final String poiTransactionId;
  private final Instant poiTransactionTimestamp;
  private final String serviceId;
  private final Receipt customerReceipt;
  private final Receipt merchantReceipt;
  private final int pointsReversed;
  private final int remainingPointBalance;

  private VoidResult(Builder builder) {
    this.success = builder.success;
    this.reversedAmount = builder.reversedAmount;
    this.cardReversals = Collections.unmodifiableList(new ArrayList<>(builder.cardReversals));
    this.biltRewardsReversals =
        Collections.unmodifiableList(new ArrayList<>(builder.biltRewardsReversals));
    this.storedValueReversal = builder.storedValueReversal;
    this.reversedAmountByTender =
        Collections.unmodifiableList(new ArrayList<>(builder.reversedAmountByTender));
    this.poiTransactionId = builder.poiTransactionId;
    this.poiTransactionTimestamp = builder.poiTransactionTimestamp;
    this.serviceId = builder.serviceId;
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
   * Amount reversed by this call's money tender legs: the sum of {@link
   * #getReversedAmountByTender()}. A leg the terminal did not echo an amount for counts the
   * original card payment's amount when known; otherwise the total is {@code null} rather than
   * understated.
   */
  public BigDecimal getReversedAmount() {
    return reversedAmount;
  }

  /**
   * Terminal reference of the first reversal this call sent (the newest card payment when there are
   * several), for later status checks; see {@link #getCardReversals()} for every card payment's
   * reference and receipts.
   */
  public String getPoiTransactionId() {
    return poiTransactionId;
  }

  public Instant getPoiTransactionTimestamp() {
    return poiTransactionTimestamp;
  }

  /**
   * The Nexo {@code ServiceID} of the request whose terminal reference {@link
   * #getPoiTransactionId()} reports; {@code null} when that is absent.
   */
  public String getServiceId() {
    return serviceId;
  }

  /**
   * Receipt of the first leg this call sent; see {@link #getCardReversals()} for every card
   * payment's.
   */
  public Receipt getCustomerReceipt() {
    return customerReceipt;
  }

  /**
   * Receipt of the first leg this call sent; see {@link #getCardReversals()} for every card
   * payment's.
   */
  public Receipt getMerchantReceipt() {
    return merchantReceipt;
  }

  /**
   * Every card payment this call reversed, in the order the requests were sent (newest card payment
   * first), each with the terminal's own reference and receipts. Empty when no card payment was
   * reversed by this call. Every entry's {@link TenderMovement#getTenderType()} is {@code CARD}.
   * The stored value leg is {@link #getStoredValueReversal()}, and a Bilt rewards payment is {@link
   * #getBiltRewardsReversals()}.
   */
  public List<TenderMovement> getCardReversals() {
    return cardReversals;
  }

  /**
   * Every Bilt rewards payment this call reversed (a payment the terminal paid entirely with Bilt
   * rewards), in the order the requests were sent, each with the terminal's own reference. Empty
   * when none was. Every entry's {@link TenderMovement#getTenderType()} is {@code BILT_REWARDS}.
   */
  public List<TenderMovement> getBiltRewardsReversals() {
    return biltRewardsReversals;
  }

  /**
   * The stored value (gift card) payment this call reversed, with the terminal's own reference and
   * receipts for it; {@code null} when this call reversed none. A sale has at most one.
   */
  public TenderMovement getStoredValueReversal() {
    return storedValueReversal;
  }

  /**
   * The amount each money tender leg of this call returned, in the order the legs were sent: one
   * entry per card payment, one per Bilt rewards payment and one for a stored value payment. A card
   * payment that Bilt rewards paid part of has two entries with its reference, its card part under
   * {@code CARD} and its rewards part under {@code BILT_REWARDS}. Loyalty movements are not tenders
   * and are not listed. When there is any entry, the entries sum to {@link #getReversedAmount()};
   * when a leg's amount is unknown, that entry's amount and {@link #getReversedAmount()} are both
   * {@code null}.
   */
  public List<TenderAmount> getReversedAmountByTender() {
    return reversedAmountByTender;
  }

  /** Loyalty points reversed alongside the void; best-effort. */
  public int getPointsReversed() {
    return pointsReversed;
  }

  /** Member's balance after the reversal; {@code 0} when not reported. */
  public int getRemainingPointBalance() {
    return remainingPointBalance;
  }

  /** Builder for {@link VoidResult}. Intended for SDK use. */
  public static final class Builder {

    private boolean success;
    private BigDecimal reversedAmount;
    private List<TenderMovement> cardReversals = new ArrayList<>();
    private List<TenderMovement> biltRewardsReversals = new ArrayList<>();
    private TenderMovement storedValueReversal;
    private List<TenderAmount> reversedAmountByTender = new ArrayList<>();
    private String poiTransactionId;
    private Instant poiTransactionTimestamp;
    private String serviceId;
    private Receipt customerReceipt;
    private Receipt merchantReceipt;
    private int pointsReversed;
    private int remainingPointBalance;

    private Builder() {}

    public Builder cardReversals(List<TenderMovement> cardReversals) {
      this.cardReversals =
          cardReversals == null ? new ArrayList<>() : new ArrayList<>(cardReversals);
      return this;
    }

    public Builder biltRewardsReversals(List<TenderMovement> biltRewardsReversals) {
      this.biltRewardsReversals =
          biltRewardsReversals == null ? new ArrayList<>() : new ArrayList<>(biltRewardsReversals);
      return this;
    }

    public Builder addBiltRewardsReversal(TenderMovement biltRewardsReversal) {
      this.biltRewardsReversals.add(
          Objects.requireNonNull(biltRewardsReversal, "biltRewardsReversal"));
      return this;
    }

    public Builder addCardReversal(TenderMovement cardReversal) {
      this.cardReversals.add(Objects.requireNonNull(cardReversal, "cardReversal"));
      return this;
    }

    public Builder storedValueReversal(TenderMovement storedValueReversal) {
      this.storedValueReversal = storedValueReversal;
      return this;
    }

    public Builder reversedAmountByTender(List<TenderAmount> reversedAmountByTender) {
      this.reversedAmountByTender =
          reversedAmountByTender == null
              ? new ArrayList<>()
              : new ArrayList<>(reversedAmountByTender);
      return this;
    }

    public Builder success(boolean success) {
      this.success = success;
      return this;
    }

    public Builder reversedAmount(BigDecimal reversedAmount) {
      this.reversedAmount = reversedAmount;
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

    public Builder serviceId(String serviceId) {
      this.serviceId = serviceId;
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

    public VoidResult build() {
      return new VoidResult(this);
    }
  }
}
