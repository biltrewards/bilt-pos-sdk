// The register's own arithmetic and bookkeeping: money parsing, the NJ tax policy, the catalog
// and keyed-in lines, and the log store.
import { describe, expect, it } from 'vitest';
import * as fx from '../../../packages/sdk/test/fixtures';
import { CATALOG, customItem, nextCustomSku, toBasketItem } from '../src/catalog';
import { LogStore, summarize, track } from '../src/log';
import { offerAmount, offerTarget, parseMoney, recomputeTotal } from '../src/money';
import { NJ_SALES_TAX_RATE, taxRateFor } from '../src/tax';
import { bridgeOptions, loadSettings, saveSettings } from '../src/settings';

describe('money', () => {
  it('parses positive amounts with at most two decimals', () => {
    expect(parseMoney('12.5')).toBe('12.50');
    expect(parseMoney(' $3 ')).toBe('3.00');
    expect(parseMoney('0')).toBeUndefined();
    expect(parseMoney('0', true)).toBe('0.00');
    expect(parseMoney('1.234')).toBeUndefined();
    expect(parseMoney('-1')).toBeUndefined();
    expect(parseMoney('abc')).toBeUndefined();
  });

  it('re-taxes the adjusted amount after rebates', () => {
    const line = fx.lineItem({
      sku: 'SKU-0013',
      description: 'Desk Lamp',
      unitPrice: '34.99',
      taxRate: NJ_SALES_TAX_RATE,
    });
    const basket = fx.basket([{ ...line, rebateAmount: '10.00', adjustedTotal: '24.99' }]);
    // 24.99 + round(24.99 × 0.06625) = 24.99 + 1.66
    expect(recomputeTotal(basket)).toBe('26.65');
  });

  it('keeps a fixed tax amount on lines without a rate and subtracts credit lines', () => {
    const sale = fx.lineItem({ sku: 'A', description: 'A', unitPrice: '10.00', taxAmount: '0.50' });
    const credit = fx.lineItem({ sku: 'C', description: 'C', unitPrice: '2.00', type: 'CREDIT' });
    const basket = fx.basket([
      sale,
      { ...credit, subtotal: '-2.00', adjustedTotal: '-2.00', taxAmount: '0.00' },
    ]);
    expect(recomputeTotal(basket)).toBe('8.50');
  });

  it('values an offer against its target line', () => {
    const cheap = fx.lineItem({ sku: 'SKU-0001', description: 'Gum', unitPrice: '0.75' });
    const dear = fx.lineItem({ sku: 'SKU-0024', description: 'TV', unitPrice: '549.99' });
    const basket = fx.basket([cheap, dear]);
    expect(offerTarget({ id: 'o', scope: 'BASKET', creativeId: 'c' }, basket)?.sku).toBe(
      'SKU-0024',
    );
    expect(offerAmount({ id: 'o', scope: 'BASKET', percentage: '10', creativeId: 'c' }, dear)).toBe(
      '55.00',
    );
    expect(
      offerAmount(
        { id: 'o', scope: 'LINE_ITEM', sku: 'SKU-0001', amount: '5.00', creativeId: 'c' },
        cheap,
      ),
    ).toBe('0.75');
  });
});

describe('tax and catalog', () => {
  it('exempts grocery and apparel, as the desktop emulator does', () => {
    expect(taxRateFor('Grocery')).toBeUndefined();
    expect(taxRateFor('Apparel')).toBeUndefined();
    expect(taxRateFor('Electronics')).toBe('0.06625');
    expect(taxRateFor(undefined)).toBe('0.06625');
  });

  it('carries the desktop catalog with prices in cents', () => {
    expect(CATALOG).toHaveLength(24);
    const banana = toBasketItem(CATALOG[1]!);
    expect(banana).toEqual({
      sku: 'SKU-0002',
      description: 'Banana',
      quantity: 1,
      unitPrice: '0.35',
      category: 'Grocery',
    });
    expect(toBasketItem(CATALOG[23]!, 2)).toMatchObject({
      unitPrice: '549.99',
      quantity: 2,
      taxRate: '0.06625',
    });
  });

  it('numbers keyed-in lines per cart past the highest in use', () => {
    expect(nextCustomSku('cart_12345678abc', [])).toBe('CUSTOM-cart1234-1');
    expect(nextCustomSku('cart_12345678abc', ['CUSTOM-cart1234-3', 'SKU-0001'])).toBe(
      'CUSTOM-cart1234-4',
    );
    expect(customItem('CUSTOM-x-1', '12.50', '', true)).toMatchObject({
      description: 'Custom amount',
      category: 'Custom',
      taxRate: '0.06625',
    });
    expect(customItem('CUSTOM-x-1', '12.50', 'Gift wrap', false).taxRate).toBeUndefined();
  });
});

describe('settings', () => {
  it('round-trips through localStorage with defaults for malformed fields', () => {
    saveSettings({ ...loadSettings(), mode: 'local', bridgePort: 48334, currency: 'eur' });
    expect(loadSettings()).toMatchObject({ mode: 'local', bridgePort: 48334, currency: 'EUR' });
    localStorage.setItem('bilt-pos-emulator.settings', '{"bridgePort":"nope","mode":"weird"}');
    expect(loadSettings()).toMatchObject({ mode: 'terminal', bridgePort: 48333 });
  });

  it('builds the probe options for each route', () => {
    expect(bridgeOptions({ bridge: 'direct', bridgePort: 48340 })).toEqual({ port: 48340 });
    // jsdom serves the page from http://localhost:3000
    expect(bridgeOptions({ bridge: 'proxy', bridgePort: 48333 })).toEqual({
      host: 'localhost',
      port: 3000,
      fallbackPorts: 0,
    });
  });
});

describe('log', () => {
  it('numbers entries, summarises events and tracks operations', async () => {
    const log = new LogStore();
    const seen: number[] = [];
    log.subscribe(() => seen.push(log.snapshot().length));
    log.add(
      'event',
      'basket.changed',
      summarize('basket.changed', {
        previous: fx.basket([]),
        current: fx.basket([fx.lineItem({ sku: 'A', description: 'A', unitPrice: '1.00' })]),
        source: 'INCREMENTAL',
        added: [],
        removed: [],
        quantityChanged: [],
        priceChanged: [],
        discountsChanged: [],
        taxChanged: [],
        detailsChanged: [],
        taxTotalChanged: false,
      }),
    );
    await track(log, 'refund', Promise.reject(new Error('declined'))).then(
      () => undefined,
      () => undefined,
    );
    await new Promise((resolve) => setTimeout(resolve, 0));
    const entries = log.snapshot();
    expect(entries.map((e) => e.seq)).toEqual([1, 2, 3]);
    expect(entries[0]?.summary).toContain('incremental: 1 line(s), total 1.00');
    expect(entries[2]).toMatchObject({
      kind: 'operation',
      type: 'refund',
      summary: 'failed: declined',
    });
    expect(seen.length).toBe(3);
    expect(summarize('session.ended', { sessionId: 's', forced: true })).toBe(
      'session s ended (forced)',
    );
  });
});
