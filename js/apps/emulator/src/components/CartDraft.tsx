import type { Basket } from '@bilt/pos-sdk';
import { useState, type ReactNode } from 'react';
import { CATALOG, toBasketItem } from '../catalog';
import { useLane } from '../lane/LaneProvider';

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
export function CartDraft({ disabled }: { readonly disabled: boolean }): ReactNode {
  const { basket, run } = useLane();
  const [quantities, setQuantities] = useState<Quantities>({});
  const [open, setOpen] = useState(false);
  const items = CATALOG.flatMap((product) => {
    const quantity = quantities[product.sku] ?? 0;
    return quantity > 0 ? [toBasketItem(product, quantity)] : [];
  });

  return (
    <section className="panel">
      <div className="panel-header">
        <h2>POS-owned cart</h2>
        <button type="button" className="link" onClick={() => setOpen((o) => !o)}>
          {open ? 'hide' : 'show'}
        </button>
      </div>
      <p className="muted small">
        Edit quantities here, then push the whole cart with one <code>basket.replace</code>.
      </p>
      {open ? (
        <table className="draft">
          <tbody>
            {CATALOG.map((product) => (
              <tr key={product.sku}>
                <td>{product.name}</td>
                <td>
                  <input
                    type="number"
                    min={0}
                    step={1}
                    aria-label={`${product.name} draft quantity`}
                    value={quantities[product.sku] ?? 0}
                    onChange={(event) =>
                      setQuantities((previous) => ({
                        ...previous,
                        [product.sku]: Math.max(0, Math.trunc(Number(event.target.value) || 0)),
                      }))
                    }
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
      <div className="actions">
        <button type="button" disabled={disabled} onClick={() => run(basket.replace(items))}>
          Sync cart ({items.length} {items.length === 1 ? 'line' : 'lines'})
        </button>
        <button
          type="button"
          className="secondary"
          onClick={() => {
            setQuantities(fromBasket(basket.basket));
            setOpen(true);
          }}
        >
          Copy from basket
        </button>
      </div>
    </section>
  );
}
