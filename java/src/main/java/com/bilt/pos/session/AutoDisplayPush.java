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

import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.internal.BasketDisplay;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * The asynchronous customer-display push behind {@code autoDisplay}, shared
 * by the session types that own a basket.
 *
 * <p>A basket mutation stays pure local compute — it returns its snapshot
 * immediately and {@link #push(Basket)}es it here, safe on a UI thread. The
 * send runs on the session's operation lane, so pushes cannot race their
 * snapshots out of order and can never land mid-payment and overwrite the
 * terminal's payment screen; an operation executed after a ring-up queues
 * behind the pending push.</p>
 *
 * <p>Pushes are conflated: the newest snapshot is held in a single slot and
 * a send task is enqueued only when none is queued; the task sends whatever
 * is newest when it runs. This is load-bearing, not polish — mutations are
 * instant, so a fast ring-up outruns the terminal roundtrips, and without
 * conflation the queue would grow with stale sends that a {@code pay()}
 * must wait behind. With it there is at most one send queued plus one in
 * flight, a queued operation waits at most one roundtrip, and the customer
 * display skips straight to the current state.</p>
 *
 * <p>Best-effort throughout: a failed push is logged and reported through
 * the session's background-error handler (see
 * {@link SessionOperations#backgroundError}), never interrupting the
 * checkout. A task that finds the session ended sends nothing — queued
 * tasks still run after the executor's shutdown, and a push after the End
 * bracket is pointless wire noise.</p>
 */
final class AutoDisplayPush {

    /** The pending (newest) snapshot; non-null means a send task is queued
     *  or has not consumed it yet. Mutations are serialized by the owning
     *  session (a checkout pushes under its basket lock), so writers do
     *  not race each other. */
    private final AtomicReference<Basket> pending = new AtomicReference<>();

    private final SessionOperations operations;
    private final BasketDisplay display;
    private final Supplier<SessionState> state;

    AutoDisplayPush(SessionOperations operations, BasketDisplay display,
                    Supplier<SessionState> state) {
        this.operations = operations;
        this.display = display;
        this.state = state;
    }

    /** Records the snapshot and enqueues a send when none is queued. */
    void push(Basket snapshot) {
        if (pending.getAndSet(snapshot) != null) {
            // a task is already queued or about to consume; it will send
            // this newer snapshot (or one newer still) when it runs
            return;
        }
        try {
            operations.executor().execute(this::send);
        } catch (RejectedExecutionException e) {
            // the session ended and shut its executor down; the push is
            // pointless, not an error
            pending.set(null);
        }
    }

    private void send() {
        Basket snapshot = pending.getAndSet(null);
        if (snapshot == null) {
            // conflated away: an earlier task already sent a newer snapshot
            return;
        }
        SessionState current = state.get();
        if (current == SessionState.ENDED) {
            return;
        }
        try {
            display.show(snapshot, current);
        } catch (RuntimeException e) {
            operations.backgroundError("the automatic display push", e);
        }
    }
}
