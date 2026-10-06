import type { Basket, BasketChange, BasketLineItem } from '@bilt/pos-sdk';
import type { ReactNode } from 'react';
import { cents, formatMoney } from '../money';

export interface BasketPanelProps {
  readonly basket: Basket | null;
  readonly currency: string;
  readonly lastChange: BasketChange['source'] | null;

  /** Mutations are refused while money moves and after the basket settled. */
  readonly locked: boolean;
  readonly onQuantity: (line: BasketLineItem, quantity: number) => void;
  readonly onRemove: (line: BasketLineItem) => void;
  readonly onRemoveDiscounts: () => void;
  readonly onClear: () => void;
}

function Row({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className={strong ? 'total strong' : 'total'}>
      <span>{label}</span>
      <span data-testid={strong ? 'grand-total' : undefined}>{value}</span>
    </div>
  );
}

/** The session's basket as the cashier sees it, with the per-line quantity and removal controls. */
export function BasketPanel(props: BasketPanelProps): ReactNode {
  const { basket, currency, lastChange, locked } = props;
  const lines = basket?.items ?? [];
  const hasDiscounts = lines.some((line) => line.discounts.length > 0);
  const fmt = (value: string) => formatMoney(value, currency);

  return (
    <section className="panel basket">
      <div className="panel-header">
        <h2>Basket</h2>
        {lastChange ? <span className="badge">last change: {lastChange}</span> : null}
      </div>
      {lines.length === 0 ? (
        <p className="muted">Nothing rung yet.</p>
      ) : (
        <table className="lines">
          <thead>
            <tr>
              <th>Item</th>
              <th>Qty</th>
              <th className="num">Line</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {lines.map((line) => (
              <tr key={line.itemId}>
                <td>
                  <div>{line.description}</div>
                  <div className="muted small">
                    {line.sku} · {fmt(line.unitPrice)} each
                    {line.type !== 'SALE' ? ` · ${line.type}` : ''}
                  </div>
                  {line.discounts.map((discount, index) => (
                    <div key={`${discount.reference ?? discount.label}-${index}`} className="small">
                      − {fmt(discount.amount)} {discount.label}
                    </div>
                  ))}
                  {cents(line.rebateAmount) > 0 ? (
                    <div className="small">
                      − {fmt(line.rebateAmount)} {line.rebateLabel ?? 'rebate'}
                    </div>
                  ) : null}
                </td>
                <td className="qty">
                  <button
                    type="button"
                    aria-label={`Decrease ${line.description}`}
                    disabled={locked}
                    onClick={() => props.onQuantity(line, line.quantity - 1)}
                  >
                    −
                  </button>
                  <span>{line.quantity}</span>
                  <button
                    type="button"
                    aria-label={`Increase ${line.description}`}
                    disabled={locked}
                    onClick={() => props.onQuantity(line, line.quantity + 1)}
                  >
                    +
                  </button>
                </td>
                <td className="num">{fmt(line.adjustedTotal)}</td>
                <td>
                  <button
                    type="button"
                    className="link"
                    aria-label={`Remove ${line.description}`}
                    disabled={locked}
                    onClick={() => props.onRemove(line)}
                  >
                    &times;
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {basket ? (
        <div className="totals">
          <Row label="Subtotal" value={fmt(basket.subtotal)} />
          {cents(basket.discountTotal) > 0 ? (
            <Row label="Discounts" value={`− ${fmt(basket.discountTotal)}`} />
          ) : null}
          {cents(basket.rebateTotal) > 0 ? (
            <Row label="Rebates" value={`− ${fmt(basket.rebateTotal)}`} />
          ) : null}
          <Row label="Tax" value={fmt(basket.taxTotal)} />
          <Row label="Total" value={fmt(basket.grandTotal)} strong />
        </div>
      ) : null}
      <div className="actions">
        <button
          type="button"
          className="secondary"
          disabled={locked || !hasDiscounts}
          onClick={props.onRemoveDiscounts}
        >
          Remove all discounts (one `mutate`)
        </button>
        <button
          type="button"
          className="secondary"
          disabled={locked || lines.length === 0}
          onClick={props.onClear}
        >
          Clear basket
        </button>
      </div>
    </section>
  );
}
