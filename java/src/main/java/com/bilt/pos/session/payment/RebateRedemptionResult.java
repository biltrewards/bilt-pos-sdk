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

import com.bilt.pos.session.basket.Basket;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Result of the rebate step, delivered to {@code onRebatesRedeemed}. The
 * handler returns the total for the next step — typically
 * {@link #getSuggestedTotal()}, adjusted when tax must be recomputed on the
 * discounted amount.
 */
public final class RebateRedemptionResult {

    private final List<RedeemedRebate> rebates;
    private final BigDecimal totalRebateAmount;
    private final BigDecimal previousTotal;
    private final BigDecimal suggestedTotal;
    private final Basket updatedBasket;

    public RebateRedemptionResult(List<RedeemedRebate> rebates, BigDecimal totalRebateAmount,
                                  BigDecimal previousTotal, BigDecimal suggestedTotal,
                                  Basket updatedBasket) {
        this.rebates = Collections.unmodifiableList(rebates);
        this.totalRebateAmount = totalRebateAmount;
        this.previousTotal = previousTotal;
        this.suggestedTotal = suggestedTotal;
        this.updatedBasket = updatedBasket;
    }

    /** The rebates that were applied. */
    public List<RedeemedRebate> getRebates() {
        return rebates;
    }

    /** Sum of the applied rebate values. */
    public BigDecimal getTotalRebateAmount() {
        return totalRebateAmount;
    }

    /** Running total before this step. */
    public BigDecimal getPreviousTotal() {
        return previousTotal;
    }

    /** {@code previousTotal − totalRebateAmount}. */
    public BigDecimal getSuggestedTotal() {
        return suggestedTotal;
    }

    /** Basket snapshot with the rebates applied per line. */
    public Basket getUpdatedBasket() {
        return updatedBasket;
    }
}
