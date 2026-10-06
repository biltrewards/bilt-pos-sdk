import type {
  CreateSessionRequest,
  Operation as OperationResource,
  Session,
  SessionEvent,
  SessionEventType,
  StepReply,
} from '@bilt/pos-protocol';
import { SessionError } from '../errors';
import type { Engine, EngineOperationRequest } from '../internal';
import type { Operation } from '../operation';
import type {
  RetailMediaWidget,
  SessionEventHandler,
  SessionKind,
  SessionState,
  ShopperSession,
  ShopperSessionOptions,
  Unsubscribe,
  WidgetHandle,
  WidgetType,
} from '../session';
import { SessionBasketImpl } from './basket';
import { SessionContextImpl } from './context';
import { SessionEmitter } from './emitter';
import { newIdempotencyKey } from './ids';
import { SessionMemberImpl } from './member';
import { PendingOperation } from './operation';
import type { Reporter } from './report';
import type { StepHandlers } from './steps';
import { buildWidgets, toWidgetConfig, type RetailMediaWidgetImpl } from './widgets';

/** What every session shares: the engine it runs on and where failures are reported. */
export interface SessionRuntime {
  readonly engine: Engine;
  readonly report: Reporter;
}

/** Events about one operation, kept until the engine's acceptance names the operation. */
type OperationEvent = Extract<
  SessionEvent,
  { type: 'operation.step' | 'operation.movement' | 'operation.completed' }
>;

const UNCLAIMED_LIMIT = 64;
const RECONNECT_BASE_MS = 250;
const RECONNECT_MAX_MS = 10_000;
/** How long `end()` waits for the `session.ended` event once the end operation succeeded. */
const ENDED_EVENT_GRACE_MS = 3_000;

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** Builds the protocol's creation request from the public options. */
export function toCreateSessionRequest(
  kind: SessionKind,
  options: ShopperSessionOptions & { poiId?: string; autoDisplay?: boolean },
): CreateSessionRequest {
  const request: CreateSessionRequest = {
    kind,
    saleId: options.saleId,
    currency: options.currency,
  };
  if (options.poiId !== undefined) request.poiId = options.poiId;
  if (options.storeLocation !== undefined) request.storeLocation = options.storeLocation;
  if (options.autoDisplay !== undefined) request.autoDisplay = options.autoDisplay;
  if (options.member !== undefined) request.member = options.member;
  if (options.phase !== undefined || options.attributes !== undefined) {
    request.context = {};
    if (options.phase !== undefined) request.context.phase = options.phase;
    if (options.attributes !== undefined) request.context.attributes = { ...options.attributes };
  }
  if (options.widgets && options.widgets.length > 0) {
    request.widgets = options.widgets.map(toWidgetConfig);
  }
  if (options.rendering !== undefined) {
    request.clientCapabilities = {
      formats: [...options.rendering.formats],
      surfaceKind: options.rendering.surfaceKind,
      ...(options.rendering.actions ? { actions: [...options.rendering.actions] } : {}),
    };
  }
  return request;
}

/**
 * The SDK's `ShopperSession` over an engine. State lives on the host; this object mirrors it
 * from the session's event stream, which it pumps from creation until `session.ended`.
 *
 * **Operations** are started with `startOperation`: the request goes to the engine with a fresh
 * idempotency key, and the returned `Operation` settles from the matching `operation.completed`
 * event. Events for an operation the engine has not yet named (the acceptance response is still
 * in flight) are held and replayed once it is.
 *
 * **Stream loss.** When the engine's event iterable fails, the session polls every pending
 * operation through `GET .../operations/{id}` (answering a pending step it finds there) and
 * resubscribes from the last sequence number it processed, with exponential backoff. A gap in
 * sequence numbers after a reconnect means the host could not replay everything; the session
 * then re-reads basket, member, context and its pending operations.
 */
export class ShopperSessionImpl implements ShopperSession {
  readonly id: string;
  readonly kind: SessionKind;
  readonly saleId: string;
  readonly currency: string;
  readonly storeLocation: string | undefined;
  readonly basket: SessionBasketImpl;
  readonly member: SessionMemberImpl;
  readonly context: SessionContextImpl;

  protected readonly engine: Engine;
  protected readonly report: Reporter;
  protected readonly emitter: SessionEmitter;
  protected currentState: SessionState = 'open';

  private widgetHandles: RetailMediaWidgetImpl[] = [];
  private readonly pending = new Map<string, PendingOperation<unknown>>();
  private readonly unclaimed = new Map<string, OperationEvent[]>();
  private lastSeq = 0;
  private stopped = false;
  private pumpDone: Promise<void> = Promise.resolve();
  private readonly versions = { basket: 0, member: 0, context: 0 };
  private endedPromise: Promise<void>;
  private resolveEnded!: () => void;
  private endedEventSeen: Promise<void>;
  private resolveEndedEventSeen!: () => void;

  constructor(
    runtime: SessionRuntime,
    session: Session,
    private readonly options: ShopperSessionOptions,
  ) {
    this.engine = runtime.engine;
    this.report = runtime.report;
    this.emitter = new SessionEmitter(runtime.report);
    this.id = session.id;
    this.kind = session.kind;
    this.saleId = session.saleId;
    this.currency = session.currency;
    this.storeLocation = session.storeLocation;
    this.basket = new SessionBasketImpl(this.engine, this.id, {
      cartId: '',
      saleTransactionId: { transactionId: '', timestamp: session.createdAt },
      items: [],
      taxTotal: '0',
      originalTotal: '0',
      discountTotal: '0',
      subtotal: '0',
      grandTotal: '0',
      rebateTotal: '0',
      pointDiscountTotal: '0',
      storedValueTotal: '0',
      cardPaymentTotal: '0',
      externalPaymentTotal: '0',
      updatedAt: '',
    });
    this.member = new SessionMemberImpl(this.engine, this.id, null);
    this.context = new SessionContextImpl(this.engine, this.id, {
      phase: options.phase ?? 'SCANNING',
      attributes: { ...(options.attributes ?? {}) },
      saleId: session.saleId,
      currency: session.currency,
      ...(session.storeLocation !== undefined ? { storeLocation: session.storeLocation } : {}),
      ...(session.poiId !== undefined ? { poiId: session.poiId } : {}),
    });
    this.endedPromise = new Promise((resolve) => {
      this.resolveEnded = resolve;
    });
    this.endedEventSeen = new Promise((resolve) => {
      this.resolveEndedEventSeen = resolve;
    });
  }

  /** Starts the event pump and loads the initial mirrors; the session is usable once this resolves. */
  async initialize(): Promise<this> {
    this.pumpDone = this.pump();
    const [widgets] = await Promise.all([
      this.options.widgets && this.options.widgets.length > 0
        ? this.engine.widgets(this.id)
        : Promise.resolve([]),
      this.resync(),
    ]);
    this.widgetHandles = buildWidgets(this.engine, this.id, this.options.widgets ?? [], widgets);
    return this;
  }

  get state(): SessionState {
    return this.currentState;
  }

  widget(type: 'retail-media'): RetailMediaWidget;
  widget(type: WidgetType): WidgetHandle;
  widget(type: WidgetType): WidgetHandle {
    const found = this.widgetHandles.find((w) => w.type === type);
    if (!found) {
      throw new Error(`no ${type} widget was configured on session ${this.id}`);
    }
    return found;
  }

  widgets(): readonly WidgetHandle[] {
    return [...this.widgetHandles];
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

  async end(): Promise<void> {
    await this.endWith({ type: 'end' });
  }

  async [Symbol.asyncDispose](): Promise<void> {
    if (this.currentState !== 'open') return;
    try {
      await this.end();
    } catch (error) {
      this.report(`ending session ${this.id} on dispose failed`, error);
    }
  }

  // ─── Operations ───

  /**
   * Starts an operation on the session's lane. `end` and `forceEnd` go through here too; they
   * are the only operations a local session accepts.
   */
  protected startOperation<T>(request: EngineOperationRequest, steps?: StepHandlers): Operation<T> {
    const key = newIdempotencyKey();
    const operation = new PendingOperation<T>(request.type, key, steps, {
      reply: (operationId, reply) => this.reply(operationId, reply),
      abort: (operationId) =>
        this.engine.abort(this.id, operationId, { idempotencyKey: newIdempotencyKey() }),
      reportStep: (context, error) => this.reportStep(context, error),
    });
    if (this.currentState === 'ended') {
      operation.refuse(
        new SessionError({ code: 'INVALID_STATE', message: `session ${this.id} has ended` }),
      );
      return operation.handle;
    }
    this.engine.request(this.id, request, { idempotencyKey: key }).then(
      (resource) => this.accepted(operation as PendingOperation<unknown>, resource),
      (error: unknown) => operation.refuse(error),
    );
    return operation.handle;
  }

  private accepted(operation: PendingOperation<unknown>, resource: OperationResource): void {
    operation.accept(resource);
    // Held events replay even when accepting already settled the operation: its movements still
    // belong to the register's handlers, which must hear them before the result resolves.
    this.pending.set(resource.id, operation);
    const held = this.unclaimed.get(resource.id);
    if (held) {
      this.unclaimed.delete(resource.id);
      for (const event of held) this.routeOperationEvent(event);
    }
    if (operation.done) this.pending.delete(resource.id);
  }

  private reply(operationId: string, reply: StepReply): Promise<unknown> {
    return this.engine.reply(this.id, operationId, reply, { idempotencyKey: newIdempotencyKey() });
  }

  /** A step handler, movement handler or reply failed: the register hears about it as a `background.error`. */
  private reportStep(context: string, error: unknown): void {
    const message = error instanceof Error ? error.message : String(error);
    this.emitter.emit('background.error', {
      code: error instanceof SessionError ? error.code : 'UNKNOWN',
      message: `${context}: ${message}`,
    });
  }

  /**
   * Runs the `end` or `forceEnd` operation. Once it succeeded the session waits briefly for the
   * `session.ended` event, so handlers registered for it have run by the time `end()` resolves;
   * a stream that is down does not hold the register up for longer than the grace period.
   */
  protected async endWith(request: EngineOperationRequest): Promise<void> {
    if (this.currentState === 'ended') return;
    const previous = this.currentState;
    this.currentState = 'ending';
    try {
      await this.startOperation<void>(request);
    } catch (error) {
      if (this.currentState === 'ending') this.currentState = previous;
      throw error;
    }
    await Promise.race([this.endedEventSeen, sleep(ENDED_EVENT_GRACE_MS)]);
    this.markEnded();
  }

  private markEnded(): void {
    if (this.currentState === 'ended') return;
    this.currentState = 'ended';
    this.resolveEnded();
    for (const [id, operation] of this.pending) {
      operation.fail(
        new SessionError({
          code: 'INVALID_STATE',
          message: `session ${this.id} ended before operation ${id} completed`,
        }),
      );
    }
    this.pending.clear();
    this.unclaimed.clear();
  }

  // ─── The event pump ───

  /** True once the pump has nothing left to do; a method, so narrowing does not freeze the state. */
  private finished(): boolean {
    return this.stopped || this.currentState === 'ended';
  }

  private async pump(): Promise<void> {
    let delay = RECONNECT_BASE_MS;
    while (!this.finished()) {
      try {
        const since = this.lastSeq > 0 ? this.lastSeq : undefined;
        for await (const event of this.engine.events(this.id, since)) {
          if (this.stopped) return;
          if (event.seq <= this.lastSeq) continue;
          const gap = this.lastSeq > 0 && event.seq > this.lastSeq + 1;
          this.lastSeq = event.seq;
          delay = RECONNECT_BASE_MS;
          this.handle(event);
          if (gap) void this.resync().catch((error) => this.report('resync after a gap', error));
          if (event.type === 'session.ended') return;
        }
        if (this.finished()) return;
        this.report(`the event stream of session ${this.id} ended early`, undefined);
      } catch (error) {
        if (this.finished()) return;
        this.report(`the event stream of session ${this.id} dropped`, error);
      }
      await sleep(delay);
      delay = Math.min(delay * 2, RECONNECT_MAX_MS);
      if (this.finished()) return;
      await this.pollPending();
    }
  }

  /** Re-reads the pending operations and, unless events moved them meanwhile, the state mirrors. */
  protected async resync(): Promise<void> {
    const seen = { ...this.versions };
    const [basket, member, context] = await Promise.all([
      this.engine.basket(this.id, { kind: 'get' }),
      this.engine.member(this.id, { kind: 'get' }),
      this.engine.context(this.id),
      this.pollPending(),
    ]);
    if (seen.basket === this.versions.basket) this.basket.install(basket);
    if (seen.member === this.versions.member) this.member.current = member;
    if (seen.context === this.versions.context) this.context.state = context;
  }

  /** `GET .../operations/{id}` for every operation still pending; the poll fallback for a lost stream. */
  private async pollPending(): Promise<void> {
    const polls = [...this.pending].map(async ([id, operation]) => {
      try {
        const resource = await this.engine.operation(this.id, id);
        operation.update(resource);
        if (operation.done) this.pending.delete(id);
      } catch (error) {
        if (error instanceof SessionError && error.code === 'NOT_FOUND') {
          this.pending.delete(id);
          operation.fail(error);
        } else {
          this.report(`polling operation ${id}`, error);
        }
      }
    });
    await Promise.all(polls);
  }

  private handle(event: SessionEvent): void {
    switch (event.type) {
      case 'session.started':
        this.versions.context++;
        this.context.state = event.payload.context;
        this.emitter.emit(event.type, event.payload);
        return;
      case 'basket.changed':
        this.versions.basket++;
        this.basket.install(event.payload.current);
        this.emitter.emit(event.type, event.payload);
        return;
      case 'member.changed':
        this.versions.member++;
        this.member.current = event.payload.member;
        this.emitter.emit(event.type, event.payload);
        return;
      case 'context.changed':
        this.versions.context++;
        this.context.state = event.payload;
        this.emitter.emit(event.type, event.payload);
        return;
      case 'operation.step':
      case 'operation.movement':
      case 'operation.completed':
        this.dispatchOperationEvent(event);
        return;
      case 'session.ended':
        this.markEnded();
        this.emitter.emit(event.type, event.payload);
        this.emitter.clear();
        this.resolveEndedEventSeen();
        return;
      default:
        this.emitter.emit(event.type, event.payload);
    }
  }

  private dispatchOperationEvent(event: OperationEvent): void {
    this.routeOperationEvent(event);
    switch (event.type) {
      case 'operation.step':
        this.emitter.emit(event.type, event.payload);
        break;
      case 'operation.movement':
        this.emitter.emit(event.type, event.payload);
        break;
      case 'operation.completed':
        this.emitter.emit(event.type, event.payload);
        break;
    }
  }

  /** Hands an event to its pending operation, or holds it; replayed events come through here so listeners hear each one once. */
  private routeOperationEvent(event: OperationEvent): void {
    const operationId =
      event.type === 'operation.completed' ? event.payload.id : event.payload.operationId;
    const operation = this.pending.get(operationId);
    if (operation) {
      switch (event.type) {
        case 'operation.step':
          operation.step(event.payload);
          break;
        case 'operation.movement':
          operation.movement(event.payload.movement);
          break;
        case 'operation.completed':
          operation.update(event.payload);
          if (operation.done) this.pending.delete(operationId);
          break;
      }
    } else if (event.type !== 'operation.completed' || this.currentState !== 'ended') {
      this.hold(operationId, event);
    }
  }

  /** Keeps an event for an operation the engine has not named yet; bounded, oldest first out. */
  private hold(operationId: string, event: OperationEvent): void {
    let held = this.unclaimed.get(operationId);
    if (!held) {
      held = [];
      this.unclaimed.set(operationId, held);
      if (this.unclaimed.size > UNCLAIMED_LIMIT) {
        const oldest = this.unclaimed.keys().next().value;
        if (oldest !== undefined) this.unclaimed.delete(oldest);
      }
    }
    held.push(event);
  }

  /** Stops the pump without ending the session on the host; for `BiltPos.close()`. */
  async detach(): Promise<void> {
    this.stopped = true;
    this.resolveEndedEventSeen();
    await Promise.race([this.pumpDone, sleep(0)]);
  }

  /** Resolves once the session has ended, however that happened. */
  get ended(): Promise<void> {
    return this.endedPromise;
  }
}
