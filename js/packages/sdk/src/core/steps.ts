import type {
  OperationStep,
  SettlementMovement,
  SettlementRecovery,
  StepKind,
  StepReply,
} from '@bilt/pos-protocol';
import { SessionError } from '../errors';
import type { ReversalHandlers, SettlementHandlers, StepInfo } from '../terminal-session';

/** The handlers an operation was started with, tagged so `onError`'s two signatures stay apart. */
export type StepHandlers =
  | { readonly kind: 'settlement'; readonly handlers: SettlementHandlers }
  | { readonly kind: 'reversal'; readonly handlers: ReversalHandlers };

/**
 * The step kinds to declare in a request's `handledSteps`: exactly those the caller passed
 * handlers for, so the host resolves the rest with their defaults at once and emits no
 * `operation.step` for them, as the Java SDK does when no handler is registered.
 */
export function handledSteps(steps: StepHandlers | undefined): StepKind[] {
  if (!steps) return [];
  if (steps.kind === 'reversal') {
    return steps.handlers.onError ? ['REVERSAL_DECISION_REQUIRED'] : [];
  }
  const h = steps.handlers;
  const kinds: StepKind[] = [];
  if (h.beforeStep) kinds.push('BEFORE_STEP');
  if (h.onRebatesRedeemed || h.onPointsRedeemed || h.onGiftCardPayment) {
    kinds.push('TOTAL_REQUIRED');
  }
  if (h.onError) kinds.push('RECOVERY_REQUIRED');
  return kinds;
}

/** `"RETRY"` becomes `{ action: "RETRY" }`; an object passes through. */
export function normalizeRecovery(
  answer: SettlementRecovery | SettlementRecovery['action'],
): SettlementRecovery {
  return typeof answer === 'string' ? { action: answer } : answer;
}

/**
 * Hands an `operation.movement` to `onMovement` and to the handler named after its step. Each
 * runs on its own, so a handler that throws is reported through `onError` without keeping the
 * other from hearing about a movement that has already been committed.
 */
export function fanOutMovement(
  steps: StepHandlers | undefined,
  movement: SettlementMovement,
  onError: (context: string, error: unknown) => void,
): void {
  if (!steps || steps.kind !== 'settlement') return;
  const h = steps.handlers;
  const specific = {
    CARD_CHARGE: h.onCardCharged,
    EXTERNAL_PAYMENT: h.onExternallyPaid,
    AWARD: h.onAwarded,
    STORED_VALUE_LOAD: h.onStoredValueLoaded,
    CARD_REFUND: h.onCardRefunded,
    STORED_VALUE_REFUND: h.onGiftCardRefunded,
    EXTERNAL_REFUND: h.onExternalRefunded,
    POINT_REDEMPTION_REFUND: h.onPointsRefunded,
    REBATE_REFUND: h.onRebateRefunded,
    AWARD_REFUND: h.onAwardRefunded,
  }[movement.step as string];
  for (const [context, handler] of [
    ['onMovement threw', h.onMovement],
    [`the ${movement.step} movement handler threw`, specific],
  ] as const) {
    try {
      handler?.(movement);
    } catch (error) {
      onError(context, error);
    }
  }
}

/** The outcome of consulting the register for one step. */
export type StepAnswer =
  /** The register answered in time; send this reply. */
  | { readonly outcome: 'reply'; readonly reply: StepReply }
  /** No handler covers this step; the step's default is sent at once rather than waiting for the deadline. */
  | { readonly outcome: 'default'; readonly reply: StepReply }
  /** The handler threw: nothing is sent and the host's default applies at the deadline. */
  | { readonly outcome: 'failed'; readonly error: unknown }
  /** The handler answered after the deadline; its answer is ignored. */
  | { readonly outcome: 'late' };

/**
 * Consults the register's handler for a step, within the host's deadline. The handler gets a
 * `StepInfo` whose `signal` aborts when the deadline passes; an answer that arrives after it is
 * dropped, since the host has already applied the default. `now` is injectable for tests.
 */
export async function answerStep(
  step: OperationStep,
  steps: StepHandlers | undefined,
  now: () => number = Date.now,
): Promise<StepAnswer> {
  const deadline = new Date(step.deadlineAt);
  const remaining = deadline.getTime() - now();
  if (!(remaining > 0)) return { outcome: 'late' };

  const invoke = pickHandler(step, steps);
  if (!invoke) return { outcome: 'default', reply: step.default };

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(new Error('step deadline passed')), remaining);
  const info: StepInfo = { deadline, signal: controller.signal };
  try {
    const reply = await invoke(info);
    if (controller.signal.aborted) return { outcome: 'late' };
    return { outcome: 'reply', reply };
  } catch (error) {
    return controller.signal.aborted ? { outcome: 'late' } : { outcome: 'failed', error };
  } finally {
    clearTimeout(timer);
  }
}

type Invoke = (info: StepInfo) => Promise<StepReply>;

function pickHandler(step: OperationStep, steps: StepHandlers | undefined): Invoke | undefined {
  if (!steps) return undefined;
  const stepId = step.stepId;
  if (steps.kind === 'reversal') {
    if (step.kind !== 'REVERSAL_DECISION_REQUIRED') return undefined;
    const onError = steps.handlers.onError;
    if (!onError) return undefined;
    const error = new SessionError(step.error);
    return async (info) => {
      const decision = await onError(step.step ?? null, error, info);
      return { stepId, decision: decision ?? 'ABORT' };
    };
  }
  const h = steps.handlers;
  switch (step.kind) {
    case 'BEFORE_STEP': {
      const beforeStep = h.beforeStep;
      if (!beforeStep) return undefined;
      return async (info) => {
        const id = await beforeStep(step.context, info);
        return id ? { stepId, saleTransactionId: id } : step.default;
      };
    }
    case 'TOTAL_REQUIRED': {
      if (step.step === 'REBATE_REDEMPTION' && h.onRebatesRedeemed && step.rebates) {
        const handler = h.onRebatesRedeemed;
        const rebates = step.rebates;
        return async (info) => ({ stepId, total: await handler(rebates, info) });
      }
      if (step.step === 'POINT_REDEMPTION' && h.onPointsRedeemed && step.points) {
        const handler = h.onPointsRedeemed;
        const points = step.points;
        return async (info) => ({ stepId, total: await handler(points, info) });
      }
      if (step.step === 'STORED_VALUE_CHARGE' && h.onGiftCardPayment && step.giftCard) {
        const handler = h.onGiftCardPayment;
        const giftCard = step.giftCard;
        return async (info) => ({ stepId, total: await handler(giftCard, info) });
      }
      return undefined;
    }
    case 'RECOVERY_REQUIRED': {
      const onError = h.onError;
      if (!onError) return undefined;
      return async (info) => ({
        stepId,
        recovery: normalizeRecovery(await onError(step.failure, info)),
      });
    }
    default:
      return undefined;
  }
}
