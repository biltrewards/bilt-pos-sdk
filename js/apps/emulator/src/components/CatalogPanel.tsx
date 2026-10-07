import { useState, type ReactNode } from 'react';
import {
  CATALOG,
  CATEGORIES,
  customItem,
  nextCustomSku,
  toBasketItem,
  type Product,
} from '../catalog';
import { useLane } from '../lane/LaneProvider';
import { formatMoney, money, parseMoney } from '../money';
import { taxRateFor } from '../tax';

/** The scanner stand-in: one button per catalog product, grouped by category, plus a keyed-in line. */
export function CatalogPanel({ disabled }: { readonly disabled: boolean }): ReactNode {
  const { basket, run, settings } = useLane();
  const [amount, setAmount] = useState('');
  const [label, setLabel] = useState('');
  const [taxed, setTaxed] = useState(true);
  const scan = (product: Product) => run(basket.addItem(toBasketItem(product)));
  const addCustom = () => {
    const price = parseMoney(amount);
    const current = basket.basket;
    if (!price || !current) return;
    const sku = nextCustomSku(
      current.cartId,
      current.items.map((line) => line.sku),
    );
    run(basket.addItem(customItem(sku, price, label, taxed)).then(() => setAmount('')));
  };

  return (
    <section className="panel">
      <h2>Scan</h2>
      <p className="muted small">
        Each tap is one incremental <code>addItem</code>; a repeated SKU bumps its quantity. Tax is
        the NJ policy: 6.625% except Grocery and Apparel.
      </p>
      {CATEGORIES.map((category) => (
        <div key={category}>
          <div className="category">
            {category}
            {taxRateFor(category) === undefined ? ' · tax exempt' : ''}
          </div>
          <div className="catalog">
            {CATALOG.filter((product) => product.category === category).map((product) => (
              <button
                key={product.sku}
                type="button"
                className="catalog-item"
                disabled={disabled}
                onClick={() => scan(product)}
              >
                <span className="catalog-name">{product.name}</span>
                <span className="catalog-meta">
                  {product.sku} · {formatMoney(money(product.priceMinor), settings.currency)}
                </span>
              </button>
            ))}
          </div>
        </div>
      ))}
      <form
        className="inline"
        onSubmit={(event) => {
          event.preventDefault();
          addCustom();
        }}
      >
        <input
          aria-label="Custom amount"
          placeholder="Custom amount, e.g. 12.50"
          inputMode="decimal"
          value={amount}
          onChange={(event) => setAmount(event.target.value)}
          disabled={disabled}
        />
        <input
          aria-label="Custom item description"
          placeholder="Description (optional)"
          value={label}
          onChange={(event) => setLabel(event.target.value)}
          disabled={disabled}
        />
        <label className="check">
          <input
            type="checkbox"
            checked={taxed}
            onChange={(event) => setTaxed(event.target.checked)}
          />
          taxed
        </label>
        <button type="submit" disabled={disabled || parseMoney(amount) === undefined}>
          Add custom item
        </button>
      </form>
    </section>
  );
}
