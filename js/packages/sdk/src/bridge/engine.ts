import type {
  Basket,
  CreateSessionRequest,
  Health,
  Member,
  Operation,
  Session,
  SessionContext,
  SessionContextPatch,
  SessionEvent,
  StepReply,
  TerminalInfo,
  WidgetAction,
  WidgetState,
} from '@bilt/pos-protocol';
import type {
  BasketCommand,
  Engine,
  EngineCapabilities,
  EngineOperationRequest,
  MemberCommand,
  RequestOptions,
  TerminalCommand,
  TerminalCommandResult,
} from '../internal';
import { SessionError } from '../errors';
import { BridgeEventStream } from './events';
import { BridgeHttp, type HttpRequest } from './http';
import type { ResolvedBridgeOptions } from './options';

/** What the Terminal Bridge can do: it survives the page and works through an internet outage. */
export const BRIDGE_CAPABILITIES: EngineCapabilities = Object.freeze({
  name: 'bridge',
  survivesPageReload: true,
  worksOffline: true,
  supportsWidgets: true,
  supportsTerminalSessions: true,
  supportsLocalSessions: true,
});

function sessionPath(sessionId: string, rest = ''): string {
  return `/v1/sessions/${encodeURIComponent(sessionId)}${rest}`;
}

function keyed(
  options: RequestOptions | undefined,
): Pick<HttpRequest, 'idempotencyKey' | 'signal'> {
  return {
    ...(options?.idempotencyKey !== undefined ? { idempotencyKey: options.idempotencyKey } : {}),
    ...(options?.signal ? { signal: options.signal } : {}),
  };
}

/**
 * The `Engine` over a Terminal Bridge on loopback: every method is the Session Protocol request
 * it names, through `BridgeHttp`; `events()` is a `BridgeEventStream`. Nothing about ports,
 * URLs, idempotency keys or sequence numbers leaves this class.
 */
export class BridgeEngine implements Engine {
  readonly capabilities = BRIDGE_CAPABILITIES;
  readonly http: BridgeHttp;
  private readonly streams = new Set<BridgeEventStream>();

  constructor(
    readonly baseUrl: string,
    private readonly options: ResolvedBridgeOptions,
  ) {
    this.http = new BridgeHttp(baseUrl, options);
  }

  health(): Promise<Health> {
    return this.http.send<Health>({ method: 'GET', path: '/health' });
  }

  async terminalInfo(): Promise<TerminalInfo | null> {
    try {
      return await this.http.send<TerminalInfo>({ method: 'GET', path: '/v1/terminal' });
    } catch (error) {
      if (error instanceof SessionError && error.code === 'NOT_FOUND') return null;
      throw error;
    }
  }

  async terminal<C extends TerminalCommand>(
    poiId: string | undefined,
    command: C,
    options?: RequestOptions,
  ): Promise<TerminalCommandResult<C>> {
    const common = { ...keyed(options), query: { poiId } };
    switch (command.kind) {
      case 'diagnose':
        return this.http.send({ method: 'POST', path: '/v1/terminal/diagnose', ...common });
      case 'totals':
      case 'reconcile':
        return this.http.send({
          method: 'POST',
          path: command.kind === 'totals' ? '/v1/terminal/totals' : '/v1/terminal/reconcile',
          ...common,
          query: { poiId, storeLocation: command.storeLocation },
        });
      case 'print':
        return this.http.send({
          method: 'POST',
          path: '/v1/terminal/print',
          body: command.payload,
          ...common,
        });
      case 'sound':
        return this.http.send({
          method: 'POST',
          path: '/v1/terminal/sound',
          body: command.request,
          ...common,
        });
      default:
        throw new Error(`unknown terminal command ${String((command as TerminalCommand).kind)}`);
    }
  }

  createSession(request: CreateSessionRequest, options?: RequestOptions): Promise<Session> {
    return this.http.send<Session>({
      method: 'POST',
      path: '/v1/sessions',
      body: request,
      ...keyed(options),
    });
  }

  session(sessionId: string, options?: RequestOptions): Promise<Session> {
    return this.http.send<Session>({
      method: 'GET',
      path: sessionPath(sessionId),
      ...keyed(options),
    });
  }

  basket(sessionId: string, command: BasketCommand, options?: RequestOptions): Promise<Basket> {
    const common = keyed(options);
    const base = sessionPath(sessionId, '/basket');
    switch (command.kind) {
      case 'get':
        return this.http.send({ method: 'GET', path: base, ...common });
      case 'add':
        return this.http.send({
          method: 'POST',
          path: `${base}/items`,
          body:
            command.itemId === undefined
              ? command.item
              : { ...command.item, itemId: command.itemId },
          ...common,
        });
      case 'patch': {
        const { kind: _kind, itemId, ...patch } = command;
        return this.http.send({
          method: 'PATCH',
          path: `${base}/items/${encodeURIComponent(itemId)}`,
          body: patch,
          ...common,
        });
      }
      case 'remove':
        return this.http.send({
          method: 'DELETE',
          path: `${base}/items/${encodeURIComponent(command.itemId)}`,
          ...common,
        });
      case 'mutate':
        return this.http.send({
          method: 'POST',
          path: `${base}/mutations`,
          body: { mutations: command.mutations },
          ...common,
        });
      case 'replace':
        return this.http.send({
          method: 'PUT',
          path: base,
          body: 'snapshot' in command ? { snapshot: command.snapshot } : { items: command.items },
          ...common,
        });
      case 'taxTotal':
        return this.http.send({
          method: 'POST',
          path: `${base}/tax-total`,
          body: { amount: command.amount },
          ...common,
        });
      case 'clear':
        return this.http.send({ method: 'POST', path: `${base}/clear`, ...common });
      default:
        throw new Error(`unknown basket command ${String((command as BasketCommand).kind)}`);
    }
  }

  async member(
    sessionId: string,
    command: MemberCommand,
    options?: RequestOptions,
  ): Promise<Member | null> {
    const common = keyed(options);
    const path = sessionPath(sessionId, '/member');
    switch (command.kind) {
      case 'get':
        return (
          (await this.http.send<Member | undefined>({ method: 'GET', path, ...common })) ?? null
        );
      case 'set':
        return this.http.send<Member>({ method: 'PUT', path, body: command.member, ...common });
      case 'clear':
        await this.http.send<undefined>({ method: 'DELETE', path, ...common });
        return null;
      default:
        throw new Error(`unknown member command ${String((command as MemberCommand).kind)}`);
    }
  }

  context(
    sessionId: string,
    patch?: SessionContextPatch,
    options?: RequestOptions,
  ): Promise<SessionContext> {
    const path = sessionPath(sessionId, '/context');
    return patch
      ? this.http.send({ method: 'PATCH', path, body: patch, ...keyed(options) })
      : this.http.send({ method: 'GET', path, ...keyed(options) });
  }

  request(
    sessionId: string,
    operation: EngineOperationRequest,
    options?: RequestOptions,
  ): Promise<Operation> {
    const common = keyed(options);
    switch (operation.type) {
      case 'end':
        return this.http.send({ method: 'DELETE', path: sessionPath(sessionId), ...common });
      case 'forceEnd':
        return this.http.send({
          method: 'POST',
          path: sessionPath(sessionId, '/force-end'),
          body: { reason: operation.reason },
          ...common,
        });
      default:
        return this.http.send({
          method: 'POST',
          path: sessionPath(sessionId, '/operations'),
          body: operation,
          ...common,
        });
    }
  }

  operations(sessionId: string, options?: RequestOptions): Promise<readonly Operation[]> {
    return this.http.send<Operation[]>({
      method: 'GET',
      path: sessionPath(sessionId, '/operations'),
      ...keyed(options),
    });
  }

  operation(sessionId: string, operationId: string, options?: RequestOptions): Promise<Operation> {
    return this.http.send<Operation>({
      method: 'GET',
      path: sessionPath(sessionId, `/operations/${encodeURIComponent(operationId)}`),
      ...keyed(options),
    });
  }

  reply(
    sessionId: string,
    operationId: string,
    reply: StepReply,
    options?: RequestOptions,
  ): Promise<Operation> {
    return this.http.send<Operation>({
      method: 'POST',
      path: sessionPath(sessionId, `/operations/${encodeURIComponent(operationId)}/reply`),
      body: reply,
      ...keyed(options),
    });
  }

  async abort(sessionId: string, operationId?: string, options?: RequestOptions): Promise<void> {
    await this.http.send({
      method: 'POST',
      path:
        operationId === undefined
          ? sessionPath(sessionId, '/abort')
          : sessionPath(sessionId, `/operations/${encodeURIComponent(operationId)}/abort`),
      body: {},
      ...keyed(options),
    });
  }

  widgets(sessionId: string, options?: RequestOptions): Promise<readonly WidgetState[]> {
    return this.http.send<WidgetState[]>({
      method: 'GET',
      path: sessionPath(sessionId, '/widgets'),
      ...keyed(options),
    });
  }

  widget(
    sessionId: string,
    widgetType: WidgetState['type'],
    command: 'pause' | 'resume',
    options?: RequestOptions,
  ): Promise<WidgetState> {
    return this.http.send<WidgetState>({
      method: 'POST',
      path: sessionPath(sessionId, `/widgets/${encodeURIComponent(widgetType)}/${command}`),
      body: {},
      ...keyed(options),
    });
  }

  async widgetAction(
    sessionId: string,
    widgetType: WidgetState['type'],
    action: WidgetAction,
    options?: RequestOptions,
  ): Promise<void> {
    await this.http.send({
      method: 'POST',
      path: sessionPath(sessionId, `/widgets/${encodeURIComponent(widgetType)}/actions`),
      body: action,
      ...keyed(options),
    });
  }

  events(sessionId: string, since?: number): AsyncIterable<SessionEvent> {
    const stream = new BridgeEventStream(this.http, sessionId, since, this.options);
    this.streams.add(stream);
    const streams = this.streams;
    return {
      async *[Symbol.asyncIterator]() {
        try {
          for await (const event of stream) yield event;
        } finally {
          streams.delete(stream);
          stream.close();
        }
      },
    };
  }

  async close(): Promise<void> {
    for (const stream of this.streams) stream.close();
    this.streams.clear();
  }
}
