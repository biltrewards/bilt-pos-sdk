import type { SessionEventPayload, SessionEventType, ShopperSession } from '@bilt/pos-sdk';
import { SessionError } from '@bilt/pos-sdk';
import { useEffect, useSyncExternalStore } from 'react';

/** What produced a log line: a session event, an operation's lifecycle, an SDK error, or the page. */
export type LogKind = 'event' | 'operation' | 'error' | 'info';

export interface LogEntry {
  /** The log's own sequence number; the wire `seq` stays inside the engine. */
  readonly seq: number;
  readonly at: string;
  readonly kind: LogKind;
  readonly type: string;
  readonly summary: string;
  readonly payload?: unknown;
}

type Listener = () => void;

const MAX_ENTRIES = 2000;

/**
 * The Log pane's store: an append-only list React reads through `useSyncExternalStore`. Kept
 * outside React so the lane, the panes and the SDK event handlers can all write to it without
 * threading state through props, and so it survives a lane restart.
 */
export class LogStore {
  private entries: readonly LogEntry[] = [];
  private seq = 0;
  private readonly listeners = new Set<Listener>();

  add(kind: LogKind, type: string, summary: string, payload?: unknown): LogEntry {
    const entry: LogEntry = {
      seq: ++this.seq,
      at: new Date().toISOString(),
      kind,
      type,
      summary,
      ...(payload === undefined ? {} : { payload }),
    };
    const next = [...this.entries, entry];
    this.entries = next.length > MAX_ENTRIES ? next.slice(next.length - MAX_ENTRIES) : next;
    this.notify();
    return entry;
  }

  info(type: string, summary: string, payload?: unknown): void {
    this.add('info', type, summary, payload);
  }

  error(type: string, error: unknown): void {
    this.add('error', type, describeError(error), errorPayload(error));
  }

  clear(): void {
    this.entries = [];
    this.notify();
  }

  snapshot(): readonly LogEntry[] {
    return this.entries;
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notify(): void {
    for (const listener of this.listeners) listener();
  }
}

/** The log's entries as state, re-rendered on every addition. */
export function useLogEntries(store: LogStore): readonly LogEntry[] {
  return useSyncExternalStore(
    (listener) => store.subscribe(listener),
    () => store.snapshot(),
    () => store.snapshot(),
  );
}

/** One line for any failure: the `SessionError` code when there is one, the message otherwise. */
export function describeError(error: unknown): string {
  if (error instanceof SessionError) return `${error.code}: ${error.message}`;
  if (error instanceof Error) return error.message;
  return String(error);
}

function errorPayload(error: unknown): unknown {
  if (error instanceof SessionError) {
    return {
      ...error.toJSON(),
      ...(error.abandonedSettlement ? { abandonedSettlement: error.abandonedSettlement } : {}),
    };
  }
  if (error instanceof Error) return { name: error.name, message: error.message };
  return error;
}

/** Every event type the protocol defines, so the log can subscribe to all of them. */
export const SESSION_EVENT_TYPES: readonly SessionEventType[] = [
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
];

/** A one-line summary of an event's payload, for the log table. */
export function summarize<T extends SessionEventType>(
  type: T,
  payload: SessionEventPayload<T>,
): string {
  switch (type) {
    case 'session.started': {
      const p = payload as SessionEventPayload<'session.started'>;
      return `session ${p.session.id} (${p.session.kind}) in ${p.context.phase}`;
    }
    case 'basket.changed': {
      const p = payload as SessionEventPayload<'basket.changed'>;
      return `${p.source.toLowerCase()}: ${p.current.items.length} line(s), total ${p.current.grandTotal} (+${p.added.length} −${p.removed.length})`;
    }
    case 'member.changed': {
      const p = payload as SessionEventPayload<'member.changed'>;
      if (!p.member) return 'guest';
      return p.member.resolved
        ? `member ${p.member.memberId} (${p.member.pointBalance} pts, ${p.member.rewards.length} reward(s))`
        : `pending ${p.member.resolver?.type.toLowerCase() ?? 'lookup'} ${p.member.resolver?.value ?? ''}`;
    }
    case 'context.changed': {
      const p = payload as SessionEventPayload<'context.changed'>;
      return `phase ${p.phase}, ${Object.keys(p.attributes).length} attribute(s)`;
    }
    case 'operation.step': {
      const p = payload as SessionEventPayload<'operation.step'>;
      return `${p.kind} on ${p.operationId}, deadline ${p.deadlineAt}`;
    }
    case 'operation.movement': {
      const p = payload as SessionEventPayload<'operation.movement'>;
      return `${p.movement.step} ${p.movement.amount}${p.movement.poiTransactionId ? ` (txn ${p.movement.poiTransactionId})` : ''}`;
    }
    case 'operation.completed': {
      const p = payload as SessionEventPayload<'operation.completed'>;
      return `${p.type} ${p.id} ${p.status}${p.error ? `: ${p.error.code}` : ''}`;
    }
    case 'widget.rendering': {
      const p = payload as SessionEventPayload<'widget.rendering'>;
      return `${p.placement}: ${p.rendering.media.type} ${p.rendering.creativeId} "${p.rendering.headline}"`;
    }
    case 'widget.clear': {
      const p = payload as SessionEventPayload<'widget.clear'>;
      return `${p.placement} cleared`;
    }
    case 'widget.offer': {
      const p = payload as SessionEventPayload<'widget.offer'>;
      return `offer ${p.offer.id} (${p.offer.scope}${p.offer.amount ? ` ${p.offer.amount} off` : ''}${p.offer.percentage ? ` ${p.offer.percentage}% off` : ''})`;
    }
    case 'widget.interaction': {
      const p = payload as SessionEventPayload<'widget.interaction'>;
      return `${p.interaction.kind} on ${p.interaction.creativeId}`;
    }
    case 'background.error': {
      const p = payload as SessionEventPayload<'background.error'>;
      return `${p.code}: ${p.message}`;
    }
    case 'session.ended': {
      const p = payload as SessionEventPayload<'session.ended'>;
      return `session ${p.sessionId} ended${p.forced ? ' (forced)' : ''}`;
    }
    default:
      return '';
  }
}

/** Logs every event of a session for as long as the component is mounted. */
export function useSessionLogging(
  session: ShopperSession | null | undefined,
  store: LogStore,
): void {
  useEffect(() => {
    if (!session) return;
    const offs = SESSION_EVENT_TYPES.map((type) =>
      session.on(type, (payload) =>
        store.add(
          type === 'background.error' ? 'error' : 'event',
          type,
          summarize(type, payload),
          payload,
        ),
      ),
    );
    return () => offs.forEach((off) => off());
  }, [session, store]);
}

/**
 * Tracks an operation's lifecycle in the log: when it started, and how it ended. Returns the
 * operation, so a call site can keep awaiting it.
 */
export function track<T>(
  store: LogStore,
  label: string,
  operation: PromiseLike<T>,
): PromiseLike<T> {
  store.add('operation', label, 'started');
  operation.then(
    (result) => store.add('operation', label, 'succeeded', result),
    (error: unknown) =>
      store.add('operation', label, `failed: ${describeError(error)}`, errorPayload(error)),
  );
  return operation;
}

/** Everything a bug report needs, as text for the clipboard. */
export function diagnostics(store: LogStore, context: Record<string, unknown>): string {
  return JSON.stringify(
    { exportedAt: new Date().toISOString(), ...context, log: store.snapshot() },
    null,
    2,
  );
}
