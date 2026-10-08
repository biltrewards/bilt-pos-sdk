// The register's own arithmetic and bookkeeping: money parsing, the NJ tax policy, the catalog
// and keyed-in lines, and the log store.
import { describe, expect, it } from 'vitest';
import * as fx from '../../../packages/sdk/test/fixtures';
import { CATALOG, customItem, nextCustomSku, toBasketItem } from '../src/catalog';
import { LineLog, summarize } from '../src/log';
import { formatMinor, nonNegativeMoneyMinor, parseMoney, recomputeTotal } from '../src/money';
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

  it('formats and validates cents as the desktop does', () => {
    expect(formatMinor(7999)).toBe('79.99');
    expect(formatMinor(5)).toBe('0.05');
    expect(formatMinor(-3731)).toBe('-37.31');
    expect(nonNegativeMoneyMinor('12.5')).toBe(1250);
    expect(nonNegativeMoneyMinor('0')).toBe(0);
    expect(nonNegativeMoneyMinor('1.')).toBe(100);
    expect(nonNegativeMoneyMinor('1.234')).toBeNull();
    expect(nonNegativeMoneyMinor('-1')).toBeNull();
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

  it('applies a new launch over the saved settings once', () => {
    localStorage.clear();
    saveSettings({ ...loadSettings(), poiId: 'SAVED', mode: 'terminal', saleId: 'LANE-9' });
    const launch = { mode: 'local' } as const;
    expect(loadSettings(localStorage, launch)).toMatchObject({
      mode: 'local',
      poiId: 'SAVED',
      saleId: 'LANE-9',
    });
    // a Settings edit after that launch persists until the launch changes
    saveSettings({ ...loadSettings(localStorage, launch), mode: 'terminal' });
    expect(loadSettings(localStorage, launch).mode).toBe('terminal');
    saveSettings({ ...loadSettings(localStorage, launch), mode: 'local' });
    expect(loadSettings(localStorage, { mode: 'terminal' }).mode).toBe('terminal');
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
  it('stamps lines, notifies readers and summarises events', () => {
    const log = new LineLog();
    const seen: number[] = [];
    log.subscribe(() => seen.push(log.snapshot().length));
    log.add(
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
    log.add('second');
    const lines = log.snapshot();
    expect(lines[0]).toMatch(/^\d\d:\d\d:\d\d incremental: 1 line\(s\), total 1\.00/);
    expect(lines[1]).toMatch(/ second$/);
    expect(seen).toEqual([1, 2]);
    expect(summarize('session.ended', { sessionId: 's', forced: true })).toBe(
      'session s ended (forced)',
    );
  });
});
