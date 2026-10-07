import type { Basket, BasketItem, BasketLineItem } from '@bilt/pos-sdk';
import { useState, type ReactNode } from 'react';
import { CATALOG, toBasketItem } from '../catalog';
import { useLane } from '../lane/LaneProvider';

type Quantities = Readonly<Record<string, number>>;

interface Draft {
  readonly quantities: Quantities;
  /** Catalog lines as copied from the basket, so a sync keeps their discounts and reference. */
  readonly copied: Readonly<Record<string, BasketItem>>;
  /** Lines the draft does not edit (keyed-in, gift-card, credit, return), synced back as they were. */
  readonly kept: readonly BasketItem[];
}

const EMPTY: Draft = { quantities: {}, copied: {}, kept: [] };
const CATALOG_SKUS = new Set(CATALOG.map((product) => product.sku));

/** The register-side fields of a basket line; a fixed tax is the amount on a line without a rate. */
function toItem(line: BasketLineItem): BasketItem {
  return {
    sku: line.sku,
    description: line.description,
    quantity: line.quantity,
    unitPrice: line.unitPrice,
    discounts: line.discounts,
    type: line.type,
    metadata: line.metadata,
    ...(line.reference === undefined ? {} : { reference: line.reference }),
    ...(line.category === undefined ? {} : { category: line.category }),
    ...(line.taxRate === undefined
      ? Number(line.taxAmount) !== 0
        ? { taxAmount: line.taxAmount }
        : {}
      : { taxRate: line.taxRate }),
  };
}

function fromBasket(basket: Basket | null): Draft {
  const quantities: Record<string, number> = {};
  const copied: Record<string, BasketItem> = {};
  const kept: BasketItem[] = [];
  for (const line of basket?.items ?? []) {
    if (line.type === 'SALE' && CATALOG_SKUS.has(line.sku) && copied[line.sku] === undefined) {
      quantities[line.sku] = line.quantity;
      copied[line.sku] = toItem(line);
    } else {
      kept.push(toItem(line));
    }
  }
  return { quantities, copied, kept };
}

/**
 * A cart the register owns, edited offline and pushed whole with `replace`: the model of a POS
 * that keeps its own cart and syncs it after every change, as opposed to scanning into the
 * session line by line. Lines are paired by SKU, so a synced quantity change keeps its item id.
 * The draft edits catalog quantities; a copy from the basket keeps every other line as it was, so
 * syncing an unchanged copy leaves the basket unchanged.
 */
export function CartDraft({ disabled }: { readonly disabled: boolean }): ReactNode {
  const { basket, run } = useLane();
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [open, setOpen] = useState(false);
  const { quantities, copied, kept } = draft;
  const items = [
    ...CATALOG.flatMap((product) => {
      const quantity = quantities[product.sku] ?? 0;
      if (quantity <= 0) return [];
      const line = copied[product.sku];
      return [line ? { ...line, quantity } : toBasketItem(product, quantity)];
    }),
    ...kept,
  ];

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
                      setDraft((previous) => ({
                        ...previous,
                        quantities: {
                          ...previous.quantities,
                          [product.sku]: Math.max(0, Math.trunc(Number(event.target.value) || 0)),
                        },
                      }))
                    }
                  />
                </td>
              </tr>
            ))}
            {kept.map((line, index) => (
              <tr key={`kept-${index}`}>
                <td>{line.description}</td>
                <td className="muted small">{line.quantity ?? 1} × kept as copied</td>
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
            setDraft(fromBasket(basket.basket));
            setOpen(true);
          }}
        >
          Copy from basket
        </button>
      </div>
    </section>
  );
}
