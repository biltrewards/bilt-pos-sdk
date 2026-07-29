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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A lazy, single-shot session operation.
 *
 * <p>Creating a {@code SessionResult} sends nothing to the terminal. Handlers
 * registered via {@link #onSuccess} and {@link #onError} are recorded only;
 * the operation runs — blocking, on the calling thread — when one of the
 * terminal methods is invoked:</p>
 *
 * <ul>
 *   <li>{@link #execute()} — run and dispatch handlers; returns nothing.</li>
 *   <li>{@link #get()} — run (if not already run) and return the value, or
 *       throw {@link SessionException} on failure.</li>
 *   <li>{@link #getOrNull()} — like {@code get()} but returns {@code null}
 *       on failure.</li>
 *   <li>{@link #isSuccess()} — run (if not already run) and report the outcome.</li>
 * </ul>
 *
 * <pre>{@code
 * session.requestConfirmation("Would you like a receipt?")
 *     .onSuccess(confirmed -> { if (confirmed) register.printReceipt(); })
 *     .onError(e -> register.showError(e.getMessage()))
 *     .execute();
 * }</pre>
 *
 * <p>The operation runs at most once; {@code get()}/{@code getOrNull()}/
 * {@code isSuccess()} return the cached outcome afterwards. Calling
 * {@link #execute()} a second time, or registering a handler after the
 * operation has started, throws {@link IllegalStateException}.</p>
 *
 * <p>Instances are not thread-safe; use from a single thread.</p>
 *
 * @param <T> the operation's result type
 */
public final class SessionResult<T> {

    private final String operationName;
    private final Supplier<T> body;
    private final AtomicBoolean started = new AtomicBoolean();

    private Consumer<T> successHandler;
    private Consumer<SessionError> errorHandler;
    private Consumer<T> handlerFailureCleanup;

    private T value;
    private SessionError error;
    private RuntimeException unexpected;

    SessionResult(String operationName, Supplier<T> body) {
        this.operationName = operationName;
        this.body = body;
    }

    /**
     * Registers a handler invoked with the result when the operation succeeds.
     *
     * @throws IllegalStateException if the operation has already started
     */
    public SessionResult<T> onSuccess(Consumer<T> handler) {
        Objects.requireNonNull(handler, "handler");
        checkNotStarted();
        this.successHandler = handler;
        return this;
    }

    /**
     * Registers a handler invoked with the {@link SessionError} when the
     * operation fails.
     *
     * @throws IllegalStateException if the operation has already started
     */
    public SessionResult<T> onError(Consumer<SessionError> handler) {
        Objects.requireNonNull(handler, "handler");
        checkNotStarted();
        this.errorHandler = handler;
        return this;
    }

    /**
     * Runs the operation on the calling thread and dispatches the registered
     * handlers.
     *
     * @throws IllegalStateException if the operation has already run
     */
    public void execute() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException(operationName + " has already been executed");
        }
        run();
    }

    /**
     * Runs the operation if it has not run yet and returns its value.
     *
     * @throws SessionException if the operation failed
     */
    public T get() {
        runIfNeeded();
        if (error != null) {
            throw new SessionException(error);
        }
        return value;
    }

    /**
     * Runs the operation if it has not run yet and returns its value, or
     * {@code null} if it failed.
     */
    public T getOrNull() {
        runIfNeeded();
        return error == null ? value : null;
    }

    /** Runs the operation if it has not run yet and reports whether it succeeded. */
    public boolean isSuccess() {
        runIfNeeded();
        return error == null;
    }

    private void runIfNeeded() {
        if (started.compareAndSet(false, true)) {
            run();
        }
        // a bug recorded by run() stays loud on every later accessor
        rethrowUnexpected();
    }

    private void run() {
        try {
            value = body.get();
        } catch (SessionException e) {
            error = e.getError();
        } catch (RuntimeException e) {
            // an unexpected exception is a bug, not a terminal outcome:
            // remember it so later accessors rethrow it instead of
            // reporting a successful null result, then fail loudly
            unexpected = e;
            throw e;
        }
        if (error == null) {
            if (successHandler != null) {
                try {
                    successHandler.accept(value);
                } catch (RuntimeException handlerFailure) {
                    releaseOnHandlerFailure(handlerFailure);
                    throw handlerFailure;
                }
            }
        } else if (errorHandler != null) {
            errorHandler.accept(error);
        }
    }

    /**
     * Cleanup for operations that hand out a live resource: when the
     * {@code onSuccess} handler throws, the resource it was given would
     * otherwise be lost with the escaping exception (the builder's
     * {@code start()} uses this to end the just-started session). A cleanup
     * failure must not mask the handler's exception — it is attached as
     * suppressed instead.
     */
    SessionResult<T> releasing(Consumer<T> cleanup) {
        this.handlerFailureCleanup = cleanup;
        return this;
    }

    private void releaseOnHandlerFailure(RuntimeException handlerFailure) {
        if (handlerFailureCleanup == null) {
            return;
        }
        try {
            handlerFailureCleanup.accept(value);
        } catch (RuntimeException cleanupFailure) {
            handlerFailure.addSuppressed(cleanupFailure);
        }
    }

    private void rethrowUnexpected() {
        if (unexpected != null) {
            throw unexpected;
        }
    }

    private void checkNotStarted() {
        if (started.get()) {
            throw new IllegalStateException(
                    "handlers must be registered before " + operationName + " is executed");
        }
    }
}
