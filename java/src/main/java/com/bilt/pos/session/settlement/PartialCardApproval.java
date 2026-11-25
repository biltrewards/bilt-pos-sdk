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
import com.bilt.pos.session.payment.CardPaymentResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Context supplied to {@code SettlementFlow.onPartialCardApproval} when a card payment committed
 * with an authorized amount below the amount it requested. By then the payment's movement has been
 * published and {@code onCardCharged} has fired. The handler returns a {@link
 * PartialApprovalDecision} for the shortfall.
 *
 * <p>With implicit loyalty and multi-tender on the terminal, the partial payment can be the
 * terminal's Bilt rewards leg: the member's rewards paid {@link #getApproved()} and no card was in
 * it. Then {@link #getBiltRewardsPayment()} is that payment and {@link #isBiltRewards()} is {@code
 * true}; the register should report it to the shopper as rewards, not as a declined card.
 */
public final class PartialCardApproval {

  private final BigDecimal approved;
  private final BigDecimal requested;
  private final BigDecimal amountDue;
  private final List<CardPaymentResult> cardPayments;
  private final SettlementMovement movement;
  private final BigDecimal biltRewardsAmount;
  private final BiltRewardsPaymentResult biltRewardsPayment;

  private PartialCardApproval(Builder builder) {
    this.approved = Objects.requireNonNull(builder.approved, "approved");
    this.requested = Objects.requireNonNull(builder.requested, "requested");
    this.amountDue = Objects.requireNonNull(builder.amountDue, "amountDue");
    this.cardPayments =
        Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(builder.cardPayments, "cardPayments")));
    this.movement = Objects.requireNonNull(builder.movement, "movement");
    this.biltRewardsAmount =
        builder.biltRewardsAmount == null ? BigDecimal.ZERO : builder.biltRewardsAmount;
    this.biltRewardsPayment = builder.biltRewardsPayment;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Amount the terminal authorized for this card payment. */
  public BigDecimal getApproved() {
    return approved;
  }

  /** Amount this card payment requested. */
  public BigDecimal getRequested() {
    return requested;
  }

  /** What this card payment left unpaid: {@link #getRequested()} minus {@link #getApproved()}. */
  public BigDecimal getShortfall() {
    return requested.subtract(approved);
  }

  /**
   * Everything the card phase still owes after this payment: the shortfall plus the card payments
   * still to come. An {@link PartialApprovalDecision#external(ExternalPayment)} decision must pay
   * exactly this amount.
   */
  public BigDecimal getAmountDue() {
    return amountDue;
  }

  /**
   * The card payments this settlement committed so far, in order, this partial one last unless it
   * was a Bilt rewards payment ({@link #isBiltRewards()}): rewards payments are not card payments
   * and are never listed. The handler can count them to limit how many cards a sale takes.
   */
  public List<CardPaymentResult> getCardPayments() {
    return cardPayments;
  }

  /** The {@code CARD_CHARGE} movement of this partial card payment. */
  public SettlementMovement getMovement() {
    return movement;
  }

  /**
   * The part of {@link #getApproved()} the terminal paid with Bilt rewards; zero when it was all
   * card. Equal to {@link #getApproved()} for a Bilt rewards payment.
   */
  public BigDecimal getBiltRewardsAmount() {
    return biltRewardsAmount;
  }

  /**
   * This partial payment when Bilt rewards paid all of it (the terminal's points leg, with no card
   * in it), else {@code null}.
   */
  public BiltRewardsPaymentResult getBiltRewardsPayment() {
    return biltRewardsPayment;
  }

  /** Whether this partial payment was a Bilt rewards payment rather than a card payment. */
  public boolean isBiltRewards() {
    return biltRewardsPayment != null;
  }

  @Override
  public String toString() {
    return "PartialCardApproval(approved="
        + approved
        + ", requested="
        + requested
        + ", amountDue="
        + amountDue
        + ", cardPayments="
        + cardPayments.size()
        + (isBiltRewards() ? ", biltRewards" : "")
        + ")";
  }

  public static final class Builder {
    private BigDecimal approved;
    private BigDecimal requested;
    private BigDecimal amountDue;
    private List<CardPaymentResult> cardPayments = List.of();
    private SettlementMovement movement;
    private BigDecimal biltRewardsAmount;
    private BiltRewardsPaymentResult biltRewardsPayment;

    private Builder() {}

    public Builder approved(BigDecimal approved) {
      this.approved = approved;
      return this;
    }

    public Builder requested(BigDecimal requested) {
      this.requested = requested;
      return this;
    }

    public Builder amountDue(BigDecimal amountDue) {
      this.amountDue = amountDue;
      return this;
    }

    public Builder cardPayments(List<CardPaymentResult> cardPayments) {
      this.cardPayments = cardPayments;
      return this;
    }

    public Builder movement(SettlementMovement movement) {
      this.movement = movement;
      return this;
    }

    public Builder biltRewardsAmount(BigDecimal biltRewardsAmount) {
      this.biltRewardsAmount = biltRewardsAmount;
      return this;
    }

    public Builder biltRewardsPayment(BiltRewardsPaymentResult biltRewardsPayment) {
      this.biltRewardsPayment = biltRewardsPayment;
      return this;
    }

    public PartialCardApproval build() {
      return new PartialCardApproval(this);
    }
  }
}
