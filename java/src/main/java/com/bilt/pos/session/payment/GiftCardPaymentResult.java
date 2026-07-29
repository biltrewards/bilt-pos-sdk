/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.payment;

import java.math.BigDecimal;

/**
 * Result of the stored value step, delivered to {@code onGiftCardPayment}.
 * The handler returns the total for the next step — typically
 * {@link #getSuggestedTotal()}. When the card balance was insufficient, the
 * charge is a partial authorization and {@code suggestedTotal} carries the
 * remainder for the card payment step.
 */
public final class GiftCardPaymentResult {

    private final BigDecimal amountCharged;
    private final BigDecimal remainingCardBalance;
    private final BigDecimal previousTotal;
    private final BigDecimal suggestedTotal;

    public GiftCardPaymentResult(BigDecimal amountCharged, BigDecimal remainingCardBalance,
                                 BigDecimal previousTotal, BigDecimal suggestedTotal) {
        this.amountCharged = amountCharged;
        this.remainingCardBalance = remainingCardBalance;
        this.previousTotal = previousTotal;
        this.suggestedTotal = suggestedTotal;
    }

    public BigDecimal getAmountCharged() {
        return amountCharged;
    }

    /** Balance left on the gift card, or {@code null} if not reported. */
    public BigDecimal getRemainingCardBalance() {
        return remainingCardBalance;
    }

    /** Running total before this step. */
    public BigDecimal getPreviousTotal() {
        return previousTotal;
    }

    /** {@code previousTotal − amountCharged}. */
    public BigDecimal getSuggestedTotal() {
        return suggestedTotal;
    }
}
