import type { ReactNode } from 'react';
import { CATALOG, type CatalogEntry } from '../catalog';
import { formatMoney } from '../money';

export interface CatalogPanelProps {
  readonly currency: string;
  readonly disabled: boolean;

  /** A scan: one unit of the entry, added incrementally. */
  readonly onScan: (entry: CatalogEntry) => void;
}

/** The scanner stand-in: one button per catalog entry. */
export function CatalogPanel({ currency, disabled, onScan }: CatalogPanelProps): ReactNode {
  return (
    <section className="panel">
      <h2>Scan</h2>
      <p className="muted">
        Each tap is one incremental `addItem`; a repeated SKU bumps its quantity.
      </p>
      <div className="catalog">
        {CATALOG.map((entry) => (
          <button
            key={entry.sku}
            type="button"
            className="catalog-item"
            disabled={disabled}
            onClick={() => onScan(entry)}
          >
            <span className="catalog-name">{entry.description}</span>
            <span className="catalog-meta">
              {entry.sku} · {formatMoney(entry.unitPrice, currency)}
            </span>
          </button>
        ))}
      </div>
    </section>
  );
}
