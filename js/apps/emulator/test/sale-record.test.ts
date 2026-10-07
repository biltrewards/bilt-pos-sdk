// The persisted sale: the projection from a SettlementResult, the IndexedDB store, and the
// reversal plans built from a stored sale.
import type { SettlementResult } from '@bilt/pos-sdk';
import { describe, expect, it } from 'vitest';
import * as fx from '../../../packages/sdk/test/fixtures';
import {
  defaultReversalDecision,
  planReferencedRefund,
  refundRecordFrom,
  reversalProgress,
} from '../src/store/reversals';
import {
  isRefundable,
  isVoidable,
  originalSaleRecord,
  refundsLinked,
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

describe('reversal plans', () => {
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
    expect(isVoidable(retried)).toBe(true);
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
    expect(isVoidable(retried)).toBe(true);
  });

  it('refuses a referenced refund while a gift-card load stands, leaving the void', () => {
    expect(isRefundable(stored)).toBe(false);
    expect(planReferencedRefund(stored, '10.00')).toEqual({
      error: 'The sale loaded a gift card: void it so the load is reversed with its funding.',
    });
    expect(isVoidable(stored)).toBe(true);
  });

  // The same sale without the gift-card purchase, so its tender alone is refundable.
  const tenderOnly: StoredSale = { ...stored, sale: { ...stored.sale, giftCardLoads: [] } };

  it('plans a partial and a full referenced refund against the card leg', () => {
    const partial = planReferencedRefund(tenderOnly, '10.00');
    if ('error' in partial) throw new Error(partial.error);
    expect(partial.full).toBe(false);
    expect(partial.reversesAward).toBe(false);
    expect(partial.allocations).toEqual([
      {
        type: 'CARD',
        amount: '10.00',
        originalPoiTransactionId: 'POI-PAY-1',
        originalPoiTransactionTimestamp: '2026-10-06T14:03:10Z',
      },
    ]);
    expect(partial.returnLine).toMatchObject({ type: 'RETURN', unitPrice: '10.00', quantity: 1 });

    const full = planReferencedRefund(tenderOnly, undefined);
    if ('error' in full) throw new Error(full.error);
    expect(full).toMatchObject({ amount: '62.31', full: true, reversesAward: true });
    expect(full.allocations[1]).toMatchObject({
      type: 'AWARD',
      amount: '0',
      originalPoiTransactionId: 'POI-AW-1',
      memberId: '98234',
    });

    expect(planReferencedRefund(tenderOnly, '100.00')).toEqual({
      error: 'At most 62.31 can still be refunded from the CARD leg.',
    });
  });

  it('keeps the ledger straight across refunds', () => {
    const plan = planReferencedRefund(tenderOnly, '10.00');
    if ('error' in plan) throw new Error(plan.error);
    const refunded = refundRecordFrom(tenderOnly, plan, {
      ...result(),
      movements: [
        {
          step: 'CARD_REFUND',
          target: { type: 'REFUNDS' },
          amount: '10.00',
          poiTransactionId: 'POI-RF-1',
        },
      ],
    });
    expect(refunded).toMatchObject({
      amount: '10.00',
      leg: 'CARD',
      full: false,
      poiTransactionId: 'POI-RF-1',
    });
    const after: StoredSale = { ...tenderOnly, refunds: [refunded] };
    expect(remainingLegAmount(after, 'CARD')).toBe('52.31');
    expect(isRefundable(after)).toBe(true);
    expect(isVoidable(after)).toBe(false);
  });

  it('takes the linked refund only for the card leg', () => {
    expect(refundsLinked(tenderOnly)).toBe(true);
    const giftCardOnly: StoredSale = {
      ...tenderOnly,
      sale: toSaleRecord(
        result({
          poiTransactionId: 'POI-SV-1',
          storedValuePoiTransactionId: 'POI-SV-1',
          storedValueAmountUsed: '62.31',
          cardAmountCharged: '0.00',
          movements: [],
        }),
        CONTEXT,
      ),
    };
    expect(refundsLinked(giftCardOnly)).toBe(false);
    // Split tender: once the card is refunded in full, the stored value rest is not linked.
    const split: StoredSale = {
      ...tenderOnly,
      sale: {
        ...tenderOnly.sale,
        legs: [...tenderOnly.sale.legs, { type: 'STORED_VALUE', poiTransactionId: 'POI-SV-2' }],
      },
      refunds: [
        {
          saleId: tenderOnly.sale.id,
          recordedAt: '2026-10-06T15:00:00Z',
          amount: '62.31',
          leg: 'CARD',
          full: true,
          awardReversed: true,
          reversalProgress: false,
        },
      ],
    };
    expect(refundsLinked(split)).toBe(false);
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
