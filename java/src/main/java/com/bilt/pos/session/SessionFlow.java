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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The execution machinery shared by the negotiation flows —
 * {@link PaymentFlow} and {@link ReversalFlow}: a lazy, single-shot run
 * with an asynchronous {@link #execute()} on the session's operation
 * thread, blocking {@link #executeSync()}/{@link #get()}/{@link
 * #getOrNull()} ordered through the same thread, handlers marshalled to
 * the callback executor with the flow thread awaiting each answer, and
 * the exactly-once {@code onComplete} guarantee on every completion path.
 *
 * <p>Subclasses own the typed handler surface (registration methods and
 * the SDK-internal accessors that hand marshalled handlers to the
 * orchestration) and the failure-delivery policy — which failures reach
 * {@code onError}, and with what arguments; the base delivers through
 * {@link #deliverFailure} after the outcome settles and through
 * {@link #notifyRejection} when the session's executor rejected the
 * submission.</p>
 *
 * @param <T> the flow's result type
 */
abstract class SessionFlow<T> {

    private static final Logger LOGGER = Logger.getLogger(SessionFlow.class.getName());

    /** Human-readable flow name ("payment", "reversal") for messages. */
    private final String name;

    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean completeDispatched = new AtomicBoolean();

    /** Opens once the outcome is final — body AND handler dispatch, since
     *  a throwing handler is recorded as the outcome; accessors await it. */
    private final CountDownLatch settled = new CountDownLatch(1);

    private Consumer<T> successHandler;
    private Runnable completeHandler;

    /** The owning session's execution machinery; null for a detached
     *  flow, which runs everything inline. */
    private SessionOperations session;
    /** Callback delivery: initialized to the session's default at wiring
     *  — before user code can call {@code callbackOn} — and overwritten
     *  by it; null means delivery on the flow's executing thread. */
    private Executor callbackExecutor;

    /** True once {@link #execute()} claimed the run: only then are
     *  handlers marshalled to the callback executor — the synchronous
     *  paths dispatch inline on the calling thread. Volatile: set on the
     *  caller's thread, read on the operation thread. */
    private volatile boolean asyncRun;

    private T result;
    private SessionException failure;
    private volatile RuntimeException unexpected;

    SessionFlow(String name) {
        this.name = name;
    }

    // ─── Subclass surface ───

    /** The flow's body: one full run of the underlying orchestration. */
    abstract T runBody();

    /**
     * Delivers a settled failure to the subclass's {@code onError}
     * handler, marshalled per {@link #marshalled}/{@link
     * #handlerExecutor()}; the subclass decides whether this particular
     * failure is owed to the handler at all.
     */
    abstract void deliverFailure(SessionException failure);

    /**
     * Delivers a rejected-at-submission failure (the session ended before
     * the flow could run) to the subclass's {@code onError} handler.
     * Already running on the callback thread — dispatch inline.
     */
    abstract void notifyRejection(SessionError error);

    /** This flow's name in messages and dispatch logs. */
    final String name() {
        return name;
    }

    /** Wiring: the owning session, and its callback delivery default. */
    final void attach(SessionOperations session) {
        this.session = session;
        this.callbackExecutor = session.callback();
    }

    /** The per-flow callback override behind {@code callbackOn}. */
    final void overrideCallback(Executor executor) {
        this.callbackExecutor = executor;
    }

    /** Handler-registration setters, called inside the subclass's
     *  registration guard. */
    final void successHandler(Consumer<T> handler) {
        this.successHandler = handler;
    }

    final void completeHandler(Runnable handler) {
        this.completeHandler = handler;
    }

    /** Guard for the subclass's fluent registration methods. */
    final void guardRegistration() {
        if (started.get()) {
            throw new IllegalStateException(
                    "handlers must be registered before the " + name + " is executed");
        }
    }

    /** Where handlers deliver: the callback executor for an asynchronous
     *  run, in place for the synchronous paths. */
    final Executor handlerExecutor() {
        return asyncRun ? callbackExecutor : null;
    }

    /** Wraps a mid-flow negotiation handler so each invocation delivers
     *  on the callback executor and waits for the answer. */
    final <A, R> Function<A, R> marshalled(Function<A, R> handler) {
        if (handler == null) {
            return null;
        }
        return argument -> awaitHandlerCall(() -> handler.apply(argument));
    }

    /** Delivers a handler on the callback executor and waits for its
     *  answer. The dispatch carries the session's awaited-handler marker,
     *  so a blocking session call made inside the handler runs inline —
     *  this thread is parked on the handler, and queueing behind it would
     *  deadlock. */
    final <R> R awaitHandlerCall(Supplier<R> handler) {
        Supplier<R> dispatch = session != null && handlerExecutor() != null
                ? session.awaitedHandler(handler)
                : handler;
        return HandlerDispatch.awaitCall(handlerExecutor(), name, dispatch);
    }

    /** [awaitHandlerCall] for handlers without an answer. */
    final void awaitHandlerRun(Runnable handler) {
        awaitHandlerCall(() -> {
            handler.run();
            return null;
        });
    }

    // ─── Terminal methods ───

    /**
     * Runs the flow asynchronously on the session's operation thread —
     * handlers included, see the subclass docs — and returns immediately.
     * When the session has already ended and its executor rejects the
     * submission, the flow fails with
     * {@link SessionErrorCode#INVALID_STATE} through {@code onError} and
     * {@code onComplete}.
     *
     * @throws IllegalStateException if the flow has already run, or if it is
     *     not attached to a session executor (use {@link #executeSync()})
     */
    public final void execute() {
        if (session == null) {
            throw new IllegalStateException("this " + name + " is not attached to a "
                    + "session executor; use executeSync()");
        }
        claimStart();
        asyncRun = true;
        try {
            session.executor().execute(this::runAsync);
        } catch (RejectedExecutionException e) {
            failure = new SessionException(new SessionError(SessionErrorCode.INVALID_STATE,
                    "the " + name + " was not run: the session has ended"));
            settled.countDown();
            // fire-and-forget: this thread is the caller's, and awaiting a
            // callback executor the caller itself dispatches would deadlock
            HandlerDispatch.fireAndForget(callbackExecutor, name, this::dispatchRejection);
        }
    }

    /**
     * Runs the flow blocking — the operation takes its turn on the
     * session's operation thread (queueing behind anything in flight, so
     * a sync call never races an asynchronous one) while the caller
     * waits; handlers dispatch on the calling thread.
     *
     * @throws IllegalStateException if the flow has already run
     */
    public final void executeSync() {
        claimStart();
        run();
    }

    /**
     * Runs the flow if it has not run yet and returns the result. Waits
     * for an in-flight {@link #execute()} to settle first.
     *
     * @throws SessionException if the flow failed
     */
    public final T get() {
        runIfNeeded();
        if (failure != null) {
            throw failure;
        }
        return result;
    }

    /** Like {@link #get()} but returns {@code null} on failure. */
    public final T getOrNull() {
        runIfNeeded();
        return failure == null ? result : null;
    }

    // ─── Internals ───

    private void claimStart() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "this " + name + " has already been executed");
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
                    "interrupted while waiting for the " + name + " to settle"));
        }
    }

    /** The async body: [run]'s strict semantics, but nothing may escape
     *  into the executor thread — bugs are already recorded, so log them. */
    private void runAsync() {
        try {
            run();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "the " + name + " threw unexpectedly", e);
        }
    }

    private void run() {
        try {
            try {
                result = session != null ? session.callOrdered(this::runBody) : runBody();
            } catch (SessionException e) {
                failure = e;
            } catch (RuntimeException e) {
                // an unexpected exception is a bug, not a terminal outcome:
                // remember it so later accessors rethrow it instead of
                // reporting a successful null result, then fail loudly
                unexpected = e;
                throw e;
            }
            if (failure != null) {
                deliverFailure(failure);
                return;
            }
            if (successHandler != null) {
                try {
                    awaitHandlerRun(() -> successHandler.accept(result));
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
            // only now is the outcome final — a throwing handler is
            // recorded as the outcome above, and an accessor released
            // earlier could report a clean success it must not
            settled.countDown();
        }
    }

    /** Rejected before it ran: the failure still reaches the register
     *  through the normal handlers. Runs on the callback thread already —
     *  posted whole by [execute] — so everything here dispatches inline. */
    private void dispatchRejection() {
        try {
            notifyRejection(failure.getError());
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "the " + name + "'s onError handler threw", e);
        } finally {
            dispatchComplete();
        }
    }

    /** [dispatchComplete] on the callback executor when running async. */
    private void deliverComplete() {
        if (completeHandler == null) {
            return;
        }
        awaitHandlerRun(this::dispatchComplete);
    }

    /** Exactly once, on every path; a throwing hook is logged, not thrown. */
    private void dispatchComplete() {
        if (completeHandler == null || !completeDispatched.compareAndSet(false, true)) {
            return;
        }
        try {
            completeHandler.run();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "the " + name + "'s onComplete hook threw", e);
        }
    }
}
