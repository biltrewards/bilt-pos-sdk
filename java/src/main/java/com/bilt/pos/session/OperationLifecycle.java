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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The single-shot lifecycle shared by every lazy session operation —
 * {@link SessionResult} and the {@link SessionFlow} family hold one by
 * composition. It owns the invariants that must not drift between them:
 *
 * <ul>
 *   <li>the start claim — one run, ever; registration only before it;</li>
 *   <li>the settled latch accessors await — opened by the owner only once
 *       the outcome is final, handler dispatch included, so a released
 *       accessor can never report a clean success past a pending handler
 *       failure;</li>
 *   <li>the exactly-once {@code onComplete} hook — every completion path,
 *       rejection included; a throwing hook is logged, never propagated;</li>
 *   <li>the rejected-at-submission failure shape.</li>
 * </ul>
 *
 * <p>What stays with the owners is deliberate, not accidental: their
 * handler contracts differ. {@code SessionResult} delivers outcome
 * consumers ({@code Consumer}-shaped {@code onSuccess}/{@code onError})
 * with {@code releasing()} cleanup and the {@code unordered()} escape;
 * the flows deliver value-returning negotiation handlers whose answers
 * steer the sequence, with per-flow failure-delivery policy. Their run
 * orchestration therefore differs on purpose — the mechanics below are
 * what they must always agree on.</p>
 */
final class OperationLifecycle {

    private static final Logger LOGGER = Logger.getLogger(OperationLifecycle.class.getName());

    /** Display name for messages: an operation name ("acquireCard") or a
     *  flow label ("the payment"). */
    private final String name;

    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean completeDispatched = new AtomicBoolean();

    /** Opens once the outcome is final — body AND handler dispatch, since
     *  a throwing handler is recorded as the outcome; accessors await it. */
    private final CountDownLatch settled = new CountDownLatch(1);

    private Runnable completeHandler;

    OperationLifecycle(String name) {
        this.name = name;
    }

    String name() {
        return name;
    }

    /** Claims the single run quietly; false when it already ran. */
    boolean claim() {
        return started.compareAndSet(false, true);
    }

    /** Claims the single run for a terminal method; throws when repeated. */
    void claimStart() {
        if (!claim()) {
            throw new IllegalStateException(name + " has already been executed");
        }
    }

    /** Guard for the fluent registration methods. */
    void guardRegistration() {
        if (started.get()) {
            throw new IllegalStateException(
                    "handlers must be registered before " + name + " is executed");
        }
    }

    /** Registers the exactly-once completion hook (inside the guard). */
    void completeHandler(Runnable handler) {
        this.completeHandler = handler;
    }

    boolean hasCompleteHandler() {
        return completeHandler != null;
    }

    /** The owner declares the outcome final; waiting accessors release. */
    void openSettled() {
        settled.countDown();
    }

    /** Blocks an accessor until the outcome is final. */
    void awaitSettled() {
        try {
            settled.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SessionException(new SessionError(SessionErrorCode.UNKNOWN,
                    "interrupted while waiting for " + name + " to settle"));
        }
    }

    /** Exactly once, on every path; a throwing hook is logged, not thrown —
     *  it must neither mask the operation's outcome nor skip on retry. */
    void dispatchComplete() {
        if (completeHandler == null || !completeDispatched.compareAndSet(false, true)) {
            return;
        }
        try {
            completeHandler.run();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, name + "'s onComplete hook threw", e);
        }
    }

    /** The failure recorded when the session's executor rejected the
     *  submission — the session ended before the operation could run. */
    SessionError rejected() {
        return new SessionError(SessionErrorCode.INVALID_STATE,
                name + " was not run: the session has ended");
    }
}
