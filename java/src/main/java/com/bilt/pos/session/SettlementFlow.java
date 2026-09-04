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

import com.bilt.pos.session.payment.GiftCardPaymentResult;
import com.bilt.pos.session.payment.PointRedemptionResult;
import com.bilt.pos.session.payment.RebateRedemptionResult;
import com.bilt.pos.session.settlement.AbandonedSettlementRecord;
import com.bilt.pos.session.settlement.SettlementContext;
import com.bilt.pos.session.settlement.SettlementFailure;
import com.bilt.pos.session.settlement.SettlementMovement;
import com.bilt.pos.session.settlement.SettlementRecovery;
import com.bilt.pos.session.settlement.SettlementResult;
import com.bilt.pos.session.settlement.SettlementStep;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The settlement orchestration chain returned by {@code CheckoutSession.settle()}.
 *
 * <p>Registering handlers sends nothing. With the default settlement option,
 * the sequence is refund allocations (including external register-managed
 * refunds), rebate redemption, point redemption, stored value tender, card charge,
 * stored value line fulfillment, and award. With net settlement, only the signed basket difference is charged or
 * refunded. The sequence runs when
 * {@link #execute()} (asynchronously, on the session's operation thread),
 * {@link #executeSync()} (blocking), {@link #get()}, or {@link #getOrNull()}
 * is invoked. Each step handler receives that step's result and returns the
 * updated total for the next step (letting the register recompute tax on
 * discounted amounts).</p>
 *
 * <pre>{@code
 * session.settle()
 *     .onRebatesRedeemed(rebates -> rebates.getSuggestedTotal())
 *     .onPointsRedeemed(points -> points.getSuggestedTotal())
 *     .onSuccess(result -> register.printReceipt(result.getMerchantReceipt()))
 *     .onError(error -> SettlementRecovery.abort())
 *     .execute();
 * }</pre>
 *
 * <p>With {@link #execute()}, every handler — the step handlers,
 * {@code onSuccess}, {@code onError}, and {@code onComplete} — is delivered
 * through the callback executor (the session builder's
 * {@code callbackExecutor}, or the per-flow {@link #callbackOn(Executor)}
 * override), and the settlement thread <em>waits for each answer</em>: the
 * handlers are part of the settlement negotiation, so their return values
 * steer the sequence exactly as if they ran inline — they just physically
 * run on the integrator's thread. Keep them quick (settlement is paused
 * while they run), and never block a callback thread on this flow's
 * {@code get()} — the flow would be waiting on that thread's handler while
 * it waits on the flow. Without a callback executor, handlers run directly
 * on the thread executing the sequence.</p>
 *
 * <p>If a charge-side terminal request has an indeterminate outcome, the SDK
 * first resolves it through TransactionStatus. A recovered success continues
 * without calling {@code onError}; a recovered failure or authoritative
 * not-found result is reported as definitive. {@code onError} also receives an
 * indeterminate failure when TransactionStatus itself cannot resolve the request.
 * Its {@link SettlementRecovery} retries only the failed step, skips an optional
 * step, records an external final tender, aborts and unwinds, or abandons the
 * partial flow to the register. Earlier successful steps remain committed unless
 * the register returns {@link SettlementRecovery#abort()}. Refund allocation
 * failures are different:
 * they are not retried in the same run, and committed refund allocations are
 * not unwound. Retry by calling {@code settle()} again with the same committed
 * allocation prefix. The default charge-side policy (no handler) is
 * {@code abort()}.</p>
 *
 * <p>Movement callbacks are immediate observations, not the authoritative
 * final ledger. An abort may reverse charge-side movements, and retrying,
 * skipping, or replacing a partially committed failed step first reverses
 * only that step's partial movements. Those reversals do not emit compensating
 * movement callbacks. Refund allocation movements are durable because
 * settlement never unwinds them. Treat
 * {@link SettlementResult#getMovements()} from a successful result as the
 * final movement ledger.</p>
 */
public final class SettlementFlow extends SessionFlow<SettlementResult> {

    private final Function<SettlementFlow, SettlementResult> executor;

    private Function<SettlementContext, String> beforeStepHandler;
    private Function<RebateRedemptionResult, BigDecimal> rebatesHandler;
    private Function<PointRedemptionResult, BigDecimal> pointsHandler;
    private Function<GiftCardPaymentResult, BigDecimal> giftCardHandler;
    private Consumer<SettlementMovement> cardChargeHandler;
    private Consumer<SettlementMovement> externalPaymentHandler;
    private Consumer<SettlementMovement> awardHandler;
    private Consumer<SettlementMovement> storedValueLoadedHandler;
    private Consumer<SettlementMovement> cardRefundHandler;
    private Consumer<SettlementMovement> giftCardRefundHandler;
    private Consumer<SettlementMovement> externalRefundHandler;
    private Consumer<SettlementMovement> pointsRefundHandler;
    private Consumer<SettlementMovement> rebateRefundHandler;
    private Consumer<SettlementMovement> awardRefundHandler;
    private Consumer<SettlementMovement> movementHandler;
    private Function<SettlementFailure, SettlementRecovery> errorHandler;
    private Consumer<AbandonedSettlementRecord> abandonedHandler;

    private boolean errorHandlerConsulted;
    private boolean recoveryRequested;

    SettlementFlow(Function<SettlementFlow, SettlementResult> executor) {
        super("the settlement");
        this.executor = executor;
    }

    /** Session wiring: execution and default callback delivery. Applied
     *  by {@code settle()} — before user code can call {@link #callbackOn}. */
    SettlementFlow session(SessionOperations session) {
        attach(session);
        return this;
    }

    /**
     * Delivers this flow's handlers through {@code executor} instead of the
     * session's default callback executor. Affects {@link #execute()} only —
     * the synchronous paths always dispatch on the calling thread.
     */
    public SettlementFlow callbackOn(Executor executor) {
        Objects.requireNonNull(executor, "executor");
        return register(() -> overrideCallback(executor));
    }

    /**
     * Called before each step; returns the {@code SaleTransactionID} to use.
     * Default: a fresh UUID per step.
     */
    public SettlementFlow beforeStep(Function<SettlementContext, String> handler) {
        return register(() -> this.beforeStepHandler = requireHandler(handler));
    }

    /**
     * Called when rebates/coupons are committed. Returns the updated total
     * for the next step. Default: accept, {@code previousTotal − rebates}.
     */
    public SettlementFlow onRebatesRedeemed(Function<RebateRedemptionResult, BigDecimal> handler) {
        return register(() -> this.rebatesHandler = requireHandler(handler));
    }

    /**
     * Called when points/rewards are redeemed for monetary value. Returns
     * the updated total. Default: accept, subtract the monetary value.
     */
    public SettlementFlow onPointsRedeemed(Function<PointRedemptionResult, BigDecimal> handler) {
        return register(() -> this.pointsHandler = requireHandler(handler));
    }

    /**
     * Called when the stored value card is charged. Returns the updated
     * total. Default: accept, subtract the charged amount.
     */
    public SettlementFlow onGiftCardPayment(Function<GiftCardPaymentResult, BigDecimal> handler) {
        return register(() -> this.giftCardHandler = requireHandler(handler));
    }

    /**
     * Called when the card charge leg commits. See {@link #onMovement} for
     * the provisional charge-side callback contract.
     */
    public SettlementFlow onCardCharged(Consumer<SettlementMovement> handler) {
        return register(() -> this.cardChargeHandler = requireHandler(handler));
    }

    /** Called when a register-managed payment such as cash is recorded. */
    public SettlementFlow onExternallyPaid(Consumer<SettlementMovement> handler) {
        return register(() -> this.externalPaymentHandler = requireHandler(handler));
    }

    /**
     * Called when the loyalty award leg commits. See {@link #onMovement} for
     * the provisional charge-side callback contract.
     */
    public SettlementFlow onAwarded(Consumer<SettlementMovement> handler) {
        return register(() -> this.awardHandler = requireHandler(handler));
    }

    /** Called when a purchased stored value line is activated or loaded. */
    public SettlementFlow onStoredValueLoaded(Consumer<SettlementMovement> handler) {
        return register(() -> this.storedValueLoadedHandler = requireHandler(handler));
    }

    /** Called when a card refund allocation commits. */
    public SettlementFlow onCardRefunded(Consumer<SettlementMovement> handler) {
        return register(() -> this.cardRefundHandler = requireHandler(handler));
    }

    /** Called when a stored value refund allocation commits. */
    public SettlementFlow onGiftCardRefunded(Consumer<SettlementMovement> handler) {
        return register(() -> this.giftCardRefundHandler = requireHandler(handler));
    }

    /** Called when an external refund allocation is recorded. */
    public SettlementFlow onExternalRefunded(Consumer<SettlementMovement> handler) {
        return register(() -> this.externalRefundHandler = requireHandler(handler));
    }

    /** Called when a point/reward redemption refund allocation commits. */
    public SettlementFlow onPointsRefunded(Consumer<SettlementMovement> handler) {
        return register(() -> this.pointsRefundHandler = requireHandler(handler));
    }

    /** Called when a rebate/coupon refund allocation commits. */
    public SettlementFlow onRebateRefunded(Consumer<SettlementMovement> handler) {
        return register(() -> this.rebateRefundHandler = requireHandler(handler));
    }

    /** Called when an award refund allocation commits. */
    public SettlementFlow onAwardRefunded(Consumer<SettlementMovement> handler) {
        return register(() -> this.awardRefundHandler = requireHandler(handler));
    }

    /**
     * Called after any externally visible money or loyalty movement commits.
     *
     * <p>Charge-side movement callbacks are provisional: an abort can unwind
     * them, and retrying, skipping, or replacing a partially committed failed
     * step can reverse that step's movements without emitting a compensating
     * callback. Earlier successful steps remain committed. Refund allocation
     * movements are not unwound. Use
     * {@link SettlementResult#getMovements()} from the successful result as
     * the authoritative final ledger.</p>
     */
    public SettlementFlow onMovement(Consumer<SettlementMovement> handler) {
        return register(() -> this.movementHandler = requireHandler(handler));
    }

    /** Called after the full sequence completes successfully. */
    public SettlementFlow onSuccess(Consumer<SettlementResult> handler) {
        return register(() -> successHandler(requireHandler(handler)));
    }

    /**
     * Called when a charge-side step definitively fails, or when automatic
     * TransactionStatus recovery cannot resolve an indeterminate terminal request.
     * A recovered successful request continues without invoking this handler.
     * The returned {@link SettlementRecovery} retries recovery, skips the failed
     * step, records an external tender, aborts with unwind, or abandons recovery
     * to the register. For an indeterminate failure delivered here, retry checks
     * TransactionStatus again; skip and external replacement are invalid until
     * the original request is resolved.
     * For refund allocation failures, the handler is a terminal failure
     * notification: the returned recovery decision is ignored, committed refund
     * allocations stay recorded, and the register retries with the same
     * committed allocation prefix. Default charge-side policy:
     * {@code abort()}.
     */
    public SettlementFlow onError(Function<SettlementFailure, SettlementRecovery> handler) {
        return register(() -> this.errorHandler = requireHandler(handler));
    }

    /** Called when {@link SettlementRecovery#abandon()} transfers recovery ownership. */
    public SettlementFlow onAbandoned(Consumer<AbandonedSettlementRecord> handler) {
        return register(() -> this.abandonedHandler = requireHandler(handler));
    }

    /**
     * Registers a hook that runs exactly once after settlement completes, on
     * every path — success, failure, unexpected exception, or rejection
     * because the session ended before settlement could run. The place for
     * cleanup that must not leak. A throwing hook is logged, never
     * propagated.
     */
    public SettlementFlow onComplete(Runnable handler) {
        return register(() -> completeHandler(requireHandler(handler)));
    }

    // ─── SessionFlow hooks ───

    @Override
    SettlementResult runBody() {
        return executor.apply(this);
    }

    @Override
    void deliverFailure(SessionException failure) {
        if (failure.getAbandonedSettlement() != null) {
            if (abandonedHandler != null) {
                awaitHandlerRun(() -> abandonedHandler.accept(
                        failure.getAbandonedSettlement()));
            }
            return;
        }
        // orchestration consults onError with the failure it throws, but
        // two outcomes would otherwise end in silence for execute()-style
        // registers: failures before the sequence starts (a state
        // rejection, a failed standing-movement drain) never reach the
        // handler at all, and a consultation whose recovery resolution was
        // refused (for example, an invalid skip or incomplete step reset)
        // leaves the register
        // believing recovery is underway. Both are delivered here; the
        // returned recovery decision is ignored — there is nothing left to resolve.
        // ABORTED outcomes stay bypassed by design: the register initiated
        // the abort, and it must not surface as a failure to resolve.
        if (errorHandler != null
                && (!errorHandlerConsulted || recoveryRequested)
                && failure.getError().getCode() != SessionErrorCode.ABORTED) {
            SettlementFailure context = terminalFailure(failure.getError());
            awaitHandlerCall(() -> errorHandler.apply(context));
        }
    }

    @Override
    void notifyRejection(SessionError error) {
        if (errorHandler != null) {
            errorHandler.apply(terminalFailure(error));
        }
    }

    private SettlementFlow register(Runnable assignment) {
        guardRegistration();
        assignment.run();
        return this;
    }

    private static <T> T requireHandler(T handler) {
        return Objects.requireNonNull(handler, "handler");
    }

    // ─── SDK-internal accessors ───
    // Each handler is wrapped so its invocations — from the orchestrator,
    // mid-sequence, on the operation thread — deliver on the callback
    // executor and wait for the answer (see the class docs).

    Function<SettlementContext, String> beforeStepHandler() {
        return marshalled(beforeStepHandler);
    }

    Function<RebateRedemptionResult, BigDecimal> rebatesHandler() {
        return marshalled(rebatesHandler);
    }

    Function<PointRedemptionResult, BigDecimal> pointsHandler() {
        return marshalled(pointsHandler);
    }

    Function<GiftCardPaymentResult, BigDecimal> giftCardHandler() {
        return marshalled(giftCardHandler);
    }

    Consumer<SettlementMovement> movementHandler() {
        if (movementHandler == null
                && cardChargeHandler == null
                && externalPaymentHandler == null
                && awardHandler == null
                && storedValueLoadedHandler == null
                && cardRefundHandler == null
                && giftCardRefundHandler == null
                && externalRefundHandler == null
                && pointsRefundHandler == null
                && rebateRefundHandler == null
                && awardRefundHandler == null) {
            return null;
        }
        return movement -> awaitHandlerRun(() -> {
            if (movementHandler != null) {
                movementHandler.accept(movement);
            }
            Consumer<SettlementMovement> specific = specificMovementHandler(movement.getStep());
            if (specific != null) {
                specific.accept(movement);
            }
        });
    }

    Function<SettlementFailure, SettlementRecovery> errorHandler() {
        if (errorHandler == null) {
            return null;
        }
        // the flag mutations ride inside the marshalled call, so the
        // awaited hand-off publishes them back to the operation thread
        return marshalled(failure -> {
            errorHandlerConsulted = true;
            SettlementRecovery resolution = errorHandler.apply(failure);
            // a nonterminal answer obliges us to tell the register if settlement
            // ends in failure anyway (see deliverFailure)
            recoveryRequested = resolution != null
                    && resolution.getAction() != SettlementRecovery.Action.ABORT
                    && resolution.getAction() != SettlementRecovery.Action.ABANDON;
            return resolution;
        });
    }

    private static SettlementFailure terminalFailure(SessionError error) {
        return SettlementFailure.builder()
                .error(error)
                .amountDue(BigDecimal.ZERO)
                .committedMovements(List.of())
                .outcomeCertainty(SettlementFailure.OutcomeCertainty.DEFINITIVE)
                .build();
    }

    private Consumer<SettlementMovement> specificMovementHandler(SettlementStep step) {
        switch (step) {
            case CARD_CHARGE:
                return cardChargeHandler;
            case EXTERNAL_PAYMENT:
                return externalPaymentHandler;
            case AWARD:
                return awardHandler;
            case STORED_VALUE_LOAD:
                return storedValueLoadedHandler;
            case CARD_REFUND:
                return cardRefundHandler;
            case STORED_VALUE_REFUND:
                return giftCardRefundHandler;
            case EXTERNAL_REFUND:
                return externalRefundHandler;
            case POINT_REDEMPTION_REFUND:
                return pointsRefundHandler;
            case REBATE_REFUND:
                return rebateRefundHandler;
            case AWARD_REFUND:
                return awardRefundHandler;
            default:
                return null;
        }
    }
}
