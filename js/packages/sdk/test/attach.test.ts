// Reattaching to a session after the page went away: the engine (the bridge, the mock) keeps
// the session, its event buffer and its operations; a fresh `BiltPos` attaches by id and
// rebuilds the mirrors and the in-flight operations from the replay.
import { describe, expect, it, vi } from 'vitest';
import { BiltPos, SessionError } from '../src/index';
import { rebateStep, ScriptedEngine } from './scripted-engine';

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

describe('attaching to a session', () => {
  it('reattaches to a running session and rebuilds the mirrors from the event replay', async () => {
    const { engine, pos, session } = await lane();
    await session.basket.addItem({
      sku: 'SKU-1',
      description: 'Toothpaste',
      quantity: 1,
      unitPrice: '4.99',
    });
    await session.member.set({ id: 'mbr_1' });
    await session.context.setPhase('TENDERING');
    const op = session.settle();
    await vi.waitFor(() => expect(engine.requests).toHaveLength(1));
    const opId = engine.lastOperation(session.id).id;

    // The register's page went away: the sessions detach, the engine keeps everything.
    await pos.close();

    const pos2 = await BiltPos.connect(() => engine);
    const attached = await pos2.attachTerminalSession(session.id);

    expect(attached.id).toBe(session.id);
    expect(attached.state).toBe('open');
    // The mirrors converge as the buffered events replay.
    await vi.waitFor(() => {
      expect(attached.basket.current.items).toHaveLength(1);
      expect(attached.member.current?.memberId).toBe('mbr_1');
      expect(attached.context.phase()).toBe('TENDERING');
    });
    // The attach replays the session's event buffer from the start, then continues live.
    expect(
      engine.eventSubscriptions.some((s) => s.sessionId === session.id && s.since === undefined),
    ).toBe(true);

    engine.complete(session.id, opId, { result: { cardAmountCharged: '4.99' } } as never);
    const [settled] = await attached.operations();
    await expect(settled).resolves.toMatchObject({ cardAmountCharged: '4.99' });
    void op.catch(() => undefined);
  });

  it('attaches to an ended session and resolves with ended already settled', async () => {
    const { engine, pos, session } = await lane();
    void session.settle();
    await vi.waitFor(() => expect(engine.requests).toHaveLength(1));
    const opId = engine.lastOperation(session.id).id;
    engine.complete(session.id, opId, { result: { cardAmountCharged: '4.99' } } as never);
    await session.end();
    await pos.close();

    const pos2 = await BiltPos.connect(() => engine);
    const attached = await pos2.attachTerminalSession(session.id);
    expect(attached.state).toBe('ended');
    await attached.ended;
    await attached.end(); // ending an attached, ended session is a no-op

    // The final state is still readable: the settle's result comes back on its handle.
    const ops = await attached.operations();
    const settled = ops.find((o) => o.type === 'settle')!;
    await expect(settled).resolves.toMatchObject({ cardAmountCharged: '4.99' });
  });

  it('rejects an unknown session id', async () => {
    const pos = await BiltPos.connect(() => new ScriptedEngine());
    await expect(pos.attachTerminalSession('ses_nope')).rejects.toMatchObject({
      code: 'NOT_FOUND',
    });
    await expect(pos.attachShopperSession('ses_nope')).rejects.toMatchObject({
      code: 'NOT_FOUND',
    });
  });

  it('rejects a session of the other kind', async () => {
    const { pos, session } = await lane();
    const local = await pos.startShopperSession({ saleId: 'LANE-4', currency: 'USD' });
    await expect(pos.attachTerminalSession(local.id)).rejects.toMatchObject({
      code: 'INVALID_STATE',
    });
    await expect(pos.attachShopperSession(session.id)).rejects.toMatchObject({
      code: 'INVALID_STATE',
    });
    // The matching kind attaches and returns the same session implementation.
    const attached = await pos.attachShopperSession(local.id);
    expect(attached.id).toBe(local.id);
    expect(attached.kind).toBe('local');
  });
});

describe('operations on an attached session', () => {
  it('lists the operations newest first, unfiltered and filtered by status', async () => {
    const { engine, pos, session } = await lane();
    void session.requestConfirmation('Receipt?');
    void session.requestConfirmation('Bag?');
    await vi.waitFor(() => expect(engine.requests).toHaveLength(2));
    const [second, first] = await engine.operations(session.id);
    engine.patch(session.id, first!.id, { status: 'succeeded' });
    engine.patch(session.id, second!.id, { status: 'running' });
    await pos.close();

    const pos2 = await BiltPos.connect(() => engine);
    const attached = await pos2.attachTerminalSession(session.id);

    const all = await attached.operations();
    expect(all.map((o) => o.id)).toEqual([second!.id, first!.id]);

    const inFlight = await attached.operations({ status: ['running', 'awaitingReply'] });
    expect(inFlight.map((o) => o.id)).toEqual([second!.id]);
    // The listing hands out the same handle again rather than a second tracker.
    expect(attached.operation(second!.id)).toBe(inFlight[0]);
    expect(attached.operation('op_nope')).toBeUndefined();
  });

  it('resolves a handle for a finished operation with its stored result', async () => {
    const { engine, pos, session } = await lane();
    void session.settle();
    await vi.waitFor(() => expect(engine.requests).toHaveLength(1));
    const opId = engine.lastOperation(session.id).id;
    engine.complete(session.id, opId, { result: { cardAmountCharged: '4.99' } } as never);
    await pos.close();

    const pos2 = await BiltPos.connect(() => engine);
    const attached = await pos2.attachTerminalSession(session.id);
    await attached.operations(); // pulls the finished operation in
    const handle = attached.operation(opId)!;
    expect(handle.status).toBe('succeeded');
    await expect(handle).resolves.toMatchObject({ cardAmountCharged: '4.99' });
  });

  it('rejects a handle for a finished operation with its stored error', async () => {
    const { engine, pos, session } = await lane();
    void session.settle();
    await vi.waitFor(() => expect(engine.requests).toHaveLength(1));
    const opId = engine.lastOperation(session.id).id;
    engine.complete(session.id, opId, {
      status: 'failed',
      error: { code: 'DECLINED', message: 'card declined' },
    });
    await pos.close();

    const pos2 = await BiltPos.connect(() => engine);
    const attached = await pos2.attachTerminalSession(session.id);
    const handle = (await attached.operations()).find((o) => o.id === opId)!;
    const error = await handle.catch((e: unknown) => e);
    expect(error).toBeInstanceOf(SessionError);
    expect((error as SessionError).code).toBe('DECLINED');
  });

  it('settles a running handle when the operation finishes on the host', async () => {
    const { engine, pos, session } = await lane();
    void session.requestDecimalString('Tip?');
    await vi.waitFor(() => expect(engine.requests).toHaveLength(1));
    const opId = engine.lastOperation(session.id).id;
    engine.patch(session.id, opId, { status: 'running' });
    await pos.close();

    const pos2 = await BiltPos.connect(() => engine);
    const attached = await pos2.attachTerminalSession(session.id);
    const [handle] = await attached.operations();
    expect(handle!.status).toBe('running');

    engine.complete(session.id, opId, { result: '2.50' } as never);
    await expect(handle).resolves.toBe('2.50');
    expect(handle!.status).toBe('succeeded');
  });

  it('aborts an in-flight operation through an attached handle', async () => {
    const { engine, pos, session } = await lane();
    void session.settle();
    await vi.waitFor(() => expect(engine.requests).toHaveLength(1));
    const opId = engine.lastOperation(session.id).id;
    engine.patch(session.id, opId, { status: 'running' });
    await pos.close();

    const pos2 = await BiltPos.connect(() => engine);
    const attached = await pos2.attachTerminalSession(session.id);
    const [handle] = await attached.operations({ status: ['running'] });
    await handle!.abort();
    expect(engine.aborts).toEqual([{ sessionId: session.id, operationId: opId }]);
  });

  it('answers an adopted settle’s pending step with the attach handlers, not old ones twice', async () => {
    const { engine, pos, session } = await lane();
    const op = session.settle(); // no handlers on the original page
    await vi.waitFor(() => expect(engine.requests).toHaveLength(1));
    const opId = engine.lastOperation(session.id).id;
    // A step the original page already answered, then the page goes away before s1.
    engine.step(session.id, opId, rebateStep('s0', '90.00'));
    await vi.waitFor(() => expect(engine.replies).toHaveLength(1));
    expect(engine.replies[0]!.reply).toEqual({ stepId: 's0', total: '90.00' });
    await pos.close();
    engine.step(session.id, opId, rebateStep('s1', '88.00'));

    const pos2 = await BiltPos.connect(() => engine);
    await pos2.attachTerminalSession(session.id, {
      settlement: { onRebatesRedeemed: () => '80.00' },
    });
    await vi.waitFor(() => expect(engine.replies).toHaveLength(2));
    expect(engine.replies[1]).toMatchObject({
      sessionId: session.id,
      operationId: opId,
      reply: { stepId: 's1', total: '80.00' },
    });
    // The replayed s0 event must not produce a second reply for it.
    await new Promise((r) => setTimeout(r, 20));
    expect(engine.replies).toHaveLength(2);
    void op.catch(() => undefined);
  });

  it('returns the same handle for an operation this session started', async () => {
    const { engine, pos, session } = await lane();
    await pos.close();
    const pos2 = await BiltPos.connect(() => engine);
    const attached = await pos2.attachTerminalSession(session.id);

    const own = attached.requestDigitString('Phone?');
    await vi.waitFor(() => expect(engine.requests).toHaveLength(1));
    const listed = await attached.operations();
    expect(listed[0]).toBe(own);
  });
});
