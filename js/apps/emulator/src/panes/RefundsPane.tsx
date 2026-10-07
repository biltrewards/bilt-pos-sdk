import {
  SessionError,
  type RefundResult,
  type ReversalDecision,
  type ReversalHandlers,
  type ReversalStep,
  type SettlementResult,
  type VoidResult,
} from '@bilt/pos-sdk';
import { useCallback, useRef, useState, type ReactNode } from 'react';
import { Countdown } from '../components/Countdown';
import { ReceiptView } from '../components/SettlementPanel';
import { useLane } from '../lane/LaneProvider';
import { describeError, track } from '../log';
import { cents, formatMoney, parseMoney } from '../money';
import {
  defaultReversalDecision,
  linkedRefundRecord,
  planReferencedRefund,
  refundRecordFrom,
  reversalProgress,
  voidRecordFrom,
} from '../store/reversals';
import {
  isPartiallyRefunded,
  isRefundable,
  isVoidable,
  originalSaleRecord,
  refundLeg,
  remainingLegAmount,
  type StoredSale,
} from '../store/sale-record';
import { useStoredSales } from '../store/sales-store';

/** A reversal step that failed, waiting for the cashier's decision within the host's deadline. */
interface ReversalPrompt {
  readonly step: ReversalStep | null;
  readonly error: SessionError;
  readonly deadline: Date;
  readonly decide: (decision: ReversalDecision) => void;
}

/** Where the last reversal stands, with its ledger. */
type Outcome =
  | { kind: 'void'; saleId: string; result: VoidResult }
  | { kind: 'refund'; saleId: string; result: SettlementResult }
  | { kind: 'linked'; saleId: string; result: RefundResult }
  | { kind: 'unlinked'; result: RefundResult }
  | { kind: 'error'; saleId?: string; error: unknown };

function ReversalDecisionPrompt({ prompt }: { prompt: ReversalPrompt }): ReactNode {
  const fallback = defaultReversalDecision(prompt.step);
  return (
    <div className="prompt" role="alertdialog" aria-label="Reversal step failed">
      <h3>
        {prompt.step ?? 'The reversal'} failed: {prompt.error.code}
      </h3>
      <p>{prompt.error.message}</p>
      <p className="small">
        Default ({fallback.toLowerCase()}) in <Countdown deadline={prompt.deadline} />
        {prompt.step === null ? ' · no step ran, the decision is informational' : ''}
      </p>
      <div className="actions">
        <button type="button" onClick={() => prompt.decide('RETRY')}>
          Retry the step
        </button>
        <button type="button" className="secondary" onClick={() => prompt.decide('SKIP')}>
          Skip it (leave the movement standing)
        </button>
        <button type="button" className="secondary" onClick={() => prompt.decide('ABORT')}>
          Abort (reversed legs stand)
        </button>
        <button type="button" className="link" onClick={() => prompt.decide(fallback)}>
          Use the default
        </button>
      </div>
    </div>
  );
}

function SaleCard({
  stored,
  currency,
  selected,
  onSelect,
}: {
  stored: StoredSale;
  currency: string;
  selected: boolean;
  onSelect: () => void;
}): ReactNode {
  const { sale } = stored;
  const status = stored.voided
    ? 'voided'
    : !isRefundable(stored)
      ? 'refunded'
      : stored.refunds.some((r) => !r.reversalProgress)
        ? 'partially refunded'
        : 'open';
  return (
    <div className={selected ? 'sale selected' : 'sale'} data-testid={`sale-${sale.id}`}>
      <div className="sale-header">
        <span>
          <strong>{formatMoney(sale.authorizedAmount, currency)}</strong> ·{' '}
          {new Date(sale.completedAt).toLocaleString()} ·{' '}
          {sale.memberId ? `member ${sale.memberId}` : 'guest'}
        </span>
        <span>
          <span className={status === 'open' ? 'badge' : 'badge warn'}>{status}</span>{' '}
          <button type="button" className="link" onClick={onSelect}>
            {selected ? 'selected' : 'select'}
          </button>
        </span>
      </div>
      <p className="small muted">
        <code>{sale.id}</code> · session {sale.sessionId} · {sale.items.length} item(s)
        {cents(sale.externalPaymentAmount) !== 0
          ? ` · cash ${formatMoney(sale.externalPaymentAmount, currency)} (refund manually)`
          : ''}
      </p>
      <ul className="ledger">
        {sale.legs.map((leg) => (
          <li key={`${leg.type}-${leg.poiTransactionId}`}>
            {leg.type} {leg.amount ? formatMoney(leg.amount, currency) : ''} · txn{' '}
            {leg.poiTransactionId}
            {leg.brand ? ` · ${leg.brand}` : ''}
            {leg.type === 'CARD' || leg.type === 'STORED_VALUE'
              ? ` · ${formatMoney(remainingLegAmount(stored, leg.type), currency)} left`
              : ''}
          </li>
        ))}
        {sale.giftCardLoads.map((load) => (
          <li key={load.poiTransactionId}>
            gift card load {formatMoney(load.amount, currency)} on {load.basketReference} · txn{' '}
            {load.poiTransactionId}
          </li>
        ))}
        {stored.refunds.map((refund, index) => (
          <li key={index}>
            {refund.reversalProgress ? 'reversed' : 'refund'}{' '}
            {refund.amount ? formatMoney(refund.amount, currency) : ''}
            {refund.leg ? ` from ${refund.leg}` : ''}
            {refund.full ? ' (full)' : ''}
            {refund.awardReversed ? ' · award reversed' : ''}
            {refund.poiTransactionId ? ` · txn ${refund.poiTransactionId}` : ''} ·{' '}
            {new Date(refund.recordedAt).toLocaleTimeString()}
          </li>
        ))}
        {stored.voided ? (
          <li>
            voided{stored.voided.poiTransactionId ? ` · txn ${stored.voided.poiTransactionId}` : ''}{' '}
            · {new Date(stored.voided.recordedAt).toLocaleTimeString()}
          </li>
        ) : null}
      </ul>
    </div>
  );
}

/**
 * Past sales from IndexedDB and the three reversals on the terminal session: a referenced refund
 * (full or partial amount, a `RETURN` line settled with an allocation against the original
 * tender, or the session's own `refund()` when the sale is this session's last payment), an
 * unreferenced refund, and a void by `OriginalSaleRecord`. A failed reversal step opens the
 * decision prompt; outcomes land in the sale's ledger.
 */
export function RefundsPane(): ReactNode {
  const { sales, terminalSession, settings, basket, log, report, lastSale, settlement } = useLane();
  const { sales: stored, error: storeError } = useStoredSales(sales);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [amount, setAmount] = useState('');
  const [unlinked, setUnlinked] = useState('5.00');
  const [busy, setBusy] = useState<string | null>(null);
  const [prompt, setPrompt] = useState<ReversalPrompt | null>(null);
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const currency = settings.currency;
  const selected = stored.find((s) => s.sale.id === selectedId) ?? null;
  const promptRef = useRef<ReversalPrompt | null>(null);

  const handlers: ReversalHandlers = {
    onError: (step, error, info) =>
      new Promise<ReversalDecision>((resolve) => {
        const decide = (decision: ReversalDecision) => {
          if (promptRef.current?.decide !== decide) return;
          promptRef.current = null;
          setPrompt(null);
          log.info('reversal', `${step ?? 'no step'} failed with ${error.code}: ${decision}`);
          resolve(decision);
        };
        const pending: ReversalPrompt = { step, error, deadline: info.deadline, decide };
        promptRef.current = pending;
        setPrompt(pending);
        info.signal.addEventListener('abort', () => decide(defaultReversalDecision(step)), {
          once: true,
        });
      }),
  };

  const finish = useCallback(
    (next: Outcome) => {
      setOutcome(next);
      setBusy(null);
      if (next.kind === 'error') report(next.error);
    },
    [report],
  );

  const sameSessionSale = (sale: StoredSale) =>
    terminalSession !== null &&
    lastSale !== null &&
    lastSale.id === sale.sale.id &&
    sale.sale.sessionId === terminalSession.id;

  const doVoid = (sale: StoredSale) => {
    if (!terminalSession) return;
    setBusy(`void ${sale.sale.id}`);
    const operation = sameSessionSale(sale)
      ? terminalSession.voidTransaction(undefined, handlers)
      : terminalSession.voidTransaction(originalSaleRecord(sale), handlers);
    track(log, 'voidTransaction', operation);
    operation.then(
      async (result) => {
        await sales.recordVoid(voidRecordFrom(sale, result)).catch(report);
        if (sameSessionSale(sale)) settlement.reset();
        finish({ kind: 'void', saleId: sale.sale.id, result });
      },
      async (error: unknown) => {
        if (error instanceof SessionError && error.reversedMovements.length > 0) {
          for (const record of reversalProgress(sale, error.reversedMovements)) {
            await sales.recordRefund(record).catch(report);
          }
        }
        finish({ kind: 'error', saleId: sale.sale.id, error });
      },
    );
  };

  const doRefund = (sale: StoredSale) => {
    if (!terminalSession) return;
    const requested = amount.trim() ? parseMoney(amount) : undefined;
    if (amount.trim() && !requested) {
      report(
        new Error(
          'Enter a positive amount with at most two decimals, or leave it blank for the full amount.',
        ),
      );
      return;
    }
    setBusy(`refund ${sale.sale.id}`);
    if (sameSessionSale(sale)) {
      // This session took the payment: the linked refund needs no references.
      const operation = terminalSession.refund(requested, handlers);
      track(log, 'refund', operation);
      operation.then(
        async (result) => {
          await sales.recordRefund(linkedRefundRecord(sale, requested, result)).catch(report);
          finish({ kind: 'linked', saleId: sale.sale.id, result });
        },
        (error: unknown) => finish({ kind: 'error', saleId: sale.sale.id, error }),
      );
      return;
    }
    const plan = planReferencedRefund(sale, requested);
    if ('error' in plan) {
      finish({ kind: 'error', saleId: sale.sale.id, error: new Error(plan.error) });
      return;
    }
    if ((basket.basket?.items.length ?? 0) > 0) {
      finish({
        kind: 'error',
        saleId: sale.sale.id,
        error: new Error(
          'The basket must be empty: the refund rings a RETURN line and settles it.',
        ),
      });
      return;
    }
    log.info(
      'refund',
      `referenced refund of ${plan.amount} from ${plan.leg.type} txn ${plan.leg.poiTransactionId}${plan.reversesAward ? ' with award reversal' : ''}`,
    );
    basket
      .clear()
      .then(() => basket.addItem(plan.returnLine))
      .then(() => {
        const operation = terminalSession.settle({
          settlementType: 'REFUND_THEN_CHARGE',
          refunds: [...plan.allocations],
          disableRebates: true,
          disablePoints: true,
          disableAward: true,
          // A refund allocation failure arrives here as a notification; the answer is ignored.
          onError: (failure) => {
            log.info(
              'refund',
              `${failure.step ?? 'allocation'} failed: ${failure.error.code} ${failure.error.message}`,
              failure,
            );
            return 'ABORT';
          },
        });
        track(log, 'settle (refund)', operation);
        return operation;
      })
      .then(
        async (result) => {
          await sales.recordRefund(refundRecordFrom(sale, plan, result)).catch(report);
          await basket.clear().catch(() => undefined);
          finish({ kind: 'refund', saleId: sale.sale.id, result });
        },
        async (error: unknown) => {
          await basket.clear().catch(() => undefined);
          finish({ kind: 'error', saleId: sale.sale.id, error });
        },
      );
  };

  const doUnlinked = () => {
    if (!terminalSession) return;
    const value = parseMoney(unlinked);
    if (!value) return;
    setBusy('unlinked refund');
    const operation = terminalSession.refundUnlinked(value, handlers);
    track(log, 'refundUnlinked', operation);
    operation.then(
      (result) => finish({ kind: 'unlinked', result }),
      (error: unknown) => finish({ kind: 'error', error }),
    );
  };

  const settling = settlement.status === 'running' || settlement.status === 'awaitingReply';
  const disabled = !terminalSession || busy !== null || settling;

  return (
    <div className="columns two">
      <div className="column">
        <section className="panel">
          <div className="panel-header">
            <h2>Past sales</h2>
            <span className="badge">{stored.length} in IndexedDB</span>
          </div>
          {storeError ? <p className="error small">{describeError(storeError)}</p> : null}
          {stored.length === 0 ? (
            <p className="muted">No sales recorded yet. Settle one on the Sale tab.</p>
          ) : (
            <div className="sales">
              {stored.map((sale) => (
                <SaleCard
                  key={sale.sale.id}
                  stored={sale}
                  currency={currency}
                  selected={sale.sale.id === selectedId}
                  onSelect={() => setSelectedId(sale.sale.id)}
                />
              ))}
            </div>
          )}
        </section>
      </div>
      <div className="column">
        <section className="panel">
          <h2>Reverse</h2>
          {!terminalSession ? (
            <p className="muted">Refunds and voids need a terminal session.</p>
          ) : null}
          {selected ? (
            <>
              <p className="small">
                Selected sale <code>{selected.sale.id}</code>
                {sameSessionSale(selected)
                  ? ' (this session’s last payment: linked reversal, no references)'
                  : ' (by persisted references)'}
                {refundLeg(selected)
                  ? ` · ${formatMoney(remainingLegAmount(selected, refundLeg(selected)!.type), currency)} left on ${refundLeg(selected)!.type}`
                  : ''}
              </p>
              <div className="inline">
                <input
                  aria-label="Refund amount"
                  placeholder="Amount (blank: full)"
                  inputMode="decimal"
                  value={amount}
                  onChange={(event) => setAmount(event.target.value)}
                  disabled={disabled}
                />
                <button
                  type="button"
                  disabled={disabled || !isRefundable(selected)}
                  onClick={() => doRefund(selected)}
                  title="A RETURN line settled with a refund allocation against the original tender"
                >
                  Referenced refund
                </button>
                <button
                  type="button"
                  className="danger"
                  disabled={disabled || !isVoidable(selected)}
                  onClick={() => doVoid(selected)}
                  title={
                    isPartiallyRefunded(selected)
                      ? 'Refused after a partial refund: a void would return the full amount on top'
                      : 'Reverses every movement of the sale, money legs first, then loyalty'
                  }
                >
                  Void
                </button>
              </div>
            </>
          ) : (
            <p className="muted small">Select a sale to refund or void it.</p>
          )}
          <h3>Unreferenced refund</h3>
          <div className="inline">
            <input
              aria-label="Unreferenced refund amount"
              inputMode="decimal"
              value={unlinked}
              onChange={(event) => setUnlinked(event.target.value)}
              disabled={disabled}
            />
            <button
              type="button"
              className="secondary"
              disabled={disabled || !parseMoney(unlinked)}
              onClick={doUnlinked}
            >
              Refund without a sale
            </button>
          </div>
          {busy ? <p className="small muted">Running {busy}…</p> : null}
          {prompt ? <ReversalDecisionPrompt prompt={prompt} /> : null}
          {outcome ? <OutcomeView outcome={outcome} currency={currency} /> : null}
        </section>
      </div>
    </div>
  );
}

function OutcomeView({ outcome, currency }: { outcome: Outcome; currency: string }): ReactNode {
  const fmt = (value: string | undefined) => formatMoney(value, currency);
  if (outcome.kind === 'error') {
    return (
      <div data-testid="reversal-outcome">
        <p className="error">{describeError(outcome.error)}</p>
        {outcome.error instanceof SessionError && outcome.error.reversedMovements.length > 0 ? (
          <p className="small">
            Reversed before it stopped:{' '}
            {outcome.error.reversedMovements
              .map((m) => `${m.step} ${m.poiTransactionId}`)
              .join(', ')}
            . Retry to resume at the first leg still standing.
          </p>
        ) : null}
      </div>
    );
  }
  if (outcome.kind === 'refund') {
    const { result } = outcome;
    return (
      <dl className="facts" data-testid="reversal-outcome">
        <dt>Refunded</dt>
        <dd>
          card {fmt(result.cardRefundedAmount)} · gift card {fmt(result.storedValueRefundedAmount)}{' '}
          · loyalty {fmt(result.loyaltyRefundedAmount)}
        </dd>
        <dt>Ledger</dt>
        <dd>
          <ul className="movements">
            {result.movements.map((m, i) => (
              <li key={i}>
                {m.step} {fmt(m.amount)}
                {m.poiTransactionId ? ` · txn ${m.poiTransactionId}` : ''}
              </li>
            ))}
          </ul>
        </dd>
        <dd>
          <ReceiptView title="Customer receipt" receipt={result.customerReceipt} />
        </dd>
      </dl>
    );
  }
  const { result } = outcome;
  const amount =
    outcome.kind === 'void' ? outcome.result.reversedAmount : outcome.result.refundedAmount;
  return (
    <dl className="facts" data-testid="reversal-outcome">
      <dt>{outcome.kind === 'void' ? 'Voided' : 'Refunded'}</dt>
      <dd>
        {result.success ? 'success' : 'not successful'}
        {amount ? ` · ${fmt(amount)}` : ''}
        {result.poiTransactionId ? ` · txn ${result.poiTransactionId}` : ''}
      </dd>
      <dt>Points</dt>
      <dd>
        {result.pointsReversed} reversed · balance {result.remainingPointBalance}
      </dd>
      <dd>
        <ReceiptView title="Customer receipt" receipt={result.customerReceipt} />
      </dd>
    </dl>
  );
}
