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
 * One reversible movement of a sale, as reported to
 * {@link ReversalFlow#onError} when its reversal fails.
 *
 * <p>A void — {@code CheckoutSession.voidTransaction()} for the session's
 * own payment, {@code ReversalSession.voidTransaction()} for a referenced
 * prior sale — reverses the known movements in this order: {@link #CARD},
 * {@link #STORED_VALUE}, {@link #REDEMPTION}, {@link #REBATE},
 * {@link #AWARD}. A refund flow has at most two steps: {@link #REFUND} and
 * {@link #AWARD}.</p>
 */
public enum ReversalStep {

    /** The card payment leg (Nexo {@code ReversalRequest}). */
    CARD,

    /**
     * The stored value (gift card) leg of a split tender
     * (Nexo {@code ReversalRequest}).
     */
    STORED_VALUE,

    /**
     * The committed point/reward redemption
     * (Nexo {@code LoyaltyRequest} {@code RedemptionRefund}).
     */
    REDEMPTION,

    /**
     * The committed rebate redemption
     * (Nexo {@code LoyaltyRequest} {@code RebateRefund}).
     */
    REBATE,

    /**
     * The loyalty award
     * (Nexo {@code LoyaltyRequest} {@code AwardRefund}).
     */
    AWARD,

    /**
     * The tender refund of a {@code refund()} flow
     * (Nexo {@code PaymentRequest} of type {@code Refund}).
     */
    REFUND
}
