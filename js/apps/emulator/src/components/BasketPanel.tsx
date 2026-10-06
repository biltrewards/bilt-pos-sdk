import type { BasketDiscount, BasketLineItem } from '@bilt/pos-sdk';
import { useState, type ReactNode } from 'react';
import { useLane } from '../lane/LaneProvider';
import { cents, formatMoney, parseMoney } from '../money';

function Row({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className={strong ? 'total strong' : 'total'}>
      <span>{label}</span>
      <span data-testid={strong ? 'grand-total' : undefined}>{value}</span>
    </div>
  );
}

type Editor = { kind: 'discount' | 'credit'; itemId: string } | null;

/**
 * Edits one line's register discount (manual, or an offer with a reference) or adds a register
 * credit tied to the line. A discount is `setDiscounts` on the line; a credit is a `CREDIT` line
 * of its own, because credits reduce the charge without changing the fulfilled value of the
 * line they belong to (a gift card still loads its face value).
 */
function LineEditor({
  line,
  editor,
  close,
}: {
  line: BasketLineItem;
  editor: NonNullable<Editor>;
  close: () => void;
}): ReactNode {
  const { basket, run } = useLane();
  const [amount, setAmount] = useState('');
  const [label, setLabel] = useState('');
  const [offer, setOffer] = useState(false);
  const [reference, setReference] = useState('');
  const value = parseMoney(amount);
  const tooMuch =
    editor.kind === 'discount' && value !== undefined && cents(value) > cents(line.subtotal);

  const submit = () => {
    if (!value) return;
    if (editor.kind === 'discount') {
      const discount: BasketDiscount = {
        label: label.trim() || (offer ? `Offer ${reference.trim()}` : 'Manual discount'),
        amount: value,
        ...(offer && reference.trim() ? { reference: reference.trim() } : {}),
      };
      run(basket.setDiscounts(line.itemId, [...line.discounts, discount]).then(close));
    } else {
      run(
        basket
          .addItem({
            type: 'CREDIT',
            sku: `CREDIT-${line.itemId}-${Date.now().toString(36)}`,
            description: label.trim() || `Credit on ${line.description}`,
            quantity: 1,
            unitPrice: value,
            metadata: { appliesTo: line.itemId },
          })
          .then(close),
      );
    }
  };

  return (
    <form
      className="line-editor"
      aria-label={editor.kind === 'discount' ? 'Add discount' : 'Add credit'}
      onSubmit={(event) => {
        event.preventDefault();
        submit();
      }}
    >
      <div className="inline">
        <input
          aria-label="Amount"
          placeholder="Amount"
          inputMode="decimal"
          value={amount}
          onChange={(event) => setAmount(event.target.value)}
        />
        <input
          aria-label="Label"
          placeholder="Label"
          value={label}
          onChange={(event) => setLabel(event.target.value)}
        />
        {editor.kind === 'discount' ? (
          <>
            <label className="check">
              <input
                type="checkbox"
                checked={offer}
                onChange={(event) => setOffer(event.target.checked)}
              />
              offer
            </label>
            {offer ? (
              <input
                aria-label="Offer reference"
                placeholder="Offer reference, e.g. OFFER-42"
                value={reference}
                onChange={(event) => setReference(event.target.value)}
              />
            ) : null}
          </>
        ) : null}
        <button type="submit" disabled={!value || tooMuch}>
          {editor.kind === 'discount' ? 'Apply discount' : 'Add credit'}
        </button>
        <button type="button" className="secondary" onClick={close}>
          Cancel
        </button>
      </div>
      {tooMuch ? (
        <p className="error small">A discount may reduce a line to zero, never below.</p>
      ) : null}
    </form>
  );
}

/** The session's basket as the cashier sees it, with the per-line quantity, discount, credit and removal controls. */
export function BasketPanel({ locked }: { readonly locked: boolean }): ReactNode {
  const { basket, settings, lastChange, run, pendingGiftCards } = useLane();
  const current = basket.basket;
  const lines = current?.items ?? [];
  const hasDiscounts = lines.some((line) => line.discounts.length > 0);
  const fmt = (value: string) => formatMoney(value, settings.currency);
  const [editor, setEditor] = useState<Editor>(null);

  const removeDiscounts = () =>
    run(
      basket.mutate((m) => {
        for (const line of lines) {
          if (line.discounts.length > 0) m.setDiscounts(line.itemId, []);
        }
      }),
    );
  const removeDiscount = (line: BasketLineItem, index: number) =>
    run(
      basket.setDiscounts(
        line.itemId,
        line.discounts.filter((_, i) => i !== index),
      ),
    );

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
            {lines.map((line) => {
              const giftCard = pendingGiftCards.find((gift) => gift.reference === line.reference);
              return (
                <tr key={line.itemId} data-testid={`line-${line.itemId}`}>
                  <td>
                    <div>
                      {line.description}
                      {line.type !== 'SALE' ? <span className="badge"> {line.type}</span> : null}
                      {giftCard ? (
                        <span className="badge">
                          {' '}
                          {giftCard.type.toLowerCase()}{' '}
                          {giftCard.card.storedValueId ?? 'swiped card'}
                        </span>
                      ) : null}
                    </div>
                    <div className="muted small">
                      {line.sku} · {fmt(line.unitPrice)} each
                      {line.taxRate
                        ? ` · tax ${(Number(line.taxRate) * 100).toFixed(3)}%`
                        : ' · no tax'}
                      {line.reference ? ` · ref ${line.reference}` : ''}
                    </div>
                    {line.discounts.map((discount, index) => (
                      <div
                        key={`${discount.reference ?? discount.label}-${index}`}
                        className="small"
                      >
                        − {fmt(discount.amount)} {discount.label}
                        {discount.reference ? ` (${discount.reference})` : ''}{' '}
                        <button
                          type="button"
                          className="link"
                          aria-label={`Remove discount ${discount.label}`}
                          disabled={locked}
                          onClick={() => removeDiscount(line, index)}
                        >
                          remove
                        </button>
                      </div>
                    ))}
                    {cents(line.rebateAmount) > 0 ? (
                      <div className="small">
                        − {fmt(line.rebateAmount)} {line.rebateLabel ?? 'rebate'}
                      </div>
                    ) : null}
                    {line.type === 'SALE' && !locked ? (
                      <div className="line-tools">
                        <button
                          type="button"
                          className="link"
                          onClick={() => setEditor({ kind: 'discount', itemId: line.itemId })}
                        >
                          discount
                        </button>
                        <button
                          type="button"
                          className="link"
                          onClick={() => setEditor({ kind: 'credit', itemId: line.itemId })}
                        >
                          credit
                        </button>
                      </div>
                    ) : null}
                    {editor && editor.itemId === line.itemId ? (
                      <LineEditor line={line} editor={editor} close={() => setEditor(null)} />
                    ) : null}
                  </td>
                  <td className="qty">
                    <button
                      type="button"
                      aria-label={`Decrease ${line.description}`}
                      disabled={locked}
                      onClick={() => run(basket.updateItemQuantity(line.itemId, line.quantity - 1))}
                    >
                      −
                    </button>
                    <span>{line.quantity}</span>
                    <button
                      type="button"
                      aria-label={`Increase ${line.description}`}
                      disabled={locked}
                      onClick={() => run(basket.updateItemQuantity(line.itemId, line.quantity + 1))}
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
                      onClick={() => run(basket.removeItem(line.itemId))}
                    >
                      &times;
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
      {current ? (
        <div className="totals">
          <Row label="Subtotal" value={fmt(current.subtotal)} />
          {cents(current.discountTotal) > 0 ? (
            <Row label="Discounts" value={`− ${fmt(current.discountTotal)}`} />
          ) : null}
          {cents(current.rebateTotal) > 0 ? (
            <Row label="Rebates" value={`− ${fmt(current.rebateTotal)}`} />
          ) : null}
          <Row label="Tax" value={fmt(current.taxTotal)} />
          <Row label="Total" value={fmt(current.grandTotal)} strong />
          <p className="muted small">
            cart <code>{current.cartId}</code> · sale transaction{' '}
            <code>{current.saleTransactionId.transactionId}</code>
          </p>
        </div>
      ) : null}
      <div className="actions">
        <button
          type="button"
          className="secondary"
          disabled={locked || !hasDiscounts}
          onClick={removeDiscounts}
          title="One mutate batch, one basket.changed"
        >
          Remove all discounts (one <code>mutate</code>)
        </button>
        <button
          type="button"
          className="secondary"
          disabled={locked || lines.length === 0}
          onClick={() => run(basket.clear())}
        >
          Clear basket
        </button>
      </div>
    </section>
  );
}
