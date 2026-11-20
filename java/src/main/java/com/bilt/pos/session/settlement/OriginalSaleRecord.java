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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * References from a completed sale that a later checkout session can use to void the whole original
 * transaction or allocate return settlement.
 *
 * <p>A sale may hold several card payments ({@link #getCardPayments()}, in the order they were
 * made). The single-card accessors and builder methods ({@link #getCardPoiTransactionId()}, {@link
 * Builder#cardPoiTransactionId(String)}) keep working and describe the first card payment.
 *
 * <p>A gift card payment is never a card payment. A card reference equal to the stored value
 * reference — how a gift-card-only sale was recorded before card payment lists — is read as the
 * stored value payment only: it is left out of {@link #getCardPayments()}, and the single-card
 * getters still return it.
 */
public final class OriginalSaleRecord {

  private final List<CardPaymentRecord> cardPayments;
  // a single-card reference that was the stored value payment, kept for the single-card getters
  private final CardPaymentRecord storedValueAsCard;
  private final String storedValuePoiTransactionId;
  private final Instant storedValuePoiTransactionTimestamp;
  private final List<StoredValueLoadRecord> storedValueLoads;
  private final String rebatePoiTransactionId;
  private final Instant rebatePoiTransactionTimestamp;
  private final String redemptionPoiTransactionId;
  private final Instant redemptionPoiTransactionTimestamp;
  private final String awardPoiTransactionId;
  private final Instant awardPoiTransactionTimestamp;
  private final String memberId;

  private OriginalSaleRecord(Builder builder) {
    List<CardPaymentRecord> resolved = builder.resolvedCardPayments();
    CardPaymentRecord giftCard = null;
    for (Iterator<CardPaymentRecord> it = resolved.iterator(); it.hasNext(); ) {
      CardPaymentRecord payment = it.next();
      if (payment.getPoiTransactionId().equals(builder.storedValuePoiTransactionId)) {
        giftCard = giftCard == null ? payment : giftCard;
        it.remove();
      }
    }
    this.cardPayments = Collections.unmodifiableList(resolved);
    this.storedValueAsCard = giftCard;
    this.storedValuePoiTransactionId = builder.storedValuePoiTransactionId;
    this.storedValuePoiTransactionTimestamp = builder.storedValuePoiTransactionTimestamp;
    this.storedValueLoads = Collections.unmodifiableList(new ArrayList<>(builder.storedValueLoads));
    this.rebatePoiTransactionId = builder.rebatePoiTransactionId;
    this.rebatePoiTransactionTimestamp = builder.rebatePoiTransactionTimestamp;
    this.redemptionPoiTransactionId = builder.redemptionPoiTransactionId;
    this.redemptionPoiTransactionTimestamp = builder.redemptionPoiTransactionTimestamp;
    this.awardPoiTransactionId = builder.awardPoiTransactionId;
    this.awardPoiTransactionTimestamp = builder.awardPoiTransactionTimestamp;
    this.memberId = builder.memberId;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builds a record from a completed settlement result, with every card payment the terminal
   * reported a POI reference for. A result without such a card payment keeps its single payment
   * reference ({@link SettlementResult#getPoiTransactionId()}) as the card payment, unless that is
   * the stored value payment: a gift-card-only sale has no card payments, and its payment is only
   * in the stored value fields (the single-card getters still return it, as before).
   */
  public static OriginalSaleRecord from(SettlementResult result, String memberId) {
    Builder builder = builder();
    for (CardPaymentResult payment : result.getCardPayments()) {
      if (payment.getPoiTransactionId() != null) {
        builder.addCardPayment(CardPaymentRecord.from(payment));
      }
    }
    if (builder.cardPayments.isEmpty()) {
      builder
          .cardPoiTransactionId(result.getPoiTransactionId())
          .cardPoiTransactionTimestamp(result.getPoiTransactionTimestamp());
    }
    return builder
        .storedValuePoiTransactionId(result.getStoredValuePoiTransactionId())
        .storedValuePoiTransactionTimestamp(result.getStoredValuePoiTransactionTimestamp())
        .storedValueLoads(result.getStoredValueLoads())
        .rebatePoiTransactionId(result.getRebatePoiTransactionId())
        .rebatePoiTransactionTimestamp(result.getRebatePoiTransactionTimestamp())
        .redemptionPoiTransactionId(result.getRedemptionPoiTransactionId())
        .redemptionPoiTransactionTimestamp(result.getRedemptionPoiTransactionTimestamp())
        .awardPoiTransactionId(result.getAwardPoiTransactionId())
        .awardPoiTransactionTimestamp(result.getAwardPoiTransactionTimestamp())
        .memberId(memberId)
        .build();
  }

  /** The sale's card payments in the order they were made; empty when it had none. */
  public List<CardPaymentRecord> getCardPayments() {
    return cardPayments;
  }

  /**
   * POI reference of the first card payment, or {@code null} when the sale had none. With several
   * card payments, see {@link #getCardPayments()}.
   */
  public String getCardPoiTransactionId() {
    CardPaymentRecord first = firstPaymentReference();
    return first == null ? null : first.getPoiTransactionId();
  }

  /** POI timestamp of the first card payment, or {@code null}. */
  public Instant getCardPoiTransactionTimestamp() {
    CardPaymentRecord first = firstPaymentReference();
    return first == null ? null : first.getPoiTransactionTimestamp();
  }

  /** The first card payment; for a gift-card-only sale, its payment, as the getters always gave. */
  private CardPaymentRecord firstPaymentReference() {
    return cardPayments.isEmpty() ? storedValueAsCard : cardPayments.get(0);
  }

  public String getStoredValuePoiTransactionId() {
    return storedValuePoiTransactionId;
  }

  public Instant getStoredValuePoiTransactionTimestamp() {
    return storedValuePoiTransactionTimestamp;
  }

  /** Stored value basket-line fulfillments that must be reversed by a whole-sale void. */
  public List<StoredValueLoadRecord> getStoredValueLoads() {
    return storedValueLoads;
  }

  public String getRebatePoiTransactionId() {
    return rebatePoiTransactionId;
  }

  public Instant getRebatePoiTransactionTimestamp() {
    return rebatePoiTransactionTimestamp;
  }

  public String getRedemptionPoiTransactionId() {
    return redemptionPoiTransactionId;
  }

  public Instant getRedemptionPoiTransactionTimestamp() {
    return redemptionPoiTransactionTimestamp;
  }

  public String getAwardPoiTransactionId() {
    return awardPoiTransactionId;
  }

  public Instant getAwardPoiTransactionTimestamp() {
    return awardPoiTransactionTimestamp;
  }

  public String getMemberId() {
    return memberId;
  }

  /** Whether the record contains at least one movement reference. */
  public boolean hasMovement() {
    return !cardPayments.isEmpty()
        || storedValuePoiTransactionId != null
        || !storedValueLoads.isEmpty()
        || rebatePoiTransactionId != null
        || redemptionPoiTransactionId != null
        || awardPoiTransactionId != null;
  }

  /**
   * Whether this record and {@code other} reference any of the same POI transactions. Movement type
   * is deliberately ignored: a transaction ID reused in different legs still identifies an
   * overlapping sale target.
   *
   * @param other record to compare with this one
   * @return whether the records share at least one POI transaction ID
   * @throws NullPointerException if {@code other} is null
   */
  public boolean sharesMovementWith(OriginalSaleRecord other) {
    Objects.requireNonNull(other, "other");
    for (CardPaymentRecord payment : other.cardPayments) {
      if (references(payment.getPoiTransactionId())) {
        return true;
      }
    }
    if (references(other.storedValuePoiTransactionId)
        || references(other.rebatePoiTransactionId)
        || references(other.redemptionPoiTransactionId)
        || references(other.awardPoiTransactionId)) {
      return true;
    }
    for (StoredValueLoadRecord load : other.storedValueLoads) {
      if (references(load.getPoiTransactionId())) {
        return true;
      }
    }
    return false;
  }

  private boolean references(String poiTransactionId) {
    if (poiTransactionId == null) {
      return false;
    }
    if (poiTransactionId.equals(storedValuePoiTransactionId)
        || poiTransactionId.equals(rebatePoiTransactionId)
        || poiTransactionId.equals(redemptionPoiTransactionId)
        || poiTransactionId.equals(awardPoiTransactionId)) {
      return true;
    }
    for (CardPaymentRecord payment : cardPayments) {
      if (poiTransactionId.equals(payment.getPoiTransactionId())) {
        return true;
      }
    }
    for (StoredValueLoadRecord load : storedValueLoads) {
      if (poiTransactionId.equals(load.getPoiTransactionId())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof OriginalSaleRecord)) {
      return false;
    }
    OriginalSaleRecord that = (OriginalSaleRecord) other;
    return cardPayments.equals(that.cardPayments)
        && Objects.equals(storedValueAsCard, that.storedValueAsCard)
        && Objects.equals(storedValuePoiTransactionId, that.storedValuePoiTransactionId)
        && Objects.equals(
            storedValuePoiTransactionTimestamp, that.storedValuePoiTransactionTimestamp)
        && Objects.equals(storedValueLoads, that.storedValueLoads)
        && Objects.equals(rebatePoiTransactionId, that.rebatePoiTransactionId)
        && Objects.equals(rebatePoiTransactionTimestamp, that.rebatePoiTransactionTimestamp)
        && Objects.equals(redemptionPoiTransactionId, that.redemptionPoiTransactionId)
        && Objects.equals(redemptionPoiTransactionTimestamp, that.redemptionPoiTransactionTimestamp)
        && Objects.equals(awardPoiTransactionId, that.awardPoiTransactionId)
        && Objects.equals(awardPoiTransactionTimestamp, that.awardPoiTransactionTimestamp)
        && Objects.equals(memberId, that.memberId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        cardPayments,
        storedValueAsCard,
        storedValuePoiTransactionId,
        storedValuePoiTransactionTimestamp,
        storedValueLoads,
        rebatePoiTransactionId,
        rebatePoiTransactionTimestamp,
        redemptionPoiTransactionId,
        redemptionPoiTransactionTimestamp,
        awardPoiTransactionId,
        awardPoiTransactionTimestamp,
        memberId);
  }

  /** Builder for {@link OriginalSaleRecord}. */
  public static final class Builder {

    private List<CardPaymentRecord> cardPayments = new ArrayList<>();
    private String cardPoiTransactionId;
    private Instant cardPoiTransactionTimestamp;
    private String storedValuePoiTransactionId;
    private Instant storedValuePoiTransactionTimestamp;
    private List<StoredValueLoadRecord> storedValueLoads = new ArrayList<>();
    private String rebatePoiTransactionId;
    private Instant rebatePoiTransactionTimestamp;
    private String redemptionPoiTransactionId;
    private Instant redemptionPoiTransactionTimestamp;
    private String awardPoiTransactionId;
    private Instant awardPoiTransactionTimestamp;
    private String memberId;

    private Builder() {}

    /** The sale's card payments in the order they were made. Replaces any added before. */
    public Builder cardPayments(List<CardPaymentRecord> cardPayments) {
      this.cardPayments = new ArrayList<>();
      if (cardPayments != null) {
        cardPayments.forEach(this::addCardPayment);
      }
      return this;
    }

    /** Appends the sale's next card payment. */
    public Builder addCardPayment(CardPaymentRecord cardPayment) {
      this.cardPayments.add(Objects.requireNonNull(cardPayment, "cardPayment"));
      return this;
    }

    /**
     * POI reference of the sale's first card payment. On its own it records a single card payment
     * with no amount; together with {@link #cardPayments(List)} or {@link
     * #addCardPayment(CardPaymentRecord)} it replaces the first one's reference. A timestamp
     * without a reference is ignored.
     */
    public Builder cardPoiTransactionId(String cardPoiTransactionId) {
      this.cardPoiTransactionId = cardPoiTransactionId;
      return this;
    }

    /** POI timestamp of the sale's first card payment; see {@link #cardPoiTransactionId}. */
    public Builder cardPoiTransactionTimestamp(Instant cardPoiTransactionTimestamp) {
      this.cardPoiTransactionTimestamp = cardPoiTransactionTimestamp;
      return this;
    }

    public Builder storedValuePoiTransactionId(String storedValuePoiTransactionId) {
      this.storedValuePoiTransactionId = storedValuePoiTransactionId;
      return this;
    }

    public Builder storedValuePoiTransactionTimestamp(Instant storedValuePoiTransactionTimestamp) {
      this.storedValuePoiTransactionTimestamp = storedValuePoiTransactionTimestamp;
      return this;
    }

    public Builder storedValueLoads(List<StoredValueLoadRecord> storedValueLoads) {
      this.storedValueLoads =
          storedValueLoads == null ? new ArrayList<>() : new ArrayList<>(storedValueLoads);
      return this;
    }

    public Builder addStoredValueLoad(StoredValueLoadRecord storedValueLoad) {
      this.storedValueLoads.add(Objects.requireNonNull(storedValueLoad, "storedValueLoad"));
      return this;
    }

    public Builder rebatePoiTransactionId(String rebatePoiTransactionId) {
      this.rebatePoiTransactionId = rebatePoiTransactionId;
      return this;
    }

    public Builder rebatePoiTransactionTimestamp(Instant rebatePoiTransactionTimestamp) {
      this.rebatePoiTransactionTimestamp = rebatePoiTransactionTimestamp;
      return this;
    }

    public Builder redemptionPoiTransactionId(String redemptionPoiTransactionId) {
      this.redemptionPoiTransactionId = redemptionPoiTransactionId;
      return this;
    }

    public Builder redemptionPoiTransactionTimestamp(Instant redemptionPoiTransactionTimestamp) {
      this.redemptionPoiTransactionTimestamp = redemptionPoiTransactionTimestamp;
      return this;
    }

    public Builder awardPoiTransactionId(String awardPoiTransactionId) {
      this.awardPoiTransactionId = awardPoiTransactionId;
      return this;
    }

    public Builder awardPoiTransactionTimestamp(Instant awardPoiTransactionTimestamp) {
      this.awardPoiTransactionTimestamp = awardPoiTransactionTimestamp;
      return this;
    }

    public Builder memberId(String memberId) {
      this.memberId = memberId;
      return this;
    }

    public OriginalSaleRecord build() {
      return new OriginalSaleRecord(this);
    }

    /** The card payment list with the single-card setters applied to its first entry. */
    private List<CardPaymentRecord> resolvedCardPayments() {
      List<CardPaymentRecord> resolved = new ArrayList<>(cardPayments);
      if (cardPoiTransactionId == null) {
        return resolved;
      }
      CardPaymentRecord first =
          CardPaymentRecord.builder()
              .poiTransactionId(cardPoiTransactionId)
              .poiTransactionTimestamp(cardPoiTransactionTimestamp)
              .amount(resolved.isEmpty() ? null : resolved.get(0).getAmount())
              .build();
      if (resolved.isEmpty()) {
        resolved.add(first);
      } else {
        resolved.set(0, first);
      }
      return resolved;
    }
  }
}
