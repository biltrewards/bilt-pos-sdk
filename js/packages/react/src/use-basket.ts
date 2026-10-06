import type { Basket, SessionEventType } from '@bilt/pos-protocol';
import type { SessionBasket, ShopperSession } from '@bilt/pos-sdk';
import { useMemo } from 'react';
import { useSessionValue } from './internal/use-session-value';

/** The updaters of `SessionBasket`, everything but the `current` snapshot. */
export type BasketActions = Omit<SessionBasket, 'current'>;

/** What `useBasket` returns: the live snapshot and the updaters bound to the session. */
export interface UseBasketResult extends BasketActions {
  /** The basket as of the last `basket.changed`; `null` without a session. */
  readonly basket: Basket | null;
}

const EVENTS: readonly SessionEventType[] = ['basket.changed'];

const ACTIONS = [
  'refresh',
  'addItem',
  'removeItem',
  'removeItemBySku',
  'updateItemQuantity',
  'updateItemQuantityBySku',
  'setDiscounts',
  'setDiscountsBySku',
  'setTaxRate',
  'setTaxRateBySku',
  'setTaxAmount',
  'setTaxAmountBySku',
  'setTaxTotal',
  'mutate',
  'replace',
  'clear',
] as const satisfies readonly (keyof BasketActions)[];

function bindActions(session: ShopperSession | null): BasketActions {
  const bound: Partial<Record<keyof BasketActions, unknown>> = {};
  for (const name of ACTIONS) {
    bound[name] = session
      ? (...args: unknown[]) => {
          // Called with the basket as receiver: a class-based SessionBasket reads `this`.
          const { basket } = session;
          return (basket[name] as (...a: unknown[]) => Promise<unknown>).apply(basket, args);
        }
      : () => Promise.reject(new Error(`basket.${name}() called without an open session`));
  }
  return bound as BasketActions;
}

/**
 * The session's basket as React state: re-renders on every `basket.changed`, with the
 * `SessionBasket` updaters bound to the session so a component can scan and adjust lines
 * directly. The updaters reject when there is no session.
 *
 * ```tsx
 * const { basket, addItem, updateItemQuantity } = useBasket(session);
 * ```
 */
export function useBasket(session: ShopperSession | null | undefined): UseBasketResult {
  const basket = useSessionValue<Basket | null>(session, EVENTS, (s) => s.basket.current, null);
  const actions = useMemo(() => bindActions(session ?? null), [session]);
  return useMemo(() => ({ basket, ...actions }), [basket, actions]);
}
