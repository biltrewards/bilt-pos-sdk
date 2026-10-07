// The emulator's checkout contract against the real Session Host and a scripted terminal, using
// the launcher and fake terminal of `@bilt/pos-sdk`'s contract suite: a sale whose rebate step
// is re-taxed by the register (`TOTAL_REQUIRED`) and whose declined card charge is retried
// through the recovery decision (`RECOVERY_REQUIRED`), the sale persisted the way the Refunds
// pane needs it, a gift-card tender, and a referenced refund of the persisted sale from a later
// session.
import { writeFileSync } from 'node:fs';
import type { OperationStep, SettlementFailure } from '@bilt/pos-protocol';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
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
import { recomputeTotal } from '../../src/money';
import { planReferencedRefund, refundRecordFrom } from '../../src/store/reversals';
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
    host = await startHost(hostProbe.location, [{ poiId: POI, ...address, model: 'VictaLane' }]);
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
      onRebatesRedeemed: (rebates) => recomputeTotal(rebates.updatedBasket),
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
    expect(session.context.phase()).toBe('COMPLETE');

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

  it('refunds part of the persisted sale from a later session by its card reference', async () => {
    const stored = await store.findSale('sale-contract-1');
    expect(stored).not.toBeNull();
    const plan = planReferencedRefund(stored!, '10.00');
    if ('error' in plan) throw new Error(plan.error);
    expect(plan.allocations[0]).toMatchObject({
      type: 'CARD',
      amount: '10.00',
      originalPoiTransactionId: 'POI-PAY-1',
    });

    const session = await lane();
    await session.basket.addItem(plan.returnLine);
    const result = await session.settle({
      settlementType: 'REFUND_THEN_CHARGE',
      refunds: [...plan.allocations],
      disableRebates: true,
      disablePoints: true,
      disableAward: true,
    });

    expect(Number(result.cardRefundedAmount)).toBe(10);
    expect(Number(result.cardAmountCharged)).toBe(0);
    const refundMovement = result.movements.find((m) => m.step === 'CARD_REFUND');
    expect(refundMovement?.poiTransactionId).toBe('POI-PAY-1');
    expect(Number(refundMovement?.amount)).toBe(10);
    // The linked refund named the original card payment on the wire.
    const refundRequests = terminal
      .requestsOf('Payment')
      .filter((body) => JSON.stringify(body).includes('"POI-PAY-1"'));
    expect(refundRequests.length).toBeGreaterThan(0);

    await store.recordRefund(refundRecordFrom(stored!, plan, result));
    const after = await store.findSale('sale-contract-1');
    expect(after?.refunds).toHaveLength(1);
    expect(after?.refunds[0]).toMatchObject({ amount: '10.00', leg: 'CARD', full: false });
    expect(remainingLegAmount(after!, 'CARD')).toBe('16.65');
    await session.end();
  });
});
