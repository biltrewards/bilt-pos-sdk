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
import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.session.basket.Basket;
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
import com.bilt.pos.session.input.PinOptions;
import com.bilt.pos.session.input.PinResult;
import com.bilt.pos.session.input.Signature;
import com.bilt.pos.session.settlement.OriginalSaleRecord;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.bilt.pos.session.settlement.SettlementType;
import com.bilt.pos.session.storedvalue.StoredValueBalance;
import com.bilt.pos.session.storedvalue.StoredValueCard;
import com.bilt.pos.session.storedvalue.StoredValueOperationResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * A shopper session with a Bilt terminal attached: a {@link ShopperSession} whose loyalty, prompts,
 * customer display, and settlement run on the terminal over {@link BiltNexoTerminalClient}.
 *
 * <p>On top of the basket and member every shopper session has, this one drives the terminal (or an
 * external customer display) and orchestrates settlement — return allocations, rebate redemption,
 * point redemption, stored value/card charge, and reward award — as a single flow. Every operation
 * maps to standard Nexo Sale to POI 3.0 messages; the raw client remains available via {@link
 * #getClient()}, and the session-less device and admin operations (diagnostics, totals, printing,
 * sound) via {@link #terminal()}.
 *
 * <p>Terminal operations are lazy: methods returning {@link SessionResult} (or {@link
 * SettlementFlow}, {@link ReversalFlow}) send nothing until {@code execute()} (asynchronous,
 * outcome through the registered handlers), {@code executeSync()}, {@code get()}, or {@code
 * getOrNull()} (blocking) is invoked. Basket mutations are pure local compute — the automatic
 * display refresh they trigger is asynchronous and conflated.
 *
 * <p>A session is bracketed on the terminal: the builder's {@link Builder#start() start()}
 * announces it (Nexo {@code Admin} session start signal) and only hands out the session once the
 * terminal acknowledged, and {@link #end()} tells the terminal to discard the session-scoped data
 * it accumulated. An ended session cannot be used or restarted. The register may run multiple
 * sequential settlements and other operations before ending the bracket; {@link
 * SessionBasket#clear()} starts a fresh basket after a successful settlement. {@link
 * #forceEnd(String)} is the explicit escape hatch when financial recovery cannot be completed.
 * Sessions are {@link AutoCloseable}, so try-with-resources sends the normal, guarded end signal
 * even on exception paths.
 *
 * <pre>{@code
 * try (TerminalShopperSession session = TerminalShopperSession.builder()
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
 * <p>Sessions are intended for use from a single register thread. {@link #abort()} and {@link
 * #updateInputDisplay(DisplayPayload)} are the only methods that are safe to call from another
 * thread.
 */
public interface TerminalShopperSession extends ShopperSession {

  /** A builder for a session bracketed on a terminal; see {@link Builder}. */
  static Builder builder() {
    return new Builder();
  }

  /** Target terminal identifier, sent as {@code POIID}. */
  String getPoiId();

  // ─── Member Identification ───

  /** Prompts the customer on the terminal to identify themselves. */
  SessionResult<IdentifyResult> identifyMember();

  /**
   * Prompts the customer on the terminal to identify themselves (Nexo {@code CardAcquisition} with
   * loyalty handling).
   *
   * <p>Lookup outcomes that simply leave the checkout without a member — not found, suspended,
   * customer cancelled — are delivered to {@code onSuccess} with the corresponding {@link
   * IdentifyStatus}; {@code onError} fires only for real failures.
   *
   * <p>Identification remains available after a failed settlement, so a declined guest checkout can
   * attach a member and retry with loyalty enabled.
   */
  SessionResult<IdentifyResult> identifyMember(IdentifyOptions options);

  /**
   * POS-driven member lookup by an identifier on file (Nexo {@code BalanceInquiry}); no terminal
   * prompt. Takes a member pending resolution — {@code Member.idResolver().phone("...")} or {@code
   * .accountId("...")}, optionally {@code .keyedByCashier()} — and delivers the outcome like {@link
   * #identifyMember(IdentifyOptions)} does: a found member attaches to the session, not found and
   * suspended detach any previous one. The same lookup runs in the background, without a result to
   * observe, when such a member is attached through {@link #member(Member)}.
   *
   * <p>The terminal resolves account ids and phone numbers only; an email or custom identifier
   * fails with {@link SessionErrorCode#UNSUPPORTED}.
   *
   * @throws IllegalArgumentException for a member that is already resolved — attach it with {@link
   *     #member(Member)} instead
   */
  SessionResult<IdentifyResult> identifyMember(Member pending);

  // ─── Card acquisition ───

  /** Reads card data from the terminal without initiating a payment. */
  SessionResult<CardAcquisitionResult> acquireCard();

  /** Reads card data from the terminal without initiating a payment. */
  SessionResult<CardAcquisitionResult> acquireCard(CardAcquisitionOptions options);

  // ─── Input (nexo native) ───

  /** Prompts the customer for a digit string (e.g. a ZIP code). */
  SessionResult<String> requestDigitString(String prompt);

  /** Prompts the customer for a digit string with explicit input options. */
  SessionResult<String> requestDigitString(String prompt, InputOptions options);

  /** Prompts the customer for a decimal amount (e.g. a tip). */
  SessionResult<BigDecimal> requestDecimalString(String prompt);

  /** Prompts the customer for a decimal amount with explicit input options. */
  SessionResult<BigDecimal> requestDecimalString(String prompt, InputOptions options);

  /** Prompts the customer for free text (e.g. an email address). */
  SessionResult<String> requestTextString(String prompt);

  /** Prompts the customer for free text with explicit input options. */
  SessionResult<String> requestTextString(String prompt, InputOptions options);

  /** Prompts the customer for a yes/no confirmation. */
  SessionResult<Boolean> requestConfirmation(String prompt);

  /** Prompts the customer for a yes/no confirmation with explicit options. */
  SessionResult<Boolean> requestConfirmation(String prompt, ConfirmationOptions options);

  /** Prompts the customer to pick from a menu of entries. */
  SessionResult<MenuSelection> requestMenuEntry(String prompt, List<String> entries);

  /** Prompts the customer to pick from a menu of entries with explicit options. */
  SessionResult<MenuSelection> requestMenuEntry(
      String prompt, List<String> entries, MenuOptions options);

  // ─── Input (XSD-based) ───

  /** Captures a handwritten signature on the terminal. */
  SessionResult<Signature> requestSignature(String prompt);

  /** Asks the customer to confirm an amount. */
  SessionResult<Boolean> requestAmountConfirmation(BigDecimal amount, String prompt);

  // ─── PIN ───

  /** Captures and encrypts a PIN on the secure PIN pad. */
  SessionResult<PinResult> requestPinEntry(PinOptions options);

  /** Captures a PIN and verifies it, returning the encrypted block. */
  SessionResult<PinResult> requestPinVerify(PinOptions options);

  /** Verifies a PIN without returning the block. */
  SessionResult<PinResult> requestPinVerifyOnly(PinOptions options);

  // ─── Stored Value ───

  /**
   * Registers a stored value (gift) card charged as part of a split tender during {@code settle()}.
   * The card number is treated as keyed ({@code PAN}); use {@link
   * #setStoredValueCard(StoredValueCard)} for scanned or swiped cards or to set a provider.
   */
  void setStoredValueCard(String cardNumber);

  /**
   * Registers a stored value (gift) card charged as part of a split tender during {@code settle()}.
   * Pass {@code null} to clear.
   */
  void setStoredValueCard(StoredValueCard card);

  /** Queries the available balance on a stored value card. */
  SessionResult<StoredValueBalance> storedValueBalance(StoredValueCard card);

  /**
   * Activates a stored value card, optionally loading an initial balance. Use {@code
   * BigDecimal.ZERO} to activate without funds.
   */
  SessionResult<StoredValueOperationResult> storedValueActivate(
      StoredValueCard card, BigDecimal initialAmount);

  /** Loads funds onto a stored value card. */
  SessionResult<StoredValueOperationResult> storedValueLoad(
      StoredValueCard card, BigDecimal amount);

  /** Unloads (cashes out) funds from a stored value card. */
  SessionResult<StoredValueOperationResult> storedValueUnload(
      StoredValueCard card, BigDecimal amount);

  /**
   * Permanently deactivates a stored value card (an {@code Unload} with a zero amount). Not all
   * stored value providers support deactivation.
   */
  SessionResult<StoredValueOperationResult> storedValueDeactivate(StoredValueCard card);

  /**
   * Reserves an amount on a stored value card. Provider support varies — confirm with your stored
   * value provider before relying on this.
   */
  SessionResult<StoredValueOperationResult> storedValueReserve(
      StoredValueCard card, BigDecimal amount);

  /**
   * Reverses a prior stored value operation by its terminal reference (from {@link
   * StoredValueOperationResult#getPoiTransactionId()}).
   */
  SessionResult<StoredValueOperationResult> storedValueReverse(
      String originalPoiTransactionId, Instant originalPoiTransactionTimestamp);

  /**
   * Requests a duplicate (replacement) for a stored value card. Provider support varies — confirm
   * with your stored value provider before relying on this.
   */
  SessionResult<StoredValueOperationResult> storedValueDuplicate(StoredValueCard card);

  // ─── Settlement ───

  /** Starts the settlement orchestration chain with default options. */
  SettlementFlow settle();

  /**
   * Starts the settlement orchestration chain. Nothing is sent — and no precondition is verified —
   * until the returned {@link SettlementFlow}'s {@code execute()}, {@code executeSync()}, {@code
   * get()}, or {@code getOrNull()} is invoked: the session must then be open with a non-empty,
   * unconsumed basket. With the default {@link SettlementType#REFUND_THEN_CHARGE}, sale lines less
   * credits are charged through the normal tender sequence and return lines are covered by
   * register-supplied refund allocations. With {@link SettlementType#NET}, only the signed basket
   * difference moves: a positive balance is charged, a negative balance is allocated as a refund,
   * and a zero balance sends no monetary movement.
   *
   * <p>Terminal-backed refund allocations carry itemization on the first {@code
   * PaymentRequest(Refund)} leg only. A separate refund carries the refund-side lines; a net refund
   * carries the signed mixed basket. Additional refund legs are amount-only because allocations are
   * tender-level, not line-level.
   *
   * <p>Refund allocations are not retried in the same settlement run. If one fails,
   * already-committed refund allocation movements remain recorded, and the register retries by
   * calling {@code settle()} again with the same committed allocation prefix. Charge-side failures
   * after refund allocations use {@link SettlementFlow#onError} recovery decisions.
   *
   * <p>A same-session void that already reversed one or more movements must be retried to
   * completion before another settlement can replace its payment target.
   */
  SettlementFlow settle(SettlementOptions options);

  // ─── Refund ───

  /**
   * Full linked refund of this session's completed payment. Also reverses the loyalty award when
   * one ran — best-effort by default (override via {@link ReversalFlow#onError}).
   *
   * <p>Linked refunds reference a single transaction: after a split tender this is the card leg —
   * use {@code voidTransaction()} to reverse both legs, or the stored value operations to return
   * funds to the gift card. A refund reverses the card leg and award only; the sale's committed
   * rebate and redemption movements are reversed by {@link #voidTransaction()}. Once a refund has
   * returned money the payment can no longer be voided; a tender skipped by an {@code onError}
   * decision leaves it voidable, with an award the flow reversed remembered so nothing re-credits
   * it. Once a void has partially reversed the payment's money legs, refunds are refused until the
   * void is finished. To refund a sale taken by an earlier session, ring return lines into a new
   * session and provide refund allocations on {@link SettlementOptions}. Loyalty reversals use the
   * member attached when this payment settled, even if the session was subsequently re-identified.
   */
  ReversalFlow<RefundResult> refund();

  /**
   * Partial linked refund of this session's completed payment. Also reverses the loyalty award when
   * one ran (best-effort by default).
   */
  ReversalFlow<RefundResult> refund(BigDecimal amount);

  /** Unlinked refund, not tied to a prior transaction. Payment-only — no loyalty reversal. */
  ReversalFlow<RefundResult> refundUnlinked(BigDecimal amount);

  // ─── Void ───

  /**
   * Reverses this session's completed payment: every movement it committed — the card and stored
   * value legs (Nexo {@code ReversalRequest}), then the redemption, rebate, and award (their {@code
   * LoyaltyRequest} refund types) — in that order. A checkout fully covered by rewards has no money
   * leg; voiding it refunds the loyalty movements alone. To void a sale taken by an earlier
   * session, use {@link #voidTransaction(OriginalSaleRecord)}.
   *
   * <p>When a step fails, the flow's {@link ReversalFlow#onError onError} handler decides between
   * retry, skip, and abort — see {@link ReversalFlow} for the default policy. A retried void
   * resumes at the first movement still standing — reversed movements are never re-credited, and
   * the retry's {@link VoidResult} describes only the movements that call sent. Until that retry
   * succeeds, the session refuses {@code basket().clear()}, another settlement, and {@link #end()}
   * because each would discard the in-memory resume progress. After the void succeeds, that
   * progress is discarded; a later void or linked refund reaches the terminal, which owns
   * already-voided transaction enforcement.
   *
   * <p>On a session whose payment failed with an incomplete rollback, {@code voidTransaction()}
   * finishes the unwind by retrying the reversals that did not go through. Not allowed once the
   * payment has been refunded from this session — a void would return the full amount on top of the
   * refund, so further returns must use {@link #refund(BigDecimal)}. Loyalty reversals use the
   * member attached when this payment settled, not a later session identification.
   */
  ReversalFlow<VoidResult> voidTransaction();

  /**
   * Voids a prior sale by its persisted original-sale record. This is a whole-transaction void:
   * every referenced card, stored value, rebate, redemption, and award movement is reversed in the
   * same order as a same-session void. If the void partially fails, retry it on the same session
   * instance because the in-memory reversed-movement progress is what prevents already-reversed
   * legs from being sent again; {@link #end()} is refused until that retry succeeds. Mixed
   * sale/return settlements should use {@link #settle(SettlementOptions)} with refund allocations
   * instead. A record containing any movement of this session's most recent settlement is refused;
   * use parameterless {@link #voidTransaction()} so the settlement's shared refund/void guards
   * remain authoritative.
   */
  ReversalFlow<VoidResult> voidTransaction(OriginalSaleRecord originalSale);

  // ─── Display ───

  /**
   * Refreshes the customer display from the given basket snapshot using the configured {@link
   * DisplayRenderer}. Failures are delivered through the returned result's {@code onError}, not the
   * session's {@code onBackgroundError} — that handler is only for pushes the session initiates
   * itself. Allowed until the session has ended.
   */
  SessionResult<Void> updateDisplay(Basket basket);

  /**
   * Sends a custom display payload to the customer display (or the external display when
   * configured). Same contract as {@link #updateDisplay(Basket)}.
   */
  SessionResult<Void> updateDisplay(DisplayPayload payload);

  /**
   * Replaces the display content of the input prompt currently awaiting a response (Nexo {@code
   * InputUpdate}) — e.g. to update a countdown or amend the prompt while the customer decides.
   *
   * <p>Because input calls block their calling thread, this must be invoked from a different
   * thread; like {@link #abort()} it is safe to do so. Fails with {@link
   * SessionErrorCode#INVALID_STATE} when no input is in progress.
   */
  SessionResult<Void> updateInputDisplay(DisplayPayload payload);

  // ─── Abort ───

  /**
   * Aborts the in-flight operation. The session itself continues — an abort is a normal register
   * maneuver (cancel the signature prompt, stop a tender to take a gift card first), not an
   * abandonment; to abandon the checkout after recovery, {@link #end()} the session. If recovery
   * cannot be completed, use {@link #forceEnd(String)} explicitly.
   *
   * <p>If a terminal operation is awaiting a response, a Nexo {@code AbortRequest} referencing it
   * is sent (best-effort) to the device that is processing it. An aborted payment stops at its next
   * step boundary, reverses the committed steps, and leaves the basket intact so {@code settle()}
   * may retry (the thrown error carries {@link SessionErrorCode#ABORTED}). Aborted prompts (input,
   * PIN, card reads, identification) deliver their aborted/cancelled outcome. With nothing in
   * flight this is a no-op.
   *
   * <p>Money-moving operations (refunds, stored value) always deliver their outcome even when the
   * abort raced them: the movement may have completed on the terminal, and the register must know.
   * Voids and the session lifecycle signals are never the abort's target — cancelling an in-flight
   * {@link #end()} would only strand the terminal's session-scoped data. An abort that lands after
   * the payment completed leaves the transaction standing; use {@code voidTransaction()} to reverse
   * it.
   *
   * <p>Deliberately <em>unordered</em>: queued on the session's operation lane, the abort would
   * wait on the very operation it cancels, so it overtakes it instead. Safe to call from any
   * thread.
   */
  SessionResult<Void> abort();

  // ─── Session lifecycle ───

  /**
   * Ends the session: tells the terminal to discard the session-scoped data it accumulated (Nexo
   * {@code Admin} session end signal). After it succeeds, no session operation is allowed; create a
   * new session to establish another terminal bracket.
   *
   * <p>Refused while money is in flight, while a failed payment's rollback is incomplete, while a
   * same-session or prior-sale void is partially complete, or while refund allocations from a
   * failed settlement have committed — finish the unwind with {@link #voidTransaction()} or retry
   * {@code settle()} first, or the terminal/register state would be abandoned with money movements
   * still unresolved. If the end signal fails, the session remains open so the call can be retried.
   * A concurrent {@link #abort()} never cancels an in-flight end — the exchange always settles.
   */
  @Override
  SessionResult<Void> end();

  /**
   * Irrevocably abandons this session and its in-memory recovery progress. Use only when an
   * incomplete settlement rollback, refund allocation, or void cannot be recovered and the register
   * has recorded the incident for reconciliation. Start a new session afterwards; forced
   * termination never makes this instance reusable.
   *
   * <p>Unlike {@link #end()}, this operation bypasses unresolved-recovery guards. It still refuses
   * to run while a settlement, void, or recovery drain is actively moving money. The supplied
   * reason and the abandoned recovery categories are logged. The terminal end signal remains
   * best-effort: if it fails, the returned result reports that failure, but the local session is
   * still sealed and cannot be retried. A later recovery from a new session must use externally
   * persisted transaction progress; this session's duplicate-movement protection is discarded.
   *
   * <p>{@link #close()} deliberately does not call this method. Automatic resource cleanup must
   * never silently abandon financial recovery.
   *
   * @param reason nonblank operational reason recorded in the warning log
   * @throws NullPointerException if {@code reason} is null
   * @throws IllegalArgumentException if {@code reason} is blank
   */
  SessionResult<Void> forceEnd(String reason);

  // ─── Transaction Status ───

  /**
   * Checks the status of a prior request by the {@code ServiceID} it was sent with. Maps to Nexo
   * {@code TransactionStatusRequest}; the terminal repeats the original response when the
   * transaction is found.
   *
   * @param originalServiceId the {@code ServiceID} of the original request
   */
  SessionResult<TransactionStatusResult> getTransactionStatus(String originalServiceId);

  /**
   * Checks the status of a prior request, optionally requesting receipt data for reprinting and
   * referencing a non-payment original.
   */
  SessionResult<TransactionStatusResult> getTransactionStatus(
      String originalServiceId, TransactionStatusOptions options);

  // ─── Terminal (device & admin operations) ───

  /**
   * The device and admin operations of this session's terminal — {@code diagnose()}, {@code
   * getTotals()}, {@code reconcile()}, {@code print()}, {@code playSound()}/{@code stopSound()} —
   * built from this session's client, identifiers, and callback executor. Created lazily and
   * cached; see {@link Terminal}.
   *
   * <p>The terminal is deliberately independent of the session: it has its own operation thread and
   * exchange, so its operations do not queue behind an in-flight payment (a connectivity check
   * mid-payment works), they keep working after {@link #end()}, and its {@link Terminal#close()
   * close()} does not touch the session. For the same reason {@link #abort()} does not target
   * terminal operations — they run on a separate exchange.
   */
  Terminal terminal();

  // ─── Escape hatch ───

  /** The underlying terminal client, for raw Nexo access. */
  BiltNexoTerminalClient getClient();

  /** Builder for a {@link TerminalShopperSession}. */
  final class Builder {

    BiltNexoTerminalClient client;
    String saleId;
    String poiId;
    String currency;
    String storeLocation;
    boolean autoDisplay = true;
    BiltNexoTerminalClient externalDisplayClient;
    DisplayRenderer displayRenderer;
    Consumer<Basket> onBasketUpdated;
    Executor callbackExecutor;
    Consumer<SessionError> onBackgroundError;
    Member member;
    Consumer<Member> onMemberChanged;

    private Builder() {}

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
     * Store location identifier, sent as {@code SaleTerminalData.TotalsGroupID} on every
     * transaction this session creates — it groups the store's transactions for totals and
     * reconciliation ({@code getTotals()} filters by it). Optional.
     */
    public Builder storeLocation(String storeLocation) {
      this.storeLocation = storeLocation;
      return this;
    }

    /**
     * Whether basket mutations automatically refresh the customer display. Default {@code true}.
     */
    public Builder autoDisplay(boolean autoDisplay) {
      this.autoDisplay = autoDisplay;
      return this;
    }

    /**
     * A second client for an external customer display device. When set, {@code Display} and {@code
     * Input} messages are routed to it, while payment, card, and PIN operations stay on the
     * terminal.
     */
    public Builder externalDisplayClient(BiltNexoTerminalClient externalDisplayClient) {
      this.externalDisplayClient = externalDisplayClient;
      return this;
    }

    /**
     * Custom rendering of basket snapshots for the customer display. Rarely necessary; defaults to
     * the standard itemised receipt.
     */
    public Builder displayRenderer(DisplayRenderer displayRenderer) {
      this.displayRenderer = displayRenderer;
      return this;
    }

    /**
     * Out-of-band basket update callback. Reserved for a future reactive mode in which the terminal
     * pushes offer/member changes during scanning; not invoked in v1.
     */
    public Builder onBasketUpdated(Consumer<Basket> onBasketUpdated) {
      this.onBasketUpdated = onBasketUpdated;
      return this;
    }

    /**
     * Where asynchronously executed operations deliver their handlers — e.g. an Android main-thread
     * executor so handlers may touch UI directly. Applies to {@code execute()}; {@code
     * executeSync()} and the blocking accessors are unaffected. Overridable per call with {@code
     * callbackOn(executor)}. Without one, handlers run directly on the session's operation thread
     * and must be fast, non-blocking, and must never synchronously invoke another session
     * operation.
     */
    public Builder callbackExecutor(Executor callbackExecutor) {
      this.callbackExecutor = callbackExecutor;
      return this;
    }

    /**
     * Handler for failures of work the session performs on its own behalf, with no result object to
     * report through: the automatic display push after a basket mutation ({@link
     * #autoDisplay(boolean)}) and the final settlement display refresh. Manual {@code
     * updateDisplay(...)} failures are not delivered here — they report through their own per-call
     * {@code onError}.
     *
     * <p>Background failures never interrupt the checkout and are logged whether or not a handler
     * is registered. Delivered through the {@link #callbackExecutor(Executor) callbackExecutor}
     * when one is configured, directly on the failing thread otherwise.
     */
    public Builder onBackgroundError(Consumer<SessionError> onBackgroundError) {
      this.onBackgroundError = onBackgroundError;
      return this;
    }

    // ─── Member (POS-provided) ───

    /**
     * The member to start the session with, when the shopper is already known before the visit
     * begins. Same semantics as {@link ShopperSession#member(Member)}, except that the initial
     * member is not announced through {@link #onMemberChanged(Consumer)}; a pending member is
     * looked up on the terminal once the start is acknowledged, and that resolution is announced.
     * Optional.
     */
    public Builder member(Member member) {
      this.member = member;
      return this;
    }

    /**
     * Handler for every change of the session's member: attached by the POS, found by a terminal
     * prompt or lookup, or cleared — with the new {@link Member}, {@code null} when signed out.
     * Delivered through the {@link #callbackExecutor(Executor) callbackExecutor} when one is
     * configured, directly on the changing thread otherwise. A throwing handler is logged and never
     * interrupts the checkout.
     */
    public Builder onMemberChanged(Consumer<Member> onMemberChanged) {
      this.onMemberChanged = onMemberChanged;
      return this;
    }

    /**
     * Validates the configuration and returns a lazy operation that announces the session to the
     * terminal (Nexo {@code Admin} session start signal) and yields it once the terminal
     * acknowledged — an unstarted session never exists, so no operation can reach the terminal
     * before the start. Like every session operation, nothing is sent until {@code execute()},
     * {@code get()}, or {@code getOrNull()} is invoked.
     *
     * <p>A refused start yields no session; call {@code start()} again for a fresh attempt (each
     * attempt is a new session with a new session ID).
     *
     * <p>If the registered {@code onSuccess} handler itself throws, the just-started session is
     * ended on the terminal (best-effort) before the exception propagates: a {@code start()} whose
     * execution threw never leaves a terminal-side session behind, and any session it may have
     * delivered to the handler must be considered lost.
     *
     * @throws IllegalStateException if a required field is missing
     */
    public SessionResult<TerminalShopperSession> start() {
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
      return new NexoTerminalShopperSession(this).start();
    }
  }
}
