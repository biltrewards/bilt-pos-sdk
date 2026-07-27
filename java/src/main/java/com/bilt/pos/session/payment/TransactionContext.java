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
 * Context handed to the {@code beforeStep} handler before each payment step.
 */
public final class TransactionContext {

    private final TransactionStep step;
    private final Basket currentBasket;
    private final BigDecimal currentTotal;
    private final String defaultTransactionId;
    private final List<CommittedStep> priorSteps;

    public TransactionContext(TransactionStep step, Basket currentBasket,
                              BigDecimal currentTotal, String defaultTransactionId,
                              List<CommittedStep> priorSteps) {
        this.step = step;
        this.currentBasket = currentBasket;
        this.currentTotal = currentTotal;
        this.defaultTransactionId = defaultTransactionId;
        this.priorSteps = Collections.unmodifiableList(priorSteps);
    }

    /** The step about to run. */
    public TransactionStep getStep() {
        return step;
    }

    /** The basket as of this step (rebates applied once committed). */
    public Basket getCurrentBasket() {
        return currentBasket;
    }

    /** The running total the step will be sent with. */
    public BigDecimal getCurrentTotal() {
        return currentTotal;
    }

    /** The {@code SaleTransactionID} used if the handler returns nothing else. */
    public String getDefaultTransactionId() {
        return defaultTransactionId;
    }

    /** Steps committed so far in this payment. */
    public List<CommittedStep> getPriorSteps() {
        return priorSteps;
    }
}
