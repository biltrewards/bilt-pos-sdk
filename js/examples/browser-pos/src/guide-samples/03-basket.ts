// Samples for "Build the basket".
import type { Basket, BasketItem, ShopperSession } from '@bilt/pos-sdk';

declare function render(basket: Basket): void;
declare function log(message: string): void;

// Incremental, as the cashier scans: a repeated SKU bumps its quantity, 0 removes the line.
export function scan(session: ShopperSession, item: BasketItem): Promise<Basket> {
  return session.basket.addItem(item);
}

export function setQuantity(session: ShopperSession, sku: string, qty: number): Promise<Basket> {
  return session.basket.updateItemQuantityBySku(sku, qty);
}

// Batch: several lines in one atomic change, one basket.changed, one display refresh.
export function exchange(session: ShopperSession): Promise<Basket> {
  return session.basket.mutate((m) =>
    m
      .removeItemBySku('KRK-CNDL-LRG-VAN')
      .addItem({ sku: 'KRK-FRAME-5X7-BLK', description: '5x7 Black Frame', unitPrice: '14.99' })
      .setTaxTotal('1.33'),
  );
}

// Replace: a POS that owns its cart pushes the whole thing after every change.
export function syncCart(session: ShopperSession, cart: readonly BasketItem[]): Promise<Basket> {
  return session.basket.replace(cart);
}

// Discounts and tax live on the lines.
export async function applyCoupon(session: ShopperSession, itemId: string): Promise<Basket> {
  await session.basket.setDiscounts(itemId, [
    { reference: 'CPN-10', label: '$1 off', amount: '1.00' },
  ]);
  return session.basket.setTaxRate(itemId, '0.08875');
}

// Between two settlements in one session.
export function nextBasket(session: ShopperSession): Promise<Basket> {
  return session.basket.clear();
}

export function watchBasket(session: ShopperSession): () => void {
  return session.on('basket.changed', (change) => {
    render(change.current); // session.basket.current already equals change.current here
    for (const line of change.added) log(`${change.source}: added ${line.sku}`);
  });
}
