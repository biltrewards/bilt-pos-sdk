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

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * The execution machinery behind a session's lazy operations, shared by the
 * session types.
 *
 * <p>Owns the session's operation executor: a single lazily-created thread,
 * so {@code execute()}d operations run one at a time in submission order —
 * the session is a stateful machine and concurrent operations on it are
 * invalid. The thread spins up on the first asynchronous operation and idles
 * out when unused, so a session that only ever runs synchronously (or a
 * refused {@code start()}) never costs a thread. {@link #shutdown()} is
 * called when the session ends; operations submitted after that are rejected
 * and fail through their handlers.</p>
 *
 * <p>Also tracks unexecuted operations: a forgotten {@code .execute()} is
 * the most common integration mistake with this API, so creating an
 * operation while a previous one is still unexecuted logs a warning.</p>
 */
final class SessionOperations {

    private static final Logger LOGGER = Logger.getLogger(SessionOperations.class.getName());

    /** Distinguishes session threads in dumps; sessions outnumber names. */
    private static final AtomicInteger SESSION_COUNTER = new AtomicInteger();

    private final AtomicReference<String> unexecuted = new AtomicReference<>();

    /** Core size 0 with max 1: single-threaded semantics, but the thread is
     *  created on first use and reclaimed after idling — a session whose
     *  operations all run synchronously never owns one. */
    private final ExecutorService operationExecutor = new ThreadPoolExecutor(
            0, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
            runnable -> {
                Thread thread = new Thread(runnable,
                        "bilt-session-" + SESSION_COUNTER.incrementAndGet());
                // a leaked session must not keep the JVM alive
                thread.setDaemon(true);
                return thread;
            });

    private Executor callbackExecutor;

    /** Session-wide default delivery for asynchronous operations' handlers;
     *  null means direct delivery on the operation thread. */
    void callbackExecutor(Executor callbackExecutor) {
        this.callbackExecutor = callbackExecutor;
    }

    /** Creates a lazy {@link SessionResult} tracked against this session. */
    <T> SessionResult<T> operation(String name, Supplier<T> body) {
        track(name);
        return new SessionResult<>(name, () -> {
            begin(name);
            return body.get();
        }).executors(operationExecutor, callbackExecutor);
    }

    /** The session's operation thread, for flows built outside [operation]. */
    Executor executor() {
        return operationExecutor;
    }

    /** The session-wide callback delivery default, for the same flows. */
    Executor callback() {
        return callbackExecutor;
    }

    /** Stops accepting asynchronous operations; called when the session
     *  ends. Queued operations still run (they fail their own session-ended
     *  checks); later submissions are rejected into their handlers. */
    void shutdown() {
        operationExecutor.shutdown();
    }

    /** Records a created operation, warning when the previous one never ran. */
    void track(String name) {
        String pending = unexecuted.getAndSet(name);
        if (pending != null) {
            LOGGER.warning("session operation '" + pending + "' was created but never "
                    + "executed; did you forget to call execute(), get(), or getOrNull()?");
        }
    }

    /** Marks the named operation as executing. */
    void begin(String name) {
        unexecuted.compareAndSet(name, null);
    }
}
