import type { BasketItem, Money } from '@bilt/pos-sdk';
import { money } from './money';
import { NJ_SALES_TAX_RATE, taxRateFor } from './tax';

/** A sellable product; `priceMinor` is the unit price in cents, as in the desktop emulator. */
export interface Product {
  readonly sku: string;
  readonly name: string;
  readonly priceMinor: number;
  readonly category: string;
}

/**
 * The desktop emulator's `MockProductProvider`: a fixed catalog spanning sub-dollar items to
 * $500+, so every price band of the payment flow (small-ticket, signature thresholds, large
 * amounts) is one tap away.
 */
export const CATALOG: readonly Product[] = [
  { sku: 'SKU-0001', name: 'Chewing Gum', priceMinor: 75, category: 'Grocery' },
  { sku: 'SKU-0002', name: 'Banana', priceMinor: 35, category: 'Grocery' },
  { sku: 'SKU-0003', name: 'Chocolate Bar', priceMinor: 185, category: 'Grocery' },
  { sku: 'SKU-0004', name: 'Greeting Card', priceMinor: 250, category: 'Misc' },
  { sku: 'SKU-0005', name: 'Bottled Water', priceMinor: 129, category: 'Grocery' },
  { sku: 'SKU-0006', name: 'Coffee', priceMinor: 375, category: 'Grocery' },
  { sku: 'SKU-0007', name: 'Notebook', priceMinor: 499, category: 'Office' },
  { sku: 'SKU-0008', name: 'Sandwich', priceMinor: 849, category: 'Grocery' },
  { sku: 'SKU-0009', name: 'Socks 3-Pack', priceMinor: 999, category: 'Apparel' },
  { sku: 'SKU-0010', name: 'Umbrella', priceMinor: 1499, category: 'Misc' },
  { sku: 'SKU-0011', name: 'Phone Case', priceMinor: 1999, category: 'Electronics' },
  { sku: 'SKU-0012', name: 'T-Shirt', priceMinor: 2499, category: 'Apparel' },
  { sku: 'SKU-0013', name: 'Desk Lamp', priceMinor: 3499, category: 'Home' },
  { sku: 'SKU-0014', name: 'Backpack', priceMinor: 4999, category: 'Apparel' },
  { sku: 'SKU-0015', name: 'Blender', priceMinor: 6499, category: 'Home' },
  { sku: 'SKU-0016', name: 'Running Shoes', priceMinor: 7999, category: 'Apparel' },
  { sku: 'SKU-0017', name: 'Headphones', priceMinor: 12999, category: 'Electronics' },
  { sku: 'SKU-0018', name: 'E-Reader', priceMinor: 13999, category: 'Electronics' },
  { sku: 'SKU-0019', name: 'Smartwatch', priceMinor: 19999, category: 'Electronics' },
  { sku: 'SKU-0020', name: 'Robot Vacuum', priceMinor: 27999, category: 'Home' },
  { sku: 'SKU-0021', name: 'Tablet', priceMinor: 32999, category: 'Electronics' },
  { sku: 'SKU-0022', name: 'Espresso Machine', priceMinor: 44999, category: 'Home' },
  { sku: 'SKU-0023', name: 'Game Console', priceMinor: 49999, category: 'Electronics' },
  { sku: 'SKU-0024', name: '4K Television', priceMinor: 54999, category: 'Electronics' },
];

export const CATEGORIES: readonly string[] = [...new Set(CATALOG.map((p) => p.category))];

export function findProduct(sku: string): Product | undefined {
  return CATALOG.find((product) => product.sku === sku);
}

/** The `BasketItem` the SDK takes for a product, taxed by the emulator's policy. */
export function toBasketItem(product: Product, quantity = 1): BasketItem {
  const taxRate = taxRateFor(product.category);
  return {
    sku: product.sku,
    description: product.name,
    quantity,
    unitPrice: money(product.priceMinor),
    category: product.category,
    ...(taxRate === undefined ? {} : { taxRate }),
  };
}

/**
 * A keyed-in line the catalog does not carry: the desktop emulator's `CustomItem`. The SKU is
 * generated per basket, so a line removed and re-rung never collides with a live one; the
 * category is its own, so it stays outside the tax exemptions.
 */
export const CUSTOM_SKU_PREFIX = 'CUSTOM-';
export const CUSTOM_CATEGORY = 'Custom';

export function isCustomSku(sku: string): boolean {
  return sku.startsWith(CUSTOM_SKU_PREFIX);
}

export function nextCustomSku(cartId: string, skus: Iterable<string>): string {
  const prefix = `${CUSTOM_SKU_PREFIX}${cartId.replace(/[^A-Za-z0-9]/g, '').slice(0, 8)}-`;
  let highest = 0;
  for (const sku of skus) {
    if (!sku.startsWith(prefix)) continue;
    const n = Number(sku.slice(prefix.length));
    if (Number.isInteger(n) && n > highest) highest = n;
  }
  return `${prefix}${highest + 1}`;
}

export function customItem(
  sku: string,
  unitPrice: Money,
  description: string,
  taxed: boolean,
): BasketItem {
  return {
    sku,
    description: description.trim() || 'Custom amount',
    quantity: 1,
    unitPrice,
    category: CUSTOM_CATEGORY,
    ...(taxed ? { taxRate: NJ_SALES_TAX_RATE } : {}),
  };
}
