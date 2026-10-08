// `recomputeTotal` is the register's answer to TOTAL_REQUIRED: tax on the rebated amount.
import { describe, expect, it } from 'vitest';
import { basket, lineItem } from '../../../packages/sdk/test/fixtures';
import { CATALOG, recomputeTotal } from '../src/catalog';

const candle = CATALOG.find((item) => item.sku === 'KRK-CNDL-LRG-VAN')!;

describe('recomputeTotal', () => {
  it('taxes the full price when nothing was rebated', () => {
    expect(recomputeTotal(basket([lineItem(candle)]))).toBe('27.21');
  });

  it('taxes the rebated amount, not the undiscounted price', () => {
    const [line] = basket([lineItem(candle)]).items;
    const discounted = basket([{ ...line!, adjustedTotal: '22.99' }]);
    // 22.99 + 8.875% tax (2.04) = 25.03; the host's suggestion would be 27.21 − 2.00 = 25.21.
    expect(recomputeTotal(discounted)).toBe('25.03');
  });
});
