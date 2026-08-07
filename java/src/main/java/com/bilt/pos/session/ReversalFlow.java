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

import com.bilt.pos.session.internal.ReversalManager;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The reversal chain returned by the {@code voidTransaction()} and
 * {@code refund} operations of {@link CheckoutSession} (this session's own
 * payment) and {@link ReversalSession} (a referenced prior sale).
 *
 * <p>Registering handlers sends nothing; the reversal steps run when
 * {@link #execute()} (asynchronously, on the session's operation thread) or
 * {@link #executeSync()}, {@link #get()}, or {@link #getOrNull()} (blocking,
 * on the calling thread) is invoked. When a step fails, the {@code onError}
 * handler decides how to proceed — {@link ReversalDecision#RETRY},
 * {@link ReversalDecision#SKIP}, or {@link ReversalDecision#ABORT}. Without
 * a handler the default policy applies: a failed money step (the card or
 * stored value leg) aborts; a failed loyalty movement riding along with a
 * money step is skipped (the terminal can retry it via
 * store-and-forward); a failed loyalty movement that is the substance of
 * the reversal aborts.</p>
 *
 * <pre>{@code
 * session.voidTransaction()
 *     .onError((step, error) -> step == ReversalStep.AWARD
 *             ? ReversalDecision.SKIP : ReversalDecision.ABORT)
 *     .onSuccess(result -> register.printVoidReceipt(result))
 *     .execute();
 * }</pre>
 *
 * <p>With {@link #execute()}, handlers are delivered through the callback
 * executor (the session builder's {@code callbackExecutor}, or the per-flow
 * {@link #callbackOn(Executor)} override) and the reversal thread waits for
 * each answer — the {@code onError} step decisions steer the flow exactly
 * as if they ran inline, just physically on the integrator's thread. Keep
 * handlers quick, and never block a callback thread on this flow's
 * {@code get()}. Without a callback executor, handlers run directly on the
 * thread executing the reversal.</p>
 *
 * @param <T> the reversal's result type
 */
public final class ReversalFlow<T> {

    private static final Logger LOGGER = Logger.getLogger(ReversalFlow.class.getName());

    private final Function<ReversalFlow<T>, T> executor;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean completeDispatched = new AtomicBoolean();

    /** Opens once result/failure below are final; accessors await it. */
    private final CountDownLatch settled = new CountDownLatch(1);

    private Consumer<T> successHandler;
    private BiFunction<ReversalStep, SessionError, ReversalDecision> errorHandler;
    private Runnable completeHandler;

    /** The owning session's execution machinery; null for a detached
     *  flow, which runs everything inline. */
    private SessionOperations session;
    /** Callback delivery: initialized to the session's default at creation
     *  — before user code can call {@link #callbackOn} — and overwritten by
     *  it; null means delivery on the reversal thread. */
    private Executor callbackExecutor;

    /** True once {@link #execute()} claimed the run: only then are handlers
     *  marshalled to the callback executor. Volatile: set on the caller's
     *  thread, read on the operation thread. */
    private volatile boolean asyncRun;

    private T result;
    private SessionException failure;
    private volatile RuntimeException unexpected;
    private boolean failureResolved;

    ReversalFlow(Function<ReversalFlow<T>, T> executor) {
        this.executor = executor;
    }

    /** Session wiring: execution and default callback delivery. Applied
     *  by the session — before user code can call {@link #callbackOn}. */
    ReversalFlow<T> session(SessionOperations session) {
        this.session = session;
        this.callbackExecutor = session.callback();
        return this;
    }

    /** Session-wide callback delivery default; applied by the session. */
    ReversalFlow<T> callbackExecutor(Executor callbackExecutor) {
        this.callbackExecutor = callbackExecutor;
        return this;
    }

    /**
     * Delivers this flow's handlers through {@code executor} instead of the
     * session's default callback executor. Affects {@link #execute()} only —
     * the synchronous paths always dispatch on the calling thread.
     */
    public ReversalFlow<T> callbackOn(Executor executor) {
        Objects.requireNonNull(executor, "executor");
        return register(() -> this.callbackExecutor = executor);
    }

    /** Called after every step completed (or was skipped by decision). */
    public ReversalFlow<T> onSuccess(Consumer<T> handler) {
        return register(() -> this.successHandler = requireHandler(handler));
    }

    /**
     * Called when a step fails; the returned {@link ReversalDecision}
     * controls whether the flow retries the step, skips it, or aborts.
     * A {@code null} decision counts as {@link ReversalDecision#ABORT}.
     *
     * <p>A terminal failure the handler did not resolve with an abort —
     * one before any step ran (a state rejection, a missing reference), or
     * a reversal whose every step was skipped and therefore reversed
     * nothing — is delivered with a {@code null} step; the returned
     * decision is then ignored, there is nothing left to resolve.</p>
     *
     * <p>Because of that {@code null}-step delivery, a handler that
     * {@code switch}es on the step must guard for {@code null} first — an
     * exception thrown from the handler would mask the very failure being
     * delivered. Null-safe comparisons, as in the class example
     * ({@code step == ReversalStep.AWARD ? SKIP : ABORT}), need no
     * guard.</p>
     */
    public ReversalFlow<T> onError(
            BiFunction<ReversalStep, SessionError, ReversalDecision> handler) {
        return register(() -> this.errorHandler = requireHandler(handler));
    }

    /**
     * Registers a hook that runs exactly once after the reversal settles,
     * on every path — success, failure, unexpected exception, or rejection
     * because the session ended before the reversal could run. The place
     * for cleanup that must not leak. A throwing hook is logged, never
     * propagated.
     */
    public ReversalFlow<T> onComplete(Runnable handler) {
        return register(() -> this.completeHandler = requireHandler(handler));
    }

    /**
     * Runs the reversal asynchronously on the session's operation thread —
     * handlers included, see the class docs — and returns immediately.
     * When the session has already ended and its executor rejects the
     * submission, the reversal fails with
     * {@link SessionErrorCode#INVALID_STATE} through {@code onError} (with
     * a {@code null} step) and {@code onComplete}.
     *
     * @throws IllegalStateException if the flow has already run, or if it is
     *     not attached to a session executor (use {@link #executeSync()})
     */
    public void execute() {
        if (session == null) {
            throw new IllegalStateException("this reversal is not attached to a "
                    + "session executor; use executeSync()");
        }
        claimStart();
        asyncRun = true;
        try {
            session.executor().execute(this::runAsync);
        } catch (RejectedExecutionException e) {
            failure = new SessionException(new SessionError(SessionErrorCode.INVALID_STATE,
                    "the reversal was not run: the session has ended"));
            settled.countDown();
            // fire-and-forget: this thread is the caller's, and awaiting a
            // callback executor the caller itself dispatches would deadlock
            HandlerDispatch.fireAndForget(
                    callbackExecutor, "reversal", this::dispatchRejection);
        }
    }

    /**
     * Runs the reversal blocking on the calling thread and dispatches the
     * registered handlers inline.
     *
     * @throws IllegalStateException if the flow has already run
     */
    public void executeSync() {
        claimStart();
        run();
    }

    /**
     * Runs the reversal if it has not run yet and returns the result.
     * Waits for an in-flight {@link #execute()} to settle first.
     *
     * @throws SessionException if the reversal failed
     */
    public T get() {
        runIfNeeded();
        if (failure != null) {
            throw failure;
        }
        return result;
    }

    /** Like {@link #get()} but returns {@code null} on failure. */
    public T getOrNull() {
        runIfNeeded();
        return failure == null ? result : null;
    }

    private void claimStart() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("this reversal has already been executed");
        }
    }

    private void runIfNeeded() {
        if (started.compareAndSet(false, true)) {
            run();
        } else {
            awaitSettled();
        }
        // a bug recorded by run() stays loud on every later accessor
        if (unexpected != null) {
            throw unexpected;
        }
    }

    private void awaitSettled() {
        try {
            settled.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SessionException(new SessionError(SessionErrorCode.UNKNOWN,
                    "interrupted while waiting for the reversal to settle"));
        }
    }

    /** The async body: [run]'s strict semantics, but nothing may escape
     *  into the executor thread — bugs are already recorded, so log them. */
    private void runAsync() {
        try {
            run();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "the reversal threw unexpectedly", e);
        }
    }

    /** Rejected before it ran: the failure still reaches the register
     *  through the normal handlers, with a null step — nothing ran. Runs on
     *  the callback thread already (posted whole by [execute]), so
     *  everything here dispatches inline. */
    private void dispatchRejection() {
        try {
            if (errorHandler != null) {
                errorHandler.apply(null, failure.getError());
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "the reversal's onError handler threw", e);
        } finally {
            dispatchComplete();
        }
    }

    /** [dispatchComplete] on the callback executor when running async. */
    private void deliverComplete() {
        if (completeHandler == null) {
            return;
        }
        HandlerDispatch.awaitRun(handlerExecutor(), "reversal", this::dispatchComplete);
    }

    /** Exactly once, on every path; a throwing hook is logged, not thrown. */
    private void dispatchComplete() {
        if (completeHandler == null || !completeDispatched.compareAndSet(false, true)) {
            return;
        }
        try {
            completeHandler.run();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "the reversal's onComplete hook threw", e);
        }
    }

    /** Where handlers deliver: the callback executor for an asynchronous
     *  run, in place for the synchronous paths. */
    private Executor handlerExecutor() {
        return asyncRun ? callbackExecutor : null;
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
                // a failure the handler has not resolved with an ABORT — one
                // before the first step (a state rejection, a missing
                // reference), or the terminal failure of a reversal whose
                // every step was skipped — is delivered here with a null
                // step, so execute()-style registers are not left in silence
                if (errorHandler != null && !failureResolved) {
                    HandlerDispatch.awaitCall(handlerExecutor(), "reversal",
                            () -> errorHandler.apply(null, failure.getError()));
                }
                return;
            }
            if (successHandler != null) {
                try {
                    HandlerDispatch.awaitRun(handlerExecutor(), "reversal",
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

    private ReversalFlow<T> register(Runnable assignment) {
        if (started.get()) {
            throw new IllegalStateException(
                    "handlers must be registered before the reversal is executed");
        }
        assignment.run();
        return this;
    }

    private static <T> T requireHandler(T handler) {
        return Objects.requireNonNull(handler, "handler");
    }

    // ─── SDK-internal accessors ───

    /**
     * The registered handler as a step decider, or {@code null} to select
     * the default policy. Invocations — from the reversal manager,
     * mid-flow, on the operation thread — deliver on the callback executor
     * and wait for the decision (see the class docs); the resolved flag
     * rides inside the marshalled call, so the awaited hand-off publishes
     * it back to the operation thread.
     */
    ReversalManager.StepDecider decider() {
        if (errorHandler == null) {
            return null;
        }
        return (step, error) -> HandlerDispatch.awaitCall(handlerExecutor(), "reversal",
                () -> {
                    ReversalDecision decision = errorHandler.apply(step, error);
                    // only an ABORT (explicit, or defaulted from null)
                    // resolves the failure about to be thrown; a SKIP/RETRY
                    // answer leaves a later terminal failure — e.g. every
                    // step skipped — still owed to the handler
                    if (decision == null || decision == ReversalDecision.ABORT) {
                        failureResolved = true;
                    }
                    return decision;
                });
    }
}
