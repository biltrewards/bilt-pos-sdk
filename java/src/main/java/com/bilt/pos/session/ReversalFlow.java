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
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The reversal chain returned by {@link CheckoutSession#voidTransaction()},
 * prior-sale voids, and same-session refund operations.
 *
 * <p>Registering handlers sends nothing; the reversal steps run when
 * {@link #execute()} (asynchronously, on the session's operation thread) or
 * {@link #executeSync()}, {@link #get()}, or {@link #getOrNull()}
 * (blocking) is invoked. When a step fails, the {@code onError} handler
 * decides how to proceed — {@link ReversalDecision#RETRY},
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
public final class ReversalFlow<T> extends SessionFlow<T> {

    private final Function<ReversalFlow<T>, T> executor;

    private BiFunction<ReversalStep, SessionError, ReversalDecision> errorHandler;
    private boolean failureResolved;

    ReversalFlow(Function<ReversalFlow<T>, T> executor) {
        super("the reversal");
        this.executor = executor;
    }

    /** Session wiring: execution and default callback delivery. Applied
     *  by the session — before user code can call {@link #callbackOn}. */
    ReversalFlow<T> session(SessionOperations session) {
        attach(session);
        return this;
    }

    /**
     * Delivers this flow's handlers through {@code executor} instead of the
     * session's default callback executor. Affects {@link #execute()} only —
     * the synchronous paths always dispatch on the calling thread.
     */
    public ReversalFlow<T> callbackOn(Executor executor) {
        Objects.requireNonNull(executor, "executor");
        return register(() -> overrideCallback(executor));
    }

    /** Called after every step completed (or was skipped by decision). */
    public ReversalFlow<T> onSuccess(Consumer<T> handler) {
        return register(() -> successHandler(requireHandler(handler)));
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
        return register(() -> completeHandler(requireHandler(handler)));
    }

    // ─── SessionFlow hooks ───

    @Override
    T runBody() {
        return executor.apply(this);
    }

    @Override
    void deliverFailure(SessionException failure) {
        // a failure the handler has not resolved with an ABORT — one
        // before the first step (a state rejection, a missing reference),
        // or the terminal failure of a reversal whose every step was
        // skipped — is delivered here with a null step, so
        // execute()-style registers are not left in silence
        if (errorHandler != null && !failureResolved) {
            awaitHandlerCall(() -> errorHandler.apply(null, failure.getError()));
        }
    }

    @Override
    void notifyRejection(SessionError error) {
        // null step: nothing ran
        if (errorHandler != null) {
            errorHandler.apply(null, error);
        }
    }

    private ReversalFlow<T> register(Runnable assignment) {
        guardRegistration();
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
        return (step, error) -> awaitHandlerCall(() -> {
            ReversalDecision decision = errorHandler.apply(step, error);
            // only an ABORT (explicit, or defaulted from null) resolves
            // the failure about to be thrown; a SKIP/RETRY answer leaves
            // a later terminal failure — e.g. every step skipped — still
            // owed to the handler
            if (decision == null || decision == ReversalDecision.ABORT) {
                failureResolved = true;
            }
            return decision;
        });
    }
}
