// Terminal sessions against the real Session Host and a scripted terminal: settlement steps
// answered by handlers, the host default when a handler is absent, abort mid-operation, and a
// reconnect mid-settlement with the pending step replayed through `since`.
import type {
  OperationStep,
  OriginalSaleRecord,
  PartialCardApproval,
  RebateRedemptionResult,
  SettlementMovement,
} from '@biltorg/pos-protocol';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  SessionError,
  type BiltPos,
  type StepInfo,
  type TerminalShopperSession,
} from '../../src/index';
import {
  FakeTerminal,
  paymentEcho,
  paymentApproving,
  paymentWithBiltRewards,
} from './fake-terminal';
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
    host = await startHost(hostProbe.location, { ...address, model: 'VictaLane' });
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

  it('reports the terminal and brackets a session on it with the poiId passed through', async () => {
    expect(await pos.terminalInfo()).toEqual({ model: 'VictaLane' });
    const session = await lane();
    expect(session.kind).toBe('terminal');
    expect(session.poiId).toBe(POI);
    const admin = terminal.requestsOf('Admin');
    expect(admin).toHaveLength(1);
    expect(JSON.stringify(admin[0])).toContain(`"POIID":"${POI}"`);
    await session.end();
    expect(session.state).toBe('ended');
    expect(terminal.requestsOf('Admin')).toHaveLength(2);
  });

  it('runs a session without a poiId on the same terminal under the host default', async () => {
    const session = await pos.startTerminalSession({
      saleId: 'LANE-1',
      currency: 'USD',
      autoDisplay: false,
    });
    open.push(session);
    expect(session.poiId).toBe('bilt-session-host');
    await session.end();
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

    const settlement = session.settle(undefined, {
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
    // The host moves the phase with a context event that can land after the settle result.
    await vi.waitFor(() => expect(session.context.phase()).toBe('COMPLETE'));
    await session.end();
  });

  it('reports each card payment and voids a sale record holding several', async () => {
    terminal.reply(
      'Reversal',
      '{"SaleToPOIResponse":{"ReversalResponse":{"Response":{"Result":"Success"}}}}',
    );
    const session = await lane();
    const result = await session.settle();
    expect(result.cardPayments).toEqual([
      expect.objectContaining({
        amount: '90.00',
        poiTransactionId: 'POI-PAY-1',
        approvalCode: 'APPR7',
        acquirerTransactionId: 'ACQ-1',
        paymentBrand: 'Visa',
        partialApproval: false,
      }),
    ]);

    // A record the register persisted, with a second card payment from another lane.
    const record: OriginalSaleRecord = {
      cardPayments: [
        ...result.cardPayments.flatMap(({ poiTransactionId, poiTransactionTimestamp, amount }) =>
          poiTransactionId
            ? [
                {
                  poiTransactionId,
                  amount,
                  ...(poiTransactionTimestamp ? { poiTransactionTimestamp } : {}),
                },
              ]
            : [],
        ),
        { poiTransactionId: 'POI-PAY-2', amount: '10.00' },
      ],
      memberId: '98234',
    };
    const later = await lane();
    const voided = await later.voidTransaction(record);
    expect(voided.cardReversals.map((c) => c.originalPoiTransactionId)).toEqual([
      'POI-PAY-2',
      'POI-PAY-1',
    ]);
    expect(voided.reversedAmountByTender).toEqual([
      { tender: 'CARD', poiTransactionId: 'POI-PAY-2', amount: '10.00' },
      { tender: 'CARD', poiTransactionId: 'POI-PAY-1', amount: '90.00' },
    ]);
    expect(voided.reversedAmount).toBe('100.00');
    const reversed = terminal
      .requestsOf('Reversal')
      .map((body) => JSON.stringify(body).match(/"TransactionID":"(POI-PAY-\d)"/)?.[1]);
    expect(reversed).toEqual(['POI-PAY-2', 'POI-PAY-1']);
    await session.end();
  });

  describe('split and partial card payments', () => {
    // the terminal is shared by the whole file: count only what this test sent
    let seen = 0;
    beforeEach(() => {
      seen = terminal.requests.length;
    });
    afterEach(() => {
      terminal.reply('Payment', paymentEcho);
    });

    function sent(category: string): string[] {
      return terminal.requests
        .slice(seen)
        .filter((body) => body.SaleToPOIRequest?.MessageHeader?.MessageCategory === category)
        .map((body) => JSON.stringify(body));
    }
    const paymentRequests = () => sent('Payment');

    it('splits the card amount into the planned payments and the remainder', async () => {
      terminal.reply('Payment', paymentApproving());
      const session = await lane();
      const contexts: { stepAmount: string | undefined; currentTotal: string }[] = [];
      const charged: SettlementMovement[] = [];

      const result = await session.settle(
        { cardPayments: [{ amount: '20.00' }] },
        {
          beforeStep: (context) => {
            if (context.step === 'CARD_CHARGE') {
              contexts.push({ stepAmount: context.stepAmount, currentTotal: context.currentTotal });
            }
            return undefined;
          },
          onCardCharged: (m) => charged.push(m),
        },
      );

      expect(result.cardPayments.map((p) => p.amount)).toEqual(['20.00', '70.00']);
      expect(result.cardAmountCharged).toBe('90.00');
      expect(result.cardPayments.map((p) => p.partialApproval)).toEqual([false, false]);
      expect(charged.map((m) => m.amount)).toEqual(['20.00', '70.00']);
      expect(contexts).toEqual([
        { stepAmount: '20.00', currentTotal: '90.00' },
        { stepAmount: '70.00', currentTotal: '70.00' },
      ]);
      const sent = paymentRequests();
      expect(sent).toHaveLength(2);
      expect(sent[0]).not.toContain('SplitPaymentFlag');
      expect(sent[1]).toContain('"SplitPaymentFlag":true');
      expect(sent[1]).toMatch(/"PaidAmount":20(\.0+)?[,}]/);
      await session.end();
    });

    it('fails planned payments that add up to the total with CARD_PAYMENTS_EXCEED_TOTAL', async () => {
      const session = await lane();
      const failure = await session
        .settle({ cardPayments: [{ amount: '90.00' }] })
        .catch((e: unknown) => e);
      expect(failure).toBeInstanceOf(SessionError);
      expect((failure as SessionError).code).toBe('CARD_PAYMENTS_EXCEED_TOTAL');
      expect(paymentRequests()).toEqual([]);
    });

    it('asks onPartialCardApproval and charges the shortfall on another card', async () => {
      terminal.reply('Payment', paymentApproving(0.6));
      const session = await lane();
      const steps: OperationStep[] = [];
      session.on('operation.step', (step) => steps.push(step));
      const order: string[] = [];
      const handler = vi.fn((_partial: PartialCardApproval, _step: StepInfo) => {
        order.push('partial');
        return 'ANOTHER_CARD' as const;
      });

      const result = await session.settle(undefined, {
        onCardCharged: (m) => order.push(`charged ${m.amount}`),
        onPartialCardApproval: handler,
      });

      expect(handler).toHaveBeenCalledTimes(1);
      const partial = handler.mock.calls[0]![0];
      expect(partial).toMatchObject({
        approved: '54.00',
        requested: '90.00',
        shortfall: '36.00',
        amountDue: '36.00',
      });
      expect(partial.cardPayments).toHaveLength(1);
      expect(partial.movement).toMatchObject({ step: 'CARD_CHARGE', amount: '54.00' });
      expect(steps.map((s) => s.kind)).toEqual(['PARTIAL_CARD_APPROVAL']);
      expect(order).toEqual(['charged 54.00', 'partial', 'charged 36.00']);
      expect(result.cardPayments.map((p) => [p.amount, p.partialApproval])).toEqual([
        ['54.00', true],
        ['36.00', false],
      ]);
      expect(result.cardAmountCharged).toBe('90.00');
      expect(paymentRequests()[1]).toContain('"SplitPaymentFlag":true');
      await session.end();
    });

    it('reports a Bilt rewards leg apart from the cards and voids it', async () => {
      terminal.reply('Payment', paymentWithBiltRewards(0.4));
      terminal.reply(
        'Reversal',
        '{"SaleToPOIResponse":{"ReversalResponse":{"Response":{"Result":"Success"}}}}',
      );
      const session = await lane();
      const handler = vi.fn(
        (_partial: PartialCardApproval, _step: StepInfo) => 'ANOTHER_CARD' as const,
      );

      const result = await session.settle(undefined, { onPartialCardApproval: handler });

      const partial = handler.mock.calls[0]![0];
      expect(partial.biltRewardsAmount).toBe('36.00');
      expect(partial.biltRewardsPayment).toMatchObject({
        amount: '36.00',
        poiTransactionId: 'ATT-1',
      });
      expect(partial.cardPayments).toEqual([]);
      expect(result.cardPayments.map((p) => [p.poiTransactionId, p.cardAmount])).toEqual([
        ['POI-PAY-1', '54.00'],
      ]);
      expect(result.biltRewardsPayments).toEqual([
        {
          amount: '36.00',
          poiTransactionId: 'ATT-1',
          poiTransactionTimestamp: '2026-07-20T10:00:02Z',
        },
      ]);
      expect(result.biltRewardsAmount).toBe('36.00');
      expect(result.cardAmountCharged).toBe('54.00');
      expect(result.finalBasket.biltRewardsTotal).toBe('36.00');
      expect(paymentRequests()[1]).toContain('"PaidAmount":36');

      const voided = await session.voidTransaction();
      expect(voided.cardReversals.map((c) => c.originalPoiTransactionId)).toEqual(['POI-PAY-1']);
      expect(
        voided.biltRewardsReversals.map((c) => [c.tenderType, c.originalPoiTransactionId]),
      ).toEqual([['BILT_REWARDS', 'ATT-1']]);
      expect(voided.reversedAmountByTender.map((t) => [t.tender, t.amount])).toEqual([
        ['CARD', '54.00'],
        ['BILT_REWARDS', '36.00'],
      ]);
      await session.end();
    });

    it('records the rest as an external payment and keeps the card payment', async () => {
      terminal.reply('Payment', paymentApproving(0.6));
      const session = await lane();
      const result = await session.settle(undefined, {
        onPartialCardApproval: (partial) => ({
          action: 'EXTERNAL',
          externalPayment: { tenderType: 'CASH', amount: partial.amountDue },
        }),
      });
      expect(result.cardPayments).toHaveLength(1);
      expect(result.cardAmountCharged).toBe('54.00');
      expect(result.externalPaymentAmount).toBe('36.00');
      expect(paymentRequests()).toHaveLength(1);
      await session.end();
    });

    it('aborts a partial approval: every card payment is reversed, reported, and the sale is DECLINED', async () => {
      terminal.reply('Payment', paymentApproving(0.6));
      const session = await lane();
      const charged: SettlementMovement[] = [];
      const reversed: SettlementMovement[] = [];
      const everything: SettlementMovement[] = [];
      const failure = await session
        .settle(undefined, {
          onPartialCardApproval: () => 'ABORT',
          onCardCharged: (m) => charged.push(m),
          onCardChargeReversed: (m) => reversed.push(m),
          onMovement: (m) => everything.push(m),
        })
        .catch((e: unknown) => e);
      expect(failure).toBeInstanceOf(SessionError);
      expect((failure as SessionError).code).toBe('DECLINED');
      expect(sent('Reversal').filter((json) => json.includes('POI-PAY-1'))).toHaveLength(1);

      // the abort's reversal comes back as a notice that undoes the reported charge
      expect(charged.map((m) => m.amount)).toEqual(['54.00']);
      expect(reversed).toHaveLength(1);
      expect(reversed[0]).toMatchObject({
        step: 'CARD_CHARGE',
        amount: '54.00',
        reversal: true,
        reversedMovement: { poiTransactionId: 'POI-PAY-1', serviceId: charged[0]?.serviceId },
      });
      expect(reversed[0]?.serviceId).toBeTruthy();
      expect(reversed[0]?.serviceId).not.toBe(charged[0]?.serviceId);
      expect(everything.filter((m) => m.reversal).map((m) => m.step)).toContain('CARD_CHARGE');
    });

    it('leaves the shortfall to the SDK when no handler is registered', async () => {
      terminal.reply('Payment', paymentApproving(0.6, 0.5));
      const session = await lane();
      const steps: OperationStep[] = [];
      session.on('operation.step', (step) => steps.push(step));

      const result = await session.settle(undefined, { onCardCharged: () => undefined });

      expect(steps).toEqual([]);
      // 90.00: 54.00 approved, then 36.00 asked and 18.00 approved, then the last 18.00
      expect(result.cardPayments.map((p) => p.amount)).toEqual(['54.00', '18.00', '18.00']);
      expect(result.cardAmountCharged).toBe('90.00');
      await session.end();
    });

    it('stops the automatic follow-ups at three card requests and fails as DECLINED', async () => {
      terminal.reply('Payment', paymentApproving(0.5, 0.5, 0.5, 0.5));
      const session = await lane();
      const failure = await session.settle().catch((e: unknown) => e);
      expect(failure).toBeInstanceOf(SessionError);
      expect((failure as SessionError).code).toBe('DECLINED');
      expect(paymentRequests()).toHaveLength(3);
    });
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
    const settlement = session.settle(undefined, {
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
    const settlement = session.settle(undefined, { onRebatesRedeemed: handler });
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

  // RET-6983 layer 2 (host, feature/ret-6983-vas-host) maps VasData onto the identify result and
  // FakeTerminal needs a scripted loyalty identify answer carrying a pass. Enable once it lands.
  it.todo('returns vasData from identifyMember when the terminal reads a wallet pass');

  it('runs session-less terminal operations through the terminal facade', async () => {
    terminal.reply(
      'Diagnosis',
      '{"SaleToPOIResponse":{"DiagnosisResponse":{"Response":{"Result":"Success"},"HostStatus":[]}}}',
    );
    const diagnosis = await pos.terminal(POI).diagnose();
    expect(diagnosis.hostStatuses).toEqual([]);
    expect((await pos.terminal('ANY-LANE').diagnose()).hostStatuses).toEqual([]);
    expect(JSON.stringify(terminal.requestsOf('Diagnosis').at(-1))).toContain('"POIID":"ANY-LANE"');
  });
});
