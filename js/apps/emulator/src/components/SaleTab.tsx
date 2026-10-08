import { useSyncExternalStore, type ReactNode } from 'react';
import { CATALOG } from '../catalog';
import { useController, useEmulatorState } from '../emulator/context';
import { canOperate } from '../emulator/state';
import { formatMinor } from '../money';
import { TabRow } from './common';

export type SaleTabPane = 'products' | 'keypad';

const PANES = [
  { id: 'products', label: 'Products' },
  { id: 'keypad', label: 'Keypad' },
] as const;

/** Largest amount the keypad accepts: $999,999.99; further digits are ignored. */
const MAX_KEYPAD_MINOR = 99_999_999;

/**
 * The keypad's entry: the amount typed so far and, once the operator taps a custom basket line,
 * the SKU that amount live-edits. Hoisted above the basket and the Sale tab, so a tap on a line
 * can seed it and it survives a tab switch. A plain object rather than React state, so two taps
 * in one frame see each other's effect, as the desktop's snapshot state does.
 */
export class KeypadEntry {
  minor = 0;
  editingSku: string | null = null;
  private version = 0;
  private readonly listeners = new Set<() => void>();

  private changed(): void {
    this.version++;
    for (const listener of this.listeners) listener();
  }

  append(digit: number): void {
    const next = this.minor * 10 + digit;
    if (next <= MAX_KEYPAD_MINOR) {
      this.minor = next;
      this.changed();
    }
  }

  backspace(): void {
    this.minor = Math.floor(this.minor / 10);
    this.changed();
  }

  edit(sku: string, priceMinor: number): void {
    this.editingSku = sku;
    this.minor = priceMinor;
    this.changed();
  }

  reset(): void {
    this.editingSku = null;
    this.minor = 0;
    this.changed();
  }

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  snapshot = (): number => this.version;
}

export function useKeypad(entry: KeypadEntry): KeypadEntry {
  useSyncExternalStore(entry.subscribe, entry.snapshot, entry.snapshot);
  return entry;
}

function ProductGrid(): ReactNode {
  const controller = useController();
  const state = useEmulatorState();
  return (
    <div className="product-grid scroll grow" aria-label="Products">
      {CATALOG.map((product) => (
        <button
          key={product.sku}
          type="button"
          className="product"
          disabled={!canOperate(state)}
          onClick={() => controller.addProduct(product)}
        >
          <span className="product-name">{product.name}</span>
          <span>${formatMinor(product.priceMinor)}</span>
        </button>
      ))}
    </div>
  );
}

const KEYPAD_ROWS = [
  [1, 2, 3],
  [4, 5, 6],
  [7, 8, 9],
];

/**
 * Number pad for an amount the catalog does not carry. ✓ rings the typed amount up as a custom
 * line; with a custom line adopted from the basket, every keystroke re-prices that line instead
 * and ✓ only releases it, or drops it when it was backspaced to zero.
 */
function KeypadPane({ entry }: { entry: KeypadEntry }): ReactNode {
  const controller = useController();
  const state = useEmulatorState();
  const keypad = useKeypad(entry);
  const checkoutActive = state.sessionId !== null;
  const editingSku = keypad.editingSku;
  const onKey = (edit: () => void) => {
    edit();
    const sku = keypad.editingSku;
    if (sku === null) return;
    void controller.updateCustomItemPrice(sku, keypad.minor).then((ok) => {
      if (!ok && keypad.editingSku === sku) keypad.reset();
    });
  };
  const canSubmit = editingSku !== null || (checkoutActive && keypad.minor > 0);
  const submit = async () => {
    const sku = keypad.editingSku;
    const amount = keypad.minor;
    const done =
      sku !== null && amount === 0
        ? await controller.removeCustomItem(sku)
        : sku !== null
          ? true
          : amount > 0
            ? await controller.addCustomItem(amount)
            : false;
    if (done) keypad.reset();
  };
  const hint =
    editingSku !== null && keypad.minor === 0
      ? 'Editing the marked line — ✓ now drops it from the basket'
      : editingSku !== null
        ? 'Editing the marked line — the basket updates as you type'
        : !checkoutActive
          ? 'Start a checkout to ring a custom amount up'
          : 'Tap ✓ to add this amount, or tap a custom basket line to edit it';
  return (
    <div className="keypad grow" aria-label="Keypad">
      <div className="keypad-display">
        <div className="keypad-amount" data-testid="keypad-amount">
          ${formatMinor(keypad.minor)}
        </div>
        <div className="small">{hint}</div>
      </div>
      {KEYPAD_ROWS.map((row) => (
        <div className="keypad-row" key={row.join('')}>
          {row.map((digit) => (
            <button
              key={digit}
              type="button"
              className="key"
              onClick={() => onKey(() => keypad.append(digit))}
            >
              {digit}
            </button>
          ))}
        </div>
      ))}
      <div className="keypad-row">
        <button
          type="button"
          className="key tonal"
          aria-label="Backspace"
          onClick={() => onKey(() => keypad.backspace())}
        >
          ⌫
        </button>
        <button type="button" className="key" onClick={() => onKey(() => keypad.append(0))}>
          0
        </button>
        <button
          type="button"
          className="key"
          aria-label="Submit"
          disabled={!canSubmit}
          onClick={() => void submit()}
        >
          ✓
        </button>
      </div>
    </div>
  );
}

/** The Sale tab: the catalog grid and the keypad behind a pane selector. */
export function SaleTab({
  entry,
  pane,
  onSelectPane,
}: {
  entry: KeypadEntry;
  pane: SaleTabPane;
  onSelectPane: (pane: SaleTabPane) => void;
}): ReactNode {
  const state = useEmulatorState();
  return (
    <section className="card tab-content" aria-label="Sale">
      {state.sessionId === null ? (
        <p className="small muted">Start Checkout to add products to the basket</p>
      ) : null}
      <TabRow tabs={PANES} selected={pane} onSelect={onSelectPane} label="Sale panes" />
      {pane === 'products' ? <ProductGrid /> : <KeypadPane entry={entry} />}
    </section>
  );
}
