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
import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketMutation;
import com.bilt.pos.session.display.DisplayRenderer;
import com.bilt.pos.session.internal.BasketDisplay;
import com.bilt.pos.session.internal.BasketDisplayRenderer;
import com.bilt.pos.session.internal.BasketEngine;
import com.bilt.pos.session.internal.DisplayRouter;
import com.bilt.pos.session.internal.NexoExchange;
import com.bilt.pos.session.internal.NexoMessageFactory;
import com.bilt.pos.session.internal.PoiRef;
import com.bilt.pos.session.internal.ReversalManager;
import com.bilt.pos.session.internal.ReversalMovement;
import com.bilt.pos.session.internal.SaleItemMapper;
import com.bilt.pos.session.internal.SessionSignalCodec;
import com.bilt.pos.session.internal.SessionStateMachine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * A referenced reversal of a prior sale — void or refund from a fresh
 * session, in a later process, long after the {@link CheckoutSession} that
 * took the payment is gone.
 *
 * <p>The builder carries the POI references the POS persisted from the
 * original sale's {@code CheckoutResult}: the card and stored value (gift
 * card) legs, the committed rebate and redemption movements, the loyalty
 * award, and the member. {@link #voidTransaction()} reverses every
 * referenced movement; {@link #refund()}/{@link #refund(BigDecimal)} return
 * money against the card leg and reverse the award. Reversing the payment
 * this session's own checkout just took needs no references —
 * {@code CheckoutSession} does that itself.</p>
 *
 * <p>For an item-based refund, ring the returned items into the session's
 * refund cart ({@link #basket()}) — every line is a credit line, so each
 * mutation refreshes the customer display with negative amounts — and
 * execute with {@link #refundBasket()}, which refunds the cart total
 * against the card leg with the returned items attached. Which items and
 * quantities may be returned is the register's call; the cart itself is
 * free-form.</p>
 *
 * <p>Like a checkout, the session is bracketed on the terminal: the
 * builder's {@link Builder#start() start()} announces it (Nexo {@code Admin}
 * session start signal) and {@link #end()} tells the terminal to discard its
 * session-scoped data; try-with-resources sends the end signal even on
 * exception paths. All operations are lazy — nothing is sent until
 * {@code execute()}, {@code get()}, or {@code getOrNull()} is invoked — and
 * run blocking on the calling thread.</p>
 *
 * <pre>{@code
 * try (ReversalSession session = ReversalSession.builder()
 *         .client(client)
 *         .saleId("POS-LANE-3")
 *         .poiId("VictaLane-275839164")
 *         .currency("USD")
 *         .poiTransactionId(stored.cardTxnId)
 *         .poiTransactionTimestamp(stored.cardTs)
 *         .redemptionPoiTransactionId(stored.redemptionTxnId)
 *         .awardPoiTransactionId(stored.awardTxnId)
 *         .memberId(stored.memberId)
 *         .start()
 *         .get()) {
 *     session.voidTransaction().execute();
 * }
 * }</pre>
 *
 * <p>Sessions are intended for use from a single register thread;
 * {@link #abort()} is the one method safe to call from another thread.</p>
 */
public final class ReversalSession implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(ReversalSession.class.getName());

    private final String sessionId = UUID.randomUUID().toString();
    private final SessionStateMachine stateMachine = new SessionStateMachine();
    private final SessionOperations operations = new SessionOperations();

    private final BiltNexoTerminalClient client;
    private final NexoExchange exchange;
    private final ReversalManager reversalManager;
    // the refund cart: a credit-side engine — every ring is a return
    private final BasketEngine basketEngine = new BasketEngine(true);
    private final SessionBasket basket;
    private final BasketDisplay display;
    private final boolean autoDisplay;
    private final String currency;
    private final String storeLocation;

    private final String poiTransactionId;
    private final Instant poiTransactionTimestamp;
    private final String storedValuePoiTransactionId;
    private final Instant storedValuePoiTransactionTimestamp;
    private final String rebatePoiTransactionId;
    private final Instant rebatePoiTransactionTimestamp;
    private final String redemptionPoiTransactionId;
    private final Instant redemptionPoiTransactionTimestamp;
    private final String awardPoiTransactionId;
    private final Instant awardPoiTransactionTimestamp;
    private final String memberId;

    // refund/void mutual exclusion and void-resume progress for the
    // referenced sale (see ReversalGuards)
    private final ReversalGuards guards = new ReversalGuards("sale");

    private ReversalSession(Builder builder) {
        this.operations.callbackExecutor(builder.callbackExecutor);
        this.client = builder.client;
        this.currency = builder.currency;
        this.storeLocation = builder.storeLocation;
        this.poiTransactionId = builder.poiTransactionId;
        this.poiTransactionTimestamp = builder.poiTransactionTimestamp;
        this.storedValuePoiTransactionId = builder.storedValuePoiTransactionId;
        this.storedValuePoiTransactionTimestamp = builder.storedValuePoiTransactionTimestamp;
        this.rebatePoiTransactionId = builder.rebatePoiTransactionId;
        this.rebatePoiTransactionTimestamp = builder.rebatePoiTransactionTimestamp;
        this.redemptionPoiTransactionId = builder.redemptionPoiTransactionId;
        this.redemptionPoiTransactionTimestamp = builder.redemptionPoiTransactionTimestamp;
        this.awardPoiTransactionId = builder.awardPoiTransactionId;
        this.awardPoiTransactionTimestamp = builder.awardPoiTransactionTimestamp;
        this.memberId = builder.memberId;
        NexoMessageFactory factory = new NexoMessageFactory(
                builder.saleId, builder.poiId, builder.storeLocation);
        this.exchange = new NexoExchange(
                new DisplayRouter(builder.client, builder.externalDisplayClient), factory);
        this.reversalManager = new ReversalManager(exchange, builder.currency);
        this.autoDisplay = builder.autoDisplay;
        this.display = new BasketDisplay(exchange,
                builder.displayRenderer != null
                        ? builder.displayRenderer : new BasketDisplayRenderer(),
                builder.currency);
        this.basket = new SessionBasket(new SessionBasket.Host() {
            @Override
            public Basket mutate(Consumer<BasketMutation> mutation) {
                SessionState state = stateMachine.current();
                if (state != SessionState.IDLE) {
                    throw new IllegalStateException(
                            "the refund cart cannot be modified in state " + state);
                }
                basketEngine.mutateAtomically(mutation);
                Basket snapshot = basketEngine.snapshot();
                if (autoDisplay) {
                    display.show(snapshot, stateMachine.current());
                }
                return snapshot;
            }

            @Override
            public Basket snapshot() {
                return basketEngine.snapshot();
            }
        });
    }

    public static Builder builder() {
        return new Builder();
    }

    // ─── State ───

    /** The session's current lifecycle state. */
    public SessionState getState() {
        return stateMachine.current();
    }

    /** Unique identifier of this session instance. */
    public String getSessionId() {
        return sessionId;
    }

    /** ISO 4217 currency code used by this session. */
    public String getCurrency() {
        return currency;
    }

    /** Store location identifier, or {@code null} if not configured. */
    public String getStoreLocation() {
        return storeLocation;
    }

    // ─── Refund cart ───

    /**
     * The session's refund cart: the items being returned, rung in like a
     * checkout basket. Every line is a credit line — totals are negative
     * and, with {@link Builder#autoDisplay(boolean) autoDisplay} enabled
     * (the default), each mutation refreshes the customer display with the
     * itemised returns. Mutations are allowed until a void or {@code end()}
     * seals the session; {@link #refundBasket()} consumes the cart. The
     * cart is free-form — whether an item may be returned, and in what
     * quantity, is the register's decision.
     */
    public SessionBasket basket() {
        return basket;
    }

    // ─── Void ───

    /**
     * Reverses every referenced movement of the original sale, in order:
     * the card and stored value legs (Nexo {@code ReversalRequest}), then
     * the committed redemption, rebate, and award (their
     * {@code LoyaltyRequest} refund types). Only the movements a reference
     * was supplied for are reversed; a sale with no money leg (rewards
     * covered everything) is voided by its loyalty references alone.
     *
     * <p>When a step fails, the flow's {@link ReversalFlow#onError onError}
     * handler decides between retry, skip, and abort — see
     * {@link ReversalFlow} for the default policy. A retried void resumes
     * at the first movement still standing — reversed movements are never
     * re-credited, and the retry's {@link VoidResult} describes only the
     * movements that call sent. Not allowed once a refund has returned
     * money from this session. The session moves to
     * {@link SessionState#VOIDED} on success.</p>
     */
    public ReversalFlow<VoidResult> voidTransaction() {
        operations.track("voidTransaction");
        return new ReversalFlow<>(this::executeVoid)
                .operationExecutor(operations.executor())
                .callbackExecutor(operations.callback());
    }

    private VoidResult executeVoid(ReversalFlow<VoidResult> flow) {
        operations.begin("voidTransaction");
        // state first: an ended or voided session is reported as such,
        // not by a guard whose remedy the state would also refuse
        requireState(EnumSet.of(SessionState.IDLE), "voidTransaction");
        guards.requireNotRefunded();
        stateMachine.transitionTo(SessionState.VOIDING);
        try {
            // the manager filters against the reversed-steps set (and
            // records progress into it), so a retry resumes at the
            // movements still standing while the default policy still sees
            // the whole target
            VoidResult result = reversalManager.voidMovements(referencedMovements(), memberId,
                    flow.decider(), guards.reversedSteps());
            stateMachine.transitionTo(SessionState.VOIDED);
            return result;
        } catch (RuntimeException e) {
            // a failed void leaves the referenced sale standing; the session
            // returns to IDLE so the void can be retried
            stateMachine.transitionTo(SessionState.IDLE);
            throw e;
        }
    }

    // ─── Refund ───

    /**
     * Full linked refund of the referenced card leg. Also reverses the
     * loyalty award when {@code awardPoiTransactionId} was supplied —
     * best-effort by default (override via {@link ReversalFlow#onError}).
     *
     * <p>Linked refunds reference a single transaction — the card leg. Use
     * {@link #voidTransaction()} to reverse every movement of the sale,
     * including its rebate and redemption. Repeated partial refunds are
     * allowed (the acquirer enforces the cumulative limit), but once a
     * refund has returned money the sale can no longer be voided from this
     * session. A tender skipped by an {@code onError} decision leaves the
     * sale voidable, and an award the flow reversed is remembered — neither
     * a void nor a retried refund re-credits it. Once a void has partially
     * reversed the sale's money legs, refunds are refused until the void
     * is finished.</p>
     */
    public ReversalFlow<RefundResult> refund() {
        operations.track("refund");
        return new ReversalFlow<RefundResult>(flow -> executeRefund(flow, null))
                .operationExecutor(operations.executor())
                .callbackExecutor(operations.callback());
    }

    /** Partial linked refund of the referenced card leg. */
    public ReversalFlow<RefundResult> refund(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("refund amount must be positive");
        }
        operations.track("refund");
        return new ReversalFlow<RefundResult>(flow -> executeRefund(flow, amount))
                .operationExecutor(operations.executor())
                .callbackExecutor(operations.callback());
    }

    private RefundResult executeRefund(ReversalFlow<RefundResult> flow, BigDecimal amount) {
        operations.begin("refund");
        requireLinkedRefundable();
        // the void guard follows the money — see ReversalGuards
        boolean awardReversed = guards.awardReversed();
        return reversalManager.refund(amount, null,
                poiTransactionId, poiTransactionTimestamp,
                awardReversed ? null : awardPoiTransactionId,
                awardReversed ? null : awardPoiTransactionTimestamp,
                memberId, flow.decider(),
                guards::markRefunded,
                guards::markAwardReversed);
    }

    /**
     * Item-based linked refund: refunds the refund cart's total against the
     * referenced card leg, with the returned items attached to the refund's
     * {@code PaymentRequest}. Also reverses the loyalty award when
     * {@code awardPoiTransactionId} was supplied — best-effort by default,
     * like {@link #refund()}, and subject to the same guards.
     *
     * <p>The cart is consumed (cleared, with a display refresh under
     * {@code autoDisplay}) the moment the tender refund moves money — even
     * when a later award step aborts the flow, so a retry can never re-send
     * the same returns. While no money has moved — the refund failed, or
     * the tender was skipped by an {@code onError} decision — the cart
     * stays intact for a retry. Ring the next return into {@link #basket()}
     * for a further partial refund — the acquirer enforces the cumulative
     * limit.</p>
     */
    public ReversalFlow<RefundResult> refundBasket() {
        operations.track("refundBasket");
        return new ReversalFlow<>(this::executeRefundBasket)
                .operationExecutor(operations.executor())
                .callbackExecutor(operations.callback());
    }

    private RefundResult executeRefundBasket(ReversalFlow<RefundResult> flow) {
        operations.begin("refundBasket");
        requireLinkedRefundable();
        Basket cart = basketEngine.snapshot();
        if (cart.isEmpty()) {
            throw invalidState("refundBasket() requires items in the refund cart");
        }
        // cart lines are credits, so the total is negative; the refund
        // amount is its magnitude
        BigDecimal amount = cart.getGrandTotal().negate();
        if (amount.signum() <= 0) {
            throw invalidState("the refund cart total is not positive "
                    + "(zero-priced items only); a refund cannot start");
        }
        boolean awardReversed = guards.awardReversed();
        // the cart is consumed the moment the tender refund moves money —
        // even when a later award step aborts the flow, a retry must not
        // re-send the same returns — and only then: a tender skipped by
        // decision leaves the cart intact, or the returns could never be
        // refunded at all
        boolean[] tenderRefunded = {false};
        try {
            return reversalManager.refund(amount,
                    SaleItemMapper.toRefundSaleItems(cart),
                    poiTransactionId, poiTransactionTimestamp,
                    awardReversed ? null : awardPoiTransactionId,
                    awardReversed ? null : awardPoiTransactionTimestamp,
                    memberId, flow.decider(),
                    () -> {
                        guards.markRefunded();
                        tenderRefunded[0] = true;
                    },
                    guards::markAwardReversed);
        } finally {
            if (tenderRefunded[0]) {
                basketEngine.clear();
                if (autoDisplay) {
                    display.show(basketEngine.snapshot(), stateMachine.current());
                }
            }
        }
    }

    private void requireLinkedRefundable() {
        requireState(EnumSet.of(SessionState.IDLE), "refund");
        guards.requireNoReversedMoneyLeg();
        if (poiTransactionId == null) {
            throw invalidState(
                    "a linked refund requires poiTransactionId on the session builder");
        }
    }

    /** The referenced movements of the original sale, in reversal order. */
    private List<ReversalMovement> referencedMovements() {
        return ReversalMovement.ofSale(
                PoiRef.ofNullable(poiTransactionId, poiTransactionTimestamp),
                PoiRef.ofNullable(storedValuePoiTransactionId, storedValuePoiTransactionTimestamp),
                PoiRef.ofNullable(redemptionPoiTransactionId, redemptionPoiTransactionTimestamp),
                PoiRef.ofNullable(rebatePoiTransactionId, rebatePoiTransactionTimestamp),
                PoiRef.ofNullable(awardPoiTransactionId, awardPoiTransactionTimestamp));
    }

    // ─── Abort ───

    /**
     * Best-effort abort of the in-flight request. Money-moving operations
     * always deliver their outcome even when the abort raced them: the
     * movement may have completed on the terminal, and the register must
     * know. Safe to call from any thread; with nothing in flight this is a
     * no-op.
     */
    public void abort() {
        exchange.abortInFlight();
    }

    // ─── Session lifecycle ───

    /**
     * Announces this session to the terminal. Invoked by the builder's
     * {@link Builder#start() start()}.
     */
    private ReversalSession started() {
        exchange.sendSessionSignal(SessionSignalCodec.start(sessionId));
        return this;
    }

    /**
     * Ends the session: tells the terminal to discard the session-scoped
     * data it accumulated and moves the session to
     * {@link SessionState#ENDED}, from which no operation is allowed. Not
     * allowed while a void is in flight (state {@code VOIDING}). If the
     * end signal fails, the session keeps its current state so the call
     * can be retried.
     */
    public SessionResult<Void> end() {
        return operation("end", () -> {
            SessionState state = stateMachine.current();
            if (state == SessionState.VOIDING) {
                throw invalidState("end() is not allowed while a void is in "
                        + "flight (state " + state + ")");
            }
            if (state == SessionState.ENDED) {
                throw invalidState("the session has already ended; create a new session");
            }
            exchange.sendSessionSignal(SessionSignalCodec.end(sessionId));
            stateMachine.transitionTo(SessionState.ENDED);
            // no further operations may run; asynchronous submissions after
            // this fail into their handlers instead of queueing forever
            operations.shutdown();
            return null;
        });
    }

    /**
     * Best-effort {@link #end()} for try-with-resources: a failure to send
     * the end signal is logged, not thrown, and an already-ended session is
     * left alone.
     */
    @Override
    public void close() {
        if (stateMachine.current() == SessionState.ENDED) {
            return;
        }
        // synchronous deliberately: close() runs on teardown paths where a
        // queued async end would be lost with the closing scope
        end().onError(e -> LOGGER.warning("close() could not end the session: " + e))
                .executeSync();
    }

    // ─── Escape hatch ───

    /** The underlying terminal client, for raw Nexo access. */
    public BiltNexoTerminalClient getClient() {
        return client;
    }

    // ─── Internals ───

    private void requireState(Set<SessionState> allowed, String operationName) {
        SessionError error = stateMachine.requireState(allowed, operationName);
        if (error != null) {
            throw new SessionException(error);
        }
    }

    private static SessionException invalidState(String message) {
        return new SessionException(
                new SessionError(SessionErrorCode.INVALID_STATE, message));
    }

    private <T> SessionResult<T> operation(String name, Supplier<T> body) {
        return operations.operation(name, body);
    }

    /** Builder for {@link ReversalSession}. */
    public static final class Builder {

        private BiltNexoTerminalClient client;
        private String saleId;
        private String poiId;
        private String currency;
        private String storeLocation;
        private boolean autoDisplay = true;
        private BiltNexoTerminalClient externalDisplayClient;
        private DisplayRenderer displayRenderer;
        private String poiTransactionId;
        private Instant poiTransactionTimestamp;
        private String storedValuePoiTransactionId;
        private Instant storedValuePoiTransactionTimestamp;
        private String rebatePoiTransactionId;
        private Instant rebatePoiTransactionTimestamp;
        private String redemptionPoiTransactionId;
        private Instant redemptionPoiTransactionTimestamp;
        private String awardPoiTransactionId;
        private Instant awardPoiTransactionTimestamp;
        private String memberId;
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

        /** ISO 4217 currency code, e.g. {@code "USD"}. Required. */
        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        /**
         * Store location identifier, sent as
         * {@code SaleTerminalData.TotalsGroupID} on every transaction this
         * session creates. Optional.
         */
        public Builder storeLocation(String storeLocation) {
            this.storeLocation = storeLocation;
            return this;
        }

        /**
         * Whether refund cart mutations automatically refresh the customer
         * display. Default {@code true}.
         */
        public Builder autoDisplay(boolean autoDisplay) {
            this.autoDisplay = autoDisplay;
            return this;
        }

        /**
         * A second client for an external customer display device. When set,
         * {@code Display} messages are routed to it, while the money and
         * loyalty operations stay on the terminal.
         */
        public Builder externalDisplayClient(BiltNexoTerminalClient externalDisplayClient) {
            this.externalDisplayClient = externalDisplayClient;
            return this;
        }

        /**
         * Custom rendering of refund cart snapshots for the customer
         * display. Rarely necessary; defaults to the standard itemised
         * receipt, whose credit lines print negative.
         */
        public Builder displayRenderer(DisplayRenderer displayRenderer) {
            this.displayRenderer = displayRenderer;
            return this;
        }

        /**
         * Terminal reference of the original card payment (from
         * {@code CheckoutResult.getPoiTransactionId()}). Required for
         * {@code refund()}; a void reverses this leg with a Nexo
         * {@code ReversalRequest}.
         */
        public Builder poiTransactionId(String poiTransactionId) {
            this.poiTransactionId = poiTransactionId;
            return this;
        }

        /** Timestamp of the payment referenced by {@link #poiTransactionId}. */
        public Builder poiTransactionTimestamp(Instant poiTransactionTimestamp) {
            this.poiTransactionTimestamp = poiTransactionTimestamp;
            return this;
        }

        /**
         * Terminal reference of the original sale's stored value (gift
         * card) leg (from
         * {@code CheckoutResult.getStoredValuePoiTransactionId()}). Lets a
         * void reverse the gift card charge of a split tender.
         */
        public Builder storedValuePoiTransactionId(String storedValuePoiTransactionId) {
            this.storedValuePoiTransactionId = storedValuePoiTransactionId;
            return this;
        }

        /** Timestamp of the leg referenced by {@link #storedValuePoiTransactionId}. */
        public Builder storedValuePoiTransactionTimestamp(
                Instant storedValuePoiTransactionTimestamp) {
            this.storedValuePoiTransactionTimestamp = storedValuePoiTransactionTimestamp;
            return this;
        }

        /**
         * Terminal reference of the original sale's committed rebate
         * redemption (from {@code CheckoutResult.getRebatePoiTransactionId()}).
         * Lets a void reverse the rebate movement, per the reverse-rebate
         * contract.
         */
        public Builder rebatePoiTransactionId(String rebatePoiTransactionId) {
            this.rebatePoiTransactionId = rebatePoiTransactionId;
            return this;
        }

        /** Timestamp of the rebate referenced by {@link #rebatePoiTransactionId}. */
        public Builder rebatePoiTransactionTimestamp(Instant rebatePoiTransactionTimestamp) {
            this.rebatePoiTransactionTimestamp = rebatePoiTransactionTimestamp;
            return this;
        }

        /**
         * Terminal reference of the original sale's committed point/reward
         * redemption (from
         * {@code CheckoutResult.getRedemptionPoiTransactionId()}). Lets a
         * void reverse the redemption movement, per the reverse-redemption
         * contract.
         */
        public Builder redemptionPoiTransactionId(String redemptionPoiTransactionId) {
            this.redemptionPoiTransactionId = redemptionPoiTransactionId;
            return this;
        }

        /** Timestamp of the redemption referenced by {@link #redemptionPoiTransactionId}. */
        public Builder redemptionPoiTransactionTimestamp(
                Instant redemptionPoiTransactionTimestamp) {
            this.redemptionPoiTransactionTimestamp = redemptionPoiTransactionTimestamp;
            return this;
        }

        /**
         * Terminal reference of the original sale's loyalty award (from
         * {@code CheckoutResult.getAwardPoiTransactionId()}). Lets voids
         * and refunds reverse the award by its own reference, per the
         * reverse-award contract; without it no award reversal is sent.
         */
        public Builder awardPoiTransactionId(String awardPoiTransactionId) {
            this.awardPoiTransactionId = awardPoiTransactionId;
            return this;
        }

        /** Timestamp of the award referenced by {@link #awardPoiTransactionId}. */
        public Builder awardPoiTransactionTimestamp(Instant awardPoiTransactionTimestamp) {
            this.awardPoiTransactionTimestamp = awardPoiTransactionTimestamp;
            return this;
        }

        /**
         * The member's loyalty account ID from the original sale (from
         * {@code IdentifyResult.getMemberId()} or POS records). Populates
         * {@code LoyaltyData} on the loyalty reversals; per the reversal
         * contracts it may be omitted — the original transaction reference
         * suffices.
         */
        public Builder memberId(String memberId) {
            this.memberId = memberId;
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
         * Validates the configuration and returns a lazy operation that
         * announces the session to the terminal (Nexo {@code Admin} session
         * start signal) and yields it once the terminal acknowledged.
         * Nothing is sent until {@code execute()}, {@code get()}, or
         * {@code getOrNull()} is invoked.
         *
         * <p>If the registered {@code onSuccess} handler itself throws, the
         * just-started session is ended on the terminal (best-effort)
         * before the exception propagates.</p>
         *
         * @throws IllegalStateException if a required field or every
         *         transaction reference is missing
         */
        public SessionResult<ReversalSession> start() {
            if (client == null) {
                throw new IllegalStateException("client is required");
            }
            if (saleId == null || saleId.isEmpty()) {
                throw new IllegalStateException("saleId is required");
            }
            if (poiId == null || poiId.isEmpty()) {
                throw new IllegalStateException("poiId is required");
            }
            if (currency == null || currency.isEmpty()) {
                throw new IllegalStateException("currency is required");
            }
            if (poiTransactionId == null && storedValuePoiTransactionId == null
                    && rebatePoiTransactionId == null && redemptionPoiTransactionId == null
                    && awardPoiTransactionId == null) {
                throw new IllegalStateException(
                        "at least one transaction reference is required "
                                + "(poiTransactionId, storedValuePoiTransactionId, "
                                + "rebatePoiTransactionId, redemptionPoiTransactionId, "
                                + "or awardPoiTransactionId)");
            }
            ReversalSession session = new ReversalSession(this);
            // built through the session's operations so an asynchronous
            // start runs on (and delivers like) the session it creates
            return session.operations.operation("start", session::started)
                    .releasing(ReversalSession::close);
        }
    }
}
