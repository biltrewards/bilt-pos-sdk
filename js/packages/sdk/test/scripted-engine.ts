// A MockEngine whose operations the test drives by hand: a request is accepted as `queued` and
// stays there until the test raises a step, reports a movement or completes it. It also records
// every request, reply and abort with the options the core passed, and can drop a live event
// stream so the core's poll-and-resubscribe path is exercised without a network.
import type {
  Operation,
  OperationStep,
  SessionEvent,
  SettlementMovement,
  StepReply,
} from '@bilt/pos-protocol';
import type { EngineOperationRequest, RequestOptions } from '../src/internal';
import { MockEngine } from './mock-engine';

/** `Omit` applied to each member of a union, so the step kinds keep their own fields. */
type DistributiveOmit<T, K extends PropertyKey> = T extends unknown ? Omit<T, K> : never;

/** An `OperationStep` without the operation id, which `ScriptedEngine.step` fills in. */
export type StepInput = DistributiveOmit<OperationStep, 'operationId'>;

export interface RecordedRequest {
  readonly sessionId: string;
  readonly operation: EngineOperationRequest;
  readonly options: RequestOptions | undefined;
  readonly resource: Operation;
}

export interface RecordedReply {
  readonly sessionId: string;
  readonly operationId: string;
  readonly reply: StepReply;
  readonly options: RequestOptions | undefined;
}

export class ScriptedEngine extends MockEngine {
  readonly requests: RecordedRequest[] = [];
  readonly replies: RecordedReply[] = [];
  readonly aborts: Array<{ sessionId: string; operationId: string | undefined }> = [];
  readonly eventSubscriptions: Array<{ sessionId: string; since: number | undefined }> = [];

  /** The next `request` rejects with this instead of being accepted. */
  failNextRequestWith: unknown;

  /** Completes an accepted operation before `request` returns, as a setter-like operation does. */
  completeImmediatelyWith: Partial<Operation> | undefined;

  /** Delays the acceptance response so events can arrive before the core learns the id. */
  acceptanceDelay: Promise<void> | undefined;

  private dropped = new Map<string, number>();

  override async request(
    sessionId: string,
    operation: EngineOperationRequest,
    options?: RequestOptions,
  ): Promise<Operation> {
    if (this.failNextRequestWith !== undefined) {
      const error = this.failNextRequestWith;
      this.failNextRequestWith = undefined;
      throw error;
    }
    if (operation.type === 'end' || operation.type === 'forceEnd') {
      const resource = await super.request(sessionId, operation);
      this.requests.push({ sessionId, operation, options, resource });
      return resource;
    }
    const state = this.state(sessionId);
    const resource = {
      id: `op_${++this.counter}`,
      type: operation.type,
      status: 'queued',
      createdAt: new Date().toISOString(),
    } as Operation;
    state.operations.unshift(resource);
    this.requests.push({ sessionId, operation, options, resource });
    if (this.completeImmediatelyWith) {
      const patch = this.completeImmediatelyWith;
      this.completeImmediatelyWith = undefined;
      this.complete(sessionId, resource.id, patch);
    }
    if (this.acceptanceDelay) await this.acceptanceDelay;
    return { ...resource };
  }

  override async reply(
    sessionId: string,
    operationId: string,
    reply: StepReply,
    options?: RequestOptions,
  ): Promise<Operation> {
    this.replies.push({ sessionId, operationId, reply, options });
    const resource = await this.operation(sessionId, operationId);
    this.patch(sessionId, operationId, { status: 'running' });
    delete (resource as { pendingStep?: unknown }).pendingStep;
    return resource;
  }

  override async abort(sessionId: string, operationId?: string): Promise<void> {
    this.aborts.push({ sessionId, operationId });
  }

  override async *events(sessionId: string, since = 0): AsyncIterable<SessionEvent> {
    this.eventSubscriptions.push({ sessionId, since: since || undefined });
    const state = this.state(sessionId);
    const epoch = this.dropped.get(sessionId) ?? 0;
    let cursor = since;
    for (;;) {
      if ((this.dropped.get(sessionId) ?? 0) !== epoch) {
        throw new Error('event stream dropped');
      }
      const next = state.events.find((e) => e.seq > cursor);
      if (next) {
        cursor = next.seq;
        yield next;
        if (next.type === 'session.ended') return;
        continue;
      }
      await new Promise<void>((resolve) => state.waiters.push(resolve));
    }
  }

  /** Makes every live `events()` iterator of the session throw at its next step. */
  dropStream(sessionId: string): void {
    this.dropped.set(sessionId, (this.dropped.get(sessionId) ?? 0) + 1);
    const state = this.state(sessionId);
    for (const wake of state.waiters.splice(0)) wake();
  }

  /** Removes buffered events up to `seq` so a resubscription from an older cursor skips ahead. */
  forgetEventsThrough(sessionId: string, seq: number): void {
    const state = this.state(sessionId);
    const index = state.events.findIndex((e) => e.seq > seq);
    state.events.splice(0, index < 0 ? state.events.length : index);
  }

  lastOperation(sessionId: string): Operation {
    const found = this.state(sessionId).operations[0];
    if (!found) throw new Error('no operation yet');
    return found;
  }

  step(sessionId: string, operationId: string, step: StepInput): void {
    const full = { ...step, operationId } as OperationStep;
    this.patch(sessionId, operationId, { status: 'awaitingReply', pendingStep: full });
    this.push(this.state(sessionId), 'operation.step', full);
  }

  movement(sessionId: string, operationId: string, movement: SettlementMovement): void {
    this.push(this.state(sessionId), 'operation.movement', { operationId, movement });
  }

  complete(sessionId: string, operationId: string, patch: Partial<Operation>): Operation {
    const resource = this.patch(sessionId, operationId, {
      status: 'succeeded',
      completedAt: new Date().toISOString(),
      ...patch,
    });
    delete (resource as { pendingStep?: unknown }).pendingStep;
    this.push(this.state(sessionId), 'operation.completed', resource);
    return resource;
  }

  /** Changes the stored resource without emitting an event, for the poll fallback. */
  patch(sessionId: string, operationId: string, patch: Partial<Operation>): Operation {
    const state = this.state(sessionId);
    const index = state.operations.findIndex((o) => o.id === operationId);
    if (index < 0) throw new Error(`no operation ${operationId}`);
    const resource = { ...state.operations[index], ...patch } as Operation;
    state.operations[index] = resource;
    return resource;
  }

  pushEvent<T extends SessionEvent['type']>(
    sessionId: string,
    type: T,
    payload: Extract<SessionEvent, { type: T }>['payload'],
  ): void {
    this.push(this.state(sessionId), type, payload as never);
  }
}

/** A `TOTAL_REQUIRED` step for a rebate redemption, due in `inMs`. */
export function rebateStep(stepId: string, suggestedTotal: string, inMs = 30_000): StepInput {
  const previous = (Number(suggestedTotal) + 10).toFixed(2);
  return {
    stepId,
    kind: 'TOTAL_REQUIRED',
    deadlineAt: new Date(Date.now() + inMs).toISOString(),
    default: { stepId, total: suggestedTotal },
    step: 'REBATE_REDEMPTION',
    rebates: {
      rebates: [{ amount: '10.00', label: 'Gold' }],
      totalRebateAmount: '10.00',
      previousTotal: previous,
      suggestedTotal,
      updatedBasket: {
        cartId: 'cart_1',
        saleTransactionId: { transactionId: 'txn', timestamp: new Date().toISOString() },
        items: [],
        taxTotal: '0',
        originalTotal: previous,
        discountTotal: '0',
        subtotal: previous,
        grandTotal: previous,
        rebateTotal: '10.00',
        pointDiscountTotal: '0',
        storedValueTotal: '0',
        cardPaymentTotal: '0',
        externalPaymentTotal: '0',
        updatedAt: new Date().toISOString(),
      },
    },
  };
}
