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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A lazy, single-shot session operation.
 *
 * <p>Creating a {@code SessionResult} sends nothing to the terminal. Handlers
 * registered via {@link #onSuccess}, {@link #onError}, and
 * {@link #onComplete} are recorded only; the operation runs when one of the
 * terminal methods is invoked:</p>
 *
 * <ul>
 *   <li>{@link #execute()} — run asynchronously on the session's operation
 *       executor (a single thread per session, so operations run in
 *       submission order) and deliver the handlers through the callback
 *       executor; returns immediately.</li>
 *   <li>{@link #executeSync()} — run blocking: the operation takes its
 *       turn on the same operation thread (queueing behind anything in
 *       flight, so a sync call can never race an asynchronous one) while
 *       the caller waits; handlers dispatch on the calling thread. Called
 *       from the operation thread itself — an operation body or a
 *       direct-delivered handler invoking another operation — it runs
 *       inline.</li>
 *   <li>{@link #get()} — run (if not already run) and return the value, or
 *       throw {@link SessionException} on failure. Waits for an in-flight
 *       {@link #execute()} to settle.</li>
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
 * <p>With {@link #execute()}, handlers are delivered through the callback
 * executor configured on the session builder ({@code callbackExecutor}), or
 * the per-call {@link #callbackOn(Executor)} override — e.g. an Android
 * main-thread executor so handlers may touch UI directly. With neither
 * configured, handlers run directly on the session's operation thread; such
 * handlers must be fast, must not block, and must never synchronously invoke
 * another session operation (the operation thread is single, so a nested
 * blocking operation deadlocks). {@link #executeSync()} always dispatches
 * inline on the calling thread and ignores callback executors.</p>
 *
 * <p>{@link #onComplete(Runnable)} registers a cleanup hook that runs exactly
 * once after the outcome handlers, on every path: success, session error,
 * unexpected exception, a throwing outcome handler, and an operation the
 * session's executor rejected because the session already ended.</p>
 *
 * <p>The operation runs at most once; {@code get()}/{@code getOrNull()}/
 * {@code isSuccess()} return the cached outcome afterwards. Calling
 * {@link #execute()} or {@link #executeSync()} a second time, or registering
 * a handler after the operation has started, throws
 * {@link IllegalStateException}.</p>
 *
 * @param <T> the operation's result type
 */
public final class SessionResult<T> {

    private static final Logger LOGGER = Logger.getLogger(SessionResult.class.getName());

    private final String operationName;
    private final Supplier<T> body;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean completeDispatched = new AtomicBoolean();

    /** Opens once the outcome fields below are final; accessors await it. */
    private final CountDownLatch settled = new CountDownLatch(1);

    private Consumer<T> successHandler;
    private Consumer<SessionError> errorHandler;
    private Runnable completeHandler;
    private Consumer<T> handlerFailureCleanup;

    /** The owning session's execution machinery; null for a detached
     *  result, which runs everything inline. */
    private SessionOperations session;

    /** False for the rare operation that must overlap an in-flight one —
     *  {@code updateInputDisplay} targets the very input holding the
     *  operation thread, so queueing it there would wait on the thing it
     *  updates. Unordered operations run inline (sync) or on their own
     *  short-lived thread (async). */
    private boolean ordered = true;
    /** Callback delivery: initialized to the session's default when the
     *  result is created, overwritten by {@link #callbackOn(Executor)};
     *  null means direct delivery on the operation thread. */
    private Executor callbackExecutor;

    // value and error are always written before the latch opens, so the
    // await gives readers their visibility; unexpected alone can also be
    // written later, by a failing handler on the callback thread — volatile
    // so a concurrent accessor still sees the recorded bug
    private T value;
    private SessionError error;
    private volatile RuntimeException unexpected;

    SessionResult(String operationName, Supplier<T> body) {
        this.operationName = operationName;
        this.body = body;
    }

    /** Session wiring: where executions run and the default callback
     *  delivery. Applied by the session when the result is created —
     *  before user code can register a {@link #callbackOn} override, so
     *  plain assignment suffices. */
    SessionResult<T> session(SessionOperations session, Executor callbackExecutor) {
        this.session = session;
        this.callbackExecutor = callbackExecutor;
        return this;
    }

    /** Opts this operation out of the session's one-at-a-time ordering —
     *  see [ordered] for the why. */
    SessionResult<T> unordered() {
        this.ordered = false;
        return this;
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
     * Registers a hook that runs exactly once after the outcome handlers, on
     * every completion path — success, error, unexpected exception, a
     * throwing outcome handler, or rejection because the session ended
     * before the operation could run. The place for cleanup that must not
     * leak: re-enabling buttons, releasing claims. A throwing hook is
     * logged, never propagated, and never masks the operation's outcome.
     *
     * @throws IllegalStateException if the operation has already started
     */
    public SessionResult<T> onComplete(Runnable handler) {
        Objects.requireNonNull(handler, "handler");
        checkNotStarted();
        this.completeHandler = handler;
        return this;
    }

    /**
     * Delivers this operation's handlers through {@code executor} instead of
     * the session's default callback executor. Affects {@link #execute()}
     * only — {@link #executeSync()} always dispatches on the calling thread.
     *
     * @throws IllegalStateException if the operation has already started
     */
    public SessionResult<T> callbackOn(Executor executor) {
        Objects.requireNonNull(executor, "executor");
        checkNotStarted();
        this.callbackExecutor = executor;
        return this;
    }

    /**
     * Runs the operation asynchronously: it is submitted to the session's
     * operation executor (operations run one at a time, in submission
     * order) and this call returns immediately. Handlers are delivered
     * through the callback executor — see the class docs for the delivery
     * rules. When the session has already ended and its executor rejects
     * the submission, the operation fails with
     * {@link SessionErrorCode#INVALID_STATE} through the normal
     * {@code onError}/{@code onComplete} delivery.
     *
     * @throws IllegalStateException if the operation has already run, or if
     *     this result is not attached to a session (use {@link #executeSync()})
     */
    public void execute() {
        if (session == null) {
            throw new IllegalStateException(operationName + " is not attached to a "
                    + "session executor; use executeSync()");
        }
        claimStart();
        if (!ordered) {
            // deliberately concurrent (see [ordered]): its own short-lived
            // thread instead of the session queue it must overlap
            Thread runner = new Thread(this::runAsync, "bilt-" + operationName);
            runner.setDaemon(true);
            runner.start();
            return;
        }
        try {
            session.executor().execute(this::runAsync);
        } catch (RejectedExecutionException e) {
            // the session ended and shut its executor down before this
            // operation ran; deliver the failure through the normal
            // handler path so onError/onComplete cleanup still happens.
            // Fire-and-forget: this thread is the caller's, and awaiting a
            // callback executor the caller itself dispatches would deadlock.
            error = new SessionError(SessionErrorCode.INVALID_STATE,
                    operationName + " was not run: the session has ended");
            settled.countDown();
            HandlerDispatch.fireAndForget(
                    callbackExecutor, operationName, this::dispatchHandlers);
        }
    }

    /**
     * Runs the operation blocking — the deliberate synchronous spelling
     * for callers that own their threading. The operation still takes its
     * turn on the session's operation thread, queueing behind anything in
     * flight (a sync call can never race an asynchronous one); the caller
     * waits, and handlers dispatch on the calling thread.
     *
     * @throws IllegalStateException if the operation has already run
     */
    public void executeSync() {
        claimStart();
        runSync();
    }

    /**
     * Runs the operation if it has not run yet and returns its value. Waits
     * for an in-flight {@link #execute()} to settle first.
     *
     * @throws SessionException if the operation failed
     */
    public T get() {
        awaitOutcome();
        if (error != null) {
            throw new SessionException(error);
        }
        return value;
    }

    /**
     * Runs the operation if it has not run yet and returns its value, or
     * {@code null} if it failed. Waits for an in-flight {@link #execute()}
     * to settle first.
     */
    public T getOrNull() {
        awaitOutcome();
        return error == null ? value : null;
    }

    /** Runs the operation if it has not run yet and reports whether it
     *  succeeded. Waits for an in-flight {@link #execute()} to settle first. */
    public boolean isSuccess() {
        awaitOutcome();
        return error == null;
    }

    private void awaitOutcome() {
        if (started.compareAndSet(false, true)) {
            runSync();
        } else {
            awaitSettled();
        }
        // a bug recorded by a run stays loud on every later accessor
        rethrowUnexpected();
    }

    private void awaitSettled() {
        try {
            settled.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SessionException(new SessionError(SessionErrorCode.UNKNOWN,
                    "interrupted while waiting for " + operationName + " to settle"));
        }
    }

    /** The async body: outcome on the session thread, handlers via [deliver]. */
    private void runAsync() {
        try {
            value = body.get();
        } catch (SessionException e) {
            error = e.getError();
        } catch (RuntimeException e) {
            // a bug, not a terminal outcome. There is no caller on this
            // thread to rethrow to, so it is recorded (later accessors
            // rethrow it instead of reporting success) and logged loudly.
            unexpected = e;
            LOGGER.log(Level.SEVERE, operationName + " threw unexpectedly", e);
        } finally {
            settled.countDown();
        }
        // awaited: the next queued operation must not start until this
        // one's handlers (and their cleanup) have finished
        HandlerDispatch.awaitRun(callbackExecutor, operationName, this::dispatchHandlers);
    }

    /** Handler dispatch for the async path: nothing here may kill the
     *  executor thread, so handler failures are recorded and logged. */
    private void dispatchHandlers() {
        try {
            if (unexpected == null) {
                if (error == null) {
                    if (successHandler != null) {
                        successHandler.accept(value);
                    }
                } else if (errorHandler != null) {
                    errorHandler.accept(error);
                }
            }
        } catch (RuntimeException handlerFailure) {
            // recorded so later accessors rethrow instead of reporting the
            // value as a success (for a releasing operation that value is a
            // resource the cleanup below has just rendered unusable)
            unexpected = handlerFailure;
            releaseOnHandlerFailure(handlerFailure);
            LOGGER.log(Level.SEVERE,
                    operationName + "'s handler threw", handlerFailure);
        } finally {
            dispatchComplete();
        }
    }

    /** The synchronous body: previous {@code execute()} semantics — the
     *  caller blocks, handlers dispatch on the calling thread, bugs
     *  rethrown — plus the {@code onComplete} guarantee. The operation
     *  itself takes its turn on the session's operation thread (via
     *  [SessionOperations.callOrdered]), so it cannot race an in-flight
     *  asynchronous execution. */
    private void runSync() {
        try {
            try {
                value = session != null && ordered ? session.callOrdered(body) : body.get();
            } catch (SessionException e) {
                error = e.getError();
            } catch (RuntimeException e) {
                // an unexpected exception is a bug, not a terminal outcome:
                // remember it so later accessors rethrow it instead of
                // reporting a successful null result, then fail loudly
                unexpected = e;
                throw e;
            } finally {
                settled.countDown();
            }
            if (error == null) {
                if (successHandler != null) {
                    try {
                        successHandler.accept(value);
                    } catch (RuntimeException handlerFailure) {
                        // a throwing success handler is a bug like any other
                        // unexpected exception: record it so later accessors
                        // rethrow it instead of reporting the value as a
                        // success (for a releasing operation that value is a
                        // resource the cleanup has just rendered unusable)
                        unexpected = handlerFailure;
                        releaseOnHandlerFailure(handlerFailure);
                        throw handlerFailure;
                    }
                }
            } else if (errorHandler != null) {
                errorHandler.accept(error);
            }
        } finally {
            dispatchComplete();
        }
    }

    /** Exactly once, on every path; a throwing hook is logged, not thrown —
     *  it must neither mask the operation's outcome nor skip on retry. */
    private void dispatchComplete() {
        if (completeHandler == null || !completeDispatched.compareAndSet(false, true)) {
            return;
        }
        try {
            completeHandler.run();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, operationName + "'s onComplete hook threw", e);
        }
    }

    /**
     * Cleanup for operations that hand out a live resource: when the
     * {@code onSuccess} handler throws, the resource it was given would
     * otherwise be lost with the escaping exception (the builder's
     * {@code start()} uses this to end the just-started session). A cleanup
     * failure must not mask the handler's exception — it is attached as
     * suppressed instead. The handler's exception is also recorded, so
     * {@code get()}/{@code getOrNull()}/{@code isSuccess()} rethrow it
     * rather than handing back the released resource as a success.
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

    private void claimStart() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException(operationName + " has already been executed");
        }
    }

    private void checkNotStarted() {
        if (started.get()) {
            throw new IllegalStateException(
                    "handlers must be registered before " + operationName + " is executed");
        }
    }
}
