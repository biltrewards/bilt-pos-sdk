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
 * One reversible leg of a sale, as reported to
 * {@link ReversalFlow#onError} when a reversal step fails. The step names
 * the leg, not the wire verb — which operation was running is already
 * known from the flow the handler is registered on.
 *
 * <p>A void — {@code CheckoutSession.voidTransaction()} for the session's
 * own payment, {@code ReversalSession.voidTransaction()} for a referenced
 * prior sale — reverses the known legs in this order: {@link #CARD},
 * {@link #STORED_VALUE}, {@link #REDEMPTION}, {@link #REBATE},
 * {@link #AWARD}. A refund flow has at most two steps: {@link #CARD} (the
 * tender refund) and {@link #AWARD}.</p>
 */
public enum ReversalStep {

    /**
     * The card payment leg. A void reverses it (Nexo
     * {@code ReversalRequest} — full, pre-settlement); a refund returns
     * money against it (Nexo {@code PaymentRequest} of type {@code Refund}
     * — amount-based and repeatable, and the leg an unlinked refund's
     * money goes back to).
     */
    CARD,

    /**
     * The stored value (gift card) leg of a split tender (Nexo
     * {@code ReversalRequest}).
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
    AWARD
}
