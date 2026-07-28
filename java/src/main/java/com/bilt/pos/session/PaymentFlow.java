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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The payment orchestration chain returned by {@code CheckoutSession.pay()}.
 *
 * <p>Registering handlers sends nothing; the sequence — rebate redemption,
 * point redemption, stored value, card payment, award — runs blocking on the
 * calling thread when {@link #execute()}, {@link #get()}, or
 * {@link #getOrNull()} is invoked. Each step handler receives that step's
 * result and returns the updated total for the next step (letting the
 * register recompute tax on discounted amounts).</p>
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
 * <p>If a step fails, the {@code onError} handler decides how to recover via
 * the {@link PaymentOptions} it returns; committed steps are reversed before
 * a retry or failure. The default (no handler) is
 * {@link PaymentOptions#voidAndAbort()}.</p>
 */
public final class PaymentFlow {

    private final Function<PaymentFlow, CheckoutResult> executor;
    private final AtomicBoolean started = new AtomicBoolean();

    private Function<TransactionContext, String> beforeStepHandler;
    private Function<RebateRedemptionResult, BigDecimal> rebatesHandler;
    private Function<PointRedemptionResult, BigDecimal> pointsHandler;
    private Function<GiftCardPaymentResult, BigDecimal> giftCardHandler;
    private Consumer<CheckoutResult> successHandler;
    private Function<SessionError, PaymentOptions> errorHandler;

    private CheckoutResult result;
    private SessionException failure;
    private RuntimeException unexpected;
    private boolean errorHandlerConsulted;

    PaymentFlow(Function<PaymentFlow, CheckoutResult> executor) {
        this.executor = executor;
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
        return register(() -> this.successHandler = requireHandler(handler));
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
     * Runs the payment sequence and dispatches the registered handlers.
     *
     * @throws IllegalStateException if the flow has already run
     */
    public void execute() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("this payment has already been executed");
        }
        run();
    }

    /**
     * Runs the payment sequence if it has not run yet and returns the result.
     *
     * @throws SessionException if the payment failed
     */
    public CheckoutResult get() {
        runIfNeeded();
        if (failure != null) {
            throw failure;
        }
        return result;
    }

    /** Like {@link #get()} but returns {@code null} on failure. */
    public CheckoutResult getOrNull() {
        runIfNeeded();
        return failure == null ? result : null;
    }

    private void runIfNeeded() {
        if (started.compareAndSet(false, true)) {
            run();
        }
        // a bug recorded by run() stays loud on every later accessor
        rethrowUnexpected();
    }

    private void rethrowUnexpected() {
        if (unexpected != null) {
            throw unexpected;
        }
    }

    private void run() {
        try {
            result = executor.apply(this);
        } catch (SessionException e) {
            failure = e;
            // orchestration consults onError with the failure it throws,
            // but failures before the sequence starts (a state rejection, a
            // failed standing-movement drain) never reach it — execute()
            // must still deliver those rather than return silently.
            // Resolution is moot at this point, so the returned options are
            // ignored. ABORTED outcomes stay bypassed by design: the
            // register initiated the abort, and it must not surface as a
            // failure to resolve.
            if (errorHandler != null && !errorHandlerConsulted
                    && e.getError().getCode() != SessionErrorCode.ABORTED) {
                errorHandler.apply(e.getError());
            }
            return;
        } catch (RuntimeException e) {
            // an unexpected exception is a bug, not a terminal outcome:
            // remember it so later accessors rethrow it instead of
            // reporting a successful null result, then fail loudly
            unexpected = e;
            throw e;
        }
        if (successHandler != null) {
            successHandler.accept(result);
        }
    }

    private PaymentFlow register(Runnable assignment) {
        if (started.get()) {
            throw new IllegalStateException(
                    "handlers must be registered before the payment is executed");
        }
        assignment.run();
        return this;
    }

    private static <T> T requireHandler(T handler) {
        return Objects.requireNonNull(handler, "handler");
    }

    // ─── SDK-internal accessors ───

    Function<TransactionContext, String> beforeStepHandler() {
        return beforeStepHandler;
    }

    Function<RebateRedemptionResult, BigDecimal> rebatesHandler() {
        return rebatesHandler;
    }

    Function<PointRedemptionResult, BigDecimal> pointsHandler() {
        return pointsHandler;
    }

    Function<GiftCardPaymentResult, BigDecimal> giftCardHandler() {
        return giftCardHandler;
    }

    Function<SessionError, PaymentOptions> errorHandler() {
        if (errorHandler == null) {
            return null;
        }
        return error -> {
            errorHandlerConsulted = true;
            return errorHandler.apply(error);
        };
    }
}
