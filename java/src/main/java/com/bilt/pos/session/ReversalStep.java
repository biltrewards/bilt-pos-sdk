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

/**
 * One reversible leg of a sale, as reported to {@link ReversalFlow#onError} when a reversal step
 * fails. The step names the leg, not the wire verb — which operation was running is already known
 * from the flow the handler is registered on.
 *
 * <p>A void reverses the known legs in this order: {@link #STORED_VALUE_LOAD}, {@link #CARD},
 * {@link #BILT_REWARDS}, {@link #STORED_VALUE}, {@link #REDEMPTION}, {@link #REBATE}, {@link
 * #AWARD}. A refund flow has at most three: {@link #CARD} (the tender refund), {@link
 * #BILT_REWARDS} and {@link #AWARD}.
 */
public enum ReversalStep {

  /** A stored value activation or load fulfilled for a purchased basket line. */
  STORED_VALUE_LOAD,

  /**
   * The card payment leg. A void reverses it (Nexo {@code ReversalRequest} — full, pre-settlement);
   * a refund returns money against it (Nexo {@code PaymentRequest} of type {@code Refund} —
   * amount-based and repeatable, and the leg an unlinked refund's money goes back to).
   */
  CARD,

  /**
   * A Bilt rewards payment: a payment the terminal paid entirely with Bilt rewards (implicit
   * loyalty, {@code biltRewardsAmount} equal to its authorized amount). It is reversed and refunded
   * with the same Nexo requests as {@link #CARD}, by its POI reference; the terminal returns the
   * rewards. A card payment that rewards paid only part of stays a {@link #CARD} leg.
   */
  BILT_REWARDS,

  /** The stored value (gift card) leg of a split tender (Nexo {@code ReversalRequest}). */
  STORED_VALUE,

  /**
   * The committed point/reward redemption (Nexo {@code LoyaltyRequest} {@code RedemptionRefund}).
   */
  REDEMPTION,

  /** The committed rebate redemption (Nexo {@code LoyaltyRequest} {@code RebateRefund}). */
  REBATE,

  /** The loyalty award (Nexo {@code LoyaltyRequest} {@code AwardRefund}). */
  AWARD
}
