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

import com.bilt.pos.display.DisplayPayload;
import com.bilt.pos.display.DisplayPayloadHelper;
import com.bilt.pos.nexo.client.BiltNexoClientException;
import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.DocumentQualifierEnum;
import com.bilt.pos.nexo.model.ErrorConditionType;
import com.bilt.pos.nexo.model.InputUpdate;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.MessageClassType;
import com.bilt.pos.nexo.model.MessageReference;
import com.bilt.pos.nexo.model.OutputContent;
import com.bilt.pos.nexo.model.OutputFormatEnum;
import com.bilt.pos.nexo.model.RepeatedResponseMessageBody;
import com.bilt.pos.nexo.model.ResultType;
import com.bilt.pos.nexo.model.SaleItem;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.SaleToPOIResponse;
import com.bilt.pos.nexo.model.StoredValueTransactionTypeEnum;
import com.bilt.pos.nexo.model.TransactionStatusRequest;
import com.bilt.pos.nexo.model.TransactionStatusResponse;
import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketMutation;
import com.bilt.pos.session.display.DisplayRenderer;
import com.bilt.pos.session.identity.CardAcquisitionOptions;
import com.bilt.pos.session.identity.CardAcquisitionResult;
import com.bilt.pos.session.identity.IdentifyOptions;
import com.bilt.pos.session.identity.IdentifyResult;
import com.bilt.pos.session.identity.IdentifyStatus;
import com.bilt.pos.session.identity.MemberIdentifier;
import com.bilt.pos.session.input.ConfirmationOptions;
import com.bilt.pos.session.input.InputOptions;
import com.bilt.pos.session.input.MenuOptions;
import com.bilt.pos.session.input.MenuSelection;
import com.bilt.pos.session.input.PinMode;
import com.bilt.pos.session.input.PinOptions;
import com.bilt.pos.session.input.PinResult;
import com.bilt.pos.session.input.Signature;
import com.bilt.pos.session.internal.BasketDisplay;
import com.bilt.pos.session.internal.BasketDisplayRenderer;
import com.bilt.pos.session.internal.BasketEngine;
import com.bilt.pos.session.internal.DisplayRouter;
import com.bilt.pos.session.internal.IdentityManager;
import com.bilt.pos.session.internal.InputManager;
import com.bilt.pos.session.internal.NexoExchange;
import com.bilt.pos.session.internal.NexoMessageFactory;
import com.bilt.pos.session.internal.PoiRef;
import com.bilt.pos.session.internal.PaymentOrchestrator;
import com.bilt.pos.session.internal.ReversalManager;
import com.bilt.pos.session.internal.ReversalMovement;
import com.bilt.pos.session.internal.SaleItemMapper;
import com.bilt.pos.session.internal.SessionSignalCodec;
import com.bilt.pos.session.internal.SessionStateMachine;
import com.bilt.pos.session.internal.StoredValueManager;
import com.bilt.pos.session.internal.Wire;
import com.bilt.pos.session.settlement.CommittedStep;
import com.bilt.pos.session.settlement.OriginalSaleRecord;
import com.bilt.pos.session.settlement.RefundAllocation;
import com.bilt.pos.session.settlement.RefundAllocationType;
import com.bilt.pos.session.settlement.SettlementResult;
import com.bilt.pos.session.settlement.SettlementContext;
import com.bilt.pos.session.settlement.SettlementMovement;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.bilt.pos.session.settlement.SettlementStep;
import com.bilt.pos.session.storedvalue.StoredValueBalance;
import com.bilt.pos.session.storedvalue.StoredValueCard;
import com.bilt.pos.session.storedvalue.StoredValueOperationResult;

import jakarta.xml.bind.JAXBException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * A stateful, loyalty-enabled checkout on top of {@link BiltNexoTerminalClient}.
 *
 * <p>The session owns the basket, drives the terminal (or an external
 * customer display), and orchestrates settlement — return allocations,
 * rebate redemption, point redemption, stored value/card charge, and reward
 * award — as a single flow. Every operation maps to standard Nexo Sale to POI 3.0
 * messages; the raw client remains available via {@link #getClient()}, and
 * the session-less device and admin operations (diagnostics, totals,
 * printing, sound) via {@link #terminal()}.</p>
 *
 * <p>Terminal operations are lazy: methods returning {@link SessionResult}
 * (or {@link SettlementFlow}) send nothing until {@code execute()} (asynchronous,
 * outcome through the registered handlers), {@code executeSync()},
 * {@code get()}, or {@code getOrNull()} (blocking) is invoked. Basket
 * mutations are pure local compute — the automatic display refresh they
 * trigger is asynchronous and conflated.</p>
 *
 * <p>A session is bracketed on the terminal: the builder's
 * {@link Builder#start() start()} announces it (Nexo {@code Admin} session
 * start signal) and only hands out the session once the terminal
 * acknowledged, and {@link #end()} tells the terminal to discard the
 * session-scoped data it accumulated. An ended session cannot be used or
 * restarted — create a new one per checkout. Sessions are
 * {@link AutoCloseable}, so try-with-resources sends the end signal even on
 * exception paths.</p>
 *
 * <pre>{@code
 * try (CheckoutSession session = CheckoutSession.builder()
 *         .client(client)
 *         .saleId("POS-LANE-3")
 *         .poiId("VictaLane-275839164")
 *         .currency("USD")
 *         .start()
 *         .get()) {
 *     ...
 * }
 * }</pre>
 *
 * <p>Sessions are intended for use from a single register thread.
 * {@link #abort()} and {@link #updateInputDisplay(DisplayPayload)} are the
 * only methods that are safe to call from another thread.</p>
 */
public final class CheckoutSession implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(CheckoutSession.class.getName());

    /** The states in which the basket accepts mutations — and, by the same
     *  token, in which a queued auto-display push is still current. */
    private static final Set<SessionState> BASKET_LIVE_STATES = EnumSet.of(
            SessionState.IDLE, SessionState.IDENTIFIED,
            SessionState.ACTIVE, SessionState.FAILED);

    private final String sessionId = UUID.randomUUID().toString();
    private final SessionStateMachine stateMachine = new SessionStateMachine();
    private final ReentrantLock lock = new ReentrantLock();
    private final SessionOperations operations;

    private final BiltNexoTerminalClient client;
    private final NexoMessageFactory factory;
    private final NexoExchange exchange;
    private final DisplayRouter router;
    private final BasketEngine basketEngine = new BasketEngine();
    private final SessionBasket basket;
    private final BasketDisplay display;
    private final AutoDisplayPush autoDisplayPush;
    private final DisplayRenderer displayRenderer;
    private final Consumer<Basket> onBasketUpdated;
    private final String currency;
    private final String storeLocation;
    private final boolean autoDisplay;

    private final IdentityManager identityManager;
    private final InputManager inputManager;
    private final ReversalManager reversalManager;
    private final PaymentOrchestrator paymentOrchestrator;
    private final StoredValueManager storedValueManager;

    private volatile boolean abortRequested;
    private boolean memberIdentified;
    private volatile IdentifyResult member;
    private volatile StoredValueCard storedValueCard;
    private volatile LastPayment lastPayment = LastPayment.NONE;
    private volatile boolean lastSettlementIncludesRefunds;
    // refund/void mutual exclusion and void-resume progress for this
    // session's completed payment (see ReversalGuards). Never stale across
    // payments: a failed void restores COMPLETED, from which settle() is
    // blocked, and the refund guard is cleared when a new payment
    // completes (see executeSettlement).
    private final ReversalGuards guards = new ReversalGuards("payment");
    // Non-empty = "FAILED with an incomplete rollback", a substate that
    // restricts FAILED's transitions at five sites (see rollbackIncomplete()
    // callers). If this lifecycle grows further, promote it to an explicit
    // SessionState instead of adding a sixth guard.
    private volatile List<PaymentOrchestrator.StandingMovement> standingMovements = List.of();
    // set while a drain has claimed the list and is reversing on the wire:
    // an empty standingMovements alone is ambiguous between "nothing
    // standing" and "claimed by an in-flight drain", and sealing the session
    // on the latter would strand the movements if the drain then fails
    private volatile boolean drainInFlight;
    // Refund allocations are real outward movements; once one commits in a
    // settlement that later fails, a retry must not send it again. The retry
    // may continue only with the same allocation prefix.
    private volatile List<RefundAllocation> committedRefundAllocations = List.of();
    private volatile List<SettlementMovement> committedRefundMovements = List.of();
    // Prior-sale voids do not use this session's lastPayment/guards, but
    // they still need resume state when one movement reversed and a later
    // one failed. The target record prevents applying that progress to a
    // different prior sale.
    private volatile OriginalSaleRecord priorSaleVoidTarget;
    private volatile Set<ReversalStep> priorSaleVoidReversedSteps =
            ConcurrentHashMap.newKeySet();

    // the session's Terminal facade, created lazily by terminal(); it has
    // its own executor and exchange, so the session's lifecycle never
    // constrains it (and vice versa)
    private volatile Terminal terminal;

    private CheckoutSession(Builder builder) {
        this.operations = new SessionOperations(builder.callbackExecutor,
                builder.onBackgroundError);
        this.client = builder.client;
        this.currency = builder.currency;
        this.storeLocation = builder.storeLocation;
        this.autoDisplay = builder.autoDisplay;
        this.displayRenderer = builder.displayRenderer != null
                ? builder.displayRenderer : new BasketDisplayRenderer();
        this.onBasketUpdated = builder.onBasketUpdated;
        this.factory = new NexoMessageFactory(builder.saleId, builder.poiId,
                builder.storeLocation);
        this.router = new DisplayRouter(builder.client, builder.externalDisplayClient);
        this.exchange = new NexoExchange(router, factory);
        this.identityManager = new IdentityManager(exchange);
        this.inputManager = new InputManager(exchange);
        this.reversalManager = new ReversalManager(exchange, builder.currency);
        this.paymentOrchestrator = new PaymentOrchestrator(exchange, builder.currency);
        this.storedValueManager = new StoredValueManager(exchange, builder.currency);
        this.display = new BasketDisplay(exchange, displayRenderer, builder.currency);
        this.autoDisplayPush = new AutoDisplayPush(operations, display,
                stateMachine::current, BASKET_LIVE_STATES);
        this.basket = new SessionBasket(new SessionBasket.Host() {
            @Override
            public Basket mutate(Consumer<BasketMutation> mutation) {
                return mutateBasket(mutation);
            }

            @Override
            public Basket snapshot() {
                lock.lock();
                try {
                    return basketEngine.snapshot();
                } finally {
                    lock.unlock();
                }
            }
        });
    }

    /**
     * The transaction references of this session's completed payment, kept
     * for void and refund. One immutable snapshot: written atomically when
     * a payment completes, read as a group afterwards — deliberately just
     * the references, so receipts and the final basket are not pinned for
     * the session's lifetime.
     */
    private static final class LastPayment {
        static final LastPayment NONE = new LastPayment(null);

        final String poiTransactionId;
        final Instant poiTransactionTimestamp;
        final String storedValuePoiTransactionId;
        final Instant storedValuePoiTransactionTimestamp;
        final String awardPoiTransactionId;
        final Instant awardPoiTransactionTimestamp;
        final String rebatePoiTransactionId;
        final Instant rebatePoiTransactionTimestamp;
        final String redemptionPoiTransactionId;
        final Instant redemptionPoiTransactionTimestamp;

        LastPayment(SettlementResult result) {
            poiTransactionId = result == null ? null : result.getPoiTransactionId();
            poiTransactionTimestamp = result == null ? null : result.getPoiTransactionTimestamp();
            storedValuePoiTransactionId = result == null
                    ? null : result.getStoredValuePoiTransactionId();
            storedValuePoiTransactionTimestamp = result == null
                    ? null : result.getStoredValuePoiTransactionTimestamp();
            awardPoiTransactionId = result == null ? null : result.getAwardPoiTransactionId();
            awardPoiTransactionTimestamp = result == null
                    ? null : result.getAwardPoiTransactionTimestamp();
            rebatePoiTransactionId = result == null ? null : result.getRebatePoiTransactionId();
            rebatePoiTransactionTimestamp = result == null
                    ? null : result.getRebatePoiTransactionTimestamp();
            redemptionPoiTransactionId = result == null
                    ? null : result.getRedemptionPoiTransactionId();
            redemptionPoiTransactionTimestamp = result == null
                    ? null : result.getRedemptionPoiTransactionTimestamp();
        }
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

    // ─── Basket ───

    /**
     * The session's basket: item and tax mutations, batch edits, and
     * snapshots. Mutations follow the session lifecycle — they are rejected
     * while a payment is in flight or after the session has ended. Each one
     * returns the updated snapshot without touching the wire; with
     * {@link Builder#autoDisplay(boolean) autoDisplay} enabled it also
     * enqueues an asynchronous, conflated customer-display refresh (a
     * failed refresh reports through
     * {@link Builder#onBackgroundError(Consumer) onBackgroundError}).
     */
    public SessionBasket basket() {
        return basket;
    }

    private Basket mutateBasket(Consumer<BasketMutation> mutation) {
        Basket snapshot;
        lock.lock();
        try {
            SessionState state = stateMachine.current();
            if (!BASKET_LIVE_STATES.contains(state)) {
                throw new IllegalStateException(
                        "the basket cannot be modified in state " + state);
            }
            if (state == SessionState.FAILED && hasCommittedRefundAllocations()) {
                throw new IllegalStateException("the basket cannot be modified after "
                        + "refund allocations have committed; retry settle() with the "
                        + "same refund allocations");
            }
            // atomic: a mutation (or batch) that throws restores the basket,
            // and the state transitions below only run on success
            basketEngine.mutateAtomically(mutation);
            if (state == SessionState.FAILED && !rollbackIncomplete()) {
                // a fully-unwound failed payment resumes the checkout on an
                // edit (e.g. dropping an item before a retry). While the
                // rollback is incomplete the session stays FAILED — ACTIVE
                // would cut off voidTransaction(), the path that finishes it
                stateMachine.transitionTo(SessionState.ACTIVE);
            }
            syncStateWithBasket();
            snapshot = basketEngine.snapshot();
            if (autoDisplay) {
                // under the lock so concurrent mutations cannot enter the
                // conflated push out of snapshot order
                autoDisplayPush.push(snapshot);
            }
        } finally {
            lock.unlock();
        }
        return snapshot;
    }

    private void syncStateWithBasket() {
        SessionState state = stateMachine.current();
        if (basketEngine.isEmpty()) {
            if (state == SessionState.ACTIVE) {
                stateMachine.transitionTo(memberIdentified
                        ? SessionState.IDENTIFIED : SessionState.IDLE);
            }
        } else if (state == SessionState.IDLE || state == SessionState.IDENTIFIED) {
            stateMachine.transitionTo(SessionState.ACTIVE);
        }
    }

    // ─── Member Identification ───

    /**
     * The identified member, or {@code null} for a guest checkout. The
     * terminal-side identification never forces the customer — if they opted
     * out, this stays {@code null}.
     */
    public IdentifyResult getMember() {
        IdentifyResult current = member;
        return current != null && current.getStatus() == IdentifyStatus.FOUND ? current : null;
    }

    /** Prompts the customer on the terminal to identify themselves. */
    public SessionResult<IdentifyResult> identifyMember() {
        return identifyMember(IdentifyOptions.defaults());
    }

    /**
     * Prompts the customer on the terminal to identify themselves
     * (Nexo {@code CardAcquisition} with loyalty handling).
     *
     * <p>Lookup outcomes that simply leave the checkout without a member —
     * not found, suspended, customer cancelled — are delivered to
     * {@code onSuccess} with the corresponding {@link IdentifyStatus};
     * {@code onError} fires only for real failures.</p>
     *
     * <p>Also allowed while a failed payment awaits retry
     * ({@link SessionState#FAILED}), so a declined guest checkout can attach
     * a member and retry with loyalty enabled.</p>
     */
    public SessionResult<IdentifyResult> identifyMember(IdentifyOptions options) {
        Objects.requireNonNull(options, "options");
        return operation("identifyMember",
                () -> completeIdentify(identifyStateChecked(
                        () -> identityManager.identifyPrompted(options))));
    }

    /** POS-driven member lookup by an identifier on file; no terminal prompt. */
    public SessionResult<IdentifyResult> identifyMember(MemberIdentifier identifier) {
        Objects.requireNonNull(identifier, "identifier");
        return operation("identifyMember",
                () -> completeIdentify(identifyStateChecked(
                        () -> identityManager.identifyByIdentifier(identifier))));
    }

    private IdentifyResult identifyStateChecked(Supplier<IdentifyResult> lookup) {
        // FAILED is allowed so a register can identify (or re-identify) the
        // member before retrying a failed payment with loyalty enabled — a
        // guest checkout whose card was declined would otherwise be stuck
        // retrying as a guest, since settle() accepts FAILED but the checkout
        // only resumes ACTIVE on a basket edit.
        requireState(EnumSet.of(SessionState.IDLE, SessionState.IDENTIFIED, SessionState.ACTIVE,
                SessionState.FAILED), "identifyMember");
        return lookup.get();
    }

    /**
     * Applies an identification outcome to the session. The latest completed
     * attempt wins: {@code FOUND} attaches the member; {@code NOT_FOUND} and
     * {@code SUSPENDED} are affirmative "no usable member" outcomes and
     * detach any previously identified member (so a re-identify cannot leave
     * loyalty running against a stale account). {@code CANCELLED} only means
     * the customer dismissed this prompt — a prior identification stands.
     */
    private IdentifyResult completeIdentify(IdentifyResult result) {
        lock.lock();
        try {
            SessionState state = stateMachine.current();
            if (state != SessionState.IDLE && state != SessionState.IDENTIFIED
                    && state != SessionState.ACTIVE && state != SessionState.FAILED) {
                // abort() — the one documented cross-thread entry point —
                // can end the session while the lookup is on the wire. An
                // outcome that arrives after that must be discarded: it must
                // not attach a member to, or fire onSuccess on, an ended
                // session
                throw discardedMidFlight("identifyMember", state);
            }
            if (result.getStatus() == IdentifyStatus.FOUND) {
                this.member = result;
                this.memberIdentified = true;
                if (stateMachine.current() == SessionState.IDLE) {
                    stateMachine.transitionTo(SessionState.IDENTIFIED);
                }
            } else if (result.getStatus() != IdentifyStatus.CANCELLED) {
                this.member = null;
                this.memberIdentified = false;
                if (stateMachine.current() == SessionState.IDENTIFIED) {
                    stateMachine.transitionTo(SessionState.IDLE);
                }
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    // ─── Card acquisition ───

    /** Reads card data from the terminal without initiating a payment. */
    public SessionResult<CardAcquisitionResult> acquireCard() {
        return acquireCard(CardAcquisitionOptions.defaults());
    }

    /** Reads card data from the terminal without initiating a payment. */
    public SessionResult<CardAcquisitionResult> acquireCard(CardAcquisitionOptions options) {
        Objects.requireNonNull(options, "options");
        return operation("acquireCard", () -> {
            requireNotEnded("acquireCard");
            CardAcquisitionResult acquired = identityManager.acquireCard(options);
            discardIfEndedMidFlight("acquireCard");
            return acquired;
        });
    }

    // ─── Input (nexo native) ───

    /** Prompts the customer for a digit string (e.g. a ZIP code). */
    public SessionResult<String> requestDigitString(String prompt) {
        return requestDigitString(prompt, InputOptions.defaults());
    }

    public SessionResult<String> requestDigitString(String prompt, InputOptions options) {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(options, "options");
        return inputOperation("requestDigitString",
                () -> inputManager.digitString(prompt, options));
    }

    /** Prompts the customer for a decimal amount (e.g. a tip). */
    public SessionResult<BigDecimal> requestDecimalString(String prompt) {
        return requestDecimalString(prompt, InputOptions.defaults());
    }

    public SessionResult<BigDecimal> requestDecimalString(String prompt, InputOptions options) {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(options, "options");
        return inputOperation("requestDecimalString",
                () -> inputManager.decimalString(prompt, options));
    }

    /** Prompts the customer for free text (e.g. an email address). */
    public SessionResult<String> requestTextString(String prompt) {
        return requestTextString(prompt, InputOptions.defaults());
    }

    public SessionResult<String> requestTextString(String prompt, InputOptions options) {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(options, "options");
        return inputOperation("requestTextString",
                () -> inputManager.textString(prompt, options));
    }

    /** Prompts the customer for a yes/no confirmation. */
    public SessionResult<Boolean> requestConfirmation(String prompt) {
        return requestConfirmation(prompt, ConfirmationOptions.defaults());
    }

    public SessionResult<Boolean> requestConfirmation(String prompt, ConfirmationOptions options) {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(options, "options");
        return inputOperation("requestConfirmation",
                () -> inputManager.confirmation(prompt, options));
    }

    /** Prompts the customer to pick from a menu of entries. */
    public SessionResult<MenuSelection> requestMenuEntry(String prompt, List<String> entries) {
        return requestMenuEntry(prompt, entries, MenuOptions.defaults());
    }

    public SessionResult<MenuSelection> requestMenuEntry(String prompt, List<String> entries,
                                                         MenuOptions options) {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(options, "options");
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("entries must not be empty");
        }
        List<String> entriesCopy = List.copyOf(entries);
        return inputOperation("requestMenuEntry",
                () -> inputManager.menuEntry(prompt, entriesCopy, options));
    }

    // ─── Input (XSD-based) ───

    /** Captures a handwritten signature on the terminal. */
    public SessionResult<Signature> requestSignature(String prompt) {
        Objects.requireNonNull(prompt, "prompt");
        return inputOperation("requestSignature", () -> inputManager.signature(prompt));
    }

    /** Asks the customer to confirm an amount. */
    public SessionResult<Boolean> requestAmountConfirmation(BigDecimal amount, String prompt) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(prompt, "prompt");
        return inputOperation("requestAmountConfirmation",
                () -> inputManager.amountConfirmation(amount, prompt, currency));
    }

    // ─── PIN ───

    /** Captures and encrypts a PIN on the secure PIN pad. */
    public SessionResult<PinResult> requestPinEntry(PinOptions options) {
        Objects.requireNonNull(options, "options");
        return inputOperation("requestPinEntry",
                () -> inputManager.pin(PinMode.PIN_ENTER, options));
    }

    /** Captures a PIN and verifies it, returning the encrypted block. */
    public SessionResult<PinResult> requestPinVerify(PinOptions options) {
        Objects.requireNonNull(options, "options");
        return inputOperation("requestPinVerify",
                () -> inputManager.pin(PinMode.PIN_VERIFY, options));
    }

    /** Verifies a PIN without returning the block. */
    public SessionResult<PinResult> requestPinVerifyOnly(PinOptions options) {
        Objects.requireNonNull(options, "options");
        return inputOperation("requestPinVerifyOnly",
                () -> inputManager.pin(PinMode.PIN_VERIFY_ONLY, options));
    }

    private <T> SessionResult<T> inputOperation(String name, Supplier<T> body) {
        return operation(name, () -> {
            requireNotEnded(name);
            T value = body.get();
            discardIfEndedMidFlight(name);
            return value;
        });
    }

    /**
     * Post-completion guard for read-only prompts: {@code abort()} — safe
     * from any thread — can end the session while the request is on the
     * wire. An outcome arriving after that is discarded rather than
     * delivered: the register aborted, so stale customer input, PIN, or
     * card data must not reach {@code onSuccess}.
     */
    private void discardIfEndedMidFlight(String operationName) {
        lock.lock();
        try {
            SessionState state = stateMachine.current();
            if (state.isTerminal()) {
                throw discardedMidFlight(operationName, state);
            }
        } finally {
            lock.unlock();
        }
    }

    /** The discard error for an outcome that arrived after the session moved on. */
    private static SessionException discardedMidFlight(String operationName,
                                                       SessionState state) {
        return new SessionException(new SessionError(
                SessionErrorCode.INVALID_STATE,
                operationName + " completed after the session moved to " + state
                        + "; the result was discarded"));
    }

    private void requireState(Set<SessionState> allowed, String operationName) {
        SessionError error = stateMachine.requireState(allowed, operationName);
        if (error != null) {
            throw new SessionException(error);
        }
    }

    private void requireNotEnded(String operationName) {
        SessionState state = stateMachine.current();
        if (state.isTerminal()) {
            throw invalidState(operationName
                    + " is not allowed after the session has ended (state " + state + ")");
        }
    }

    // ─── Stored Value ───

    /**
     * Registers a stored value (gift) card charged as part of a split tender
     * during {@code settle()}. The card number is treated as keyed
     * ({@code PAN}); use {@link #setStoredValueCard(StoredValueCard)} for
     * scanned or swiped cards or to set a provider.
     */
    public void setStoredValueCard(String cardNumber) {
        this.storedValueCard = cardNumber == null ? null : StoredValueCard.number(cardNumber);
    }

    /**
     * Registers a stored value (gift) card charged as part of a split tender
     * during {@code settle()}. Pass {@code null} to clear.
     */
    public void setStoredValueCard(StoredValueCard card) {
        this.storedValueCard = card;
    }

    /** Queries the available balance on a stored value card. */
    public SessionResult<StoredValueBalance> storedValueBalance(StoredValueCard card) {
        Objects.requireNonNull(card, "card");
        return operation("storedValueBalance", () -> {
            requireNotEnded("storedValueBalance");
            return storedValueManager.balance(card);
        });
    }

    /**
     * Activates a stored value card, optionally loading an initial balance.
     * Use {@code BigDecimal.ZERO} to activate without funds.
     */
    public SessionResult<StoredValueOperationResult> storedValueActivate(StoredValueCard card,
                                                                         BigDecimal initialAmount) {
        Objects.requireNonNull(initialAmount, "initialAmount");
        requireNonNegative(initialAmount);
        return storedValueOperation("storedValueActivate",
                StoredValueTransactionTypeEnum.ACTIVATE, card, initialAmount);
    }

    /** Loads funds onto a stored value card. */
    public SessionResult<StoredValueOperationResult> storedValueLoad(StoredValueCard card,
                                                                     BigDecimal amount) {
        requirePositiveAmount(amount);
        return storedValueOperation("storedValueLoad",
                StoredValueTransactionTypeEnum.LOAD, card, amount);
    }

    /** Unloads (cashes out) funds from a stored value card. */
    public SessionResult<StoredValueOperationResult> storedValueUnload(StoredValueCard card,
                                                                       BigDecimal amount) {
        requirePositiveAmount(amount);
        return storedValueOperation("storedValueUnload",
                StoredValueTransactionTypeEnum.UNLOAD, card, amount);
    }

    /**
     * Permanently deactivates a stored value card (an {@code Unload} with a
     * zero amount). Not all stored value providers support deactivation.
     */
    public SessionResult<StoredValueOperationResult> storedValueDeactivate(StoredValueCard card) {
        return storedValueOperation("storedValueDeactivate",
                StoredValueTransactionTypeEnum.UNLOAD, card, BigDecimal.ZERO);
    }

    /**
     * Reserves an amount on a stored value card. Provider support varies —
     * confirm with your stored value provider before relying on this.
     */
    public SessionResult<StoredValueOperationResult> storedValueReserve(StoredValueCard card,
                                                                        BigDecimal amount) {
        requirePositiveAmount(amount);
        return storedValueOperation("storedValueReserve",
                StoredValueTransactionTypeEnum.RESERVE, card, amount);
    }

    /**
     * Reverses a prior stored value operation by its terminal reference
     * (from {@link StoredValueOperationResult#getPoiTransactionId()}).
     */
    public SessionResult<StoredValueOperationResult> storedValueReverse(
            String originalPoiTransactionId, Instant originalPoiTransactionTimestamp) {
        Objects.requireNonNull(originalPoiTransactionId, "originalPoiTransactionId");
        return operation("storedValueReverse", () -> {
            requireNotEnded("storedValueReverse");
            return storedValueManager.operation(StoredValueTransactionTypeEnum.REVERSE,
                    null, null, originalPoiTransactionId, originalPoiTransactionTimestamp);
        });
    }

    /**
     * Requests a duplicate (replacement) for a stored value card. Provider
     * support varies — confirm with your stored value provider before
     * relying on this.
     */
    public SessionResult<StoredValueOperationResult> storedValueDuplicate(StoredValueCard card) {
        return storedValueOperation("storedValueDuplicate",
                StoredValueTransactionTypeEnum.DUPLICATE, card, null);
    }

    private SessionResult<StoredValueOperationResult> storedValueOperation(
            String name, StoredValueTransactionTypeEnum type, StoredValueCard card,
            BigDecimal amount) {
        Objects.requireNonNull(card, "card");
        return operation(name, () -> {
            requireNotEnded(name);
            return storedValueManager.operation(type, card, amount, null, null);
        });
    }

    private static void requirePositiveAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    private static void requireNonNegative(BigDecimal amount) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
    }

    // ─── Settlement ───

    /** Starts the settlement orchestration chain with default options. */
    public SettlementFlow settle() {
        return settle(SettlementOptions.defaults());
    }

    /**
     * Starts the settlement orchestration chain. Nothing is sent — and no
     * precondition is verified — until the returned {@link SettlementFlow}'s
     * {@code execute()}, {@code executeSync()}, {@code get()}, or
     * {@code getOrNull()} is invoked: the session must then be in a
     * settlable state ({@code ACTIVE}, or {@code FAILED} for a retry) with a
     * non-empty basket. Sale lines are charged through the normal tender
     * sequence; credit lines must be covered by the register-supplied
     * refund allocations in {@link SettlementOptions}.
     *
     * <p>Terminal-backed refund allocations carry the return-side
     * itemization on the first {@code PaymentRequest(Refund)} leg only.
     * Additional refund legs are amount-only because allocations are
     * tender-level, not line-level.</p>
     *
     * <p>Refund allocations are not retried in the same settlement run. If
     * one fails, the session moves to {@code FAILED}; already-committed
     * refund allocation movements remain recorded, and the register retries
     * by calling {@code settle()} again with the same committed allocation
     * prefix. Charge-side failures after refund allocations use
     * {@link SettlementFlow#onError} recovery options.</p>
     */
    public SettlementFlow settle(SettlementOptions options) {
        Objects.requireNonNull(options, "options");
        operations.track("settle");
        return new SettlementFlow(flow -> executeSettlement(flow, options))
                .session(operations);
    }

    private SettlementResult executeSettlement(SettlementFlow flow, SettlementOptions options) {
        operations.begin("settle");
        PaymentOrchestrator.Request request = new PaymentOrchestrator.Request();
        Basket fullBasket;
        Basket purchaseBasket;
        BigDecimal returnTotal;
        lock.lock();
        try {
            SessionError error = stateMachine.requireState(
                    EnumSet.of(SessionState.ACTIVE, SessionState.FAILED), "settle");
            if (error != null) {
                throw new SessionException(error);
            }
            if (basketEngine.isEmpty()) {
                throw invalidState("the basket is empty; settlement cannot start");
            }
            fullBasket = basketEngine.snapshot();
            purchaseBasket = fullBasket.salePortion();
            returnTotal = fullBasket.returnTotal();
            validateRefundAllocations(returnTotal, options.getRefundAllocations());
            validateCommittedRefundRetry(options.getRefundAllocations());
            if (purchaseBasket.isEmpty() && returnTotal.signum() == 0) {
                throw invalidState("settlement requires a sale line or a return line");
            }
            if (!purchaseBasket.isEmpty() && purchaseBasket.getGrandTotal().signum() <= 0) {
                throw invalidState("the sale portion of the basket is not positive; "
                        + "settlement cannot charge it");
            }
            if (purchaseBasket.isEmpty() && options.getCashback() != null) {
                throw invalidState("cashback requires a card charge in the settlement");
            }
            stateMachine.transitionTo(SessionState.SETTLING);
            // the abort flag is scoped to a single settlement run: a stale
            // abort left over from an earlier decline or failed void must
            // not kill a legitimate retry at its first checkAbort
            abortRequested = false;
        } finally {
            lock.unlock();
        }
        request.member = getMember();
        request.storedValueCard = storedValueCard;
        request.options = options;
        request.basket = purchaseBasket;
        request.abortRequested = () -> abortRequested;
        // movements an incomplete unwind left standing are kept so that
        // voidTransaction() on the failed session can finish the reversal
        request.onUnreversed = movements -> standingMovements = List.copyOf(movements);
        request.handlers.beforeStep = flow.beforeStepHandler();
        request.handlers.onRebatesRedeemed = flow.rebatesHandler();
        request.handlers.onPointsRedeemed = flow.pointsHandler();
        request.handlers.onGiftCardPayment = flow.giftCardHandler();
        request.handlers.onMovement = flow.movementHandler();
        request.handlers.onError = flow.errorHandler();

        try {
            // a previous run's incomplete rollback left movements standing:
            // finish that unwind before charging anew — a retry on top of
            // them would double-charge the tender or double-commit loyalty
            if (rollbackIncomplete()) {
                try {
                    drainStandingMovements();
                } catch (SessionException e) {
                    throw new SessionException(Wire.annotated(e.getError(),
                            "the previous payment's rollback is still incomplete; the "
                                    + "retry did not start: " + e.getError().getMessage(), e));
                }
            }
            List<SettlementMovement> refundMovements = new ArrayList<>(
                    committedRefundMovementsSnapshot());
            List<CommittedStep> committedRefundSteps = committedRefundSteps(refundMovements);
            int committedRefundCount = committedRefundAllocationsCount();
            List<RefundAllocation> refundAllocations = options.getRefundAllocations();
            List<RefundAllocation> pendingAllocations = refundAllocations
                    .subList(committedRefundCount, refundAllocations.size());
            refundMovements.addAll(executeRefundAllocations(flow, fullBasket,
                    pendingAllocations, committedRefundSteps,
                    hasItemizedRefundAllocation(
                            refundAllocations.subList(0, committedRefundCount))));
            request.priorSteps = committedRefundSteps;
            SettlementResult purchaseResult = purchaseBasket.isEmpty()
                    ? null : paymentOrchestrator.run(request);
            SettlementResult result = combineSettlementResult(fullBasket, purchaseResult,
                    refundMovements);
            showFinalSettlement(result.getFinalBasket());
            lock.lock();
            try {
                // this thread is the sole writer of settlement-final states;
                // abort() defers to it while the session is SETTLING
                stateMachine.transitionTo(SessionState.COMPLETED);
                lastPayment = new LastPayment(result);
                lastSettlementIncludesRefunds = !refundMovements.isEmpty();
                clearCommittedRefundAllocations();
                // this settlement replaced the void target, so the guard on
                // the previous one lifts (the void-progress set needs no
                // reset — see the field declarations)
                guards.clearRefundIssued();
            } finally {
                lock.unlock();
            }
            if (abortRequested) {
                LOGGER.warning("abort() arrived after settlement completed; the transaction "
                        + "stands — use voidTransaction() to reverse it");
            }
            return result;
        } catch (SessionException e) {
            // aborted or failed, settlement lands in FAILED: the basket
            // stays intact and retry/void/end all remain available. An
            // abort is not an abandonment — the error's ABORTED code tells
            // the register what happened; end() abandons the checkout.
            transitionLocked(SessionState.FAILED);
            throw e;
        } catch (RuntimeException e) {
            // defense in depth: whatever escapes, the session must not stay
            // frozen in SETTLING with the basket locked
            transitionLocked(SessionState.FAILED);
            throw e;
        }
    }

    private List<SettlementMovement> executeRefundAllocations(
            SettlementFlow flow, Basket basket, List<RefundAllocation> allocations,
            List<CommittedStep> committedSteps, boolean refundSaleItemsAlreadySent) {
        List<SettlementMovement> movements = new ArrayList<>();
        Function<SettlementContext, String> beforeStep = flow.beforeStepHandler();
        Consumer<SettlementMovement> onMovement = flow.movementHandler();
        List<SaleItem> refundSaleItems = refundSaleItemsAlreadySent
                ? List.of() : SaleItemMapper.toRefundSaleItems(basket.returnPortion());
        boolean refundSaleItemsSent = refundSaleItemsAlreadySent;
        for (RefundAllocation allocation : allocations) {
            if (abortRequested) {
                throw new SessionException(new SessionError(SessionErrorCode.ABORTED,
                        "the settlement was aborted"));
            }
            SettlementStep step = refundStep(allocation.getType());
            String saleTransactionId = SettlementContext.resolveSaleTransactionId(
                    step, basket, allocation.getAmount(), committedSteps, beforeStep);
            // Refund allocation failures deliberately bypass the
            // charge-side onError recovery loop: successful refunds cannot
            // be re-charged as compensation, so a failed run is resumed by
            // retrying settle() with the same committed allocation prefix.
            List<SaleItem> saleItems = null;
            if (!refundSaleItemsSent && carriesRefundSaleItems(allocation.getType())
                    && !refundSaleItems.isEmpty()) {
                // Allocations are tender-level, not line-level. Carry the
                // complete return itemization once, on the first refund
                // PaymentRequest, so split refunds do not duplicate receipt
                // lines across tender legs.
                saleItems = refundSaleItems;
                refundSaleItemsSent = true;
            }
            SettlementMovement movement = executeRefundAllocation(allocation, step,
                    saleTransactionId, saleItems);
            movements.add(movement);
            committedSteps.add(new CommittedStep(step, saleTransactionId,
                    movement.getPoiTransactionId(), movement.getPoiTransactionTimestamp(), true));
            recordCommittedRefundAllocation(allocation, movement);
            if (onMovement != null) {
                onMovement.accept(movement);
            }
        }
        return movements;
    }

    private SettlementMovement executeRefundAllocation(RefundAllocation allocation,
                                                       SettlementStep step,
                                                       String saleTransactionId,
                                                       List<SaleItem> saleItems) {
        switch (allocation.getType()) {
            case CARD:
            case STORED_VALUE:
                RefundResult card = reversalManager.refund(allocation.getAmount(), saleItems,
                        allocation.getOriginalPoiTransactionId(),
                        allocation.getOriginalPoiTransactionTimestamp(),
                        null, null, allocation.getMemberId(), saleTransactionId,
                        null, () -> { }, () -> { });
                return refundMovement(step, allocation, saleTransactionId,
                        card.getRefundedAmount(), card.getPoiTransactionId(),
                        card.getPoiTransactionTimestamp(), null, null);
            case STORE_CREDIT:
                StoredValueOperationResult storeCredit = storedValueManager.operation(
                        StoredValueTransactionTypeEnum.LOAD,
                        allocation.getStoredValueCard(), allocation.getAmount(),
                        null, null, saleTransactionId);
                return refundMovement(step, allocation, saleTransactionId,
                        storeCredit.getAmount(), storeCredit.getPoiTransactionId(),
                        storeCredit.getPoiTransactionTimestamp(), null, null);
            case EXTERNAL:
                return refundMovement(step, allocation, saleTransactionId,
                        allocation.getAmount(), null, null, null, null);
            case POINT_REDEMPTION:
                VoidResult points = reversalManager.refundLoyalty(ReversalStep.REDEMPTION,
                        allocation.getOriginalPoiTransactionId(),
                        allocation.getOriginalPoiTransactionTimestamp(),
                        allocation.getMemberId(), saleTransactionId);
                return refundMovement(step, allocation, saleTransactionId,
                        points.getReversedAmount(), points.getPoiTransactionId(),
                        points.getPoiTransactionTimestamp(), points.getPointsReversed(),
                        points.getRemainingPointBalance());
            case REBATE:
                VoidResult rebate = reversalManager.refundLoyalty(ReversalStep.REBATE,
                        allocation.getOriginalPoiTransactionId(),
                        allocation.getOriginalPoiTransactionTimestamp(),
                        allocation.getMemberId(), saleTransactionId);
                return refundMovement(step, allocation, saleTransactionId,
                        rebate.getReversedAmount(), rebate.getPoiTransactionId(),
                        rebate.getPoiTransactionTimestamp(), rebate.getPointsReversed(),
                        rebate.getRemainingPointBalance());
            case AWARD:
                VoidResult award = reversalManager.refundLoyalty(ReversalStep.AWARD,
                        allocation.getOriginalPoiTransactionId(),
                        allocation.getOriginalPoiTransactionTimestamp(),
                        allocation.getMemberId(), saleTransactionId);
                return refundMovement(step, allocation, saleTransactionId,
                        BigDecimal.ZERO, award.getPoiTransactionId(),
                        award.getPoiTransactionTimestamp(), award.getPointsReversed(),
                        award.getRemainingPointBalance());
            default:
                throw new IllegalArgumentException("unsupported refund allocation type "
                        + allocation.getType());
        }
    }

    private static boolean hasItemizedRefundAllocation(List<RefundAllocation> allocations) {
        for (RefundAllocation allocation : allocations) {
            if (carriesRefundSaleItems(allocation.getType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean carriesRefundSaleItems(RefundAllocationType type) {
        return type == RefundAllocationType.CARD
                || type == RefundAllocationType.STORED_VALUE;
    }

    private static SettlementMovement refundMovement(SettlementStep step,
            RefundAllocation allocation, String saleTransactionId, BigDecimal actualAmount,
            String poiTransactionId, Instant poiTransactionTimestamp, Integer points,
            Integer pointBalance) {
        BigDecimal amount = actualAmount != null ? actualAmount : allocation.getAmount();
        return SettlementMovement.builder()
                .step(step)
                .amount(amount)
                .saleTransactionId(saleTransactionId)
                .poiTransactionId(poiTransactionId)
                .poiTransactionTimestamp(poiTransactionTimestamp)
                .memberId(allocation.getMemberId())
                .points(points)
                .pointBalance(pointBalance)
                .build();
    }

    private static SettlementStep refundStep(RefundAllocationType type) {
        switch (type) {
            case CARD:
                return SettlementStep.CARD_REFUND;
            case STORED_VALUE:
            case STORE_CREDIT:
                return SettlementStep.STORED_VALUE_REFUND;
            case EXTERNAL:
                return SettlementStep.EXTERNAL_REFUND;
            case POINT_REDEMPTION:
                return SettlementStep.POINT_REDEMPTION_REFUND;
            case REBATE:
                return SettlementStep.REBATE_REFUND;
            case AWARD:
                return SettlementStep.AWARD_REFUND;
            default:
                throw new IllegalArgumentException("unsupported refund allocation type " + type);
        }
    }

    private static void validateRefundAllocations(BigDecimal returnTotal,
                                                  List<RefundAllocation> allocations) {
        if (returnTotal.signum() == 0 && !allocations.isEmpty()) {
            throw invalidState("refund allocations require at least one return line");
        }
        BigDecimal allocated = BigDecimal.ZERO;
        for (RefundAllocation allocation : allocations) {
            if (allocation.countsTowardRefundTotal()) {
                allocated = allocated.add(allocation.getAmount());
            }
        }
        if (returnTotal.signum() > 0 && allocated.compareTo(returnTotal) != 0) {
            throw invalidState("refund allocations total " + allocated
                    + " but return lines total " + returnTotal);
        }
    }

    private void validateCommittedRefundRetry(List<RefundAllocation> allocations) {
        List<RefundAllocation> committed = committedRefundAllocations;
        if (committed.isEmpty()) {
            return;
        }
        if (allocations.size() < committed.size()) {
            throw committedRefundRetryError();
        }
        for (int i = 0; i < committed.size(); i++) {
            if (!Objects.equals(committed.get(i), allocations.get(i))) {
                throw committedRefundRetryError();
            }
        }
    }

    private static SessionException committedRefundRetryError() {
        return invalidState("a previous settlement attempt already committed refund "
                + "allocations; retry settle() with the same refund allocations");
    }

    private int committedRefundAllocationsCount() {
        return committedRefundAllocations.size();
    }

    private List<SettlementMovement> committedRefundMovementsSnapshot() {
        return List.copyOf(committedRefundMovements);
    }

    private boolean hasCommittedRefundAllocations() {
        return !committedRefundAllocations.isEmpty();
    }

    private void recordCommittedRefundAllocation(RefundAllocation allocation,
                                                 SettlementMovement movement) {
        lock.lock();
        try {
            List<RefundAllocation> allocations = new ArrayList<>(committedRefundAllocations);
            allocations.add(allocation);
            committedRefundAllocations = List.copyOf(allocations);

            List<SettlementMovement> movements = new ArrayList<>(committedRefundMovements);
            movements.add(movement);
            committedRefundMovements = List.copyOf(movements);
        } finally {
            lock.unlock();
        }
    }

    private void clearCommittedRefundAllocations() {
        committedRefundAllocations = List.of();
        committedRefundMovements = List.of();
    }

    private static List<CommittedStep> committedRefundSteps(
            List<SettlementMovement> movements) {
        List<CommittedStep> steps = new ArrayList<>();
        for (SettlementMovement movement : movements) {
            steps.add(new CommittedStep(movement.getStep(), movement.getSaleTransactionId(),
                    movement.getPoiTransactionId(), movement.getPoiTransactionTimestamp(), true));
        }
        return steps;
    }

    private static SettlementResult combineSettlementResult(Basket fullBasket,
            SettlementResult purchase, List<SettlementMovement> refundMovements) {
        List<SettlementMovement> movements = new ArrayList<>(refundMovements);
        if (purchase != null) {
            movements.addAll(purchase.getMovements());
        }
        BigDecimal cardRefunded = sumMovements(refundMovements, SettlementStep.CARD_REFUND);
        BigDecimal storedValueRefunded = sumMovements(refundMovements,
                SettlementStep.STORED_VALUE_REFUND);
        BigDecimal externalRefunded = sumMovements(refundMovements,
                SettlementStep.EXTERNAL_REFUND);
        BigDecimal loyaltyRefunded = sumMovements(refundMovements,
                SettlementStep.POINT_REDEMPTION_REFUND)
                .add(sumMovements(refundMovements, SettlementStep.REBATE_REFUND));
        Basket finalBasket = purchase == null
                ? fullBasket
                : fullBasket.withSettledSalePortion(purchase.getFinalBasket());
        return (purchase == null ? SettlementResult.builder() : purchase.toBuilder())
                .success(true)
                .finalBasket(finalBasket)
                .cardRefundedAmount(cardRefunded)
                .storedValueRefundedAmount(storedValueRefunded)
                .externalRefundedAmount(externalRefunded)
                .loyaltyRefundedAmount(loyaltyRefunded)
                .movements(movements)
                .build();
    }

    private static BigDecimal sumMovements(List<SettlementMovement> movements,
                                           SettlementStep step) {
        BigDecimal total = BigDecimal.ZERO;
        for (SettlementMovement movement : movements) {
            if (movement.getStep() == step && movement.getAmount() != null) {
                total = total.add(movement.getAmount());
            }
        }
        return total;
    }

    private void showFinalSettlement(Basket basket) {
        if (!autoDisplay) {
            return;
        }
        try {
            showBasket(basket);
        } catch (RuntimeException e) {
            operations.backgroundError("the final settlement display", e);
        }
    }


    // ─── Refund ───

    /**
     * Full linked refund of this session's completed payment. Also
     * reverses the loyalty award when one ran — best-effort by default
     * (override via {@link ReversalFlow#onError}).
     *
     * <p>Linked refunds reference a single transaction: after a split
     * tender this is the card leg — use {@code voidTransaction()} to
     * reverse both legs, or the stored value operations to return funds to
     * the gift card. A refund reverses the card leg and award only; the
     * sale's committed rebate and redemption movements are reversed by
     * {@link #voidTransaction()}. Once a refund has returned money the
     * payment can no longer be voided; a tender skipped by an
     * {@code onError} decision leaves it voidable, with an award the flow
     * reversed remembered so nothing re-credits it. Once a void has
     * partially reversed the payment's money legs, refunds are refused
     * until the void is finished. To refund a sale taken by an earlier
     * session, ring return lines into a new checkout session and provide
     * refund allocations on {@link SettlementOptions}.</p>
     */
    public ReversalFlow<RefundResult> refund() {
        operations.track("refund");
        return new ReversalFlow<RefundResult>(flow -> executeRefund(flow, "refund", null, true))
                .session(operations);
    }

    /**
     * Partial linked refund of this session's completed payment. Also
     * reverses the loyalty award when one ran (best-effort by default).
     */
    public ReversalFlow<RefundResult> refund(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        requirePositive(amount);
        operations.track("refund");
        return new ReversalFlow<RefundResult>(flow -> executeRefund(flow, "refund", amount, true))
                .session(operations);
    }

    /**
     * Unlinked refund, not tied to a prior transaction. Payment-only — no
     * loyalty reversal.
     */
    public ReversalFlow<RefundResult> refundUnlinked(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        requirePositive(amount);
        operations.track("refundUnlinked");
        return new ReversalFlow<RefundResult>(flow -> executeRefund(flow, "refundUnlinked", amount, false))
                .session(operations);
    }

    private RefundResult executeRefund(ReversalFlow<RefundResult> flow, String name,
                                       BigDecimal amount, boolean linked) {
        operations.begin(name);
        requireRefundable(name);
        guards.requireNoReversedMoneyLeg();
        LastPayment paid = linked ? lastPayment : LastPayment.NONE;
        if (linked && paid.poiTransactionId == null) {
            throw invalidState("a linked refund requires a completed payment in this "
                    + "session; refund a prior sale through settle() with return lines "
                    + "and SettlementOptions refund allocations");
        }
        // the void guard follows the money (see ReversalGuards) — it counts
        // even when unlinked, since through a checkout session an unlinked
        // refund is almost certainly returning this checkout's money
        boolean awardReversed = guards.awardReversed();
        return reversalManager.refund(amount, null,
                paid.poiTransactionId, paid.poiTransactionTimestamp,
                awardReversed ? null : paid.awardPoiTransactionId,
                awardReversed ? null : paid.awardPoiTransactionTimestamp,
                linked ? reversalMemberId() : null,
                flow.decider(),
                guards::markRefunded,
                guards::markAwardReversed);
    }

    /** The identified member for a reversal's {@code LoyaltyData}, or {@code null}. */
    private String reversalMemberId() {
        IdentifyResult currentMember = getMember();
        return currentMember != null ? currentMember.getMemberId() : null;
    }

    /**
     * Refunds are allowed on sessions whose payment has completed or
     * failed — unlike {@link #requireNotEnded}, {@code COMPLETED} is a
     * refundable state so a register can refund the payment it just took.
     * The pre-payment states are allowed for {@code refundUnlinked}.
     */
    private void requireRefundable(String operationName) {
        requireState(EnumSet.of(SessionState.IDLE, SessionState.IDENTIFIED,
                SessionState.ACTIVE, SessionState.COMPLETED, SessionState.FAILED),
                operationName);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("refund amount must be positive");
        }
    }

    // ─── Void ───

    /**
     * Reverses this session's completed payment: every movement it
     * committed — the card and stored value legs (Nexo
     * {@code ReversalRequest}), then the redemption, rebate, and award
     * (their {@code LoyaltyRequest} refund types) — in that order. A
     * checkout fully covered by rewards has no money leg; voiding it
     * refunds the loyalty movements alone. To void a sale taken by an
     * earlier session, use {@link #voidTransaction(OriginalSaleRecord)} on
     * a fresh idle checkout session.
     *
     * <p>When a step fails, the flow's {@link ReversalFlow#onError onError}
     * handler decides between retry, skip, and abort — see
     * {@link ReversalFlow} for the default policy. A retried void resumes
     * at the first movement still standing — reversed movements are never
     * re-credited, and the retry's {@link VoidResult} describes only the
     * movements that call sent.</p>
     *
     * <p>On a session whose payment failed with an incomplete rollback,
     * {@code voidTransaction()} finishes the unwind by retrying the
     * reversals that did not go through. Not allowed once the payment has
     * been refunded from this session — a void would return the full amount
     * on top of the refund, so further returns must use
     * {@link #refund(BigDecimal)}. The session moves to
     * {@link SessionState#VOIDED} on success.</p>
     */
    public ReversalFlow<VoidResult> voidTransaction() {
        operations.track("voidTransaction");
        return new ReversalFlow<VoidResult>(this::executeVoid)
                .session(operations);
    }

    /**
     * Voids a prior sale by its persisted original-sale record. This is a
     * whole-transaction void: every referenced card, stored value, rebate,
     * redemption, and award movement is reversed in the same order as a
     * same-session void. Use this from a fresh idle checkout session for
     * the first attempt; if that void partially fails, retry it on the same
     * session instance because the in-memory reversed-step progress is what
     * prevents already-reversed legs from being sent again. Mixed sale/return
     * settlements should use {@link #settle(SettlementOptions)} with refund
     * allocations instead.
     */
    public ReversalFlow<VoidResult> voidTransaction(OriginalSaleRecord originalSale) {
        Objects.requireNonNull(originalSale, "originalSale");
        operations.track("voidTransaction");
        return new ReversalFlow<VoidResult>(flow -> executeVoid(flow, originalSale))
                .session(operations);
    }

    private VoidResult executeVoid(ReversalFlow<VoidResult> flow) {
        operations.begin("voidTransaction");
        // a failed settlement whose rollback was incomplete left movements
        // standing; voiding that session means finishing the unwind
        // state first (advisory — the locked check below stays
        // authoritative): an ended or settling session is reported as such,
        // not by a guard whose remedy the state would also refuse
        requireState(EnumSet.of(SessionState.IDLE, SessionState.COMPLETED,
                SessionState.FAILED), "voidTransaction");
        boolean resumeRollback = rollbackIncomplete();
        List<ReversalMovement> movements = List.of();
        if (!resumeRollback) {
            if (hasCommittedRefundAllocations()) {
                throw invalidState("voidTransaction cannot reverse committed refund "
                        + "allocations; retry settle() with the same refund allocations");
            }
            guards.requireNotRefunded();
            if (lastSettlementIncludesRefunds) {
                throw invalidState("voidTransaction is only supported for a pure sale "
                        + "settlement; this settlement included refund allocations");
            }
            movements = voidTarget();
            if (movements.isEmpty()) {
                throw invalidState(
                        "voidTransaction requires a completed payment in this "
                                + "session; to void a prior sale, use "
                                + "voidTransaction(OriginalSaleRecord)");
            }
        }
        SessionState stateBeforeVoid;
        lock.lock();
        try {
            SessionError error = stateMachine.requireState(
                    EnumSet.of(SessionState.IDLE, SessionState.COMPLETED, SessionState.FAILED),
                    "voidTransaction");
            if (error != null) {
                throw new SessionException(error);
            }
            stateBeforeVoid = stateMachine.current();
            stateMachine.transitionTo(SessionState.VOIDING);
        } finally {
            lock.unlock();
        }
        try {
            if (resumeRollback) {
                drainStandingMovements();
            }
            // the manager filters against the reversed-steps set (and
            // records progress into it), so a retry resumes at the
            // movements still standing while the default policy still sees
            // the whole target
            VoidResult result = reversalManager.voidMovements(movements, reversalMemberId(),
                    flow.decider(), guards.reversedSteps());
            transitionLocked(SessionState.VOIDED);
            return result;
        } catch (RuntimeException e) {
            // a failed void leaves the referenced transaction standing, so
            // the session returns to its pre-void state — a COMPLETED
            // payment must not become FAILED, which would let settle() retry
            // and authorize a second charge on top of the original.
            // Catching all RuntimeExceptions (not just SessionException)
            // matters: VOIDING has no other exits, so an unexpected error
            // must never leave the session stranded there.
            transitionLocked(stateBeforeVoid);
            throw e;
        }
    }

    private VoidResult executeVoid(ReversalFlow<VoidResult> flow,
                                   OriginalSaleRecord originalSale) {
        operations.begin("voidTransaction");
        if (!originalSale.hasMovement()) {
            throw invalidState("voidTransaction(OriginalSaleRecord) requires at least "
                    + "one original transaction reference");
        }
        requireState(EnumSet.of(SessionState.IDLE), "voidTransaction");
        List<ReversalMovement> movements = voidTarget(originalSale);
        Set<ReversalStep> reversedSteps = priorSaleVoidProgress(originalSale);
        SessionState stateBeforeVoid;
        lock.lock();
        try {
            SessionError error = stateMachine.requireState(
                    EnumSet.of(SessionState.IDLE), "voidTransaction");
            if (error != null) {
                throw new SessionException(error);
            }
            stateBeforeVoid = stateMachine.current();
            stateMachine.transitionTo(SessionState.VOIDING);
        } finally {
            lock.unlock();
        }
        try {
            VoidResult result = reversalManager.voidMovements(movements,
                    originalSale.getMemberId(), flow.decider(),
                    reversedSteps);
            clearPriorSaleVoidProgress(originalSale);
            transitionLocked(SessionState.VOIDED);
            return result;
        } catch (RuntimeException e) {
            transitionLocked(stateBeforeVoid);
            throw e;
        }
    }

    /** The completed payment's movements, in reversal order. */
    private List<ReversalMovement> voidTarget() {
        LastPayment paid = lastPayment;
        return ReversalMovement.ofSale(
                PoiRef.ofNullable(paid.poiTransactionId, paid.poiTransactionTimestamp),
                PoiRef.ofNullable(paid.storedValuePoiTransactionId,
                        paid.storedValuePoiTransactionTimestamp),
                PoiRef.ofNullable(paid.redemptionPoiTransactionId,
                        paid.redemptionPoiTransactionTimestamp),
                PoiRef.ofNullable(paid.rebatePoiTransactionId, paid.rebatePoiTransactionTimestamp),
                PoiRef.ofNullable(paid.awardPoiTransactionId, paid.awardPoiTransactionTimestamp));
    }

    private static List<ReversalMovement> voidTarget(OriginalSaleRecord originalSale) {
        return ReversalMovement.ofSale(
                PoiRef.ofNullable(originalSale.getCardPoiTransactionId(),
                        originalSale.getCardPoiTransactionTimestamp()),
                PoiRef.ofNullable(originalSale.getStoredValuePoiTransactionId(),
                        originalSale.getStoredValuePoiTransactionTimestamp()),
                PoiRef.ofNullable(originalSale.getRedemptionPoiTransactionId(),
                        originalSale.getRedemptionPoiTransactionTimestamp()),
                PoiRef.ofNullable(originalSale.getRebatePoiTransactionId(),
                        originalSale.getRebatePoiTransactionTimestamp()),
                PoiRef.ofNullable(originalSale.getAwardPoiTransactionId(),
                        originalSale.getAwardPoiTransactionTimestamp()));
    }

    private Set<ReversalStep> priorSaleVoidProgress(OriginalSaleRecord originalSale) {
        lock.lock();
        try {
            if (priorSaleVoidTarget == null) {
                priorSaleVoidTarget = originalSale;
                return priorSaleVoidReversedSteps;
            }
            if (Objects.equals(priorSaleVoidTarget, originalSale)) {
                return priorSaleVoidReversedSteps;
            }
            if (!priorSaleVoidReversedSteps.isEmpty()) {
                throw invalidState("a void of another prior sale is partially complete; "
                        + "retry voidTransaction(OriginalSaleRecord) with the same "
                        + "original sale record before voiding another sale");
            }
            priorSaleVoidTarget = originalSale;
            priorSaleVoidReversedSteps = ConcurrentHashMap.newKeySet();
            return priorSaleVoidReversedSteps;
        } finally {
            lock.unlock();
        }
    }

    private void clearPriorSaleVoidProgress(OriginalSaleRecord originalSale) {
        lock.lock();
        try {
            if (Objects.equals(priorSaleVoidTarget, originalSale)) {
                priorSaleVoidTarget = null;
                priorSaleVoidReversedSteps = ConcurrentHashMap.newKeySet();
            }
        } finally {
            lock.unlock();
        }
    }

    private void transitionLocked(SessionState target) {
        lock.lock();
        try {
            stateMachine.transitionTo(target);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Re-runs each standing reversal in the unwind's own order, dropping
     * movements as they succeed so a failed attempt can be retried from the
     * first movement still standing.
     */
    private void drainStandingMovements() {
        List<PaymentOrchestrator.StandingMovement> remaining;
        lock.lock();
        try {
            // take ownership atomically: concurrent drains (abort() vs a
            // retried settle() vs a second abort()) must not reverse the same
            // movement twice, and only ONE drain may be reversing at a time
            // — a concurrent caller fails fast instead of concluding from
            // the empty list that the rollback completed
            if (drainInFlight) {
                throw invalidState("another recovery attempt is already reversing the "
                        + "standing movements; retry once it settles");
            }
            remaining = new ArrayList<>(standingMovements);
            if (remaining.isEmpty()) {
                return;
            }
            standingMovements = List.of();
            drainInFlight = true;
        } finally {
            lock.unlock();
        }
        try {
            for (Iterator<PaymentOrchestrator.StandingMovement> it = remaining.iterator();
                    it.hasNext(); ) {
                it.next().reverse();
                it.remove();
            }
        } finally {
            // publish the outcome under the same lock the claim used: the
            // remainder (if a reversal failed) and the end of the drain
            lock.lock();
            try {
                if (!remaining.isEmpty()) {
                    standingMovements = List.copyOf(remaining);
                }
                drainInFlight = false;
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * The previous payment's unwind left movements standing — or a drain
     * has claimed them and is still reversing on the wire, which every
     * guard must treat the same way: the rollback is not complete.
     */
    private boolean rollbackIncomplete() {
        return !standingMovements.isEmpty() || drainInFlight;
    }

    /** The uniform guard failure for operations the session cannot honor. */
    private static SessionException invalidState(String message) {
        return new SessionException(
                new SessionError(SessionErrorCode.INVALID_STATE, message));
    }

    // ─── Display ───

    /**
     * Refreshes the customer display from the given basket snapshot using the
     * configured {@link DisplayRenderer}. Failures are delivered through the
     * returned result's {@code onError}, not the session's
     * {@code onBackgroundError} — that handler is only for pushes the
     * session initiates itself. Allowed until the session has ended.
     */
    public SessionResult<Void> updateDisplay(Basket basket) {
        Objects.requireNonNull(basket, "basket");
        return this.<Void>operation("updateDisplay", () -> {
            requireSessionNotEnded("updateDisplay");
            display.show(basket, stateMachine.current());
            return null;
        });
    }

    /** Shows a basket snapshot; callers decide whether failures are fatal. */
    private void showBasket(Basket basket) {
        display.show(basket, stateMachine.current());
    }

    /**
     * Sends a custom display payload to the customer display (or the external
     * display when configured). Same contract as
     * {@link #updateDisplay(Basket)}.
     */
    public SessionResult<Void> updateDisplay(DisplayPayload payload) {
        Objects.requireNonNull(payload, "payload");
        return this.<Void>operation("updateDisplay", () -> {
            requireSessionNotEnded("updateDisplay");
            display.send(payload);
            return null;
        });
    }

    // ─── Abort ───

    /**
     * Aborts the in-flight operation. The session itself continues — an
     * abort is a normal register maneuver (cancel the signature prompt, stop
     * a tender to take a gift card first), not an abandonment; to abandon
     * the checkout, {@link #end()} the session.
     *
     * <p>If a terminal operation is awaiting a response, a Nexo
     * {@code AbortRequest} referencing it is sent (best-effort) to the
     * device that is processing it. An aborted payment stops at its next
     * step boundary, reverses the committed steps, and settles in
     * {@link SessionState#FAILED} — the basket stays intact and
     * {@code settle()} may retry (the thrown error carries
     * {@link SessionErrorCode#ABORTED}). Aborted prompts (input, PIN, card
     * reads, identification) deliver their aborted/cancelled outcome and
     * leave the session state unchanged. With nothing in flight this is a
     * no-op.</p>
     *
     * <p>Money-moving operations (refunds, stored value) always deliver
     * their outcome even when the abort raced them: the movement may have
     * completed on the terminal, and the register must know. Voids and the
     * session lifecycle signals are never the abort's target — cancelling
     * an in-flight {@link #end()} would only strand the terminal's
     * session-scoped data. An abort that lands after the payment completed
     * leaves the transaction standing; use {@code voidTransaction()} to
     * reverse it.</p>
     *
     * <p>Deliberately <em>unordered</em>: queued on the session's operation
     * lane, the abort would wait on the very operation it cancels, so it
     * overtakes it instead. Safe to call from any thread.</p>
     */
    public SessionResult<Void> abort() {
        return this.<Void>operation("abort", () -> {
            lock.lock();
            try {
                // flag and state check share this critical section, so the
                // reset a starting payment performs entering SETTLING cannot
                // eat a live abort. Outside SETTLING the flag stays clear: a
                // stale abort must not kill the next payment at its first
                // checkAbort, and prompts are aborted via the wire request
                // below.
                if (stateMachine.current() == SessionState.SETTLING) {
                    abortRequested = true;
                }
            } finally {
                lock.unlock();
            }
            // the session lifecycle signals are never the abort's target —
            // like VOIDING, an in-flight end() always settles
            exchange.abortInFlight();
            return null;
        }).unordered();
    }

    // ─── Session lifecycle ───

    /**
     * Announces this session to the terminal. Invoked by the builder's
     * {@link Builder#start() start()} — the session is handed out only after
     * this succeeds, so an unstarted session never escapes.
     */
    private CheckoutSession started() {
        exchange.sendSessionSignal(SessionSignalCodec.start(sessionId));
        return this;
    }

    /**
     * Ends the session: tells the terminal to discard the session-scoped
     * data it accumulated (Nexo {@code Admin} session end signal) and moves
     * the session to {@link SessionState#ENDED}, from which no operation of
     * any kind — including a restart — is allowed. Create a new session for
     * the next checkout.
     *
     * <p>Allowed from any state except {@code SETTLING} and {@code VOIDING}
     * (money in flight), and refused while a failed payment's rollback is
     * incomplete, or while refund allocations from a failed settlement have
     * committed — finish the unwind with {@link #voidTransaction()} or retry
     * {@code settle()} first, or the terminal/register state would be
     * abandoned with money movements still unresolved. If the end signal
     * fails, the session keeps its current state so the call can be retried.
     * A concurrent {@link #abort()} never cancels an in-flight end — the
     * exchange always settles, and its success still moves the session to
     * {@code ENDED}.</p>
     */
    public SessionResult<Void> end() {
        return operation("end", () -> {
            lock.lock();
            try {
                SessionState state = stateMachine.current();
                if (state == SessionState.SETTLING || state == SessionState.VOIDING) {
                    throw invalidState("end() is not allowed while an operation is in "
                            + "flight (state " + state + ")");
                }
                if (state == SessionState.ENDED) {
                    throw invalidState(
                            "the session has already ended; create a new session");
                }
                if (rollbackIncomplete()) {
                    throw invalidState("a failed payment's rollback is incomplete; "
                            + "finish the unwind with voidTransaction() before ending "
                            + "the session");
                }
                if (hasCommittedRefundAllocations()) {
                    throw invalidState("refund allocations from a failed settlement are "
                            + "committed; retry settle() with the same refund allocations "
                            + "before ending the session");
                }
            } finally {
                lock.unlock();
            }
            exchange.sendSessionSignal(SessionSignalCodec.end(sessionId));
            transitionLocked(SessionState.ENDED);
            // no further operations may run; asynchronous submissions after
            // this fail into their handlers instead of queueing forever
            operations.shutdown();
            return null;
        });
    }

    /**
     * Best-effort {@link #end()} for try-with-resources: a failure to send
     * the end signal, or a lifecycle state that refuses {@code end()}, is
     * logged, not thrown, and an already-ended session is left alone.
     * Registers that need to react to a failed end should call
     * {@code end()} directly.
     *
     * <p>Blocking, and queued behind any in-flight operation — so with a
     * callback executor configured, never call it from that executor's
     * thread while operations may be in flight: the in-flight operation
     * may need this thread for its handlers before it can finish, and
     * both would wait forever. UI-driven teardown should use
     * {@code end().execute()} with an {@code onComplete} instead; close()
     * is for try-with-resources and process-exit paths.</p>
     */
    @Override
    public void close() {
        if (stateMachine.current() == SessionState.ENDED) {
            return;
        }
        // synchronous deliberately: close() runs on teardown paths (often
        // try-with-resources or process exit) where a queued async end
        // would be lost with the closing scope
        end().onError(e -> LOGGER.warning("close() could not end the session: " + e))
                .executeSync();
    }

    /**
     * Guard for operations that deliberately keep working after a checkout
     * settles in {@code COMPLETED} or {@code VOIDED} (status queries for
     * receipt reprints): the one state that shuts them off is
     * {@link SessionState#ENDED} — the register said goodbye and the
     * terminal discarded the session.
     */
    private void requireSessionNotEnded(String operationName) {
        if (stateMachine.current() == SessionState.ENDED) {
            throw invalidState(operationName
                    + " is not allowed after end(); create a new session");
        }
    }

    // ─── Transaction Status ───

    /**
     * Checks the status of a prior request by the {@code ServiceID} it was
     * sent with. Maps to Nexo {@code TransactionStatusRequest}; the terminal
     * repeats the original response when the transaction is found.
     *
     * @param originalServiceId the {@code ServiceID} of the original request
     */
    public SessionResult<TransactionStatusResult> getTransactionStatus(String originalServiceId) {
        return getTransactionStatus(originalServiceId, TransactionStatusOptions.defaults());
    }

    /**
     * Checks the status of a prior request, optionally requesting receipt
     * data for reprinting and referencing a non-payment original.
     */
    public SessionResult<TransactionStatusResult> getTransactionStatus(
            String originalServiceId, TransactionStatusOptions options) {
        Objects.requireNonNull(originalServiceId, "originalServiceId");
        Objects.requireNonNull(options, "options");
        return operation("getTransactionStatus", () -> {
            requireSessionNotEnded("getTransactionStatus");
            TransactionStatusRequest.Builder statusRequest = TransactionStatusRequest.builder()
                    .messageReference(MessageReference.builder()
                            .messageCategory(options.getOriginalCategory())
                            .serviceID(originalServiceId)
                            .saleID(factory.getSaleId())
                            .build());
            if (options.isReceiptReprint()) {
                statusRequest.receiptReprintFlag(true)
                        .documentQualifier(options.getDocumentQualifiers()
                                .toArray(new DocumentQualifierEnum[0]));
            }
            SaleToPOIRequest request = SaleToPOIRequest.builder()
                    .messageHeader(factory.header(MessageClassType.SERVICE,
                            MessageCategoryType.TRANSACTION_STATUS))
                    .transactionStatusRequest(statusRequest.build())
                    .build();
            SaleToPOIResponse response = exchange.sendExpectingBody(
                    MessageCategoryType.TRANSACTION_STATUS, request);
            TransactionStatusResponse body = response.getTransactionStatusResponse();
            if (body == null) {
                throw Wire.missing("TransactionStatusResponse");
            }
            if (body.getResponse() != null
                    && body.getResponse().getResult() == ResultType.FAILURE
                    && body.getResponse().getErrorCondition() == ErrorConditionType.NOT_FOUND) {
                return TransactionStatusResult.notFound();
            }
            exchange.requireSuccess(MessageCategoryType.TRANSACTION_STATUS, body.getResponse());
            return toTransactionStatusResult(body);
        });
    }

    private static TransactionStatusResult toTransactionStatusResult(TransactionStatusResponse body) {
        RepeatedResponseMessageBody repeated = body.getRepeatedMessageResponse() == null
                ? null
                : body.getRepeatedMessageResponse().getRepeatedResponseMessageBody();
        if (repeated == null) {
            return TransactionStatusResult.notFound();
        }
        String category = null;
        if (body.getRepeatedMessageResponse().getMessageHeader() != null
                && body.getRepeatedMessageResponse().getMessageHeader().getMessageCategory() != null) {
            category = body.getRepeatedMessageResponse().getMessageHeader()
                    .getMessageCategory().toValue();
        }
        return TransactionStatusResult.found(category,
                repeated.getPaymentResponse(),
                repeated.getLoyaltyResponse(),
                repeated.getStoredValueResponse(),
                repeated.getReversalResponse());
    }

    // ─── Input update ───

    /**
     * Replaces the display content of the input prompt currently awaiting a
     * response (Nexo {@code InputUpdate}) — e.g. to update a countdown or
     * amend the prompt while the customer decides.
     *
     * <p>Because input calls block their calling thread, this must be
     * invoked from a different thread; like {@link #abort()} it is safe to
     * do so. Fails with {@link SessionErrorCode#INVALID_STATE} when no input
     * is in progress.</p>
     */
    public SessionResult<Void> updateInputDisplay(DisplayPayload payload) {
        Objects.requireNonNull(payload, "payload");
        // unordered, like abort(): both exist to overlap the in-flight
        // operation occupying the operation thread — queued behind it,
        // this would wait on the very prompt it amends, and an abort on
        // the very operation it cancels
        return this.<Void>operation("updateInputDisplay", () -> {
            NexoExchange.InFlight inFlight = exchange.currentInFlight();
            if (inFlight == null || inFlight.getCategory() != MessageCategoryType.INPUT) {
                throw invalidState(
                        "updateInputDisplay requires an input request awaiting a response");
            }
            String base64;
            try {
                base64 = DisplayPayloadHelper.toBase64(payload);
            } catch (JAXBException e) {
                throw new SessionException(new SessionError(SessionErrorCode.UNKNOWN,
                        "failed to serialize input update payload", null, e));
            }
            SaleToPOIRequest request = SaleToPOIRequest.builder()
                    .messageHeader(factory.header(MessageClassType.DEVICE,
                            MessageCategoryType.INPUT_UPDATE))
                    .inputUpdate(InputUpdate.builder()
                            .messageReference(MessageReference.builder()
                                    .messageCategory(MessageCategoryType.INPUT)
                                    .serviceID(inFlight.getServiceId())
                                    .saleID(factory.getSaleId())
                                    .build())
                            .outputContent(OutputContent.builder()
                                    .outputFormat(OutputFormatEnum.XHTML)
                                    .outputXHTML(base64)
                                    .build())
                            .build())
                    .build();
            try {
                // must go to the device processing the input, not through routing
                inFlight.getClient().request(factory.envelope(request));
            } catch (BiltNexoClientException e) {
                throw new SessionException(new SessionError(SessionErrorCode.NETWORK,
                        "input update failed: " + e.getMessage(), null, e));
            }
            return null;
        }).unordered();
    }

    // ─── Terminal (device & admin operations) ───

    /**
     * The device and admin operations of this session's terminal —
     * {@code diagnose()}, {@code getTotals()}, {@code reconcile()},
     * {@code print()}, {@code playSound()}/{@code stopSound()} — built from
     * this session's client, identifiers, and callback executor. Created
     * lazily and cached; see {@link Terminal}.
     *
     * <p>The terminal is deliberately independent of the session: it has its
     * own operation thread and exchange, so its operations do not queue
     * behind an in-flight payment (a connectivity check mid-payment works),
     * they keep working after {@link #end()}, and its {@link Terminal#close()
     * close()} does not touch the session. For the same reason
     * {@link #abort()} does not target terminal operations — they run on a
     * separate exchange.</p>
     */
    public Terminal terminal() {
        Terminal current = terminal;
        if (current != null) {
            return current;
        }
        lock.lock();
        try {
            if (terminal == null) {
                terminal = Terminal.builder()
                        .client(client)
                        .saleId(factory.getSaleId())
                        .poiId(factory.getPoiId())
                        .storeLocation(storeLocation)
                        .callbackExecutor(operations.callback())
                        .build();
            }
            return terminal;
        } finally {
            lock.unlock();
        }
    }

    // ─── Escape hatch ───

    /** The underlying terminal client, for raw Nexo access. */
    public BiltNexoTerminalClient getClient() {
        return client;
    }

    // ─── Internals ───

    private <T> SessionResult<T> operation(String name, Supplier<T> body) {
        return operations.operation(name, body);
    }

    /** Builder for {@link CheckoutSession}. */
    public static final class Builder {

        private BiltNexoTerminalClient client;
        private String saleId;
        private String poiId;
        private String currency;
        private String storeLocation;
        private boolean autoDisplay = true;
        private BiltNexoTerminalClient externalDisplayClient;
        private DisplayRenderer displayRenderer;
        private Consumer<Basket> onBasketUpdated;
        private Executor callbackExecutor;
        private Consumer<SessionError> onBackgroundError;

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
         * session creates — it groups the store's transactions for totals
         * and reconciliation ({@code getTotals()} filters by it). Optional.
         */
        public Builder storeLocation(String storeLocation) {
            this.storeLocation = storeLocation;
            return this;
        }

        /**
         * Whether basket mutations automatically refresh the customer
         * display. Default {@code true}.
         */
        public Builder autoDisplay(boolean autoDisplay) {
            this.autoDisplay = autoDisplay;
            return this;
        }

        /**
         * A second client for an external customer display device. When set,
         * {@code Display} and {@code Input} messages are routed to it, while
         * payment, card, and PIN operations stay on the terminal.
         */
        public Builder externalDisplayClient(BiltNexoTerminalClient externalDisplayClient) {
            this.externalDisplayClient = externalDisplayClient;
            return this;
        }

        /**
         * Custom rendering of basket snapshots for the customer display.
         * Rarely necessary; defaults to the standard itemised receipt.
         */
        public Builder displayRenderer(DisplayRenderer displayRenderer) {
            this.displayRenderer = displayRenderer;
            return this;
        }

        /**
         * Out-of-band basket update callback. Reserved for a future reactive
         * mode in which the terminal pushes offer/member changes during
         * scanning; not invoked in v1.
         */
        public Builder onBasketUpdated(Consumer<Basket> onBasketUpdated) {
            this.onBasketUpdated = onBasketUpdated;
            return this;
        }

        /**
         * Where asynchronously executed operations deliver their handlers —
         * e.g. an Android main-thread executor so handlers may touch UI
         * directly. Applies to {@code execute()}; {@code executeSync()} and
         * the blocking accessors are unaffected. Overridable per call with
         * {@code callbackOn(executor)}. Without one, handlers run directly
         * on the session's operation thread and must be fast, non-blocking,
         * and must never synchronously invoke another session operation.
         */
        public Builder callbackExecutor(Executor callbackExecutor) {
            this.callbackExecutor = callbackExecutor;
            return this;
        }

        /**
         * Handler for failures of work the session performs on its own
         * behalf, with no result object to report through: the automatic
         * display push after a basket mutation ({@link #autoDisplay(boolean)})
         * and the final settlement display refresh. Manual
         * {@code updateDisplay(...)} failures are not delivered here — they
         * report through their own per-call {@code onError}.
         *
         * <p>Background failures never interrupt the checkout and are
         * logged whether or not a handler is registered. Delivered through
         * the {@link #callbackExecutor(Executor) callbackExecutor} when one
         * is configured, directly on the failing thread otherwise.</p>
         */
        public Builder onBackgroundError(Consumer<SessionError> onBackgroundError) {
            this.onBackgroundError = onBackgroundError;
            return this;
        }

        /**
         * Validates the configuration and returns a lazy operation that
         * announces the session to the terminal (Nexo {@code Admin} session
         * start signal) and yields it once the terminal acknowledged — an
         * unstarted session never exists, so no operation can reach the
         * terminal before the start. Like every session operation, nothing
         * is sent until {@code execute()}, {@code get()}, or
         * {@code getOrNull()} is invoked.
         *
         * <p>A refused start yields no session; call {@code start()} again
         * for a fresh attempt (each attempt is a new session with a new
         * session ID).</p>
         *
         * <p>If the registered {@code onSuccess} handler itself throws, the
         * just-started session is ended on the terminal (best-effort) before
         * the exception propagates: a {@code start()} whose execution threw
         * never leaves a terminal-side session behind, and any session it
         * may have delivered to the handler must be considered lost.</p>
         *
         * @throws IllegalStateException if a required field is missing
         */
        public SessionResult<CheckoutSession> start() {
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
            CheckoutSession session = new CheckoutSession(this);
            // the terminal has acknowledged Start by the time onSuccess
            // runs; a handler that throws would strand that session-scoped
            // context with no session object to end it, so it is released.
            // Built through the session's operations so an asynchronous
            // start runs on (and its handlers deliver like) every other
            // operation of the session it creates.
            return session.operations.operation("start", session::started)
                    .releasing(CheckoutSession::close);
        }
    }
}
