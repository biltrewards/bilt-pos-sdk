import type {
  GiftCardPaymentResult,
  Money,
  PointRedemptionResult,
  RebateRedemptionResult,
  SettlementContext,
  SettlementFailure,
  SettlementMovement,
  SettlementRecovery,
  SettlementResult,
} from '@bilt/pos-protocol';
import {
  SessionError,
  type Operation,
  type SettleOptions,
  type SettlementRecoveryAction,
  type StepInfo,
  type TerminalShopperSession,
} from '@bilt/pos-sdk';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

/**
 * Where the hook's settlement stands: `idle` before `settle()`, `running` while the host works,
 * `awaitingReply` while a step waits on `reply()`, then `succeeded`, `failed` or `aborted`.
 */
export type SettlementStatus =
  'idle' | 'running' | 'awaitingReply' | 'succeeded' | 'failed' | 'aborted';

/** The step kinds the hook can hold open for an interactive answer. */
export type InteractiveStepKind = 'BEFORE_STEP' | 'TOTAL_REQUIRED' | 'RECOVERY_REQUIRED';

/** Options for `useSettlement`. */
export interface UseSettlementOptions {
  /**
   * Which steps the hook surfaces as `pendingStep` and holds open until `reply()`; the rest take
   * their SDK default at once. Empty by default, because a register that does not render
   * `pendingStep` would otherwise hang every settlement until the host's deadline. A handler
   * passed to `settle()` for a step always wins over this list.
   */
  readonly interactive?: readonly InteractiveStepKind[];
}

/** What every pending step carries; `signal` aborts at `deadline`. */
export interface PendingStepBase extends StepInfo {
  /** Answers the step with its SDK default, e.g. the suggested total or `ABORT`. */
  useDefault(): void;
}

/** A `TOTAL_REQUIRED` step: rebates, points or a gift card moved and the register sets the new running total. */
export interface PendingTotalStep extends PendingStepBase {
  readonly kind: 'TOTAL_REQUIRED';
  readonly step: 'REBATE_REDEMPTION' | 'POINT_REDEMPTION' | 'STORED_VALUE_CHARGE';

  /** The default: the previous total minus the step's amount. */
  readonly suggestedTotal: Money;
  readonly rebates?: RebateRedemptionResult;
  readonly points?: PointRedemptionResult;
  readonly giftCard?: GiftCardPaymentResult;
}

/** A `RECOVERY_REQUIRED` step: a charge-side failure waiting for the register's recovery decision. */
export interface PendingRecoveryStep extends PendingStepBase {
  readonly kind: 'RECOVERY_REQUIRED';
  readonly failure: SettlementFailure;
}

/** A `BEFORE_STEP` step: the register may supply the `SaleTransactionID` for the next step. */
export interface PendingBeforeStep extends PendingStepBase {
  readonly kind: 'BEFORE_STEP';
  readonly context: SettlementContext;
}

export type PendingSettlementStep = PendingTotalStep | PendingRecoveryStep | PendingBeforeStep;

/** The answer to a pending step; the field must match the step's kind. */
export type SettlementStepReply =
  | { readonly total: Money }
  | { readonly recovery: SettlementRecovery | SettlementRecoveryAction }
  | { readonly saleTransactionId: string | null | undefined };

/** What `useSettlement` returns. */
export interface UseSettlementResult {
  /**
   * Starts a settlement with `session.settle(options)` and tracks it. Handlers in `options` run
   * as they would on the session; steps listed in `interactive` and not handled there become
   * `pendingStep`. Throws without an open terminal session or while one settlement is running.
   */
  settle(options?: SettleOptions): Operation<SettlementResult>;

  readonly status: SettlementStatus;

  /** The step waiting on `reply()`, or `null`. */
  readonly pendingStep: PendingSettlementStep | null;

  /**
   * Answers `pendingStep`. Throws when no step is pending or the answer does not fit the step's
   * kind (a `total` for a `RECOVERY_REQUIRED` step, say).
   */
  reply(answer: SettlementStepReply): void;

  /** The settlement's result once it `succeeded`. */
  readonly result: SettlementResult | null;

  /** Why it `failed` or was `aborted`: a `SessionError`, with `abandonedSettlement` set after `ABANDON`. */
  readonly error: Error | null;

  /** Every movement the running or last settlement reported, in order. */
  readonly movements: readonly SettlementMovement[];

  /** The `Operation` handle of the running or last settlement. */
  readonly operation: Operation<SettlementResult> | null;

  /** Aborts the running settlement; a pending step is released with its default only after the abort has been sent. */
  abort(): Promise<void>;

  /**
   * Back to `idle`, dropping the last result or error; the next shopper. Throws while a
   * settlement is running, so a live operation is never detached from `abort()`; `abort()` it first.
   */
  reset(): void;
}

interface State {
  status: SettlementStatus;
  pendingStep: PendingSettlementStep | null;
  result: SettlementResult | null;
  error: Error | null;
  movements: readonly SettlementMovement[];
  operation: Operation<SettlementResult> | null;
}

const IDLE: State = {
  status: 'idle',
  pendingStep: null,
  result: null,
  error: null,
  movements: [],
  operation: null,
};

const NO_STEPS: readonly InteractiveStepKind[] = [];

type StepWithoutDefault = PendingSettlementStep extends infer S
  ? S extends PendingSettlementStep
    ? Omit<S, 'useDefault'>
    : never
  : never;

interface Waiting<A> {
  resolve(answer: A): void;
  accept(reply: SettlementStepReply): A;
  fallback(): A;
}

function toError(value: unknown): Error {
  return value instanceof Error ? value : new Error(String(value));
}

/**
 * Runs settlements as React state: `settle()` starts one, `status`, `movements`, `result` and
 * `error` follow it, and steps the register wants to answer interactively (a recomputed total
 * after rebates, a recovery decision after a decline) are held open as `pendingStep` until
 * `reply()`. Unanswered steps fall back to their SDK defaults at the host's deadline, exactly
 * as a missing handler would.
 *
 * ```tsx
 * const settlement = useSettlement(session, { interactive: ['TOTAL_REQUIRED'] });
 * if (settlement.pendingStep?.kind === 'TOTAL_REQUIRED') {
 *   const step = settlement.pendingStep;
 *   return <TotalPrompt suggested={step.suggestedTotal} onAccept={(total) => settlement.reply({ total })} />;
 * }
 * <button onClick={() => settlement.settle()}>Pay</button>
 * ```
 */
export function useSettlement(
  session: TerminalShopperSession | null | undefined,
  options: UseSettlementOptions = {},
): UseSettlementResult {
  const interactive = options.interactive ?? NO_STEPS;
  const interactiveRef = useRef(interactive);
  interactiveRef.current = interactive;
  const [state, setState] = useState<State>(IDLE);
  const waiting = useRef<Waiting<unknown> | null>(null);
  const live = useRef<{ operation?: Operation<SettlementResult> } | null>(null);
  const current = session ?? null;

  // A settlement belongs to the session it started on: when that session changes or the hook
  // unmounts, abort the operation and drop its state, finished results included, so the new
  // session starts clean and the last shopper's receipt never shows on the next one. The open
  // step is answered only once the abort has been sent: its default (the suggested total) would
  // otherwise let the host carry on to the charge.
  useEffect(() => {
    return () => {
      const pending = waiting.current;
      waiting.current = null;
      const run = live.current;
      live.current = null;
      setState(IDLE);
      const answer = () => pending?.resolve(pending.fallback());
      if (run?.operation) {
        run.operation.abort().then(answer, answer);
      } else {
        answer();
      }
    };
  }, [current]);

  const settleWaiting = useCallback((answer?: SettlementStepReply) => {
    const pending = waiting.current;
    if (!pending) return;
    // A reply that does not fit the step throws here, before the step is consumed.
    const value = answer === undefined ? pending.fallback() : pending.accept(answer);
    waiting.current = null;
    setState((previous) =>
      previous.pendingStep ? { ...previous, status: 'running', pendingStep: null } : previous,
    );
    pending.resolve(value);
  }, []);

  const ask = useCallback(
    <A>(
      step: StepWithoutDefault,
      info: StepInfo,
      fallback: () => A,
      accept: (reply: SettlementStepReply) => A,
    ): Promise<A> =>
      new Promise<A>((resolve) => {
        const pendingStep = { ...step, useDefault: () => settleWaiting() } as PendingSettlementStep;
        waiting.current = { resolve, accept, fallback } as Waiting<unknown>;
        setState((previous) => ({ ...previous, status: 'awaitingReply', pendingStep }));
        info.signal.addEventListener(
          'abort',
          () => {
            if (waiting.current?.resolve === resolve) settleWaiting();
          },
          { once: true },
        );
      }),
    [settleWaiting],
  );

  const settle = useCallback(
    (settleOptions: SettleOptions = {}): Operation<SettlementResult> => {
      if (!current) throw new Error('settle() called without an open terminal session');
      // `live` is a ref so two calls in one turn, before any render, cannot both pass.
      if (
        live.current ||
        waiting.current ||
        state.status === 'running' ||
        state.status === 'awaitingReply'
      ) {
        throw new Error('a settlement is already running on this hook');
      }
      const wants = (kind: InteractiveStepKind) => interactiveRef.current.includes(kind);
      const wrapped: SettleOptions = { ...settleOptions };

      const run: { operation?: Operation<SettlementResult> } = {};
      live.current = run;

      wrapped.onMovement = (movement) => {
        if (live.current === run)
          setState((previous) => ({ ...previous, movements: [...previous.movements, movement] }));
        settleOptions.onMovement?.(movement);
      };

      const totalStep =
        (step: PendingTotalStep['step']) =>
        (
          result: RebateRedemptionResult | PointRedemptionResult | GiftCardPaymentResult,
          info: StepInfo,
        ) =>
          ask<Money>(
            {
              kind: 'TOTAL_REQUIRED',
              step,
              suggestedTotal: result.suggestedTotal,
              ...(step === 'REBATE_REDEMPTION'
                ? { rebates: result as RebateRedemptionResult }
                : {}),
              ...(step === 'POINT_REDEMPTION' ? { points: result as PointRedemptionResult } : {}),
              ...(step === 'STORED_VALUE_CHARGE'
                ? { giftCard: result as GiftCardPaymentResult }
                : {}),
              deadline: info.deadline,
              signal: info.signal,
            },
            info,
            () => result.suggestedTotal,
            (reply) => {
              if (!('total' in reply)) throw new Error('a TOTAL_REQUIRED step takes { total }');
              return reply.total;
            },
          );
      if (!settleOptions.onRebatesRedeemed && wants('TOTAL_REQUIRED')) {
        wrapped.onRebatesRedeemed = totalStep('REBATE_REDEMPTION');
      }
      if (!settleOptions.onPointsRedeemed && wants('TOTAL_REQUIRED')) {
        wrapped.onPointsRedeemed = totalStep('POINT_REDEMPTION');
      }
      if (!settleOptions.onGiftCardPayment && wants('TOTAL_REQUIRED')) {
        wrapped.onGiftCardPayment = totalStep('STORED_VALUE_CHARGE');
      }
      if (!settleOptions.onError && wants('RECOVERY_REQUIRED')) {
        wrapped.onError = (failure, info) =>
          ask<SettlementRecovery | SettlementRecoveryAction>(
            {
              kind: 'RECOVERY_REQUIRED',
              failure,
              deadline: info.deadline,
              signal: info.signal,
            },
            info,
            () => 'ABORT',
            (reply) => {
              if (!('recovery' in reply)) {
                throw new Error('a RECOVERY_REQUIRED step takes { recovery }');
              }
              return reply.recovery;
            },
          );
      }
      if (!settleOptions.beforeStep && wants('BEFORE_STEP')) {
        wrapped.beforeStep = (context, info) =>
          ask<string | null | undefined>(
            { kind: 'BEFORE_STEP', context, deadline: info.deadline, signal: info.signal },
            info,
            () => undefined,
            (reply) => {
              if (!('saleTransactionId' in reply)) {
                throw new Error('a BEFORE_STEP step takes { saleTransactionId }');
              }
              return reply.saleTransactionId;
            },
          );
      }

      let operation: Operation<SettlementResult>;
      try {
        operation = current.settle(wrapped);
      } catch (error) {
        if (live.current === run) live.current = null;
        throw error;
      }
      run.operation = operation;
      setState({ ...IDLE, status: 'running', operation });
      operation.then(
        (result) => {
          if (live.current === run) {
            live.current = null;
            waiting.current = null;
          }
          setState((previous) =>
            previous.operation === operation
              ? { ...previous, status: 'succeeded', pendingStep: null, result }
              : previous,
          );
        },
        (cause: unknown) => {
          if (live.current === run) {
            live.current = null;
            waiting.current = null;
          }
          const error = toError(cause);
          const aborted = error instanceof SessionError && error.code === 'ABORTED';
          setState((previous) =>
            previous.operation === operation
              ? { ...previous, status: aborted ? 'aborted' : 'failed', pendingStep: null, error }
              : previous,
          );
        },
      );
      return operation;
    },
    [current, state.status, ask],
  );

  const reply = useCallback(
    (answer: SettlementStepReply) => {
      if (!waiting.current) throw new Error('reply() called with no step pending');
      settleWaiting(answer);
    },
    [settleWaiting],
  );

  const abort = useCallback(async () => {
    const operation = state.operation;
    if (!operation) return;
    // Abort first: the step's default would otherwise let the host go on to charge before the
    // abort lands. A refused abort still frees the held step.
    try {
      await operation.abort();
    } finally {
      settleWaiting();
    }
  }, [state.operation, settleWaiting]);

  const reset = useCallback(() => {
    if (waiting.current || state.status === 'running' || state.status === 'awaitingReply') {
      throw new Error('reset() called while a settlement is running; abort() it first');
    }
    setState(IDLE);
  }, [state.status]);

  return useMemo(
    () => ({ settle, reply, abort, reset, ...state }),
    [settle, reply, abort, reset, state],
  );
}
