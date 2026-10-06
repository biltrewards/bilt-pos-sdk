import type { SessionEventType } from '@bilt/pos-protocol';
import type { ShopperSession } from '@bilt/pos-sdk';
import { useCallback, useRef, useSyncExternalStore } from 'react';

interface Cache<T> {
  session: ShopperSession | null;
  version: number;
  value: T;
}

/**
 * Reads a value off a session and keeps it current with `useSyncExternalStore`, re-reading
 * only after one of `types` fired. The re-read is cached per event, so a getter that allocates
 * (`context.snapshot()`) still hands React a stable reference between events, which is what
 * `useSyncExternalStore` needs to avoid re-rendering forever.
 */
export function useSessionValue<T>(
  session: ShopperSession | null | undefined,
  types: readonly SessionEventType[],
  read: (session: ShopperSession) => T,
  empty: T,
): T {
  const current = session ?? null;
  const version = useRef(0);
  const cache = useRef<Cache<T> | null>(null);
  const readRef = useRef(read);
  readRef.current = read;

  const subscribe = useCallback(
    (onChange: () => void) => {
      if (!current) return () => {};
      const bump = () => {
        version.current += 1;
        onChange();
      };
      const unsubscribes = types.map((type) => current.on(type, bump));
      return () => {
        for (const unsubscribe of unsubscribes) unsubscribe();
      };
    },
    // `types` is a module-level constant at every call site, so its identity is stable.
    [current, types],
  );

  const getSnapshot = useCallback((): T => {
    if (!current) return empty;
    const cached = cache.current;
    if (cached && cached.session === current && cached.version === version.current) {
      return cached.value;
    }
    const value = readRef.current(current);
    cache.current = { session: current, version: version.current, value };
    return value;
  }, [current, empty]);

  return useSyncExternalStore(subscribe, getSnapshot, getSnapshot);
}
