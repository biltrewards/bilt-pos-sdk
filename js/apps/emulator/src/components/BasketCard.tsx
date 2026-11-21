import { useEffect, useState, type ReactNode } from 'react';
import { useController, useEmulatorState } from '../emulator/context';
import { canOperateTerminal, sessionOperationInProgress, type BasketLine } from '../emulator/state';
import { cents, formatMinor, nonNegativeMoneyMinor, plannedAmountMinor } from '../money';
import { NJ_SALES_TAX_RATE } from '../tax';
import { Dialog, LabeledCheckbox, LineItemRow } from './common';

type AdjustmentKind = 'CREDIT' | 'DISCOUNT';

interface BasketAdjustment {
  readonly line: BasketLine;
  readonly kind: AdjustmentKind;
}

const TAX_PERCENT = `${(Number(NJ_SALES_TAX_RATE) * 100).toFixed(3).replace(/0+$/, '')}%`;

function describeLine(line: BasketLine, editing: boolean): string {
  let text = line.description;
  if (line.type === 'SALE' && line.giftCard) text += ' (stored value purchase)';
  if (line.type === 'RETURN') text += ' (return)';
  if (line.type === 'CREDIT') text += ' (credit)';
  if (editing) text += ' — editing';
  if (line.discountLabels.length > 0) {
    text += ` · ${line.discountLabels.join(', ')} −$${line.discountTotal}`;
  }
  return text;
}

function BasketAdjustmentDialog({
  adjustment,
  onDismiss,
}: {
  adjustment: BasketAdjustment;
  onDismiss: () => void;
}): ReactNode {
  const controller = useController();
  const state = useEmulatorState();
  const { line } = adjustment;
  const discount = adjustment.kind === 'DISCOUNT';
  const [amount, setAmount] = useState(
    discount && line.discountTotal !== '0.00' ? line.discountTotal.replace(/^-/, '') : '',
  );
  const [label, setLabel] = useState(
    discount ? (line.discountLabels[0] ?? '') : `Credit for ${line.description}`,
  );
  const entered = nonNegativeMoneyMinor(amount);
  const maximum = nonNegativeMoneyMinor(discount ? line.originalTotal : line.lineTotal) ?? 0;
  const valid = entered !== null && entered <= maximum && (discount || entered > 0);
  const apply = () => {
    if (discount) controller.applyDiscount(line.itemId, amount, label);
    else controller.applyCredit(line.itemId, amount, label);
    onDismiss();
  };
  return (
    <Dialog
      title={discount ? 'Apply line discount' : 'Apply item credit'}
      onDismiss={onDismiss}
      actions={
        <>
          <button type="button" className="text" onClick={onDismiss}>
            Cancel
          </button>
          <button
            type="button"
            className="text"
            disabled={!valid || sessionOperationInProgress(state)}
            onClick={apply}
          >
            {discount && entered === 0 ? 'Clear' : 'Apply'}
          </button>
        </>
      }
    >
      <p className="small">
        {line.quantity}× {line.description} · maximum ${formatMinor(maximum)}
      </p>
      <input
        className="compact wide"
        aria-label={discount ? 'Amount (0 clears)' : 'Credit amount'}
        placeholder={discount ? 'Amount (0 clears)' : 'Credit amount'}
        value={amount}
        disabled={state.paymentInProgress}
        onChange={(event) => setAmount(event.target.value)}
        autoFocus
      />
      <input
        className="compact wide"
        aria-label="Receipt label"
        placeholder="Receipt label"
        value={label}
        disabled={state.paymentInProgress}
        onChange={(event) => setLabel(event.target.value)}
      />
    </Dialog>
  );
}

/**
 * The planned card payments of a split sale: one amount field per fixed payment, + Add card, and
 * the read-only last payment that takes whatever the basket total leaves. The total is the
 * basket's; the host checks the amounts again once rebates, points and the gift card have
 * reduced what the card owes.
 */
function SplitCardControls({
  amounts,
  total,
  disabled,
  onChange,
}: {
  amounts: readonly string[];
  total: string;
  disabled: boolean;
  onChange: (amounts: string[]) => void;
}): ReactNode {
  const valid = amounts.every((amount) => plannedAmountMinor(amount) !== null);
  const fixed = amounts.reduce((sum, amount) => sum + (plannedAmountMinor(amount) ?? 0), 0);
  const remainder = Math.max(0, cents(total) - fixed);
  return (
    <div className="split-card" aria-label="Split card payments">
      {amounts.map((amount, index) => (
        <div key={index} className="flow-row tight">
          <label className="check">
            Card {index + 1} $
            <input
              className="compact"
              aria-label={`Card ${index + 1} amount`}
              placeholder="0.00"
              inputMode="decimal"
              value={amount}
              disabled={disabled}
              onChange={(event) =>
                onChange(amounts.map((entry, i) => (i === index ? event.target.value : entry)))
              }
            />
          </label>
          {amounts.length > 1 ? (
            <button
              type="button"
              className="text"
              aria-label={`Remove card ${index + 1}`}
              disabled={disabled}
              onClick={() => onChange(amounts.filter((_, i) => i !== index))}
            >
              Remove
            </button>
          ) : null}
        </div>
      ))}
      <div className="flow-row tight">
        <button
          type="button"
          className="text"
          disabled={disabled}
          onClick={() => onChange([...amounts, ''])}
        >
          + Add card
        </button>
        <span className="small" data-testid="card-remainder">
          Card {amounts.length + 1}: remainder ${formatMinor(remainder)}
        </span>
      </div>
      {!valid ? (
        <p className="small error">Enter a positive amount for every card</p>
      ) : cents(total) - fixed <= 0 ? (
        <p className="small error">
          The planned cards reach the total: the settlement will be refused
        </p>
      ) : null}
    </div>
  );
}

/**
 * The toggles for the next payment and the Settle button. The loyalty steps run only for an
 * identified member; the gift card is charged first and the remainder goes to the card; net
 * settlement moves only the signed difference of a mixed basket.
 */
function PaymentControls(): ReactNode {
  const controller = useController();
  const state = useEmulatorState();
  const [implicit, setImplicit] = useState(true);
  const [rebates, setRebates] = useState(false);
  const [redemption, setRedemption] = useState(false);
  const [award, setAward] = useState(false);
  const [giftCard, setGiftCard] = useState(false);
  const [netSettlement, setNetSettlement] = useState(true);
  const [splitCard, setSplitCard] = useState(false);
  const [plannedAmounts, setPlannedAmounts] = useState<string[]>(['']);
  const [partialApprovals, setPartialApprovals] = useState(true);
  const [giftCardNumber, setGiftCardNumber] = useState('');
  const [consumedRead, setConsumedRead] = useState(0);
  const read = state.acquiredCard;
  useEffect(() => {
    if (giftCard && read && read.sequence > consumedRead) {
      setConsumedRead(read.sequence);
      setGiftCardNumber(read.number);
    }
  }, [giftCard, read, consumedRead]);

  const paid = state.lastPayment !== null;
  const splitValid = !splitCard || plannedAmounts.every((a) => plannedAmountMinor(a) !== null);
  const canPay = canOperateTerminal(state) && state.basket.length > 0 && !paid && splitValid;
  const busy = state.paymentInProgress;
  const label = busy
    ? 'Settling…'
    : paid
      ? 'Settled'
      : state.basketTotal.startsWith('-')
        ? `Settle (refund $${state.basketTotal.slice(1)})`
        : `Settle $${state.basketTotal}`;

  return (
    <div className="payment-controls">
      <div className="flow-row tight">
        {/* implicit: the terminal handles all loyalty in the card payment, so no SDK steps */}
        <LabeledCheckbox
          label="Implicit Loyalty"
          checked={implicit}
          disabled={busy}
          onChange={(checked) => {
            setImplicit(checked);
            // planned card payments cannot ride with implicit loyalty
            if (checked) setSplitCard(false);
          }}
        />
        <LabeledCheckbox
          label="Rebates"
          checked={rebates}
          disabled={busy || implicit}
          onChange={setRebates}
        />
        <LabeledCheckbox
          label="Redemption"
          checked={redemption}
          disabled={busy || implicit}
          onChange={setRedemption}
        />
        <LabeledCheckbox
          label="Award"
          checked={award}
          disabled={busy || implicit}
          onChange={setAward}
        />
        <LabeledCheckbox
          label="Gift card"
          checked={giftCard}
          disabled={busy}
          onChange={setGiftCard}
        />
        <LabeledCheckbox
          label="Split card"
          checked={splitCard}
          disabled={busy || implicit}
          onChange={setSplitCard}
        />
        <LabeledCheckbox
          label="Partial approvals"
          checked={partialApprovals}
          disabled={busy}
          onChange={setPartialApprovals}
        />
        <LabeledCheckbox
          label="Net settlement"
          checked={netSettlement}
          disabled={busy}
          onChange={setNetSettlement}
        />
      </div>
      {giftCard ? (
        <div className="flow-row tight">
          <input
            className="compact grow"
            aria-label="Gift card number"
            placeholder="Gift card number (blank = swipe on terminal)"
            value={giftCardNumber}
            disabled={busy}
            onChange={(event) => setGiftCardNumber(event.target.value)}
          />
          <button
            type="button"
            disabled={!canOperateTerminal(state)}
            onClick={() => controller.acquireCard()}
          >
            Read card
          </button>
        </div>
      ) : null}
      {implicit ? (
        <p className="small" data-testid="split-card-caption">
          Split card needs explicit loyalty: clear Implicit Loyalty first
        </p>
      ) : null}
      {splitCard ? (
        <SplitCardControls
          amounts={plannedAmounts}
          total={state.basketTotal}
          disabled={busy}
          onChange={setPlannedAmounts}
        />
      ) : null}
      {paid ? (
        <p className="small success" data-testid="last-payment">
          {state.lastPayment} —{' '}
          {state.sessionId === null
            ? 'Start Checkout for the next customer'
            : 'End Checkout to start the next one'}
        </p>
      ) : null}
      <button
        type="button"
        className="wide"
        disabled={!canPay}
        onClick={() =>
          controller.settle(
            { implicit, rebates, redemption, award },
            giftCard ? { cardNumber: giftCardNumber } : null,
            netSettlement,
            { plannedAmounts: splitCard ? plannedAmounts : [], partialApprovals },
          )
        }
      >
        {label}
      </button>
    </div>
  );
}

/**
 * The shared basket, above every tab's content. Custom (keypad) lines are tappable: the tap
 * hands the line's SKU and price to the keypad, and `editingSku` marks the line it re-prices.
 */
export function BasketCard({
  editingSku,
  onEditCustomLine,
}: {
  editingSku: string | null;
  onEditCustomLine: (sku: string, priceMinor: number) => void;
}): ReactNode {
  const state = useEmulatorState();
  const [adjustment, setAdjustment] = useState<BasketAdjustment | null>(null);
  const adjustable = state.sessionId !== null && !state.paymentInProgress;
  return (
    <section className="card basket" aria-label="Basket">
      {adjustment ? (
        <BasketAdjustmentDialog adjustment={adjustment} onDismiss={() => setAdjustment(null)} />
      ) : null}
      <h2 data-testid="basket-total">Basket — total ${state.basketTotal}</h2>
      {state.basketTax !== '0.00' ? (
        <p className="small">
          incl. ${state.basketTax} tax (NJ {TAX_PERCENT})
        </p>
      ) : null}
      <hr />
      <div className="scroll grow">
        {state.basket.length === 0 ? (
          <p className="small">Empty</p>
        ) : (
          <ul className="lines">
            {state.basket.map((line) => {
              const editable = line.editablePriceMinor;
              const editing = editable !== null && line.sku === editingSku;
              return (
                <LineItemRow
                  key={line.itemId}
                  quantity={line.quantity}
                  description={describeLine(line, editing)}
                  amountLabel={`$${line.lineTotal}`}
                  className={editing ? 'editing' : ''}
                  {...(editable !== null
                    ? { onClick: () => onEditCustomLine(line.sku, editable) }
                    : {})}
                  trailing={
                    line.type === 'SALE' ? (
                      <span className="line-actions">
                        <button
                          type="button"
                          className="text"
                          disabled={!adjustable}
                          aria-label={`Discount ${line.description}`}
                          onClick={() => setAdjustment({ line, kind: 'DISCOUNT' })}
                        >
                          Discount
                        </button>
                        <button
                          type="button"
                          className="text"
                          disabled={!adjustable}
                          aria-label={`Credit ${line.description}`}
                          onClick={() => setAdjustment({ line, kind: 'CREDIT' })}
                        >
                          Credit
                        </button>
                      </span>
                    ) : null
                  }
                />
              );
            })}
          </ul>
        )}
      </div>
      <hr />
      <PaymentControls />
    </section>
  );
}
