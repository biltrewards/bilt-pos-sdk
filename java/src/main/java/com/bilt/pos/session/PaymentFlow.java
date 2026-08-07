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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The payment orchestration chain returned by {@code CheckoutSession.pay()}.
 *
 * <p>Registering handlers sends nothing; the sequence — rebate redemption,
 * point redemption, stored value, card payment, award — runs when
 * {@link #execute()} (asynchronously, on the session's operation thread),
 * {@link #executeSync()} (blocking, on the calling thread), {@link #get()},
 * or {@link #getOrNull()} is invoked. Each step handler receives that step's
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
public final class PaymentFlow {

    private static final Logger LOGGER = Logger.getLogger(PaymentFlow.class.getName());

    private final Function<PaymentFlow, CheckoutResult> executor;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean completeDispatched = new AtomicBoolean();

    /** Opens once result/failure below are final; accessors await it. */
    private final CountDownLatch settled = new CountDownLatch(1);

    private Function<TransactionContext, String> beforeStepHandler;
    private Function<RebateRedemptionResult, BigDecimal> rebatesHandler;
    private Function<PointRedemptionResult, BigDecimal> pointsHandler;
    private Function<GiftCardPaymentResult, BigDecimal> giftCardHandler;
    private Consumer<CheckoutResult> successHandler;
    private Function<SessionError, PaymentOptions> errorHandler;
    private Runnable completeHandler;

    /** The owning session's execution machinery; null for a detached
     *  flow, which runs everything inline. */
    private SessionOperations session;
    /** Callback delivery: initialized to the session's default by
     *  {@code pay()} — before user code can call {@link #callbackOn} —
     *  and overwritten by it; null means delivery on the payment thread. */
    private Executor callbackExecutor;

    /** True once {@link #execute()} claimed the run: only then are handlers
     *  marshalled to the callback executor — the synchronous paths dispatch
     *  inline on the calling thread. Volatile: set on the caller's thread,
     *  read on the operation thread. */
    private volatile boolean asyncRun;

    private CheckoutResult result;
    private SessionException failure;
    private volatile RuntimeException unexpected;
    private boolean errorHandlerConsulted;
    private boolean retryRequested;

    PaymentFlow(Function<PaymentFlow, CheckoutResult> executor) {
        this.executor = executor;
    }

    /** Session wiring: execution and default callback delivery. Applied
     *  by {@code pay()} — before user code can call {@link #callbackOn}. */
    PaymentFlow session(SessionOperations session) {
        this.session = session;
        this.callbackExecutor = session.callback();
        return this;
    }

    /** Session-wide callback delivery default; applied by {@code pay()}. */
    PaymentFlow callbackExecutor(Executor callbackExecutor) {
        this.callbackExecutor = callbackExecutor;
        return this;
    }

    /**
     * Delivers this flow's handlers through {@code executor} instead of the
     * session's default callback executor. Affects {@link #execute()} only —
     * the synchronous paths always dispatch on the calling thread.
     */
    public PaymentFlow callbackOn(Executor executor) {
        Objects.requireNonNull(executor, "executor");
        return register(() -> this.callbackExecutor = executor);
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
     * Registers a hook that runs exactly once after the payment settles, on
     * every path — success, failure, unexpected exception, or rejection
     * because the session ended before the payment could run. The place for
     * cleanup that must not leak. A throwing hook is logged, never
     * propagated.
     */
    public PaymentFlow onComplete(Runnable handler) {
        return register(() -> this.completeHandler = requireHandler(handler));
    }

    /**
     * Runs the payment sequence asynchronously on the session's operation
     * thread — handlers included, see the class docs — and returns
     * immediately. When the session has already ended and its executor
     * rejects the submission, the payment fails with
     * {@link SessionErrorCode#INVALID_STATE} through {@code onError} and
     * {@code onComplete}.
     *
     * @throws IllegalStateException if the flow has already run, or if it is
     *     not attached to a session executor (use {@link #executeSync()})
     */
    public void execute() {
        if (session == null) {
            throw new IllegalStateException("this payment is not attached to a "
                    + "session executor; use executeSync()");
        }
        claimStart();
        asyncRun = true;
        try {
            session.executor().execute(this::runAsync);
        } catch (RejectedExecutionException e) {
            failure = new SessionException(new SessionError(SessionErrorCode.INVALID_STATE,
                    "the payment was not run: the session has ended"));
            settled.countDown();
            // fire-and-forget: this thread is the caller's, and awaiting a
            // callback executor the caller itself dispatches would deadlock
            HandlerDispatch.fireAndForget(callbackExecutor, "pay", this::dispatchRejection);
        }
    }

    /**
     * Runs the payment sequence blocking on the calling thread and
     * dispatches the registered handlers inline.
     *
     * @throws IllegalStateException if the flow has already run
     */
    public void executeSync() {
        claimStart();
        run();
    }

    /**
     * Runs the payment sequence if it has not run yet and returns the
     * result. Waits for an in-flight {@link #execute()} to settle first.
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

    private void claimStart() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("this payment has already been executed");
        }
    }

    private void runIfNeeded() {
        if (started.compareAndSet(false, true)) {
            run();
        } else {
            awaitSettled();
        }
        // a bug recorded by run() stays loud on every later accessor
        rethrowUnexpected();
    }

    private void awaitSettled() {
        try {
            settled.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SessionException(new SessionError(SessionErrorCode.UNKNOWN,
                    "interrupted while waiting for the payment to settle"));
        }
    }

    /** The async body: [run]'s strict semantics, but nothing may escape
     *  into the executor thread — bugs are already recorded, so log them. */
    private void runAsync() {
        try {
            run();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "the payment threw unexpectedly", e);
        }
    }

    /** Rejected before it ran: the failure still reaches the register
     *  through the normal handlers. Runs on the callback thread already —
     *  posted whole by [execute] — so everything here dispatches inline. */
    private void dispatchRejection() {
        try {
            if (errorHandler != null) {
                errorHandler.apply(failure.getError());
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "the payment's onError handler threw", e);
        } finally {
            dispatchComplete();
        }
    }

    /** [dispatchComplete] on the callback executor when running async. */
    private void deliverComplete() {
        if (completeHandler == null) {
            return;
        }
        HandlerDispatch.awaitRun(handlerExecutor(), "pay", this::dispatchComplete);
    }

    /** Exactly once, on every path; a throwing hook is logged, not thrown. */
    private void dispatchComplete() {
        if (completeHandler == null || !completeDispatched.compareAndSet(false, true)) {
            return;
        }
        try {
            completeHandler.run();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "the payment's onComplete hook threw", e);
        }
    }

    /** Where handlers deliver: the callback executor for an asynchronous
     *  run, in place for the synchronous paths. */
    private Executor handlerExecutor() {
        return asyncRun ? callbackExecutor : null;
    }

    private void rethrowUnexpected() {
        if (unexpected != null) {
            throw unexpected;
        }
    }

    private void run() {
        try {
            try {
                result = session != null
                        ? session.callOrdered(() -> executor.apply(this))
                        : executor.apply(this);
            } catch (SessionException e) {
                failure = e;
            } catch (RuntimeException e) {
                // an unexpected exception is a bug, not a terminal outcome:
                // remember it so later accessors rethrow it instead of
                // reporting a successful null result, then fail loudly
                unexpected = e;
                throw e;
            } finally {
                settled.countDown();
            }
            if (failure != null) {
                // orchestration consults onError with the failure it throws,
                // but two outcomes would otherwise end in silence for
                // execute()-style registers: failures before the sequence
                // starts (a state rejection, a failed standing-movement
                // drain) never reach the handler at all, and a consultation
                // whose RETRY resolution was refused (incomplete unwind,
                // retry cap) leaves the register believing a retry is
                // underway. Both are delivered here; the returned options
                // are ignored — there is nothing left to resolve. ABORTED
                // outcomes stay bypassed by design: the register initiated
                // the abort, and it must not surface as a failure to
                // resolve.
                if (errorHandler != null
                        && (!errorHandlerConsulted || retryRequested)
                        && failure.getError().getCode() != SessionErrorCode.ABORTED) {
                    HandlerDispatch.awaitCall(handlerExecutor(), "pay",
                            () -> errorHandler.apply(failure.getError()));
                }
                return;
            }
            if (successHandler != null) {
                try {
                    HandlerDispatch.awaitRun(handlerExecutor(), "pay",
                            () -> successHandler.accept(result));
                } catch (RuntimeException handlerFailure) {
                    // a throwing success handler is a bug like any other
                    // unexpected exception (see SessionResult): record it so
                    // later accessors rethrow it instead of reporting the
                    // outcome as a clean success
                    unexpected = handlerFailure;
                    throw handlerFailure;
                }
            }
        } finally {
            deliverComplete();
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
            // ends in failure anyway (see run()'s catch)
            retryRequested = resolution != null && !resolution.isVoidAndAbort();
            return resolution;
        });
    }

    private <A, R> Function<A, R> marshalled(Function<A, R> handler) {
        if (handler == null) {
            return null;
        }
        return argument -> HandlerDispatch.awaitCall(
                handlerExecutor(), "pay", () -> handler.apply(argument));
    }
}
