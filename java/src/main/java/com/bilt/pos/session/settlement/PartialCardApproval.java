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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Context supplied to {@code SettlementFlow.onPartialCardApproval} when a card payment committed
 * with an authorized amount below the amount it requested. By then the payment's movement has been
 * published and {@code onCardCharged} has fired. The handler returns a {@link
 * PartialApprovalDecision} for the shortfall.
 */
public final class PartialCardApproval {

  private final BigDecimal approved;
  private final BigDecimal requested;
  private final BigDecimal amountDue;
  private final List<CardPaymentResult> cardPayments;
  private final SettlementMovement movement;

  private PartialCardApproval(Builder builder) {
    this.approved = Objects.requireNonNull(builder.approved, "approved");
    this.requested = Objects.requireNonNull(builder.requested, "requested");
    this.amountDue = Objects.requireNonNull(builder.amountDue, "amountDue");
    this.cardPayments =
        Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(builder.cardPayments, "cardPayments")));
    this.movement = Objects.requireNonNull(builder.movement, "movement");
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
   * The card payments this settlement committed so far, in order, this partial one last. The
   * handler can count them to limit how many cards a sale takes.
   */
  public List<CardPaymentResult> getCardPayments() {
    return cardPayments;
  }

  /** The {@code CARD_CHARGE} movement of this partial card payment. */
  public SettlementMovement getMovement() {
    return movement;
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
        + ")";
  }

  public static final class Builder {
    private BigDecimal approved;
    private BigDecimal requested;
    private BigDecimal amountDue;
    private List<CardPaymentResult> cardPayments = List.of();
    private SettlementMovement movement;

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

    public PartialCardApproval build() {
      return new PartialCardApproval(this);
    }
  }
}
