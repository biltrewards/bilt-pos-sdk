// The persisted sale: the projection from a SettlementResult, the IndexedDB store, and the
// return and full-refund plans built from a stored sale.
import type { SettlementResult } from '@bilt/pos-sdk';
import { describe, expect, it } from 'vitest';
import * as fx from '../../../packages/sdk/test/fixtures';
import {
  fullRefundRecord,
  planAllocations,
  planReturn,
  returnLine,
  settledReturns,
  toStoredSaleUi,
} from '../src/emulator/returns';
import { defaultReversalDecision, reversalProgress } from '../src/store/reversals';
import {
  originalSaleRecord,
  remainingLegAmount,
  toSaleRecord,
  type StoredSale,
} from '../src/store/sale-record';
import { IndexedDbSaleStore } from '../src/store/sales-store';

const CONTEXT = {
  sessionId: 'ses_1',
  saleId: 'LANE-3',
  poiId: 'VictaLane-1',
  currency: 'USD',
  memberId: '98234',
  recordId: 'sale-1',
  completedAt: new Date('2026-10-06T14:03:11.412Z'),
};

function result(overrides: Partial<SettlementResult> = {}): SettlementResult {
  const basket = fx.basket([
    fx.lineItem({
      sku: 'SKU-0013',
      description: 'Desk Lamp',
      unitPrice: '34.99',
      taxRate: '0.06625',
    }),
    fx.lineItem({
      sku: 'GIFT-CARD',
      description: 'Gift card',
      unitPrice: '25.00',
      reference: 'gift-card-1',
    }),
  ]);
  return {
    ...fx.settlementResult(basket, '62.31'),
    poiTransactionId: 'POI-PAY-1',
    poiTransactionTimestamp: '2026-10-06T14:03:10Z',
    awardPoiTransactionId: 'POI-AW-1',
    rebatePoiTransactionId: 'POI-RB-1',
    totalRebateAmount: '10.00',
    movements: [
      {
        step: 'REBATE_REDEMPTION',
        target: { type: 'SALES' },
        amount: '10.00',
        poiTransactionId: 'POI-RB-1',
      },
      {
        step: 'STORED_VALUE_LOAD',
        target: { type: 'BASKET_LINE', basketReference: 'gift-card-1' },
        amount: '25.00',
        poiTransactionId: 'POI-SV-LOAD-1',
      },
      {
        step: 'CARD_CHARGE',
        target: { type: 'SALES' },
        amount: '62.31',
        poiTransactionId: 'POI-PAY-1',
      },
      {
        step: 'AWARD',
        target: { type: 'SALES' },
        amount: '0',
        poiTransactionId: 'POI-AW-1',
        points: 62,
      },
    ],
    ...overrides,
  };
}

describe('toSaleRecord', () => {
  it('records the committed legs, the gift-card load and the refundable merchandise', () => {
    const record = toSaleRecord(result(), CONTEXT);
    expect(record.legs.map((leg) => leg.type)).toEqual(['CARD', 'AWARD', 'REBATE']);
    expect(record.legs[0]).toMatchObject({
      poiTransactionId: 'POI-PAY-1',
      poiTimestamp: '2026-10-06T14:03:10Z',
      amount: '62.31',
      approvalCode: 'A1B2C3',
      brand: 'Visa',
    });
    expect(record.giftCardLoads).toEqual([
      { basketReference: 'gift-card-1', amount: '25.00', poiTransactionId: 'POI-SV-LOAD-1' },
    ]);
    // The fulfilled gift card is unwound with its load, not returned as merchandise.
    expect(record.items.map((item) => item.sku)).toEqual(['SKU-0013']);
    expect(record).toMatchObject({ memberId: '98234', authorizedAmount: '62.31', currency: 'USD' });
  });

  it('does not record a gift-card-only payment twice', () => {
    const record = toSaleRecord(
      result({
        poiTransactionId: 'POI-SV-1',
        storedValuePoiTransactionId: 'POI-SV-1',
        storedValueAmountUsed: '62.31',
        cardAmountCharged: '0.00',
      }),
      CONTEXT,
    );
    expect(record.legs.map((leg) => leg.type)).toEqual(['STORED_VALUE', 'AWARD', 'REBATE']);
    expect(record.legs[0]).toMatchObject({ amount: '62.31', approvalCode: 'A1B2C3' });
  });
});

describe('return and refund plans', () => {
  const stored: StoredSale = { sale: toSaleRecord(result(), CONTEXT), refunds: [], voided: null };

  it('builds the OriginalSaleRecord from every standing leg', () => {
    expect(originalSaleRecord(stored)).toEqual({
      cardPoiTransactionId: 'POI-PAY-1',
      cardPoiTransactionTimestamp: '2026-10-06T14:03:10Z',
      storedValueLoads: [
        { basketReference: 'gift-card-1', amount: '25.00', poiTransactionId: 'POI-SV-LOAD-1' },
      ],
      rebatePoiTransactionId: 'POI-RB-1',
      awardPoiTransactionId: 'POI-AW-1',
      memberId: '98234',
    });
  });

  it('omits the legs a stopped void already reversed', () => {
    const progress = reversalProgress(stored, [{ step: 'CARD', poiTransactionId: 'POI-REV-1' }]);
    const retried: StoredSale = { ...stored, refunds: progress };
    expect(originalSaleRecord(retried).cardPoiTransactionId).toBeUndefined();
    expect(originalSaleRecord(retried).awardPoiTransactionId).toBe('POI-AW-1');
  });

  it('omits the gift-card loads and rebate a stopped void already reversed', () => {
    const progress = reversalProgress(stored, [
      { step: 'STORED_VALUE_LOAD', poiTransactionId: 'POI-SV-LOAD-1' },
      { step: 'REBATE', poiTransactionId: 'POI-RB-1' },
    ]);
    const retried: StoredSale = { ...stored, refunds: progress };
    expect(originalSaleRecord(retried)).toEqual({
      cardPoiTransactionId: 'POI-PAY-1',
      cardPoiTransactionTimestamp: '2026-10-06T14:03:10Z',
      awardPoiTransactionId: 'POI-AW-1',
      memberId: '98234',
    });
  });

  it('refuses an item return of a sale with a gift-card purchase', () => {
    expect(planReturn(stored, new Set(['SKU-0013']), () => 0)).toEqual({
      error:
        'The sale contains a gift card purchase — use the full refund to reverse its load and funding together',
    });
    expect(toStoredSaleUi(stored, () => 0)).toMatchObject({
      hasGiftCardPurchase: true,
      fullRefundAvailable: true,
    });
  });

  // The same sale without the gift-card purchase, so its merchandise is returnable.
  const tenderOnly: StoredSale = { ...stored, sale: { ...stored.sale, giftCardLoads: [] } };

  it('plans an item return at shelf price plus tax against the card leg', () => {
    const plan = planReturn(tenderOnly, new Set(['SKU-0013']), () => 0);
    if ('error' in plan) throw new Error(plan.error);
    // 34.99 + round(34.99 × 6.625%) = 37.31
    expect(plan.pending).toMatchObject({
      saleId: 'sale-1',
      amountMinor: 3731,
      legCapacityMinor: 6231,
    });
    expect(plan.pending.leg.type).toBe('CARD');
    expect(returnLine(plan.pending.items[0]!)).toEqual({
      sku: 'SKU-0013',
      description: 'Desk Lamp',
      quantity: 1,
      unitPrice: '34.99',
      type: 'RETURN',
      taxRate: '0.06625',
    });
    // A return already in the basket leaves nothing to ring.
    expect(planReturn(tenderOnly, new Set(['SKU-0013']), () => 1)).toEqual({
      error: 'Nothing left to return among the selected items',
    });

    const { returns, allocations } = planAllocations([plan.pending], 3731);
    expect(allocations).toEqual([
      {
        type: 'CARD',
        amount: '37.31',
        originalPoiTransactionId: 'POI-PAY-1',
        originalPoiTransactionTimestamp: '2026-10-06T14:03:10Z',
      },
    ]);
    // Under NET a purchase that absorbs the return needs no refund at all.
    expect(planAllocations([plan.pending], 0).allocations).toEqual([]);
    // A tender that collected less than the shelf value refunds what it has; the register pays the rest.
    const capped = planAllocations([{ ...plan.pending, legCapacityMinor: 2000 }], 3731);
    expect(capped.allocations.map((a) => [a.type, a.amount])).toEqual([
      ['CARD', '20.00'],
      ['EXTERNAL', '17.31'],
    ]);

    const { records, parts } = settledReturns(returns, {
      ...result(),
      movements: [
        {
          step: 'CARD_REFUND',
          target: { type: 'REFUNDS' },
          amount: '37.31',
          poiTransactionId: 'POI-RF-1',
        },
      ],
    });
    expect(parts).toEqual(['returned $37.31 to the card']);
    expect(records[0]).toMatchObject({
      amount: '37.31',
      tenderAmount: '37.31',
      leg: 'CARD',
      full: false,
      poiTransactionId: 'POI-RF-1',
      items: [{ sku: 'SKU-0013', quantity: 1 }],
    });
    const after: StoredSale = { ...tenderOnly, refunds: [...records] };
    expect(remainingLegAmount(after, 'CARD')).toBe('25.00');
    const ui = toStoredSaleUi(after, () => 0);
    expect(ui.items[0]).toMatchObject({ remainingQuantity: 0, refundMinor: 0 });
    expect(ui).toMatchObject({ refunded: true, fullyRefunded: false, fullRefundAvailable: false });
  });

  it('records a full refund as exhausting the sale', () => {
    const record = fullRefundRecord(tenderOnly, {
      success: true,
      reversedAmount: '62.31',
      poiTransactionId: 'POI-V-1',
      pointsReversed: 62,
      remainingPointBalance: 0,
    });
    expect(record).toMatchObject({ full: true, awardReversed: true, amount: '62.31' });
    expect(record.leg).toBeUndefined();
    const ui = toStoredSaleUi({ ...tenderOnly, refunds: [record] }, () => 0);
    expect(ui).toMatchObject({ fullyRefunded: true, fullRefundAvailable: false });
  });

  it('mirrors the Java default reversal policy', () => {
    expect(defaultReversalDecision('CARD')).toBe('ABORT');
    expect(defaultReversalDecision('STORED_VALUE_LOAD')).toBe('ABORT');
    expect(defaultReversalDecision('AWARD')).toBe('SKIP');
    expect(defaultReversalDecision(null)).toBe('ABORT');
  });
});

describe('IndexedDbSaleStore', () => {
  it('lists sales newest first with their refunds and void folded in', async () => {
    const store = new IndexedDbSaleStore(indexedDB, `sales-${Date.now()}`);
    const notified: number[] = [];
    store.subscribe(() => notified.push(1));
    const older = toSaleRecord(result(), {
      ...CONTEXT,
      recordId: 'sale-a',
      completedAt: new Date('2026-10-06T10:00:00Z'),
    });
    const newer = toSaleRecord(result(), {
      ...CONTEXT,
      recordId: 'sale-b',
      completedAt: new Date('2026-10-06T11:00:00Z'),
    });
    await store.recordSale(older);
    await store.recordSale(newer);
    await store.recordRefund({
      saleId: 'sale-a',
      recordedAt: 'now',
      amount: '5.00',
      leg: 'CARD',
      full: false,
      awardReversed: false,
      reversalProgress: false,
    });
    await store.recordVoid({ saleId: 'sale-b', recordedAt: 'now', poiTransactionId: 'POI-V-1' });

    const sales = await store.listSales();
    expect(sales.map((s) => s.sale.id)).toEqual(['sale-b', 'sale-a']);
    expect(sales[0]?.voided?.poiTransactionId).toBe('POI-V-1');
    expect(sales[1]?.refunds).toHaveLength(1);
    expect((await store.findSale('sale-a'))?.refunds[0]?.amount).toBe('5.00');
    expect(await store.findSale('nope')).toBeNull();
    expect(notified).toHaveLength(4);
  });
});
