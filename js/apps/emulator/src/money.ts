import type { Basket, BasketLineItem, Money, Offer } from '@bilt/pos-sdk';

/** `Money` is a decimal string on the wire; the register does its arithmetic in integer cents. */
export function cents(value: Money | undefined): number {
  return value === undefined ? 0 : Math.round(Number(value) * 100);
}

export function money(value: number): Money {
  return (value / 100).toFixed(2);
}

export function formatMoney(value: Money | undefined, currency: string): string {
  if (value === undefined) return '—';
  try {
    return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(Number(value));
  } catch {
    return `${value} ${currency}`;
  }
}

/**
 * A user-typed amount as `Money`, or `undefined` when it is not a positive decimal with at most
 * two places. `allowZero` admits `0` (an activation without funds).
 */
export function parseMoney(input: string, allowZero = false): Money | undefined {
  const trimmed = input.trim().replace(/^\$/, '');
  if (!/^\d+(\.\d{1,2})?$/.test(trimmed)) return undefined;
  const value = cents(trimmed);
  if (value < 0 || (!allowZero && value === 0)) return undefined;
  return money(value);
}

/**
 * The register's own total for a basket: each line's adjusted total (price after register
 * discounts and loyalty rebates) plus tax on that adjusted amount. This is the answer to a
 * `TOTAL_REQUIRED` step after rebates: the host's suggested total is the previous total minus
 * the rebates, which keeps tax on the undiscounted price, whereas the register taxes what the
 * shopper actually pays. Lines without a rate keep the tax amount they were rung with. Return
 * and credit lines arrive with negative totals from the host, so no sign is applied here. A
 * rebate the terminal granted at cart level, not on any line, comes off the end.
 */
export function recomputeTotal(basket: Basket): Money {
  let goods = 0;
  let tax = 0;
  let lineRebates = 0;
  for (const line of basket.items) {
    const adjusted = cents(line.adjustedTotal);
    goods += adjusted;
    lineRebates += cents(line.rebateAmount);
    tax +=
      line.taxRate === undefined
        ? cents(line.taxAmount)
        : Math.round(adjusted * Number(line.taxRate));
  }
  const cartRebate = Math.max(0, cents(basket.rebateTotal) - lineRebates);
  return money(Math.max(0, goods + tax - cartRebate));
}

/** The discount an offer is worth on a line: its fixed amount, or its percentage of the line's subtotal, never more than the line. */
export function offerAmount(offer: Offer, line: BasketLineItem): Money {
  const subtotal = cents(line.subtotal);
  const amount =
    offer.amount !== undefined
      ? cents(offer.amount)
      : offer.percentage !== undefined
        ? Math.round((subtotal * Number(offer.percentage)) / 100)
        : 0;
  return money(Math.min(Math.max(amount, 0), subtotal));
}

/** The line a basket-scoped offer lands on: the most valuable one. A line-scoped offer names its SKU. */
export function offerTarget(offer: Offer, basket: Basket): BasketLineItem | undefined {
  const sale = basket.items.filter((line) => line.type === 'SALE');
  if (offer.scope === 'LINE_ITEM') return sale.find((line) => line.sku === offer.sku);
  return [...sale].sort((a, b) => cents(b.subtotal) - cents(a.subtotal))[0];
}
