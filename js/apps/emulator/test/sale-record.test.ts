// The persisted sale: the projection from a SettlementResult, the IndexedDB store, and the
// return and full-refund plans built from a stored sale.
import type { SettlementResult } from '@biltorg/pos-sdk';
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
  biltRewardsLegs,
  isFullyRefunded,
  legOf,
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
    cardPayments: [
      {
        amount: '62.31',
        biltRewardsAmount: '0',
        cardAmount: '62.31',
        poiTransactionId: 'POI-PAY-1',
        poiTransactionTimestamp: '2026-10-06T14:03:10Z',
        approvalCode: 'A1B2C3',
        paymentBrand: 'Visa',
        partialApproval: false,
      },
    ],
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
        cardPayments: [],
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
    const progress = reversalProgress(stored, [{ step: 'CARD', poiTransactionId: 'POI-PAY-1' }]);
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

  it('keeps a sale refundable when a stopped void reversed only its gift-card load', () => {
    const progress = reversalProgress(stored, [
      { step: 'STORED_VALUE_LOAD', poiTransactionId: 'POI-SV-LOAD-1' },
    ]);
    expect(isFullyRefunded({ ...stored, refunds: progress })).toBe(false);
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
    expect(remainingLegAmount(after, legOf(after.sale, 'CARD')!)).toBe('25.00');
    const ui = toStoredSaleUi(after, () => 0);
    expect(ui.items[0]).toMatchObject({ remainingQuantity: 0, refundMinor: 0 });
    expect(ui).toMatchObject({ refunded: true, fullyRefunded: false, fullRefundAvailable: false });
  });

  it('records a full refund as exhausting the sale', () => {
    const record = fullRefundRecord(tenderOnly, {
      success: true,
      reversedAmount: '62.31',
      cardReversals: [],
      biltRewardsReversals: [],
      reversedAmountByTender: [{ tender: 'CARD', poiTransactionId: 'POI-PAY-1', amount: '62.31' }],
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
    expect(defaultReversalDecision('BILT_REWARDS')).toBe('ABORT');
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

describe('a sale with several card payments', () => {
  const split = (): SettlementResult => ({
    ...result({ movements: [] }),
    cardAmountCharged: '62.31',
    cardPayments: [
      {
        amount: '20.00',
        biltRewardsAmount: '0',
        cardAmount: '20.00',
        poiTransactionId: 'POI-A',
        poiTransactionTimestamp: '2026-10-06T14:03:10Z',
        paymentBrand: 'Visa',
        maskedPan: '************4242',
        partialApproval: true,
      },
      {
        amount: '42.31',
        biltRewardsAmount: '0',
        cardAmount: '42.31',
        poiTransactionId: 'POI-B',
        poiTransactionTimestamp: '2026-10-06T14:04:10Z',
        paymentBrand: 'Mastercard',
        partialApproval: false,
      },
    ],
  });
  // no gift-card purchase, so its merchandise is returnable
  const sale = { ...toSaleRecord(split(), CONTEXT), giftCardLoads: [] };
  const stored: StoredSale = { sale, refunds: [], voided: null };

  it('records one card leg per payment', () => {
    const cards = sale.legs.filter((leg) => leg.type === 'CARD');
    expect(cards).toMatchObject([
      {
        poiTransactionId: 'POI-A',
        amount: '20.00',
        brand: 'Visa',
        lastDigits: '4242',
        partial: true,
      },
      { poiTransactionId: 'POI-B', amount: '42.31', brand: 'Mastercard' },
    ]);
    expect(cards[1]).not.toHaveProperty('partial');
    expect(toStoredSaleUi(stored, () => 0).cardPayments).toMatchObject([
      { poiTransactionId: 'POI-A', partial: true, refunded: false },
      { poiTransactionId: 'POI-B', partial: false, refunded: false },
    ]);
  });

  it('voids every card payment through cardPayments', () => {
    const record = originalSaleRecord(stored);
    expect(record.cardPayments).toEqual([
      {
        poiTransactionId: 'POI-A',
        poiTransactionTimestamp: '2026-10-06T14:03:10Z',
        amount: '20.00',
      },
      {
        poiTransactionId: 'POI-B',
        poiTransactionTimestamp: '2026-10-06T14:04:10Z',
        amount: '42.31',
      },
    ]);
    expect(record.cardPoiTransactionId).toBeUndefined();
  });

  it('retries a stopped void from the card payment still standing', () => {
    const progress = reversalProgress(stored, [{ step: 'CARD', poiTransactionId: 'POI-B' }]);
    const retried: StoredSale = { ...stored, refunds: progress };
    expect(originalSaleRecord(retried)).toMatchObject({ cardPoiTransactionId: 'POI-A' });
    expect(originalSaleRecord(retried).cardPayments).toBeUndefined();
    expect(isFullyRefunded(retried)).toBe(false);
    const both = reversalProgress(stored, [
      { step: 'CARD', poiTransactionId: 'POI-B' },
      { step: 'CARD', poiTransactionId: 'POI-A' },
    ]);
    expect(isFullyRefunded({ ...stored, refunds: both })).toBe(true);
  });

  it('shows every card payment refunded after a whole-sale void', () => {
    const voided: StoredSale = {
      ...stored,
      refunds: [fullRefundRecord(stored, { success: true } as never)],
    };
    expect(isFullyRefunded(voided)).toBe(true);
    expect(toStoredSaleUi(voided, () => 0).cardPayments).toMatchObject([
      { poiTransactionId: 'POI-A', refunded: true, remainingMinor: 0 },
      { poiTransactionId: 'POI-B', refunded: true, remainingMinor: 0 },
    ]);
  });

  it('defaults a return to the card that still holds something, and lists the drawn-down one without capacity', () => {
    const drawnDown: StoredSale = {
      ...stored,
      refunds: [
        {
          saleId: 'sale-1',
          recordedAt: '2026-10-07T10:00:00Z',
          amount: '20.00',
          leg: 'CARD',
          legPoiTransactionId: 'POI-A',
          tenderAmount: '20.00',
          full: false,
          awardReversed: false,
          reversalProgress: false,
          items: [],
        },
      ],
    };
    const plan = planReturn(drawnDown, new Set(['SKU-0013']), () => 0);
    expect('pending' in plan && plan.pending.leg.poiTransactionId).toBe('POI-B');
    expect('pending' in plan && plan.cardNumber).toBe(2);
    expect(toStoredSaleUi(drawnDown, () => 0).cardPayments).toMatchObject([
      { poiTransactionId: 'POI-A', refunded: false, remainingMinor: 0 },
      { poiTransactionId: 'POI-B', refunded: false, remainingMinor: 4231 },
    ]);
  });

  it('plans a return against the chosen card, the first standing one by default', () => {
    const skus = new Set(['SKU-0013']);
    const byDefault = planReturn(stored, skus, () => 0);
    const chosen = planReturn(stored, skus, () => 0, 'POI-B');
    expect('pending' in byDefault && byDefault.pending.leg.poiTransactionId).toBe('POI-A');
    expect('pending' in chosen && chosen.pending.leg.poiTransactionId).toBe('POI-B');
    expect('pending' in chosen && chosen.pending.legCapacityMinor).toBe(4231);
    expect(planReturn(stored, skus, () => 0, 'POI-NOPE')).toMatchObject({
      error: expect.any(String),
    });
  });

  it('keeps a card’s refunds apart from the other card’s capacity', () => {
    const skus = new Set(['SKU-0013']);
    const plan = planReturn(stored, skus, () => 0, 'POI-A');
    if (!('pending' in plan)) throw new Error(plan.error);
    const { returns, allocations } = planAllocations([plan.pending], 3731);
    const { records } = settledReturns(returns, {
      ...split(),
      movements: [
        {
          step: 'CARD_REFUND',
          target: { type: 'SALES' },
          amount: '20.00',
          poiTransactionId: 'POI-RF',
        },
      ],
    });
    // card A only collected $20.00; the register pays out the rest
    expect(allocations).toEqual([
      {
        type: 'CARD',
        amount: '20.00',
        originalPoiTransactionId: 'POI-A',
        originalPoiTransactionTimestamp: '2026-10-06T14:03:10Z',
      },
      { type: 'EXTERNAL', amount: '17.31' },
    ]);
    expect(records[0]).toMatchObject({
      leg: 'CARD',
      legPoiTransactionId: 'POI-A',
      tenderAmount: '20.00',
    });
    const after: StoredSale = { ...stored, refunds: [...records] };
    const [cardA, cardB] = sale.legs.filter((leg) => leg.type === 'CARD');
    expect(remainingLegAmount(after, cardA!)).toBe('0.00');
    expect(remainingLegAmount(after, cardB!)).toBe('42.31');
  });
});

describe('a sale Bilt rewards paid part of', () => {
  // The terminal's points leg (40% of the basket) answered as its own payment; a card paid the rest.
  const REWARDS_PAYMENT = {
    amount: '14.92',
    poiTransactionId: 'POI-R',
    poiTransactionTimestamp: '2026-10-06T14:03:05Z',
  };
  const CARD_PAYMENT = {
    amount: '22.39',
    biltRewardsAmount: '0',
    cardAmount: '22.39',
    poiTransactionId: 'POI-C',
    poiTransactionTimestamp: '2026-10-06T14:03:10Z',
    approvalCode: 'AP1',
    paymentBrand: 'Visa',
    maskedPan: '************4242',
    partialApproval: false,
  };
  const lamp = fx.basket([
    fx.lineItem({
      sku: 'SKU-0013',
      description: 'Desk Lamp',
      unitPrice: '34.99',
      taxRate: '0.06625',
    }),
  ]);
  const mixedSale = (): SettlementResult => ({
    ...fx.settlementResult(lamp, '37.31'),
    biltRewardsAmount: '14.92',
    biltRewardsPayments: [REWARDS_PAYMENT],
    cardAmountCharged: '22.39',
    cardPayments: [CARD_PAYMENT],
    poiTransactionId: 'POI-C',
    movements: [],
  });
  const rewardsOnly = (): SettlementResult => ({
    ...fx.settlementResult(lamp, '37.31'),
    biltRewardsAmount: '37.31',
    biltRewardsPayments: [{ ...REWARDS_PAYMENT, amount: '37.31' }],
    cardAmountCharged: '0.00',
    cardPayments: [],
    // a sale with no card payment reports its first rewards payment in the single-payment fields
    poiTransactionId: 'POI-R',
    movements: [],
  });
  const storedOf = (settled: SettlementResult): StoredSale => ({
    sale: toSaleRecord(settled, CONTEXT),
    refunds: [],
    voided: null,
  });

  it('records a rewards payment as its own leg, never a card leg', () => {
    const { sale } = storedOf(mixedSale());
    expect(sale.legs.filter((leg) => leg.type === 'CARD')).toMatchObject([
      { poiTransactionId: 'POI-C', amount: '22.39' },
    ]);
    expect(biltRewardsLegs(sale)).toMatchObject([
      { type: 'BILT_REWARDS', poiTransactionId: 'POI-R', amount: '14.92' },
    ]);
    const cardOnly = sale.legs.find((leg) => leg.type === 'CARD');
    expect(cardOnly).not.toHaveProperty('biltRewardsAmount');
  });

  it('records no card leg for a rewards-only sale although its single-payment fields name one', () => {
    const { sale } = storedOf(rewardsOnly());
    expect(sale.legs.map((leg) => leg.type)).toEqual(['BILT_REWARDS']);
    expect(sale.legs[0]).toMatchObject({ poiTransactionId: 'POI-R', amount: '37.31' });
  });

  it('keeps the rewards part of a mixed card payment on its leg, through the store', async () => {
    const settled: SettlementResult = {
      ...mixedSale(),
      biltRewardsPayments: [],
      cardPayments: [
        { ...CARD_PAYMENT, amount: '37.31', biltRewardsAmount: '9.33', cardAmount: '27.98' },
      ],
    };
    const store = new IndexedDbSaleStore(indexedDB, 'bilt-rewards-roundtrip');
    await store.recordSale(toSaleRecord(settled, CONTEXT));
    const found = await store.findSale('sale-1');
    expect(found?.sale.legs).toMatchObject([
      { type: 'CARD', poiTransactionId: 'POI-C', amount: '37.31', biltRewardsAmount: '9.33' },
    ]);
    expect(toStoredSaleUi(found!, () => 0).cardPayments).toMatchObject([
      { poiTransactionId: 'POI-C', biltRewardsAmount: '9.33' },
    ]);
  });

  it('voids the rewards payments after the card, and sends a mixed card payment as a list', () => {
    const record = originalSaleRecord(storedOf(mixedSale()));
    expect(record.cardPoiTransactionId).toBe('POI-C');
    expect(record.biltRewardsPayments).toEqual([
      {
        poiTransactionId: 'POI-R',
        poiTransactionTimestamp: '2026-10-06T14:03:05Z',
        amount: '14.92',
      },
    ]);
    const rewardsOnlyRecord = originalSaleRecord(storedOf(rewardsOnly()));
    expect(rewardsOnlyRecord.biltRewardsPayments).toHaveLength(1);
    expect(rewardsOnlyRecord).not.toHaveProperty('cardPoiTransactionId');
    expect(rewardsOnlyRecord).not.toHaveProperty('cardPayments');

    const mixedCard: SettlementResult = {
      ...mixedSale(),
      biltRewardsPayments: [],
      cardPayments: [
        { ...CARD_PAYMENT, amount: '37.31', biltRewardsAmount: '9.33', cardAmount: '27.98' },
      ],
    };
    const mixed = originalSaleRecord(storedOf(mixedCard));
    expect(mixed.cardPayments).toMatchObject([
      { poiTransactionId: 'POI-C', biltRewardsAmount: '9.33' },
    ]);
    expect(mixed).not.toHaveProperty('cardPoiTransactionId');
    expect(mixed).not.toHaveProperty('biltRewardsPayments');
  });

  it('omits a rewards payment a stopped void already reversed', () => {
    const stored = storedOf(mixedSale());
    const progress = reversalProgress(stored, [
      { step: 'CARD', poiTransactionId: 'POI-C', amount: '22.39' },
      { step: 'BILT_REWARDS', poiTransactionId: 'POI-R', amount: '14.92' },
    ] as never);
    expect(progress).toMatchObject([
      { leg: 'CARD', legPoiTransactionId: 'POI-C' },
      { leg: 'BILT_REWARDS', legPoiTransactionId: 'POI-R' },
    ]);
    const retried = originalSaleRecord({ ...stored, refunds: [progress[1]!] });
    expect(retried).not.toHaveProperty('biltRewardsPayments');
    expect(retried.cardPoiTransactionId).toBe('POI-C');
    expect(defaultReversalDecision('BILT_REWARDS')).toBe('ABORT');
  });

  it('lists the rewards payment apart from the cards and refunds it once reversed', () => {
    const stored = storedOf(mixedSale());
    const ui = toStoredSaleUi(stored, () => 0);
    expect(ui.cardPayments).toHaveLength(1);
    expect(ui.biltRewardsPayments).toEqual([
      { poiTransactionId: 'POI-R', amount: '14.92', refunded: false, remainingMinor: 1492 },
    ]);
    const reversed = reversalProgress(stored, [
      { step: 'BILT_REWARDS', poiTransactionId: 'POI-R', amount: '14.92' },
    ] as never);
    const after = toStoredSaleUi({ ...stored, refunds: reversed }, () => 0);
    expect(after.biltRewardsPayments[0]).toMatchObject({ refunded: true, remainingMinor: 0 });
    expect(after.cardPayments[0]).toMatchObject({ refunded: false });
    // the sale counts as refunded only once every money leg is
    expect(isFullyRefunded({ ...stored, refunds: reversed })).toBe(false);
  });

  it('returns to the card by default and to the rewards payment when it is picked', () => {
    const stored = storedOf(mixedSale());
    const skus = new Set(['SKU-0013']);
    const byDefault = planReturn(stored, skus, () => 0);
    expect('pending' in byDefault && byDefault.pending.leg.type).toBe('CARD');
    const picked = planReturn(stored, skus, () => 0, 'POI-R');
    if (!('pending' in picked)) throw new Error(picked.error);
    expect(picked.pending.leg.type).toBe('BILT_REWARDS');
    expect(picked.cardNumber).toBe(0);
    expect(picked.pending.legCapacityMinor).toBe(1492);
    const { returns, allocations } = planAllocations([picked.pending], 3731);
    // the rewards payment only collected $14.92; the register pays out the rest
    expect(allocations).toEqual([
      {
        type: 'BILT_REWARDS',
        amount: '14.92',
        originalPoiTransactionId: 'POI-R',
        originalPoiTransactionTimestamp: '2026-10-06T14:03:05Z',
      },
      { type: 'EXTERNAL', amount: '22.39' },
    ]);
    const { records, parts } = settledReturns(returns, {
      ...mixedSale(),
      movements: [
        {
          step: 'CARD_REFUND',
          target: { type: 'SALES' },
          amount: '14.92',
          biltRewardsAmount: '14.92',
          poiTransactionId: 'POI-RF',
        },
      ],
    });
    expect(parts[0]).toContain('$14.92 to Bilt Rewards');
    expect(parts[0]).toContain('Bilt Rewards collected less than the shelf value');
    expect(records[0]).toMatchObject({
      leg: 'BILT_REWARDS',
      legPoiTransactionId: 'POI-R',
      tenderAmount: '14.92',
      poiTransactionId: 'POI-RF',
    });
  });

  it('defaults a rewards-only sale to its rewards payment, not to the register', () => {
    const plan = planReturn(storedOf(rewardsOnly()), new Set(['SKU-0013']), () => 0);
    expect('pending' in plan && plan.pending.leg.type).toBe('BILT_REWARDS');
  });

  it('refuses a return to a rewards payment that was already refunded', () => {
    const stored = storedOf(mixedSale());
    const reversed = reversalProgress(stored, [
      { step: 'BILT_REWARDS', poiTransactionId: 'POI-R', amount: '14.92' },
    ] as never);
    expect(
      planReturn({ ...stored, refunds: reversed }, new Set(['SKU-0013']), () => 0, 'POI-R'),
    ).toMatchObject({ error: expect.stringContaining('already refunded') });
  });
});
