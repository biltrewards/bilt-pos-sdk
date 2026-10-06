import { SessionError, type Operation, type OperationStatus } from '@bilt/pos-sdk';
import { useCallback, useEffect, useMemo, useState } from 'react';

/**
 * Where a tracked operation stands: `idle` when nothing is tracked, one of the SDK's
 * `OperationStatus` values otherwise. A plain promise is `running` until it settles.
 */
export type TrackedOperationStatus = 'idle' | OperationStatus;

/** What `useOperation` returns. */
export interface UseOperationResult<T> {
  readonly status: TrackedOperationStatus;

  /** The operation's value once it `succeeded`. */
  readonly result: T | undefined;

  /** Why it `failed` or was `aborted`; a `SessionError` for session operations. */
  readonly error: Error | null;

  /** `true` while the tracked operation is still going. */
  readonly pending: boolean;

  /** Aborts the tracked operation when it is an SDK `Operation`; a no-op otherwise. */
  abort(): Promise<void>;
}

interface Settled<T> {
  source: PromiseLike<T>;
  outcome: { ok: true; value: T } | { ok: false; error: Error };
}

const STATUS_POLL_MS = 100;

function isOperation<T>(value: PromiseLike<T>): value is Operation<T> {
  return typeof (value as Partial<Operation<T>>).abort === 'function' && 'status' in value;
}

function toError(value: unknown): Error {
  return value instanceof Error ? value : new Error(String(value));
}

/**
 * Tracks any thenable, typically an SDK `Operation`: `const prompt = useOperation(op)` gives a
 * component the status, result and error of `op` as state, re-rendered when it settles. Hold
 * the operation in state and hand it to the hook:
 *
 * ```tsx
 * const [op, setOp] = useState<Operation<IdentifyResult> | null>(null);
 * const identify = useOperation(op);
 * <button onClick={() => setOp(session.identifyMember())} disabled={identify.pending}>
 * ```
 *
 * For an SDK `Operation`, `status` reflects the handle's own `status` (so `awaitingReply` shows
 * while the host waits on the register) re-read every 100 ms while pending, and `abort()` forwards to it.
 */
export function useOperation<T>(
  operation: PromiseLike<T> | null | undefined,
): UseOperationResult<T> {
  const source = operation ?? null;
  const [settled, setSettled] = useState<Settled<T> | null>(null);
  const [observed, setObserved] = useState<OperationStatus | null>(null);

  useEffect(() => {
    if (!source) return;
    let cancelled = false;
    source.then(
      (value) => {
        if (!cancelled) setSettled({ source, outcome: { ok: true, value } });
      },
      (cause: unknown) => {
        if (!cancelled) setSettled({ source, outcome: { ok: false, error: toError(cause) } });
      },
    );
    return () => {
      cancelled = true;
    };
  }, [source]);

  // The handle's `status` is a plain getter with no change event, so while the operation is
  // pending watch it and re-render on a transition (`running` to `awaitingReply`).
  useEffect(() => {
    if (!source || !isOperation(source)) return;
    setObserved(source.status);
    const timer = setInterval(() => setObserved(source.status), STATUS_POLL_MS);
    return () => clearInterval(timer);
  }, [source]);

  const abort = useCallback(async () => {
    if (source && isOperation(source)) await source.abort();
  }, [source]);

  return useMemo<UseOperationResult<T>>(() => {
    if (!source) return { status: 'idle', result: undefined, error: null, pending: false, abort };
    const outcome = settled?.source === source ? settled.outcome : null;
    if (!outcome) {
      const status: TrackedOperationStatus = isOperation(source) ? source.status : 'running';
      const pending = status === 'queued' || status === 'running' || status === 'awaitingReply';
      return { status, result: undefined, error: null, pending, abort };
    }
    if (outcome.ok) {
      return { status: 'succeeded', result: outcome.value, error: null, pending: false, abort };
    }
    const aborted = outcome.error instanceof SessionError && outcome.error.code === 'ABORTED';
    return {
      status: aborted ? 'aborted' : 'failed',
      result: undefined,
      error: outcome.error,
      pending: false,
      abort,
    };
    // `observed` is not read here: it only makes the render that re-reads `source.status` happen.
  }, [source, settled, abort, observed]);
}
