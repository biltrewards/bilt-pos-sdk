// Small, type-complete protocol values for the test doubles.
import type {
  Basket,
  BasketItem,
  BasketLineItem,
  Member,
  Offer,
  RebateRedemptionResult,
  Rendering,
  SessionContext,
  SettlementResult,
} from '@bilt/pos-protocol';

let lineCounter = 0;

function cents(money: string): number {
  return Math.round(Number(money) * 100);
}

function money(centsValue: number): string {
  return (centsValue / 100).toFixed(2);
}

export function lineItem(item: BasketItem, itemId = String(++lineCounter)): BasketLineItem {
  const quantity = item.quantity ?? 1;
  const original = cents(item.unitPrice) * quantity;
  const discount = (item.discounts ?? []).reduce((sum, d) => sum + cents(d.amount), 0);
  const subtotal = original - discount;
  const taxAmount = item.taxAmount
    ? cents(item.taxAmount)
    : item.taxRate
      ? Math.round(subtotal * Number(item.taxRate))
      : 0;
  const line: BasketLineItem = {
    itemId,
    sku: item.sku,
    description: item.description,
    quantity,
    unitPrice: item.unitPrice,
    discounts: item.discounts ?? [],
    discountTotal: money(discount),
    subtotal: money(subtotal),
    type: item.type ?? 'SALE',
    originalTotal: money(original),
    rebateAmount: '0.00',
    adjustedTotal: money(subtotal),
    taxAmount: money(taxAmount),
    metadata: item.metadata ?? {},
  };
  if (item.reference !== undefined) line.reference = item.reference;
  if (item.category !== undefined) line.category = item.category;
  if (item.taxRate !== undefined) line.taxRate = item.taxRate;
  return line;
}

export function basket(items: readonly BasketLineItem[], cartId = 'cart_1'): Basket {
  const sum = (pick: (line: BasketLineItem) => string) =>
    items.reduce((total, line) => total + cents(pick(line)), 0);
  const subtotal = sum((l) => l.subtotal);
  const taxTotal = sum((l) => l.taxAmount);
  return {
    cartId,
    saleTransactionId: { transactionId: `txn_${cartId}`, timestamp: '2026-10-06T14:03:11.412Z' },
    items: [...items],
    taxTotal: money(taxTotal),
    originalTotal: money(sum((l) => l.originalTotal)),
    discountTotal: money(sum((l) => l.discountTotal)),
    subtotal: money(subtotal),
    grandTotal: money(subtotal + taxTotal),
    rebateTotal: '0.00',
    pointDiscountTotal: '0.00',
    storedValueTotal: '0.00',
    cardPaymentTotal: '0.00',
    externalPaymentTotal: '0.00',
    updatedAt: new Date().toISOString(),
  };
}

export function context(overrides: Partial<SessionContext> = {}): SessionContext {
  return {
    phase: 'SCANNING',
    attributes: {},
    saleId: 'LANE-3',
    currency: 'USD',
    ...overrides,
  };
}

export function resolvedMember(memberId: string): Member {
  return { resolved: true, memberId, status: 'FOUND', rewards: [], pointBalance: 0 };
}

export function rebates(current: Basket, amount: string): RebateRedemptionResult {
  const previous = cents(current.grandTotal);
  return {
    rebates: [{ amount, label: 'Gold Member', itemId: current.items[0]?.itemId ?? '1' }],
    totalRebateAmount: amount,
    previousTotal: money(previous),
    suggestedTotal: money(previous - cents(amount)),
    updatedBasket: { ...current, rebateTotal: amount },
  };
}

export function settlementResult(finalBasket: Basket, charged: string): SettlementResult {
  return {
    success: true,
    finalBasket,
    authorizedAmount: charged,
    storedValueAmountUsed: '0.00',
    storedValueLoadedAmount: '0.00',
    cardAmountCharged: charged,
    externalPaymentAmount: '0.00',
    approvalCode: 'A1B2C3',
    paymentBrand: 'Visa',
    redeemedRebates: [],
    totalRebateAmount: finalBasket.rebateTotal,
    pointsRedeemed: 0,
    pointsMonetaryValue: '0.00',
    earnedRewards: [],
    totalPointsEarned: 0,
    pointsBalance: 0,
    promotionMessages: [],
    poiTransactionId: 'POI-1',
    cardRefundedAmount: '0.00',
    storedValueRefundedAmount: '0.00',
    externalRefundedAmount: '0.00',
    loyaltyRefundedAmount: '0.00',
    movements: [{ step: 'CARD_CHARGE', target: { type: 'SALES' }, amount: charged }],
    warnings: [],
  };
}

export function offer(creativeId: string): Offer {
  return { id: 'ofr_1', scope: 'BASKET', amount: '1.00', creativeId };
}

export function rendering(placement: string): Rendering {
  return {
    creativeId: 'crt_1',
    placement,
    media: { type: 'IMAGE', url: 'https://cdn.example/crt_1.png' },
    headline: 'Save $1 today',
    cta: { label: 'Apply', action: 'APPLY_OFFER', token: 'tok_1' },
    ttl: 'PT30S',
    tracking: {},
  };
}
