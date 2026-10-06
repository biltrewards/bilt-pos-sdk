import type { SessionEventType } from '@bilt/pos-protocol';
import type { SessionEventHandler, ShopperSession } from '@bilt/pos-sdk';
import { useEffect } from 'react';
import { useLatest } from './internal/use-latest';

/**
 * Subscribes to one session event for the component's lifetime. The handler is read fresh on
 * every event, so an inline arrow function works and the subscription is only redone when the
 * session or the event type changes. Does nothing while `session` is `null`.
 *
 * ```ts
 * useSessionEvent(session, 'widget.offer', ({ offer }) => applyOffer(offer));
 * ```
 */
export function useSessionEvent<T extends SessionEventType>(
  session: ShopperSession | null | undefined,
  type: T,
  handler: SessionEventHandler<T>,
): void {
  const latest = useLatest(handler);
  useEffect(() => {
    if (!session) return;
    return session.on(type, (payload) => latest.current(payload));
  }, [session, type, latest]);
}
