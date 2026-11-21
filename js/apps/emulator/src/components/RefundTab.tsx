import { useState, type ReactNode } from 'react';
import { useController, useEmulatorState } from '../emulator/context';
import {
  memberLabel,
  refundLabel,
  cardLine,
  saleRefundable,
  saleStatusLabel,
  type StoredSaleUi,
} from '../emulator/state';
import { formatMinor } from '../money';
import { LabeledRadio, LineItemRow } from './common';

function SalesListCard({
  sales,
  selectedId,
  onSelect,
}: {
  sales: readonly StoredSaleUi[];
  selectedId: string | null;
  onSelect: (id: string) => void;
}): ReactNode {
  return (
    <section className="card sales-list" aria-label="Completed sales">
      <h2>Completed sales</h2>
      {sales.length === 0 ? (
        <p className="small">None stored yet — complete a payment on the Sale tab</p>
      ) : (
        <ul className="scroll grow sales">
          {sales.map((sale) => (
            <li key={sale.id}>
              <button
                type="button"
                className={sale.id === selectedId ? 'sale selected' : 'sale tonal'}
                aria-pressed={sale.id === selectedId}
                onClick={() => onSelect(sale.id)}
              >
                <span className="sale-top">
                  <strong>${sale.totalAmount}</strong>
                  <span className="small">{sale.completedAtLabel}</span>
                </span>
                <span className="small">{saleStatusLabel(sale)}</span>
                {sale.cardPayments.length > 1
                  ? sale.cardPayments.map((card, index) => (
                      <span key={card.poiTransactionId} className="small">
                        {cardLine(index, card)}
                      </span>
                    ))
                  : null}
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

type RefundMode = 'FULL' | 'ITEMS';

/**
 * What the Refund button returns: the full amount (a void of every movement of the sale, on its
 * own session with no checkout open) or the checked items (returns rung into the active
 * checkout's basket and settled with it).
 */
function RefundDetailsCard({ sale }: { sale: StoredSaleUi }): ReactNode {
  const controller = useController();
  const state = useEmulatorState();
  const fullAvailable = sale.fullRefundAvailable;
  const [mode, setMode] = useState<RefundMode>(fullAvailable ? 'FULL' : 'ITEMS');
  const [selectedSkus, setSelectedSkus] = useState<ReadonlySet<string>>(new Set());
  // A return restores to one card payment: with several still standing, the cashier picks which.
  // a card drawn down to nothing by earlier returns has no capacity to take another
  const hasCapacity = (card: StoredSaleUi['cardPayments'][number]) =>
    !card.refunded && card.remainingMinor !== 0;
  const standingCards = sale.cardPayments.filter(hasCapacity);
  const [pickedCard, setPickedCard] = useState<string | null>(null);
  const cardChoice =
    standingCards.find((card) => card.poiTransactionId === pickedCard) ?? standingCards[0] ?? null;
  const hasRecordedItems = sale.items.length > 0;
  const itemsAvailable = hasRecordedItems && !sale.hasGiftCardPurchase;
  const refundable = saleRefundable(sale);
  const itemsMinor = sale.items
    .filter((item) => selectedSkus.has(item.sku))
    .reduce((sum, item) => sum + item.refundMinor, 0);
  const amount = mode === 'FULL' ? sale.totalAmount : formatMinor(itemsMinor);
  const connected = state.connection.phase !== 'DISCONNECTED';
  const sessionActive = state.sessionId !== null;
  const terminal = state.mode === 'terminal';
  const enabled =
    refundable &&
    connected &&
    (mode === 'FULL'
      ? fullAvailable && terminal && !sessionActive && !state.refundInProgress
      : sessionActive && itemsMinor > 0);

  const notice = sale.voided
    ? 'Voided — nothing left to refund'
    : sale.fullyRefunded
      ? sale.externalPaymentAmount
        ? 'Terminal movements reversed'
        : 'Refunded in full — nothing left to refund'
      : sale.refunded && fullAvailable
        ? 'Partially refunded — Full amount reverses what is outstanding'
        : sale.refunded
          ? 'Already partially refunded'
          : null;
  const guard = !connected
    ? 'Connect to the terminal to run a refund'
    : mode === 'FULL' && sessionActive
      ? 'End the active checkout to run a full refund'
      : mode === 'FULL' && !terminal
        ? 'A full refund needs a terminal — clear Local session'
        : mode === 'ITEMS' && !sessionActive
          ? 'Start a checkout to ring returns into its basket'
          : null;

  return (
    <section className="card refund-details" aria-label="Refund">
      <h2>Refund</h2>
      <p>
        ${sale.totalAmount} · {sale.completedAtLabel} · {memberLabel(sale)}
      </p>
      {sale.externalPaymentAmount ? (
        <p className="small">
          Cash ${sale.externalPaymentAmount} must be reimbursed manually; this refund reverses
          terminal movements only.
        </p>
      ) : null}
      {notice ? (
        <p className={`small ${sale.voided || sale.fullyRefunded ? 'error' : ''}`}>{notice}</p>
      ) : null}
      {sale.cardPayments.length > 0 ? (
        <ul className="small sale-cards" aria-label="Card payments">
          {sale.cardPayments.map((card, index) => (
            <li key={card.poiTransactionId}>
              {cardLine(index, card)}
              {card.refunded ? ' · refunded' : ''}
            </li>
          ))}
        </ul>
      ) : null}
      {mode === 'FULL' && sale.cardPayments.length > 1 ? (
        <p className="small">Reverses all {sale.cardPayments.length} card payments, newest first</p>
      ) : null}
      <hr />
      <div className="flow-row tight">
        <LabeledRadio
          name={`mode-${sale.id}`}
          label="Full amount"
          selected={mode === 'FULL'}
          enabled={refundable && fullAvailable}
          onClick={() => setMode('FULL')}
        />
        <LabeledRadio
          name={`mode-${sale.id}`}
          label="Selected items"
          selected={mode === 'ITEMS'}
          enabled={refundable && itemsAvailable}
          onClick={() => setMode('ITEMS')}
        />
      </div>
      {hasRecordedItems ? (
        <>
          {sale.hasGiftCardPurchase ? (
            <p className="small">
              Contains a gift card purchase — refund the full sale to reverse the load and its
              funding together
            </p>
          ) : null}
          <ul className="lines scroll grow">
            {sale.items.map((item) => {
              const refunded = item.quantity - item.remainingQuantity;
              const description =
                refunded > 0
                  ? `${item.description} (${refunded} of ${item.quantity} refunded)`
                  : item.description;
              return (
                <LineItemRow
                  key={item.sku}
                  quantity={item.remainingQuantity}
                  description={description}
                  amountLabel={refundLabel(item)}
                  trailing={
                    <input
                      type="checkbox"
                      aria-label={`Return ${item.description}`}
                      checked={selectedSkus.has(item.sku)}
                      disabled={
                        !itemsAvailable ||
                        mode !== 'ITEMS' ||
                        !refundable ||
                        item.remainingQuantity <= 0
                      }
                      onChange={(event) => {
                        const next = new Set(selectedSkus);
                        if (event.target.checked) next.add(item.sku);
                        else next.delete(item.sku);
                        setSelectedSkus(next);
                      }}
                    />
                  }
                />
              );
            })}
          </ul>
        </>
      ) : (
        <p className="small grow">
          No line items recorded for this sale — only a full refund is possible
        </p>
      )}
      {mode === 'ITEMS' && refundable && sale.cardPayments.length > 1 ? (
        <fieldset className="card-picker" aria-label="Return to">
          <legend className="small">Return to:</legend>
          {sale.cardPayments.map((card, index) => (
            <LabeledRadio
              key={card.poiTransactionId}
              name={`card-${sale.id}`}
              label={`${cardLine(index, card)}${
                card.remainingMinor === null ? '' : ` · up to $${formatMinor(card.remainingMinor)}`
              }`}
              selected={cardChoice?.poiTransactionId === card.poiTransactionId}
              enabled={refundable && hasCapacity(card)}
              onClick={() => setPickedCard(card.poiTransactionId)}
            />
          ))}
        </fieldset>
      ) : null}
      <hr />
      {guard ? <p className="small">{guard}</p> : null}
      <button
        type="button"
        className="wide"
        disabled={!enabled}
        onClick={() => {
          if (mode === 'FULL') {
            controller.refundSale(sale.id);
          } else {
            controller.addReturnToBasket(
              sale.id,
              selectedSkus,
              sale.cardPayments.length > 1 ? cardChoice?.poiTransactionId : undefined,
            );
            setSelectedSkus(new Set());
          }
        }}
      >
        {mode === 'FULL' && state.refundInProgress
          ? 'Refunding…'
          : mode === 'FULL'
            ? `Refund $${amount}`
            : `Add return to basket ($${amount})`}
      </button>
    </section>
  );
}

/** The Refund tab: the stored sales and, for the selected one, what a refund returns. */
export function RefundTab(): ReactNode {
  const state = useEmulatorState();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected = state.sales.find((sale) => sale.id === selectedId) ?? state.sales[0] ?? null;
  return (
    <div className="refund-tab tab-content-row">
      <SalesListCard
        sales={state.sales}
        selectedId={selected?.id ?? null}
        onSelect={setSelectedId}
      />
      {selected ? <RefundDetailsCard key={selected.id} sale={selected} /> : null}
    </div>
  );
}
