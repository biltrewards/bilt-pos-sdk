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
import java.time.Instant;

/**
 * One money or loyalty movement committed as part of a settlement, or the notice that the SDK
 * reversed one.
 *
 * <p>A reversal notice ({@link #isReversal()}) is delivered through {@code
 * SettlementFlow.onMovement} (and, for a card payment, {@code onCardChargeReversed}) when the
 * settlement itself successfully reverses a movement it had already reported: before a {@code
 * retry()}, {@code skip()} or {@code external()} replaces a failed step that had committed, and for
 * each movement an {@code abort()} unwind reverses. It carries the reversed movement's step,
 * target, amount and sale transaction, with the reversal request's own {@code ServiceID} and POI
 * reference, and names the movement it reverses in {@link #getReversedMovement()}. Reversal notices
 * never appear in a settlement's movement ledger ({@code SettlementResult.getMovements()}, {@code
 * SettlementFailure.getCommittedMovements()}): the reversed movement leaves the ledger instead.
 */
public final class SettlementMovement {

  private final SettlementStep step;
  private final SettlementTarget target;
  private final BigDecimal amount;
  private final String saleTransactionId;
  private final String serviceId;
  private final String poiTransactionId;
  private final Instant poiTransactionTimestamp;
  private final String memberId;
  private final Integer points;
  private final Integer pointBalance;
  private final String externalTenderType;
  private final String externalReference;
  private final SettlementMovement reversedMovement;

  private SettlementMovement(Builder builder) {
    this.step = builder.step;
    this.target = builder.target;
    this.amount = builder.amount;
    this.saleTransactionId = builder.saleTransactionId;
    this.serviceId = builder.serviceId;
    this.poiTransactionId = builder.poiTransactionId;
    this.poiTransactionTimestamp = builder.poiTransactionTimestamp;
    this.memberId = builder.memberId;
    this.points = builder.points;
    this.pointBalance = builder.pointBalance;
    this.externalTenderType = builder.externalTenderType;
    this.externalReference = builder.externalReference;
    this.reversedMovement = builder.reversedMovement;
  }

  public static Builder builder() {
    return new Builder();
  }

  public SettlementStep getStep() {
    return step;
  }

  /** Basket obligation resolved by this movement. */
  public SettlementTarget getTarget() {
    return target;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getSaleTransactionId() {
    return saleTransactionId;
  }

  /**
   * The Nexo {@code ServiceID} of the terminal request that committed this movement — the id the
   * step's {@code beforeStep} context reported. {@code null} for movements the register settled
   * outside the terminal (external payments and refunds).
   */
  public String getServiceId() {
    return serviceId;
  }

  public String getPoiTransactionId() {
    return poiTransactionId;
  }

  public Instant getPoiTransactionTimestamp() {
    return poiTransactionTimestamp;
  }

  public String getMemberId() {
    return memberId;
  }

  public Integer getPoints() {
    return points;
  }

  public Integer getPointBalance() {
    return pointBalance;
  }

  /** Register-managed tender name, populated for {@code EXTERNAL_PAYMENT}. */
  public String getExternalTenderType() {
    return externalTenderType;
  }

  /** Register transaction reference, populated when supplied for external payment. */
  public String getExternalReference() {
    return externalReference;
  }

  /**
   * Whether this is the notice that the SDK reversed {@link #getReversedMovement()}, rather than a
   * movement of its own. On a reversal notice, {@link #getServiceId()} and {@link
   * #getPoiTransactionId()} identify the reversal request, as the terminal reported it.
   */
  public boolean isReversal() {
    return reversedMovement != null;
  }

  /** The movement this notice reverses; {@code null} unless {@link #isReversal()}. */
  public SettlementMovement getReversedMovement() {
    return reversedMovement;
  }

  /** Builder for {@link SettlementMovement}. */
  public static final class Builder {

    private SettlementStep step;
    private SettlementTarget target = SettlementTarget.sales();
    private BigDecimal amount = BigDecimal.ZERO;
    private String saleTransactionId;
    private String serviceId;
    private String poiTransactionId;
    private Instant poiTransactionTimestamp;
    private String memberId;
    private Integer points;
    private Integer pointBalance;
    private String externalTenderType;
    private String externalReference;
    private SettlementMovement reversedMovement;

    private Builder() {}

    public Builder step(SettlementStep step) {
      this.step = step;
      return this;
    }

    public Builder target(SettlementTarget target) {
      this.target = target;
      return this;
    }

    public Builder amount(BigDecimal amount) {
      this.amount = amount;
      return this;
    }

    public Builder saleTransactionId(String saleTransactionId) {
      this.saleTransactionId = saleTransactionId;
      return this;
    }

    public Builder serviceId(String serviceId) {
      this.serviceId = serviceId;
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

    public Builder memberId(String memberId) {
      this.memberId = memberId;
      return this;
    }

    public Builder points(Integer points) {
      this.points = points;
      return this;
    }

    public Builder pointBalance(Integer pointBalance) {
      this.pointBalance = pointBalance;
      return this;
    }

    public Builder externalTenderType(String externalTenderType) {
      this.externalTenderType = externalTenderType;
      return this;
    }

    public Builder externalReference(String externalReference) {
      this.externalReference = externalReference;
      return this;
    }

    /** Makes this a reversal notice for {@code reversedMovement}; see {@link #isReversal()}. */
    public Builder reversedMovement(SettlementMovement reversedMovement) {
      this.reversedMovement = reversedMovement;
      return this;
    }

    public SettlementMovement build() {
      return new SettlementMovement(this);
    }
  }
}
