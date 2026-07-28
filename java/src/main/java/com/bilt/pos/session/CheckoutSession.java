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
import com.bilt.pos.nexo.model.AbortRequest;
import com.bilt.pos.nexo.model.DiagnosisRequest;
import com.bilt.pos.nexo.model.DiagnosisResponse;
import com.bilt.pos.nexo.model.DocumentQualifierEnum;
import com.bilt.pos.nexo.model.ErrorConditionType;
import com.bilt.pos.nexo.model.GetTotalsRequest;
import com.bilt.pos.nexo.model.GetTotalsResponse;
import com.bilt.pos.nexo.model.InputUpdate;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.MessageClassType;
import com.bilt.pos.nexo.model.MessageReference;
import com.bilt.pos.nexo.model.OutputContent;
import com.bilt.pos.nexo.model.OutputFormatEnum;
import com.bilt.pos.nexo.model.OutputText;
import com.bilt.pos.nexo.model.PrintOutput;
import com.bilt.pos.nexo.model.PrintRequest;
import com.bilt.pos.nexo.model.PrintResponse;
import com.bilt.pos.nexo.model.ReconciliationRequest;
import com.bilt.pos.nexo.model.ReconciliationResponse;
import com.bilt.pos.nexo.model.ReconciliationTypeEnum;
import com.bilt.pos.nexo.model.RepeatedResponseMessageBody;
import com.bilt.pos.nexo.model.ResponseModeEnum;
import com.bilt.pos.nexo.model.ResultType;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.SaleToPOIResponse;
import com.bilt.pos.nexo.model.SoundActionEnum;
import com.bilt.pos.nexo.model.SoundContent;
import com.bilt.pos.nexo.model.SoundFormatEnum;
import com.bilt.pos.nexo.model.SoundRequest;
import com.bilt.pos.nexo.model.StoredValueTransactionTypeEnum;
import com.bilt.pos.nexo.model.TotalFilter;
import com.bilt.pos.nexo.model.TransactionStatusRequest;
import com.bilt.pos.nexo.model.TransactionStatusResponse;
import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.basket.BasketMutation;
import com.bilt.pos.session.display.DisplayContext;
import com.bilt.pos.session.display.DisplayRenderer;
import com.bilt.pos.session.display.DisplayTarget;
import com.bilt.pos.session.identity.CardAcquisitionOptions;
import com.bilt.pos.session.identity.CardAcquisitionResult;
import com.bilt.pos.session.identity.IdentifyOptions;
import com.bilt.pos.session.identity.IdentifyResult;
import com.bilt.pos.session.identity.IdentifyStatus;
import com.bilt.pos.session.identity.MemberIdentifier;
import com.bilt.pos.session.identity.Reward;
import com.bilt.pos.session.input.ConfirmationOptions;
import com.bilt.pos.session.input.InputOptions;
import com.bilt.pos.session.input.MenuOptions;
import com.bilt.pos.session.input.MenuSelection;
import com.bilt.pos.session.input.PinMode;
import com.bilt.pos.session.input.PinOptions;
import com.bilt.pos.session.input.PinResult;
import com.bilt.pos.session.input.Signature;
import com.bilt.pos.session.internal.BasketDisplayRenderer;
import com.bilt.pos.session.internal.BasketEngine;
import com.bilt.pos.session.internal.DisplayRouter;
import com.bilt.pos.session.internal.IdentityManager;
import com.bilt.pos.session.internal.InputManager;
import com.bilt.pos.session.internal.LoyaltyPayloadCodec;
import com.bilt.pos.session.internal.NexoExchange;
import com.bilt.pos.session.internal.NexoMessageFactory;
import com.bilt.pos.session.internal.PaymentOrchestrator;
import com.bilt.pos.session.internal.RefundManager;
import com.bilt.pos.session.internal.SessionStateMachine;
import com.bilt.pos.session.internal.StoredValueManager;
import com.bilt.pos.session.internal.Wire;
import com.bilt.pos.session.payment.CheckoutResult;
import com.bilt.pos.session.payment.PaymentOptions;
import com.bilt.pos.session.storedvalue.StoredValueBalance;
import com.bilt.pos.session.storedvalue.StoredValueCard;
import com.bilt.pos.session.storedvalue.StoredValueOperationResult;

import jakarta.xml.bind.JAXBException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A stateful, loyalty-enabled checkout on top of {@link BiltNexoTerminalClient}.
 *
 * <p>The session owns the basket, drives the terminal (or an external
 * customer display), and orchestrates the payment sequence — rebate
 * redemption, point redemption, stored value, card payment, and reward award
 * — as a single flow. Every operation maps to standard Nexo Sale to POI 3.0
 * messages; the raw client remains available via {@link #getClient()}.</p>
 *
 * <p>Terminal operations are lazy: methods returning {@link SessionResult}
 * (or a payment flow) send nothing until {@code execute()}, {@code get()}, or
 * {@code getOrNull()} is invoked. All I/O and handlers run blocking on the
 * calling thread.</p>
 *
 * <pre>{@code
 * CheckoutSession session = CheckoutSession.builder()
 *     .client(client)
 *     .saleId("POS-LANE-3")
 *     .poiId("VictaLane-275839164")
 *     .currency("USD")
 *     .build();
 * }</pre>
 *
 * <p>Sessions are intended for use from a single register thread.
 * {@link #abort()} and {@link #updateInputDisplay(DisplayPayload)} are the
 * only methods that are safe to call from another thread.</p>
 */
public final class CheckoutSession {

    private static final Logger LOGGER = Logger.getLogger(CheckoutSession.class.getName());

    private final String sessionId = UUID.randomUUID().toString();
    private final SessionStateMachine stateMachine = new SessionStateMachine();
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicReference<String> unexecutedOperation = new AtomicReference<>();

    private final BiltNexoTerminalClient client;
    private final NexoMessageFactory factory;
    private final NexoExchange exchange;
    private final DisplayRouter router;
    private final BasketEngine basketEngine = new BasketEngine();
    private final DisplayRenderer displayRenderer;
    private final Consumer<Basket> onBasketUpdated;
    private final String currency;
    private final String storeLocation;
    private final String poiTransactionId;
    private final Instant poiTransactionTimestamp;
    private final String awardPoiTransactionId;
    private final Instant awardPoiTransactionTimestamp;
    private final String builderMemberId;
    private final boolean autoDisplay;

    private final IdentityManager identityManager;
    private final InputManager inputManager;
    private final RefundManager refundManager;
    private final PaymentOrchestrator paymentOrchestrator;
    private final StoredValueManager storedValueManager;

    private volatile boolean abortRequested;
    private boolean memberIdentified;
    private volatile IdentifyResult member;
    private volatile StoredValueCard storedValueCard;
    private volatile String lastPoiTransactionId;
    private volatile Instant lastPoiTransactionTimestamp;
    private volatile String lastStoredValuePoiTransactionId;
    private volatile Instant lastStoredValuePoiTransactionTimestamp;
    private volatile String lastAwardPoiTransactionId;
    private volatile Instant lastAwardPoiTransactionTimestamp;
    private volatile String lastRebatePoiTransactionId;
    private volatile Instant lastRebatePoiTransactionTimestamp;
    private volatile String lastRedemptionPoiTransactionId;
    private volatile Instant lastRedemptionPoiTransactionTimestamp;
    private volatile boolean voidCardLegReversed;
    private volatile boolean voidRedemptionLegReversed;

    private CheckoutSession(Builder builder) {
        this.client = builder.client;
        this.currency = builder.currency;
        this.storeLocation = builder.storeLocation;
        this.poiTransactionId = builder.poiTransactionId;
        this.poiTransactionTimestamp = builder.poiTransactionTimestamp;
        this.awardPoiTransactionId = builder.awardPoiTransactionId;
        this.awardPoiTransactionTimestamp = builder.awardPoiTransactionTimestamp;
        this.builderMemberId = builder.memberId;
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
        this.refundManager = new RefundManager(exchange, builder.currency);
        this.paymentOrchestrator = new PaymentOrchestrator(exchange, builder.currency);
        this.storedValueManager = new StoredValueManager(exchange, builder.currency);
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

    /** An immutable snapshot of the current basket. */
    public Basket getBasket() {
        lock.lock();
        try {
            return basketEngine.snapshot();
        } finally {
            lock.unlock();
        }
    }

    // ─── Basket Operations ───

    /**
     * Adds an item. When the SKU is already in the basket, its quantity is
     * incremented (upsert).
     *
     * @return the updated basket snapshot
     * @throws IllegalStateException if the basket is frozen (payment in
     *         progress) or the session has ended
     */
    public Basket addItem(BasketItem item) {
        Objects.requireNonNull(item, "item");
        return mutateBasket(basket -> basket.addItem(item));
    }

    /** Adds an item with an explicit item ID (numeric string, new SKUs only). */
    public Basket addItem(BasketItem item, String itemId) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(itemId, "itemId");
        return mutateBasket(basket -> basket.addItem(item, itemId));
    }

    /** Removes the line with the given session-assigned item ID. */
    public Basket removeItem(String itemId) {
        Objects.requireNonNull(itemId, "itemId");
        return mutateBasket(basket -> basket.removeItem(itemId));
    }

    /** Removes the line with the given SKU. */
    public Basket removeItemBySku(String sku) {
        Objects.requireNonNull(sku, "sku");
        return mutateBasket(basket -> basket.removeItemBySku(sku));
    }

    /** Sets an absolute quantity; {@code 0} removes the line. */
    public Basket updateItemQuantity(String itemId, int quantity) {
        Objects.requireNonNull(itemId, "itemId");
        return mutateBasket(basket -> basket.updateItemQuantity(itemId, quantity));
    }

    /** Sets an absolute quantity by SKU; {@code 0} removes the line. */
    public Basket updateItemQuantityBySku(String sku, int quantity) {
        Objects.requireNonNull(sku, "sku");
        return mutateBasket(basket -> basket.updateItemQuantityBySku(sku, quantity));
    }

    /**
     * Applies a batch of basket mutations atomically, followed by a single
     * display update.
     */
    public Basket mutate(Consumer<BasketMutation> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        return mutateBasket(mutation);
    }

    // ─── Tax ───

    /** Sets the tax rate on a line ({@code taxAmount = adjustedTotal × rate});
     * clears any explicit fixed tax amount previously set on it. */
    public Basket setTaxRate(String itemId, BigDecimal rate) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(rate, "rate");
        return mutateBasket(basket -> basket.setTaxRate(itemId, rate));
    }

    /** Sets the tax rate on a line by SKU. */
    public Basket setTaxRateBySku(String sku, BigDecimal rate) {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(rate, "rate");
        return mutateBasket(basket -> basket.setTaxRateBySku(sku, rate));
    }

    /** Sets a fixed tax amount on a line, overriding any rate. */
    public Basket setTaxAmount(String itemId, BigDecimal amount) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(amount, "amount");
        return mutateBasket(basket -> basket.setTaxAmount(itemId, amount));
    }

    /** Sets a fixed tax amount on a line by SKU. */
    public Basket setTaxAmountBySku(String sku, BigDecimal amount) {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(amount, "amount");
        return mutateBasket(basket -> basket.setTaxAmountBySku(sku, amount));
    }

    /**
     * Overrides the basket's total tax; passing {@code null} restores
     * item-level computation.
     */
    public Basket setTaxTotal(BigDecimal amount) {
        return mutateBasket(basket -> basket.setTaxTotal(amount));
    }

    private Basket mutateBasket(Consumer<BasketMutation> mutation) {
        Basket snapshot;
        lock.lock();
        try {
            SessionState state = stateMachine.current();
            if (state != SessionState.IDLE && state != SessionState.IDENTIFIED
                    && state != SessionState.ACTIVE && state != SessionState.FAILED) {
                throw new IllegalStateException(
                        "the basket cannot be modified in state " + state);
            }
            // atomic: a mutation (or batch) that throws restores the basket,
            // and the state transitions below only run on success
            basketEngine.mutateAtomically(mutation);
            if (state == SessionState.FAILED) {
                // the failed payment was fully unwound; editing the basket
                // resumes the checkout (e.g. dropping an item before a retry)
                stateMachine.transitionTo(SessionState.ACTIVE);
            }
            syncStateWithBasket();
            snapshot = basketEngine.snapshot();
        } finally {
            lock.unlock();
        }
        if (autoDisplay) {
            showBasket(snapshot);
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
        requireState(EnumSet.of(SessionState.IDLE, SessionState.IDENTIFIED, SessionState.ACTIVE),
                "identifyMember");
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
            return identityManager.acquireCard(options);
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
            return body.get();
        });
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
            throw new SessionException(new SessionError(SessionErrorCode.INVALID_STATE,
                    operationName + " is not allowed after the session has ended (state "
                            + state + ")"));
        }
    }

    // ─── Stored Value ───

    /**
     * Registers a stored value (gift) card charged as part of a split tender
     * during {@code pay()}. The card number is treated as keyed
     * ({@code PAN}); use {@link #setStoredValueCard(StoredValueCard)} for
     * scanned or swiped cards or to set a provider.
     */
    public void setStoredValueCard(String cardNumber) {
        this.storedValueCard = cardNumber == null ? null : StoredValueCard.number(cardNumber);
    }

    /**
     * Registers a stored value (gift) card charged as part of a split tender
     * during {@code pay()}. Pass {@code null} to clear.
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

    // ─── Payment ───

    /** Starts the payment orchestration chain with default options. */
    public PaymentFlow pay() {
        return pay(PaymentOptions.defaults());
    }

    /**
     * Starts the payment orchestration chain. Nothing is sent until the
     * returned {@link PaymentFlow}'s {@code execute()}, {@code get()}, or
     * {@code getOrNull()} is invoked.
     *
     * @throws IllegalStateException if the basket is empty or the session is
     *         not in a payable state
     */
    public PaymentFlow pay(PaymentOptions options) {
        Objects.requireNonNull(options, "options");
        SessionState state = stateMachine.current();
        if (state != SessionState.ACTIVE && state != SessionState.FAILED) {
            throw new IllegalStateException(
                    "pay() requires items in the basket (state " + state + ")");
        }
        trackUnexecuted("pay");
        return new PaymentFlow(flow -> executePayment(flow, options));
    }

    private CheckoutResult executePayment(PaymentFlow flow, PaymentOptions options) {
        unexecutedOperation.compareAndSet("pay", null);
        PaymentOrchestrator.Request request = new PaymentOrchestrator.Request();
        lock.lock();
        try {
            SessionError error = stateMachine.requireState(
                    EnumSet.of(SessionState.ACTIVE, SessionState.FAILED), "pay");
            if (error != null) {
                throw new SessionException(error);
            }
            stateMachine.transitionTo(SessionState.PAYING);
            // the abort flag is scoped to a single payment run: a stale
            // abort left over from an earlier decline or failed void must
            // not kill a legitimate retry at its first checkAbort
            abortRequested = false;
            request.basket = basketEngine.snapshot();
        } finally {
            lock.unlock();
        }
        request.member = getMember();
        request.storedValueCard = storedValueCard;
        request.options = options;
        request.abortRequested = () -> abortRequested;
        request.finalDisplay = autoDisplay ? this::showBasket : basket -> { };
        request.handlers.beforeStep = flow.beforeStepHandler();
        request.handlers.onRebatesRedeemed = flow.rebatesHandler();
        request.handlers.onPointsRedeemed = flow.pointsHandler();
        request.handlers.onGiftCardPayment = flow.giftCardHandler();
        request.handlers.onError = flow.errorHandler();

        try {
            CheckoutResult result = paymentOrchestrator.run(request);
            lock.lock();
            try {
                // this thread is the sole writer of payment-final states;
                // abort() defers to it while the session is PAYING
                stateMachine.transitionTo(SessionState.COMPLETED);
                lastPoiTransactionId = result.getPoiTransactionId();
                lastPoiTransactionTimestamp = result.getPoiTransactionTimestamp();
                lastStoredValuePoiTransactionId = result.getStoredValuePoiTransactionId();
                lastStoredValuePoiTransactionTimestamp =
                        result.getStoredValuePoiTransactionTimestamp();
                lastAwardPoiTransactionId = result.getAwardPoiTransactionId();
                lastAwardPoiTransactionTimestamp = result.getAwardPoiTransactionTimestamp();
                lastRebatePoiTransactionId = result.getRebatePoiTransactionId();
                lastRebatePoiTransactionTimestamp = result.getRebatePoiTransactionTimestamp();
                lastRedemptionPoiTransactionId = result.getRedemptionPoiTransactionId();
                lastRedemptionPoiTransactionTimestamp =
                        result.getRedemptionPoiTransactionTimestamp();
            } finally {
                lock.unlock();
            }
            if (abortRequested) {
                LOGGER.warning("abort() arrived after the payment completed; the transaction "
                        + "stands — use voidTransaction() to reverse it");
            }
            return result;
        } catch (SessionException e) {
            settlePaymentFailure(e.getError().getCode() == SessionErrorCode.ABORTED
                    ? SessionState.ABORTED : SessionState.FAILED);
            throw e;
        } catch (RuntimeException e) {
            // defense in depth: whatever escapes, the session must not stay
            // frozen in PAYING with the basket locked
            settlePaymentFailure(SessionState.FAILED);
            throw e;
        }
    }

    private void settlePaymentFailure(SessionState target) {
        lock.lock();
        try {
            stateMachine.transitionTo(target);
        } finally {
            lock.unlock();
        }
    }

    // ─── Refund ───

    /**
     * Full linked refund of the prior transaction referenced by the
     * builder's {@code poiTransactionId}. Also reverses loyalty points
     * awarded on the original transaction (best-effort).
     *
     * <p>Linked refunds reference a single transaction: after a split
     * tender this is the card leg — use {@code voidTransaction()} to
     * reverse both legs, or the stored value operations to return funds to
     * the gift card.</p>
     */
    public SessionResult<RefundResult> refund() {
        return linkedRefund(null);
    }

    /**
     * Partial linked refund of the prior transaction referenced by the
     * builder's {@code poiTransactionId}. Also reverses loyalty points
     * (best-effort).
     */
    public SessionResult<RefundResult> refund(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        requirePositive(amount);
        return linkedRefund(amount);
    }

    /**
     * Unlinked refund, not tied to a prior transaction. Payment-only — no
     * loyalty reversal.
     */
    public SessionResult<RefundResult> refundUnlinked(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        requirePositive(amount);
        return operation("refundUnlinked", () -> {
            requireRefundable("refundUnlinked");
            return refundManager.refund(amount, null, null, RefundManager.LoyaltyRef.NONE);
        });
    }

    private SessionResult<RefundResult> linkedRefund(BigDecimal amount) {
        return operation("refund", () -> {
            requireRefundable("refund");
            String originalId = effectivePoiTransactionId();
            if (originalId == null) {
                throw new SessionException(new SessionError(SessionErrorCode.INVALID_STATE,
                        "a linked refund requires poiTransactionId on the session builder "
                                + "or a completed payment in this session"));
            }
            return refundManager.refund(amount, originalId,
                    effectivePoiTransactionTimestamp(), loyaltyRef());
        });
    }

    /**
     * The award reference and member for loyalty reversals: builder-supplied
     * for external transactions, else this session's completed payment.
     */
    private RefundManager.LoyaltyRef loyaltyRef() {
        if (poiTransactionId != null) {
            return new RefundManager.LoyaltyRef(
                    awardPoiTransactionId, awardPoiTransactionTimestamp, builderMemberId);
        }
        IdentifyResult currentMember = getMember();
        return new RefundManager.LoyaltyRef(
                lastAwardPoiTransactionId, lastAwardPoiTransactionTimestamp,
                currentMember != null ? currentMember.getMemberId() : builderMemberId);
    }

    /**
     * The rewardRefs payload a redemption refund must carry, rebuilt from
     * the identified member's rewards — the same refs the redemption sent.
     */
    private String memberRewardRefsPayload() {
        IdentifyResult currentMember = getMember();
        if (currentMember == null) {
            return null;
        }
        List<String> refs = new ArrayList<>();
        for (Reward reward : currentMember.getRewards()) {
            if (reward.getRewardRef() != null) {
                refs.add(reward.getRewardRef());
            }
        }
        return LoyaltyPayloadCodec.encodeRewardRefs(refs);
    }

    /** Builder-supplied prior transaction, else the last completed payment. */
    private String effectivePoiTransactionId() {
        return poiTransactionId != null ? poiTransactionId : lastPoiTransactionId;
    }

    private Instant effectivePoiTransactionTimestamp() {
        return poiTransactionId != null ? poiTransactionTimestamp : lastPoiTransactionTimestamp;
    }

    /**
     * Refunds are allowed on fresh sessions (builder-referenced) and on
     * sessions whose payment has completed or failed — unlike
     * {@link #requireNotEnded}, {@code COMPLETED} is a refundable state so a
     * register can refund the payment it just took.
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
     * Reverses the completed transaction referenced by the builder's
     * {@code poiTransactionId} (Nexo {@code ReversalRequest}), including a
     * best-effort loyalty award reversal. A completed checkout that had no
     * payment legs — rewards covered the whole basket — is voided by
     * refunding its committed loyalty movements (redemption, rebate, award)
     * instead. The session moves to {@link SessionState#VOIDED} on success.
     */
    public SessionResult<VoidResult> voidTransaction() {
        return operation("voidTransaction", () -> {
            String originalId = effectivePoiTransactionId();
            // a checkout fully covered by rewards completes without a payment
            // leg: there is no transaction to reverse, so the void refunds
            // the committed loyalty movements instead
            boolean loyaltyOnly = originalId == null
                    && (lastRedemptionPoiTransactionId != null
                            || lastRebatePoiTransactionId != null
                            || lastAwardPoiTransactionId != null);
            if (originalId == null && !loyaltyOnly) {
                throw new SessionException(new SessionError(SessionErrorCode.INVALID_STATE,
                        "voidTransaction requires poiTransactionId on the session builder "
                                + "or a completed payment in this session"));
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
                VoidResult result;
                if (loyaltyOnly) {
                    // a retry after a partial void resumes at the rebate leg
                    // instead of re-crediting the redemption
                    result = refundManager.voidLoyalty(
                            voidRedemptionLegReversed ? null : lastRedemptionPoiTransactionId,
                            lastRedemptionPoiTransactionTimestamp,
                            memberRewardRefsPayload(),
                            lastRebatePoiTransactionId, lastRebatePoiTransactionTimestamp,
                            loyaltyRef(), () -> voidRedemptionLegReversed = true);
                } else {
                    // a session-fallback void of a split tender reverses both
                    // legs; a builder-referenced void stays single-leg (the
                    // caller controls the reference), and a gift-card-only
                    // checkout has one transaction, not two
                    String storedValueLeg = poiTransactionId == null
                            && lastStoredValuePoiTransactionId != null
                            && !lastStoredValuePoiTransactionId.equals(originalId)
                            ? lastStoredValuePoiTransactionId : null;
                    // a retry after a partial void resumes at the leg that is
                    // still standing instead of re-reversing the card payment
                    String cardLeg = voidCardLegReversed ? null : originalId;
                    result = refundManager.voidTransaction(
                            cardLeg, effectivePoiTransactionTimestamp(),
                            storedValueLeg, lastStoredValuePoiTransactionTimestamp,
                            loyaltyRef(), () -> voidCardLegReversed = true);
                }
                transitionLocked(SessionState.VOIDED);
                return result;
            } catch (RuntimeException e) {
                // a failed void leaves the referenced transaction standing, so
                // the session returns to its pre-void state — a COMPLETED
                // payment must not become FAILED, which would let pay() retry
                // and authorize a second charge on top of the original.
                // Catching all RuntimeExceptions (not just SessionException)
                // matters: VOIDING has no other exits, so an unexpected error
                // must never leave the session stranded there.
                transitionLocked(stateBeforeVoid);
                throw e;
            }
        });
    }

    private void transitionLocked(SessionState target) {
        lock.lock();
        try {
            stateMachine.transitionTo(target);
        } finally {
            lock.unlock();
        }
    }

    // ─── Display ───

    /**
     * Refreshes the customer display from the given basket snapshot using the
     * configured {@link DisplayRenderer}.
     *
     * <p>Display is best-effort: failures are logged and never interrupt the
     * checkout.</p>
     */
    public void updateDisplay(Basket basket) {
        Objects.requireNonNull(basket, "basket");
        showBasket(basket);
    }

    private void showBasket(Basket basket) {
        DisplayContext context = new DisplayContext(stateMachine.current(),
                router.hasExternalDisplay() ? DisplayTarget.EXTERNAL : DisplayTarget.TERMINAL,
                currency);
        DisplayPayload payload;
        try {
            payload = displayRenderer.render(basket, context);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "display renderer failed", e);
            return;
        }
        if (payload != null) {
            updateDisplay(payload);
        }
    }

    /**
     * Sends a custom display payload to the customer display (or the external
     * display when configured).
     *
     * <p>Display is best-effort: failures are logged and never interrupt the
     * checkout.</p>
     */
    public void updateDisplay(DisplayPayload payload) {
        Objects.requireNonNull(payload, "payload");
        String base64;
        try {
            base64 = DisplayPayloadHelper.toBase64(payload);
        } catch (JAXBException e) {
            LOGGER.log(Level.WARNING, "failed to serialize display payload", e);
            return;
        }
        sendDisplay(base64);
    }

    private void sendDisplay(String base64Xhtml) {
        try {
            exchange.send(MessageCategoryType.DISPLAY, factory.displayRequest(base64Xhtml));
        } catch (SessionException e) {
            LOGGER.log(Level.WARNING, "display update failed: " + e.getError(), e);
        }
    }

    // ─── Abort ───

    /**
     * Aborts the current operation and the session.
     *
     * <p>If a terminal operation is awaiting a response, a Nexo
     * {@code AbortRequest} referencing it is sent (best-effort) to the device
     * that is processing it. The session then moves to
     * {@link SessionState#ABORTED}; aborting a session already in a terminal
     * state has no effect.</p>
     *
     * <p>Safe to call from any thread.</p>
     */
    public void abort() {
        lock.lock();
        try {
            // the flag is set under the lock so it cannot race the reset a
            // starting payment performs when it enters PAYING: either this
            // abort ends the session before the payment starts, or the
            // payment starts with a fresh flag and observes a later abort
            abortRequested = true;
            SessionState state = stateMachine.current();
            if (state != SessionState.PAYING && !state.isTerminal()) {
                // during PAYING the payment thread observes the flag between
                // steps, unwinds what was committed, and settles the final
                // state — transitioning here would race that decision
                if (stateMachine.canTransitionTo(SessionState.ABORTED)) {
                    stateMachine.transitionTo(SessionState.ABORTED);
                } else {
                    LOGGER.warning("abort() called in state " + state + "; state unchanged");
                }
            }
        } finally {
            lock.unlock();
        }
        NexoExchange.InFlight inFlight = exchange.currentInFlight();
        if (inFlight != null) {
            sendAbort(inFlight);
        }
    }

    private void sendAbort(NexoExchange.InFlight inFlight) {
        SaleToPOIRequest request = SaleToPOIRequest.builder()
                .messageHeader(factory.header(MessageClassType.SERVICE, MessageCategoryType.ABORT))
                .abortRequest(AbortRequest.builder()
                        .abortReason("MerchantAbort")
                        .messageReference(MessageReference.builder()
                                .messageCategory(inFlight.getCategory())
                                .serviceID(inFlight.getServiceId())
                                .saleID(factory.getSaleId())
                                .build())
                        .build())
                .build();
        try {
            inFlight.getClient().request(factory.envelope(request));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "abort request failed", e);
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
        return operation("playSound", () -> sendSound(SoundActionEnum.START_SOUND,
                SoundContent.builder()
                        .soundFormat(SoundFormatEnum.SOUND_REF)
                        .referenceID(soundReferenceId)
                        .build(),
                volumePercent));
    }

    /** Stops any sound currently playing on the terminal. */
    public SessionResult<Void> stopSound() {
        return operation("stopSound", () -> sendSound(SoundActionEnum.STOP_SOUND, null, null));
    }

    private Void sendSound(SoundActionEnum action, SoundContent content, Integer volumePercent) {
        SoundRequest.Builder soundRequest = SoundRequest.builder()
                .soundAction(action)
                .responseMode(ResponseModeEnum.IMMEDIATE);
        if (content != null) {
            soundRequest.soundContent(content);
        }
        if (volumePercent != null) {
            soundRequest.soundVolume(volumePercent.longValue());
        }
        SaleToPOIRequest request = SaleToPOIRequest.builder()
                .messageHeader(factory.header(MessageClassType.DEVICE, MessageCategoryType.SOUND))
                .soundRequest(soundRequest.build())
                .build();
        SaleToPOIResponse response = exchange.send(MessageCategoryType.SOUND, request);
        if (response != null && response.getSoundResponse() != null) {
            exchange.requireSuccess(MessageCategoryType.SOUND,
                    response.getSoundResponse().getResponse());
        }
        return null;
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
        return operation("updateInputDisplay", () -> {
            NexoExchange.InFlight inFlight = exchange.currentInFlight();
            if (inFlight == null || inFlight.getCategory() != MessageCategoryType.INPUT) {
                throw new SessionException(new SessionError(SessionErrorCode.INVALID_STATE,
                        "updateInputDisplay requires an input request awaiting a response"));
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
        });
    }

    // ─── Diagnostics & Admin ───

    /**
     * Queries the terminal's running totals since the last reconciliation
     * (Nexo {@code GetTotals}) — lighter than {@link #reconcile()}, which
     * closes the period.
     */
    public SessionResult<ReconciliationResult> getTotals() {
        return operation("getTotals", () -> {
            SaleToPOIRequest request = SaleToPOIRequest.builder()
                    .messageHeader(factory.header(MessageClassType.SERVICE,
                            MessageCategoryType.GET_TOTALS))
                    .getTotalsRequest(GetTotalsRequest.builder()
                            .totalFilter(TotalFilter.builder()
                                    .saleID(factory.getSaleId())
                                    .totalsGroupID(storeLocation)
                                    .build())
                            .build())
                    .build();
            SaleToPOIResponse response = exchange.sendExpectingBody(
                    MessageCategoryType.GET_TOTALS, request);
            GetTotalsResponse body = response.getGetTotalsResponse();
            if (body == null) {
                throw Wire.missing("GetTotalsResponse");
            }
            exchange.requireSuccess(MessageCategoryType.GET_TOTALS, body.getResponse());
            return new ReconciliationResult(body.getPoiReconciliationID(),
                    body.getTransactionTotals() == null
                            ? null : Arrays.asList(body.getTransactionTotals()));
        });
    }

    /** Queries terminal health and host reachability. */
    public SessionResult<DiagnosisResult> diagnose() {
        return operation("diagnose", () -> {
            SaleToPOIRequest request = SaleToPOIRequest.builder()
                    .messageHeader(factory.header(MessageClassType.SERVICE,
                            MessageCategoryType.DIAGNOSIS))
                    .diagnosisRequest(DiagnosisRequest.builder().build())
                    .build();
            SaleToPOIResponse response = exchange.sendExpectingBody(
                    MessageCategoryType.DIAGNOSIS, request);
            DiagnosisResponse body = response.getDiagnosisResponse();
            if (body == null) {
                throw Wire.missing("DiagnosisResponse");
            }
            exchange.requireSuccess(MessageCategoryType.DIAGNOSIS, body.getResponse());
            return new DiagnosisResult(body.getPoiStatus(),
                    body.getHostStatus() == null ? null : Arrays.asList(body.getHostStatus()));
        });
    }

    /** Runs a sale reconciliation (end-of-period totals). */
    public SessionResult<ReconciliationResult> reconcile() {
        return operation("reconcile", () -> {
            SaleToPOIRequest request = SaleToPOIRequest.builder()
                    .messageHeader(factory.header(MessageClassType.SERVICE,
                            MessageCategoryType.RECONCILIATION))
                    .reconciliationRequest(ReconciliationRequest.builder()
                            .reconciliationType(ReconciliationTypeEnum.SALE_RECONCILIATION)
                            .build())
                    .build();
            SaleToPOIResponse response = exchange.sendExpectingBody(
                    MessageCategoryType.RECONCILIATION, request);
            ReconciliationResponse body = response.getReconciliationResponse();
            if (body == null) {
                throw Wire.missing("ReconciliationResponse");
            }
            exchange.requireSuccess(MessageCategoryType.RECONCILIATION, body.getResponse());
            return new ReconciliationResult(body.getPoiReconciliationID(),
                    body.getTransactionTotals() == null
                            ? null : Arrays.asList(body.getTransactionTotals()));
        });
    }

    /** Prints a document on the terminal printer. */
    public SessionResult<Void> print(PrintPayload payload) {
        Objects.requireNonNull(payload, "payload");
        return operation("print", () -> {
            OutputContent.Builder content = OutputContent.builder();
            if (payload.getFormat() == PrintPayload.Format.TEXT) {
                content.outputFormat(OutputFormatEnum.TEXT)
                        .outputText(new OutputText[] {OutputText.builder()
                                .text(payload.getContent())
                                .build()});
            } else {
                content.outputFormat(OutputFormatEnum.XHTML)
                        .outputXHTML(payload.getContent());
            }
            SaleToPOIRequest request = SaleToPOIRequest.builder()
                    .messageHeader(factory.header(MessageClassType.DEVICE,
                            MessageCategoryType.PRINT))
                    .printRequest(PrintRequest.builder()
                            .printOutput(PrintOutput.builder()
                                    .documentQualifier(payload.getDocumentQualifier())
                                    .responseMode(ResponseModeEnum.PRINT_END)
                                    .outputContent(content.build())
                                    .build())
                            .build())
                    .build();
            SaleToPOIResponse response = exchange.sendExpectingBody(
                    MessageCategoryType.PRINT, request);
            PrintResponse body = response.getPrintResponse();
            if (body == null) {
                throw Wire.missing("PrintResponse");
            }
            exchange.requireSuccess(MessageCategoryType.PRINT, body.getResponse());
            return null;
        });
    }

    // ─── Escape hatch ───

    /** The underlying terminal client, for raw Nexo access. */
    public BiltNexoTerminalClient getClient() {
        return client;
    }

    // ─── Internals ───

    /**
     * Creates a lazy {@link SessionResult}, warning when a previously created
     * operation was never executed (a forgotten {@code .execute()} is the
     * most common integration mistake with this API).
     */
    private <T> SessionResult<T> operation(String name, Supplier<T> body) {
        trackUnexecuted(name);
        return new SessionResult<>(name, () -> {
            unexecutedOperation.compareAndSet(name, null);
            return body.get();
        });
    }

    /** Warns when a previously created operation was never executed. */
    private void trackUnexecuted(String name) {
        String pending = unexecutedOperation.getAndSet(name);
        if (pending != null) {
            LOGGER.warning("session operation '" + pending + "' was created but never executed; "
                    + "did you forget to call execute(), get(), or getOrNull()?");
        }
    }

    /** Builder for {@link CheckoutSession}. */
    public static final class Builder {

        private BiltNexoTerminalClient client;
        private String saleId;
        private String poiId;
        private String currency;
        private String storeLocation;
        private String poiTransactionId;
        private Instant poiTransactionTimestamp;
        private String awardPoiTransactionId;
        private Instant awardPoiTransactionTimestamp;
        private String memberId;
        private boolean autoDisplay = true;
        private BiltNexoTerminalClient externalDisplayClient;
        private DisplayRenderer displayRenderer;
        private Consumer<Basket> onBasketUpdated;

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
         * Reference to a prior transaction, required for linked refunds and
         * {@code voidTransaction()}.
         */
        public Builder poiTransactionId(String poiTransactionId) {
            this.poiTransactionId = poiTransactionId;
            return this;
        }

        /** Timestamp of the prior transaction referenced by {@link #poiTransactionId}. */
        public Builder poiTransactionTimestamp(Instant poiTransactionTimestamp) {
            this.poiTransactionTimestamp = poiTransactionTimestamp;
            return this;
        }

        /**
         * Terminal reference of the prior transaction's loyalty award (from
         * {@code CheckoutResult.getAwardPoiTransactionId()}). Lets linked
         * refunds and voids on this session reverse the award by its own
         * reference, per the reverse-award contract; without it the payment
         * reference is sent and the terminal resolves the award.
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
         * {@code IdentifyResult.getMemberId()} or
         * {@code CheckoutResult}/POS records). The reverse-award contract
         * requires it in {@code LoyaltyData}, so supply it for linked
         * refunds and voids on builder-referenced sessions — without it the
         * loyalty reversal is attempted without member identification.
         */
        public Builder memberId(String memberId) {
            this.memberId = memberId;
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

        public CheckoutSession build() {
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
            return new CheckoutSession(this);
        }
    }
}
