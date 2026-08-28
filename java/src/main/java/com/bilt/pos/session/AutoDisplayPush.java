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
import java.util.function.BooleanSupplier;

/**
 * The asynchronous customer-display push behind {@code autoDisplay}, shared
 * by the session types that own a basket.
 *
 * <p>Sends run on the session's operation lane — not the unordered one — so
 * pushes cannot race their snapshots out of order and never land mid-payment
 * over the terminal's payment screen. Pushes are conflated: since mutations
 * return without waiting, a fast ring-up outruns the terminal roundtrips,
 * and an uncapped queue would grow with stale sends that a {@code settle()}
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
    private final BooleanSupplier current;

    /**
     * @param current whether the snapshot still belongs on the display. A
     *        push that outlived its basket or session sends nothing because
     *        the settlement display or End bracket supersedes it.
     */
    AutoDisplayPush(SessionOperations operations, BasketDisplay display,
                    BooleanSupplier current) {
        this.operations = operations;
        this.display = display;
        this.current = current;
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
        if (!current.getAsBoolean()) {
            return;
        }
        try {
            display.show(snapshot);
        } catch (RuntimeException e) {
            operations.backgroundError("the automatic display push", e);
        }
    }
}
