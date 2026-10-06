/**
 * `@bilt/pos-sdk/internal`: the seam between the SDK core and an engine. **Unstable** — this
 * entry point exists so the core can be written once and run over the Terminal Bridge, the
 * Cloud Session Service or an in-browser engine; it follows the Session Protocol closely and
 * changes with it. Registers import from `@bilt/pos-sdk`, never from here.
 *
 * @packageDocumentation
 */
import type {
  Basket,
  BasketItem,
  BasketMutation,
  CreateSessionRequest,
  DiagnosisResult,
  Health,
  Member,
  MemberInput,
  Money,
  Operation,
  OperationRequest,
  PrintPayload,
  ReconciliationResult,
  Session,
  SessionContext,
  SessionContextPatch,
  SessionEvent,
  SoundRequest,
  StepReply,
  TerminalInfo,
  WidgetAction,
  WidgetState,
  WidgetConfig,
} from '@bilt/pos-protocol';
import type { EngineCapabilities, EngineContext, EngineFactory } from './pos';

export type { EngineCapabilities, EngineContext, EngineFactory };

/** A session-less terminal operation. */
export type TerminalCommand =
  | { readonly kind: 'diagnose' }
  | { readonly kind: 'totals'; readonly storeLocation?: string }
  | { readonly kind: 'reconcile'; readonly storeLocation?: string }
  | { readonly kind: 'print'; readonly payload: PrintPayload }
  | { readonly kind: 'sound'; readonly request: SoundRequest };

/** What each `TerminalCommand` resolves with. */
export type TerminalCommandResult<C extends TerminalCommand> = C extends { kind: 'diagnose' }
  ? DiagnosisResult
  : C extends { kind: 'totals' | 'reconcile' }
    ? ReconciliationResult
    : void;

/** One basket change; every variant resolves with the new `Basket`. */
export type BasketCommand =
  | { readonly kind: 'get' }
  | { readonly kind: 'add'; readonly item: BasketItem; readonly itemId?: string }
  | {
      readonly kind: 'patch';
      readonly itemId: string;
      readonly quantity?: number;
      readonly discounts?: BasketItem['discounts'];
      readonly taxRate?: Money;
      readonly taxAmount?: Money;
    }
  | { readonly kind: 'remove'; readonly itemId: string }
  | { readonly kind: 'mutate'; readonly mutations: readonly BasketMutation[] }
  | { readonly kind: 'replace'; readonly snapshot: Basket }
  | { readonly kind: 'replace'; readonly items: readonly BasketItem[] }
  | { readonly kind: 'taxTotal'; readonly amount: Money | null }
  | { readonly kind: 'clear' };

export type MemberCommand =
  | { readonly kind: 'get' }
  | { readonly kind: 'set'; readonly member: MemberInput }
  | { readonly kind: 'clear' };

/** The operations the core starts; the request types plus the two lifecycle signals. */
export type EngineOperationRequest =
  | OperationRequest
  | { readonly type: 'end' }
  | { readonly type: 'forceEnd'; readonly reason: string };

/** Per-request options the core passes along. */
export interface RequestOptions {
  /** The idempotency key for this request; the core mints one per intended request and reuses it on retry. */
  readonly idempotencyKey?: string;
  readonly signal?: AbortSignal;
}

/** Widget configuration as the core resolves it from `ShopperSessionOptions`. */
export type EngineWidgetConfig = WidgetConfig;

/**
 * What an engine implements. One instance per `BiltPos`. Methods reject with a `SessionError`
 * for protocol-level failures (the HTTP error bodies) and with an `EngineError` when the host is
 * gone. The engine owns transport details — base URLs, idempotency keys, reconnection, event
 * replay cursors — and exposes none of them beyond this interface.
 */
export interface Engine {
  readonly capabilities: EngineCapabilities;

  /** `GET /health`. */
  health(): Promise<Health>;

  /** `GET /v1/terminals`. */
  terminals(): Promise<readonly TerminalInfo[]>;

  /** The session-less terminal operations. */
  terminal<C extends TerminalCommand>(
    poiId: string,
    command: C,
    options?: RequestOptions,
  ): Promise<TerminalCommandResult<C>>;

  /** `POST /v1/sessions`. */
  createSession(request: CreateSessionRequest, options?: RequestOptions): Promise<Session>;

  /** `GET /v1/sessions/{id}`. */
  session(sessionId: string, options?: RequestOptions): Promise<Session>;

  basket(sessionId: string, command: BasketCommand, options?: RequestOptions): Promise<Basket>;

  member(
    sessionId: string,
    command: MemberCommand,
    options?: RequestOptions,
  ): Promise<Member | null>;

  /** Reads the context, or patches it when `patch` is given. */
  context(
    sessionId: string,
    patch?: SessionContextPatch,
    options?: RequestOptions,
  ): Promise<SessionContext>;

  /** Starts an operation; resolves with the resource as accepted, normally still `queued`. */
  request(
    sessionId: string,
    operation: EngineOperationRequest,
    options?: RequestOptions,
  ): Promise<Operation>;

  /** The operations of a session, newest first. */
  operations(sessionId: string, options?: RequestOptions): Promise<readonly Operation[]>;

  /** One operation's current state. */
  operation(sessionId: string, operationId: string, options?: RequestOptions): Promise<Operation>;

  /** Answers a pending step. */
  reply(
    sessionId: string,
    operationId: string,
    reply: StepReply,
    options?: RequestOptions,
  ): Promise<Operation>;

  /** Aborts one operation, or whatever is in flight when `operationId` is omitted. */
  abort(sessionId: string, operationId?: string, options?: RequestOptions): Promise<void>;

  widgets(sessionId: string, options?: RequestOptions): Promise<readonly WidgetState[]>;

  widget(
    sessionId: string,
    widgetType: WidgetState['type'],
    command: 'pause' | 'resume',
    options?: RequestOptions,
  ): Promise<WidgetState>;

  widgetAction(
    sessionId: string,
    widgetType: WidgetState['type'],
    action: WidgetAction,
    options?: RequestOptions,
  ): Promise<void>;

  /**
   * The session's events from `since` (exclusive; every buffered event when omitted), live
   * until `session.ended`, as the protocol's envelopes. Reconnection and replay are the
   * engine's: a consumer sees each `seq` once, in order.
   */
  events(sessionId: string, since?: number): AsyncIterable<SessionEvent>;

  /** Releases transports; sessions on the host are untouched. */
  close(): Promise<void>;
}
