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

import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.session.internal.DisplayRouter;
import com.bilt.pos.session.internal.NexoExchange;
import com.bilt.pos.session.internal.NexoMessageFactory;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * A terminal's device and admin operations — diagnostics, totals,
 * reconciliation, printing, sound — outside any session.
 *
 * <p>These map to SERVICE/DEVICE-class nexo messages that carry no session
 * reference on the wire, so unlike a {@link CheckoutSession} there is no
 * bracket: {@link Builder#build() build()} sends nothing, and the terminal
 * holds no state on this object's behalf. Use it for a connectivity ping
 * before the first checkout, end-of-day reconciliation with no customer
 * present, or a receipt reprint after the session that took the payment has
 * ended.</p>
 *
 * <p>Operations are lazy like every session operation: methods returning a
 * {@link SessionResult} send nothing until {@code execute()},
 * {@code executeSync()}, {@code get()}, or {@code getOrNull()} is invoked.
 * Asynchronous executions run one at a time on the terminal's own operation
 * thread and deliver their handlers through the configured
 * {@link Builder#callbackExecutor(Executor) callbackExecutor}, with the same
 * semantics as the session builders.</p>
 *
 * <pre>{@code
 * Terminal terminal = Terminal.builder()
 *         .client(client)
 *         .saleId("POS-LANE-3")
 *         .poiId("VictaLane-275839164")
 *         .build();
 *
 * terminal.diagnose()
 *         .onSuccess(d -> register.showTerminalStatus(d.getPoiStatus()))
 *         .onError(e -> register.showUnreachable(e.getMessage()))
 *         .execute();
 * }</pre>
 *
 * <p>{@link #close()} sends nothing — there is nothing to end on the
 * terminal — it only stops this object accepting further operations.
 * Mid-checkout, {@link CheckoutSession#terminal()} keeps these operations
 * one call away.</p>
 */
public final class Terminal implements AutoCloseable {

    private final SessionOperations operations;
    private final BiltNexoTerminalClient client;
    private final TerminalServices services;

    private volatile boolean closed;

    private Terminal(Builder builder) {
        this.operations = new SessionOperations(builder.callbackExecutor);
        this.client = builder.client;
        NexoMessageFactory factory = new NexoMessageFactory(
                builder.saleId, builder.poiId, builder.storeLocation);
        NexoExchange exchange = new NexoExchange(
                new DisplayRouter(builder.client, null), factory);
        this.services = new TerminalServices(exchange, factory, builder.storeLocation);
    }

    public static Builder builder() {
        return new Builder();
    }

    // ─── Diagnostics & Admin ───

    /** Queries terminal health and host reachability. */
    public SessionResult<DiagnosisResult> diagnose() {
        return operation("diagnose", services::diagnose);
    }

    /**
     * Queries the terminal's running totals since the last reconciliation
     * (Nexo {@code GetTotals}) — lighter than {@link #reconcile()}, which
     * closes the period. Filtered by this terminal's {@code SaleID} and,
     * when configured, the {@link Builder#storeLocation(String)
     * storeLocation} as {@code TotalsGroupID}.
     */
    public SessionResult<ReconciliationResult> getTotals() {
        return operation("getTotals", services::getTotals);
    }

    /** Runs a sale reconciliation (end-of-period totals). */
    public SessionResult<ReconciliationResult> reconcile() {
        return operation("reconcile", services::reconcile);
    }

    // ─── Print ───

    /** Prints a document on the terminal printer. */
    public SessionResult<Void> print(PrintPayload payload) {
        Objects.requireNonNull(payload, "payload");
        return operation("print", () -> services.print(payload));
    }

    // ─── Sound ───

    /** Plays a pre-provisioned sound on the terminal by its reference ID. */
    public SessionResult<Void> playSound(String soundReferenceId) {
        return playSound(soundReferenceId, null);
    }

    /**
     * Plays a pre-provisioned sound on the terminal.
     *
     * @param volumePercent volume 0–100, or {@code null} for the terminal default
     */
    public SessionResult<Void> playSound(String soundReferenceId, Integer volumePercent) {
        Objects.requireNonNull(soundReferenceId, "soundReferenceId");
        if (volumePercent != null && (volumePercent < 0 || volumePercent > 100)) {
            throw new IllegalArgumentException("volumePercent must be between 0 and 100");
        }
        return operation("playSound",
                () -> services.playSound(soundReferenceId, volumePercent));
    }

    /** Stops any sound currently playing on the terminal. */
    public SessionResult<Void> stopSound() {
        return operation("stopSound", services::stopSound);
    }

    // ─── Lifecycle ───

    /**
     * Stops accepting operations. Sends nothing — the terminal holds no
     * state for this object, so there is nothing to end — and is
     * idempotent. Operations executed after this fail with
     * {@link SessionErrorCode#INVALID_STATE} through their normal
     * {@code onError}/{@code onComplete} delivery.
     */
    @Override
    public void close() {
        closed = true;
        operations.shutdown();
    }

    // ─── Escape hatch ───

    /** The underlying terminal client, for raw Nexo access. */
    public BiltNexoTerminalClient getClient() {
        return client;
    }

    // ─── Internals ───

    private <T> SessionResult<T> operation(String name, Supplier<T> body) {
        // the closed check lives in the body: asynchronous executions after
        // close() are rejected by the shut-down executor (existing
        // machinery), while the synchronous paths run inline after shutdown
        // and rely on this guard
        return operations.operation(name, () -> {
            if (closed) {
                throw new SessionException(new SessionError(SessionErrorCode.INVALID_STATE,
                        name + " is not allowed after close(); create a new Terminal"));
            }
            return body.get();
        });
    }

    /** Builder for {@link Terminal}. */
    public static final class Builder {

        private BiltNexoTerminalClient client;
        private String saleId;
        private String poiId;
        private String storeLocation;
        private Executor callbackExecutor;

        private Builder() {
        }

        /** The terminal client. Required. */
        public Builder client(BiltNexoTerminalClient client) {
            this.client = client;
            return this;
        }

        /** POS identifier sent as {@code SaleID}. Required. */
        public Builder saleId(String saleId) {
            this.saleId = saleId;
            return this;
        }

        /** Target terminal identifier sent as {@code POIID}. Required. */
        public Builder poiId(String poiId) {
            this.poiId = poiId;
            return this;
        }

        /**
         * Store location identifier: the {@code TotalsGroupID} that
         * {@link Terminal#getTotals()} filters by. Optional.
         */
        public Builder storeLocation(String storeLocation) {
            this.storeLocation = storeLocation;
            return this;
        }

        /**
         * Where asynchronously executed operations deliver their handlers —
         * see {@code CheckoutSession.Builder#callbackExecutor}. Applies to
         * {@code execute()}; {@code executeSync()} and the blocking
         * accessors are unaffected.
         */
        public Builder callbackExecutor(Executor callbackExecutor) {
            this.callbackExecutor = callbackExecutor;
            return this;
        }

        /**
         * Validates the configuration and returns the terminal. Sends
         * nothing — there is no session bracket to announce.
         *
         * @throws IllegalStateException if a required field is missing
         */
        public Terminal build() {
            if (client == null) {
                throw new IllegalStateException("client is required");
            }
            if (saleId == null || saleId.isEmpty()) {
                throw new IllegalStateException("saleId is required");
            }
            if (poiId == null || poiId.isEmpty()) {
                throw new IllegalStateException("poiId is required");
            }
            return new Terminal(this);
        }
    }
}
