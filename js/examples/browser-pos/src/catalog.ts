import type { BasketItem, Money } from '@bilt/pos-sdk';

/** One product the register can scan. Prices and tax rates are the register's own. */
export interface CatalogEntry {
  readonly sku: string;
  readonly description: string;
  readonly unitPrice: Money;
  readonly category: string;

  /** The line's tax rate; the host computes `taxAmount = subtotal × rate` from it. */
  readonly taxRate: Money;
}

/** A handful of items from the Java guides' examples plus a laundromat's shelf. */
export const CATALOG: readonly CatalogEntry[] = [
  {
    sku: 'KRK-CNDL-LRG-VAN',
    description: 'Large Vanilla Candle',
    unitPrice: '24.99',
    category: 'home',
    taxRate: '0.08875',
  },
  {
    sku: 'KRK-FRAME-5X7-BLK',
    description: '5x7 Black Frame',
    unitPrice: '14.99',
    category: 'home',
    taxRate: '0.08875',
  },
  {
    sku: 'LDY-WASH-20',
    description: 'Wash & Fold, 20 lb',
    unitPrice: '32.00',
    category: 'laundry',
    taxRate: '0.08875',
  },
  {
    sku: 'LDY-DTRG-POD',
    description: 'Detergent Pods',
    unitPrice: '11.49',
    category: 'laundry',
    taxRate: '0.08875',
  },
  {
    sku: 'GRC-OJ-1L',
    description: 'Orange Juice 1L',
    unitPrice: '4.49',
    category: 'grocery',
    taxRate: '0',
  },
  {
    sku: 'GRC-BRD-SRD',
    description: 'Sourdough Loaf',
    unitPrice: '6.25',
    category: 'grocery',
    taxRate: '0',
  },
];

export function findEntry(sku: string): CatalogEntry | undefined {
  return CATALOG.find((entry) => entry.sku === sku);
}

/** The `BasketItem` the SDK takes for a catalog entry. */
export function toBasketItem(entry: CatalogEntry, quantity = 1): BasketItem {
  return {
    sku: entry.sku,
    description: entry.description,
    quantity,
    unitPrice: entry.unitPrice,
    category: entry.category,
    taxRate: entry.taxRate,
  };
}
