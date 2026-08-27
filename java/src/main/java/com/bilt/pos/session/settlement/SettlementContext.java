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

import com.bilt.pos.session.basket.Basket;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Context handed to the {@code beforeStep} handler before each payment step.
 */
public final class SettlementContext {

    private final SettlementStep step;
    private final Basket currentBasket;
    private final BigDecimal currentTotal;
    private final String defaultTransactionId;
    private final List<CommittedStep> priorSteps;

    public SettlementContext(SettlementStep step, Basket currentBasket,
                              BigDecimal currentTotal, String defaultTransactionId,
                              List<CommittedStep> priorSteps) {
        this.step = step;
        this.currentBasket = currentBasket;
        this.currentTotal = currentTotal;
        this.defaultTransactionId = defaultTransactionId;
        this.priorSteps = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(priorSteps, "priorSteps")));
    }

    /**
     * Resolves the {@code SaleTransactionID} for a settlement step using the
     * same contract as {@code SettlementFlow.beforeStep}: generate a fresh
     * default ID, call the handler when one is registered, and fall back to
     * the default when the handler returns {@code null} or an empty string.
     */
    public static String resolveSaleTransactionId(SettlementStep step,
            Basket currentBasket, BigDecimal currentTotal, List<CommittedStep> priorSteps,
            Function<SettlementContext, String> handler) {
        String defaultTransactionId = UUID.randomUUID().toString();
        if (handler == null) {
            return defaultTransactionId;
        }
        String transactionId = handler.apply(new SettlementContext(step, currentBasket,
                currentTotal, defaultTransactionId, priorSteps));
        return transactionId != null && !transactionId.isEmpty()
                ? transactionId : defaultTransactionId;
    }

    /** The step about to run. */
    public SettlementStep getStep() {
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
