import type { SessionEvent, SessionEventType } from '@bilt/pos-protocol';
import { EngineUnavailableError, SessionError } from '../errors';
import type { BridgeHttp } from './http';
import type { ResolvedBridgeOptions } from './options';

/** Every event type the SSE fallback must listen for, since the host names each frame's `event`. */
const EVENT_TYPES = [
  'session.started',
  'basket.changed',
  'member.changed',
  'context.changed',
  'operation.step',
  'operation.movement',
  'operation.completed',
  'widget.rendering',
  'widget.clear',
  'widget.offer',
  'widget.interaction',
  'background.error',
  'session.ended',
] as const satisfies readonly SessionEventType[];

type MissingEventType = Exclude<SessionEventType, (typeof EVENT_TYPES)[number]>;
const _everyEventTypeListed: MissingEventType extends never ? true : never = true;
void _everyEventTypeListed;

const RECONNECT_BASE_MS = 250;
const RECONNECT_MAX_MS = 10_000;

/** WebSocket close codes the host uses; the 44xx ones mirror the HTTP statuses. */
const WS_NORMAL = 1000;
const WS_BAD_REQUEST = 4400;
const WS_UNAUTHORIZED = 4401;
const WS_NOT_FOUND = 4404;
const WS_GONE = 4410;

function sleep(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    if (signal.aborted) return resolve();
    const timer = setTimeout(done, ms);
    function done() {
      signal.removeEventListener('abort', done);
      clearTimeout(timer);
      resolve();
    }
    signal.addEventListener('abort', done, { once: true });
  });
}

/** What one transport attempt ended with. */
type Outcome = 'ended' | 'retry' | 'gap' | 'unsupported';

function decodeFrame(data: unknown): string | undefined {
  if (typeof data === 'string') return data;
  if (data instanceof ArrayBuffer) return new TextDecoder().decode(data);
  if (ArrayBuffer.isView(data)) return new TextDecoder().decode(data);
  return undefined;
}

/**
 * One session's event stream from the bridge, as an async iterable the core consumes.
 *
 * It opens a WebSocket to `.../events?since=<seq>`, hands each frame on as a `SessionEvent`,
 * drops anything with a `seq` it has already delivered, and on any close before
 * `session.ended` reconnects with the last `seq` it saw, backing off exponentially from 250 ms
 * to 10 s. When a WebSocket cannot be opened at all and an `EventSource` is available, the same
 * URL is read as Server-Sent Events. A 4410 / 410 (the `since` fell out of the host's replay
 * window; over SSE, any refusal before the first frame) resubscribes from the oldest buffered event; the resulting jump in `seq` is the
 * core's cue to re-read state. The iteration ends after `session.ended`, when the session is
 * unknown to the host (4404), or when the engine closes.
 */
export class BridgeEventStream implements AsyncIterable<SessionEvent> {
  private readonly queue: SessionEvent[] = [];
  private waiter: (() => void) | undefined;
  private finished = false;
  private failure: unknown;
  private lastSeq: number;
  private readonly closer = new AbortController();
  private transport: { close(): void } | undefined;
  private started = false;

  constructor(
    private readonly http: BridgeHttp,
    private readonly sessionId: string,
    since: number | undefined,
    private readonly options: ResolvedBridgeOptions,
  ) {
    this.lastSeq = since ?? 0;
  }

  /** Closes the transport; a pending `next()` resolves `done`. */
  close(): void {
    this.finish();
  }

  [Symbol.asyncIterator](): AsyncIterator<SessionEvent> {
    if (!this.started) {
      this.started = true;
      void this.run();
    }
    return {
      next: async (): Promise<IteratorResult<SessionEvent>> => {
        for (;;) {
          const event = this.queue.shift();
          if (event) return { value: event, done: false };
          if (this.finished) {
            if (this.failure !== undefined) {
              const failure = this.failure;
              this.failure = undefined;
              throw failure;
            }
            return { value: undefined, done: true };
          }
          await new Promise<void>((resolve) => {
            this.waiter = resolve;
          });
        }
      },
      return: async (): Promise<IteratorResult<SessionEvent>> => {
        this.finish();
        return { value: undefined, done: true };
      },
    };
  }

  private wake(): void {
    const waiter = this.waiter;
    this.waiter = undefined;
    waiter?.();
  }

  private finish(failure?: unknown): void {
    if (this.finished) return;
    this.finished = true;
    this.failure = failure;
    this.closer.abort();
    this.transport?.close();
    this.transport = undefined;
    this.wake();
  }

  private deliver(raw: string): boolean {
    let event: SessionEvent;
    try {
      event = JSON.parse(raw) as SessionEvent;
    } catch {
      return false;
    }
    if (typeof event.seq !== 'number' || event.seq <= this.lastSeq) return false;
    this.lastSeq = event.seq;
    this.queue.push(event);
    this.wake();
    return event.type === 'session.ended';
  }

  private url(scheme: 'http' | 'ws'): string {
    const url = new URL(
      this.http.url(
        `/v1/sessions/${encodeURIComponent(this.sessionId)}/events`,
        this.lastSeq > 0 ? { since: this.lastSeq } : {},
      ),
    );
    if (scheme === 'ws') url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
    return url.toString();
  }

  private async run(): Promise<void> {
    let delay = RECONNECT_BASE_MS;
    let webSocketUsable = this.options.webSocket !== undefined;
    while (!this.finished) {
      let outcome: Outcome;
      const before = this.lastSeq;
      if (webSocketUsable) {
        outcome = await this.overWebSocket();
        if (outcome === 'unsupported') {
          webSocketUsable = false;
          continue;
        }
      } else if (this.options.eventSource) {
        outcome = await this.overServerSentEvents();
        if (outcome === 'unsupported') {
          webSocketUsable = this.options.webSocket !== undefined;
          outcome = 'retry';
        }
      } else {
        this.finish(
          new EngineUnavailableError(
            'no WebSocket or EventSource is available for the bridge event stream',
          ),
        );
        return;
      }
      if (this.finished || outcome === 'ended') {
        this.finish();
        return;
      }
      if (outcome === 'gap') {
        // The host no longer buffers our position: start over from its oldest event.
        this.lastSeq = 0;
        continue;
      }
      if (this.lastSeq > before) delay = RECONNECT_BASE_MS;
      await sleep(delay, this.closer.signal);
      delay = Math.min(delay * 2, RECONNECT_MAX_MS);
    }
  }

  private overWebSocket(): Promise<Outcome> {
    const WebSocketImpl = this.options.webSocket;
    if (!WebSocketImpl) return Promise.resolve('unsupported');
    return new Promise<Outcome>((resolve) => {
      let socket: WebSocket;
      try {
        socket = new WebSocketImpl(this.url('ws'));
      } catch {
        resolve('unsupported');
        return;
      }
      let opened = false;
      let ended = false;
      let settled = false;
      const done = (outcome: Outcome) => {
        if (settled) return;
        settled = true;
        this.transport = undefined;
        resolve(outcome);
      };
      this.transport = {
        close: () => {
          try {
            socket.close(WS_NORMAL, 'client closed');
          } catch {
            // closing an already closed socket is fine
          }
          done('ended');
        },
      };
      socket.onopen = () => {
        opened = true;
      };
      socket.onmessage = (message: MessageEvent) => {
        const text = decodeFrame(message.data);
        if (text !== undefined && this.deliver(text)) {
          ended = true;
          try {
            socket.close(WS_NORMAL, 'session ended');
          } catch {
            // the host closes it too
          }
          done('ended');
        }
      };
      socket.onerror = () => {
        // the close event that follows carries the verdict
      };
      socket.onclose = (close: CloseEvent) => {
        if (ended) return done('ended');
        switch (close.code) {
          case WS_GONE:
            return done('gap');
          case WS_NOT_FOUND:
            this.finish(
              new SessionError({
                code: 'NOT_FOUND',
                message: `session ${this.sessionId} is unknown to the bridge`,
              }),
            );
            return done('ended');
          case WS_UNAUTHORIZED:
            this.finish(
              new SessionError({
                code: 'UNAUTHORIZED',
                message: 'the bridge refused the event stream',
              }),
            );
            return done('ended');
          case WS_BAD_REQUEST:
            this.finish(
              new SessionError({ code: 'VALIDATION', message: close.reason || 'bad request' }),
            );
            return done('ended');
          default:
            // A close before `open` means WebSocket itself does not get through (a proxy, a
            // browser policy): fall back to SSE when there is one. A close after `open` is a
            // dropped connection to reconnect.
            return done(opened || !this.options.eventSource ? 'retry' : 'unsupported');
        }
      };
    });
  }

  private overServerSentEvents(): Promise<Outcome> {
    const EventSourceImpl = this.options.eventSource;
    if (!EventSourceImpl) return Promise.resolve('unsupported');
    return new Promise<Outcome>((resolve) => {
      let source: EventSource;
      try {
        source = new EventSourceImpl(this.url('http'));
      } catch {
        resolve('unsupported');
        return;
      }
      let opened = false;
      let settled = false;
      const done = (outcome: Outcome) => {
        if (settled) return;
        settled = true;
        this.transport = undefined;
        source.close();
        resolve(outcome);
      };
      this.transport = { close: () => done('ended') };
      source.onopen = () => {
        opened = true;
      };
      const onFrame = (message: MessageEvent) => {
        const text = decodeFrame(message.data);
        if (text !== undefined && this.deliver(text)) done('ended');
      };
      for (const type of EVENT_TYPES) source.addEventListener(type, onFrame);
      source.addEventListener('error', (message: MessageEvent) => {
        // The host answers an unknown session with an `error` frame and closes.
        const text = decodeFrame(message.data);
        if (text !== undefined) {
          this.finish(new SessionError({ code: 'NOT_FOUND', message: text }));
          done('ended');
        }
      });
      source.onerror = () => {
        if (source.readyState === EventSourceImpl.CLOSED) {
          // EventSource hides the HTTP status, so a refusal before any frame while we hold a
          // position is treated like a 410: resubscribe from the host's oldest event. A plain
          // outage costs nothing, as the core drops the events it has already seen.
          done(opened ? 'retry' : this.lastSeq > 0 ? 'gap' : 'unsupported');
        }
        // CONNECTING: the EventSource is retrying on its own with Last-Event-ID; let it.
      };
    });
  }
}
