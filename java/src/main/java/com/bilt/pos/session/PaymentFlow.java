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

import com.bilt.pos.session.payment.CheckoutResult;
import com.bilt.pos.session.payment.GiftCardPaymentResult;
import com.bilt.pos.session.payment.PaymentOptions;
import com.bilt.pos.session.payment.PointRedemptionResult;
import com.bilt.pos.session.payment.RebateRedemptionResult;
import com.bilt.pos.session.payment.TransactionContext;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The payment orchestration chain returned by {@code CheckoutSession.pay()}.
 *
 * <p>Registering handlers sends nothing; the sequence — rebate redemption,
 * point redemption, stored value, card payment, award — runs when
 * {@link #execute()} (asynchronously, on the session's operation thread),
 * {@link #executeSync()} (blocking), {@link #get()}, or {@link #getOrNull()}
 * is invoked. Each step handler receives that step's result and returns the
 * updated total for the next step (letting the register recompute tax on
 * discounted amounts).</p>
 *
 * <pre>{@code
 * session.pay()
 *     .onRebatesRedeemed(rebates -> rebates.getSuggestedTotal())
 *     .onPointsRedeemed(points -> points.getSuggestedTotal())
 *     .onSuccess(result -> register.printReceipt(result.getMerchantReceipt()))
 *     .onError(error -> PaymentOptions.voidAndAbort())
 *     .execute();
 * }</pre>
 *
 * <p>With {@link #execute()}, every handler — the step handlers,
 * {@code onSuccess}, {@code onError}, and {@code onComplete} — is delivered
 * through the callback executor (the session builder's
 * {@code callbackExecutor}, or the per-flow {@link #callbackOn(Executor)}
 * override), and the payment thread <em>waits for each answer</em>: the
 * handlers are part of the payment negotiation, so their return values
 * steer the sequence exactly as if they ran inline — they just physically
 * run on the integrator's thread. Keep them quick (the payment is paused
 * while they run), and never block a callback thread on this flow's
 * {@code get()} — the flow would be waiting on that thread's handler while
 * it waits on the flow. Without a callback executor, handlers run directly
 * on the thread executing the sequence.</p>
 *
 * <p>If a step fails, the {@code onError} handler decides how to recover via
 * the {@link PaymentOptions} it returns; committed steps are reversed before
 * a retry or failure. The default (no handler) is
 * {@link PaymentOptions#voidAndAbort()}.</p>
 */
public final class PaymentFlow extends SessionFlow<CheckoutResult> {

    private final Function<PaymentFlow, CheckoutResult> executor;

    private Function<TransactionContext, String> beforeStepHandler;
    private Function<RebateRedemptionResult, BigDecimal> rebatesHandler;
    private Function<PointRedemptionResult, BigDecimal> pointsHandler;
    private Function<GiftCardPaymentResult, BigDecimal> giftCardHandler;
    private Function<SessionError, PaymentOptions> errorHandler;

    private boolean errorHandlerConsulted;
    private boolean retryRequested;

    PaymentFlow(Function<PaymentFlow, CheckoutResult> executor) {
        super("the payment");
        this.executor = executor;
    }

    /** Session wiring: execution and default callback delivery. Applied
     *  by {@code pay()} — before user code can call {@link #callbackOn}. */
    PaymentFlow session(SessionOperations session) {
        attach(session);
        return this;
    }

    /**
     * Delivers this flow's handlers through {@code executor} instead of the
     * session's default callback executor. Affects {@link #execute()} only —
     * the synchronous paths always dispatch on the calling thread.
     */
    public PaymentFlow callbackOn(Executor executor) {
        Objects.requireNonNull(executor, "executor");
        return register(() -> overrideCallback(executor));
    }

    /**
     * Called before each step; returns the {@code SaleTransactionID} to use.
     * Default: a fresh UUID per step.
     */
    public PaymentFlow beforeStep(Function<TransactionContext, String> handler) {
        return register(() -> this.beforeStepHandler = requireHandler(handler));
    }

    /**
     * Called when rebates/coupons are committed. Returns the updated total
     * for the next step. Default: accept, {@code previousTotal − rebates}.
     */
    public PaymentFlow onRebatesRedeemed(Function<RebateRedemptionResult, BigDecimal> handler) {
        return register(() -> this.rebatesHandler = requireHandler(handler));
    }

    /**
     * Called when points/rewards are redeemed for monetary value. Returns
     * the updated total. Default: accept, subtract the monetary value.
     */
    public PaymentFlow onPointsRedeemed(Function<PointRedemptionResult, BigDecimal> handler) {
        return register(() -> this.pointsHandler = requireHandler(handler));
    }

    /**
     * Called when the stored value card is charged. Returns the updated
     * total. Default: accept, subtract the charged amount.
     */
    public PaymentFlow onGiftCardPayment(Function<GiftCardPaymentResult, BigDecimal> handler) {
        return register(() -> this.giftCardHandler = requireHandler(handler));
    }

    /** Called after the full sequence completes successfully. */
    public PaymentFlow onSuccess(Consumer<CheckoutResult> handler) {
        return register(() -> successHandler(requireHandler(handler)));
    }

    /**
     * Called when a step fails; the returned {@link PaymentOptions} controls
     * recovery — retry (e.g. {@code retryWithoutLoyalty()}) or
     * {@code voidAndAbort()}. Default: {@code voidAndAbort()}.
     */
    public PaymentFlow onError(Function<SessionError, PaymentOptions> handler) {
        return register(() -> this.errorHandler = requireHandler(handler));
    }

    /**
     * Registers a hook that runs exactly once after the payment settles, on
     * every path — success, failure, unexpected exception, or rejection
     * because the session ended before the payment could run. The place for
     * cleanup that must not leak. A throwing hook is logged, never
     * propagated.
     */
    public PaymentFlow onComplete(Runnable handler) {
        return register(() -> completeHandler(requireHandler(handler)));
    }

    // ─── SessionFlow hooks ───

    @Override
    CheckoutResult runBody() {
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

    private PaymentFlow register(Runnable assignment) {
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

    Function<TransactionContext, String> beforeStepHandler() {
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

    Function<SessionError, PaymentOptions> errorHandler() {
        if (errorHandler == null) {
            return null;
        }
        // the flag mutations ride inside the marshalled call, so the
        // awaited hand-off publishes them back to the operation thread
        return marshalled(error -> {
            errorHandlerConsulted = true;
            PaymentOptions resolution = errorHandler.apply(error);
            // a retry answer obliges us to tell the register if the payment
            // ends in failure anyway (see deliverFailure)
            retryRequested = resolution != null && !resolution.isVoidAndAbort();
            return resolution;
        });
    }
}
