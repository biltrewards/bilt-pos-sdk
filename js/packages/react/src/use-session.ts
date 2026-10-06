import type {
  BiltPos,
  ShopperSession,
  ShopperSessionOptions,
  TerminalSessionOptions,
  TerminalShopperSession,
} from '@bilt/pos-sdk';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useBiltPos } from './provider';

/**
 * Where a hook-managed session stands. `idle` until the provider is connected (or while the
 * hook is disabled), `starting` while the host creates it, `open` while it runs, `ending` while
 * `end()` is in flight, `ended` afterwards, and `error` when starting or ending failed.
 */
export type SessionStatus = 'idle' | 'starting' | 'open' | 'ending' | 'ended' | 'error';

/** Options for the session hooks beyond the session's own. */
export interface UseSessionOptions {
  /**
   * Whether to start the session. `false` keeps the hook `idle`, for a register that waits for a
   * lane or terminal to be chosen; flipping it to `true` starts the session then.
   */
  readonly enabled?: boolean;
}

/** What `useShopperSession` and `useTerminalSession` return. */
export interface UseSessionResult<S extends ShopperSession> {
  /** The session once it is open; `null` before and after. */
  readonly session: S | null;
  readonly status: SessionStatus;

  /** Why the session could not be started or ended; `null` otherwise. */
  readonly error: Error | null;

  /**
   * Ends the current session, as `session.end()` does; a refusal rejects and leaves the session
   * open. Resolves at once when there is no open session.
   */
  end(): Promise<void>;

  /**
   * Ends the current session best-effort and starts a new one with the same options: the next
   * shopper on the same lane.
   */
  restart(): void;
}

function toError(value: unknown): Error {
  return value instanceof Error ? value : new Error(String(value));
}

function useManagedSession<S extends ShopperSession>(
  start: (pos: BiltPos) => Promise<S>,
  enabled: boolean,
): UseSessionResult<S> {
  const { pos } = useBiltPos();
  const startRef = useRef(start);
  startRef.current = start;
  const [generation, setGeneration] = useState(0);
  // The previous session's start and disposal; the next start waits on it so a terminal never
  // sees the replacement arrive while the old session is still open or ending.
  const previous = useRef<Promise<void>>(Promise.resolve());
  const [state, setState] = useState<{
    session: S | null;
    status: SessionStatus;
    error: Error | null;
  }>({ session: null, status: 'idle', error: null });

  useEffect(() => {
    if (!pos || !enabled) {
      setState({ session: null, status: 'idle', error: null });
      return;
    }
    let cancelled = false;
    let started: S | null = null;
    let unsubscribe: (() => void) | null = null;
    setState({ session: null, status: 'starting', error: null });
    const lifecycle = previous.current
      .then(() => (cancelled ? undefined : startRef.current(pos)))
      .then(
        (session) => {
          if (!session) return;
          if (cancelled) {
            void session[Symbol.asyncDispose]();
            return;
          }
          started = session;
          unsubscribe = session.on('session.ended', () => {
            setState((previous) =>
              previous.session === session
                ? { ...previous, session: null, status: 'ended' }
                : previous,
            );
          });
          setState({ session, status: 'open', error: null });
        },
        (cause: unknown) => {
          if (!cancelled) setState({ session: null, status: 'error', error: toError(cause) });
        },
      );
    return () => {
      cancelled = true;
      unsubscribe?.();
      previous.current = lifecycle
        .then(() =>
          started && started.state === 'open' ? started[Symbol.asyncDispose]() : undefined,
        )
        .catch(() => undefined);
    };
  }, [pos, enabled, generation]);

  const end = useCallback(async () => {
    const session = state.session;
    if (!session || session.state !== 'open') return;
    setState((previous) => ({ ...previous, status: 'ending', error: null }));
    try {
      await session.end();
      setState((previous) =>
        previous.session === session ? { ...previous, session: null, status: 'ended' } : previous,
      );
    } catch (cause: unknown) {
      setState((previous) =>
        previous.session === session
          ? { ...previous, status: 'error', error: toError(cause) }
          : previous,
      );
      throw cause;
    }
  }, [state.session]);

  const restart = useCallback(() => setGeneration((n) => n + 1), []);

  return useMemo(
    () => ({ session: state.session, status: state.status, error: state.error, end, restart }),
    [state, end, restart],
  );
}

/**
 * Starts a shopper session without a terminal when the component mounts (once the provider is
 * connected) and ends it when the component unmounts. `options` are read when the session
 * starts; to start over with new options call `restart()` or remount the component.
 *
 * ```tsx
 * const { session, status } = useShopperSession({ saleId: 'LANE-3', currency: 'USD' });
 * ```
 */
export function useShopperSession(
  options: ShopperSessionOptions,
  hookOptions: UseSessionOptions = {},
): UseSessionResult<ShopperSession> {
  const optionsRef = useRef(options);
  optionsRef.current = options;
  const start = useCallback((pos: BiltPos) => pos.startShopperSession(optionsRef.current), []);
  return useManagedSession(start, hookOptions.enabled ?? true);
}

/**
 * Starts a session bracketed on a terminal when the component mounts and ends it when the
 * component unmounts, with the same rules as `useShopperSession`. A refused start (terminal
 * unreachable, session already open on it) surfaces as `status: 'error'` with the
 * `SessionError` in `error`.
 *
 * ```tsx
 * const lane = useTerminalSession({
 *   saleId: 'LANE-3', poiId: 'VictaLane-275839164', currency: 'USD', storeLocation: 'STR-0142',
 *   widgets: [{ type: 'retail-media', placements: ['lane-banner'] }],
 * });
 * ```
 */
export function useTerminalSession(
  options: TerminalSessionOptions,
  hookOptions: UseSessionOptions = {},
): UseSessionResult<TerminalShopperSession> {
  const optionsRef = useRef(options);
  optionsRef.current = options;
  const start = useCallback((pos: BiltPos) => pos.startTerminalSession(optionsRef.current), []);
  return useManagedSession(start, hookOptions.enabled ?? true);
}
