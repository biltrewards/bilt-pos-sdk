// The emulator's checkout contract against the real Session Host and a scripted terminal, using
// the launcher and fake terminal of `@bilt/pos-sdk`'s contract suite: a sale whose rebate step
// is re-taxed by the register (`TOTAL_REQUIRED`) and whose declined card charge is retried
// through the recovery decision (`RECOVERY_REQUIRED`), the sale persisted the way the Refunds
// tab needs it, a gift-card tender, and an item return of the persisted sale from a later
// session, refunded to the original card.
import { writeFileSync } from 'node:fs';
import type { OperationStep, SettlementFailure } from '@bilt/pos-protocol';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import type { BiltPos, TerminalShopperSession } from '@bilt/pos-sdk';
import { FakeTerminal, paymentEcho } from '../../../../packages/sdk/test/contract/fake-terminal';
import {
  buildHost,
  hostAvailable,
  hostProbe,
  skipReason,
  startHost,
  type RunningHost,
} from '../../../../packages/sdk/test/contract/host';
import { connectTo } from '../../../../packages/sdk/test/contract/setup';
import { CATALOG, toBasketItem } from '../../src/catalog';
import { totalAfterRebates } from '../../src/emulator/controller';
import {
  planAllocations,
  planReturn,
  requiredRefundMinor,
  returnLine,
  settledReturns,
  toStoredSaleUi,
} from '../../src/emulator/returns';
import { remainingLegAmount, toSaleRecord } from '../../src/store/sale-record';
import { IndexedDbSaleStore } from '../../src/store/sales-store';

const POI = 'VictaLane-EMU';
const CARD = '6006491260550218157';

/** A $10 rebate on the lamp line, so the register has something to re-tax. */
const LAMP_REBATE =
  '{"SaleToPOIResponse":{"LoyaltyResponse":{' +
  '"Response":{"Result":"Success"},' +
  '"POIData":{"POITransactionID":{"TransactionID":"POI-RB-EMU","TimeStamp":"2026-10-06T10:00:01Z"}},' +
  '"LoyaltyResult":[{"Rebates":{"TotalRebate":10.00,"RebateLabel":"Gold Member",' +
  '"SaleItemRebate":[{"ItemID":1,"ProductCode":"SKU-0013","ItemAmount":10.00,"RebateLabel":"Gold: $10 off"}]}}]}}}';

const PAYMENT_DECLINED =
  '{"SaleToPOIResponse":{"PaymentResponse":{' +
  '"Response":{"Result":"Failure","ErrorCondition":"Refusal","AdditionalResponse":"Do not honour"},' +
  '"POIData":{"POITransactionID":{"TransactionID":"POI-PAY-DECLINED","TimeStamp":"2026-10-06T10:00:02Z"}},' +
  '"PaymentResult":{"AmountsResp":{"Currency":"USD","AuthorizedAmount":0}}}}}';

const lamp = CATALOG.find((product) => product.sku === 'SKU-0013')!;
const coffee = CATALOG.find((product) => product.sku === 'SKU-0006')!;

if (!hostAvailable) console.warn(`[contract] skipping: ${skipReason()}`);

describe.skipIf(!hostAvailable)('the emulator against the Session Host', () => {
  let terminal: FakeTerminal;
  let host: RunningHost;
  let pos: BiltPos;
  let store: IndexedDbSaleStore;
  const open: TerminalShopperSession[] = [];

  beforeAll(async () => {
    if (!('location' in hostProbe)) return;
    buildHost(hostProbe.location);
    terminal = new FakeTerminal();
    terminal.reply('Loyalty/Rebate', LAMP_REBATE);
    const address = await terminal.start();
    host = await startHost(hostProbe.location, { ...address, model: 'VictaLane' });
    pos = await connectTo(host.port);
    store = new IndexedDbSaleStore(indexedDB, `contract-${Date.now()}`);
  });

  afterAll(async () => {
    for (const session of open.splice(0)) {
      if (session.state === 'open') await session.forceEnd('test cleanup').catch(() => undefined);
    }
    await pos?.close();
    await host?.stop();
    await terminal?.stop();
    // `BILT_HOST_LOG=/path` keeps the host's output for a failed run.
    if (process.env.BILT_HOST_LOG && host) writeFileSync(process.env.BILT_HOST_LOG, host.log());
  });

  async function lane(): Promise<TerminalShopperSession> {
    const session = await pos.startTerminalSession({
      saleId: 'LANE-EMU',
      poiId: POI,
      currency: 'USD',
      storeLocation: 'STR-0142',
      autoDisplay: false,
    });
    open.push(session);
    return session;
  }

  it('sells a taxed item: re-taxes the rebate, retries the declined charge, and persists the sale', async () => {
    const session = await lane();
    await session.member.set({ id: '98234' });
    await session.basket.addItem(toBasketItem(lamp));
    expect(session.basket.current.grandTotal).toBe('37.31');

    // Decline the first card charge; the register answers RETRY and the second attempt approves.
    let charges = 0;
    terminal.reply('Payment', (request) =>
      ++charges === 1 ? PAYMENT_DECLINED : paymentEcho(request),
    );

    const steps: OperationStep['kind'][] = [];
    session.on('operation.step', (step) => steps.push(step.kind));
    const failures: SettlementFailure[] = [];
    const transactionIds: string[] = [];
    const result = await session.settle({
      beforeStep: (context) => {
        transactionIds.push(context.defaultTransactionId);
        return context.defaultTransactionId;
      },
      onRebatesRedeemed: totalAfterRebates,
      onError: (failure) => {
        failures.push(failure);
        return 'RETRY';
      },
    });

    // 34.99 − 10.00 rebate = 24.99, taxed at 6.625% = 1.66 → 26.65, not the host's 27.31.
    expect(Number(result.cardAmountCharged)).toBe(26.65);
    expect(Number(result.totalRebateAmount)).toBe(10);
    expect(failures).toHaveLength(1);
    expect(failures[0]).toMatchObject({ step: 'CARD_CHARGE', outcomeCertainty: 'DEFINITIVE' });
    expect(failures[0]?.error.code).toBe('DECLINED');
    expect(charges).toBe(2);
    expect(steps).toContain('TOTAL_REQUIRED');
    expect(steps).toContain('RECOVERY_REQUIRED');
    expect(new Set(transactionIds).size).toBe(1);
    // The host moves the phase with a context event that can land after the settle result.
    await vi.waitFor(() => expect(session.context.phase()).toBe('COMPLETE'));

    const record = toSaleRecord(result, {
      sessionId: session.id,
      saleId: session.saleId,
      poiId: session.poiId,
      currency: session.currency,
      memberId: '98234',
      recordId: 'sale-contract-1',
      completedAt: new Date(),
    });
    expect(record.legs.map((leg) => leg.type).sort()).toEqual(['AWARD', 'CARD', 'REBATE']);
    expect(record.legs.find((leg) => leg.type === 'CARD')).toMatchObject({
      poiTransactionId: 'POI-PAY-1',
      amount: '26.65',
      approvalCode: 'APPR7',
      brand: 'Visa',
    });
    expect(record.items.map((item) => item.sku)).toEqual(['SKU-0013']);
    await store.recordSale(record);
    await session.end();
  });

  it('pays with a gift card first and records the stored value leg', async () => {
    terminal.reply('Payment', paymentEcho);
    const session = await lane();
    await session.basket.addItem(toBasketItem(coffee));
    expect(session.basket.current.grandTotal).toBe('3.75');
    await session.setStoredValueCard(CARD);

    let giftCardSuggested: string | undefined;
    const result = await session.settle({
      disableRebates: true,
      disablePoints: true,
      disableAward: true,
      onGiftCardPayment: (giftCard) => {
        giftCardSuggested = giftCard.suggestedTotal;
        return giftCard.suggestedTotal;
      },
    });

    expect(Number(result.storedValueAmountUsed)).toBe(3.75);
    expect(Number(giftCardSuggested)).toBe(0);
    expect(Number(result.cardAmountCharged)).toBe(0);
    expect(result.movements.map((m) => m.step)).toEqual(['STORED_VALUE_CHARGE']);
    const record = toSaleRecord(result, {
      sessionId: session.id,
      saleId: session.saleId,
      poiId: session.poiId,
      currency: session.currency,
      memberId: undefined,
      recordId: 'sale-contract-2',
      completedAt: new Date(),
    });
    expect(record.legs.map((leg) => leg.type)).toEqual(['STORED_VALUE']);
    expect(record.legs[0]?.amount).toBe('3.75');
    await store.recordSale(record);
    await session.end();
  });

  it('returns the persisted sale item from a later session against its card reference', async () => {
    const stored = await store.findSale('sale-contract-1');
    expect(stored).not.toBeNull();
    const plan = planReturn(stored!, new Set(['SKU-0013']), () => 0);
    if ('error' in plan) throw new Error(plan.error);
    // Shelf price plus tax, 37.31; the card collected only 26.65 after the rebate.
    expect(plan.pending).toMatchObject({ amountMinor: 3731, legCapacityMinor: 2665 });

    const session = await lane();
    await session.basket.addItem(returnLine(plan.pending.items[0]!));
    expect(session.basket.current.grandTotal).toBe('-37.31');
    const { returns, allocations } = planAllocations(
      [plan.pending],
      requiredRefundMinor(session.basket.current, false),
    );
    expect(allocations).toEqual([
      {
        type: 'CARD',
        amount: '26.65',
        originalPoiTransactionId: 'POI-PAY-1',
        originalPoiTransactionTimestamp: expect.any(String),
      },
      { type: 'EXTERNAL', amount: '10.66' },
    ]);
    const result = await session.settle({
      settlementType: 'REFUND_THEN_CHARGE',
      refunds: allocations,
      disableRebates: true,
      disablePoints: true,
      disableAward: true,
    });

    expect(Number(result.cardRefundedAmount)).toBe(26.65);
    expect(Number(result.cardAmountCharged)).toBe(0);
    const refundMovement = result.movements.find((m) => m.step === 'CARD_REFUND');
    expect(Number(refundMovement?.amount)).toBe(26.65);
    // The linked refund named the original card payment on the wire.
    const refundRequests = terminal
      .requestsOf('Payment')
      .filter((body) => JSON.stringify(body).includes('"POI-PAY-1"'));
    expect(refundRequests.length).toBeGreaterThan(0);

    const { records, parts } = settledReturns(returns, result);
    expect(parts[0]).toContain('returned $37.31 ($26.65 to the card, $10.66 register-paid');
    for (const record of records) await store.recordRefund(record);
    const after = await store.findSale('sale-contract-1');
    expect(after?.refunds[0]).toMatchObject({
      amount: '37.31',
      tenderAmount: '26.65',
      leg: 'CARD',
      full: false,
      items: [{ sku: 'SKU-0013', quantity: 1 }],
    });
    expect(remainingLegAmount(after!, 'CARD')).toBe('0.00');
    expect(toStoredSaleUi(after!, () => 0).items[0]?.remainingQuantity).toBe(0);
    await session.end();
  });

  // RET-6983: needs the host change (feature/ret-6983-vas-host) to map a wallet-pass read onto
  // IdentifyResult.vasData; the in-memory tests cover the emulator's rendering until it lands.
  it.todo('shows the wallet pass on the sign-in card when Read VAS leaves the entry mode unforced');
});
