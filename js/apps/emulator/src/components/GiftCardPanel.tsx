import { useOperation } from '@bilt/pos-react';
import type { Operation, StoredValueBalance, StoredValueOperationResult } from '@bilt/pos-sdk';
import { useState, type ReactNode } from 'react';
import { describeCard, storedValueCard, type StoredValueLoadType } from '../gift-cards';
import { useLane } from '../lane/LaneProvider';
import { describeError, track } from '../log';
import { formatMoney, parseMoney } from '../money';

/**
 * Stored value, both sides of it: selling a gift card (a referenced line now, the activation or
 * reload as a settlement-time fulfilment) and paying with one (the split-tender card the
 * settlement charges first), plus a balance inquiry and a direct activation outside any sale.
 */
export function GiftCardPanel({ locked }: { readonly locked: boolean }): ReactNode {
  const {
    terminalSession,
    settings,
    addGiftCard,
    pendingGiftCards,
    tenderCard,
    setTenderCard,
    report,
    log,
  } = useLane();
  const [purchaseAmount, setPurchaseAmount] = useState('25.00');
  const [purchaseCard, setPurchaseCard] = useState('6006491260550218157');
  const [loadType, setLoadType] = useState<StoredValueLoadType>('ACTIVATE');
  const [tenderNumber, setTenderNumber] = useState('6006491260550218157');
  const [balanceOp, setBalanceOp] = useState<Operation<StoredValueBalance> | null>(null);
  const [activateOp, setActivateOp] = useState<Operation<StoredValueOperationResult> | null>(null);
  const balance = useOperation(balanceOp);
  const activation = useOperation(activateOp);
  const fmt = (value: string | undefined) => formatMoney(value, settings.currency);

  if (!terminalSession) {
    return (
      <section className="panel">
        <h2>Gift cards</h2>
        <p className="muted small">Stored value needs a terminal session.</p>
      </section>
    );
  }

  const inquire = () =>
    setBalanceOp(
      track(
        log,
        'storedValueBalance',
        terminalSession.storedValueBalance(storedValueCard(tenderNumber)),
      ) as Operation<StoredValueBalance>,
    );
  const activateEmpty = () =>
    setActivateOp(
      track(
        log,
        'storedValueActivate',
        terminalSession.storedValueActivate(storedValueCard(purchaseCard), '0'),
      ) as Operation<StoredValueOperationResult>,
    );

  return (
    <section className="panel">
      <h2>Gift cards</h2>
      <h3>Sell one</h3>
      <p className="muted small">
        A referenced <code>GIFT-CARD</code> line now; the terminal activates or reloads the card at
        settlement (<code>fulfillments</code>), after funding and before the charge.
      </p>
      <form
        className="inline"
        onSubmit={(event) => {
          event.preventDefault();
          const amount = parseMoney(purchaseAmount);
          if (amount) addGiftCard(amount, purchaseCard, loadType).catch(report);
        }}
      >
        <input
          aria-label="Gift card face value"
          inputMode="decimal"
          value={purchaseAmount}
          onChange={(event) => setPurchaseAmount(event.target.value)}
          disabled={locked}
        />
        <input
          aria-label="Gift card number to load"
          placeholder="Card number (blank: read on terminal)"
          value={purchaseCard}
          onChange={(event) => setPurchaseCard(event.target.value)}
          disabled={locked}
        />
        <select
          aria-label="Fulfilment"
          value={loadType}
          onChange={(event) => setLoadType(event.target.value as StoredValueLoadType)}
          disabled={locked}
        >
          <option value="ACTIVATE">activate</option>
          <option value="RELOAD">reload</option>
        </select>
        <button type="submit" disabled={locked || parseMoney(purchaseAmount) === undefined}>
          Add gift card line
        </button>
      </form>
      {pendingGiftCards.length > 0 ? (
        <ul className="ledger">
          {pendingGiftCards.map((gift) => (
            <li key={gift.reference}>
              {gift.type.toLowerCase()} {fmt(gift.amount)} on {describeCard(gift.card)} (
              <code>{gift.reference}</code>)
            </li>
          ))}
        </ul>
      ) : null}
      <div className="actions">
        <button
          type="button"
          className="secondary"
          disabled={activation.pending}
          onClick={activateEmpty}
        >
          {activation.pending ? 'Activating…' : 'Activate with zero balance (no sale)'}
        </button>
      </div>
      {activation.status === 'succeeded' && activation.result ? (
        <p className="small">
          Activated: txn {activation.result.poiTransactionId ?? '—'}
          {activation.result.currentBalance
            ? ` · balance ${fmt(activation.result.currentBalance)}`
            : ''}
        </p>
      ) : null}
      {activation.error ? <p className="small error">{describeError(activation.error)}</p> : null}

      <h3>Pay with one</h3>
      <p className="muted small">
        <code>setStoredValueCard</code> before Pay: the settlement charges the card first and the
        remainder goes to the card payment. Dropped by a basket clear.
      </p>
      <div className="inline">
        <input
          aria-label="Tender card number"
          placeholder="Card number (blank: swipe on terminal)"
          value={tenderNumber}
          onChange={(event) => setTenderNumber(event.target.value)}
          disabled={locked}
        />
        <button
          type="button"
          disabled={locked}
          onClick={() => setTenderCard(tenderNumber).catch(report)}
        >
          Use as tender
        </button>
        <button
          type="button"
          className="secondary"
          disabled={locked || tenderCard === null}
          onClick={() => setTenderCard(null).catch(report)}
        >
          Clear tender
        </button>
        <button type="button" className="secondary" disabled={balance.pending} onClick={inquire}>
          {balance.pending ? 'Asking…' : 'Balance'}
        </button>
      </div>
      <p className="small" data-testid="tender-card">
        Tender: {tenderCard ? describeCard(tenderCard) : 'none (card payment only)'}
      </p>
      {balance.status === 'succeeded' && balance.result ? (
        <p className="small">
          Balance: {formatMoney(balance.result.balance, balance.result.currency)}
        </p>
      ) : null}
      {balance.error ? <p className="small error">{describeError(balance.error)}</p> : null}
    </section>
  );
}
