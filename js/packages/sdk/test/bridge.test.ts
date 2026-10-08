// The Terminal Bridge engine without a bridge: detection over a fake fetch, the HTTP layer's
// headers, retries and error mapping, the route mapping of the Engine seam, and the event
// stream's reconnection over a fake WebSocket.
import type { SessionEvent } from '@bilt/pos-protocol';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  BRIDGE_DEFAULT_PORT,
  BridgeEngine,
  BridgeMissingError,
  BridgeOutdatedError,
  detectBridge,
  localBridge,
} from '../src/bridge/index';
import { resolveOptions } from '../src/bridge/options';
import { BiltPos, EngineOutdatedError, EngineUnavailableError, SessionError } from '../src/index';

type Call = { url: string; init: RequestInit & { targetAddressSpace?: string } };

function json(body: unknown, status = 200): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function health(versions: string[] = ['2']) {
  return {
    host: 'bridge',
    hostVersion: '0.1',
    sdkVersion: '0.30',
    protocolVersions: versions,
  };
}

/** A fetch answering per port: `ports[port]` is the health body, or `undefined` for silence. */
function fakeFetch(
  answer: (url: URL, init: Call['init']) => Response | Promise<Response> | undefined,
): { fetch: typeof globalThis.fetch; calls: Call[] } {
  const calls: Call[] = [];
  const fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    const url = new URL(
      typeof input === 'string' ? input : input instanceof URL ? input.href : input.url,
    );
    calls.push({ url: url.href, init: (init ?? {}) as Call['init'] });
    const response = await answer(url, (init ?? {}) as Call['init']);
    if (!response) throw new TypeError('fetch failed');
    return response;
  }) as typeof globalThis.fetch;
  return { fetch, calls };
}

afterEach(() => {
  vi.useRealTimers();
});

describe('detectBridge', () => {
  it('probes the primary port and the fallback range at once with loopback hints', async () => {
    const { fetch, calls } = fakeFetch((url) =>
      url.port === String(BRIDGE_DEFAULT_PORT + 2) ? json(health()) : undefined,
    );
    const detection = await detectBridge({ fetch });
    expect(detection).toMatchObject({
      status: 'ready',
      baseUrl: `http://127.0.0.1:${BRIDGE_DEFAULT_PORT + 2}`,
    });
    expect(calls).toHaveLength(11);
    expect(calls[0]!.url).toBe(`http://127.0.0.1:${BRIDGE_DEFAULT_PORT}/health`);
    expect(calls[0]!.init.targetAddressSpace).toBe('loopback');
    expect(calls[0]!.init.signal).toBeInstanceOf(AbortSignal);
  });

  it('prefers the lowest compatible port over an outdated one', async () => {
    const { fetch } = fakeFetch((url) => {
      if (url.port === '48333') return json(health(['0']));
      if (url.port === '48334') return json(health(['1', '2']));
      return undefined;
    });
    expect(await detectBridge({ fetch })).toMatchObject({
      status: 'ready',
      baseUrl: 'http://127.0.0.1:48334',
    });
  });

  it('reports outdated and missing bridges', async () => {
    const outdated = fakeFetch((url) => (url.port === '48333' ? json(health(['0'])) : undefined));
    expect(await detectBridge({ fetch: outdated.fetch })).toMatchObject({ status: 'outdated' });
    const missing = fakeFetch(() => undefined);
    const detection = await detectBridge({ fetch: missing.fetch, fallbackPorts: 1 });
    expect(detection).toEqual({
      status: 'missing',
      probed: ['http://127.0.0.1:48333/health', 'http://127.0.0.1:48334/health'],
    });
  });

  it('treats a slow port as silent after the health timeout', async () => {
    const { fetch } = fakeFetch(
      (_url, init) =>
        new Promise<Response>((_resolve, reject) => {
          init.signal?.addEventListener('abort', () =>
            reject(new DOMException('aborted', 'AbortError')),
          );
        }),
    );
    const detection = await detectBridge({ fetch, fallbackPorts: 0, healthTimeoutMs: 20 });
    expect(detection.status).toBe('missing');
  });
});

describe('localBridge', () => {
  it('throws BridgeMissingError, an EngineUnavailableError, when nothing answers', async () => {
    const { fetch } = fakeFetch(() => undefined);
    const error = await BiltPos.connect(localBridge({ fetch, fallbackPorts: 0 })).catch(
      (e: unknown) => e,
    );
    expect(error).toBeInstanceOf(BridgeMissingError);
    expect(error).toBeInstanceOf(EngineUnavailableError);
    expect((error as BridgeMissingError).probed).toEqual(['http://127.0.0.1:48333/health']);
  });

  it('throws BridgeOutdatedError, an EngineOutdatedError, for an old bridge', async () => {
    const { fetch } = fakeFetch(() => json(health(['0'])));
    const error = await BiltPos.connect(localBridge({ fetch, fallbackPorts: 0 })).catch(
      (e: unknown) => e,
    );
    expect(error).toBeInstanceOf(BridgeOutdatedError);
    expect(error).toBeInstanceOf(EngineOutdatedError);
    expect(error).toMatchObject({
      required: '2',
      available: ['0'],
      baseUrl: 'http://127.0.0.1:48333',
    });
  });

  it('connects and exposes the bridge capabilities', async () => {
    const { fetch } = fakeFetch(() => json(health()));
    const pos = await BiltPos.connect(localBridge({ fetch, fallbackPorts: 0, port: 50000 }));
    expect(pos.capabilities).toMatchObject({
      name: 'bridge',
      survivesPageReload: true,
      worksOffline: true,
      supportsWidgets: true,
    });
    await pos.close();
  });
});

describe('BridgeEngine over HTTP', () => {
  function engine(
    answer: Parameters<typeof fakeFetch>[0],
    extra: Parameters<typeof resolveOptions>[0] = {},
  ) {
    const { fetch, calls } = fakeFetch(answer);
    const options = resolveOptions({ fetch, retries: 2, ...extra });
    return { engine: new BridgeEngine('http://127.0.0.1:48333', options), calls };
  }

  it('sends JSON with the idempotency key and bearer token, and maps routes', async () => {
    const { engine: e, calls } = engine(
      (url) => {
        if (url.pathname.endsWith('/member') && url.pathname.includes('/v1/sessions/'))
          return json(undefined, 204);
        return json({ ok: true, path: url.pathname });
      },
      { bearerToken: 'tok' },
    );
    await e.basket(
      's 1',
      { kind: 'add', item: { sku: 'A', description: 'A', unitPrice: '1' }, itemId: '7' },
      { idempotencyKey: 'k1' },
    );
    const add = calls[0]!;
    expect(add.url).toBe('http://127.0.0.1:48333/v1/sessions/s%201/basket/items');
    expect(add.init.method).toBe('POST');
    expect(add.init.headers).toMatchObject({
      'Idempotency-Key': 'k1',
      Authorization: 'Bearer tok',
      'Content-Type': 'application/json',
    });
    expect(JSON.parse(add.init.body as string)).toEqual({
      sku: 'A',
      description: 'A',
      unitPrice: '1',
      itemId: '7',
    });
    expect(add.init.targetAddressSpace).toBe('loopback');

    expect(await e.member('s1', { kind: 'get' })).toBeNull();
    await e.request('s1', { type: 'end' }, { idempotencyKey: 'k2' });
    expect(calls.at(-1)).toMatchObject({
      url: 'http://127.0.0.1:48333/v1/sessions/s1',
      init: { method: 'DELETE' },
    });
    await e.request('s1', { type: 'forceEnd', reason: 'closing' });
    expect(calls.at(-1)!.url).toMatch(/\/force-end$/);
    await e.abort('s1');
    expect(calls.at(-1)!.url).toMatch(/\/v1\/sessions\/s1\/abort$/);
    await e.abort('s1', 'op1');
    expect(calls.at(-1)!.url).toMatch(/\/operations\/op1\/abort$/);
    await e.basket('s1', { kind: 'patch', itemId: '3', quantity: 0 });
    expect(calls.at(-1)).toMatchObject({ init: { method: 'PATCH', body: '{"quantity":0}' } });
    await e.terminal('P', { kind: 'totals', storeLocation: 'STR' });
    expect(calls.at(-1)!.url).toBe(
      'http://127.0.0.1:48333/v1/terminal/totals?poiId=P&storeLocation=STR',
    );
    await e.terminal(undefined, { kind: 'diagnose' });
    expect(calls.at(-1)!.url).toBe('http://127.0.0.1:48333/v1/terminal/diagnose');
    await e.widgetAction('s1', 'retail-media', { kind: 'viewed', creativeId: 'c', placement: 'p' });
    expect(calls.at(-1)!.url).toMatch(/\/widgets\/retail-media\/actions$/);
  });

  it('turns an error body into a SessionError carrying the HTTP status', async () => {
    const { engine: e } = engine(() => json({ code: 'INVALID_STATE', message: 'frozen' }, 409));
    const error = await e.basket('s1', { kind: 'clear' }).catch((err: unknown) => err);
    expect(error).toBeInstanceOf(SessionError);
    expect(error).toMatchObject({
      code: 'INVALID_STATE',
      message: 'frozen',
      details: { httpStatus: 409 },
    });
    const { engine: e2 } = engine(() => new Response('nope', { status: 502 }));
    await expect(e2.health()).rejects.toMatchObject({
      code: 'UNKNOWN',
      details: { httpStatus: 502 },
    });
  });

  it('retries a request that never reached the bridge with the same idempotency key', async () => {
    let attempts = 0;
    const { engine: e, calls } = engine(() => (++attempts < 3 ? undefined : json({ id: 'op' })));
    await e.request('s1', { type: 'settle' }, { idempotencyKey: 'same' });
    expect(calls).toHaveLength(3);
    expect(calls.map((c) => (c.init.headers as Record<string, string>)['Idempotency-Key'])).toEqual(
      ['same', 'same', 'same'],
    );
  });

  it('gives up as EngineUnavailableError once the retries are spent', async () => {
    const { engine: e, calls } = engine(() => undefined);
    await expect(e.health()).rejects.toBeInstanceOf(EngineUnavailableError);
    expect(calls).toHaveLength(3);
  });
});

/** A WebSocket the test opens, feeds and closes by hand; one instance per connection attempt. */
class FakeSocket {
  static instances: FakeSocket[] = [];
  static failToOpen = false;
  onopen: ((e: Event) => void) | null = null;
  onmessage: ((e: MessageEvent) => void) | null = null;
  onclose: ((e: CloseEvent) => void) | null = null;
  onerror: ((e: Event) => void) | null = null;
  closedByClient = false;
  constructor(readonly url: string) {
    FakeSocket.instances.push(this);
    queueMicrotask(() => {
      if (FakeSocket.failToOpen) this.serverClose(1006);
      else this.onopen?.(new Event('open'));
    });
  }
  send(event: Partial<SessionEvent> & { seq: number; type: string }) {
    this.onmessage?.({
      data: JSON.stringify({ at: 'now', payload: {}, ...event }),
    } as MessageEvent);
  }
  serverClose(code: number, reason = '') {
    this.onclose?.({ code, reason } as CloseEvent);
  }
  close() {
    this.closedByClient = true;
  }
}

describe('the bridge event stream', () => {
  afterEach(() => {
    FakeSocket.instances = [];
    FakeSocket.failToOpen = false;
  });

  function stream(since?: number) {
    const options = resolveOptions({
      fetch: fakeFetch(() => undefined).fetch,
      webSocket: FakeSocket as unknown as typeof WebSocket,
    });
    const e = new BridgeEngine('http://127.0.0.1:48333', options);
    const received: SessionEvent[] = [];
    const done = (async () => {
      for await (const event of e.events('s1', since)) received.push(event);
    })();
    return { e, received, done };
  }

  it('delivers events in order, drops duplicate seqs and ends after session.ended', async () => {
    const { received, done } = stream();
    await vi.waitFor(() => expect(FakeSocket.instances).toHaveLength(1));
    const socket = FakeSocket.instances[0]!;
    expect(socket.url).toBe('ws://127.0.0.1:48333/v1/sessions/s1/events');
    socket.send({ seq: 1, type: 'session.started' });
    socket.send({ seq: 2, type: 'basket.changed' });
    socket.send({ seq: 2, type: 'basket.changed' });
    socket.send({ seq: 3, type: 'session.ended' });
    socket.serverClose(1000);
    await done;
    expect(received.map((e) => e.seq)).toEqual([1, 2, 3]);
  });

  it('reconnects with since after a drop, backing off exponentially', async () => {
    vi.useFakeTimers();
    const { received, done } = stream();
    await vi.advanceTimersByTimeAsync(0);
    const first = FakeSocket.instances[0]!;
    first.send({ seq: 1, type: 'session.started' });
    first.send({ seq: 2, type: 'basket.changed' });
    first.serverClose(1006);
    await vi.advanceTimersByTimeAsync(249);
    expect(FakeSocket.instances).toHaveLength(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(FakeSocket.instances).toHaveLength(2);
    const second = FakeSocket.instances[1]!;
    expect(second.url).toMatch(/\?since=2$/);
    second.serverClose(1006); // nothing new arrived: the delay doubles
    await vi.advanceTimersByTimeAsync(499);
    expect(FakeSocket.instances).toHaveLength(2);
    await vi.advanceTimersByTimeAsync(1);
    const third = FakeSocket.instances[2]!;
    third.send({ seq: 2, type: 'basket.changed' }); // a replay of what we had
    third.send({ seq: 3, type: 'session.ended' });
    third.serverClose(1000);
    await done;
    expect(received.map((e) => e.seq)).toEqual([1, 2, 3]);
  });

  it('starts over from the oldest event when the host answers 4410', async () => {
    vi.useFakeTimers();
    const { received, done } = stream(5);
    await vi.advanceTimersByTimeAsync(0);
    const first = FakeSocket.instances[0]!;
    expect(first.url).toMatch(/\?since=5$/);
    first.serverClose(4410, 'since is no longer buffered');
    await vi.advanceTimersByTimeAsync(0);
    const second = FakeSocket.instances[1]!;
    expect(second.url).not.toMatch(/since=/);
    second.send({ seq: 9, type: 'context.changed' });
    second.send({ seq: 10, type: 'session.ended' });
    await done;
    expect(received.map((e) => e.seq)).toEqual([9, 10]);
  });

  it('fails the iteration when the session is unknown to the bridge', async () => {
    const { done } = stream();
    await vi.waitFor(() => expect(FakeSocket.instances).toHaveLength(1));
    FakeSocket.instances[0]!.serverClose(4404, 'session not found');
    await expect(done).rejects.toMatchObject({ code: 'NOT_FOUND' });
  });

  it('closes the socket when the engine closes', async () => {
    const { e, done } = stream();
    await vi.waitFor(() => expect(FakeSocket.instances).toHaveLength(1));
    await e.close();
    await done;
    expect(FakeSocket.instances[0]!.closedByClient).toBe(true);
  });

  it('falls back to EventSource when a WebSocket cannot be opened', async () => {
    FakeSocket.failToOpen = true;
    const sources: FakeSource[] = [];
    class FakeSource {
      static readonly CLOSED = 2;
      readonly listeners = new Map<string, (e: MessageEvent) => void>();
      onopen: ((e: Event) => void) | null = null;
      onerror: ((e: Event) => void) | null = null;
      readyState = 0;
      constructor(readonly url: string) {
        sources.push(this);
        queueMicrotask(() => this.onopen?.(new Event('open')));
      }
      addEventListener(type: string, listener: (e: MessageEvent) => void) {
        this.listeners.set(type, listener);
      }
      close() {
        this.readyState = 2;
      }
      emit(type: string, event: object) {
        this.listeners.get(type)?.({ data: JSON.stringify(event) } as MessageEvent);
      }
    }
    const options = resolveOptions({
      fetch: fakeFetch(() => undefined).fetch,
      webSocket: FakeSocket as unknown as typeof WebSocket,
      eventSource: FakeSource as unknown as typeof EventSource,
    });
    const e = new BridgeEngine('http://127.0.0.1:48333', options);
    const received: SessionEvent[] = [];
    const done = (async () => {
      for await (const event of e.events('s1', 1)) received.push(event);
    })();
    await vi.waitFor(() => expect(sources).toHaveLength(1));
    expect(sources[0]!.url).toBe('http://127.0.0.1:48333/v1/sessions/s1/events?since=1');
    sources[0]!.emit('member.changed', {
      seq: 2,
      type: 'member.changed',
      at: 'now',
      payload: { member: null },
    });
    sources[0]!.emit('session.ended', { seq: 3, type: 'session.ended', at: 'now', payload: {} });
    await done;
    expect(received.map((ev) => ev.type)).toEqual(['member.changed', 'session.ended']);
  });

  it('resubscribes over EventSource from the oldest event when the replay position is refused', async () => {
    FakeSocket.failToOpen = true;
    const sources: RefusableSource[] = [];
    class RefusableSource {
      static readonly CLOSED = 2;
      readonly listeners = new Map<string, (e: MessageEvent) => void>();
      onopen: ((e: Event) => void) | null = null;
      onerror: ((e: Event) => void) | null = null;
      readyState = 0;
      constructor(readonly url: string) {
        sources.push(this);
        // The first connection is refused with a 410, which EventSource reports as a bare error.
        queueMicrotask(() => {
          if (sources.length === 1) {
            this.readyState = 2;
            this.onerror?.(new Event('error'));
          } else this.onopen?.(new Event('open'));
        });
      }
      addEventListener(type: string, listener: (e: MessageEvent) => void) {
        this.listeners.set(type, listener);
      }
      close() {
        this.readyState = 2;
      }
      emit(type: string, event: object) {
        this.listeners.get(type)?.({ data: JSON.stringify(event) } as MessageEvent);
      }
    }
    const options = resolveOptions({
      fetch: fakeFetch(() => undefined).fetch,
      webSocket: FakeSocket as unknown as typeof WebSocket,
      eventSource: RefusableSource as unknown as typeof EventSource,
    });
    const e = new BridgeEngine('http://127.0.0.1:48333', options);
    const received: SessionEvent[] = [];
    const done = (async () => {
      for await (const event of e.events('s1', 5)) received.push(event);
    })();
    await vi.waitFor(() => expect(sources).toHaveLength(2));
    expect(sources[0]!.url).toMatch(/since=5/);
    expect(sources[1]!.url).not.toMatch(/since=/);
    sources[1]!.emit('session.ended', { seq: 9, type: 'session.ended', at: 'now', payload: {} });
    await done;
    expect(received.map((ev) => ev.seq)).toEqual([9]);
  });
});
