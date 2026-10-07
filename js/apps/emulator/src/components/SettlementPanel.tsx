import { useOperation } from '@bilt/pos-react';
import type { PendingRecoveryStep, UseSettlementResult } from '@bilt/pos-react';
import {
  SessionError,
  type Operation,
  type Receipt,
  type SettlementResult,
  type VoidResult,
} from '@bilt/pos-sdk';
import { useState, type ReactNode } from 'react';
import { DEFAULT_PAY_OPTIONS, useLane, type PayOptions } from '../lane/LaneProvider';
import { describeError, track } from '../log';
import { cents, formatMoney } from '../money';
import { reversalProgress, voidRecordFrom } from '../store/reversals';
import { Countdown } from './Countdown';

/**
 * A charge-side failure waiting for the register. The host applies the default (`ABORT`) at
 * the deadline, so the prompt counts down; `SKIP` and an external tender are only valid when
 * the terminal outcome is known, and the external tender replaces only a failed card charge and
 * covers exactly the amount due.
 */
function RecoveryPrompt({
  step,
  currency,
  reply,
}: {
  step: PendingRecoveryStep;
  currency: string;
  reply: UseSettlementResult['reply'];
}): ReactNode {
  const indeterminate = step.failure.outcomeCertainty === 'INDETERMINATE';
  // The SDK takes an external tender only in place of the final card charge, and only for the
  // amount due exactly; offering it elsewhere, or for another amount, just fails the settlement.
  const cashAllowed = step.failure.step === 'CARD_CHARGE';

  return (
    <div className="prompt" role="alertdialog" aria-label="Payment step failed">
      <h3>
        {step.failure.step ?? 'Settlement'} failed: {step.failure.error.code}
      </h3>
      <p>{step.failure.error.message}</p>
      <p className="small">
        {formatMoney(step.failure.amountDue, currency)} still due ·{' '}
        {step.failure.committedMovements.length} movement(s) committed so far · outcome{' '}
        {step.failure.outcomeCertainty.toLowerCase()} · default (abort) in{' '}
        <Countdown deadline={step.deadline} />
      </p>
      <div className="actions">
        <button type="button" onClick={() => reply({ recovery: 'RETRY' })}>
          Retry the step
        </button>
        <button
          type="button"
          className="secondary"
          disabled={indeterminate}
          title={indeterminate ? 'Not allowed while the terminal outcome is unknown' : undefined}
          onClick={() => reply({ recovery: 'SKIP' })}
        >
          Skip it
        </button>
        <button type="button" className="secondary" onClick={() => reply({ recovery: 'ABORT' })}>
          Abort and unwind
        </button>
        <button
          type="button"
          className="danger"
          onClick={() => reply({ recovery: 'ABANDON' })}
          title="Leaves the committed movements standing; the register reconciles them"
        >
          Abandon to the register
        </button>
        <button type="button" className="link" onClick={step.useDefault}>
          Use the default
        </button>
      </div>
      {cashAllowed ? (
        <div className="inline">
          <button
            type="button"
            className="secondary"
            disabled={indeterminate}
            title="Record cash for exactly the amount due and complete the sale"
            onClick={() =>
              reply({
                recovery: {
                  action: 'EXTERNAL',
                  externalPayment: { tenderType: 'CASH', amount: step.failure.amountDue },
                },
              })
            }
          >
            Paid {formatMoney(step.failure.amountDue, currency)} in cash
          </button>
        </div>
      ) : null}
    </div>
  );
}

export function ReceiptView({
  title,
  receipt,
}: {
  title: string;
  receipt: Receipt | undefined;
}): ReactNode {
  if (!receipt) return null;
  if (receipt.plainText) {
    return (
      <details>
        <summary>{title}</summary>
        <pre className="receipt">{receipt.plainText}</pre>
      </details>
    );
  }
  if (receipt.html) {
    return (
      <details>
        <summary>{title}</summary>
        <iframe className="receipt" title={title} sandbox="" srcDoc={receipt.html} />
      </details>
    );
  }
  return null;
}

function ResultSummary({ result, currency }: { result: SettlementResult; currency: string }) {
  const fmt = (value: string) => formatMoney(value, currency);
  return (
    <dl className="facts" data-testid="settlement-result">
      <dt>Authorized</dt>
      <dd>{fmt(result.authorizedAmount)}</dd>
      <dt>Charged to card</dt>
      <dd>
        {fmt(result.cardAmountCharged)}
        {result.paymentBrand ? ` · ${result.paymentBrand}` : ''}
        {result.approvalCode ? ` · approval ${result.approvalCode}` : ''}
        {result.poiTransactionId ? ` · txn ${result.poiTransactionId}` : ''}
      </dd>
      {cents(result.storedValueAmountUsed) !== 0 ? (
        <>
          <dt>Gift card</dt>
          <dd>
            {fmt(result.storedValueAmountUsed)}
            {result.storedValuePoiTransactionId
              ? ` · txn ${result.storedValuePoiTransactionId}`
              : ''}
          </dd>
        </>
      ) : null}
      {cents(result.storedValueLoadedAmount) !== 0 ? (
        <>
          <dt>Gift cards loaded</dt>
          <dd>{fmt(result.storedValueLoadedAmount)}</dd>
        </>
      ) : null}
      {cents(result.externalPaymentAmount) !== 0 ? (
        <>
          <dt>Paid externally</dt>
          <dd>{fmt(result.externalPaymentAmount)}</dd>
        </>
      ) : null}
      {cents(result.totalRebateAmount) !== 0 ? (
        <>
          <dt>Rebates</dt>
          <dd>
            {fmt(result.totalRebateAmount)}
            {result.redeemedRebates.map(
              (r) => ` · ${r.label ?? r.promotionRef ?? ''} ${fmt(r.amount)}`,
            )}
          </dd>
        </>
      ) : null}
      {result.pointsRedeemed > 0 ? (
        <>
          <dt>Points redeemed</dt>
          <dd>
            {result.pointsRedeemed} ({fmt(result.pointsMonetaryValue)})
          </dd>
        </>
      ) : null}
      {cents(result.cardRefundedAmount) !== 0 || cents(result.storedValueRefundedAmount) !== 0 ? (
        <>
          <dt>Refunded</dt>
          <dd>
            card {fmt(result.cardRefundedAmount)} · gift card{' '}
            {fmt(result.storedValueRefundedAmount)}
          </dd>
        </>
      ) : null}
      <dt>Points earned</dt>
      <dd>
        {result.totalPointsEarned} · balance {result.pointsBalance}
      </dd>
      <dt>Ledger</dt>
      <dd>
        <ul className="movements">
          {result.movements.map((movement, index) => (
            <li key={index}>
              {movement.step} {fmt(movement.amount)}
              {movement.poiTransactionId ? ` · txn ${movement.poiTransactionId}` : ''}
              {movement.target.basketReference ? ` · ${movement.target.basketReference}` : ''}
            </li>
          ))}
        </ul>
      </dd>
      {result.promotionMessages.length > 0 ? (
        <>
          <dt>Promotions</dt>
          <dd>{result.promotionMessages.join(' · ')}</dd>
        </>
      ) : null}
      {result.warnings.length > 0 ? (
        <>
          <dt>Warnings</dt>
          <dd>{result.warnings.join(' · ')}</dd>
        </>
      ) : null}
    </dl>
  );
}

/** The tender side of the lane: pay, follow the settlement, answer a recovery step, show the outcome. */
export function SettlementPanel(): ReactNode {
  const {
    settlement,
    settings,
    basket,
    terminalSession,
    pay,
    pendingStep,
    nextShopper,
    report,
    log,
    lastSale,
    sales,
    reversal,
    abandoned,
    resolveAbandoned,
    pendingRefund,
  } = useLane();
  const [options, setOptions] = useState<PayOptions>(DEFAULT_PAY_OPTIONS);
  // The quick void belongs to the payment it voided: the panel stays mounted across shoppers, so
  // a later payment's result must not inherit an earlier "Voided".
  const [voidOp, setVoidOp] = useState<{
    readonly operation: Operation<VoidResult>;
    readonly of: SettlementResult;
  } | null>(null);
  const voiding = useOperation(voidOp && voidOp.of === settlement.result ? voidOp.operation : null);
  const voidPayment = () => {
    if (!terminalSession || !settlement.result) return;
    const operation = track(
      log,
      'voidTransaction',
      terminalSession.voidTransaction(),
    ) as Operation<VoidResult>;
    setVoidOp({ operation, of: settlement.result });
    // The sale is already in the store, so the void goes there too, as the Refunds tab's does:
    // otherwise that tab still offers a refund or void of a payment the terminal reversed.
    const stored = lastSale ? { sale: lastSale, refunds: [], voided: null } : null;
    if (!stored) return;
    operation.then(
      (result) => sales.recordVoid(voidRecordFrom(stored, result)).catch(report),
      async (error: unknown) => {
        if (error instanceof SessionError) {
          for (const record of reversalProgress(stored, error.reversedMovements)) {
            await sales.recordRefund(record).catch(report);
          }
        }
      },
    );
  };
  const currency = settings.currency;
  const total = basket.basket?.grandTotal ?? null;
  const running = settlement.status === 'running' || settlement.status === 'awaitingReply';
  // A Refunds-tab reversal also moves money on the session: no payment or void alongside it.
  const canPay =
    (basket.basket?.items.length ?? 0) > 0 &&
    reversal === null &&
    abandoned === null &&
    pendingRefund === null;
  const toggle = (key: keyof PayOptions) => (
    <label className="check" key={key}>
      <input
        type="checkbox"
        checked={options[key]}
        onChange={(event) => setOptions({ ...options, [key]: event.target.checked })}
        disabled={settlement.status !== 'idle'}
      />
      {key}
    </label>
  );

  if (!terminalSession) {
    return (
      <section className="panel">
        <h2>Settle</h2>
        <p className="muted">
          Settlement, prompts and the customer display need a terminal session. Switch the mode in
          the Settings tab to pay.
        </p>
      </section>
    );
  }

  return (
    <section className="panel settlement">
      <div className="panel-header">
        <h2>Settle</h2>
        <span className="badge" data-testid="settlement-status">
          {settlement.status}
          {settlement.operation ? ` · ${settlement.operation.status}` : ''}
        </span>
      </div>

      {abandoned ? (
        <div className="prompt" role="alertdialog" aria-label="Abandoned settlement">
          <h3>Settlement {abandoned.settlementId} abandoned to the register</h3>
          <p className="small">
            {formatMoney(abandoned.outstandingAmount, currency)} outstanding. These movements
            committed and stand; reconcile them before taking another payment, which could move them
            again:
          </p>
          <ul className="movements">
            {abandoned.committedMovements.map((movement, index) => (
              <li key={index}>
                {movement.step} {formatMoney(movement.amount, currency)}
                {movement.poiTransactionId ? ` · txn ${movement.poiTransactionId}` : ''}
              </li>
            ))}
          </ul>
          <button type="button" className="secondary" onClick={resolveAbandoned}>
            Mark reconciled
          </button>
        </div>
      ) : null}

      {pendingRefund ? (
        <p className="small" data-testid="refund-hold">
          The refund of sale <code>{pendingRefund.sale.sale.id}</code> is incomplete: its return
          line stays in the basket until the Refunds tab retries it.
        </p>
      ) : null}

      {settlement.status === 'idle' ? (
        <>
          <div className="inline">
            {(['rebates', 'redemption', 'award', 'net'] as const).map(toggle)}
          </div>
          <button type="button" className="pay" disabled={!canPay} onClick={() => pay(options)}>
            Pay {total ? formatMoney(total, currency) : ''}
          </button>
        </>
      ) : null}

      {running ? (
        <>
          <p>
            {settlement.status === 'awaitingReply'
              ? 'Waiting for the cashier.'
              : 'The host is working through the settlement sequence…'}
          </p>
          {pendingStep ? (
            <p className="small muted">
              Persisted before <code>{pendingStep.step}</code>: sale transaction{' '}
              <code>{pendingStep.saleTransactionId}</code>, total{' '}
              {formatMoney(pendingStep.currentTotal, currency)}
            </p>
          ) : null}
          <button
            type="button"
            className="secondary"
            onClick={() => settlement.abort().catch(report)}
          >
            Abort payment
          </button>
        </>
      ) : null}

      {settlement.pendingStep?.kind === 'RECOVERY_REQUIRED' ? (
        <RecoveryPrompt
          step={settlement.pendingStep}
          currency={currency}
          reply={settlement.reply}
        />
      ) : null}

      {settlement.movements.length > 0 && settlement.status !== 'succeeded' ? (
        <ul className="movements" aria-label="Movements so far">
          {settlement.movements.map((movement, index) => (
            <li key={index}>
              {movement.step} {formatMoney(movement.amount, currency)}
              {movement.poiTransactionId ? ` · txn ${movement.poiTransactionId}` : ''}
            </li>
          ))}
        </ul>
      ) : null}

      {settlement.status === 'succeeded' && settlement.result ? (
        <>
          <ResultSummary result={settlement.result} currency={currency} />
          {lastSale ? (
            <p className="small muted">
              Recorded as sale <code>{lastSale.id}</code> for the Refunds tab.
            </p>
          ) : null}
          <ReceiptView title="Customer receipt" receipt={settlement.result.customerReceipt} />
          <ReceiptView title="Merchant receipt" receipt={settlement.result.merchantReceipt} />
          <div className="actions">
            <button
              type="button"
              disabled={voiding.pending || reversal !== null}
              onClick={nextShopper}
            >
              Next shopper
            </button>
            <button
              type="button"
              className="danger"
              disabled={voiding.pending || voiding.status === 'succeeded' || reversal !== null}
              onClick={voidPayment}
              title="Reverses every movement this payment committed"
            >
              {voiding.pending
                ? 'Voiding…'
                : voiding.status === 'succeeded'
                  ? 'Voided'
                  : 'Void this payment'}
            </button>
          </div>
          {voiding.error ? <p className="error small">{describeError(voiding.error)}</p> : null}
        </>
      ) : null}

      {settlement.status === 'failed' || settlement.status === 'aborted' ? (
        <>
          <p className="error">{settlement.error ? describeError(settlement.error) : 'Failed'}</p>
          {settlement.error instanceof SessionError && settlement.error.abandonedSettlement ? (
            <p className="small">
              Abandoned: {settlement.error.abandonedSettlement.committedMovements.length} committed
              movement(s) are now the register&apos;s to reconcile.
            </p>
          ) : null}
          {settlement.error instanceof SessionError &&
          settlement.error.reversedMovements.length > 0 ? (
            <p className="small">
              Reversed so far:{' '}
              {settlement.error.reversedMovements
                .map((m) => `${m.step} ${m.poiTransactionId}`)
                .join(', ')}
            </p>
          ) : null}
          <div className="actions">
            <button type="button" onClick={settlement.reset}>
              Back to the basket
            </button>
          </div>
        </>
      ) : null}
    </section>
  );
}
