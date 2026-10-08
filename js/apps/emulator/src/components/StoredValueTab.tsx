import { useEffect, useState, type ReactNode } from 'react';
import { useController, useEmulatorState } from '../emulator/context';
import { canOperateTerminal, sessionOperationInProgress } from '../emulator/state';
import { positiveMoneyMinor } from '../money';
import { TabRow } from './common';

type StoredValueAction = 'balance' | 'activation' | 'purchase';

const ACTIONS = [
  { id: 'balance', label: 'Balance inquiry' },
  { id: 'activation', label: 'Activation' },
  { id: 'purchase', label: 'Purchase' },
] as const;

const DESCRIPTIONS: Readonly<Record<StoredValueAction, string>> = {
  balance: 'Query the available balance without changing the card.',
  activation: 'Activate a new card with a zero starting balance.',
  purchase: 'Add a card purchase to the basket; loading runs before payment.',
};

/** The Stored Value tab: balance inquiry, activation, and a gift-card purchase into the basket. */
export function StoredValueTab(): ReactNode {
  const controller = useController();
  const state = useEmulatorState();
  const [action, setAction] = useState<StoredValueAction>('balance');
  const [cardNumber, setCardNumber] = useState('');
  const [amount, setAmount] = useState('');
  const [consumedRead, setConsumedRead] = useState(0);
  const read = state.acquiredCard;
  useEffect(() => {
    if (read && read.sequence > consumedRead) {
      setConsumedRead(read.sequence);
      setCardNumber(read.number);
    }
  }, [read, consumedRead]);

  const busy = sessionOperationInProgress(state);
  const enabled =
    action === 'purchase'
      ? positiveMoneyMinor(amount) !== null && canOperateTerminal(state)
      : canOperateTerminal(state);
  const run = () => {
    if (action === 'balance') controller.inquireStoredValueBalance(cardNumber);
    else if (action === 'activation') controller.activateStoredValue(cardNumber);
    else {
      controller.addGiftCardPurchase(amount, cardNumber);
      setAmount('');
      setCardNumber('');
    }
  };

  return (
    <section className="card tab-content" aria-label="Stored value">
      <h2>Stored value</h2>
      <TabRow tabs={ACTIONS} selected={action} onSelect={setAction} label="Stored value actions" />
      <p className="small muted">{DESCRIPTIONS[action]}</p>
      <div className="flow-row tight">
        <input
          className="compact grow"
          aria-label="Card number"
          placeholder={
            action === 'purchase'
              ? 'Card number (blank = read at settlement)'
              : 'Card number (blank = read on terminal)'
          }
          value={cardNumber}
          disabled={busy}
          onChange={(event) => setCardNumber(event.target.value)}
        />
        <button
          type="button"
          disabled={!canOperateTerminal(state)}
          onClick={() => controller.acquireCard()}
        >
          {state.cardReadInProgress ? 'Reading…' : 'Read card'}
        </button>
      </div>
      {action === 'purchase' ? (
        <input
          className="compact wide"
          aria-label="Purchase amount"
          placeholder="Purchase amount (for example 25.00)"
          value={amount}
          disabled={busy}
          onChange={(event) => setAmount(event.target.value)}
        />
      ) : null}
      {state.sessionId === null ? (
        <p className="small muted">Start Checkout to use stored value operations</p>
      ) : null}
      <span className="grow" />
      <button type="button" className="wide" disabled={!enabled} onClick={run}>
        {state.storedValueInProgress
          ? 'Working…'
          : action === 'balance'
            ? 'Check balance'
            : action === 'activation'
              ? 'Activate card'
              : 'Add purchase to basket'}
      </button>
    </section>
  );
}
