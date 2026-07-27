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
 * Result of the point/reward redemption step, delivered to
 * {@code onPointsRedeemed}. The handler returns the total for the next step —
 * typically {@link #getSuggestedTotal()}.
 */
public final class PointRedemptionResult {

    private final int pointsUsed;
    private final BigDecimal monetaryValue;
    private final BigDecimal previousTotal;
    private final BigDecimal suggestedTotal;
    private final int remainingPointBalance;

    public PointRedemptionResult(int pointsUsed, BigDecimal monetaryValue,
                                 BigDecimal previousTotal, BigDecimal suggestedTotal,
                                 int remainingPointBalance) {
        this.pointsUsed = pointsUsed;
        this.monetaryValue = monetaryValue;
        this.previousTotal = previousTotal;
        this.suggestedTotal = suggestedTotal;
        this.remainingPointBalance = remainingPointBalance;
    }

    public int getPointsUsed() {
        return pointsUsed;
    }

    /** Dollar value of the redeemed points/rewards. */
    public BigDecimal getMonetaryValue() {
        return monetaryValue;
    }

    /** Running total before this step. */
    public BigDecimal getPreviousTotal() {
        return previousTotal;
    }

    /** {@code previousTotal − monetaryValue}. */
    public BigDecimal getSuggestedTotal() {
        return suggestedTotal;
    }

    /** Member's point balance after the redemption; {@code 0} if not reported. */
    public int getRemainingPointBalance() {
        return remainingPointBalance;
    }
}
