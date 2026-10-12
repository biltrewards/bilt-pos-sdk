// An in-memory double of the public surface, enough to run the design's integration sketch
// end to end. It is deliberately simple: one basket, one member, handlers invoked with fixture
// data, events emitted on demand through `emit`. It proves the interfaces compose; it is not
// the SDK core.
import type {
  Basket,
  BasketDiscount,
  BasketItem,
  BasketLineItem,
  CheckoutPhase,
  Cta,
  IdentifyOptions,
  IdentifyResult,
  VasData,
  Member,
  MemberIdResolver,
  MemberInput,
  Money,
  Rendering,
  SessionContext,
  SessionEventPayload,
  SessionEventType,
} from '@bilt/pos-protocol';
import type { SessionError } from '../src/index';
import {
  type BasketMutationBuilder,
  type BiltPos,
  type EngineCapabilities,
  type Operation,
  type OperationStatus,
  type OperationType,
  type RetailMediaWidget,
  type SessionBasket,
  type SessionContextApi,
  type SessionEventHandler,
  type SessionMember,
  type SettleOptions,
  type ShopperSession,
  type ShopperSessionOptions,
  type Terminal,
  type TerminalSessionOptions,
  type TerminalShopperSession,
  type Unsubscribe,
  type WidgetHandle,
  type WidgetType,
} from '../src/index';
import * as fx from './fixtures';

let operationCounter = 0;

function operation<T>(type: OperationType, run: () => Promise<T>): Operation<T> {
  let status: OperationStatus = 'running';
  const promise = run().then(
    (value) => {
      status = 'succeeded';
      return value;
    },
    (error: unknown) => {
      status = 'failed';
      throw error;
    },
  );
  const handle = Object.defineProperties(promise, {
    id: { value: `op_${++operationCounter}`, enumerable: true },
    type: { value: type, enumerable: true },
    status: { get: () => status, enumerable: true },
    abort: { value: async () => undefined, enumerable: true },
  });
  return handle as Operation<T>;
}

/**
 * The register-side fields of a line, so a patched line is recomputed from them rather than from
 * its derived totals. A line's `taxAmount` is always filled in; it is the register's own fixed
 * amount only on a line without a rate (`setTaxAmount` clears the rate, `setTaxRate` the amount),
 * and a fixed amount survives a quantity or discount edit, as on the host.
 */
function toItem(line: BasketLineItem): BasketItem {
  return {
    sku: line.sku,
    description: line.description,
    quantity: line.quantity,
    unitPrice: line.unitPrice,
    discounts: line.discounts,
    type: line.type,
    metadata: line.metadata,
    ...(line.reference === undefined ? {} : { reference: line.reference }),
    ...(line.category === undefined ? {} : { category: line.category }),
    ...(line.taxRate === undefined ? {} : { taxRate: line.taxRate }),
    ...(line.taxRate === undefined && Number(line.taxAmount) !== 0
      ? { taxAmount: line.taxAmount }
      : {}),
  };
}

/** The pass the double's terminal reports; the payload is opaque hex, never decrypted. */
export const MOCK_VAS_DATA: VasData = {
  source: 'ApplePay',
  merchantId: 'VerifoneTestRix2',
  services: [
    {
      serviceId: 'pass.com.biltrewards.loyalty',
      serviceType: 'Coupon1',
      statusWord: '9000',
      encryptedData: '8ff4b4de0c11a7',
      cipherTimestamp: '3006a261',
    },
  ],
};

class Emitter {
  private readonly handlers = new Map<
    SessionEventType,
    Set<SessionEventHandler<SessionEventType>>
  >();

  on<T extends SessionEventType>(type: T, handler: SessionEventHandler<T>): Unsubscribe {
    const set = this.handlers.get(type) ?? new Set();
    set.add(handler as unknown as SessionEventHandler<SessionEventType>);
    this.handlers.set(type, set);
    return () => this.off(type, handler);
  }

  once<T extends SessionEventType>(type: T, handler: SessionEventHandler<T>): Unsubscribe {
    const off = this.on(type, (payload) => {
      off();
      handler(payload);
    });
    return off;
  }

  off<T extends SessionEventType>(type: T, handler: SessionEventHandler<T>): void {
    this.handlers.get(type)?.delete(handler as unknown as SessionEventHandler<SessionEventType>);
  }

  emit<T extends SessionEventType>(type: T, payload: SessionEventPayload<T>): void {
    for (const handler of this.handlers.get(type) ?? []) {
      (handler as unknown as SessionEventHandler<T>)(payload);
    }
  }
}

class MockBasket implements SessionBasket {
  current: Basket = fx.basket([]);

  constructor(private readonly emitter: Emitter) {}

  private commit(
    next: Basket,
    source: 'INCREMENTAL' | 'BATCH' | 'REPLACE' | 'CLEAR',
  ): Promise<Basket> {
    const previous = this.current;
    this.current = next;
    this.emitter.emit('basket.changed', {
      previous,
      current: next,
      source,
      added: next.items.filter((l) => !previous.items.some((p) => p.itemId === l.itemId)),
      removed: previous.items.filter((l) => !next.items.some((n) => n.itemId === l.itemId)),
      quantityChanged: [],
      priceChanged: [],
      discountsChanged: [],
      taxChanged: [],
      detailsChanged: [],
      taxTotalChanged: previous.taxTotal !== next.taxTotal,
    });
    return Promise.resolve(next);
  }

  refresh(): Promise<Basket> {
    return Promise.resolve(this.current);
  }

  addItem(item: BasketItem, itemId?: string): Promise<Basket> {
    const line = itemId === undefined ? fx.lineItem(item) : fx.lineItem(item, itemId);
    return this.commit(
      fx.basket([...this.current.items, line], this.current.cartId),
      'INCREMENTAL',
    );
  }

  removeItem(itemId: string): Promise<Basket> {
    return this.commit(
      fx.basket(
        this.current.items.filter((l) => l.itemId !== itemId),
        this.current.cartId,
      ),
      'INCREMENTAL',
    );
  }

  removeItemBySku(sku: string): Promise<Basket> {
    return this.commit(
      fx.basket(
        this.current.items.filter((l) => l.sku !== sku),
        this.current.cartId,
      ),
      'INCREMENTAL',
    );
  }

  updateItemQuantity(itemId: string, quantity: number): Promise<Basket> {
    if (quantity === 0) return this.removeItem(itemId);
    return this.patchLine((l) => l.itemId === itemId, { quantity });
  }

  updateItemQuantityBySku(sku: string, quantity: number): Promise<Basket> {
    const line = this.current.items.find((l) => l.sku === sku);
    return line ? this.updateItemQuantity(line.itemId, quantity) : Promise.resolve(this.current);
  }

  /** Rebuilds one line with a patch, as the host would; an unknown line leaves the basket untouched. */
  private patchLine(
    match: (line: BasketLineItem) => boolean,
    patch: Partial<BasketItem>,
    clear?: 'taxRate' | 'taxAmount',
  ): Promise<Basket> {
    const items = this.current.items.map((l) => {
      if (!match(l)) return l;
      const item: BasketItem = { ...toItem(l), ...patch };
      if (clear) delete item[clear];
      return fx.lineItem(item, l.itemId);
    });
    return this.commit(fx.basket(items, this.current.cartId), 'INCREMENTAL');
  }

  setDiscounts(itemId: string, discounts: readonly BasketDiscount[]): Promise<Basket> {
    return this.patchLine((l) => l.itemId === itemId, { discounts: [...discounts] });
  }
  setDiscountsBySku(sku: string, discounts: readonly BasketDiscount[]): Promise<Basket> {
    return this.patchLine((l) => l.sku === sku, { discounts: [...discounts] });
  }
  setTaxRate(itemId: string, rate: Money): Promise<Basket> {
    return this.patchLine((l) => l.itemId === itemId, { taxRate: rate }, 'taxAmount');
  }
  setTaxRateBySku(sku: string, rate: Money): Promise<Basket> {
    return this.patchLine((l) => l.sku === sku, { taxRate: rate }, 'taxAmount');
  }
  setTaxAmount(itemId: string, amount: Money): Promise<Basket> {
    return this.patchLine((l) => l.itemId === itemId, { taxAmount: amount }, 'taxRate');
  }
  setTaxAmountBySku(sku: string, amount: Money): Promise<Basket> {
    return this.patchLine((l) => l.sku === sku, { taxAmount: amount }, 'taxRate');
  }

  setTaxTotal(amount: Money | null): Promise<Basket> {
    const next = { ...this.current, taxTotal: amount ?? this.current.taxTotal };
    return this.commit(next, 'INCREMENTAL');
  }

  mutate(build: (mutation: BasketMutationBuilder) => void): Promise<Basket> {
    const items = [...this.current.items];
    const builder: BasketMutationBuilder = {
      addItem: (item, itemId) => {
        items.push(itemId === undefined ? fx.lineItem(item) : fx.lineItem(item, itemId));
        return builder;
      },
      removeItem: (itemId) => {
        const index = items.findIndex((l) => l.itemId === itemId);
        if (index >= 0) items.splice(index, 1);
        return builder;
      },
      removeItemBySku: (sku) => {
        const index = items.findIndex((l) => l.sku === sku);
        if (index >= 0) items.splice(index, 1);
        return builder;
      },
      updateItemQuantity: () => builder,
      updateItemQuantityBySku: () => builder,
      setDiscounts: () => builder,
      setDiscountsBySku: () => builder,
      setTaxRate: () => builder,
      setTaxRateBySku: () => builder,
      setTaxAmount: () => builder,
      setTaxAmountBySku: () => builder,
      setTaxTotal: () => builder,
    };
    build(builder);
    return this.commit(fx.basket(items, this.current.cartId), 'BATCH');
  }

  replace(content: Basket | readonly BasketItem[]): Promise<Basket> {
    const next = Array.isArray(content)
      ? fx.basket(
          (content as readonly BasketItem[]).map((item) => fx.lineItem(item)),
          this.current.cartId,
        )
      : (content as Basket);
    return this.commit(next, 'REPLACE');
  }

  clear(): Promise<Basket> {
    return this.commit(fx.basket([], `cart_${Date.now()}`), 'CLEAR');
  }
}

class MockMember implements SessionMember {
  current: Member | null = null;

  constructor(private readonly emitter: Emitter) {}

  get(): Promise<Member | null> {
    return Promise.resolve(this.current);
  }

  set(member: MemberInput): Promise<Member> {
    this.current =
      member.id !== undefined
        ? fx.resolvedMember(member.id)
        : { resolved: false, resolver: member.resolver!, rewards: [], pointBalance: 0 };
    this.emitter.emit('member.changed', { member: this.current });
    return Promise.resolve(this.current);
  }

  clear(): Promise<void> {
    this.current = null;
    this.emitter.emit('member.changed', { member: null });
    return Promise.resolve();
  }
}

class MockContext implements SessionContextApi {
  private state: SessionContext;

  constructor(
    private readonly emitter: Emitter,
    initial: SessionContext,
  ) {
    this.state = initial;
  }

  phase(): CheckoutPhase {
    return this.state.phase;
  }

  setPhase(phase: CheckoutPhase): Promise<void> {
    this.state = { ...this.state, phase };
    this.emitter.emit('context.changed', this.state);
    return Promise.resolve();
  }

  attributes(): Readonly<Record<string, string>> {
    return { ...this.state.attributes };
  }

  setAttribute(key: string, value: string): Promise<void> {
    this.state = { ...this.state, attributes: { ...this.state.attributes, [key]: value } };
    this.emitter.emit('context.changed', this.state);
    return Promise.resolve();
  }

  removeAttribute(key: string): Promise<void> {
    const { [key]: _removed, ...rest } = this.state.attributes;
    this.state = { ...this.state, attributes: rest };
    this.emitter.emit('context.changed', this.state);
    return Promise.resolve();
  }

  snapshot(): SessionContext {
    return { ...this.state, attributes: { ...this.state.attributes } };
  }
}

class MockRetailMedia implements RetailMediaWidget {
  readonly type = 'retail-media' as const;
  readonly inert = false;
  readonly error = null;
  private paused = false;

  constructor(
    private readonly emitter: Emitter,
    readonly placements: readonly string[],
  ) {}

  isPaused(): boolean {
    return this.paused;
  }

  pause(): Promise<void> {
    this.paused = true;
    for (const placement of this.placements) {
      this.emitter.emit('widget.clear', { widget: 'retail-media', placement });
    }
    return Promise.resolve();
  }

  resume(): Promise<void> {
    this.paused = false;
    return Promise.resolve();
  }

  /** Accepts the tap when the token matches, as the host does, and announces the offer. */
  perform(rendering: Rendering, cta: Cta): Promise<void> {
    if (rendering.cta?.token !== cta.token && rendering.secondary?.token !== cta.token) {
      this.emitter.emit('background.error', {
        code: 'INVALID_STATE',
        message: `token ${cta.token} was not issued for creative ${rendering.creativeId}`,
      });
      return Promise.resolve();
    }
    if (cta.action === 'APPLY_OFFER') {
      this.emitter.emit('widget.offer', {
        widget: 'retail-media',
        offer: fx.offer(rendering.creativeId),
      });
    }
    return Promise.resolve();
  }

  viewed(): Promise<void> {
    return Promise.resolve();
  }
  dismissed(): Promise<void> {
    return Promise.resolve();
  }
  completed(): Promise<void> {
    return Promise.resolve();
  }
}

export class MockShopperSession implements ShopperSession {
  readonly id = `ses_${Math.random().toString(36).slice(2, 8)}`;
  readonly kind: 'local' | 'terminal' = 'local';
  readonly saleId: string;
  readonly currency: string;
  readonly storeLocation: string | undefined;
  state: 'open' | 'ending' | 'ended' = 'open';
  readonly emitter = new Emitter();
  readonly basket: MockBasket;
  readonly member: MockMember;
  readonly context: MockContext;
  private readonly widgetHandles: WidgetHandle[] = [];

  constructor(options: ShopperSessionOptions) {
    this.saleId = options.saleId;
    this.currency = options.currency;
    this.storeLocation = options.storeLocation;
    this.basket = new MockBasket(this.emitter);
    this.member = new MockMember(this.emitter);
    const ctx = fx.context({ saleId: options.saleId, currency: options.currency });
    if (options.storeLocation !== undefined) ctx.storeLocation = options.storeLocation;
    this.context = new MockContext(this.emitter, ctx);
    for (const widget of options.widgets ?? []) {
      this.widgetHandles.push(
        new MockRetailMedia(
          this.emitter,
          widget.placements.map((p) => (typeof p === 'string' ? p : p.id)),
        ),
      );
    }
  }

  widget(type: 'retail-media'): RetailMediaWidget;
  widget(type: WidgetType): WidgetHandle;
  widget(type: WidgetType): WidgetHandle {
    const found = this.widgetHandles.find((w) => w.type === type);
    if (!found) throw new Error(`no ${type} widget on this session`);
    return found;
  }

  widgets(): readonly WidgetHandle[] {
    return this.widgetHandles;
  }

  on<T extends SessionEventType>(type: T, handler: SessionEventHandler<T>): Unsubscribe {
    return this.emitter.on(type, handler);
  }
  once<T extends SessionEventType>(type: T, handler: SessionEventHandler<T>): Unsubscribe {
    return this.emitter.once(type, handler);
  }
  off<T extends SessionEventType>(type: T, handler: SessionEventHandler<T>): void {
    this.emitter.off(type, handler);
  }

  end(): Promise<void> {
    this.state = 'ended';
    this.emitter.emit('session.ended', { sessionId: this.id, forced: false });
    return Promise.resolve();
  }

  async [Symbol.asyncDispose](): Promise<void> {
    if (this.state === 'open') await this.end();
  }
}

export class MockTerminalSession extends MockShopperSession implements TerminalShopperSession {
  override readonly kind = 'terminal' as const;
  readonly poiId: string;
  /** What the next `settle()` fails with, if anything; the recovery handler's answer is recorded. */
  failNextChargeWith: SessionError | null = null;
  recoveries: string[] = [];

  constructor(options: TerminalSessionOptions) {
    super(options);
    this.poiId = options.poiId ?? 'bilt-session-host';
  }

  /** Reads a wallet pass unless `forceEntryModes` restricts entry to `KEYED`, as the terminal does. */
  identifyMember(options?: IdentifyOptions): Operation<IdentifyResult>;
  identifyMember(pending: { resolver: MemberIdResolver }): Operation<IdentifyResult>;
  identifyMember(
    arg?: IdentifyOptions | { resolver: MemberIdResolver },
  ): Operation<IdentifyResult> {
    const options = arg !== undefined && 'resolver' in arg ? undefined : arg;
    return operation('identifyMember', async () => {
      const modes = options?.forceEntryModes;
      const keyedOnly =
        modes !== undefined && modes.length > 0 && modes.every((m) => m === 'KEYED');
      const result: IdentifyResult = {
        status: 'FOUND',
        memberId: 'mbr_8f2a',
        rewards: [],
        pointBalance: 120,
        ...(keyedOnly
          ? {}
          : {
              vasData: {
                ...MOCK_VAS_DATA,
                services: MOCK_VAS_DATA.services.map((service) => ({ ...service })),
              },
            }),
      };
      await this.member.set({ id: 'mbr_8f2a' });
      return result;
    });
  }

  acquireCard() {
    return operation('acquireCard', async () => ({ maskedPan: '****1234', additionalData: {} }));
  }
  requestDigitString(): Operation<string> {
    return operation('requestDigitString', async () => '10001');
  }
  requestDecimalString(): Operation<Money> {
    return operation('requestDecimalString', async () => '2.00');
  }
  requestTextString(): Operation<string> {
    return operation('requestTextString', async () => 'a@b.c');
  }
  requestConfirmation(): Operation<boolean> {
    return operation('requestConfirmation', async () => true);
  }
  requestMenuEntry(_prompt: string, entries: readonly string[]) {
    return operation('requestMenuEntry', async () => ({
      indices: [0],
      values: [entries[0] ?? ''],
    }));
  }
  requestSignature() {
    return operation('requestSignature', async () => ({
      imageData: '',
      format: 'PNG',
      width: 0,
      height: 0,
    }));
  }
  requestAmountConfirmation(): Operation<boolean> {
    return operation('requestAmountConfirmation', async () => true);
  }
  requestPinEntry() {
    return operation('requestPinEntry', async () => ({
      mode: 'PIN_ENTER' as const,
      verified: false,
    }));
  }
  requestPinVerify() {
    return operation('requestPinVerify', async () => ({
      mode: 'PIN_VERIFY' as const,
      verified: true,
    }));
  }
  requestPinVerifyOnly() {
    return operation('requestPinVerifyOnly', async () => ({
      mode: 'PIN_VERIFY_ONLY' as const,
      verified: true,
    }));
  }
  setStoredValueCard(): Promise<void> {
    return Promise.resolve();
  }
  storedValueBalance() {
    return operation('storedValueBalance', async () => ({
      balance: '25.00',
      currency: this.currency,
    }));
  }
  private storedValue(
    type: OperationType,
    transactionType: 'ACTIVATE' | 'LOAD' | 'UNLOAD' | 'RESERVE' | 'REVERSE' | 'DUPLICATE',
  ) {
    return operation(type, async () => ({ transactionType, poiTransactionId: 'POI-SV-1' }));
  }
  storedValueActivate() {
    return this.storedValue('storedValueActivate', 'ACTIVATE');
  }
  storedValueLoad() {
    return this.storedValue('storedValueLoad', 'LOAD');
  }
  storedValueUnload() {
    return this.storedValue('storedValueUnload', 'UNLOAD');
  }
  storedValueDeactivate() {
    return this.storedValue('storedValueDeactivate', 'UNLOAD');
  }
  storedValueReserve() {
    return this.storedValue('storedValueReserve', 'RESERVE');
  }
  storedValueReverse() {
    return this.storedValue('storedValueReverse', 'REVERSE');
  }
  storedValueDuplicate() {
    return this.storedValue('storedValueDuplicate', 'DUPLICATE');
  }

  settle(options: SettleOptions = {}) {
    return operation('settle', async () => {
      const step = {
        deadline: new Date(Date.now() + 30_000),
        signal: new AbortController().signal,
      };
      await this.context.setPhase('TENDERING');
      let total: Money = this.basket.current.grandTotal;
      if (options.beforeStep) {
        await options.beforeStep(
          {
            step: 'REBATE_REDEMPTION',
            currentBasket: this.basket.current,
            currentTotal: total,
            defaultTransactionId: this.basket.current.saleTransactionId.transactionId,
            priorSteps: [],
          },
          step,
        );
      }
      if (!options.disableRebates && options.onRebatesRedeemed) {
        const result = fx.rebates(this.basket.current, '2.00');
        total = await options.onRebatesRedeemed(result, step);
      }
      if (this.failNextChargeWith) {
        const error = this.failNextChargeWith;
        this.failNextChargeWith = null;
        const answer = options.onError
          ? await options.onError(
              {
                step: 'CARD_CHARGE',
                error: error.toJSON(),
                amountDue: total,
                committedMovements: [],
                outcomeCertainty: 'DEFINITIVE',
              },
              step,
            )
          : 'ABORT';
        const action = typeof answer === 'string' ? answer : answer.action;
        this.recoveries.push(action);
        if (action !== 'RETRY') {
          await this.context.setPhase('SCANNING');
          throw error;
        }
      }
      const movement = {
        step: 'CARD_CHARGE' as const,
        target: { type: 'SALES' as const },
        amount: total,
      };
      options.onMovement?.(movement);
      options.onCardCharged?.(movement);
      await this.context.setPhase('COMPLETE');
      return fx.settlementResult(this.basket.current, total);
    });
  }

  refund() {
    return operation('refund', async () => ({
      success: true,
      pointsReversed: 0,
      remainingPointBalance: 0,
    }));
  }
  refundUnlinked() {
    return operation('refundUnlinked', async () => ({
      success: true,
      pointsReversed: 0,
      remainingPointBalance: 0,
    }));
  }
  voidTransaction() {
    return operation('voidTransaction', async () => ({
      success: true,
      pointsReversed: 0,
      remainingPointBalance: 0,
    }));
  }
  getTransactionStatus() {
    return operation('getTransactionStatus', async () => ({ found: false }));
  }
  updateDisplay(): Operation<void> {
    return operation('updateDisplay', async () => undefined);
  }
  updateInputDisplay(): Operation<void> {
    return operation('updateInputDisplay', async () => undefined);
  }
  abort(): Promise<void> {
    return Promise.resolve();
  }
  forceEnd(): Promise<void> {
    this.state = 'ended';
    this.emitter.emit('session.ended', { sessionId: this.id, forced: true });
    return Promise.resolve();
  }
  terminal(): Terminal {
    return mockTerminal(this.poiId);
  }
}

function mockTerminal(poiId?: string): Terminal {
  return {
    poiId,
    diagnose: async () => ({ hostStatuses: [] }),
    totals: async () => ({ transactionTotals: [] }),
    reconcile: async () => ({ transactionTotals: [] }),
    print: async () => undefined,
    playSound: async () => undefined,
    stopSound: async () => undefined,
  };
}

/** The `BiltPos` double; `connect()` stands in for `BiltPos.connect(localBridge())`. */
export class MockBiltPos implements BiltPos {
  readonly capabilities: EngineCapabilities = {
    name: 'mock',
    survivesPageReload: false,
    worksOffline: true,
    supportsWidgets: true,
    supportsTerminalSessions: true,
    supportsLocalSessions: true,
  };
  readonly sessions: MockShopperSession[] = [];

  static connect(): Promise<MockBiltPos> {
    return Promise.resolve(new MockBiltPos());
  }

  health() {
    return Promise.resolve({
      host: 'bridge' as const,
      hostVersion: '0.0.0',
      sdkVersion: '0.0.0',
      protocolVersions: ['2'],
      terminal: { model: 'VictaLane', reachable: true },
    });
  }

  startShopperSession(options: ShopperSessionOptions): Promise<ShopperSession> {
    const session = new MockShopperSession(options);
    this.sessions.push(session);
    return Promise.resolve(session);
  }

  startTerminalSession(options: TerminalSessionOptions): Promise<MockTerminalSession> {
    const session = new MockTerminalSession(options);
    this.sessions.push(session);
    return Promise.resolve(session);
  }

  terminalInfo() {
    return this.health().then((h) => h.terminal);
  }

  terminal(poiId?: string): Terminal {
    return mockTerminal(poiId);
  }

  close(): Promise<void> {
    return Promise.resolve();
  }

  [Symbol.asyncDispose](): Promise<void> {
    return this.close();
  }
}
