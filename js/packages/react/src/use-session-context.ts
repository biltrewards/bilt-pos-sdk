import type { CheckoutPhase, SessionContext, SessionEventType } from '@bilt/pos-protocol';
import type { ShopperSession } from '@bilt/pos-sdk';
import { useCallback, useMemo } from 'react';
import { useSessionValue } from './internal/use-session-value';

/** What `useSessionContext` returns: the checkout context snapshot and its setters. */
export interface UseSessionContextResult {
  /** A consistent copy of the context as of the last `context.changed`; `null` without a session. */
  readonly context: SessionContext | null;

  /** The current phase; `null` without a session. */
  readonly phase: CheckoutPhase | null;

  /** The attributes as they are now, in insertion order; empty without a session. */
  readonly attributes: Readonly<Record<string, string>>;

  setPhase(phase: CheckoutPhase): Promise<void>;
  setAttribute(key: string, value: string): Promise<void>;
  removeAttribute(key: string): Promise<void>;
}

const EVENTS: readonly SessionEventType[] = ['context.changed'];
const NO_ATTRIBUTES: Readonly<Record<string, string>> = Object.freeze({});

function noSession(what: string): Promise<never> {
  return Promise.reject(new Error(`context.${what}() called without an open session`));
}

/**
 * The checkout context as React state: the phase and the attributes widgets target on,
 * re-rendered on `context.changed`. Named `useSessionContext` so it does not shadow React's
 * `useContext`.
 *
 * ```tsx
 * const { phase, setPhase } = useSessionContext(session);
 * ```
 */
export function useSessionContext(
  session: ShopperSession | null | undefined,
): UseSessionContextResult {
  const context = useSessionValue<SessionContext | null>(
    session,
    EVENTS,
    (s) => s.context.snapshot(),
    null,
  );
  const setPhase = useCallback(
    (phase: CheckoutPhase) => (session ? session.context.setPhase(phase) : noSession('setPhase')),
    [session],
  );
  const setAttribute = useCallback(
    (key: string, value: string) =>
      session ? session.context.setAttribute(key, value) : noSession('setAttribute'),
    [session],
  );
  const removeAttribute = useCallback(
    (key: string) =>
      session ? session.context.removeAttribute(key) : noSession('removeAttribute'),
    [session],
  );
  return useMemo(
    () => ({
      context,
      phase: context?.phase ?? null,
      attributes: context?.attributes ?? NO_ATTRIBUTES,
      setPhase,
      setAttribute,
      removeAttribute,
    }),
    [context, setPhase, setAttribute, removeAttribute],
  );
}
