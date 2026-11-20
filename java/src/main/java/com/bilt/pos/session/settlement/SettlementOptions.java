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

import com.bilt.pos.display.DisplayPayload;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The register's complete plan for resolving a basket during settlement.
 *
 * <p>The basket declares commercial obligations. These options supply the execution choices that
 * cannot be inferred from it: how the aggregate return value is allocated and which referenced sale
 * lines receive stored value fulfillment. Retry decisions are deliberately separate in {@link
 * SettlementRecovery} so recovery never replaces or drops these instructions.
 */
public final class SettlementOptions {

  private final SettlementLoyalty loyalty;
  private final BigDecimal cashback;
  private final List<CardPayment> cardPayments;
  private final DisplayPayload paymentProcessingDisplay;
  private final List<RefundAllocation> refunds;
  private final List<StoredValueLoad> fulfillments;
  private final SettlementType settlementType;

  private SettlementOptions(Builder builder) {
    this.loyalty = builder.loyalty;
    this.cashback = builder.cashback;
    this.cardPayments = Collections.unmodifiableList(new ArrayList<>(builder.cardPayments));
    this.paymentProcessingDisplay = builder.paymentProcessingDisplay;
    this.refunds = Collections.unmodifiableList(new ArrayList<>(builder.refunds));
    this.fulfillments = Collections.unmodifiableList(new ArrayList<>(builder.fulfillments));
    this.settlementType = builder.settlementType;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Standard options: loyalty enabled, no cashback, refunds, or fulfillments. */
  public static SettlementOptions defaults() {
    return builder().build();
  }

  /** Who handles loyalty; default {@link SettlementLoyalty#explicit()}. */
  public SettlementLoyalty getLoyalty() {
    return loyalty;
  }

  /** Cashback to request with the card payment, or {@code null}. */
  public BigDecimal getCashback() {
    return cashback;
  }

  /**
   * The planned card payments, in order, each followed by the next and the last by one more card
   * payment for the remainder. Empty (the default) means one card payment for the whole card
   * amount.
   */
  public List<CardPayment> getCardPayments() {
    return cardPayments;
  }

  /** Display shown while the card payment is processing, or {@code null}. */
  public DisplayPayload getPaymentProcessingDisplay() {
    return paymentProcessingDisplay;
  }

  /** Allocations resolving the basket's aggregate return target. */
  public List<RefundAllocation> getRefunds() {
    return refunds;
  }

  /** Stored value operations fulfilling referenced basket lines. */
  public List<StoredValueLoad> getFulfillments() {
    return fulfillments;
  }

  /** How a mixed sale/return basket moves money. */
  public SettlementType getSettlementType() {
    return settlementType;
  }

  /** Builder for {@link SettlementOptions}. */
  public static final class Builder {

    private SettlementLoyalty loyalty = SettlementLoyalty.explicit();
    private BigDecimal cashback;
    private List<CardPayment> cardPayments = new ArrayList<>();
    private DisplayPayload paymentProcessingDisplay;
    private List<RefundAllocation> refunds = new ArrayList<>();
    private List<StoredValueLoad> fulfillments = new ArrayList<>();
    private SettlementType settlementType = SettlementType.REFUND_THEN_CHARGE;

    private Builder() {}

    /**
     * Who handles loyalty: the SDK's loyalty steps, the terminal during the card payment, or
     * nobody. Default {@link SettlementLoyalty#explicit()}.
     */
    public Builder loyalty(SettlementLoyalty loyalty) {
      this.loyalty = Objects.requireNonNull(loyalty, "loyalty");
      return this;
    }

    /** Requests positive cashback in addition to the card charge. */
    public Builder cashback(BigDecimal cashback) {
      if (cashback != null && cashback.signum() <= 0) {
        throw new IllegalArgumentException("cashback must be positive");
      }
      this.cashback = cashback;
      return this;
    }

    /**
     * Splits the card amount into several card payments planned up front: each given payment
     * charges its fixed amount on its own card, in order, and one more card payment always follows
     * for the remainder. {@code cardPayments(CardPayment.of(new BigDecimal("20.00")))} charges $20
     * on the first card and the rest on the next.
     *
     * <p>The card amount is known only when the card step starts, after rebates, points and stored
     * value. The planned amounts must then add up to less than it, or the card step fails before
     * any card is charged, with {@link
     * com.bilt.pos.session.SessionErrorCode#CARD_PAYMENTS_EXCEED_TOTAL}. Every card payment after
     * the first is sent as a nexo split payment. A split cannot carry cashback, and needs the SDK's
     * own loyalty steps ({@link SettlementLoyalty#explicit()}, the default, or {@link
     * SettlementLoyalty#forbidden()}): {@link #build()} refuses it with cashback or with {@link
     * SettlementLoyalty#implicit()}. No card payments (the default) means one card payment for the
     * whole card amount.
     */
    public Builder cardPayments(CardPayment... cardPayments) {
      return cardPayments(cardPayments == null ? null : java.util.Arrays.asList(cardPayments));
    }

    /** Replaces the planned card payments; see {@link #cardPayments(CardPayment...)}. */
    public Builder cardPayments(List<CardPayment> cardPayments) {
      List<CardPayment> planned = new ArrayList<>();
      if (cardPayments != null) {
        for (CardPayment cardPayment : cardPayments) {
          planned.add(Objects.requireNonNull(cardPayment, "cardPayment"));
        }
      }
      this.cardPayments = planned;
      return this;
    }

    /** Overrides the display shown while the card payment is processing. */
    public Builder paymentProcessingDisplay(DisplayPayload paymentProcessingDisplay) {
      this.paymentProcessingDisplay = paymentProcessingDisplay;
      return this;
    }

    /** Replaces the allocations resolving the basket's return target. */
    public Builder refunds(List<RefundAllocation> refunds) {
      this.refunds = refunds == null ? new ArrayList<>() : new ArrayList<>(refunds);
      return this;
    }

    /** Adds one register-selected refund allocation. */
    public Builder addRefund(RefundAllocation refund) {
      this.refunds.add(Objects.requireNonNull(refund, "refund"));
      return this;
    }

    /** Replaces the stored value line fulfillments. */
    public Builder fulfillments(List<StoredValueLoad> fulfillments) {
      this.fulfillments = fulfillments == null ? new ArrayList<>() : new ArrayList<>(fulfillments);
      return this;
    }

    /** Adds one activation or reload instruction for a referenced basket line. */
    public Builder addFulfillment(StoredValueLoad fulfillment) {
      this.fulfillments.add(Objects.requireNonNull(fulfillment, "fulfillment"));
      return this;
    }

    /** Default {@link SettlementType#REFUND_THEN_CHARGE}. */
    public Builder settlementType(SettlementType settlementType) {
      this.settlementType = Objects.requireNonNull(settlementType, "settlementType");
      return this;
    }

    /**
     * Builds the options.
     *
     * @throws IllegalStateException when {@link #cardPayments} are planned together with {@link
     *     #cashback}, which rides on a single card authorization that a split doesn't have, or with
     *     {@link SettlementLoyalty#implicit()} loyalty. With implicit loyalty the terminal books
     *     the whole sale's earn when card payment 1 is approved, before the sale is paid, and keeps
     *     it if a later card payment fails and the sale is aborted; its hosted checkout can also
     *     lower card payment 1 below its planned amount. Use explicit loyalty, whose award runs
     *     once after every tender.
     */
    public SettlementOptions build() {
      if (cashback != null && !cardPayments.isEmpty()) {
        throw new IllegalStateException("cashback cannot be combined with planned card payments");
      }
      if (!cardPayments.isEmpty() && loyalty.getHandling() == SettlementLoyalty.Handling.IMPLICIT) {
        throw new IllegalStateException(
            "planned card payments cannot use implicit loyalty: the terminal would book the whole"
                + " sale's earn at card payment 1, before the sale is paid; use"
                + " SettlementLoyalty.explicit(), whose award runs once after every tender");
      }
      return new SettlementOptions(this);
    }
  }
}
