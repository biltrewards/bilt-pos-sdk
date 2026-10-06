import type { Money } from '@bilt/pos-sdk';

/**
 * The emulator's tax policy, a port of the desktop emulator's `NjSalesTax`: New Jersey sales
 * tax, 6.625%, with the two big NJ exemptions that map onto the catalog's categories: unprepared
 * food (Grocery) and clothing (Apparel) are not taxed. Lines carry the rate as `taxRate`, the
 * host computes `taxAmount = subtotal × rate`, and `recomputeTotal` re-taxes the adjusted
 * amount after rebates.
 */
export const NJ_SALES_TAX_RATE: Money = '0.06625';

export const TAX_EXEMPT_CATEGORIES: readonly string[] = ['Grocery', 'Apparel'];

/** The `BasketItem.taxRate` for a category, or `undefined` when the category is exempt. */
export function taxRateFor(category: string | undefined): Money | undefined {
  return category !== undefined && TAX_EXEMPT_CATEGORIES.includes(category)
    ? undefined
    : NJ_SALES_TAX_RATE;
}
