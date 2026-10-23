import type {
  Operation as OperationResource,
  OperationStatus,
  OperationStep,
  OperationType,
  SettlementMovement,
  StepReply,
} from '@biltorg/pos-protocol';
import { SessionError } from '../errors';
import type { Operation } from '../operation';
import type { ReversalHandlers, SettlementHandlers } from '../terminal-session';
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
  /** A second handle on this operation follows it: when its request raced a listing's adopt. */
  private readonly mirrors = new Set<PendingOperation<unknown>>();
  /** Set once this tracker follows another: mirrored updates still apply, only the owner replies. */
  private owner: PendingOperation<unknown> | undefined;
  /** A step that arrived before `guide()` registered the handlers that answer it. */
  private deferredStep: OperationStep | undefined;
  private deferredSeq: number | undefined;
  /** The newest stream `seq` a step arrived with; replayed steps older than it are resolved. */
  private latestStepSeq: number | undefined;
  /** Every stepId this tracker ever saw, so a stale snapshot can't pose as new information. */
  private readonly seenStepIds = new Set<string>();

  constructor(
    readonly type: OperationType,
    /** The client's id for the request, its idempotency key; `id` reports it until the engine assigned one. */
    readonly requestId: string,
    private steps: StepHandlers | undefined,
    private readonly links: OperationLinks,
    /** An adopted operation holds its steps open until `guide()` wires handlers for them. */
    private readonly deferStepAnswers = false,
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
      guide: {
        value: (handlers: SettlementHandlers | ReversalHandlers) => this.guide(handlers),
        enumerable: true,
      },
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
    for (const mirror of this.mirrors) mirror.update(resource);
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
    for (const mirror of this.mirrors) mirror.fail(error);
    this.settled = true;
    // An abort waiting for the engine id must not wait for an acceptance that will never matter.
    this.resolveAcceptance(undefined);
    this.currentStatus =
      error instanceof SessionError && error.code === 'ABORTED' ? 'aborted' : 'failed';
    this.rejectPromise(error);
  }

  /**
   * Makes this tracker follow `owner`: a `operations()` listing adopted the operation while
   * `owner`'s request was still unanswered, so it is the second handle on one operation. It
   * keeps updating with the owner's events but never sends its own step replies — the owner's
   * handlers are the ones the register registered.
   */
  follow(owner: PendingOperation<unknown>): void {
    this.owner = owner;
    // The mirrors only receive non-generic callbacks, so `this` is safe to widen.
    owner.mirrors.add(this as PendingOperation<unknown>);
    // Handlers and a step deferred on this second handle belong to the owner, the replying one.
    if (this.steps !== undefined && owner.steps === undefined) owner.steps = this.steps;
    owner.noteSeq(this.latestStepSeq);
    for (const stepId of this.seenStepIds) owner.seenStepIds.add(stepId);
    const step = this.deferredStep;
    const seq = this.deferredSeq;
    this.deferredStep = undefined;
    this.deferredSeq = undefined;
    if (step !== undefined) owner.step(step, seq);
  }

  /**
   * Registers the handlers the operation answers its steps and reports its movements with —
   * the `SettlementFlow`/`ReversalFlow` handler calls in Java, taken here on the handle: an
   * adopted operation's pending step is answered with them right away. Calling `guide` on a
   * handle that follows another forwards to the owner, whose handlers are the replying ones.
   */
  guide(handlers: SettlementHandlers | ReversalHandlers): void {
    if (this.owner !== undefined) {
      this.owner.guide(handlers);
      return;
    }
    this.steps = {
      kind: this.type === 'settle' ? 'settlement' : 'reversal',
      handlers,
    } as StepHandlers;
    const step = this.deferredStep;
    this.deferredStep = undefined;
    this.deferredSeq = undefined;
    if (step !== undefined && !this.settled && !this.answeredSteps.has(step.stepId)) {
      this.answeredSteps.add(step.stepId);
      void this.answer(step);
    }
  }

  private noteSeq(seq: number | undefined): void {
    if (seq !== undefined && (this.latestStepSeq === undefined || seq > this.latestStepSeq)) {
      this.latestStepSeq = seq;
    }
  }

  /**
   * An `operation.step` for this operation, from the stream or a poll; each step is answered
   * once. `seq` is the event's stream position when it has one: a step older than the newest
   * one seen is a replay the host already resolved, not something to answer.
   */
  step(step: OperationStep, seq?: number): void {
    const unseen = !this.seenStepIds.has(step.stepId);
    this.seenStepIds.add(step.stepId);
    if (seq !== undefined && this.latestStepSeq !== undefined && seq < this.latestStepSeq) {
      return;
    }
    this.noteSeq(seq);
    for (const mirror of this.mirrors) mirror.step(step, seq);
    if (this.settled || this.answeredSteps.has(step.stepId)) return;
    this.currentStatus = 'awaitingReply';
    // A followed tracker records the step but leaves the reply to the owner's handlers.
    if (this.owner !== undefined) return;
    // An adopted operation keeps a step the previous page's handlers would have answered open
    // until `guide()` registers this page's — otherwise it would resolve with defaults first.
    if (this.deferStepAnswers && this.steps === undefined) {
      // A snapshot injection has no seq: it may displace the held step only when it names one
      // the stream never delivered — a poll's read of what the host awaits beats the last
      // event we saw. A seen id is the same step or a stale one; it must not drop the answer
      // `guide()` still owes.
      if (seq === undefined && this.deferredStep !== undefined && !unseen) return;
      this.deferredStep = step;
      this.deferredSeq = seq;
      return;
    }
    this.answeredSteps.add(step.stepId);
    void this.answer(step);
  }

  movement(movement: SettlementMovement): void {
    for (const mirror of this.mirrors) mirror.movement(movement);
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
          // `INVALID_STATE` here is the host's `notPending`: the step was already resolved
          // by another page or by its deadline — exactly what an attach's replayed step
          // events produce — so the reply is redundant rather than a fault.
          if (!(error instanceof SessionError && error.code === 'INVALID_STATE')) {
            this.links.reportStep(`replying to ${step.kind} step ${step.stepId} failed`, error);
          }
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
