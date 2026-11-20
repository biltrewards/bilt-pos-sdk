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

/**
 * The register's answer to {@code SettlementFlow.onPartialCardApproval}: what to do about the
 * shortfall of a card payment that the terminal approved for less than it was asked for. The
 * partial card payment itself stays committed unless the decision is {@link #abort()}.
 *
 * <p>A decision the settlement cannot carry out ({@link #addToNext()} with no card payment left, an
 * {@link #external(ExternalPayment)} amount other than {@link PartialCardApproval#getAmountDue()})
 * is refused like an invalid {@code SettlementRecovery}: the settlement fails with {@code
 * INVALID_STATE}, and the committed steps stand until a retried {@code settle()} or {@code
 * voidTransaction()} reverses them.
 */
public final class PartialApprovalDecision {

  /** What the settlement does about the shortfall. */
  public enum Action {
    /** Charge the shortfall on another card, then continue the plan. */
    ANOTHER_CARD,
    /** Add the shortfall to the next card payment. */
    ADD_TO_NEXT,
    /** Record a register-managed payment for everything the card phase still owes. */
    EXTERNAL,
    /** Reverse every committed charge-side step and fail the settlement. */
    ABORT
  }

  private static final PartialApprovalDecision ANOTHER_CARD =
      new PartialApprovalDecision(Action.ANOTHER_CARD, null);
  private static final PartialApprovalDecision ADD_TO_NEXT =
      new PartialApprovalDecision(Action.ADD_TO_NEXT, null);
  private static final PartialApprovalDecision ABORT =
      new PartialApprovalDecision(Action.ABORT, null);

  private final Action action;
  private final ExternalPayment externalPayment;

  private PartialApprovalDecision(Action action, ExternalPayment externalPayment) {
    this.action = action;
    this.externalPayment = externalPayment;
  }

  /**
   * Charge the shortfall as a new card payment right after this one, then continue with the planned
   * payments. On the last card payment of the settlement this is the way to collect the shortfall
   * on one more card.
   */
  public static PartialApprovalDecision anotherCard() {
    return ANOTHER_CARD;
  }

  /**
   * Add the shortfall to the next card payment: the next planned amount, or the remainder payment
   * when no planned amount is left. Valid only while a card payment is left after this one; on the
   * last card payment of the settlement it is refused, and {@link #anotherCard()} charges the
   * shortfall on one more card instead.
   */
  public static PartialApprovalDecision addToNext() {
    return ADD_TO_NEXT;
  }

  /**
   * Record a register-managed payment, such as cash, for everything the card phase still owes, and
   * end the card phase. Every committed card payment stays. The amount must equal {@link
   * PartialCardApproval#getAmountDue()}. The payment is recorded as an {@code EXTERNAL_PAYMENT}
   * movement, as with {@code SettlementRecovery.external}.
   */
  public static PartialApprovalDecision external(ExternalPayment payment) {
    if (payment == null) {
      throw new NullPointerException("payment");
    }
    return new PartialApprovalDecision(Action.EXTERNAL, payment);
  }

  /**
   * Reverse every committed charge-side step, this partial card payment and the earlier card
   * payments included, and fail the settlement as {@code DECLINED}: the same unwind as {@code
   * SettlementRecovery.abort()}. Card brand rules require a partial approval that the sale does not
   * complete to be reversed, so this is the answer when the shopper cannot pay the shortfall.
   */
  public static PartialApprovalDecision abort() {
    return ABORT;
  }

  public Action getAction() {
    return action;
  }

  /** Register-managed payment; non-null only for {@link Action#EXTERNAL}. */
  public ExternalPayment getExternalPayment() {
    return externalPayment;
  }

  @Override
  public String toString() {
    return externalPayment == null
        ? "PartialApprovalDecision(" + action + ")"
        : "PartialApprovalDecision(" + action + ", " + externalPayment.getAmount() + ")";
  }
}
