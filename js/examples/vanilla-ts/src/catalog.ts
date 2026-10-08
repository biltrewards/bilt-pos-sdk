import type { Basket, BasketItem, Money } from '@bilt/pos-sdk';

/** What the register can scan. Prices and tax rates are the register's own. */
export const CATALOG: readonly BasketItem[] = [
  {
    sku: 'KRK-CNDL-LRG-VAN',
    description: 'Large Vanilla Candle',
    unitPrice: '24.99',
    taxRate: '0.08875',
  },
  {
    sku: 'KRK-FRAME-5X7-BLK',
    description: '5x7 Black Frame',
    unitPrice: '14.99',
    taxRate: '0.08875',
  },
  { sku: 'GRC-OJ-1L', description: 'Orange Juice 1L', unitPrice: '4.49', taxRate: '0' },
];

function cents(value: Money): number {
  return Math.round(Number(value) * 100);
}

/**
 * The register's answer to `TOTAL_REQUIRED` after rebates: each line's adjusted total (after
 * rebates) plus tax on that adjusted amount, rather than the host's suggestion, which keeps tax
 * on the undiscounted price.
 */
export function recomputeTotal(basket: Basket): Money {
  let total = 0;
  for (const line of basket.items) {
    const sign = line.type === 'SALE' ? 1 : -1;
    const adjusted = cents(line.adjustedTotal);
    const tax =
      line.taxRate === undefined
        ? cents(line.taxAmount)
        : Math.round(adjusted * Number(line.taxRate));
    total += sign * (adjusted + tax);
  }
  return (Math.max(0, total) / 100).toFixed(2);
}
