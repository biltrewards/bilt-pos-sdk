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

import com.bilt.pos.session.settlement.SettlementResult;
import com.bilt.pos.session.payment.GiftCardPaymentResult;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.bilt.pos.session.payment.PointRedemptionResult;
import com.bilt.pos.session.payment.RebateRedemptionResult;
import com.bilt.pos.session.settlement.SettlementContext;
import com.bilt.pos.session.settlement.SettlementMovement;
import com.bilt.pos.session.settlement.SettlementStep;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The settlement orchestration chain returned by {@code CheckoutSession.settle()}.
 *
 * <p>Registering handlers sends nothing; the sequence — refund allocations
 * (including external register-managed refunds), rebate redemption, point
 * redemption, stored value, card charge, award — runs when
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
 *     .onError(error -> SettlementOptions.voidAndAbort())
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
 * <p>If a charge-side step fails after refund allocations, the {@code onError}
 * handler decides how to recover via the {@link SettlementOptions} it returns;
 * committed charge-side steps are reversed before a retry or failure. Refund
 * allocation failures are different: they are not retried in the same run, and
 * committed refund allocations are not unwound. The session moves to
 * {@code FAILED}; retry by calling {@code settle()} again with the same
 * committed allocation prefix. The default charge-side policy (no handler) is
 * {@link SettlementOptions#voidAndAbort()}.</p>
 *
 * <p>Movement callbacks are immediate observations, not the authoritative
 * final ledger. Charge-side movements may be reversed by a later same-run
 * unwind before the settlement retries or fails, and that unwind does not
 * emit compensating movement callbacks. Refund allocation movements are
 * durable because settlement never unwinds them. Treat
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
    private Consumer<SettlementMovement> awardHandler;
    private Consumer<SettlementMovement> cardRefundHandler;
    private Consumer<SettlementMovement> giftCardRefundHandler;
    private Consumer<SettlementMovement> externalRefundHandler;
    private Consumer<SettlementMovement> pointsRefundHandler;
    private Consumer<SettlementMovement> rebateRefundHandler;
    private Consumer<SettlementMovement> awardRefundHandler;
    private Consumer<SettlementMovement> movementHandler;
    private Function<SessionError, SettlementOptions> errorHandler;

    private boolean errorHandlerConsulted;
    private boolean retryRequested;

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

    /**
     * Called when the loyalty award leg commits. See {@link #onMovement} for
     * the provisional charge-side callback contract.
     */
    public SettlementFlow onAwarded(Consumer<SettlementMovement> handler) {
        return register(() -> this.awardHandler = requireHandler(handler));
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
     * <p>Charge-side movement callbacks are provisional: a later failure,
     * abort, or {@code onError} retry can unwind them in the same settlement
     * run without emitting a compensating callback. Refund allocation
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
     * Called when settlement fails. For charge-side failures after refund
     * allocations, the returned {@link SettlementOptions} controls recovery —
     * retry (e.g. {@code retryWithoutLoyalty()}) or {@code voidAndAbort()}.
     * For refund allocation failures, the handler is a terminal failure
     * notification: the returned options are ignored, committed refund
     * allocations stay recorded, and the register retries with the same
     * committed allocation prefix. Default charge-side policy:
     * {@code voidAndAbort()}.
     */
    public SettlementFlow onError(Function<SessionError, SettlementOptions> handler) {
        return register(() -> this.errorHandler = requireHandler(handler));
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
        // orchestration consults onError with the failure it throws, but
        // two outcomes would otherwise end in silence for execute()-style
        // registers: failures before the sequence starts (a state
        // rejection, a failed standing-movement drain) never reach the
        // handler at all, and a consultation whose RETRY resolution was
        // refused (incomplete unwind, retry cap) leaves the register
        // believing a retry is underway. Both are delivered here; the
        // returned options are ignored — there is nothing left to resolve.
        // ABORTED outcomes stay bypassed by design: the register initiated
        // the abort, and it must not surface as a failure to resolve.
        if (errorHandler != null
                && (!errorHandlerConsulted || retryRequested)
                && failure.getError().getCode() != SessionErrorCode.ABORTED) {
            awaitHandlerCall(() -> errorHandler.apply(failure.getError()));
        }
    }

    @Override
    void notifyRejection(SessionError error) {
        if (errorHandler != null) {
            errorHandler.apply(error);
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
                && awardHandler == null
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

    Function<SessionError, SettlementOptions> errorHandler() {
        if (errorHandler == null) {
            return null;
        }
        // the flag mutations ride inside the marshalled call, so the
        // awaited hand-off publishes them back to the operation thread
        return marshalled(error -> {
            errorHandlerConsulted = true;
            SettlementOptions resolution = errorHandler.apply(error);
            // a retry answer obliges us to tell the register if settlement
            // ends in failure anyway (see deliverFailure)
            retryRequested = resolution != null && !resolution.isVoidAndAbort();
            return resolution;
        });
    }

    private Consumer<SettlementMovement> specificMovementHandler(SettlementStep step) {
        switch (step) {
            case CARD_CHARGE:
                return cardChargeHandler;
            case AWARD:
                return awardHandler;
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
