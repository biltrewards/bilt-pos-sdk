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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Delivery of handlers to a callback executor.
 *
 * <p>{@link #awaitCall} runs the handler on the callback executor and waits
 * for it, so a handler behaves exactly as if invoked inline — same ordering,
 * same exception propagation, and a returned value can steer the flow that
 * asked — while physically running on the integrator's thread. It must
 * only be called from a session's operation thread: awaiting from the very
 * thread the callback executor dispatches to would deadlock.</p>
 *
 * <p>{@link #fireAndForget} is for the one dispatch that happens on the
 * caller's own thread — an operation rejected at submission — where awaiting
 * could deadlock a caller that is itself the callback thread.</p>
 *
 * <p>Everywhere, a {@code null} executor means direct delivery (run
 * in place), and a callback executor that rejects the dispatch falls back to
 * running in place rather than costing the {@code onComplete} guarantee.</p>
 */
final class HandlerDispatch {

    private static final Logger LOGGER = Logger.getLogger(HandlerDispatch.class.getName());

    private HandlerDispatch() {
    }

    /** Runs {@code handler} on {@code callback} and returns its answer. */
    static <R> R awaitCall(Executor callback, String operationName, Supplier<R> handler) {
        if (callback == null) {
            return handler.get();
        }
        FutureTask<R> task = new FutureTask<>(handler::get);
        try {
            callback.execute(task);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, operationName + "'s callback executor rejected the "
                    + "dispatch; running the handler on the session thread", e);
            return handler.get();
        }
        try {
            return task.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SessionException(new SessionError(SessionErrorCode.UNKNOWN,
                    "interrupted while waiting for " + operationName + "'s handler"));
        }
    }

    // no Runnable-shaped await variant on purpose: awaited dispatches must
    // carry the session's awaited-handler marker, and the owners attach it
    // around the Supplier they hand to awaitCall (SessionFlow's
    // awaitHandlerCall/awaitHandlerRun, SessionResult's async dispatch) —
    // a bare convenience here would invite marker-less awaits, which is
    // exactly the deadlock the marker prevents

    /** Runs {@code dispatch} on {@code callback} without waiting. */
    static void fireAndForget(Executor callback, String operationName, Runnable dispatch) {
        if (callback == null) {
            dispatch.run();
            return;
        }
        try {
            callback.execute(dispatch);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, operationName + "'s callback executor rejected the "
                    + "dispatch; delivering on the calling thread", e);
            dispatch.run();
        }
    }
}
