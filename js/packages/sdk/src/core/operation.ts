import type {
  Operation as OperationResource,
  OperationStatus,
  OperationStep,
  OperationType,
  SettlementMovement,
  StepReply,
} from '@bilt/pos-protocol';
import { SessionError } from '../errors';
import type { Operation } from '../operation';
import { answerStep, fanOutMovement, type StepHandlers } from './steps';

/** What a pending operation needs from its session. */
export interface OperationLinks {
  reply(operationId: string, reply: StepReply): Promise<unknown>;
  abort(operationId: string): Promise<void>;
  /** A step handler or reply failed; surfaced to the register as a `background.error`. */
  reportStep(context: string, error: unknown): void;
}

/**
 * One operation from the moment the register called a method until `operation.completed`:
 * the promise the register awaits, the handle it may abort, and the step and movement handlers
 * it registered. The session feeds it the engine's acceptance and the operation's events; it
 * settles the promise exactly once.
 */
export class PendingOperation<T> {
  readonly handle: Operation<T>;
  private readonly promise: Promise<T>;
  private resolvePromise!: (value: T) => void;
  private rejectPromise!: (error: unknown) => void;
  private readonly acceptance: Promise<string | undefined>;
  private resolveAcceptance!: (id: string | undefined) => void;
  private engineId: string | undefined;
  private currentStatus: OperationStatus = 'queued';
  private settled = false;
  private readonly answeredSteps = new Set<string>();

  constructor(
    readonly type: OperationType,
    /** The client's id for the request, its idempotency key; `id` reports it until the engine assigned one. */
    readonly requestId: string,
    private readonly steps: StepHandlers | undefined,
    private readonly links: OperationLinks,
  ) {
    this.promise = new Promise<T>((resolve, reject) => {
      this.resolvePromise = resolve;
      this.rejectPromise = reject;
    });
    // An operation nobody awaits (a fire-and-forget display refresh) must not take the page down
    // as an unhandled rejection; `await op` still throws, since this handler is an extra one.
    this.promise.catch(() => undefined);
    this.acceptance = new Promise((resolve) => {
      this.resolveAcceptance = resolve;
    });
    this.handle = Object.defineProperties(this.promise, {
      id: { get: () => this.engineId ?? this.requestId, enumerable: true },
      type: { value: type, enumerable: true },
      status: { get: () => this.currentStatus, enumerable: true },
      abort: { value: () => this.abort(), enumerable: true },
    }) as Operation<T>;
  }

  get id(): string | undefined {
    return this.engineId;
  }

  get done(): boolean {
    return this.settled;
  }

  /** The engine accepted the request; the resource may already be complete. */
  accept(resource: OperationResource): void {
    this.engineId = resource.id;
    this.resolveAcceptance(resource.id);
    this.update(resource);
  }

  /** The request itself failed: the operation never reached the host. */
  refuse(error: unknown): void {
    this.resolveAcceptance(undefined);
    this.fail(error);
  }

  /** A fresh view of the resource, from an event or a poll. */
  update(resource: OperationResource): void {
    if (this.settled) return;
    this.currentStatus = resource.status;
    if (resource.status === 'awaitingReply' && resource.pendingStep) {
      this.step(resource.pendingStep);
    }
    if (resource.status === 'succeeded') {
      this.settled = true;
      this.resolvePromise(('result' in resource ? resource.result : undefined) as T);
    } else if (resource.status === 'failed' || resource.status === 'aborted') {
      const error = resource.error ?? {
        code: 'UNKNOWN' as const,
        message: `operation ${resource.type} ${resource.status}`,
      };
      const abandoned =
        resource.type === 'settle' && resource.abandonedSettlement
          ? resource.abandonedSettlement
          : undefined;
      if (abandoned && this.steps?.kind === 'settlement') {
        try {
          this.steps.handlers.onAbandoned?.(abandoned);
        } catch (failure) {
          this.links.reportStep('onAbandoned threw', failure);
        }
      }
      this.fail(
        new SessionError(error, abandoned ? { abandonedSettlement: abandoned } : undefined),
      );
    }
  }

  /** Fails the operation from the client side (the session ended, the request was refused). */
  fail(error: unknown): void {
    if (this.settled) return;
    this.settled = true;
    this.currentStatus =
      error instanceof SessionError && error.code === 'ABORTED' ? 'aborted' : 'failed';
    this.rejectPromise(error);
  }

  /** An `operation.step` for this operation, from the stream or a poll; each step is answered once. */
  step(step: OperationStep): void {
    if (this.settled || this.answeredSteps.has(step.stepId)) return;
    this.answeredSteps.add(step.stepId);
    this.currentStatus = 'awaitingReply';
    void this.answer(step);
  }

  movement(movement: SettlementMovement): void {
    fanOutMovement(this.steps, movement, (context, error) => this.links.reportStep(context, error));
  }

  private async answer(step: OperationStep): Promise<void> {
    const answer = await answerStep(step, this.steps);
    switch (answer.outcome) {
      case 'reply':
      case 'default': {
        const id = this.engineId ?? (await this.acceptance);
        if (id === undefined || this.settled) return;
        try {
          await this.links.reply(id, answer.reply);
        } catch (error) {
          this.links.reportStep(`replying to ${step.kind} step ${step.stepId} failed`, error);
        }
        return;
      }
      case 'failed':
        this.links.reportStep(
          `the ${step.kind} handler threw; the host default applies`,
          answer.error,
        );
        return;
      case 'late':
        return;
      default:
        return;
    }
  }

  private async abort(): Promise<void> {
    if (this.settled) return;
    const id = this.engineId ?? (await this.acceptance);
    if (id === undefined || this.settled) return;
    await this.links.abort(id);
  }
}
