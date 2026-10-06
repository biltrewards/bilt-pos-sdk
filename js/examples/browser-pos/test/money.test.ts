import { describe, expect, it } from 'vitest';
import * as fx from '../../../packages/sdk/test/fixtures';
import { offerAmount, offerTarget, recomputeTotal } from '../src/money';
import { loadSettings, saveSettings, DEFAULT_SETTINGS } from '../src/settings';

describe('recomputeTotal', () => {
  it('taxes the rebated line totals instead of the undiscounted price', () => {
    const candle = fx.lineItem(
      { sku: 'C', description: 'Candle', quantity: 2, unitPrice: '24.99', taxRate: '0.08875' },
      '1',
    );
    const juice = fx.lineItem({ sku: 'J', description: 'Juice', unitPrice: '4.49' }, '2');
    const basket = fx.basket([candle, juice]);
    // The host's suggestion keeps tax on $49.98; the register taxes the $44.98 the shopper pays.
    const rebated = {
      ...basket,
      items: [{ ...candle, rebateAmount: '5.00', adjustedTotal: '44.98' }, juice],
    };
    expect(recomputeTotal(rebated)).toBe('53.46'); // 44.98 + 4.49 + round(44.98 × 0.08875)
  });
});

describe('offers', () => {
  it('lands a basket offer on the most valuable sale line, capped at that line', () => {
    const cheap = fx.lineItem({ sku: 'A', description: 'A', unitPrice: '1.00' }, '1');
    const dear = fx.lineItem({ sku: 'B', description: 'B', unitPrice: '9.00' }, '2');
    const basket = fx.basket([cheap, dear]);
    const target = offerTarget(
      { id: 'o', scope: 'BASKET', amount: '20.00', creativeId: 'c' },
      basket,
    );
    expect(target?.itemId).toBe('2');
    expect(offerAmount({ id: 'o', scope: 'BASKET', amount: '20.00', creativeId: 'c' }, dear)).toBe(
      '9.00',
    );
    expect(
      offerAmount(
        { id: 'o', scope: 'LINE_ITEM', sku: 'B', percentage: '10', creativeId: 'c' },
        dear,
      ),
    ).toBe('0.90');
  });
});

describe('settings', () => {
  it('round-trip through storage and fall back field by field', () => {
    saveSettings({ ...DEFAULT_SETTINGS, poiId: 'LANE-9', currency: 'eur' });
    expect(loadSettings()).toMatchObject({ poiId: 'LANE-9', currency: 'EUR' });
    localStorage.setItem('browser-pos.settings', '{"mode":"sideways","saleId":""}');
    expect(loadSettings()).toEqual(DEFAULT_SETTINGS);
    localStorage.setItem('browser-pos.settings', 'not json');
    expect(loadSettings()).toEqual(DEFAULT_SETTINGS);
  });
});
