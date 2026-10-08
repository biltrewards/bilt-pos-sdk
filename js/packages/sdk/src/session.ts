import type {
  Basket,
  BasketDiscount,
  BasketItem,
  CheckoutPhase,
  ClientCapabilities,
  Cta,
  Duration,
  MediaType,
  Member,
  MemberInput,
  Money,
  PlacementConfig,
  Rendering,
  SessionContext,
  SessionEventPayload,
  SessionEventType,
  SurfaceKind,
} from '@bilt/pos-protocol';
import type { SessionError } from './errors';

/** Whether a session has a terminal: `local` is basket and member state only. */
export type SessionKind = 'local' | 'terminal';

/** Where a session stands: `ending` while `end()` is in flight. */
export type SessionState = 'open' | 'ending' | 'ended';

/** The widget types the SDK ships; the retail-media banner is the first. */
export type WidgetType = 'retail-media';

/** Stops a subscription made with `on` or `once`. */
export type Unsubscribe = () => void;

/** Receives one event's payload; the event envelope stays inside the engine. */
export type SessionEventHandler<T extends SessionEventType> = (
  payload: SessionEventPayload<T>,
) => void;

/**
 * One shopper's visit at a lane, with or without a terminal — the JavaScript form of the Java
 * `ShopperSession`.
 *
 * A session owns the basket being rung, the member identified for the visit and the checkout
 * context, and carries the lane's identifiers. Everything that needs a terminal lives on
 * `TerminalShopperSession`. State lives on the engine's host; the properties here are the SDK's
 * mirror of it, kept current from the session's event stream, so reads are synchronous and
 * writes return promises.
 *
 * A session ends once, with `end()`, and cannot be restarted. It is async-disposable, so
 * `await using session = await pos.startTerminalSession(..)` ends it on the way out of the
 * block; disposal is a best-effort `end()` that never throws.
 */
export interface ShopperSession {
  /** Unique identifier of this session instance. */
  readonly id: string;

  readonly kind: SessionKind;

  /** The register's identifier for this lane. */
  readonly saleId: string;

  /** ISO 4217 currency code. */
  readonly currency: string;

  /** Store location identifier, when configured. */
  readonly storeLocation: string | undefined;

  readonly state: SessionState;

  /** The basket: item and tax mutations, batch edits, snapshots. */
  readonly basket: SessionBasket;

  /** The member attached to the visit. */
  readonly member: SessionMember;

  /** Checkout phase and attributes that widgets use. */
  readonly context: SessionContextApi;

  /**
   * The registered widget of a type, for pausing, resuming and (for retail media) reporting what
   * the shopper did with a rendering. Asking for a widget that was not configured on the session
   * is a programming error and throws.
   */
  widget(type: 'retail-media'): RetailMediaWidget;
  widget(type: WidgetType): WidgetHandle;

  /** Every registered widget, in registration order. */
  widgets(): readonly WidgetHandle[];

  /**
   * Subscribes to one event type. Handlers receive the payload the protocol defines for the
   * type: a `BasketChange` for `basket.changed`, an `Offer` for `widget.offer`, a `SessionError`
   * for `background.error`, and so on. Handlers run in event order, after the session's own
   * mirror of the state was updated, so `session.basket.current` already reflects a
   * `basket.changed` when its handler runs. A throwing handler is reported, never propagated.
   */
  on<T extends SessionEventType>(type: T, handler: SessionEventHandler<T>): Unsubscribe;

  /** Like `on`, for the next event of the type only. */
  once<T extends SessionEventType>(type: T, handler: SessionEventHandler<T>): Unsubscribe;

  /** Removes a handler registered with `on` or `once`. */
  off<T extends SessionEventType>(type: T, handler: SessionEventHandler<T>): void;

  /**
   * Ends the session. Afterwards no operation is allowed and the basket is frozen; start a new
   * session for the next shopper. A terminal session also tells the terminal to discard its
   * session-scoped data and may refuse while money movement is unresolved — see
   * `TerminalShopperSession.end()`. Rejects with a `SessionError` on refusal, leaving the session
   * open so the call can be retried.
   */
  end(): Promise<void>;

  /** Best-effort `end()` for `await using`; a refusal or failure is reported, not thrown. */
  [Symbol.asyncDispose](): Promise<void>;
}

/**
 * The basket surface of a session — the Java `SessionBasket`. Three ways to update one basket,
 * all atomic, each ending in exactly one `basket.changed` event: incremental calls as the
 * cashier scans, a `mutate` batch for a register action that touches several lines, and
 * `replace` for a POS that owns its cart and pushes the whole thing after every change. Sale,
 * return and credit lines coexist in one basket.
 *
 * Mutations are refused once the session has ended; a terminal session also refuses them while
 * money is moving and after the current basket has settled. `clear()` is the boundary between
 * baskets in one session.
 */
export interface SessionBasket {
  /** The latest snapshot the SDK has seen. */
  readonly current: Basket;

  /** Re-reads the snapshot from the engine; normally unnecessary, `current` follows the events. */
  refresh(): Promise<Basket>;

  /** Adds an item; when its SKU (and type, for an unreferenced item) is already present the quantity is incremented. */
  addItem(item: BasketItem, itemId?: string): Promise<Basket>;

  removeItem(itemId: string): Promise<Basket>;
  removeItemBySku(sku: string): Promise<Basket>;

  /** Sets an absolute quantity; `0` removes the line. */
  updateItemQuantity(itemId: string, quantity: number): Promise<Basket>;
  updateItemQuantityBySku(sku: string, quantity: number): Promise<Basket>;

  /** Replaces the register-applied discounts on a line; an empty list clears them. */
  setDiscounts(itemId: string, discounts: readonly BasketDiscount[]): Promise<Basket>;
  setDiscountsBySku(sku: string, discounts: readonly BasketDiscount[]): Promise<Basket>;

  /** Sets the tax rate on a line (`taxAmount = subtotal × rate`), clearing a fixed amount. */
  setTaxRate(itemId: string, rate: Money): Promise<Basket>;
  setTaxRateBySku(sku: string, rate: Money): Promise<Basket>;

  /** Sets a fixed tax amount on a line, overriding any rate. */
  setTaxAmount(itemId: string, amount: Money): Promise<Basket>;
  setTaxAmountBySku(sku: string, amount: Money): Promise<Basket>;

  /** Overrides the basket's total tax; `null` restores item-level computation. */
  setTaxTotal(amount: Money | null): Promise<Basket>;

  /**
   * Applies several mutations atomically, with one event and one display refresh:
   * `basket.mutate((m) => m.addItem(frame).removeItemBySku('KRK-CNDL-LRG-VAN').setTaxTotal('1.33'))`.
   * If any step is invalid the basket is left untouched.
   */
  mutate(build: (mutation: BasketMutationBuilder) => void): Promise<Basket>;

  /**
   * Replaces the whole basket with a snapshot or a list of register items. Lines are paired
   * with the current basket by `reference`, otherwise by SKU and type, and paired lines keep
   * their item ids; a snapshot equal to the current basket produces no change. On a basket
   * consumed by a settlement this starts a fresh cart, as `clear()` would.
   */
  replace(content: Basket | readonly BasketItem[]): Promise<Basket>;

  /**
   * Clears items and tax and starts a fresh basket with a new cart id, dropping the stored-value
   * card selected for split tender. Refused while money movement, settlement recovery or a
   * partially completed void is in progress.
   */
  clear(): Promise<Basket>;
}

/** The receiver of a `mutate` batch; every method returns the builder so calls chain. */
export interface BasketMutationBuilder {
  addItem(item: BasketItem, itemId?: string): this;
  removeItem(itemId: string): this;
  removeItemBySku(sku: string): this;
  updateItemQuantity(itemId: string, quantity: number): this;
  updateItemQuantityBySku(sku: string, quantity: number): this;
  setDiscounts(itemId: string, discounts: readonly BasketDiscount[]): this;
  setDiscountsBySku(sku: string, discounts: readonly BasketDiscount[]): this;
  setTaxRate(itemId: string, rate: Money): this;
  setTaxRateBySku(sku: string, rate: Money): this;
  setTaxAmount(itemId: string, amount: Money): this;
  setTaxAmountBySku(sku: string, amount: Money): this;
  setTaxTotal(amount: Money | null): this;
}

/**
 * The member attached to a visit — `ShopperSession.member(..)` and `member()` in Java.
 *
 * A member with an `id` attaches at once. One with a `resolver` (a phone number, account id,
 * email or retailer identifier) attaches as pending and is resolved in the background; until
 * then the visit is a guest's. Every change, from the register, a terminal prompt or a completed
 * lookup, arrives as a `member.changed` event, and `current` follows it.
 */
export interface SessionMember {
  /** The member as last announced: resolved, pending, or `null` for a guest. */
  readonly current: Member | null;

  /** Re-reads the member from the engine. */
  get(): Promise<Member | null>;

  /**
   * Attaches the member for this visit. Allowed at any time, but refused once the session has
   * ended and, on a terminal session, while a settlement or void is moving money. Resolves with
   * the member as attached, still pending when a lookup is under way.
   */
  set(member: MemberInput): Promise<Member>;

  /** Signs the member out. Same guards as `set`. */
  clear(): Promise<void>;
}

/**
 * The checkout context — the Java `SessionContext`: the phase and free-form attributes a widget
 * uses for eligibility and targeting, plus the lane identifiers. Pure bookkeeping; nothing reaches
 * a terminal. A terminal session moves the phase itself around settlement and may override one
 * set here. Writes are refused once the session is ending.
 */
export interface SessionContextApi {
  phase(): CheckoutPhase;
  setPhase(phase: CheckoutPhase): Promise<void>;

  /** The attributes as they are now, in insertion order. */
  attributes(): Readonly<Record<string, string>>;
  setAttribute(key: string, value: string): Promise<void>;
  removeAttribute(key: string): Promise<void>;

  /** A consistent copy of the whole context. */
  snapshot(): SessionContext;
}

/**
 * A widget registered on the session. `pause()` clears its placements and keeps them clear
 * until `resume()`, for a companion display shared with PIN entry or signature capture; both
 * are idempotent. A widget that could not attach (no ad decision service on the host, missing
 * store location) is `inert` for the session with `error` saying why, and the checkout runs
 * without it.
 */
export interface WidgetHandle {
  readonly type: WidgetType;
  readonly inert: boolean;
  readonly error: SessionError | null;
  isPaused(): boolean;
  pause(): Promise<void>;
  resume(): Promise<void>;
}

/**
 * The retail-media widget. The host decides what to show and validates every tap; the page
 * renders. Renderings arrive as `widget.rendering` and `widget.clear` events, and the renderer
 * reports back through the four methods below — the JavaScript form of the Java `ActionSink`.
 * Validated offers come back as `widget.offer`, the one event the register must act on; every
 * measured event as `widget.interaction`; a rejected tap (foreign, stale or tampered token) as
 * `background.error`, never as a thrown error.
 */
export interface RetailMediaWidget extends WidgetHandle {
  readonly type: 'retail-media';

  /** The shopper tapped `cta` on `rendering`; the token goes back exactly as received. */
  perform(rendering: Rendering, cta: Cta): Promise<void>;

  /** The rendering was visible long enough to count as seen; report at most once per rendering. */
  viewed(rendering: Rendering): Promise<void>;

  /** The shopper closed the rendering from the surface itself. */
  dismissed(rendering: Rendering): Promise<void>;

  /** The rendering's video played to its end. */
  completed(rendering: Rendering): Promise<void>;
}

/** Configuration of a retail-media widget on a session; the `RetailMedia.builder()` options a client may set. */
export interface RetailMediaOptions {
  readonly type: 'retail-media';

  /** The placements this register has a surface for; a bare string is a placement id with the session's default capabilities. */
  readonly placements: readonly (string | PlacementConfig)[];

  /** Phases in which media is requested and shown; default every phase except `COMPLETE`. */
  readonly eligiblePhases?: readonly CheckoutPhase[];

  /** How long a decision may take before the placement stays blank; default 500 ms. */
  readonly decisionTimeout?: Duration;

  /** Overrides how long a rendering stays before refresh; default each rendering's own TTL. */
  readonly renderingTtl?: Duration;
}

export type WidgetOptions = RetailMediaOptions;

/** What this register can render, applied to every placement that does not declare its own. */
export interface RenderingCapabilities {
  readonly formats: readonly MediaType[];
  readonly surfaceKind: SurfaceKind;
  readonly actions?: ClientCapabilities['actions'];
}

/** Options for `BiltPos.startShopperSession`; the `ShopperSession.Builder` fields. */
export interface ShopperSessionOptions {
  /** The register's identifier for this lane. */
  readonly saleId: string;

  /** ISO 4217 currency code. */
  readonly currency: string;

  /** Store location identifier; required for a retail-media widget. */
  readonly storeLocation?: string;

  /** The member to start with when the shopper is known before the visit begins. */
  readonly member?: MemberInput;

  /** The phase the context starts in; default `SCANNING`. */
  readonly phase?: CheckoutPhase;

  /** Attributes to seed the context with. */
  readonly attributes?: Readonly<Record<string, string>>;

  /** Shopper-facing widgets to attach, in order. */
  readonly widgets?: readonly WidgetOptions[];

  /** Rendering capabilities for widget placements that declare none. */
  readonly rendering?: RenderingCapabilities;
}

/** Options for `BiltPos.startTerminalSession`; the `TerminalShopperSession.Builder` fields. */
export interface TerminalSessionOptions extends ShopperSessionOptions {
  /**
   * The Nexo `POIID` the session's messages carry. It does not select a terminal: a host drives
   * exactly one and passes this value through, using its own default when it is omitted.
   */
  readonly poiId?: string;

  /** Whether basket changes refresh the terminal's customer display automatically; default `true`. */
  readonly autoDisplay?: boolean;
}
