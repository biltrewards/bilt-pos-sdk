// Samples for "Settle".
import { SessionError } from '@biltorg/pos-sdk';
import type {
  AbandonedSettlementRecord,
  Basket,
  Money,
  PartialApprovalAction,
  PartialApprovalDecision,
  PartialCardApproval,
  SettlementFailure,
  SettlementMovement,
  SettlementRecovery,
  SettlementRecoveryAction,
  SettlementResult,
  StepInfo,
  TerminalShopperSession,
} from '@biltorg/pos-sdk';

declare const taxService: { total(basket: Basket, signal: AbortSignal): Promise<Money> };
declare const cashier: {
  chooseRecovery(
    failure: SettlementFailure,
    signal: AbortSignal,
  ): Promise<SettlementRecoveryAction | 'EXTERNAL'>;
  choosePartial(
    partial: PartialCardApproval,
    signal: AbortSignal,
  ): Promise<PartialApprovalAction | 'EXTERNAL'>;
};
declare const ledger: {
  record(movement: SettlementMovement): void;
  reverse(notice: SettlementMovement): void;
  file(record: AbandonedSettlementRecord): void;
};
declare const cancelButton: { onclick: (() => void) | null };
declare function showStatus(status: string): void;
declare function log(step: string, amount: Money | undefined): void;
declare function print(text: string | undefined): void;
declare function showError(code: string, message: string): void;

export function pay(session: TerminalShopperSession): Promise<SettlementResult> {
  return session.settle(undefined, {
    // TOTAL_REQUIRED after rebates: re-tax the rebated lines, within the step's deadline.
    onRebatesRedeemed: (rebates, step) => taxService.total(rebates.updatedBasket, step.signal),
    // Points and a gift card are tender, not price changes: the suggested total is right.
    onPointsRedeemed: (points) => points.suggestedTotal,
    onGiftCardPayment: (giftCard) => giftCard.suggestedTotal,
    // Observations as they commit; SettlementResult.movements is the ledger.
    onMovement: (movement) => ledger.record(movement),
    // RECOVERY_REQUIRED: a charge-side step failed; answer within the deadline or ABORT applies.
    onError: (failure, step) => decideRecovery(failure, step),
    onAbandoned: (record) => ledger.file(record),
  });
}

async function decideRecovery(
  failure: SettlementFailure,
  step: StepInfo,
): Promise<SettlementRecovery | SettlementRecoveryAction> {
  if (failure.outcomeCertainty === 'INDETERMINATE') return 'RETRY'; // SKIP and EXTERNAL are refused
  const choice = await cashier.chooseRecovery(failure, step.signal); // aborts at step.deadline
  return choice === 'EXTERNAL'
    ? { action: 'EXTERNAL', externalPayment: { tenderType: 'CASH', amount: failure.amountDue } }
    : choice;
}

export function splitPay(session: TerminalShopperSession): Promise<SettlementResult> {
  return session.settle(
    // $20.00 on the first card, whatever is left on the next one
    { cardPayments: [{ amount: '20.00' }] },
    {
      // Each card payment is its own BEFORE_STEP; stepAmount is this payment's share.
      beforeStep: (context) => (log(context.step, context.stepAmount), undefined),
      // Once per card payment, partial approvals included; a notice undoes one it reported.
      onCardCharged: (movement) => ledger.record(movement),
      onCardChargeReversed: (notice) => ledger.reverse(notice),
      onPartialCardApproval: (partial, step) => decidePartial(partial, step),
    },
  );
}

async function decidePartial(
  partial: PartialCardApproval,
  step: StepInfo,
): Promise<PartialApprovalDecision | PartialApprovalAction> {
  if (partial.cardPayments.length >= 4) return 'ABORT'; // no cap of its own: count the cards
  const choice = await cashier.choosePartial(partial, step.signal); // aborts at step.deadline
  return choice === 'EXTERNAL'
    ? { action: 'EXTERNAL', externalPayment: { tenderType: 'CASH', amount: partial.amountDue } }
    : choice;
}

export async function payAndReport(session: TerminalShopperSession): Promise<void> {
  try {
    const result = await pay(session);
    print(result.customerReceipt?.plainText);
  } catch (error) {
    if (!(error instanceof SessionError)) throw error;
    if (error.code === 'ABORTED') return; // unwound; the basket is intact and settle() may run again
    if (error.abandonedSettlement) ledger.file(error.abandonedSettlement);
    showError(error.code, error.message);
  }
}

export async function payWithProgress(session: TerminalShopperSession): Promise<void> {
  const operation = session.settle(); // on its way already; no execute()
  const timer = setInterval(() => showStatus(operation.status), 250); // queued, running, awaitingReply, ...
  cancelButton.onclick = () => void operation.abort(); // stops at the next step boundary and unwinds
  try {
    await operation;
  } finally {
    clearInterval(timer);
    cancelButton.onclick = null;
  }
}

export function netSettlement(session: TerminalShopperSession): Promise<SettlementResult> {
  return session.settle({
    settlementType: 'NET',
    loyalty: { handling: 'EXPLICIT', steps: ['REBATES', 'POINTS'] },
  });
}
