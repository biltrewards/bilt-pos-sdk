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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The reversal chain returned by the {@code voidTransaction()} and
 * {@code refund} operations of {@link CheckoutSession} (this session's own
 * payment) and {@link ReversalSession} (a referenced prior sale).
 *
 * <p>Registering handlers sends nothing; the reversal steps run blocking on
 * the calling thread when {@link #execute()}, {@link #get()}, or
 * {@link #getOrNull()} is invoked. When a step fails, the {@code onError}
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
 * @param <T> the reversal's result type
 */
public final class ReversalFlow<T> {

    private final Function<ReversalFlow<T>, T> executor;
    private final AtomicBoolean started = new AtomicBoolean();

    private Consumer<T> successHandler;
    private BiFunction<ReversalStep, SessionError, ReversalDecision> errorHandler;

    private T result;
    private SessionException failure;
    private RuntimeException unexpected;
    private boolean failureResolved;

    ReversalFlow(Function<ReversalFlow<T>, T> executor) {
        this.executor = executor;
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
     * Runs the reversal and dispatches the registered handlers.
     *
     * @throws IllegalStateException if the flow has already run
     */
    public void execute() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("this reversal has already been executed");
        }
        run();
    }

    /**
     * Runs the reversal if it has not run yet and returns the result.
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

    private void runIfNeeded() {
        if (started.compareAndSet(false, true)) {
            run();
        }
        // a bug recorded by run() stays loud on every later accessor
        if (unexpected != null) {
            throw unexpected;
        }
    }

    private void run() {
        try {
            result = executor.apply(this);
        } catch (SessionException e) {
            failure = e;
            // a failure the handler has not resolved with an ABORT — one
            // before the first step (a state rejection, a missing
            // reference), or the terminal failure of a reversal whose
            // every step was skipped — is delivered here with a null step,
            // so execute()-style registers are not left in silence
            if (errorHandler != null && !failureResolved) {
                errorHandler.apply(null, e.getError());
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
            try {
                successHandler.accept(result);
            } catch (RuntimeException handlerFailure) {
                // a throwing success handler is a bug like any other
                // unexpected exception (see SessionResult): record it so
                // later accessors rethrow it instead of reporting the
                // outcome as a clean success
                unexpected = handlerFailure;
                throw handlerFailure;
            }
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
     * the default policy.
     */
    ReversalManager.StepDecider decider() {
        if (errorHandler == null) {
            return null;
        }
        return (step, error) -> {
            ReversalDecision decision = errorHandler.apply(step, error);
            // only an ABORT (explicit, or defaulted from null) resolves
            // the failure about to be thrown; a SKIP/RETRY answer leaves
            // a later terminal failure — e.g. every step skipped — still
            // owed to the handler
            if (decision == null || decision == ReversalDecision.ABORT) {
                failureResolved = true;
            }
            return decision;
        };
    }
}
