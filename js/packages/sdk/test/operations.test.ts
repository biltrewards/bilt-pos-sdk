// Operations, steps, deadlines, idempotency keys and stream recovery, driven through the
// ScriptedEngine so every host behaviour is under the test's control.
import type { OperationStep, SessionError as SessionErrorData } from '@bilt/pos-protocol';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { BiltPos, SessionError, type TerminalShopperSession } from '../src/index';
import { rebateStep, ScriptedEngine } from './scripted-engine';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

async function lane(engine = new ScriptedEngine()) {
  const pos = await BiltPos.connect(() => engine);
  const session = await pos.startTerminalSession({
    saleId: 'LANE-3',
    poiId: 'VictaLane-275839164',
    currency: 'USD',
    storeLocation: 'STR-0142',
  });
  return { engine, pos, session };
}

function failed(code: SessionErrorData['code'], message: string = code): SessionErrorData {
  return { code, message };
}

afterEach(() => {
  vi.useRealTimers();
});

describe('starting an operation', () => {
  it('posts the request with a fresh UUID v4 idempotency key and declares handledSteps', async () => {
    const { engine, session } = await lane();
    const op = session.settle({
      disableAward: true,
      cashback: '5.00',
      onRebatesRedeemed: (r) => r.suggestedTotal,
      onError: () => 'RETRY',
      onMovement: () => undefined,
    });
    expect(op.type).toBe('settle');
    expect(op.status).toBe('queued');
    await vi.waitFor(() => expect(engine.requests).toHaveLength(1));
    const request = engine.requests[0]!;
    expect(request.operation).toEqual({
      type: 'settle',
      options: { disableAward: true, cashback: '5.00' },
      handledSteps: ['TOTAL_REQUIRED', 'RECOVERY_REQUIRED'],
    });
    expect(request.options?.idempotencyKey).toMatch(UUID_V4);

    const second = session.refund('1.00', { onError: () => 'SKIP' });
    await vi.waitFor(() => expect(engine.requests).toHaveLength(2));
    expect(engine.requests[1]!.operation).toEqual({
      type: 'refund',
      amount: '1.00',
      handledSteps: ['REVERSAL_DECISION_REQUIRED'],
    });
    expect(engine.requests[1]!.options?.idempotencyKey).not.toBe(request.options?.idempotencyKey);
    void second.catch(() => undefined);
  });

  it('declares no steps for a settlement without handlers', async () => {
    const { engine, session } = await lane();
    void session.settle();
    void session.voidTransaction();
    await vi.waitFor(() => expect(engine.requests).toHaveLength(2));
    expect(engine.requests[0]!.operation).toEqual({ type: 'settle' });
    expect(engine.requests[1]!.operation).toEqual({ type: 'voidTransaction' });
  });

  it('reports the engine id once accepted and resolves from operation.completed', async () => {
    const { engine, session } = await lane();
    const op = session.requestConfirmation('Receipt?');
    await vi.waitFor(() => expect(engine.requests).toHaveLength(1));
    const resource = engine.lastOperation(session.id);
    await vi.waitFor(() => expect(op.id).toBe(resource.id));
    engine.complete(session.id, resource.id, { result: true } as never);
    await expect(op).resolves.toBe(true);
    expect(op.status).toBe('succeeded');
  });

  it('resolves an operation the engine completed before the acceptance response', async () => {
    const engine = new ScriptedEngine();
    engine.completeImmediatelyWith = { result: '2.50' } as never;
    const { session } = await lane(engine);
    await expect(session.requestDecimalString('Tip?')).resolves.toBe('2.50');
  });

  it('replays events that arrived before the engine named the operation', async () => {
    const engine = new ScriptedEngine();
    let release!: () => void;
    engine.acceptanceDelay = new Promise<void>((resolve) => {
      release = resolve;
    });
    const { session } = await lane(engine);
    const op = session.settle({ onRebatesRedeemed: () => '80.00' });
    const heard: unknown[] = [];
    session.on('operation.step', (step) => heard.push(step));
    await vi.waitFor(() => expect(engine.requests).toHaveLength(1));
    const id = engine.lastOperation(session.id).id;
    engine.step(session.id, id, rebateStep('s1', '90.00'));
    await new Promise((r) => setTimeout(r, 10));
    expect(engine.replies).toEqual([]); // the core cannot answer before it knows the id
    release();
    await vi.waitFor(() => expect(engine.replies).toHaveLength(1));
    expect(engine.replies[0]!.reply).toEqual({ stepId: 's1', total: '80.00' });
    expect(heard).toHaveLength(1); // the replay must not emit the event a second time
    engine.complete(session.id, id, { result: { cardAmountCharged: '80.00' } } as never);
    await expect(op).resolves.toMatchObject({ cardAmountCharged: '80.00' });
  });

  it('still delivers held movements when the acceptance response already carries the result', async () => {
    const engine = new ScriptedEngine();
    let release!: () => void;
    engine.acceptanceDelay = new Promise<void>((resolve) => {
      release = resolve;
    });
    const { session } = await lane(engine);
    const onMovement = vi.fn();
    const op = session.settle({ onMovement });
    await vi.waitFor(() => expect(engine.requests).toHaveLength(1));
    const id = engine.lastOperation(session.id).id;
    engine.movement(session.id, id, {
      step: 'CARD_CHARGE',
      target: { type: 'SALES' },
      amount: '1',
    });
    engine.complete(session.id, id, { result: { cardAmountCharged: '1' } } as never);
    await new Promise((r) => setTimeout(r, 10)); // the pump delivers both events while acceptance is pending
    release();
    await expect(op).resolves.toMatchObject({ cardAmountCharged: '1' });
    expect(onMovement).toHaveBeenCalledTimes(1);
  });

  it('rejects an in-flight operation when BiltPos.close() detaches the session', async () => {
    const { engine, pos, session } = await lane();
    const op = session.settle();
    await vi.waitFor(() => expect(engine.requests).toHaveLength(1));
    await vi.waitFor(() => expect(op.id).toBeDefined());
    await pos.close();
    const error = await op.catch((e: unknown) => e);
    expect(error).toBeInstanceOf(SessionError);
    expect((error as SessionError).code).toBe('INVALID_STATE');
  });

  it('stops the event pump when the initial reads fail', async () => {
    const engine = new ScriptedEngine();
    engine.context = async () => {
      throw new Error('context read failed');
    };
    const pos = await BiltPos.connect(() => engine);
    await expect(
      pos.startTerminalSession({
        saleId: 'LANE-3',
        poiId: 'VictaLane-275839164',
        currency: 'USD',
        storeLocation: 'STR-0142',
      }),
    ).rejects.toThrow('context read failed');
    // No session reached the caller, so nothing but initialize() can stop the pump: a dropped
    // stream must not be resubscribed.
    const subscriptions = engine.eventSubscriptions.length;
    const sessionId = engine.requests[0]?.sessionId ?? engine.eventSubscriptions[0]!.sessionId;
    engine.dropStream(sessionId);
    await new Promise((r) => setTimeout(r, 600));
    expect(engine.eventSubscriptions).toHaveLength(subscriptions);
  });

  it('still calls the step handler when onMovement throws, and reports it', async () => {
    const { engine, session } = await lane();
    const errors: unknown[] = [];
    session.on('background.error', (e) => errors.push(e));
    const onCardCharged = vi.fn();
    const op = session.settle({
      onMovement: () => {
        throw new Error('observer broke');
      },
      onCardCharged,
    });
    await vi.waitFor(() => expect(engine.requests).toHaveLength(1));
    const id = engine.lastOperation(session.id).id;
    engine.movement(session.id, id, {
      step: 'CARD_CHARGE',
      target: { type: 'SALES' },
      amount: '1',
    });
    await vi.waitFor(() => expect(onCardCharged).toHaveBeenCalledTimes(1));
    expect(errors).toHaveLength(1);
    engine.complete(session.id, id, { result: { cardAmountCharged: '1' } } as never);
    await op;
  });

  it('rejects an operation whose request is still unanswered when BiltPos.close() detaches', async () => {
    const engine = new ScriptedEngine();
    let release!: () => void;
    engine.acceptanceDelay = new Promise<void>((resolve) => {
      release = resolve;
    });
    const { pos, session } = await lane(engine);
    const op = session.settle();
    await vi.waitFor(() => expect(engine.requests).toHaveLength(1));
    const aborted = op.abort();
    await pos.close();
    const error = await op.catch((e: unknown) => e);
    expect(error).toBeInstanceOf(SessionError);
    expect((error as SessionError).code).toBe('INVALID_STATE');
    release(); // the late acceptance must not bring it back
    await aborted; // and a concurrent abort must not wait for an acceptance that is moot
    expect(op.status).toBe('failed');
  });

  it('rejects with the SessionError of a failed operation and marks aborted ones', async () => {
    const { engine, session } = await lane();
    const op = session.settle();
    await vi.waitFor(() => expect(engine.requests).toHaveLength(1));
    const id = engine.lastOperation(session.id).id;
    engine.complete(session.id, id, { status: 'aborted', error: failed('ABORTED') });
    const error = await op.catch((e: unknown) => e);
    expect(error).toBeInstanceOf(SessionError);
    expect((error as SessionError).code).toBe('ABORTED');
    expect(op.status).toBe('aborted');
  });

  it('hands the abandoned settlement record to onAbandoned and the error', async () => {
    const { engine, session } = await lane();
    const onAbandoned = vi.fn();
    const op = session.settle({ onAbandoned });
    await vi.waitFor(() => expect(engine.requests).toHaveLength(1));
    const id = engine.lastOperation(session.id).id;
    const record = {
      settlementId: 'stl_1',
      abandonedAt: new Date().toISOString(),
      basket: session.basket.current,
      options: {},
      committedMovements: [],
      outstandingAmount: '10.00',
    };
    engine.complete(session.id, id, {
      status: 'failed',
      error: failed('ABANDONED'),
      abandonedSettlement: record,
    } as never);
    const error = (await op.catch((e: unknown) => e)) as SessionError;
    expect(error.code).toBe('ABANDONED');
    expect(error.abandonedSettlement).toEqual(record);
    expect(onAbandoned).toHaveBeenCalledWith(record);
  });

  it('refuses the operation when the request itself is rejected', async () => {
    const engine = new ScriptedEngine();
    engine.failNextRequestWith = new SessionError(failed('UNSUPPORTED', 'local session'));
    const { session } = await lane(engine);
    await expect(session.acquireCard()).rejects.toMatchObject({ code: 'UNSUPPORTED' });
  });

  it('refuses operations once the session has ended', async () => {
    const { session } = await lane();
    await session.end();
    await expect(session.requestSignature('Sign')).rejects.toMatchObject({
      code: 'INVALID_STATE',
    });
  });

  it('aborts through the engine once the operation id is known', async () => {
    const { engine, session } = await lane();
    const op = session.requestTextString('Email?');
    const aborting = op.abort();
    await vi.waitFor(() => expect(engine.aborts).toHaveLength(1));
    expect(engine.aborts[0]).toEqual({ sessionId: session.id, operationId: op.id });
    await aborting;
    await session.abort();
    expect(engine.aborts[1]).toEqual({ sessionId: session.id, operationId: undefined });
    engine.complete(session.id, op.id, { status: 'aborted', error: failed('ABORTED') });
    await expect(op).rejects.toBeInstanceOf(SessionError);
  });

  it('fans a movement out to onMovement and the per-movement handler', async () => {
    const { engine, session } = await lane();
    const onMovement = vi.fn();
    const onCardCharged = vi.fn();
    const onAwarded = vi.fn();
    const seen: unknown[] = [];
    session.on('operation.movement', (m) => seen.push(m.movement.step));
    const op = session.settle({ onMovement, onCardCharged, onAwarded });
    await vi.waitFor(() => expect(engine.requests).toHaveLength(1));
    const id = engine.lastOperation(session.id).id;
    engine.movement(session.id, id, {
      step: 'CARD_CHARGE',
      target: { type: 'SALES' },
      amount: '1',
    });
    engine.movement(session.id, id, { step: 'AWARD', target: { type: 'SALES' }, amount: '0' });
    engine.movement(session.id, id, {
      step: 'REBATE_REDEMPTION',
      target: { type: 'SALES' },
      amount: '1',
    });
    await vi.waitFor(() => expect(onMovement).toHaveBeenCalledTimes(3));
    expect(onCardCharged).toHaveBeenCalledTimes(1);
    expect(onAwarded).toHaveBeenCalledTimes(1);
    expect(seen).toEqual(['CARD_CHARGE', 'AWARD', 'REBATE_REDEMPTION']);
    engine.complete(session.id, id, { result: {} } as never);
    await op;
  });
});

describe('steps', () => {
  async function settleWith(
    engine: ScriptedEngine,
    session: TerminalShopperSession,
    handlers: Parameters<TerminalShopperSession['settle']>[0],
  ) {
    const op = session.settle(handlers);
    await vi.waitFor(() => expect(engine.requests).toHaveLength(1));
    return { op, id: engine.lastOperation(session.id).id };
  }

  it('answers TOTAL_REQUIRED with the handler total and a fresh idempotency key', async () => {
    const { engine, session } = await lane();
    const handler = vi.fn((result: { suggestedTotal: string }, info: { deadline: Date }) => {
      expect(info.deadline).toBeInstanceOf(Date);
      return (Number(result.suggestedTotal) - 0.5).toFixed(2);
    });
    const steps: OperationStep[] = [];
    session.on('operation.step', (s) => steps.push(s));
    const { op, id } = await settleWith(engine, session, { onRebatesRedeemed: handler });
    engine.step(session.id, id, rebateStep('s1', '90.00'));
    await vi.waitFor(() => expect(engine.replies).toHaveLength(1));
    expect(op.status).toBe('awaitingReply');
    expect(engine.replies[0]).toMatchObject({
      operationId: id,
      reply: { stepId: 's1', total: '89.50' },
    });
    expect(engine.replies[0]!.options?.idempotencyKey).toMatch(UUID_V4);
    expect(engine.replies[0]!.options?.idempotencyKey).not.toBe(
      engine.requests[0]!.options?.idempotencyKey,
    );
    expect(steps.map((s) => s.stepId)).toEqual(['s1']);
    engine.complete(session.id, id, { result: {} } as never);
    await op;
  });

  it('answers a replayed step only once', async () => {
    const { engine, session } = await lane();
    const handler = vi.fn(() => '1.00');
    const { id } = await settleWith(engine, session, { onRebatesRedeemed: handler });
    engine.step(session.id, id, rebateStep('s1', '90.00'));
    engine.step(session.id, id, rebateStep('s1', '90.00'));
    await vi.waitFor(() => expect(engine.replies).toHaveLength(1));
    await new Promise((r) => setTimeout(r, 20));
    expect(handler).toHaveBeenCalledTimes(1);
    expect(engine.replies).toHaveLength(1);
  });

  it('sends the default at once when a sibling total handler is missing', async () => {
    const { engine, session } = await lane();
    const { id } = await settleWith(engine, session, { onPointsRedeemed: () => '1.00' });
    engine.step(session.id, id, rebateStep('s1', '90.00'));
    await vi.waitFor(() => expect(engine.replies).toHaveLength(1));
    expect(engine.replies[0]!.reply).toEqual({ stepId: 's1', total: '90.00' });
  });

  it('sends nothing and reports a background.error when the handler throws', async () => {
    const { engine, session } = await lane();
    const errors: SessionErrorData[] = [];
    session.on('background.error', (e) => errors.push(e));
    const { id } = await settleWith(engine, session, {
      onRebatesRedeemed: () => {
        throw new Error('tax service down');
      },
    });
    engine.step(session.id, id, rebateStep('s1', '90.00'));
    await vi.waitFor(() => expect(errors).toHaveLength(1));
    expect(errors[0]!.code).toBe('UNKNOWN');
    expect(errors[0]!.message).toContain('tax service down');
    expect(engine.replies).toEqual([]);
  });

  it('ignores an answer that arrives after the deadline and aborts the handler signal', async () => {
    vi.useFakeTimers();
    const { engine, session } = await lane();
    let aborted = false;
    const { id } = await settleWith(engine, session, {
      onRebatesRedeemed: (_r, info) =>
        new Promise<string>((resolve) => {
          info.signal.addEventListener('abort', () => {
            aborted = true;
          });
          setTimeout(() => resolve('1.00'), 2_000);
        }),
    });
    engine.step(session.id, id, rebateStep('s1', '90.00', 1_000));
    await vi.advanceTimersByTimeAsync(1_500);
    expect(aborted).toBe(true);
    await vi.advanceTimersByTimeAsync(1_000);
    expect(engine.replies).toEqual([]);
  });

  it('does not consult the handler for a step whose deadline already passed', async () => {
    const { engine, session } = await lane();
    const handler = vi.fn(() => '1.00');
    const { id } = await settleWith(engine, session, { onRebatesRedeemed: handler });
    engine.step(session.id, id, rebateStep('s1', '90.00', -10));
    await new Promise((r) => setTimeout(r, 20));
    expect(handler).not.toHaveBeenCalled();
    expect(engine.replies).toEqual([]);
  });

  it('normalises a bare recovery action and answers BEFORE_STEP defaults', async () => {
    const { engine, session } = await lane();
    const { id } = await settleWith(engine, session, {
      beforeStep: () => null,
      onError: (failure) => (failure.outcomeCertainty === 'DEFINITIVE' ? 'RETRY' : 'ABORT'),
    });
    expect(engine.requests[0]!.operation).toMatchObject({
      handledSteps: ['BEFORE_STEP', 'RECOVERY_REQUIRED'],
    });
    engine.step(session.id, id, {
      stepId: 'b1',
      kind: 'BEFORE_STEP',
      deadlineAt: new Date(Date.now() + 30_000).toISOString(),
      default: { stepId: 'b1', saleTransactionId: 'txn_default' },
      context: {
        step: 'CARD_CHARGE',
        currentBasket: session.basket.current,
        currentTotal: '1.00',
        defaultTransactionId: 'txn_default',
        priorSteps: [],
      },
    });
    await vi.waitFor(() => expect(engine.replies).toHaveLength(1));
    expect(engine.replies[0]!.reply).toEqual({ stepId: 'b1', saleTransactionId: 'txn_default' });

    engine.step(session.id, id, {
      stepId: 'r1',
      kind: 'RECOVERY_REQUIRED',
      deadlineAt: new Date(Date.now() + 30_000).toISOString(),
      default: { stepId: 'r1', recovery: { action: 'ABORT' } },
      failure: {
        error: failed('DECLINED'),
        amountDue: '1.00',
        committedMovements: [],
        outcomeCertainty: 'DEFINITIVE',
      },
    });
    await vi.waitFor(() => expect(engine.replies).toHaveLength(2));
    expect(engine.replies[1]!.reply).toEqual({ stepId: 'r1', recovery: { action: 'RETRY' } });
  });

  it('gives a reversal handler the step and a SessionError and treats no answer as ABORT', async () => {
    const { engine, session } = await lane();
    const onError = vi.fn(() => undefined);
    const op = session.voidTransaction(undefined, { onError });
    await vi.waitFor(() => expect(engine.requests).toHaveLength(1));
    const id = engine.lastOperation(session.id).id;
    engine.step(session.id, id, {
      stepId: 'v1',
      kind: 'REVERSAL_DECISION_REQUIRED',
      deadlineAt: new Date(Date.now() + 30_000).toISOString(),
      default: { stepId: 'v1', decision: 'SKIP' },
      step: 'AWARD',
      error: failed('LOYALTY_UNAVAILABLE'),
    });
    await vi.waitFor(() => expect(engine.replies).toHaveLength(1));
    expect(engine.replies[0]!.reply).toEqual({ stepId: 'v1', decision: 'ABORT' });
    const [step, error] = onError.mock.calls[0] as unknown as [string, SessionError];
    expect(step).toBe('AWARD');
    expect(error).toBeInstanceOf(SessionError);
    expect(error.code).toBe('LOYALTY_UNAVAILABLE');
    engine.complete(session.id, id, { status: 'failed', error: failed('ABORTED') });
    await expect(op).rejects.toBeInstanceOf(SessionError);
  });
});

describe('ending', () => {
  it('is "ending" while the end operation is queued and rejects when the host refuses', async () => {
    const engine = new ScriptedEngine();
    const { session } = await lane(engine);
    engine.failNextRequestWith = new SessionError(failed('INVALID_STATE', 'money in flight'));
    await expect(session.end()).rejects.toMatchObject({ code: 'INVALID_STATE' });
    expect(session.state).toBe('open');
    const ended: unknown[] = [];
    session.on('session.ended', (p) => ended.push(p));
    await session.forceEnd('register closing');
    expect(session.state).toBe('ended');
    expect(ended).toEqual([{ sessionId: session.id, forced: true, reason: 'register closing' }]);
  });

  it('fails operations still pending when the session ends underneath them', async () => {
    const { engine, session } = await lane();
    const op = session.requestConfirmation('Bag?');
    await vi.waitFor(() => expect(engine.requests).toHaveLength(1));
    engine.pushEvent(session.id, 'session.ended', { sessionId: session.id, forced: false });
    await expect(op).rejects.toMatchObject({ code: 'INVALID_STATE' });
    await vi.waitFor(() => expect(session.state).toBe('ended'));
  });
});

describe('losing the event stream', () => {
  it('polls pending operations, answers a step found there and resubscribes with since', async () => {
    const { engine, session } = await lane();
    const poll = vi.spyOn(engine, 'operation');
    const op = session.settle({ onRebatesRedeemed: () => '42.00' });
    await vi.waitFor(() => expect(engine.requests).toHaveLength(1));
    const id = engine.lastOperation(session.id).id;

    // The step and the completion happen while the stream is down: no events reach the core.
    engine.dropStream(session.id);
    const step = { ...rebateStep('s1', '90.00'), operationId: id } as OperationStep;
    engine.patch(session.id, id, { status: 'awaitingReply', pendingStep: step });
    await vi.waitFor(() => expect(engine.replies).toHaveLength(1), { timeout: 5_000 });
    expect(engine.replies[0]!.reply).toEqual({ stepId: 's1', total: '42.00' });
    expect(poll).toHaveBeenCalledWith(session.id, id);

    // The stream comes back: the completion arrives as an event on the resubscribed stream.
    await vi.waitFor(() => expect(engine.eventSubscriptions.length).toBeGreaterThan(1), {
      timeout: 5_000,
    });
    expect(engine.eventSubscriptions.at(-1)!.since).toBe(1);
    engine.complete(session.id, id, { result: { cardAmountCharged: '42.00' } } as never);
    await expect(op).resolves.toMatchObject({ cardAmountCharged: '42.00' });
  });

  it('does not deliver an event twice and re-reads state after a gap', async () => {
    const { engine, session } = await lane();
    const basketGets = vi.fn();
    const originalBasket = engine.basket.bind(engine);
    engine.basket = (sessionId, command) => {
      if (command.kind === 'get') basketGets();
      return originalBasket(sessionId, command);
    };
    const changes: number[] = [];
    session.on('context.changed', () => changes.push(1));
    await session.context.setPhase('TENDERING');
    await vi.waitFor(() => expect(changes).toHaveLength(1));
    const getsBeforeGap = basketGets.mock.calls.length;

    engine.dropStream(session.id);
    await session.context.setPhase('COMPLETE'); // seq 3, emitted while the stream is down
    engine.forgetEventsThrough(session.id, 3); // the host no longer replays it
    await session.context.setAttribute('k', 'v'); // seq 4
    await vi.waitFor(() => expect(changes).toHaveLength(2), { timeout: 5_000 });
    await vi.waitFor(() => expect(basketGets.mock.calls.length).toBeGreaterThan(getsBeforeGap));
    expect(session.context.attributes()).toEqual({ k: 'v' });
  });
});
