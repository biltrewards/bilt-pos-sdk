import type { SessionEventPayload, SessionEventType } from '@bilt/pos-sdk';
import { SessionError } from '@bilt/pos-sdk';
import { useSyncExternalStore } from 'react';

/** Wall-clock `HH:MM:SS`, the desktop log's timestamp. */
export function timestamp(at: Date = new Date()): string {
  return at.toTimeString().slice(0, 8);
}

type Listener = () => void;

const MAX_LINES = 2000;

/**
 * One of the log panel's feeds: an append-only list of timestamped lines that React reads
 * through `useSyncExternalStore`. Kept outside React so the controller and the SDK callbacks
 * write to it directly, and so it outlives a reconnect.
 */
export class LineLog {
  private lines: readonly string[] = [];
  private readonly listeners = new Set<Listener>();

  add(message: string): void {
    const next = [...this.lines, `${timestamp()} ${message}`];
    this.lines = next.length > MAX_LINES ? next.slice(next.length - MAX_LINES) : next;
    for (const listener of this.listeners) listener();
  }

  snapshot(): readonly string[] {
    return this.lines;
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }
}

/** A feed's lines as state, re-rendered on every addition. */
export function useLines(log: LineLog): readonly string[] {
  return useSyncExternalStore(
    (listener) => log.subscribe(listener),
    () => log.snapshot(),
    () => log.snapshot(),
  );
}

/** One line for any failure: the `SessionError` code when there is one, the message otherwise. */
export function describeError(error: unknown): string {
  if (error instanceof SessionError) return `${error.code}: ${error.message}`;
  if (error instanceof Error) return error.message;
  return String(error);
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
