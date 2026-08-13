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
 * <p>Sends run on the session's operation lane — not the unordered one — so
 * pushes cannot race their snapshots out of order and never land mid-payment
 * over the terminal's payment screen. Pushes are conflated: since mutations
 * return without waiting, a fast ring-up outruns the terminal roundtrips,
 * and an uncapped queue would grow with stale sends that a {@code pay()}
 * must wait behind. Conflation bounds this to at most one send queued plus
 * one in flight, whichever runs sending the newest snapshot.</p>
 *
 * <p>Failures are best-effort — logged and reported through
 * {@link SessionOperations#backgroundError}, never interrupting the
 * checkout.</p>
 */
final class AutoDisplayPush {

    /** Non-null while a send task is queued but has not yet claimed its
     *  snapshot. Writers are serialized by the owning session (a checkout
     *  pushes under its basket lock). */
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

    void push(Basket snapshot) {
        if (pending.getAndSet(snapshot) != null) {
            // conflated: the already-queued task sends this newer snapshot
            return;
        }
        try {
            operations.executor().execute(this::send);
        } catch (RejectedExecutionException e) {
            // the session ended; the push is pointless, not an error
            pending.set(null);
        }
    }

    private void send() {
        Basket snapshot = pending.getAndSet(null);
        if (snapshot == null) {
            return;
        }
        SessionState current = state.get();
        if (current == SessionState.ENDED) {
            // queued tasks still run after the executor's shutdown, but a
            // push after the End bracket is pointless wire noise
            return;
        }
        try {
            display.show(snapshot, current);
        } catch (RuntimeException e) {
            operations.backgroundError("the automatic display push", e);
        }
    }
}
