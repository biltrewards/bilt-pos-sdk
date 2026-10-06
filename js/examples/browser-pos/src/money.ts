import type { Basket, BasketLineItem, Money, Offer } from '@bilt/pos-sdk';

/** `Money` is a decimal string on the wire; the register does its arithmetic in integer cents. */
export function cents(value: Money): number {
  return Math.round(Number(value) * 100);
}

export function money(value: number): Money {
  return (value / 100).toFixed(2);
}

export function formatMoney(value: Money, currency: string): string {
  try {
    return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(Number(value));
  } catch {
    return `${value} ${currency}`;
  }
}

function sign(line: BasketLineItem): number {
  return line.type === 'SALE' ? 1 : -1;
}

/**
 * The register's own total for a basket: each line's adjusted total (price after register
 * discounts and loyalty rebates) plus tax on that adjusted amount. This is the answer to a
 * `TOTAL_REQUIRED` step after rebates: the host's suggested total is the previous total minus
 * the rebates, which keeps tax on the undiscounted price, whereas the register taxes what the
 * shopper actually pays. Lines without a rate keep the tax amount they were rung with.
 */
export function recomputeTotal(basket: Basket): Money {
  let goods = 0;
  let tax = 0;
  for (const line of basket.items) {
    const adjusted = cents(line.adjustedTotal);
    goods += sign(line) * adjusted;
    tax +=
      sign(line) *
      (line.taxRate === undefined
        ? cents(line.taxAmount)
        : Math.round(adjusted * Number(line.taxRate)));
  }
  return money(Math.max(0, goods + tax));
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
