import type { BiltPos } from '@bilt/pos-sdk';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';

/**
 * Where the provider's connection stands. `connecting` while the `connect` factory runs,
 * `connected` once a `BiltPos` is available, `error` when the factory rejected (the error is
 * on the connection), and `closed` after the provider unmounted and released the connection it
 * created.
 */
export type BiltPosConnectionStatus = 'connecting' | 'connected' | 'error' | 'closed';

/**
 * The value `useBiltPos()` returns: the `BiltPos` once connected, the connection state, and a
 * way to try again after a failed connect.
 */
export interface BiltPosConnection {
  /** The connected `BiltPos`, or `null` until the connection is up. */
  readonly pos: BiltPos | null;
  readonly status: BiltPosConnectionStatus;

  /** Why the last connect failed; `null` otherwise. */
  readonly error: Error | null;

  /**
   * Runs the `connect` factory again. The usual move after an `error`, e.g. once the cashier has
   * installed the Terminal Bridge. A no-op for a provider given a ready-made `pos`.
   */
  reconnect(): void;
}

/**
 * Props for `BiltPosProvider`: either a `BiltPos` the page already connected or a `connect`
 * factory the provider runs on mount. With `connect` the provider owns the connection and closes
 * it on unmount; with `pos` the page keeps that responsibility. `connect` is read when the
 * provider mounts and when `reconnect()` is called, so an inline arrow function is fine.
 */
export type BiltPosProviderProps = {
  readonly children?: ReactNode;

  /** Called when a connection attempt fails; the same error is on `useBiltPos().error`. */
  readonly onError?: (error: Error) => void;
} & (
  | { readonly pos: BiltPos; readonly connect?: never }
  | { readonly connect: () => BiltPos | Promise<BiltPos>; readonly pos?: never }
);

const BiltPosContext = createContext<BiltPosConnection | null>(null);
BiltPosContext.displayName = 'BiltPosContext';

function toError(value: unknown): Error {
  return value instanceof Error ? value : new Error(String(value));
}

/**
 * Makes a `BiltPos` available to the hooks below. Typical use connects over the Terminal
 * Bridge and lets the session hooks start lanes against it:
 *
 * ```tsx
 * <BiltPosProvider connect={() => BiltPos.connect(localBridge())}>
 *   <Lane />
 * </BiltPosProvider>
 * ```
 *
 * The connection state is exposed through `useBiltPos()` so the register can show a spinner
 * while connecting and an error with a retry button when the factory rejects.
 */
export function BiltPosProvider(props: BiltPosProviderProps): ReactNode {
  const { children, onError } = props;
  const given = props.pos ?? null;
  const connectRef = useRef(props.connect);
  connectRef.current = props.connect;
  const onErrorRef = useRef(onError);
  onErrorRef.current = onError;

  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<{
    pos: BiltPos | null;
    status: BiltPosConnectionStatus;
    error: Error | null;
  }>(() =>
    given
      ? { pos: given, status: 'connected', error: null }
      : { pos: null, status: 'connecting', error: null },
  );

  useEffect(() => {
    if (given) {
      setState({ pos: given, status: 'connected', error: null });
      return;
    }
    const connect = connectRef.current;
    if (!connect) return;
    let cancelled = false;
    let created: BiltPos | null = null;
    setState({ pos: null, status: 'connecting', error: null });
    let connecting: Promise<BiltPos>;
    try {
      connecting = Promise.resolve(connect());
    } catch (cause: unknown) {
      connecting = Promise.reject(cause);
    }
    connecting.then(
      (pos) => {
        if (cancelled) {
          void pos.close().catch(() => undefined);
          return;
        }
        created = pos;
        setState({ pos, status: 'connected', error: null });
      },
      (cause: unknown) => {
        if (cancelled) return;
        const error = toError(cause);
        setState({ pos: null, status: 'error', error });
        onErrorRef.current?.(error);
      },
    );
    return () => {
      cancelled = true;
      if (created) {
        void created.close().catch(() => undefined);
        setState({ pos: null, status: 'closed', error: null });
      }
    };
  }, [given, attempt]);

  const reconnect = useCallback(() => {
    if (connectRef.current) setAttempt((n) => n + 1);
  }, []);

  const value = useMemo<BiltPosConnection>(
    () => ({ pos: state.pos, status: state.status, error: state.error, reconnect }),
    [state, reconnect],
  );

  return <BiltPosContext.Provider value={value}>{children}</BiltPosContext.Provider>;
}

/**
 * The connection from the nearest `BiltPosProvider`. Throws when there is none, since every
 * other hook in this package needs one.
 */
export function useBiltPos(): BiltPosConnection {
  const connection = useContext(BiltPosContext);
  if (!connection) {
    throw new Error('useBiltPos() needs a <BiltPosProvider> above the component calling it');
  }
  return connection;
}
