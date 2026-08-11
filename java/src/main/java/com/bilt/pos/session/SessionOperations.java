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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * The execution machinery behind a session's lazy operations, shared by the
 * session types.
 *
 * <p>Owns the session's operation executor: a single lazily-created thread,
 * so operations run one at a time in submission order — the session is a
 * stateful machine and concurrent operations on it are invalid. Every
 * execution funnels through it: {@code execute()} submits and returns,
 * while the synchronous paths ({@code executeSync()}, first-run
 * {@code get()}) submit through {@link #callOrdered} and block for their
 * turn — a sync call issued while an asynchronous operation is in flight
 * queues behind it instead of racing it. A sync call made from the
 * operation thread itself (an operation body or a direct-delivered handler
 * invoking another operation) runs inline: it is already serialized, and
 * waiting on its own thread would deadlock. The thread spins up on first
 * use and idles out when unused, so a session that never executes (or a
 * refused {@code start()}) never costs one. {@link #shutdown()} is called
 * when the session ends; asynchronous submissions after that are rejected
 * into their handlers, synchronous ones run inline and fail their own
 * session-ended checks.</p>
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

    /** Exactly one thread, created on first use and reclaimed after
     *  idling — a session whose operations all run synchronously never
     *  owns one. */
    private final ExecutorService operationExecutor = singleIdleThreadExecutor();

    private static ExecutorService singleIdleThreadExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable,
                            "bilt-session-" + SESSION_COUNTER.incrementAndGet());
                    // a leaked session must not keep the JVM alive
                    thread.setDaemon(true);
                    return thread;
                });
        // reclaimed when idle, still exactly one
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /** True while the current thread is running this session's operations —
     *  the reentrancy marker [callOrdered] consults. Per instance: a sync
     *  call into a DIFFERENT session from this session's thread must still
     *  queue on that session's executor, not run inline. */
    private final ThreadLocal<Boolean> onOperationThread =
            ThreadLocal.withInitial(() -> false);

    /** True while the current thread runs a handler the operation thread is
     *  awaiting (marshalled callback delivery). Such a handler is a
     *  synchronous extension of the in-flight operation — the operation
     *  thread is parked on this very handler — so a blocking session call
     *  made from it must run inline: queueing it behind the parked
     *  operation would deadlock, and running inline is race-free because
     *  nothing else of this session can be executing. */
    private final ThreadLocal<Boolean> inAwaitedHandler =
            ThreadLocal.withInitial(() -> false);

    /** [operationExecutor] with the on-thread marker around every task; all
     *  submissions — async and ordered-sync — go through this. */
    private final Executor marked = task -> operationExecutor.execute(() -> {
        onOperationThread.set(true);
        try {
            task.run();
        } finally {
            onOperationThread.set(false);
        }
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
        }).session(this);
    }

    /** The session's operation thread; where asynchronous executions run. */
    Executor executor() {
        return marked;
    }

    /** The session-wide callback delivery default. */
    Executor callback() {
        return callbackExecutor;
    }

    /**
     * Runs {@code body} on the operation thread and waits for its answer —
     * the synchronous paths' turnstile: they take their place in the same
     * queue as asynchronous executions instead of racing them from the
     * calling thread. Runs inline when already on the operation thread
     * (see the class docs) or when the executor has shut down — the body's
     * own session-ended checks then produce the right error.
     */
    <T> T callOrdered(Supplier<T> body) {
        if (onOperationThread.get() || inAwaitedHandler.get()) {
            return body.get();
        }
        // own started flag: FutureTask cannot tell queued from running —
        // its state stays NEW while the callable executes, so cancel(false)
        // "succeeds" against a body already on the wire
        AtomicBoolean bodyStarted = new AtomicBoolean();
        FutureTask<T> task = new FutureTask<>(() -> {
            bodyStarted.set(true);
            return body.get();
        });
        try {
            marked.execute(task);
        } catch (RejectedExecutionException e) {
            return body.get();
        }
        // An interrupt cannot stop the body — the old synchronous API
        // simply carried the wire call to completion on the calling thread,
        // interrupt flag set. A task that has not started is cancelled and
        // reported as interrupted (nothing ran, so that IS the outcome);
        // one already running must yield its real result — reporting an
        // interrupt while the terminal completes a payment would have the
        // register retry money that already moved. The wait continues
        // uninterruptibly and the flag is re-asserted on the way out.
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return task.get();
                } catch (InterruptedException e) {
                    if (!bodyStarted.get() && task.cancel(false)) {
                        interrupted = true;
                        throw new SessionException(new SessionError(SessionErrorCode.UNKNOWN,
                                "interrupted while waiting for the session's operation thread"));
                    }
                    interrupted = true;
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException) {
                        throw (RuntimeException) cause;
                    }
                    if (cause instanceof Error) {
                        throw (Error) cause;
                    }
                    throw new IllegalStateException(cause);
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Wraps a handler dispatch the operation thread will await, so
     *  blocking session calls made inside the handler run inline — see
     *  [inAwaitedHandler]. Applied only to marshalled (callback-executor)
     *  dispatches: those are the ones that park the operation thread. */
    <R> Supplier<R> awaitedHandler(Supplier<R> handler) {
        return () -> {
            inAwaitedHandler.set(true);
            try {
                return handler.get();
            } finally {
                inAwaitedHandler.set(false);
            }
        };
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
