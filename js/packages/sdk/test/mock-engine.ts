// A minimal implementation of the internal `Engine` seam: in-memory sessions, a basket, an
// operation that completes at once, and an event stream with replay. It exists to prove the
// interface is implementable by something other than a protocol client, and to pin down the
// event-stream contract (`since` is exclusive, `session.started` first, `session.ended` last).
import type {
  Basket,
  Member,
  Operation,
  Session,
  SessionContext,
  SessionEvent,
  WidgetState,
} from '@bilt/pos-protocol';
import { SessionError, type EngineCapabilities } from '../src/index';
import type {
  BasketCommand,
  Engine,
  EngineOperationRequest,
  MemberCommand,
  TerminalCommand,
  TerminalCommandResult,
} from '../src/internal';
import * as fx from './fixtures';

interface State {
  session: Session;
  basket: Basket;
  member: Member | null;
  context: SessionContext;
  operations: Operation[];
  events: SessionEvent[];
  waiters: Array<() => void>;
}

export class MockEngine implements Engine {
  readonly capabilities: EngineCapabilities = {
    name: 'mock-engine',
    survivesPageReload: true,
    worksOffline: true,
    supportsWidgets: false,
    supportsTerminalSessions: true,
    supportsLocalSessions: true,
  };

  private readonly sessions = new Map<string, State>();
  private counter = 0;

  async health() {
    return {
      host: 'bridge' as const,
      hostVersion: '0.0.0',
      sdkVersion: '0.0.0',
      protocolVersions: ['1'],
      terminals: [{ poiId: 'VictaLane-275839164' }],
    };
  }

  async terminals() {
    return (await this.health()).terminals;
  }

  async terminal<C extends TerminalCommand>(
    _poiId: string,
    command: C,
  ): Promise<TerminalCommandResult<C>> {
    switch (command.kind) {
      case 'diagnose':
        return { hostStatuses: [] } as unknown as TerminalCommandResult<C>;
      case 'totals':
      case 'reconcile':
        return { transactionTotals: [] } as unknown as TerminalCommandResult<C>;
      default:
        return undefined as TerminalCommandResult<C>;
    }
  }

  async createSession(request: Parameters<Engine['createSession']>[0]): Promise<Session> {
    const id = `ses_${++this.counter}`;
    const session: Session = {
      id,
      kind: request.kind,
      saleId: request.saleId,
      currency: request.currency,
      autoDisplay: request.autoDisplay ?? true,
      state: 'open',
      createdAt: new Date().toISOString(),
      eventsUrl: `/v1/sessions/${id}/events`,
    };
    if (request.poiId !== undefined) session.poiId = request.poiId;
    const context = fx.context({ saleId: request.saleId, currency: request.currency });
    const state: State = {
      session,
      basket: fx.basket([]),
      member: null,
      context,
      operations: [],
      events: [],
      waiters: [],
    };
    this.sessions.set(id, state);
    this.push(state, 'session.started', { session, context });
    return session;
  }

  async session(sessionId: string): Promise<Session> {
    return this.state(sessionId).session;
  }

  async basket(sessionId: string, command: BasketCommand): Promise<Basket> {
    const state = this.state(sessionId);
    if (command.kind === 'add') {
      const previous = state.basket;
      const line =
        command.itemId === undefined
          ? fx.lineItem(command.item)
          : fx.lineItem(command.item, command.itemId);
      state.basket = fx.basket([...previous.items, line], previous.cartId);
      this.push(state, 'basket.changed', {
        previous,
        current: state.basket,
        source: 'INCREMENTAL',
        added: [line],
        removed: [],
        quantityChanged: [],
        priceChanged: [],
        discountsChanged: [],
        taxChanged: [],
        detailsChanged: [],
        taxTotalChanged: previous.taxTotal !== state.basket.taxTotal,
      });
    }
    return state.basket;
  }

  async member(sessionId: string, command: MemberCommand): Promise<Member | null> {
    const state = this.state(sessionId);
    if (command.kind === 'set') {
      state.member =
        command.member.id !== undefined
          ? fx.resolvedMember(command.member.id)
          : { resolved: false, resolver: command.member.resolver!, rewards: [], pointBalance: 0 };
      this.push(state, 'member.changed', { member: state.member });
    } else if (command.kind === 'clear') {
      state.member = null;
      this.push(state, 'member.changed', { member: null });
    }
    return state.member;
  }

  async context(
    sessionId: string,
    patch?: Parameters<Engine['context']>[1],
  ): Promise<SessionContext> {
    const state = this.state(sessionId);
    if (patch) {
      const attributes = { ...state.context.attributes };
      for (const [key, value] of Object.entries(patch.attributes ?? {})) {
        if (value === null) delete attributes[key];
        else attributes[key] = value;
      }
      state.context = { ...state.context, phase: patch.phase ?? state.context.phase, attributes };
      this.push(state, 'context.changed', state.context);
    }
    return state.context;
  }

  async request(sessionId: string, operation: EngineOperationRequest): Promise<Operation> {
    const state = this.state(sessionId);
    const now = new Date().toISOString();
    const resource: Operation = {
      id: `op_${++this.counter}`,
      type: operation.type,
      status: 'succeeded',
      createdAt: now,
      completedAt: now,
    } as Operation;
    state.operations.unshift(resource);
    this.push(state, 'operation.completed', resource);
    if (operation.type === 'end' || operation.type === 'forceEnd') {
      state.session = { ...state.session, state: 'ended', endedAt: now };
      this.push(state, 'session.ended', {
        sessionId,
        forced: operation.type === 'forceEnd',
        ...(operation.type === 'forceEnd' ? { reason: operation.reason } : {}),
      });
    }
    return resource;
  }

  async operations(sessionId: string): Promise<readonly Operation[]> {
    return this.state(sessionId).operations;
  }

  async operation(sessionId: string, operationId: string): Promise<Operation> {
    const found = this.state(sessionId).operations.find((o) => o.id === operationId);
    if (!found)
      throw new SessionError({ code: 'NOT_FOUND', message: `no operation ${operationId}` });
    return found;
  }

  async reply(sessionId: string, operationId: string): Promise<Operation> {
    return this.operation(sessionId, operationId);
  }

  async abort(): Promise<void> {}

  async widgets(): Promise<readonly WidgetState[]> {
    return [];
  }

  async widget(): Promise<WidgetState> {
    throw new SessionError({ code: 'NOT_FOUND', message: 'no widgets on the mock engine' });
  }

  async widgetAction(): Promise<void> {}

  async *events(sessionId: string, since = 0): AsyncIterable<SessionEvent> {
    const state = this.state(sessionId);
    let cursor = since;
    for (;;) {
      const next = state.events.find((e) => e.seq > cursor);
      if (next) {
        cursor = next.seq;
        yield next;
        if (next.type === 'session.ended') return;
        continue;
      }
      await new Promise<void>((resolve) => state.waiters.push(resolve));
    }
  }

  async close(): Promise<void> {}

  private state(sessionId: string): State {
    const state = this.sessions.get(sessionId);
    if (!state) throw new SessionError({ code: 'NOT_FOUND', message: `no session ${sessionId}` });
    return state;
  }

  private push<T extends SessionEvent['type']>(
    state: State,
    type: T,
    payload: Extract<SessionEvent, { type: T }>['payload'],
  ): void {
    const event = {
      seq: state.events.length + 1,
      at: new Date().toISOString(),
      type,
      payload,
    } as SessionEvent;
    state.events.push(event);
    const waiters = state.waiters.splice(0);
    for (const wake of waiters) wake();
  }
}
