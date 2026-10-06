// Terminal sessions against the real Session Host and a scripted terminal: settlement steps
// answered by handlers, the host default when a handler is absent, abort mid-operation, and a
// reconnect mid-settlement with the pending step replayed through `since`.
import type { OperationStep, RebateRedemptionResult, SettlementMovement } from '@bilt/pos-protocol';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import {
  SessionError,
  type BiltPos,
  type StepInfo,
  type TerminalShopperSession,
} from '../../src/index';
import { FakeTerminal } from './fake-terminal';
import {
  buildHost,
  hostAvailable,
  hostProbe,
  skipReason,
  startHost,
  type RunningHost,
} from './host';
import { TcpProxy } from './proxy';
import { connectTo } from './setup';

const POI = 'VictaLane-1';

if (!hostAvailable) console.warn(`[contract] skipping: ${skipReason()}`);

describe.skipIf(!hostAvailable)('terminal sessions over the Terminal Bridge engine', () => {
  let terminal: FakeTerminal;
  let host: RunningHost;
  let proxy: TcpProxy;
  let pos: BiltPos;
  const open: TerminalShopperSession[] = [];

  beforeAll(async () => {
    if (!('location' in hostProbe)) return;
    buildHost(hostProbe.location);
    terminal = new FakeTerminal();
    const address = await terminal.start();
    host = await startHost(hostProbe.location, [{ poiId: POI, ...address, model: 'VictaLane' }]);
    proxy = new TcpProxy('127.0.0.1', host.port);
    const port = await proxy.start();
    pos = await connectTo(port);
  });

  afterEach(async () => {
    terminal?.release();
    for (const session of open.splice(0)) {
      if (session.state === 'open') await session.forceEnd('test cleanup').catch(() => undefined);
    }
  });

  afterAll(async () => {
    await pos?.close();
    await proxy?.stop();
    await host?.stop();
    await terminal?.stop();
  });

  async function lane(): Promise<TerminalShopperSession> {
    const session = await pos.startTerminalSession({
      saleId: 'LANE-1',
      poiId: POI,
      currency: 'USD',
      storeLocation: 'STR-1',
      autoDisplay: false,
    });
    open.push(session);
    await session.member.set({ id: '98234' });
    await session.basket.addItem({ sku: 'SKU-1', description: 'Item', unitPrice: '100.00' });
    return session;
  }

  it('lists the terminal and brackets a session on it', async () => {
    expect(await pos.terminals()).toEqual([{ poiId: POI, model: 'VictaLane' }]);
    const session = await lane();
    expect(session.kind).toBe('terminal');
    expect(session.poiId).toBe(POI);
    expect(terminal.requestsOf('Admin')).toHaveLength(1);
    await session.end();
    expect(session.state).toBe('ended');
    expect(terminal.requestsOf('Admin')).toHaveLength(2);
  });

  it('settles with a TOTAL_REQUIRED step answered by the handler', async () => {
    const session = await lane();
    const steps: OperationStep[] = [];
    session.on('operation.step', (step) => steps.push(step));
    const movements: SettlementMovement[] = [];
    const charged: SettlementMovement[] = [];
    const handler = vi.fn(async (result: RebateRedemptionResult, _step: StepInfo) => {
      expect(result.suggestedTotal).toBe('90.00');
      return '89.50';
    });

    const settlement = session.settle({
      onRebatesRedeemed: handler,
      onMovement: (m) => movements.push(m),
      onCardCharged: (m) => charged.push(m),
    });
    expect(settlement.type).toBe('settle');
    expect(settlement.status).toBe('queued');
    const result = await settlement;

    expect(settlement.status).toBe('succeeded');
    expect(handler).toHaveBeenCalledTimes(1);
    expect(handler.mock.calls[0]?.[1]?.deadline).toBeInstanceOf(Date);
    expect(result.cardAmountCharged).toBe('89.50');
    expect(result.totalRebateAmount).toBe('10.00');
    expect(result.totalPointsEarned).toBe(89);
    expect(steps.map((s) => s.kind)).toEqual(['TOTAL_REQUIRED']);
    expect(charged).toHaveLength(1);
    expect(charged[0]?.amount).toBe('89.50');
    expect(movements.map((m) => m.step)).toContain('AWARD');
    expect(session.context.phase()).toBe('COMPLETE');
    await session.end();
  });

  it('applies the host default when no handler was given', async () => {
    const session = await lane();
    const steps: OperationStep[] = [];
    session.on('operation.step', (step) => steps.push(step));
    const result = await session.settle();
    expect(result.cardAmountCharged).toBe('90.00');
    expect(steps).toEqual([]);
    await session.end();
  });

  it('aborts a settlement waiting on the register and keeps the session open', async () => {
    const session = await lane();
    const settlement = session.settle({
      onRebatesRedeemed: (_result, step) =>
        new Promise<string>((resolve) => {
          step.signal.addEventListener('abort', () => resolve('0'));
        }),
    });
    await vi.waitFor(() => expect(settlement.status).toBe('awaitingReply'), { timeout: 15_000 });
    await settlement.abort();
    const failure = await settlement.catch((e: unknown) => e);
    expect(failure).toBeInstanceOf(SessionError);
    expect((failure as SessionError).code).toBe('ABORTED');
    expect(settlement.status).toBe('aborted');
    expect(session.state).toBe('open');
    expect(session.basket.current.items).toHaveLength(1);
    // The host unwinds the committed rebate after an abort; `afterEach` force-ends the session,
    // since a plain `end()` is refused while that unwind is still open.
  });

  it('replays a pending step through since after the connection dropped mid-settlement', async () => {
    const session = await lane();
    const rebateReached = terminal.hold('Loyalty/Rebate');
    const handler = vi.fn(() => '85.00');
    const settlement = session.settle({ onRebatesRedeemed: handler });
    await rebateReached;

    const linesBefore = proxy.requestLines.length;
    expect(proxy.dropAll()).toBeGreaterThan(0);
    terminal.release();

    const result = await settlement;
    expect(result.cardAmountCharged).toBe('85.00');
    expect(handler).toHaveBeenCalledTimes(1);
    const reconnects = proxy.requestLines
      .slice(linesBefore)
      .filter((line) => line.includes(`/v1/sessions/${session.id}/events?since=`));
    expect(reconnects.length).toBeGreaterThan(0);
    await session.end();
  });

  it('queues end behind an operation in flight and settles both', async () => {
    const session = await lane();
    const inputReached = terminal.hold('Input');
    const confirmation = session.requestConfirmation('Receipt?');
    await inputReached;
    const ending = session.end();
    expect(session.state).toBe('ending');
    terminal.release();
    await expect(confirmation).resolves.toBe(true);
    await ending;
    expect(session.state).toBe('ended');
  });

  it('runs session-less terminal operations through the terminal facade', async () => {
    terminal.reply(
      'Diagnosis',
      '{"SaleToPOIResponse":{"DiagnosisResponse":{"Response":{"Result":"Success"},"HostStatus":[]}}}',
    );
    const diagnosis = await pos.terminal(POI).diagnose();
    expect(diagnosis.hostStatuses).toEqual([]);
    await expect(pos.terminal('nope').diagnose()).rejects.toMatchObject({ code: 'NOT_FOUND' });
  });
});
