import {
  SessionError,
  type Basket,
  type BasketLineItem,
  type BiltPos,
  type IdentifyResult,
  type Member,
  type Money,
  type Operation,
  type Receipt,
  type Reward,
  type SettlementFailure,
  type SettlementRecovery,
  type SettlementRecoveryAction,
  type SettlementResult,
  type ShopperSession,
  type StoredValueCard,
  type TerminalShopperSession,
} from '@bilt/pos-sdk';
import type { LocalBridgeOptions } from '@bilt/pos-sdk/bridge';
import { isCustomSku, nextCustomSku, customItem, toBasketItem, type Product } from '../catalog';
import { storedValueCard, GIFT_CARD_SKU } from '../gift-cards';
import type { LineLog } from '../log';
import { describeError, SESSION_EVENT_TYPES, summarize } from '../log';
import { cents, formatMinor, money, recomputeTotal } from '../money';
import { originalSaleRecord, toSaleRecord, type RefundRecord } from '../store/sale-record';
import { defaultReversalDecision, reversalProgress } from '../store/reversals';
import type { SaleStore } from '../store/sales-store';
import {
  fullRefundRecord,
  planAllocations,
  planReturn,
  requiredRefundMinor,
  returnLine,
  settledReturns,
  toStoredSaleUi,
  type PendingReturn,
} from './returns';
import {
  INITIAL_STATE,
  RECOVERY_ACTIONS,
  memberHeadline,
  type BasketLine,
  type EmulatorController,
  type EmulatorState,
  type LoyaltyOptions,
  type MemberIdentity,
  type MemberRewardUi,
  type PaymentOutcome,
  type PaymentRecoveryAction,
  type StoredValueOptions,
} from './state';

/** What the controller needs to know about the lane; the Settings fields of the connection card. */
export interface LaneConfig {
  readonly mode: 'terminal' | 'local';
  readonly poiId: string;
  readonly saleId: string;
  readonly currency: string;
  readonly storeLocation: string;
}

export interface ControllerOptions {
  /** Opens the connection; the default goes over `localBridge()`. */
  readonly connect: (options: LocalBridgeOptions) => Promise<BiltPos>;
  readonly sales: SaleStore;
  /** The Events feed: one curated line per thing the operator did or the terminal answered. */
  readonly events: LineLog;
  /** The Detailed feed: failures with their causes and the chatter the Events feed leaves out. */
  readonly detailed: LineLog;
  /** The Protocol feed: HTTP requests to the bridge and the session events it streams back. */
  readonly protocol: LineLog;
}

interface PendingGiftCard {
  readonly reference: string;
  readonly card: StoredValueCard;
}

const MAX_BODY = 1500;

function truncate(text: string): string {
  return text.length > MAX_BODY ? `${text.slice(0, MAX_BODY)}… (${text.length} chars)` : text;
}

function receiptText(customer?: Receipt, merchant?: Receipt): string | undefined {
  const receipt = customer ?? merchant;
  return receipt?.plainText ?? receipt?.html;
}

function isTerminal(session: ShopperSession | null): session is TerminalShopperSession {
  return session !== null && session.kind === 'terminal';
}

/** Parses a user-entered amount without silently rounding it, as the desktop's `requireMoney`. */
function requireMoney(amount: string, field: string, allowZero: boolean): number {
  const value = amount.trim();
  if (!/^\d+(\.\d{1,2})?$/.test(value)) {
    throw new Error(`${field} must be a decimal amount with at most 2 places`);
  }
  const minor = cents(value);
  if (allowZero ? minor < 0 : minor <= 0) {
    throw new Error(`${field} must be ${allowZero ? 'zero or positive' : 'positive'}`);
  }
  return minor;
}

const REWARD_EXPIRY = new Intl.DateTimeFormat(undefined, {
  month: 'short',
  day: 'numeric',
  year: 'numeric',
});

function rewardToUi(reward: Reward): MemberRewardUi {
  const expires = reward.expirationDate ? new Date(reward.expirationDate) : null;
  return {
    rewardRef: reward.rewardRef || null,
    kind: reward.type ?? 'UNKNOWN',
    description: reward.description ?? '',
    ...(expires && !Number.isNaN(expires.getTime())
      ? { expiresAtLabel: REWARD_EXPIRY.format(expires) }
      : {}),
  };
}

function identifyToUi(result: IdentifyResult): MemberIdentity {
  switch (result.status) {
    case 'FOUND':
      return result.memberId
        ? {
            kind: 'found',
            memberId: result.memberId,
            ...(result.loyaltyBrand ? { loyaltyBrand: result.loyaltyBrand } : {}),
            pointBalance: result.pointBalance > 0 ? result.pointBalance : null,
            rewards: result.rewards.map(rewardToUi),
            retained: false,
          }
        : { kind: 'failed', detail: 'The terminal reported a member with no loyalty id' };
    case 'NOT_FOUND':
    case 'SUSPENDED':
    case 'CANCELLED':
      return { kind: 'absent', reason: result.status };
    default:
      return { kind: 'failed' };
  }
}

function memberToUi(member: Member | null): MemberIdentity | null {
  if (!member?.resolved || !member.memberId) return null;
  return {
    kind: 'found',
    memberId: member.memberId,
    ...(member.loyaltyBrand ? { loyaltyBrand: member.loyaltyBrand } : {}),
    pointBalance: member.pointBalance > 0 ? member.pointBalance : null,
    rewards: member.rewards.map(rewardToUi),
    retained: true,
  };
}

/**
 * The browser port of the desktop `NexoEmulatorController`: the same actions with the same
 * guards, log lines and outcome popups, driven through `@bilt/pos-sdk` over the Terminal Bridge
 * instead of a Nexo connection. One checkout session at a time, started and ended explicitly;
 * full refunds run on a fresh session of their own, as on the desktop.
 */
export class BrowserEmulatorController implements EmulatorController {
  private state: EmulatorState = INITIAL_STATE;
  private readonly listeners = new Set<() => void>();
  private config: LaneConfig;
  private bridgeOptions: LocalBridgeOptions = {};
  private pos: BiltPos | null = null;
  /** Bumped on every connect and disconnect, so a late callback knows its connection is gone. */
  private generation = 0;
  private session: ShopperSession | null = null;
  private sessionOffs: (() => void)[] = [];
  private startClaimed = false;
  private operationClaimed = false;
  private pendingGiftCards: readonly PendingGiftCard[] = [];
  private pendingReturns: readonly PendingReturn[] = [];
  private refundSession: TerminalShopperSession | null = null;
  private refundAbortRequested = false;
  private resolveRecovery: ((action: PaymentRecoveryAction) => void) | null = null;
  private unsubscribeSales: (() => void) | null = null;
  /** Serializes the keypad's basket edits, so keystrokes land in the order they were typed. */
  private keypadQueue: Promise<unknown> = Promise.resolve();
  private scanQueue: Promise<unknown> = Promise.resolve();
  /** True once the operator disconnected by hand; the page then stops auto-connecting. */
  disconnectedByOperator = false;

  constructor(
    private readonly options: ControllerOptions,
    config: LaneConfig,
  ) {
    this.config = config;
    this.state = { ...INITIAL_STATE, mode: config.mode };
  }

  /** Starts following the sale store; `dispose()` undoes it and drops the connection. */
  start(): void {
    this.unsubscribeSales?.();
    this.unsubscribeSales = this.options.sales.subscribe(() => this.refreshSales());
    this.refreshSales();
  }

  getState = (): EmulatorState => this.state;

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  /** Takes new lane settings; they apply from the next Start Checkout. */
  configure(config: LaneConfig): void {
    this.config = config;
    if (this.session === null) this.update({ mode: config.mode });
  }

  /** Where the next `connect()` reaches the bridge. */
  setBridge(options: LocalBridgeOptions): void {
    this.bridgeOptions = options;
  }

  dispose(): void {
    this.unsubscribeSales?.();
    this.unsubscribeSales = null;
    this.teardown();
  }

  private update(patch: Partial<EmulatorState>): void {
    this.state = { ...this.state, ...patch };
    for (const listener of this.listeners) listener();
  }

  private log(message: string): void {
    this.options.events.add(message);
    this.options.detailed.add(message);
  }

  private detailedLog(message: string): void {
    this.options.detailed.add(message);
  }

  private fail(context: string, error: unknown): void {
    this.log(`${context}: ${describeError(error)}`);
    if (error instanceof Error && error.stack) this.detailedLog(error.stack);
  }

  private outcome(outcome: PaymentOutcome): void {
    this.update({ paymentOutcome: outcome });
  }

  /** The checkout-scoped fields, reset for a fresh or ended checkout. */
  private checkoutCleared(sessionId: string | null, keepLastPayment: boolean): void {
    this.update({
      sessionId,
      basket: [],
      basketTotal: '0.00',
      basketTax: '0.00',
      member: null,
      mode: this.config.mode,
      ...(keepLastPayment ? {} : { lastPayment: null }),
    });
  }

  // ---- connection -------------------------------------------------------------------------

  /** `fetch` for the bridge, mirrored into the Protocol feed: the browser's Nexo tab. */
  private tracingFetch = async (
    input: RequestInfo | URL,
    init?: RequestInit,
  ): Promise<Response> => {
    const method = init?.method ?? (input instanceof Request ? input.method : 'GET');
    const href = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    let path = href;
    try {
      const url = new URL(href, 'http://localhost');
      path = url.pathname + url.search;
    } catch {
      // keep the raw href
    }
    const body = typeof init?.body === 'string' ? ` ${truncate(init.body)}` : '';
    this.options.protocol.add(`→ ${method} ${path}${body}`);
    const started = Date.now();
    try {
      const response = await fetch(input, init);
      const text = await response
        .clone()
        .text()
        .catch(() => '');
      this.options.protocol.add(
        `← ${response.status} ${method} ${path} (${Date.now() - started} ms)${text ? ` ${truncate(text)}` : ''}`,
      );
      return response;
    } catch (error) {
      this.options.protocol.add(`✕ ${method} ${path}: ${describeError(error)}`);
      throw error;
    }
  };

  connect(): void {
    if (this.pos || this.state.connection.phase === 'CONNECTING') return;
    this.disconnectedByOperator = false;
    const generation = ++this.generation;
    this.update({ connection: { phase: 'CONNECTING' } });
    const where = this.bridgeOptions.host
      ? `http://${this.bridgeOptions.host}:${this.bridgeOptions.port ?? ''}`
      : `127.0.0.1:${this.bridgeOptions.port ?? 48333}`;
    this.log(`Connecting to the Terminal Bridge (${where})`);
    this.options.connect({ ...this.bridgeOptions, fetch: this.tracingFetch }).then(
      async (pos) => {
        if (generation !== this.generation) {
          void pos.close();
          return;
        }
        this.pos = pos;
        let detail = 'bridge connected';
        try {
          const health = await pos.health();
          const terminal = health.terminal;
          detail =
            this.config.mode === 'local'
              ? `bridge ${health.hostVersion} · local sessions`
              : `bridge ${health.hostVersion} · ${
                  !terminal
                    ? 'no terminal configured'
                    : terminal.reachable === false
                      ? 'terminal unreachable'
                      : terminal.reachable
                        ? 'terminal reachable'
                        : 'terminal listed'
                }`;
        } catch (error) {
          this.detailedLog(`Health check failed: ${describeError(error)}`);
        }
        if (generation !== this.generation) return;
        this.update({ connection: { phase: 'CONNECTED', detail } });
        this.log(`Bridge connected (${detail})`);
      },
      (error: unknown) => {
        if (generation !== this.generation) return;
        this.update({ connection: { phase: 'ERROR', detail: describeError(error) } });
        this.fail('Bridge unreachable', error);
      },
    );
  }

  disconnect(): void {
    this.disconnectedByOperator = true;
    this.teardown();
  }

  private teardown(): void {
    ++this.generation;
    const pos = this.pos;
    const session = this.session;
    this.pos = null;
    this.detachSession();
    this.resolveRecovery?.('ABORT');
    this.pendingGiftCards = [];
    this.pendingReturns = [];
    this.operationClaimed = false;
    this.startClaimed = false;
    this.update({
      connection: { phase: 'DISCONNECTED' },
      paymentInProgress: false,
      cardReadInProgress: false,
      storedValueInProgress: false,
      identifyInProgress: false,
      refundInProgress: false,
      paymentOutcome: null,
      paymentRecovery: null,
    });
    this.checkoutCleared(null, false);
    if (session?.state === 'open') {
      void session.end().catch(() => session[Symbol.asyncDispose]());
    }
    if (pos) {
      void pos.close().catch(() => undefined);
      this.log('Disconnected');
    }
  }

  // ---- checkout ---------------------------------------------------------------------------

  private attachSession(session: ShopperSession): void {
    this.detachSession();
    this.session = session;
    this.sessionOffs = SESSION_EVENT_TYPES.map((type) =>
      session.on(type, (payload) =>
        this.options.protocol.add(`⇠ ${type} ${summarize(type, payload)}`),
      ),
    );
    this.sessionOffs.push(
      session.on('basket.changed', (change) => {
        if (this.session === session) this.publishBasket(change.current);
      }),
      session.on('background.error', (error) =>
        this.log(`Customer display update failed: ${error.message}`),
      ),
      session.on('session.ended', ({ forced }) => {
        if (this.session !== session || !forced) return;
        this.detachSession();
        this.clearPendingReturns();
        this.pendingGiftCards = [];
        this.checkoutCleared(null, true);
        this.log('Checkout session force-ended by the host');
      }),
    );
  }

  private detachSession(): void {
    for (const off of this.sessionOffs) off();
    this.sessionOffs = [];
    this.session = null;
  }

  private checkout(): ShopperSession | null {
    if (!this.session) {
      this.log('No active checkout session — press Start Checkout first');
      return null;
    }
    return this.session;
  }

  private terminalCheckout(): TerminalShopperSession | null {
    const session = this.checkout();
    if (session && !isTerminal(session)) {
      this.log('This checkout is a local session — terminal operations need a terminal');
      return null;
    }
    return session as TerminalShopperSession | null;
  }

  private claim(): boolean {
    if (this.operationClaimed) {
      this.log('Another operation is already in progress');
      return false;
    }
    this.operationClaimed = true;
    return true;
  }

  startSession(identifyOnStart: boolean): void {
    const pos = this.pos;
    if (!pos) {
      this.log('Not connected — connect before starting a session');
      return;
    }
    if (this.session) {
      this.log('A checkout session is already active — end it first');
      return;
    }
    if (this.state.refundInProgress) {
      this.log('A refund is in progress — wait for it to finish');
      return;
    }
    if (this.startClaimed) {
      this.log('A checkout session is already being started');
      return;
    }
    this.startClaimed = true;
    const generation = this.generation;
    const { mode, poiId, saleId, currency, storeLocation } = this.config;
    this.log(
      mode === 'terminal'
        ? 'Starting checkout session (Start bracket)…'
        : 'Starting a local checkout session…',
    );
    const started =
      mode === 'terminal'
        ? pos.startTerminalSession(
            poiId
              ? { saleId, poiId, currency, storeLocation }
              : { saleId, currency, storeLocation },
          )
        : pos.startShopperSession({ saleId, currency, storeLocation });
    started
      .then(
        (session) => {
          if (generation !== this.generation) {
            void session.end().catch(() => undefined);
            return;
          }
          this.attachSession(session);
          this.clearPendingReturns();
          this.pendingGiftCards = [];
          this.checkoutCleared(session.id, false);
          this.publishBasket(session.basket.current);
          this.log(`Checkout session started (id ${session.id})`);
          if (identifyOnStart && isTerminal(session)) {
            this.runIdentifyPrompt(session);
          } else if (identifyOnStart) {
            this.log('Identify skipped — a local session has no terminal to prompt on');
          }
        },
        (error: unknown) => {
          if (generation === this.generation) {
            this.fail('Failed to start a checkout session', error);
          }
        },
      )
      .finally(() => {
        this.startClaimed = false;
      });
  }

  endSession(): void {
    const session = this.session;
    if (!session) {
      this.log('No active checkout session');
      return;
    }
    this.log(isTerminal(session) ? 'Ending checkout session (End bracket)…' : 'Ending checkout…');
    session.end().then(
      () => {
        if (this.session !== session) return;
        this.detachSession();
        this.clearPendingReturns();
        this.pendingGiftCards = [];
        this.checkoutCleared(null, false);
        this.log('Checkout session ended');
      },
      (error: unknown) =>
        this.fail('Failed to end the session — still active, retry or disconnect', error),
    );
  }

  private publishBasket(basket: Basket): void {
    const giftCards = new Set(this.pendingGiftCards.map((gift) => gift.reference));
    const line = (item: BasketLineItem): BasketLine => ({
      sku: item.sku,
      description: item.description,
      quantity: item.quantity,
      lineTotal: money(cents(item.adjustedTotal)),
      editablePriceMinor:
        isCustomSku(item.sku) && item.type === 'SALE' ? cents(item.unitPrice) : null,
      itemId: item.itemId,
      type: item.type,
      originalTotal: money(cents(item.originalTotal)),
      discountTotal: money(cents(item.discountTotal)),
      discountLabels: item.discounts.map((discount) => discount.label),
      giftCard: item.reference !== undefined && giftCards.has(item.reference),
    });
    this.update({
      basket: basket.items.map(line),
      basketTotal: money(cents(basket.grandTotal)),
      basketTax: money(cents(basket.taxTotal)),
    });
  }

  private mutate(context: string, action: (session: ShopperSession) => Promise<Basket>): void {
    const session = this.checkout();
    if (!session) return;
    action(session).then(
      (basket) => {
        if (this.session === session) this.publishBasket(basket);
      },
      (error: unknown) => this.fail(context, error),
    );
  }

  /**
   * Scans queue behind one another: the quantity is set from the basket as it stands, so a second
   * scan has to read it after the first one's answer or both send the same quantity.
   */
  addProduct(product: Product): void {
    const priceLabel = `$${formatMinor(product.priceMinor)}`;
    const session = this.checkout();
    if (!session) return;
    const next = this.scanQueue.then(async () => {
      const existing = session.basket.current.items.find(
        (item) => item.sku === product.sku && item.type === 'SALE',
      );
      const basket = await (existing
        ? session.basket.updateItemQuantity(existing.itemId, existing.quantity + 1)
        : session.basket.addItem(toBasketItem(product)));
      if (this.session === session) this.publishBasket(basket);
      this.log(`Added ${product.name} (${priceLabel})`);
    });
    this.scanQueue = next.catch((error: unknown) =>
      this.fail(`Failed to add ${product.name}`, error),
    );
  }

  addCustomItem(priceMinor: number): Promise<boolean> {
    const next = this.keypadQueue.then(() => this.runAddCustomItem(priceMinor));
    this.keypadQueue = next.catch(() => undefined);
    return next;
  }

  private async runAddCustomItem(priceMinor: number): Promise<boolean> {
    const session = this.checkout();
    if (!session || priceMinor <= 0) return false;
    try {
      const snapshot = session.basket.current;
      const sku = nextCustomSku(
        snapshot.cartId,
        snapshot.items.map((item) => item.sku),
      );
      this.publishBasket(
        await session.basket.addItem(customItem(sku, money(priceMinor), '', false)),
      );
      this.log(`Added custom amount $${formatMinor(priceMinor)} (${sku})`);
      return true;
    } catch (error) {
      this.fail('Failed to add the custom amount', error);
      return false;
    }
  }

  updateCustomItemPrice(sku: string, priceMinor: number): Promise<boolean> {
    const next = this.keypadQueue.then(() => this.runUpdateCustomItemPrice(sku, priceMinor));
    this.keypadQueue = next.catch(() => undefined);
    return next;
  }

  private async runUpdateCustomItemPrice(sku: string, priceMinor: number): Promise<boolean> {
    const session = this.session;
    if (!session) {
      this.log('No active checkout session — the custom amount was not applied');
      return false;
    }
    const existing = session.basket.current.items.find(
      (item) => item.sku === sku && item.type === 'SALE',
    );
    if (!existing) {
      this.detailedLog(`Custom line ${sku} is no longer in the basket — re-price ignored`);
      return false;
    }
    try {
      // The basket has no re-price mutator, so the line is replaced in one atomic batch.
      const basket = await session.basket.mutate((mutation) =>
        mutation.removeItem(existing.itemId).addItem({
          ...customItem(sku, money(priceMinor), '', false),
          quantity: existing.quantity,
        }),
      );
      this.publishBasket(basket);
      this.detailedLog(`Custom line ${sku} re-priced to $${formatMinor(priceMinor)}`);
      return true;
    } catch (error) {
      this.fail(`Failed to re-price ${sku}`, error);
      return false;
    }
  }

  removeCustomItem(sku: string): Promise<boolean> {
    const next = this.keypadQueue.then(() => this.runRemoveCustomItem(sku));
    this.keypadQueue = next.catch(() => undefined);
    return next;
  }

  private async runRemoveCustomItem(sku: string): Promise<boolean> {
    const session = this.session;
    if (!session) {
      this.log('No active checkout session — nothing was removed');
      return false;
    }
    const existing = session.basket.current.items.find(
      (item) => item.sku === sku && item.type === 'SALE',
    );
    if (!existing) {
      this.detailedLog(`Custom line ${sku} is already gone — removal ignored`);
      return true;
    }
    try {
      this.publishBasket(await session.basket.removeItem(existing.itemId));
      this.log(`Removed custom line ${sku}`);
      return true;
    } catch (error) {
      this.fail(`Failed to remove ${sku}`, error);
      return false;
    }
  }

  addGiftCardPurchase(amount: string, cardNumber: string): void {
    const session = this.checkout();
    if (!session || !this.claim()) return;
    let faceValue: number;
    try {
      faceValue = requireMoney(amount, 'gift card amount', false);
    } catch (error) {
      this.operationClaimed = false;
      this.fail('Failed to add gift card purchase', error);
      return;
    }
    const reference = `gift-card-${crypto.randomUUID()}`;
    const number = cardNumber.trim();
    const card = storedValueCard(number);
    session.basket
      .addItem({
        sku: GIFT_CARD_SKU,
        description: 'Gift card',
        quantity: 1,
        unitPrice: money(faceValue),
        reference,
      })
      .then(
        (basket) => {
          this.pendingGiftCards = [...this.pendingGiftCards, { reference, card }];
          if (this.session === session) this.publishBasket(basket);
          this.log(
            `Added gift card purchase ($${formatMinor(faceValue)}, ${
              number ? `card ${number})` : 'read on terminal at settlement)'
            }`,
          );
        },
        (error: unknown) => this.fail('Failed to add gift card purchase', error),
      )
      .finally(() => {
        this.operationClaimed = false;
      });
  }

  inquireStoredValueBalance(cardNumber: string): void {
    this.runStoredValueOperation(
      cardNumber,
      'Checking stored value balance…',
      'Balance inquiry',
      (session, card) => session.storedValueBalance(card),
      (result) => {
        const balance = money(cents(result.balance));
        return {
          title: 'Balance inquiry complete',
          message: `Available balance: $${balance} ${result.currency}`,
          event: `Stored value balance: $${balance} ${result.currency}`,
        };
      },
    );
  }

  activateStoredValue(cardNumber: string): void {
    this.runStoredValueOperation(
      cardNumber,
      'Activating stored value card…',
      'Activation',
      (session, card) => session.storedValueActivate(card, '0.00'),
      (result) => {
        const details = [
          result.currentBalance !== undefined
            ? `balance $${money(cents(result.currentBalance))} ${result.currency ?? this.config.currency}`
            : null,
          result.poiTransactionId ? `txn ${result.poiTransactionId}` : null,
        ].filter(Boolean);
        const message = `Stored value card activated${details.length ? ` (${details.join(', ')})` : ''}`;
        return { title: 'Activation complete', message, event: message };
      },
    );
  }

  private runStoredValueOperation<T>(
    cardNumber: string,
    startMessage: string,
    failureTitle: string,
    run: (session: TerminalShopperSession, card: StoredValueCard) => Operation<T>,
    describe: (result: T) => { title: string; message: string; event: string },
  ): void {
    const session = this.terminalCheckout();
    if (!session || !this.claim()) return;
    const generation = this.generation;
    this.update({ storedValueInProgress: true, paymentOutcome: null });
    this.log(startMessage);
    run(session, storedValueCard(cardNumber))
      .then(
        (result) => {
          if (generation !== this.generation) return;
          const outcome = describe(result);
          this.log(outcome.event);
          this.outcome({ success: true, title: outcome.title, message: outcome.message });
        },
        (error: unknown) => {
          if (generation !== this.generation) return;
          if (error instanceof SessionError && error.code === 'ABORTED') {
            this.log(`${failureTitle} aborted`);
            return;
          }
          this.fail(`${failureTitle} failed`, error);
          this.outcome({
            success: false,
            title: `${failureTitle} failed`,
            message:
              error instanceof SessionError
                ? `${error.code}\n${error.message}`
                : describeError(error),
          });
        },
      )
      .finally(() => {
        this.operationClaimed = false;
        if (generation === this.generation) this.update({ storedValueInProgress: false });
      });
  }

  applyCredit(itemId: string, amount: string, label: string): void {
    this.mutate('Failed to apply credit', async (session) => {
      const value = requireMoney(amount, 'credit amount', false);
      const snapshot = session.basket.current;
      const target = snapshot.items.find((item) => item.itemId === itemId);
      if (!target) throw new Error(`no basket item with itemId ${itemId}`);
      if (target.type !== 'SALE') throw new Error('credits can only be applied to sale lines');
      if (value > cents(target.adjustedTotal)) {
        throw new Error(
          `credit amount cannot exceed the line's remaining value ${target.adjustedTotal}`,
        );
      }
      const saleSubtotal = snapshot.items
        .filter((item) => item.type === 'SALE')
        .reduce((sum, item) => sum + cents(item.adjustedTotal), 0);
      const existingCredits = snapshot.items
        .filter((item) => item.type === 'CREDIT')
        .reduce((sum, item) => sum - cents(item.adjustedTotal), 0);
      const remaining = saleSubtotal - existingCredits;
      if (value > remaining) {
        throw new Error(
          `credit amount cannot exceed the basket's remaining sale value ${formatMinor(remaining)}`,
        );
      }
      const description = label.trim() || `Credit for ${target.description}`;
      const basket = await session.basket.addItem({
        type: 'CREDIT',
        sku: `CREDIT-${target.sku}`,
        description,
        quantity: 1,
        unitPrice: money(value),
        reference: `credit-${crypto.randomUUID()}`,
      });
      this.log(`Applied ${description} (−$${formatMinor(value)})`);
      return basket;
    });
  }

  applyDiscount(itemId: string, amount: string, label: string): void {
    this.mutate('Failed to apply discount', async (session) => {
      const value = requireMoney(amount, 'discount amount', true);
      const snapshot = session.basket.current;
      const target = snapshot.items.find((item) => item.itemId === itemId);
      if (!target) throw new Error(`no basket item with itemId ${itemId}`);
      if (target.type !== 'SALE') throw new Error('discounts can only be applied to sale lines');
      const saleSubtotal = snapshot.items
        .filter((item) => item.type === 'SALE' && item.itemId !== itemId)
        .reduce(
          (sum, item) => sum + cents(item.adjustedTotal),
          cents(target.originalTotal) - value,
        );
      const creditTotal = snapshot.items
        .filter((item) => item.type === 'CREDIT')
        .reduce((sum, item) => sum - cents(item.adjustedTotal), 0);
      if (creditTotal > saleSubtotal) {
        throw new Error('discount would make credits exceed the remaining sale value');
      }
      const basket = await session.basket.setDiscounts(
        itemId,
        value === 0 ? [] : [{ label: label.trim() || 'Emulator discount', amount: money(value) }],
      );
      this.log(
        value === 0
          ? `Cleared discount from ${target.description}`
          : `Applied discount to ${target.description} (−$${formatMinor(value)})`,
      );
      return basket;
    });
  }

  clearBasket(): void {
    const session = this.checkout();
    if (!session || !this.claim()) return;
    session.basket
      .clear()
      .then(
        (basket) => {
          this.pendingGiftCards = [];
          this.clearPendingReturns();
          if (this.session === session) this.publishBasket(basket);
          this.log('Basket cleared');
        },
        (error: unknown) => this.fail('Failed to clear basket', error),
      )
      .finally(() => {
        this.operationClaimed = false;
      });
  }

  // ---- loyalty and card read --------------------------------------------------------------

  identifyMember(): void {
    const session = this.terminalCheckout();
    if (session) this.runIdentifyPrompt(session);
  }

  private runIdentifyPrompt(session: TerminalShopperSession): void {
    if (!this.claim()) return;
    const generation = this.generation;
    this.update({ identifyInProgress: true, member: null });
    this.log('Loyalty sign-in on the terminal…');
    session
      .identifyMember({ forceEntryModes: ['KEYED'] })
      .then(
        (result) => {
          if (generation === this.generation && this.session === session) {
            this.publishIdentity(session, identifyToUi(result));
          }
        },
        (error: unknown) => {
          if (generation === this.generation && this.session === session) {
            this.publishIdentity(session, {
              kind: 'failed',
              ...(error instanceof Error ? { detail: describeError(error) } : {}),
            });
            if (error instanceof Error && error.stack) this.detailedLog(error.stack);
          }
        },
      )
      .finally(() => {
        this.operationClaimed = false;
        if (generation === this.generation) this.update({ identifyInProgress: false });
      });
  }

  /**
   * Reports the sign-in as the account the checkout will settle against: a cancelled or failed
   * prompt leaves an earlier identification attached, and the card says so.
   */
  private publishIdentity(session: ShopperSession, outcome: MemberIdentity): void {
    const identity =
      outcome.kind === 'found' ? outcome : (memberToUi(session.member.current) ?? outcome);
    this.update({ member: identity });
    switch (identity.kind) {
      case 'found':
        this.log(
          `Member identified: ${memberHeadline(identity)}, ${
            identity.pointBalance !== null ? `${identity.pointBalance} pts, ` : ''
          }${identity.rewards.length} reward(s)`,
        );
        break;
      case 'absent':
        this.log(`${memberHeadline(identity)} — loyalty steps will be skipped`);
        break;
      case 'failed':
        this.log(`${identity.detail ?? memberHeadline(identity)} — loyalty steps will be skipped`);
        break;
    }
  }

  acquireCard(): void {
    const session = this.terminalCheckout();
    if (!session || !this.claim()) return;
    const generation = this.generation;
    this.update({ cardReadInProgress: true });
    this.log('Reading card on the terminal (CardAcquisition, MagStripe/Scanned)…');
    session
      .acquireCard({ forceEntryModes: ['MAG_STRIPE', 'SCANNED'] })
      .then(
        (acquired) => {
          if (generation !== this.generation) return;
          const label =
            [
              acquired.paymentBrand,
              acquired.maskedPan ?? acquired.truncatedPan,
              acquired.entryMode ? `via ${acquired.entryMode}` : undefined,
            ]
              .filter(Boolean)
              .join(' ') || 'no card details returned';
          const number = acquired.rawPan?.trim();
          if (!number) {
            this.log(`Card read: ${label} — no full card number returned, type it manually`);
            return;
          }
          this.update({
            acquiredCard: { number, sequence: (this.state.acquiredCard?.sequence ?? 0) + 1 },
          });
          this.log(`Card read: ${label} — filled into the stored value field`);
        },
        (error: unknown) => {
          if (generation !== this.generation) return;
          if (error instanceof SessionError && error.code === 'ABORTED') {
            this.log('Card read aborted');
          } else {
            this.fail('Card read failed', error);
          }
        },
      )
      .finally(() => {
        this.operationClaimed = false;
        if (generation === this.generation) this.update({ cardReadInProgress: false });
      });
  }

  // ---- settlement -------------------------------------------------------------------------

  settle(loyalty: LoyaltyOptions, storedValue: StoredValueOptions | null, net: boolean): void {
    const session = this.terminalCheckout();
    if (!session || !this.claim()) return;
    const generation = this.generation;
    this.update({ paymentInProgress: true, paymentOutcome: null });
    let attempt: 'RUNNING' | 'FAILED' | 'SUCCEEDED' = 'RUNNING';
    const card = storedValue ? storedValueCard(storedValue.cardNumber) : null;
    const giftCards = this.pendingGiftCards;
    const required = requiredRefundMinor(session.basket.current, net);
    const { returns, allocations } = planAllocations(this.pendingReturns, required);
    const onOff = (on: boolean) => (on ? 'on' : 'off');
    this.log(
      `Starting ${net ? 'net ' : ''}settlement — rebates ${onOff(loyalty.rebates)}, redemption ${onOff(
        loyalty.redemption,
      )}, award ${onOff(loyalty.award)}` +
        (card ? `, gift card ${card.storedValueId ?? '(swipe on terminal)'}` : '') +
        (giftCards.length ? `, ${giftCards.length} gift card purchase(s) to load` : '') +
        (returns.length
          ? `, ${returns.length} prior sale(s) returned ($${formatMinor(required)} to refund after netting)`
          : ''),
    );
    const finish = () => {
      this.operationClaimed = false;
      if (generation === this.generation) this.update({ paymentInProgress: false });
    };
    // Set or actively cleared each attempt: the session keeps the card across a retry.
    session
      .setStoredValueCard(card)
      .then(() =>
        session.settle({
          settlementType: net ? 'NET' : 'REFUND_THEN_CHARGE',
          disableRebates: !loyalty.rebates,
          disablePoints: !loyalty.redemption,
          disableAward: !loyalty.award,
          ...(allocations.length ? { refunds: allocations } : {}),
          ...(giftCards.length
            ? {
                fulfillments: giftCards.map((gift) => ({
                  basketReference: gift.reference,
                  type: 'RELOAD' as const,
                  card: gift.card,
                })),
              }
            : {}),
          onCardRefunded: (movement) =>
            this.log(
              `Card refund committed: $${movement.amount} (txn ${movement.poiTransactionId})`,
            ),
          onGiftCardRefunded: (movement) =>
            this.log(
              `Gift card refund committed: $${movement.amount} (txn ${movement.poiTransactionId})`,
            ),
          onRebatesRedeemed: (rebates) => {
            const total = totalAfterRebates(rebates);
            this.log(`Rebates applied: −$${rebates.totalRebateAmount} → total $${total}`);
            return total;
          },
          onPointsRedeemed: (points) => {
            this.log(
              `Points redeemed: ${points.pointsUsed} (−$${points.monetaryValue}) → total $${points.suggestedTotal}`,
            );
            return points.suggestedTotal;
          },
          onGiftCardPayment: (giftCard) => {
            const balance = giftCard.remainingCardBalance
              ? ` (card balance $${giftCard.remainingCardBalance})`
              : '';
            this.log(
              `Gift card charged: $${giftCard.amountCharged}${balance} → total $${giftCard.suggestedTotal}`,
            );
            return giftCard.suggestedTotal;
          },
          onStoredValueLoaded: (movement) =>
            this.log(`Gift card loaded: $${movement.amount} (txn ${movement.poiTransactionId})`),
          onError: async (failure, info) => {
            this.log(`Settlement failed: ${failure.error.code} — ${failure.error.message}`);
            const recovery = await this.awaitPaymentRecovery(generation, failure, info.signal);
            if (recovery === 'ABORT') {
              attempt = 'FAILED';
              if (generation === this.generation) {
                this.outcome({
                  success: false,
                  title: 'Settlement failed',
                  message: `${failure.error.code}\n${failure.error.message}`,
                });
              }
            }
            return recovery;
          },
          onAbandoned: (record) => {
            attempt = 'FAILED';
            const message = `No rollback was performed. Reconcile these movements manually before settling again.\n\n${this.failureDetails(record.failure)}`;
            this.log(`Settlement abandoned: ${message}`);
            if (generation === this.generation) {
              this.outcome({ success: false, title: 'Settlement abandoned', message });
            }
          },
        }),
      )
      .then(
        (result) => {
          attempt = 'SUCCEEDED';
          // Recorded even after a disconnect: the charge stands, and an unrecorded sale could
          // never be refunded.
          this.persistSale(session, result);
          const { records, parts } = settledReturns(returns, result);
          this.pendingGiftCards = [];
          this.pendingReturns = [];
          void this.recordReturns(records).then((recorded) => {
            const returnParts = recorded
              ? parts
              : [
                  ...parts,
                  'WARNING: a return could NOT be recorded — its sale will still offer what was just refunded; refunding it again would return the money twice',
                ];
            if (generation !== this.generation) return;
            this.publishPaymentResult(result, returnParts);
            this.log('Settlement complete — ending the checkout automatically');
            this.endCompletedCheckout(session);
          });
        },
        (error: unknown) => {
          if (generation !== this.generation) return;
          if (attempt === 'RUNNING') {
            if (error instanceof SessionError && error.code === 'ABORTED') {
              this.log(
                'Settlement aborted; committed steps reversed — the basket is intact, Settle again to retry',
              );
            } else {
              this.fail('Settlement failed', error);
              this.outcome({
                success: false,
                title: 'Settlement failed',
                message:
                  error instanceof SessionError
                    ? `${error.code}\n${error.message}`
                    : describeError(error),
              });
            }
          }
        },
      )
      .finally(finish);
  }

  private failureDetails(failure: SettlementFailure): string {
    return [
      `Step: ${failure.step ?? '(none)'}`,
      `${failure.error.code}: ${failure.error.message}`,
      `Amount due: ${this.config.currency} ${failure.amountDue}`,
      failure.outcomeCertainty === 'INDETERMINATE'
        ? 'Outcome unknown — retry checks status without charging again.'
        : null,
      failure.serviceId ? `Request: ${failure.serviceId}` : null,
      ...(failure.committedMovements.length
        ? [
            'Committed movements (not yet rolled back):',
            ...failure.committedMovements.map(
              (movement) =>
                `${movement.step}: ${movement.amount} (txn ${movement.poiTransactionId ?? movement.saleTransactionId ?? '?'})`,
            ),
          ]
        : []),
    ]
      .filter((line): line is string => line !== null)
      .join('\n');
  }

  /**
   * Holds the settlement's recovery step open on the recovery dialog, offering exactly the
   * desktop's choices: Retry always; Skip for a definitive failure of an optional step that
   * committed nothing; cash for a definitive card-charge failure with an amount due; then Abort
   * and Abandon. The host's deadline answers Abort.
   */
  private awaitPaymentRecovery(
    generation: number,
    failure: SettlementFailure,
    signal: AbortSignal,
  ): Promise<SettlementRecovery | SettlementRecoveryAction> {
    if (failure.step === undefined || generation !== this.generation) {
      return Promise.resolve('ABORT');
    }
    const step = failure.step;
    const definitive = failure.outcomeCertainty === 'DEFINITIVE';
    const actions: PaymentRecoveryAction[] = ['RETRY'];
    if (
      definitive &&
      (step === 'REBATE_REDEMPTION' ||
        step === 'POINT_REDEMPTION' ||
        step === 'STORED_VALUE_CHARGE') &&
      !failure.committedMovements.some((movement) => movement.step === step)
    ) {
      actions.push('SKIP');
    }
    if (definitive && step === 'CARD_CHARGE' && cents(failure.amountDue) > 0) actions.push('CASH');
    actions.push('ABORT', 'ABANDON');
    return new Promise((resolve) => {
      const choose = (action: PaymentRecoveryAction) => {
        if (this.resolveRecovery !== choose || !actions.includes(action)) return;
        this.resolveRecovery = null;
        signal.removeEventListener('abort', onDeadline);
        this.update({ paymentRecovery: null });
        this.log(`Cashier chose ${RECOVERY_ACTIONS[action].label} for ${step}`);
        resolve(
          action === 'CASH'
            ? {
                action: 'EXTERNAL',
                externalPayment: { tenderType: 'CASH', amount: failure.amountDue },
              }
            : action,
        );
      };
      const onDeadline = () => {
        if (this.resolveRecovery !== choose) return;
        this.log('Recovery deadline passed — the host applies Abort');
        choose('ABORT');
      };
      this.resolveRecovery = choose;
      signal.addEventListener('abort', onDeadline, { once: true });
      this.update({
        paymentRecovery: { message: this.failureDetails(failure), actions, choose },
      });
    });
  }

  private endCompletedCheckout(session: ShopperSession): void {
    session.end().then(
      () => {
        if (this.session !== session) return;
        this.detachSession();
        this.checkoutCleared(null, true);
        this.log('Checkout ended');
      },
      (error: unknown) =>
        this.fail('Failed to end the checkout — press End Checkout to retry', error),
    );
  }

  private persistSale(session: TerminalShopperSession, result: SettlementResult): void {
    // A settlement that sold nothing (returns only) is not a sale: its movements live on the
    // original sales' refund history.
    if (!result.finalBasket.items.some((item) => item.type === 'SALE')) return;
    const member = session.member.current;
    const record = toSaleRecord(result, {
      sessionId: session.id,
      saleId: session.saleId,
      poiId: session.poiId,
      currency: session.currency,
      memberId: member?.resolved ? member.memberId : undefined,
      recordId: crypto.randomUUID(),
      completedAt: new Date(),
    });
    this.options.sales.recordSale(record).then(
      () => this.log(`Sale stored (${record.legs.length} transaction leg(s), id ${record.id})`),
      (error: unknown) => this.fail('Failed to store the sale', error),
    );
  }

  private async recordReturns(records: readonly RefundRecord[]): Promise<boolean> {
    let recorded = true;
    for (const record of records) {
      try {
        await this.options.sales.recordRefund(record);
      } catch (error) {
        recorded = false;
        this.fail('Failed to store the refund', error);
      }
    }
    return recorded;
  }

  private publishPaymentResult(result: SettlementResult, returnParts: readonly string[]): void {
    this.publishBasket(result.finalBasket);
    const positive = (value: Money) => cents(value) > 0;
    const parts = [
      ...returnParts,
      positive(result.externalPaymentAmount)
        ? `cash $${result.externalPaymentAmount} (register-managed; refund manually)`
        : null,
      positive(result.cardAmountCharged)
        ? `card $${result.cardAmountCharged}${result.paymentBrand ? ` (${result.paymentBrand})` : ''}`
        : null,
      positive(result.storedValueAmountUsed) ? `gift card $${result.storedValueAmountUsed}` : null,
      positive(result.storedValueLoadedAmount)
        ? `gift card loaded $${result.storedValueLoadedAmount}`
        : null,
      positive(result.totalRebateAmount) ? `rebates −$${result.totalRebateAmount}` : null,
      result.pointsRedeemed > 0
        ? `${result.pointsRedeemed} pts −$${result.pointsMonetaryValue}`
        : null,
      result.totalPointsEarned > 0
        ? `earned ${result.totalPointsEarned} pts (balance ${result.pointsBalance})`
        : null,
    ].filter((part): part is string => part !== null);
    const authorized = money(cents(result.authorizedAmount));
    const summary = `Settled $${authorized}${parts.length ? ` — ${parts.join(', ')}` : ''}`;
    const popup = [
      `Settled $${authorized}`,
      ...parts,
      ...result.promotionMessages,
      ...result.warnings.map((warning) => `Warning: ${warning}`),
    ].join('\n');
    const receipt = receiptText(result.customerReceipt, result.merchantReceipt);
    this.update({
      lastPayment: summary,
      paymentOutcome: {
        success: true,
        title: 'Settlement complete',
        message: popup,
        ...(receipt ? { receipt } : {}),
      },
    });
    this.log(summary);
    result.promotionMessages.forEach((message) => this.log(`Promo: ${message}`));
    result.warnings.forEach((warning) => this.log(`Warning: ${warning}`));
  }

  // ---- refunds ----------------------------------------------------------------------------

  private pendingQuantity = (saleId: string, sku: string): number =>
    this.pendingReturns
      .filter((pending) => pending.saleId === saleId)
      .reduce(
        (sum, pending) =>
          sum +
          pending.items
            .filter((item) => item.sku === sku)
            .reduce((n, item) => n + item.quantity, 0),
        0,
      );

  private clearPendingReturns(): void {
    if (this.pendingReturns.length > 0) {
      this.pendingReturns = [];
      this.refreshSales();
    }
  }

  private refreshSales(): void {
    this.options.sales.listSales().then(
      (sales) =>
        this.update({ sales: sales.map((sale) => toStoredSaleUi(sale, this.pendingQuantity)) }),
      (error: unknown) => this.fail('Failed to load stored sales', error),
    );
  }

  addReturnToBasket(saleId: string, skus: ReadonlySet<string>): void {
    const session = this.checkout();
    if (!session || !this.claim()) return;
    const generation = this.generation;
    this.refundAbortRequested = false;
    this.update({ refundInProgress: true });
    this.options.sales
      .findSale(saleId)
      .then(async (stored) => {
        if (!stored) {
          this.log(`Sale ${saleId} is not in the store`);
          return;
        }
        const plan = planReturn(stored, skus, (sku) => this.pendingQuantity(saleId, sku));
        if ('error' in plan) {
          this.log(plan.error);
          return;
        }
        plan.notes.forEach((note) => this.log(note));
        if (this.refundAbortRequested) {
          this.log('Return aborted — nothing was rung into the basket');
          return;
        }
        let basket = session.basket.current;
        for (const item of plan.pending.items) {
          basket = await session.basket.addItem(returnLine(item));
        }
        this.pendingReturns = [...this.pendingReturns, plan.pending];
        if (this.session === session) this.publishBasket(basket);
        this.refreshSales();
        this.log(
          `Return rung in: ${plan.pending.items
            .map((item) => `${item.quantity}× ${item.description}`)
            .join(', ')} — restores to the ${
            plan.pending.leg.type === 'STORED_VALUE' ? 'gift card' : 'card'
          } on settlement`,
        );
      })
      .catch((error: unknown) => this.fail('Failed to ring the return', error))
      .finally(() => {
        this.operationClaimed = false;
        if (generation === this.generation) this.update({ refundInProgress: false });
      });
  }

  refundSale(saleId: string): void {
    const pos = this.pos;
    if (!pos) {
      this.log('Not connected — connect before refunding');
      return;
    }
    if (this.session || this.startClaimed) {
      this.log('A checkout session is active — end it before refunding');
      return;
    }
    if (!this.claim()) return;
    const generation = this.generation;
    this.refundAbortRequested = false;
    this.update({ refundInProgress: true, paymentOutcome: null });
    this.runFullRefund(pos, saleId, generation)
      .catch((error: unknown) => {
        if (generation !== this.generation) return;
        this.fail('Refund not completed', error);
        this.outcome({
          success: false,
          title: 'Refund failed',
          message: `Refund not completed\n${describeError(error)}`,
        });
      })
      .finally(() => {
        this.operationClaimed = false;
        if (generation === this.generation) this.update({ refundInProgress: false });
      });
  }

  /**
   * The full refund of a stored sale: a void of every referenced movement still standing, on a
   * fresh terminal session bracketed around it. The legs a stopped void did reverse are
   * recorded so a retry sends only what is outstanding.
   */
  private async runFullRefund(pos: BiltPos, saleId: string, generation: number): Promise<void> {
    const stored = await this.options.sales.findSale(saleId);
    if (!stored) {
      this.log(`Sale ${saleId} is not in the store`);
      return;
    }
    const ui = toStoredSaleUi(stored, this.pendingQuantity);
    if (ui.voided || ui.fullyRefunded) {
      this.log(
        ui.voided
          ? 'The sale was voided — nothing left to refund'
          : 'The sale was already refunded in full — nothing left to refund',
      );
      return;
    }
    if (!ui.fullRefundAvailable) {
      this.log('The sale was already partially refunded — refund the remaining items instead');
      return;
    }
    const { sale } = stored;
    const hasCash = cents(sale.externalPaymentAmount) > 0;
    this.log(
      `Starting full refund of sale ${sale.id.slice(0, 8)} — voiding every movement of the prior sale (${
        sale.legs.length + sale.giftCardLoads.length
      } reference(s))…`,
    );
    const session = await pos.startTerminalSession({
      saleId: sale.saleId,
      poiId: sale.poiId,
      currency: sale.currency,
      storeLocation: this.config.storeLocation,
    });
    this.refundSession = session;
    try {
      if (this.refundAbortRequested) {
        this.log('Refund aborted before any money moved');
        this.outcome({
          success: false,
          title: 'Refund aborted',
          message: 'Refund aborted before any money moved',
        });
        return;
      }
      try {
        const result = await session.voidTransaction(originalSaleRecord(stored), {
          onError: (step, error) => {
            this.log(`Refund step ${step ?? '(none ran)'} failed: ${error.message}`);
            return defaultReversalDecision(step);
          },
        });
        let recorded = true;
        try {
          await this.options.sales.recordRefund(fullRefundRecord(stored, result));
        } catch (error) {
          recorded = false;
          this.fail('Failed to store the refund', error);
        }
        const parts = [
          `${hasCash ? 'Terminal movements reversed' : 'Refunded'}${
            result.reversedAmount !== undefined ? ` $${money(cents(result.reversedAmount))}` : ''
          }`,
          hasCash
            ? `Return ${sale.currency} ${sale.externalPaymentAmount} cash manually; the terminal did not reimburse it.`
            : null,
          result.pointsReversed > 0
            ? `reversed ${result.pointsReversed} pts (balance ${result.remainingPointBalance})`
            : null,
          recorded
            ? null
            : 'WARNING: the refund could NOT be recorded — the sale will still offer what was just refunded; refunding it again would return the money twice',
        ].filter((part): part is string => part !== null);
        this.log(parts.join(', '));
        if (generation === this.generation) {
          const receipt = receiptText(result.customerReceipt, result.merchantReceipt);
          this.outcome({
            success: true,
            title: hasCash ? 'Terminal refund complete' : 'Refund complete',
            message: parts.join('\n'),
            ...(receipt ? { receipt } : {}),
          });
        }
      } catch (error) {
        if (!(error instanceof SessionError)) throw error;
        let warning = '';
        const progress = reversalProgress(stored, error.reversedMovements);
        try {
          for (const record of progress) await this.options.sales.recordRefund(record);
          if (progress.length) {
            this.log(
              `${progress.length} movement(s) reversed before the failure — recorded; retrying the full refund covers only what is outstanding`,
            );
          }
        } catch (cause) {
          this.fail('Failed to store the reversal progress', cause);
          warning =
            '\nWARNING: a reversed movement could NOT be recorded — retrying the full refund would reverse it again';
        }
        if (generation !== this.generation) return;
        this.log(
          error.code === 'ABORTED'
            ? 'Refund aborted — the interrupted movement was not committed'
            : `Refund failed: ${error.code} — ${error.message}`,
        );
        this.outcome({
          success: false,
          title: 'Refund failed',
          message: `${error.code}\n${error.message}${warning}`,
        });
      }
    } finally {
      this.refundSession = null;
      await session.end().catch(() => session.forceEnd('emulator refund cleanup'));
    }
  }

  abort(): void {
    if (this.state.paymentRecovery) {
      this.state.paymentRecovery.choose('ABORT');
      return;
    }
    if (!this.pos) {
      this.log('Nothing to abort');
      return;
    }
    if (this.state.refundInProgress || this.refundSession) {
      this.refundAbortRequested = true;
      this.log('Aborting the refund…');
      this.refundSession?.abort().catch((error: unknown) => this.fail('Abort failed', error));
      return;
    }
    const session = this.session;
    if (!session) {
      this.log('No active checkout session');
      return;
    }
    if (!isTerminal(session)) {
      this.log('Nothing to abort on a local session');
      return;
    }
    this.log('Aborting…');
    session.abort().catch((error: unknown) => this.fail('Abort failed', error));
  }

  dismissPaymentOutcome(): void {
    this.update({ paymentOutcome: null });
  }
}

/**
 * The amount to charge once rebates are redeemed: the host's `suggestedTotal` keeps the tax of the
 * price before the rebates, so the register re-taxes the updated basket as the desktop does.
 */
export function totalAfterRebates(rebates: { readonly updatedBasket: Basket }): Money {
  return recomputeTotal(rebates.updatedBasket);
}
