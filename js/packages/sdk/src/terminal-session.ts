import type {
  AbandonedSettlementRecord,
  Basket,
  CardAcquisitionOptions,
  CardAcquisitionResult,
  ConfirmationOptions,
  DisplayPayload,
  GiftCardPaymentResult,
  IdentifyOptions,
  IdentifyResult,
  InputOptions,
  MemberIdResolver,
  MenuOptions,
  MenuSelection,
  Money,
  OriginalSaleRecord,
  PinOptions,
  PinResult,
  PointRedemptionResult,
  RebateRedemptionResult,
  RefundResult,
  ReversalDecision,
  ReversalStep,
  SettlementContext,
  SettlementFailure,
  SettlementMovement,
  SettlementOptions,
  SettlementRecovery,
  SettlementResult,
  Signature,
  StoredValueBalance,
  StoredValueCard,
  StoredValueOperationResult,
  TransactionStatusOptions,
  TransactionStatusResult,
  VoidResult,
} from '@bilt/pos-protocol';
import type { SessionError } from './errors';
import type { Operation } from './operation';
import type { ShopperSession } from './session';
import type { Terminal } from './terminal';

/** A handler may answer synchronously or with a promise. */
export type MaybePromise<T> = T | Promise<T>;

/**
 * What every step handler receives beside its payload. A step is the host waiting on the
 * register, with a deadline: a handler that has not answered by `deadline` is ignored and the
 * step's documented default applies — the Java SDK's own default — so a handler that may be slow
 * (a tax service, a cashier prompt) should watch `signal`, which aborts at the deadline. The
 * engine never extends a deadline.
 */
export interface StepInfo {
  readonly deadline: Date;
  readonly signal: AbortSignal;
}

/**
 * The short form of a recovery decision; `"RETRY"` is `{ action: "RETRY" }`. `EXTERNAL` has no
 * short form because it must carry the `externalPayment` that records the tender.
 */
export type SettlementRecoveryAction = Exclude<SettlementRecovery['action'], 'EXTERNAL'>;

/**
 * The register's handlers for a settlement — the `SettlementFlow` handlers in Java. Each is
 * optional and consulted only when present, exactly as a Java handler is consulted only when
 * registered: a settlement with no `onError` aborts on a charge-side failure, one with no
 * `onRebatesRedeemed` accepts the suggested total.
 *
 * The three total handlers return the running total for the next step, letting the register
 * recompute tax on the discounted amount; `beforeStep` returns the `SaleTransactionID` for the
 * step, or nothing to keep the basket's. Movement callbacks are observations, not the final
 * ledger: an abort or a same-run recovery can reverse charge-side movements without a
 * compensating callback, and `SettlementResult.movements` is authoritative.
 */
export interface SettlementHandlers {
  beforeStep?(context: SettlementContext, step: StepInfo): MaybePromise<string | null | undefined>;

  /** Rebates were committed; default `result.suggestedTotal`. */
  onRebatesRedeemed?(result: RebateRedemptionResult, step: StepInfo): MaybePromise<Money>;

  /** Points were redeemed for monetary value; default `result.suggestedTotal`. */
  onPointsRedeemed?(result: PointRedemptionResult, step: StepInfo): MaybePromise<Money>;

  /** The stored value card was charged; default `result.suggestedTotal`. */
  onGiftCardPayment?(result: GiftCardPaymentResult, step: StepInfo): MaybePromise<Money>;

  /** Any money or loyalty movement committed. */
  onMovement?(movement: SettlementMovement): void;
  onCardCharged?(movement: SettlementMovement): void;
  onExternallyPaid?(movement: SettlementMovement): void;
  onAwarded?(movement: SettlementMovement): void;
  onStoredValueLoaded?(movement: SettlementMovement): void;
  onCardRefunded?(movement: SettlementMovement): void;
  onGiftCardRefunded?(movement: SettlementMovement): void;
  onExternalRefunded?(movement: SettlementMovement): void;
  onPointsRefunded?(movement: SettlementMovement): void;
  onRebateRefunded?(movement: SettlementMovement): void;
  onAwardRefunded?(movement: SettlementMovement): void;

  /**
   * A charge-side step failed definitively, or TransactionStatus could not resolve an
   * indeterminate request. Returns how to recover: retry the step, skip an optional one, record
   * an external tender, abort and unwind, or abandon to the register. Default `ABORT`. For a
   * refund allocation failure this is a notification and the answer is ignored.
   */
  onError?(
    failure: SettlementFailure,
    step: StepInfo,
  ): MaybePromise<SettlementRecovery | SettlementRecoveryAction>;

  /** Recovery was abandoned; the record is now the register's reconciliation responsibility. */
  onAbandoned?(record: AbandonedSettlementRecord): void;
}

/** `settle()` takes the Java `SettlementOptions` and the handlers in one object. */
export type SettleOptions = SettlementOptions & SettlementHandlers;

/**
 * The register's handler for a refund or void — `ReversalFlow.onError` in Java. `step` is
 * `null` for a failure before any step ran or after every step was skipped; the decision is
 * then informational. `null` or `undefined` counts as `ABORT`. Without a handler the Java
 * default policy applies: a failed money step aborts, a failed loyalty movement riding along
 * with a money step is skipped, a failed loyalty movement that is the substance of the
 * reversal aborts.
 */
export interface ReversalHandlers {
  onError?(
    step: ReversalStep | null,
    error: SessionError,
    info: StepInfo,
  ): MaybePromise<ReversalDecision | null | undefined>;
}

/** What a refund or void resolves with. */
export type ReversalResult = RefundResult | VoidResult;

/**
 * A shopper session with a Bilt terminal attached — the Java `TerminalShopperSession`: loyalty
 * prompts, customer input, the customer display, stored value, settlement, refunds and voids.
 *
 * Every terminal operation returns an `Operation`: await it for the result, or hold it to watch
 * its status and abort it. Operations run one at a time in the order they were started;
 * `updateInputDisplay()` and `abort()` are the exceptions and never queue. The session is
 * bracketed on the terminal: it exists once the terminal acknowledged the start and `end()`
 * tells the terminal to discard its session-scoped data. Several settlements may run in one
 * session, with `basket.clear()` between them.
 */
export interface TerminalShopperSession extends ShopperSession {
  readonly kind: 'terminal';

  /** The Nexo `POIID` the session's messages carry: the one it was started with, or the host's default. */
  readonly poiId: string;

  /**
   * Prompts the customer on the terminal to identify themselves. Outcomes that leave the
   * checkout without a member (not found, suspended, cancelled) resolve with the matching
   * `IdentifyStatus`; the operation rejects only for real failures. A found member attaches to
   * the session. Available after a failed settlement, so a declined guest checkout can attach
   * a member and retry with loyalty.
   *
   * The terminal lets the shopper tap a mobile wallet pass (for example an Apple Wallet loyalty
   * pass) whenever `forceEntryModes` is omitted or does not restrict entry to `KEYED`; there is
   * no separate flag. A pass read arrives as `IdentifyResult.vasData`, absent when the terminal
   * returned none. Its `encryptedData` stays encrypted: nothing in the SDK, the host or the
   * terminal decrypts it, so a register that needs the pass contents forwards it to whoever
   * holds the merchant's VAS private key. Passing `forceEntryModes: ['KEYED']` shows the
   * on-screen entry only and returns no `vasData`.
   */
  identifyMember(options?: IdentifyOptions): Operation<IdentifyResult>;

  /**
   * POS-driven lookup by an identifier on file, with no terminal prompt. Account ids and phone
   * numbers only; an email or custom identifier rejects with `UNSUPPORTED`. The same lookup runs
   * in the background when such a member is attached through `member.set()`.
   */
  identifyMember(pending: { resolver: MemberIdResolver }): Operation<IdentifyResult>;

  /** Reads card data without initiating a payment. */
  acquireCard(options?: CardAcquisitionOptions): Operation<CardAcquisitionResult>;

  /** Prompts for a digit string, e.g. a ZIP code. */
  requestDigitString(prompt: string, options?: InputOptions): Operation<string>;

  /** Prompts for a decimal amount, e.g. a tip. */
  requestDecimalString(prompt: string, options?: InputOptions): Operation<Money>;

  /** Prompts for free text, e.g. an email address. */
  requestTextString(prompt: string, options?: InputOptions): Operation<string>;

  /** Prompts for a yes/no confirmation. */
  requestConfirmation(prompt: string, options?: ConfirmationOptions): Operation<boolean>;

  /** Prompts the customer to pick from a menu. */
  requestMenuEntry(
    prompt: string,
    entries: readonly string[],
    options?: MenuOptions,
  ): Operation<MenuSelection>;

  /** Captures a handwritten signature. */
  requestSignature(prompt: string): Operation<Signature>;

  /** Asks the customer to confirm an amount. */
  requestAmountConfirmation(amount: Money, prompt: string): Operation<boolean>;

  /** Captures and encrypts a PIN on the secure PIN pad. */
  requestPinEntry(options?: PinOptions): Operation<PinResult>;

  /** Captures a PIN and verifies it, returning the encrypted block. */
  requestPinVerify(options?: PinOptions): Operation<PinResult>;

  /** Verifies a PIN without returning the block. */
  requestPinVerifyOnly(options?: PinOptions): Operation<PinResult>;

  /**
   * Registers the gift card charged as part of a split tender during `settle()`, or clears it
   * with `null`. A bare card number is treated as keyed. Dropped by `basket.clear()`.
   */
  setStoredValueCard(card: StoredValueCard | string | null): Promise<void>;

  storedValueBalance(card: StoredValueCard): Operation<StoredValueBalance>;

  /** Activates a card, optionally loading an initial balance; `"0"` activates without funds. */
  storedValueActivate(
    card: StoredValueCard,
    initialAmount: Money,
  ): Operation<StoredValueOperationResult>;
  storedValueLoad(card: StoredValueCard, amount: Money): Operation<StoredValueOperationResult>;
  storedValueUnload(card: StoredValueCard, amount: Money): Operation<StoredValueOperationResult>;

  /** Permanently deactivates a card; not every provider supports it. */
  storedValueDeactivate(card: StoredValueCard): Operation<StoredValueOperationResult>;

  /** Reserves an amount; provider support varies. */
  storedValueReserve(card: StoredValueCard, amount: Money): Operation<StoredValueOperationResult>;

  /** Reverses a prior stored value operation by its terminal reference. */
  storedValueReverse(
    originalPoiTransactionId: string,
    originalPoiTransactionTimestamp?: Date,
  ): Operation<StoredValueOperationResult>;

  /** Requests a replacement card; provider support varies. */
  storedValueDuplicate(card: StoredValueCard): Operation<StoredValueOperationResult>;

  /**
   * Runs the settlement: refund allocations, rebate redemption, point redemption, stored value
   * fulfillment and tender, card charge, award — or only the signed difference with
   * `settlementType: "NET"`. The session must be open with a non-empty, unconsumed basket.
   * Handlers steer the sequence as the `SettlementFlow` handlers do in Java; each is consulted
   * within a deadline, after which its default applies (see `StepInfo`). Rejects with a
   * `SessionError` whose `code` is `ABORTED` after an abort, with `abandonedSettlement` set
   * after an `ABANDON` recovery.
   */
  settle(options?: SettleOptions): Operation<SettlementResult>;

  /**
   * Full (no `amount`) or partial linked refund of this session's completed payment, also
   * reversing the loyalty award best-effort. After a split tender this references the card leg;
   * use `voidTransaction()` to reverse both legs.
   */
  refund(amount?: Money, handlers?: ReversalHandlers): Operation<RefundResult>;

  /** An unlinked refund, not tied to a prior transaction; payment only. */
  refundUnlinked(amount: Money, handlers?: ReversalHandlers): Operation<RefundResult>;

  /**
   * Reverses this session's completed payment — every movement it committed, money legs first,
   * then redemption, rebate and award — or, with `originalSale`, a prior sale by its persisted
   * record. A retried void resumes at the first movement still standing; until it succeeds the
   * session refuses `basket.clear()`, another settlement and `end()`.
   */
  voidTransaction(
    originalSale?: OriginalSaleRecord,
    handlers?: ReversalHandlers,
  ): Operation<VoidResult>;

  /** Checks the status of a prior request by the `ServiceID` it was sent with. */
  getTransactionStatus(
    originalServiceId: string,
    options?: TransactionStatusOptions,
  ): Operation<TransactionStatusResult>;

  /** Refreshes the customer display from a basket snapshot, or shows a custom payload. */
  updateDisplay(content: Basket | DisplayPayload): Operation<void>;

  /**
   * Replaces the content of the input prompt currently awaiting a response, e.g. a countdown.
   * Never queues; rejects with `INVALID_STATE` when no input is in progress.
   */
  updateInputDisplay(display: DisplayPayload): Operation<void>;

  /**
   * Aborts whatever operation is in flight; a no-op when nothing is. See `Operation.abort()` for
   * what an abort does to each kind of operation.
   */
  abort(): Promise<void>;

  /**
   * Ends the session: tells the terminal to discard its session-scoped data. Refused while money
   * is in flight, while a failed payment's rollback is incomplete, while a void is partially
   * complete, or while refund allocations from a failed settlement have committed; finish the
   * unwind with `voidTransaction()` or retry `settle()` first, or `forceEnd()`.
   */
  end(): Promise<void>;

  /**
   * Irrevocably abandons the session and its recovery progress, bypassing the guards that make
   * `end()` refuse, for when recovery cannot be completed and the incident is recorded for
   * reconciliation. Still refused while money is actively moving. The session is sealed either
   * way; a failed terminal end signal rejects but cannot be retried.
   */
  forceEnd(reason: string): Promise<void>;

  /**
   * The device and admin operations of this session's terminal. They run on their own exchange,
   * so a connectivity check mid-payment does not queue behind the payment.
   */
  terminal(): Terminal;
}
