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
import com.bilt.pos.session.basket.BasketLineItem;
import com.bilt.pos.session.display.DisplayRenderer;
import com.bilt.pos.session.identity.CardAcquisitionOptions;
import com.bilt.pos.session.identity.CardAcquisitionResult;
import com.bilt.pos.session.identity.IdentifyOptions;
import com.bilt.pos.session.identity.IdentifyResult;
import com.bilt.pos.session.identity.IdentifyStatus;
import com.bilt.pos.session.identity.Member;
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
import com.bilt.pos.session.internal.DisplayRouter;
import com.bilt.pos.session.internal.IdentityManager;
import com.bilt.pos.session.internal.InputManager;
import com.bilt.pos.session.internal.NexoExchange;
import com.bilt.pos.session.internal.NexoMessageFactory;
import com.bilt.pos.session.internal.PaymentOrchestrator;
import com.bilt.pos.session.internal.PoiRef;
import com.bilt.pos.session.internal.ReversalManager;
import com.bilt.pos.session.internal.ReversalMovement;
import com.bilt.pos.session.internal.SaleItemMapper;
import com.bilt.pos.session.internal.SessionSignalCodec;
import com.bilt.pos.session.internal.StoredValueManager;
import com.bilt.pos.session.internal.Wire;
import com.bilt.pos.session.settlement.CommittedStep;
import com.bilt.pos.session.settlement.OriginalSaleRecord;
import com.bilt.pos.session.settlement.RefundAllocation;
import com.bilt.pos.session.settlement.RefundAllocationType;
import com.bilt.pos.session.settlement.SettlementContext;
import com.bilt.pos.session.settlement.SettlementMovement;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.bilt.pos.session.settlement.SettlementResult;
import com.bilt.pos.session.settlement.SettlementStep;
import com.bilt.pos.session.settlement.SettlementType;
import com.bilt.pos.session.settlement.StoredValueLoad;
import com.bilt.pos.session.storedvalue.StoredValueBalance;
import com.bilt.pos.session.storedvalue.StoredValueCard;
import com.bilt.pos.session.storedvalue.StoredValueOperationResult;
import jakarta.xml.bind.JAXBException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * The Nexo-backed {@link TerminalShopperSession}: the terminal-facing half of a shopper session on
 * top of the basket and member state {@link AbstractShopperSession} provides. Every operation maps
 * to Sale to POI 3.0 messages exchanged through the configured {@link BiltNexoTerminalClient}.
 */
final class NexoTerminalShopperSession extends AbstractShopperSession
    implements TerminalShopperSession {

  private static final Logger LOGGER = Logger.getLogger(NexoTerminalShopperSession.class.getName());
  private static final OriginalSaleRecord NO_SETTLEMENT = OriginalSaleRecord.builder().build();

  /** Mutually exclusive runtime activity; recovery and basket state live separately. */
  private enum SessionPhase {
    OPEN,
    SETTLING,
    VOIDING,
    ENDING,
    ENDED
  }

  private final BiltNexoTerminalClient client;
  private final NexoMessageFactory factory;
  private final NexoExchange exchange;
  private final DisplayRouter router;
  private final BasketDisplay display;
  private final DisplayRenderer displayRenderer;
  private final Consumer<Basket> onBasketUpdated;
  private final boolean autoDisplay;

  private final IdentityManager identityManager;
  private final SessionMember memberState;
  private final InputManager inputManager;
  private final ReversalManager reversalManager;
  private final PaymentOrchestrator paymentOrchestrator;
  private final StoredValueManager storedValueManager;

  private volatile boolean abortRequested;
  private volatile SessionPhase phase = SessionPhase.OPEN;
  private volatile boolean basketConsumed;
  private volatile StoredValueCard storedValueCard;
  // Compact reversal/refund metadata for the latest settlement. Keeping
  // the public result itself would retain its basket, receipts, and ledger.
  private volatile OriginalSaleRecord lastSettlementRecord = NO_SETTLEMENT;
  private volatile boolean lastSettlementIncludesRefunds;
  // Raised only when a same-session void reverses at least one movement
  // before failing. Replacing or ending the session would discard the
  // in-memory progress that makes the next void retry idempotent.
  private volatile boolean lastPaymentVoidIncomplete;
  // Refund/void mutual exclusion and void-resume progress for the most
  // recent successful settlement (see ReversalGuards). A later settlement
  // replaces this target and resets the guard.
  private final ReversalGuards guards = new ReversalGuards("payment");
  // A failed settlement may leave movements standing when its rollback
  // itself fails. The concrete recovery state is retained directly rather
  // than encoded in a broad session lifecycle state.
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
  // Prior-sale voids do not use this session's latest settlement record or
  // guards, but they still need resume state when one movement reversed and
  // a later one failed. The target record prevents applying that progress
  // to a different prior sale.
  private volatile OriginalSaleRecord priorSaleVoidTarget;
  private volatile Set<ReversalMovement.Key> priorSaleVoidReversedMovements =
      ConcurrentHashMap.newKeySet();

  // the session's Terminal facade, created lazily by terminal(); it has
  // its own executor and exchange, so the session's lifecycle never
  // constrains it (and vice versa)
  private volatile Terminal terminal;

  NexoTerminalShopperSession(TerminalShopperSession.Builder builder) {
    super(
        builder.saleId,
        builder.currency,
        builder.storeLocation,
        builder.callbackExecutor,
        builder.onBackgroundError,
        builder.poiId,
        builder.phase,
        builder.attributes,
        builder.widgets,
        builder.credentials,
        builder.environment);
    this.client = builder.client;
    this.autoDisplay = builder.autoDisplay;
    this.displayRenderer =
        builder.displayRenderer != null ? builder.displayRenderer : new BasketDisplayRenderer();
    this.onBasketUpdated = builder.onBasketUpdated;
    this.factory = new NexoMessageFactory(builder.saleId, builder.poiId, builder.storeLocation);
    this.router = new DisplayRouter(builder.client, builder.externalDisplayClient);
    this.exchange = new NexoExchange(router, factory);
    this.identityManager = new IdentityManager(exchange);
    this.memberState =
        new SessionMember(
            lock,
            operations,
            this::resolveOnTerminal,
            this::ended,
            builder.onMemberChanged,
            this::memberChanged,
            builder.member);
    this.inputManager = new InputManager(exchange);
    this.storedValueManager = new StoredValueManager(exchange, builder.currency);
    this.reversalManager = new ReversalManager(exchange, builder.currency, storedValueManager);
    this.paymentOrchestrator =
        new PaymentOrchestrator(exchange, builder.currency, storedValueManager);
    this.display = new BasketDisplay(exchange, displayRenderer, builder.currency);
    if (autoDisplay) {
      // first in line, so the customer display never waits behind a widget
      observers.addFirst(new AutoDisplayPush(display, this::basketDisplayIsCurrent));
    }
  }

  @Override
  public String getPoiId() {
    return factory.getPoiId();
  }

  // ─── Basket ───

  @Override
  void requireBasketMutable() {
    if (closingOrEnded()) {
      throw new IllegalStateException("the basket cannot be modified after end()");
    }
    if (moneyMovementInFlight()) {
      throw new IllegalStateException(
          "the basket cannot be modified while money movement is in flight");
    }
    if (basketConsumed) {
      throw new IllegalStateException(
          "the basket has already settled; call clear() " + "before starting another basket");
    }
    if (hasCommittedRefundAllocations()) {
      throw new IllegalStateException(
          "the basket cannot be modified after "
              + "refund allocations have committed; retry settle() with the "
              + "same refund allocations");
    }
  }

  @Override
  void requireBasketClearable() {
    if (closingOrEnded()) {
      throw new IllegalStateException("the basket cannot be cleared after end()");
    }
    if (moneyMovementInFlight()) {
      throw new IllegalStateException(
          "the basket cannot be cleared while money movement is in flight");
    }
    if (rollbackIncomplete()) {
      throw new IllegalStateException(
          "the basket cannot be cleared while a failed "
              + "settlement rollback is incomplete; finish the unwind first");
    }
    if (lastPaymentVoidIncomplete) {
      throw new IllegalStateException(
          "the basket cannot be cleared while a void "
              + "of the most recent payment is partially complete; retry "
              + "voidTransaction() first");
    }
    if (hasCommittedRefundAllocations()) {
      throw new IllegalStateException(
          "the basket cannot be cleared after refund "
              + "allocations have committed; retry settle() with the same allocations");
    }
  }

  @Override
  boolean basketConsumed() {
    return basketConsumed;
  }

  /**
   * A cleared basket also drops the stored-value tender selected for the previous one and returns
   * the checkout to {@link CheckoutPhase#SCANNING} for the next transaction.
   */
  @Override
  void basketCleared() {
    basketConsumed = false;
    storedValueCard = null;
    context().phase(CheckoutPhase.SCANNING);
  }

  private boolean basketDisplayIsCurrent() {
    return phase == SessionPhase.OPEN && !basketConsumed;
  }

  // ─── Member Identification ───

  @Override
  public SessionResult<IdentifyResult> identifyMember() {
    return identifyMember(IdentifyOptions.defaults());
  }

  @Override
  public SessionResult<IdentifyResult> identifyMember(IdentifyOptions options) {
    Objects.requireNonNull(options, "options");
    return operation(
        "identifyMember",
        () ->
            completeIdentify(
                identifyStateChecked(() -> identityManager.identifyPrompted(options))));
  }

  @Override
  public SessionResult<IdentifyResult> identifyMember(Member pending) {
    Objects.requireNonNull(pending, "pending");
    if (pending.isResolved()) {
      throw new IllegalArgumentException(
          "identifyMember(Member) looks up a member pending resolution "
              + "(Member.idResolver()...); a resolved member attaches through member(Member)");
    }
    return operation(
        "identifyMember",
        () ->
            completeIdentify(
                identifyStateChecked(
                    () -> identityManager.identifyByIdentifier(pending.resolver()))));
  }

  @Override
  SessionMember memberState() {
    return memberState;
  }

  /**
   * The {@link MemberResolver} behind {@link #member(Member)}: the same terminal lookup as {@link
   * #identifyMember(Member)}, mapped to the resolver contract — found attaches, not found and
   * suspended clear, a cancelled (aborted) lookup learns nothing and leaves the member pending.
   */
  private Member resolveOnTerminal(Member pending) {
    requireOpen("member");
    IdentifyResult result = identityManager.identifyByIdentifier(pending.resolver());
    if (result.getStatus() == IdentifyStatus.FOUND) {
      return Member.resolved(result);
    }
    return result.getStatus() == IdentifyStatus.CANCELLED ? pending : null;
  }

  private IdentifyResult identifyStateChecked(Supplier<IdentifyResult> lookup) {
    requireOpen("identifyMember");
    return lookup.get();
  }

  /**
   * Applies an identification outcome to the session unless the session ended while the lookup was
   * on the wire, in which case the outcome is discarded like any other late prompt result. The
   * member change, if any, is announced once the lock is released.
   */
  private IdentifyResult completeIdentify(IdentifyResult result) {
    boolean changed;
    Member now;
    lock.lock();
    try {
      if (phase == SessionPhase.ENDED) {
        throw discardedAfterEnd("identifyMember");
      }
      changed = memberState.applyIdentification(result);
      now = memberState.current();
    } finally {
      lock.unlock();
    }
    if (changed) {
      memberState.fireChanged(now);
    }
    return result;
  }

  // ─── Card acquisition ───

  @Override
  public SessionResult<CardAcquisitionResult> acquireCard() {
    return acquireCard(CardAcquisitionOptions.defaults());
  }

  @Override
  public SessionResult<CardAcquisitionResult> acquireCard(CardAcquisitionOptions options) {
    Objects.requireNonNull(options, "options");
    return operation(
        "acquireCard",
        () -> {
          requireOpen("acquireCard");
          CardAcquisitionResult acquired = identityManager.acquireCard(options);
          discardIfEndedMidFlight("acquireCard");
          return acquired;
        });
  }

  // ─── Input (nexo native) ───

  @Override
  public SessionResult<String> requestDigitString(String prompt) {
    return requestDigitString(prompt, InputOptions.defaults());
  }

  @Override
  public SessionResult<String> requestDigitString(String prompt, InputOptions options) {
    Objects.requireNonNull(prompt, "prompt");
    Objects.requireNonNull(options, "options");
    return inputOperation("requestDigitString", () -> inputManager.digitString(prompt, options));
  }

  @Override
  public SessionResult<BigDecimal> requestDecimalString(String prompt) {
    return requestDecimalString(prompt, InputOptions.defaults());
  }

  @Override
  public SessionResult<BigDecimal> requestDecimalString(String prompt, InputOptions options) {
    Objects.requireNonNull(prompt, "prompt");
    Objects.requireNonNull(options, "options");
    return inputOperation(
        "requestDecimalString", () -> inputManager.decimalString(prompt, options));
  }

  @Override
  public SessionResult<String> requestTextString(String prompt) {
    return requestTextString(prompt, InputOptions.defaults());
  }

  @Override
  public SessionResult<String> requestTextString(String prompt, InputOptions options) {
    Objects.requireNonNull(prompt, "prompt");
    Objects.requireNonNull(options, "options");
    return inputOperation("requestTextString", () -> inputManager.textString(prompt, options));
  }

  @Override
  public SessionResult<Boolean> requestConfirmation(String prompt) {
    return requestConfirmation(prompt, ConfirmationOptions.defaults());
  }

  @Override
  public SessionResult<Boolean> requestConfirmation(String prompt, ConfirmationOptions options) {
    Objects.requireNonNull(prompt, "prompt");
    Objects.requireNonNull(options, "options");
    return inputOperation("requestConfirmation", () -> inputManager.confirmation(prompt, options));
  }

  @Override
  public SessionResult<MenuSelection> requestMenuEntry(String prompt, List<String> entries) {
    return requestMenuEntry(prompt, entries, MenuOptions.defaults());
  }

  @Override
  public SessionResult<MenuSelection> requestMenuEntry(
      String prompt, List<String> entries, MenuOptions options) {
    Objects.requireNonNull(prompt, "prompt");
    Objects.requireNonNull(options, "options");
    if (entries == null || entries.isEmpty()) {
      throw new IllegalArgumentException("entries must not be empty");
    }
    List<String> entriesCopy = List.copyOf(entries);
    return inputOperation(
        "requestMenuEntry", () -> inputManager.menuEntry(prompt, entriesCopy, options));
  }

  // ─── Input (XSD-based) ───

  @Override
  public SessionResult<Signature> requestSignature(String prompt) {
    Objects.requireNonNull(prompt, "prompt");
    return inputOperation("requestSignature", () -> inputManager.signature(prompt));
  }

  @Override
  public SessionResult<Boolean> requestAmountConfirmation(BigDecimal amount, String prompt) {
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(prompt, "prompt");
    return inputOperation(
        "requestAmountConfirmation",
        () -> inputManager.amountConfirmation(amount, prompt, getCurrency()));
  }

  // ─── PIN ───

  @Override
  public SessionResult<PinResult> requestPinEntry(PinOptions options) {
    Objects.requireNonNull(options, "options");
    return inputOperation("requestPinEntry", () -> inputManager.pin(PinMode.PIN_ENTER, options));
  }

  @Override
  public SessionResult<PinResult> requestPinVerify(PinOptions options) {
    Objects.requireNonNull(options, "options");
    return inputOperation("requestPinVerify", () -> inputManager.pin(PinMode.PIN_VERIFY, options));
  }

  @Override
  public SessionResult<PinResult> requestPinVerifyOnly(PinOptions options) {
    Objects.requireNonNull(options, "options");
    return inputOperation(
        "requestPinVerifyOnly", () -> inputManager.pin(PinMode.PIN_VERIFY_ONLY, options));
  }

  private <T> SessionResult<T> inputOperation(String name, Supplier<T> body) {
    return operation(
        name,
        () -> {
          requireOpen(name);
          T value = body.get();
          discardIfEndedMidFlight(name);
          return value;
        });
  }

  /**
   * Post-completion guard for read-only prompts: {@code abort()} — safe from any thread — can end
   * the session while the request is on the wire. An outcome arriving after that is discarded
   * rather than delivered: the register aborted, so stale customer input, PIN, or card data must
   * not reach {@code onSuccess}.
   */
  private void discardIfEndedMidFlight(String operationName) {
    lock.lock();
    try {
      if (phase == SessionPhase.ENDED) {
        throw discardedAfterEnd(operationName);
      }
    } finally {
      lock.unlock();
    }
  }

  /** The discard error for an outcome that arrived after the session moved on. */
  private static SessionException discardedAfterEnd(String operationName) {
    return new SessionException(
        new SessionError(
            SessionErrorCode.INVALID_STATE,
            operationName + " completed after the session ended; the result was discarded"));
  }

  private void requireOpen(String operationName) {
    if (closingOrEnded()) {
      throw invalidState(operationName + " is not allowed after end(); create a new session");
    }
  }

  private boolean closingOrEnded() {
    return phase == SessionPhase.ENDING || phase == SessionPhase.ENDED;
  }

  private boolean moneyMovementInFlight() {
    return phase == SessionPhase.SETTLING || phase == SessionPhase.VOIDING;
  }

  // ─── Stored Value ───

  @Override
  public void setStoredValueCard(String cardNumber) {
    setStoredValueTender(cardNumber == null ? null : StoredValueCard.number(cardNumber));
  }

  @Override
  public void setStoredValueCard(StoredValueCard card) {
    setStoredValueTender(card);
  }

  private void setStoredValueTender(StoredValueCard card) {
    lock.lock();
    try {
      if (closingOrEnded()) {
        throw new IllegalStateException("the stored-value tender cannot be changed after end()");
      }
      if (moneyMovementInFlight()) {
        throw new IllegalStateException(
            "the stored-value tender cannot be changed " + "while money movement is in flight");
      }
      if (basketConsumed) {
        throw new IllegalStateException(
            "the basket has already settled; call "
                + "basket().clear() before selecting another tender");
      }
      this.storedValueCard = card;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public SessionResult<StoredValueBalance> storedValueBalance(StoredValueCard card) {
    Objects.requireNonNull(card, "card");
    return operation(
        "storedValueBalance",
        () -> {
          requireOpen("storedValueBalance");
          return storedValueManager.balance(card);
        });
  }

  @Override
  public SessionResult<StoredValueOperationResult> storedValueActivate(
      StoredValueCard card, BigDecimal initialAmount) {
    Objects.requireNonNull(initialAmount, "initialAmount");
    requireNonNegative(initialAmount);
    return storedValueOperation(
        "storedValueActivate", StoredValueTransactionTypeEnum.ACTIVATE, card, initialAmount);
  }

  @Override
  public SessionResult<StoredValueOperationResult> storedValueLoad(
      StoredValueCard card, BigDecimal amount) {
    requirePositiveAmount(amount);
    return storedValueOperation(
        "storedValueLoad", StoredValueTransactionTypeEnum.LOAD, card, amount);
  }

  @Override
  public SessionResult<StoredValueOperationResult> storedValueUnload(
      StoredValueCard card, BigDecimal amount) {
    requirePositiveAmount(amount);
    return storedValueOperation(
        "storedValueUnload", StoredValueTransactionTypeEnum.UNLOAD, card, amount);
  }

  @Override
  public SessionResult<StoredValueOperationResult> storedValueDeactivate(StoredValueCard card) {
    return storedValueOperation(
        "storedValueDeactivate", StoredValueTransactionTypeEnum.UNLOAD, card, BigDecimal.ZERO);
  }

  @Override
  public SessionResult<StoredValueOperationResult> storedValueReserve(
      StoredValueCard card, BigDecimal amount) {
    requirePositiveAmount(amount);
    return storedValueOperation(
        "storedValueReserve", StoredValueTransactionTypeEnum.RESERVE, card, amount);
  }

  @Override
  public SessionResult<StoredValueOperationResult> storedValueReverse(
      String originalPoiTransactionId, Instant originalPoiTransactionTimestamp) {
    Objects.requireNonNull(originalPoiTransactionId, "originalPoiTransactionId");
    return operation(
        "storedValueReverse",
        () -> {
          requireOpen("storedValueReverse");
          return storedValueManager.operation(
              StoredValueTransactionTypeEnum.REVERSE,
              null,
              null,
              originalPoiTransactionId,
              originalPoiTransactionTimestamp);
        });
  }

  @Override
  public SessionResult<StoredValueOperationResult> storedValueDuplicate(StoredValueCard card) {
    return storedValueOperation(
        "storedValueDuplicate", StoredValueTransactionTypeEnum.DUPLICATE, card, null);
  }

  private SessionResult<StoredValueOperationResult> storedValueOperation(
      String name, StoredValueTransactionTypeEnum type, StoredValueCard card, BigDecimal amount) {
    Objects.requireNonNull(card, "card");
    return operation(
        name,
        () -> {
          requireOpen(name);
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

  @Override
  public SettlementFlow settle() {
    return settle(SettlementOptions.defaults());
  }

  @Override
  public SettlementFlow settle(SettlementOptions options) {
    Objects.requireNonNull(options, "options");
    operations.track("settle");
    return new SettlementFlow(flow -> executeSettlement(flow, options)).session(operations);
  }

  private SettlementResult executeSettlement(SettlementFlow flow, SettlementOptions options) {
    operations.begin("settle");
    PaymentOrchestrator.Request request = new PaymentOrchestrator.Request();
    Basket fullBasket;
    Basket chargePortion;
    BigDecimal returnTotal;
    BigDecimal refundAmount;
    boolean netSettlement;
    // A failed or aborted settlement hands the checkout back to the
    // phase it was in when settle() began, whatever the POS had set.
    CheckoutPhase resumePhase = context().phase();
    boolean tendering = false;
    boolean settled = false;
    lock.lock();
    try {
      requireOpen("settle");
      if (moneyMovementInFlight()) {
        throw invalidState("settle() is not allowed while money movement is in flight");
      }
      if (lastPaymentVoidIncomplete) {
        throw invalidState(
            "a void of the most recent payment is partially complete; "
                + "retry voidTransaction() before starting another settlement");
      }
      if (basketConsumed) {
        throw invalidState(
            "the basket has already settled; call basket().clear() "
                + "before starting another settlement");
      }
      if (basketEngine().isEmpty()) {
        throw invalidState("the basket is empty; settlement cannot start");
      }
      fullBasket = basketEngine().snapshot();
      chargePortion = fullBasket.chargePortion();
      returnTotal = fullBasket.returnTotal();
      netSettlement = options.getSettlementType() == SettlementType.NET;
      refundAmount = fullBasket.getRefundAmount(options.getSettlementType());
      validateSettlementOptions(fullBasket, chargePortion, returnTotal, refundAmount, options);
      if (chargePortion.isEmpty() && returnTotal.signum() == 0) {
        throw invalidState("settlement requires a sale, return, or credit line");
      }
      Basket chargeBasket = netSettlement ? fullBasket : chargePortion;
      if ((chargeBasket.isEmpty() || chargeBasket.getGrandTotal().signum() <= 0)
          && options.getCashback() != null) {
        throw invalidState("cashback requires a card charge in the settlement");
      }
      phase = SessionPhase.SETTLING;
      context().phase(CheckoutPhase.TENDERING);
      tendering = true;
      // the abort flag is scoped to a single settlement run: a stale
      // abort left over from an earlier operation must
      // not kill a legitimate retry at its first checkAbort
      abortRequested = false;
    } finally {
      lock.unlock();
    }
    request.member = identifiedMember();
    request.storedValueCard = storedValueCard;
    request.options = options;
    request.basket = netSettlement ? fullBasket : chargePortion;
    request.fullBasket = fullBasket;
    request.abortRequested = () -> abortRequested;
    // movements an incomplete unwind left standing are kept so that
    // voidTransaction() on the failed session can finish the reversal
    request.onUnreversed = movements -> standingMovements = List.copyOf(movements);
    request.onAbandoned =
        record -> {
          lock.lock();
          try {
            standingMovements = List.of();
            clearCommittedRefundAllocations();
          } finally {
            lock.unlock();
          }
        };
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
          throw new SessionException(
              Wire.annotated(
                  e.getError(),
                  "the previous payment's rollback is still incomplete; the "
                      + "retry did not start: "
                      + e.getError().getMessage(),
                  e));
        }
      }
      List<SettlementMovement> refundMovements =
          new ArrayList<>(committedRefundMovementsSnapshot());
      List<CommittedStep> committedRefundSteps = committedRefundSteps(refundMovements);
      int committedRefundCount = committedRefundAllocationsCount();
      List<RefundAllocation> refunds = options.getRefunds();
      validateCommittedRefundRetry(refunds);
      List<RefundAllocation> pendingAllocations =
          refunds.subList(committedRefundCount, refunds.size());
      refundMovements.addAll(
          executeRefundAllocations(
              flow,
              fullBasket,
              pendingAllocations,
              committedRefundSteps,
              hasItemizedRefundAllocation(refunds.subList(0, committedRefundCount)),
              netSettlement && refundAmount.signum() > 0));
      request.priorSteps = committedRefundSteps;
      request.priorMovements = List.copyOf(refundMovements);
      boolean chargeSideWork =
          request.basket.getGrandTotal().signum() > 0 || !options.getFulfillments().isEmpty();
      SettlementResult purchaseResult = chargeSideWork ? paymentOrchestrator.run(request) : null;
      SettlementResult result =
          combineSettlementResult(fullBasket, purchaseResult, refundMovements, netSettlement);
      showFinalSettlement(result.getFinalBasket());
      lock.lock();
      try {
        basketConsumed = true;
        lastSettlementRecord =
            result.toOriginalSaleRecord(
                request.member == null ? null : request.member.getMemberId());
        lastSettlementIncludesRefunds = returnTotal.signum() > 0;
        lastPaymentVoidIncomplete = false;
        clearCommittedRefundAllocations();
        // this settlement replaced the void target, so the guard on
        // the previous one and all of its resume progress lift.
        guards.reset();
        context().phase(CheckoutPhase.COMPLETE);
        settled = true;
      } finally {
        lock.unlock();
      }
      if (abortRequested) {
        LOGGER.warning(
            "abort() arrived after settlement completed; the transaction "
                + "stands — use voidTransaction() to reverse it");
      }
      return result;
    } finally {
      lock.lock();
      try {
        phase = SessionPhase.OPEN;
        if (tendering && !settled) {
          context().phase(resumePhase);
        }
      } finally {
        lock.unlock();
      }
    }
  }

  private List<SettlementMovement> executeRefundAllocations(
      SettlementFlow flow,
      Basket basket,
      List<RefundAllocation> allocations,
      List<CommittedStep> committedSteps,
      boolean refundSaleItemsAlreadySent,
      boolean netRefund) {
    List<SettlementMovement> movements = new ArrayList<>();
    Function<SettlementContext, String> beforeStep = flow.beforeStepHandler();
    Consumer<SettlementMovement> onMovement = flow.movementHandler();
    List<SaleItem> refundSaleItems =
        refundSaleItemsAlreadySent
            ? List.of()
            : netRefund
                ? SaleItemMapper.toNetRefundSaleItems(basket)
                : SaleItemMapper.toRefundSaleItems(basket.returnPortion());
    boolean refundSaleItemsSent = refundSaleItemsAlreadySent;
    for (RefundAllocation allocation : allocations) {
      if (abortRequested) {
        throw new SessionException(
            new SessionError(SessionErrorCode.ABORTED, "the settlement was aborted"));
      }
      SettlementStep step = refundStep(allocation.getType());
      String saleTransactionId =
          SettlementContext.resolveSaleTransactionId(
              step, basket, allocation.getAmount(), committedSteps, beforeStep);
      // Refund allocation failures deliberately bypass the
      // charge-side onError recovery loop: successful refunds cannot
      // be re-charged as compensation, so a failed run is resumed by
      // retrying settle() with the same committed allocation prefix.
      List<SaleItem> saleItems = null;
      if (!refundSaleItemsSent
          && carriesRefundSaleItems(allocation.getType())
          && !refundSaleItems.isEmpty()) {
        // Allocations are tender-level, not line-level. Carry the
        // complete return itemization once, on the first refund
        // PaymentRequest, so split refunds do not duplicate receipt
        // lines across tender legs.
        saleItems = refundSaleItems;
        refundSaleItemsSent = true;
      }
      SettlementMovement movement =
          executeRefundAllocation(allocation, step, saleTransactionId, saleItems);
      movements.add(movement);
      committedSteps.add(
          new CommittedStep(
              step,
              saleTransactionId,
              movement.getPoiTransactionId(),
              movement.getPoiTransactionTimestamp(),
              true));
      recordCommittedRefundAllocation(allocation, movement);
      if (onMovement != null) {
        onMovement.accept(movement);
      }
    }
    return movements;
  }

  private SettlementMovement executeRefundAllocation(
      RefundAllocation allocation,
      SettlementStep step,
      String saleTransactionId,
      List<SaleItem> saleItems) {
    switch (allocation.getType()) {
      case CARD:
      case STORED_VALUE:
        {
          RefundResult card =
              reversalManager.refund(
                  allocation.getAmount(),
                  saleItems,
                  allocation.getOriginalPoiTransactionId(),
                  allocation.getOriginalPoiTransactionTimestamp(),
                  null,
                  null,
                  allocation.getMemberId(),
                  saleTransactionId,
                  null,
                  () -> {},
                  () -> {});
          return refundMovement(
              step,
              allocation,
              saleTransactionId,
              card.getRefundedAmount(),
              card.getPoiTransactionId(),
              card.getPoiTransactionTimestamp(),
              null,
              null);
        }
      case STORE_CREDIT:
        StoredValueOperationResult storeCredit =
            storedValueManager.operation(
                StoredValueTransactionTypeEnum.LOAD,
                allocation.getStoredValueCard(),
                allocation.getAmount(),
                null,
                null,
                saleTransactionId);
        return refundMovement(
            step,
            allocation,
            saleTransactionId,
            storeCredit.getAmount(),
            storeCredit.getPoiTransactionId(),
            storeCredit.getPoiTransactionTimestamp(),
            null,
            null);
      case EXTERNAL:
        return refundMovement(
            step, allocation, saleTransactionId, allocation.getAmount(), null, null, null, null);
      case POINT_REDEMPTION:
        VoidResult points =
            reversalManager.refundLoyalty(
                ReversalStep.REDEMPTION,
                allocation.getOriginalPoiTransactionId(),
                allocation.getOriginalPoiTransactionTimestamp(),
                allocation.getMemberId(),
                saleTransactionId);
        return refundMovement(
            step,
            allocation,
            saleTransactionId,
            points.getReversedAmount(),
            points.getPoiTransactionId(),
            points.getPoiTransactionTimestamp(),
            points.getPointsReversed(),
            points.getRemainingPointBalance());
      case REBATE:
        VoidResult rebate =
            reversalManager.refundLoyalty(
                ReversalStep.REBATE,
                allocation.getOriginalPoiTransactionId(),
                allocation.getOriginalPoiTransactionTimestamp(),
                allocation.getMemberId(),
                saleTransactionId);
        return refundMovement(
            step,
            allocation,
            saleTransactionId,
            rebate.getReversedAmount(),
            rebate.getPoiTransactionId(),
            rebate.getPoiTransactionTimestamp(),
            rebate.getPointsReversed(),
            rebate.getRemainingPointBalance());
      case AWARD:
        VoidResult award =
            reversalManager.refundLoyalty(
                ReversalStep.AWARD,
                allocation.getOriginalPoiTransactionId(),
                allocation.getOriginalPoiTransactionTimestamp(),
                allocation.getMemberId(),
                saleTransactionId);
        return refundMovement(
            step,
            allocation,
            saleTransactionId,
            BigDecimal.ZERO,
            award.getPoiTransactionId(),
            award.getPoiTransactionTimestamp(),
            award.getPointsReversed(),
            award.getRemainingPointBalance());
      default:
        throw new IllegalArgumentException(
            "unsupported refund allocation type " + allocation.getType());
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
    return type == RefundAllocationType.CARD || type == RefundAllocationType.STORED_VALUE;
  }

  private static SettlementMovement refundMovement(
      SettlementStep step,
      RefundAllocation allocation,
      String saleTransactionId,
      BigDecimal actualAmount,
      String poiTransactionId,
      Instant poiTransactionTimestamp,
      Integer points,
      Integer pointBalance) {
    BigDecimal amount = actualAmount != null ? actualAmount : allocation.getAmount();
    return SettlementMovement.builder()
        .step(step)
        .target(allocation.getTarget())
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

  private static void validateSettlementOptions(
      Basket basket,
      Basket chargePortion,
      BigDecimal returnTotal,
      BigDecimal netRefundAmount,
      SettlementOptions options) {
    if (chargePortion.getGrandTotal().signum() < 0) {
      throw invalidState(
          "credit lines exceed the sale-side value; credits cannot " + "create a customer payout");
    }
    validateRefundAllocations(returnTotal, netRefundAmount, options);
    validateFulfillments(basket, options.getFulfillments());
  }

  private static void validateRefundAllocations(
      BigDecimal returnTotal, BigDecimal netRefundAmount, SettlementOptions options) {
    List<RefundAllocation> allocations = options.getRefunds();
    if (returnTotal.signum() == 0 && !allocations.isEmpty()) {
      throw invalidState("refund allocations require at least one return line");
    }
    BigDecimal allocated = monetaryAllocationTotal(allocations);
    if (returnTotal.signum() == 0) {
      return;
    }
    if (options.getSettlementType() == SettlementType.REFUND_THEN_CHARGE
        && allocated.compareTo(returnTotal) != 0) {
      throw invalidState(
          "refund allocations total " + allocated + " but return lines total " + returnTotal);
    }
    if (options.getSettlementType() != SettlementType.NET) {
      return;
    }
    if (allocated.compareTo(netRefundAmount) != 0) {
      throw invalidState(
          "net refund allocations total "
              + allocated
              + " but the net refund amount is "
              + netRefundAmount);
    }
  }

  private static void validateFulfillments(Basket basket, List<StoredValueLoad> fulfillments) {
    HashSet<String> fulfilled = new HashSet<>();
    for (StoredValueLoad fulfillment : fulfillments) {
      String reference = fulfillment.getBasketReference();
      if (!fulfilled.add(reference)) {
        throw invalidState(
            "basket line " + reference + " has more than one stored value fulfillment");
      }
      BasketLineItem line = basket.getItemByReference(reference);
      if (line == null) {
        throw invalidState("stored value fulfillment references missing basket line " + reference);
      }
      if (!line.isSale()) {
        throw invalidState("basket line " + reference + " is not a sale and cannot be fulfilled");
      }
      if (line.getOriginalTotal().signum() <= 0) {
        throw invalidState(
            "basket line " + reference + " must have a positive original total for fulfillment");
      }
    }
  }

  private static BigDecimal monetaryAllocationTotal(List<RefundAllocation> allocations) {
    BigDecimal allocated = BigDecimal.ZERO;
    for (RefundAllocation allocation : allocations) {
      if (allocation.countsTowardRefundTotal()) {
        allocated = allocated.add(allocation.getAmount());
      }
    }
    return allocated;
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
    return invalidState(
        "a previous settlement attempt already committed refund "
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

  private void recordCommittedRefundAllocation(
      RefundAllocation allocation, SettlementMovement movement) {
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

  private static List<CommittedStep> committedRefundSteps(List<SettlementMovement> movements) {
    List<CommittedStep> steps = new ArrayList<>();
    for (SettlementMovement movement : movements) {
      steps.add(
          new CommittedStep(
              movement.getStep(),
              movement.getSaleTransactionId(),
              movement.getPoiTransactionId(),
              movement.getPoiTransactionTimestamp(),
              true));
    }
    return steps;
  }

  private static SettlementResult combineSettlementResult(
      Basket fullBasket,
      SettlementResult purchase,
      List<SettlementMovement> refundMovements,
      boolean netSettlement) {
    List<SettlementMovement> movements = new ArrayList<>(refundMovements);
    if (purchase != null) {
      movements.addAll(purchase.getMovements());
    }
    BigDecimal cardRefunded = sumMovements(refundMovements, SettlementStep.CARD_REFUND);
    BigDecimal storedValueRefunded =
        sumMovements(refundMovements, SettlementStep.STORED_VALUE_REFUND);
    BigDecimal externalRefunded = sumMovements(refundMovements, SettlementStep.EXTERNAL_REFUND);
    BigDecimal loyaltyRefunded =
        sumMovements(refundMovements, SettlementStep.POINT_REDEMPTION_REFUND)
            .add(sumMovements(refundMovements, SettlementStep.REBATE_REFUND));
    Basket finalBasket =
        purchase == null
            ? fullBasket
            : netSettlement
                ? purchase.getFinalBasket()
                : fullBasket.withSettledChargePortion(purchase.getFinalBasket());
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

  private static BigDecimal sumMovements(List<SettlementMovement> movements, SettlementStep step) {
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

  @Override
  public ReversalFlow<RefundResult> refund() {
    operations.track("refund");
    return new ReversalFlow<RefundResult>(flow -> executeRefund(flow, "refund", null, true))
        .session(operations);
  }

  @Override
  public ReversalFlow<RefundResult> refund(BigDecimal amount) {
    Objects.requireNonNull(amount, "amount");
    requirePositive(amount);
    operations.track("refund");
    return new ReversalFlow<RefundResult>(flow -> executeRefund(flow, "refund", amount, true))
        .session(operations);
  }

  @Override
  public ReversalFlow<RefundResult> refundUnlinked(BigDecimal amount) {
    Objects.requireNonNull(amount, "amount");
    requirePositive(amount);
    operations.track("refundUnlinked");
    return new ReversalFlow<RefundResult>(
            flow -> executeRefund(flow, "refundUnlinked", amount, false))
        .session(operations);
  }

  private RefundResult executeRefund(
      ReversalFlow<RefundResult> flow, String name, BigDecimal amount, boolean linked) {
    operations.begin(name);
    requireRefundable(name);
    if (linked) {
      guards.requireNoReversedMoneyLeg();
    }
    OriginalSaleRecord paid = linked ? lastSettlementRecord : NO_SETTLEMENT;
    if (linked && paid.getCardPoiTransactionId() == null) {
      throw invalidState(
          "a linked refund requires a completed payment in this "
              + "session; refund a prior sale through settle() with return lines "
              + "and SettlementOptions refund allocations");
    }
    boolean awardReversed = linked && guards.awardReversed();
    return reversalManager.refund(
        amount,
        null,
        paid.getCardPoiTransactionId(),
        paid.getCardPoiTransactionTimestamp(),
        awardReversed ? null : paid.getAwardPoiTransactionId(),
        awardReversed ? null : paid.getAwardPoiTransactionTimestamp(),
        linked ? paid.getMemberId() : null,
        flow.decider(),
        linked ? guards::markRefunded : () -> {},
        linked ? () -> guards.markAwardReversed(paid.getAwardPoiTransactionId()) : () -> {});
  }

  private void requireRefundable(String operationName) {
    lock.lock();
    try {
      requireOpen(operationName);
      if (moneyMovementInFlight()) {
        throw invalidState(operationName + " is not allowed while money movement is in flight");
      }
    } finally {
      lock.unlock();
    }
  }

  private static void requirePositive(BigDecimal amount) {
    if (amount.signum() <= 0) {
      throw new IllegalArgumentException("refund amount must be positive");
    }
  }

  // ─── Void ───

  @Override
  public ReversalFlow<VoidResult> voidTransaction() {
    operations.track("voidTransaction");
    return new ReversalFlow<VoidResult>(this::executeVoid).session(operations);
  }

  @Override
  public ReversalFlow<VoidResult> voidTransaction(OriginalSaleRecord originalSale) {
    Objects.requireNonNull(originalSale, "originalSale");
    operations.track("voidTransaction");
    return new ReversalFlow<VoidResult>(flow -> executeVoid(flow, originalSale))
        .session(operations);
  }

  private VoidResult executeVoid(ReversalFlow<VoidResult> flow) {
    operations.begin("voidTransaction");
    beginVoid();
    boolean sameSessionVoidStarted = false;
    Set<ReversalMovement.Key> reversedBefore = Set.of();
    OriginalSaleRecord paid = NO_SETTLEMENT;
    try {
      // A failed settlement whose rollback was incomplete left
      // movements standing; voiding that session finishes the unwind.
      boolean resumeRollback = rollbackIncomplete();
      List<ReversalMovement> movements = List.of();
      if (!resumeRollback) {
        if (hasCommittedRefundAllocations()) {
          throw invalidState(
              "voidTransaction cannot reverse committed refund "
                  + "allocations; retry settle() with the same refund allocations");
        }
        guards.requireNotRefunded();
        if (lastSettlementIncludesRefunds) {
          throw invalidState(
              "voidTransaction is only supported for a pure sale "
                  + "settlement; this settlement included return lines");
        }
        paid = lastSettlementRecord;
        movements = voidTarget(paid);
        if (movements.isEmpty()) {
          throw invalidState(
              "voidTransaction requires a completed payment in this "
                  + "session; to void a prior sale, use "
                  + "voidTransaction(OriginalSaleRecord)");
        }
      }
      if (resumeRollback) {
        drainStandingMovements();
      } else {
        reversedBefore = Set.copyOf(guards.reversedMovements());
        sameSessionVoidStarted = true;
      }
      // the manager filters against the reversed-movement set (and
      // records progress into it), so a retry resumes at the
      // movements still standing while the default policy still sees
      // the whole target
      VoidResult result =
          reversalManager.voidMovements(
              movements, paid.getMemberId(), flow.decider(), guards.reversedMovements());
      if (!resumeRollback) {
        guards.completeVoid();
        lastPaymentVoidIncomplete = false;
      }
      return result;
    } catch (RuntimeException e) {
      if (sameSessionVoidStarted && !guards.reversedMovements().equals(reversedBefore)) {
        lastPaymentVoidIncomplete = true;
      }
      throw e;
    } finally {
      endVoid();
    }
  }

  private VoidResult executeVoid(ReversalFlow<VoidResult> flow, OriginalSaleRecord originalSale) {
    operations.begin("voidTransaction");
    if (!originalSale.hasMovement()) {
      throw invalidState(
          "voidTransaction(OriginalSaleRecord) requires at least "
              + "one original transaction reference");
    }
    beginVoid();
    try {
      if (lastSettlementRecord.sharesMovementWith(originalSale)) {
        throw invalidState(
            "the original sale record references the most recent "
                + "settlement in this session; use parameterless voidTransaction() "
                + "so its refund and void guards remain consistent");
      }
      List<ReversalMovement> movements = voidTarget(originalSale);
      Set<ReversalMovement.Key> reversedMovements = priorSaleVoidProgress(originalSale);
      VoidResult result =
          reversalManager.voidMovements(
              movements, originalSale.getMemberId(), flow.decider(), reversedMovements);
      clearPriorSaleVoidProgress(originalSale);
      return result;
    } finally {
      endVoid();
    }
  }

  private void beginVoid() {
    lock.lock();
    try {
      requireOpen("voidTransaction");
      if (moneyMovementInFlight()) {
        throw invalidState("voidTransaction is not allowed while money movement " + "is in flight");
      }
      phase = SessionPhase.VOIDING;
    } finally {
      lock.unlock();
    }
  }

  private void endVoid() {
    lock.lock();
    try {
      phase = SessionPhase.OPEN;
    } finally {
      lock.unlock();
    }
  }

  private static List<ReversalMovement> voidTarget(OriginalSaleRecord originalSale) {
    return ReversalMovement.ofSale(
        originalSale.getStoredValueLoads(),
        PoiRef.ofNullable(
            originalSale.getCardPoiTransactionId(), originalSale.getCardPoiTransactionTimestamp()),
        PoiRef.ofNullable(
            originalSale.getStoredValuePoiTransactionId(),
            originalSale.getStoredValuePoiTransactionTimestamp()),
        PoiRef.ofNullable(
            originalSale.getRedemptionPoiTransactionId(),
            originalSale.getRedemptionPoiTransactionTimestamp()),
        PoiRef.ofNullable(
            originalSale.getRebatePoiTransactionId(),
            originalSale.getRebatePoiTransactionTimestamp()),
        PoiRef.ofNullable(
            originalSale.getAwardPoiTransactionId(),
            originalSale.getAwardPoiTransactionTimestamp()));
  }

  private Set<ReversalMovement.Key> priorSaleVoidProgress(OriginalSaleRecord originalSale) {
    lock.lock();
    try {
      if (priorSaleVoidTarget == null) {
        priorSaleVoidTarget = originalSale;
        return priorSaleVoidReversedMovements;
      }
      if (Objects.equals(priorSaleVoidTarget, originalSale)) {
        return priorSaleVoidReversedMovements;
      }
      if (!priorSaleVoidReversedMovements.isEmpty()) {
        throw invalidState(
            "a void of another prior sale is partially complete; "
                + "retry voidTransaction(OriginalSaleRecord) with the same "
                + "original sale record before voiding another sale");
      }
      priorSaleVoidTarget = originalSale;
      priorSaleVoidReversedMovements = ConcurrentHashMap.newKeySet();
      return priorSaleVoidReversedMovements;
    } finally {
      lock.unlock();
    }
  }

  private void clearPriorSaleVoidProgress(OriginalSaleRecord originalSale) {
    lock.lock();
    try {
      if (Objects.equals(priorSaleVoidTarget, originalSale)) {
        priorSaleVoidTarget = null;
        priorSaleVoidReversedMovements = ConcurrentHashMap.newKeySet();
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Re-runs each standing reversal in the unwind's own order, dropping movements as they succeed so
   * a failed attempt can be retried from the first movement still standing.
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
        throw invalidState(
            "another recovery attempt is already reversing the "
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
   * The previous payment's unwind left movements standing — or a drain has claimed them and is
   * still reversing on the wire, which every guard must treat the same way: the rollback is not
   * complete.
   */
  private boolean rollbackIncomplete() {
    return !standingMovements.isEmpty() || drainInFlight;
  }

  // ─── Display ───

  @Override
  public SessionResult<Void> updateDisplay(Basket basket) {
    Objects.requireNonNull(basket, "basket");
    return this.<Void>operation(
        "updateDisplay",
        () -> {
          requireOpen("updateDisplay");
          display.show(basket);
          return null;
        });
  }

  /** Shows a basket snapshot; callers decide whether failures are fatal. */
  private void showBasket(Basket basket) {
    display.show(basket);
  }

  @Override
  public SessionResult<Void> updateDisplay(DisplayPayload payload) {
    Objects.requireNonNull(payload, "payload");
    return this.<Void>operation(
        "updateDisplay",
        () -> {
          requireOpen("updateDisplay");
          display.send(payload);
          return null;
        });
  }

  // ─── Abort ───

  @Override
  public SessionResult<Void> abort() {
    return this.<Void>operation(
            "abort",
            () -> {
              lock.lock();
              try {
                // The flag and in-flight check share this critical section,
                // so the reset a starting settlement performs cannot
                // eat a live abort. Outside settlement the flag stays clear: a
                // stale abort must not kill the next payment at its first
                // checkAbort, and prompts are aborted via the wire request
                // below.
                if (phase == SessionPhase.SETTLING) {
                  abortRequested = true;
                }
              } finally {
                lock.unlock();
              }
              // the session lifecycle signals are never the abort's target —
              // Like a void, an in-flight end() always settles.
              exchange.abortInFlight();
              return null;
            })
        .unordered();
  }

  // ─── Session lifecycle ───

  /**
   * The lazy operation behind {@link TerminalShopperSession.Builder#start()}: announces this
   * session to the terminal and yields it once the terminal acknowledged, so an unstarted session
   * never escapes.
   */
  SessionResult<TerminalShopperSession> start() {
    // the terminal has acknowledged Start by the time onSuccess
    // runs; a handler that throws would strand that session-scoped
    // context with no session object to end it, so it is released.
    // Built through the session's operations so an asynchronous
    // start runs on (and its handlers deliver like) every other
    // operation of the session it creates.
    return this.<TerminalShopperSession>operation("start", this::started)
        .releasing(TerminalShopperSession::close);
  }

  private TerminalShopperSession started() {
    exchange.sendSessionSignal(SessionSignalCodec.start(getSessionId()));
    // the terminal has acknowledged: widgets bind now, on this lane, and
    // observers hear started before this result completes
    announceStarted();
    // a pre-seeded member pending resolution is looked up only now that
    // the bracket exists; queued behind this start on the operation lane
    memberState.resolveSeed();
    return this;
  }

  @Override
  public SessionResult<Void> end() {
    return endSession(false, null);
  }

  @Override
  public SessionResult<Void> forceEnd(String reason) {
    Objects.requireNonNull(reason, "reason");
    String normalizedReason = reason.strip();
    if (normalizedReason.isEmpty()) {
      throw new IllegalArgumentException("reason must not be blank");
    }
    return endSession(true, normalizedReason);
  }

  @Override
  boolean ended() {
    return phase == SessionPhase.ENDED;
  }

  // ─── Transaction Status ───

  @Override
  public SessionResult<TransactionStatusResult> getTransactionStatus(String originalServiceId) {
    return getTransactionStatus(originalServiceId, TransactionStatusOptions.defaults());
  }

  @Override
  public SessionResult<TransactionStatusResult> getTransactionStatus(
      String originalServiceId, TransactionStatusOptions options) {
    Objects.requireNonNull(originalServiceId, "originalServiceId");
    Objects.requireNonNull(options, "options");
    return operation(
        "getTransactionStatus",
        () -> {
          requireOpen("getTransactionStatus");
          TransactionStatusRequest.Builder statusRequest =
              TransactionStatusRequest.builder()
                  .messageReference(
                      MessageReference.builder()
                          .messageCategory(options.getOriginalCategory())
                          .serviceID(originalServiceId)
                          .saleID(factory.getSaleId())
                          .build());
          if (options.isReceiptReprint()) {
            statusRequest
                .receiptReprintFlag(true)
                .documentQualifier(
                    options.getDocumentQualifiers().toArray(new DocumentQualifierEnum[0]));
          }
          SaleToPOIRequest request =
              SaleToPOIRequest.builder()
                  .messageHeader(
                      factory.header(
                          MessageClassType.SERVICE, MessageCategoryType.TRANSACTION_STATUS))
                  .transactionStatusRequest(statusRequest.build())
                  .build();
          SaleToPOIResponse response =
              exchange.sendExpectingBody(MessageCategoryType.TRANSACTION_STATUS, request);
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
    RepeatedResponseMessageBody repeated =
        body.getRepeatedMessageResponse() == null
            ? null
            : body.getRepeatedMessageResponse().getRepeatedResponseMessageBody();
    if (repeated == null) {
      return TransactionStatusResult.notFound();
    }
    String category = null;
    if (body.getRepeatedMessageResponse().getMessageHeader() != null
        && body.getRepeatedMessageResponse().getMessageHeader().getMessageCategory() != null) {
      category =
          body.getRepeatedMessageResponse().getMessageHeader().getMessageCategory().toValue();
    }
    return TransactionStatusResult.found(
        category,
        repeated.getPaymentResponse(),
        repeated.getLoyaltyResponse(),
        repeated.getStoredValueResponse(),
        repeated.getReversalResponse());
  }

  // ─── Input update ───

  @Override
  public SessionResult<Void> updateInputDisplay(DisplayPayload payload) {
    Objects.requireNonNull(payload, "payload");
    // unordered, like abort(): both exist to overlap the in-flight
    // operation occupying the operation thread — queued behind it,
    // this would wait on the very prompt it amends, and an abort on
    // the very operation it cancels
    return this.<Void>operation(
            "updateInputDisplay",
            () -> {
              NexoExchange.InFlight inFlight = exchange.currentInFlight();
              if (inFlight == null || inFlight.getCategory() != MessageCategoryType.INPUT) {
                throw invalidState(
                    "updateInputDisplay requires an input request awaiting a response");
              }
              String base64;
              try {
                base64 = DisplayPayloadHelper.toBase64(payload);
              } catch (JAXBException e) {
                throw new SessionException(
                    new SessionError(
                        SessionErrorCode.UNKNOWN,
                        "failed to serialize input update payload",
                        null,
                        e));
              }
              SaleToPOIRequest request =
                  SaleToPOIRequest.builder()
                      .messageHeader(
                          factory.header(MessageClassType.DEVICE, MessageCategoryType.INPUT_UPDATE))
                      .inputUpdate(
                          InputUpdate.builder()
                              .messageReference(
                                  MessageReference.builder()
                                      .messageCategory(MessageCategoryType.INPUT)
                                      .serviceID(inFlight.getServiceId())
                                      .saleID(factory.getSaleId())
                                      .build())
                              .outputContent(
                                  OutputContent.builder()
                                      .outputFormat(OutputFormatEnum.XHTML)
                                      .outputXHTML(base64)
                                      .build())
                              .build())
                      .build();
              try {
                // must go to the device processing the input, not through routing
                inFlight.getClient().request(factory.envelope(request));
              } catch (BiltNexoClientException e) {
                throw new SessionException(
                    new SessionError(
                        SessionErrorCode.NETWORK,
                        "input update failed: " + e.getMessage(),
                        null,
                        e));
              }
              return null;
            })
        .unordered();
  }

  // ─── Terminal (device & admin operations) ───

  @Override
  public Terminal terminal() {
    Terminal current = terminal;
    if (current != null) {
      return current;
    }
    lock.lock();
    try {
      if (terminal == null) {
        terminal =
            Terminal.builder()
                .client(client)
                .saleId(factory.getSaleId())
                .poiId(factory.getPoiId())
                .storeLocation(getStoreLocation())
                .callbackExecutor(operations.callback())
                .build();
      }
      return terminal;
    } finally {
      lock.unlock();
    }
  }

  // ─── Escape hatch ───

  @Override
  public BiltNexoTerminalClient getClient() {
    return client;
  }

  // ─── Internals ───

  private SessionResult<Void> endSession(boolean forced, String reason) {
    String operationName = forced ? "forceEnd" : "end";
    return operation(
        operationName,
        () -> {
          String abandonedRecovery = null;
          lock.lock();
          try {
            if (phase == SessionPhase.ENDING || phase == SessionPhase.ENDED) {
              throw invalidState("the session has already ended; create a new session");
            }
            if (moneyMovementInFlight()) {
              throw invalidState(
                  operationName + "() is not allowed while money movement " + "is in flight");
            }
            if (forced) {
              if (drainInFlight) {
                throw invalidState(
                    "forceEnd() is not allowed while settlement " + "recovery is moving money");
              }
              abandonedRecovery = unresolvedRecoverySummary();
            } else {
              requireRecoveryCompleteForEnd();
            }
            phase = SessionPhase.ENDING;
          } finally {
            lock.unlock();
          }

          if (!forced) {
            try {
              exchange.sendSessionSignal(SessionSignalCodec.end(getSessionId()));
            } catch (RuntimeException e) {
              lock.lock();
              try {
                phase = SessionPhase.OPEN;
              } finally {
                lock.unlock();
              }
              throw e;
            }
            sealSession(false);
            return null;
          }

          try {
            LOGGER.warning(
                "forceEnd() is abandoning session "
                    + getSessionId()
                    + " (reason: "
                    + reason
                    + "); unresolved recovery: "
                    + abandonedRecovery);
            exchange.sendSessionSignal(SessionSignalCodec.end(getSessionId()));
          } finally {
            // This is the escape hatch: local teardown is final even if
            // the terminal cannot acknowledge its own cleanup.
            sealSession(true);
          }
          return null;
        });
  }

  private void requireRecoveryCompleteForEnd() {
    if (rollbackIncomplete()) {
      throw invalidState(
          "a failed payment's rollback is incomplete; "
              + "finish the unwind with voidTransaction() before ending "
              + "the session");
    }
    if (lastPaymentVoidIncomplete) {
      throw invalidState(
          "a void of the most recent payment is partially "
              + "complete; retry voidTransaction() before ending the session");
    }
    if (!priorSaleVoidReversedMovements.isEmpty()) {
      throw invalidState(
          "a prior-sale void is partially complete; retry "
              + "voidTransaction(OriginalSaleRecord) with the same original "
              + "sale record before ending the session");
    }
    if (hasCommittedRefundAllocations()) {
      throw invalidState(
          "refund allocations from a failed settlement are "
              + "committed; retry settle() with the same refund allocations "
              + "before ending the session");
    }
  }

  private String unresolvedRecoverySummary() {
    List<String> unresolved = new ArrayList<>();
    if (!standingMovements.isEmpty()) {
      unresolved.add(standingMovements.size() + " standing rollback movement(s)");
    }
    if (lastPaymentVoidIncomplete) {
      unresolved.add(guards.reversedMovements().size() + " reversed same-session void movement(s)");
    }
    if (!priorSaleVoidReversedMovements.isEmpty()) {
      unresolved.add(
          priorSaleVoidReversedMovements.size() + " reversed prior-sale void movement(s)");
    }
    if (hasCommittedRefundAllocations()) {
      unresolved.add(committedRefundAllocations.size() + " committed refund allocation(s)");
    }
    return unresolved.isEmpty() ? "none" : String.join(", ", unresolved);
  }

  private void sealSession(boolean abandonRecovery) {
    lock.lock();
    try {
      if (abandonRecovery) {
        standingMovements = List.of();
        committedRefundAllocations = List.of();
        committedRefundMovements = List.of();
        lastPaymentVoidIncomplete = false;
        guards.reset();
        priorSaleVoidTarget = null;
        priorSaleVoidReversedMovements = ConcurrentHashMap.newKeySet();
      }
      phase = SessionPhase.ENDED;
    } finally {
      lock.unlock();
    }
    announceEnded();
    // no further operations may run; asynchronous submissions after
    // this fail into their handlers instead of queueing forever
    operations.shutdown();
  }
}
