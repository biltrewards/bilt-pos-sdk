// The in-memory double's basket keeps the register's tax choice across line edits, as the host
// does, so tests built on it see the totals a real session would.
import { describe, expect, it } from 'vitest';
import { MockShopperSession } from './mock-pos';

function session() {
  return new MockShopperSession({ saleId: 'LANE-1', currency: 'USD' });
}

describe('the double basket', () => {
  it('keeps a fixed tax amount through quantity and discount edits', async () => {
    const { basket } = session();
    await basket.addItem({ sku: 'SKU-1', description: 'Lamp', unitPrice: '10.00' }, 'l1');
    await basket.setTaxAmount('l1', '0.50');
    await basket.updateItemQuantity('l1', 3);
    await basket.setDiscounts('l1', [{ label: 'Promo', amount: '5.00' }]);
    expect(basket.current.items[0]).toMatchObject({ subtotal: '25.00', taxAmount: '0.50' });
    expect(basket.current.taxTotal).toBe('0.50');
  });

  it('recomputes a rate-based tax on every edit', async () => {
    const { basket } = session();
    await basket.addItem(
      { sku: 'SKU-1', description: 'Lamp', unitPrice: '10.00', taxRate: '0.1' },
      'l1',
    );
    await basket.updateItemQuantity('l1', 3);
    expect(basket.current.items[0]?.taxAmount).toBe('3.00');
    await basket.setDiscounts('l1', [{ label: 'Promo', amount: '5.00' }]);
    expect(basket.current.items[0]?.taxAmount).toBe('2.50');
  });

  it('switches between a rate and a fixed amount, each clearing the other', async () => {
    const { basket } = session();
    await basket.addItem(
      { sku: 'SKU-1', description: 'Lamp', unitPrice: '10.00', taxRate: '0.1' },
      'l1',
    );
    await basket.setTaxAmount('l1', '0.25');
    await basket.updateItemQuantity('l1', 2);
    expect(basket.current.items[0]).toMatchObject({ taxAmount: '0.25' });
    expect(basket.current.items[0]?.taxRate).toBeUndefined();

    await basket.setTaxRate('l1', '0.1');
    await basket.updateItemQuantity('l1', 4);
    expect(basket.current.items[0]).toMatchObject({ taxRate: '0.1', taxAmount: '4.00' });
  });
});
