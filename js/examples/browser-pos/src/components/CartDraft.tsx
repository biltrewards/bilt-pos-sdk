import type { Basket, BasketItem } from '@bilt/pos-sdk';
import { useState, type ReactNode } from 'react';
import { CATALOG, toBasketItem } from '../catalog';

export interface CartDraftProps {
  readonly basket: Basket | null;
  readonly disabled: boolean;

  /** Pushes the whole cart as `basket.replace(items)`. */
  readonly onSync: (items: readonly BasketItem[]) => void;
}

type Quantities = Readonly<Record<string, number>>;

function fromBasket(basket: Basket | null): Quantities {
  const quantities: Record<string, number> = {};
  for (const line of basket?.items ?? []) {
    if (line.type === 'SALE') quantities[line.sku] = (quantities[line.sku] ?? 0) + line.quantity;
  }
  return quantities;
}

/**
 * A cart the register owns, edited offline and pushed whole with `replace`: the model of a POS
 * that keeps its own cart and syncs it after every change, as opposed to scanning into the
 * session line by line. Lines are paired by SKU, so a synced quantity change keeps its item id.
 */
export function CartDraft({ basket, disabled, onSync }: CartDraftProps): ReactNode {
  const [quantities, setQuantities] = useState<Quantities>({});
  const items = CATALOG.flatMap((entry) => {
    const quantity = quantities[entry.sku] ?? 0;
    return quantity > 0 ? [toBasketItem(entry, quantity)] : [];
  });

  return (
    <section className="panel">
      <h2>POS-owned cart</h2>
      <p className="muted">
        Edit quantities here, then push the whole cart with one `basket.replace`.
      </p>
      <table className="draft">
        <tbody>
          {CATALOG.map((entry) => (
            <tr key={entry.sku}>
              <td>{entry.description}</td>
              <td>
                <input
                  type="number"
                  min={0}
                  step={1}
                  aria-label={`${entry.description} quantity`}
                  value={quantities[entry.sku] ?? 0}
                  onChange={(event) =>
                    setQuantities((previous) => ({
                      ...previous,
                      [entry.sku]: Math.max(0, Math.trunc(Number(event.target.value) || 0)),
                    }))
                  }
                />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="actions">
        <button type="button" disabled={disabled} onClick={() => onSync(items)}>
          Sync cart ({items.length} {items.length === 1 ? 'line' : 'lines'})
        </button>
        <button
          type="button"
          className="secondary"
          onClick={() => setQuantities(fromBasket(basket))}
        >
          Copy from basket
        </button>
      </div>
    </section>
  );
}
