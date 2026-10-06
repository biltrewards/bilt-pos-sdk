import type { PendingRecoveryStep, UseOperationResult, UseSettlementResult } from '@bilt/pos-react';
import { SessionError, type Receipt, type SettlementResult, type VoidResult } from '@bilt/pos-sdk';
import { useEffect, useState, type ReactNode } from 'react';
import { formatMoney } from '../money';
import { describeError } from './Toasts';

export interface SettlementPanelProps {
  readonly settlement: UseSettlementResult;
  readonly currency: string;
  readonly total: string | null;
  readonly canPay: boolean;
  readonly onPay: () => void;
  readonly onVoid: () => void;
  readonly voiding: UseOperationResult<VoidResult>;
  readonly onNextShopper: () => void;
}

function secondsLeft(deadline: Date): number {
  return Math.max(0, Math.ceil((deadline.getTime() - Date.now()) / 1000));
}

/**
 * A charge-side failure waiting for the register. The host applies the default (`ABORT`) at
 * the deadline, so the prompt counts down; `SKIP` and an external tender are only valid when
 * the terminal outcome is known.
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
  const [left, setLeft] = useState(() => secondsLeft(step.deadline));
  useEffect(() => {
    const timer = setInterval(() => setLeft(secondsLeft(step.deadline)), 500);
    return () => clearInterval(timer);
  }, [step.deadline]);
  const indeterminate = step.failure.outcomeCertainty === 'INDETERMINATE';

  return (
    <div className="recovery" role="alertdialog" aria-label="Payment step failed">
      <h3>
        {step.failure.step ?? 'Settlement'} failed: {step.failure.error.code}
      </h3>
      <p>{step.failure.error.message}</p>
      <p className="small">
        {formatMoney(step.failure.amountDue, currency)} still due ·{' '}
        {step.failure.committedMovements.length} movement(s) committed so far · outcome{' '}
        {step.failure.outcomeCertainty.toLowerCase()} · default (abort) in {left}s
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
    </div>
  );
}

function ReceiptView({
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
    <dl className="facts">
      <dt>Charged to card</dt>
      <dd>
        {fmt(result.cardAmountCharged)}
        {result.paymentBrand ? ` · ${result.paymentBrand}` : ''}
        {result.approvalCode ? ` · approval ${result.approvalCode}` : ''}
      </dd>
      {result.totalRebateAmount !== '0.00' ? (
        <>
          <dt>Rebates</dt>
          <dd>{fmt(result.totalRebateAmount)}</dd>
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
      {result.storedValueAmountUsed !== '0.00' ? (
        <>
          <dt>Gift card</dt>
          <dd>{fmt(result.storedValueAmountUsed)}</dd>
        </>
      ) : null}
      <dt>Points earned</dt>
      <dd>
        {result.totalPointsEarned} · balance {result.pointsBalance}
      </dd>
      <dt>Movements</dt>
      <dd>
        {result.movements.map((movement) => `${movement.step} ${fmt(movement.amount)}`).join(', ')}
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
export function SettlementPanel(props: SettlementPanelProps): ReactNode {
  const { settlement, currency, total, canPay, voiding } = props;
  const running = settlement.status === 'running' || settlement.status === 'awaitingReply';

  return (
    <section className="panel settlement">
      <div className="panel-header">
        <h2>Settle</h2>
        <span className="badge">{settlement.status}</span>
      </div>

      {settlement.status === 'idle' ? (
        <button type="button" className="pay" disabled={!canPay} onClick={props.onPay}>
          Pay {total ? formatMoney(total, currency) : ''}
        </button>
      ) : null}

      {running ? (
        <>
          <p>
            {settlement.status === 'awaitingReply'
              ? 'Waiting for the cashier.'
              : 'The host is working through the settlement sequence…'}
          </p>
          <button
            type="button"
            className="secondary"
            onClick={() => settlement.abort().catch(() => undefined)}
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
        <ul className="small">
          {settlement.movements.map((movement, index) => (
            <li key={index}>
              {movement.step} {formatMoney(movement.amount, currency)}
            </li>
          ))}
        </ul>
      ) : null}

      {settlement.status === 'succeeded' && settlement.result ? (
        <>
          <ResultSummary result={settlement.result} currency={currency} />
          <ReceiptView title="Customer receipt" receipt={settlement.result.customerReceipt} />
          <ReceiptView title="Merchant receipt" receipt={settlement.result.merchantReceipt} />
          <div className="actions">
            <button type="button" disabled={voiding.pending} onClick={props.onNextShopper}>
              Next shopper
            </button>
            <button
              type="button"
              className="danger"
              disabled={voiding.pending || voiding.status === 'succeeded'}
              onClick={props.onVoid}
            >
              {voiding.pending ? 'Voiding…' : voiding.status === 'succeeded' ? 'Voided' : 'Void'}
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
