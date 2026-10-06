// Samples for "Refund and void".
import type {
  Money,
  OriginalSaleRecord,
  RefundResult,
  TerminalShopperSession,
  VoidResult,
} from '@bilt/pos-sdk';

declare function log(message: string): void;

export function refundLast(session: TerminalShopperSession, amount?: Money): Promise<RefundResult> {
  return session.refund(amount); // full when amount is omitted; the award is reversed best-effort
}

export function refundWithoutASale(session: TerminalShopperSession): Promise<RefundResult> {
  return session.refundUnlinked('12.00');
}

export function voidLast(session: TerminalShopperSession): Promise<VoidResult> {
  return session.voidTransaction(undefined, {
    onError: (step, error, info) => {
      log(`void step ${step ?? 'none'} failed with ${error.code}; deadline ${info.deadline}`);
      return step === 'AWARD' ? 'SKIP' : 'ABORT'; // a retried void resumes at the first leg still standing
    },
  });
}

export function voidPriorSale(
  session: TerminalShopperSession,
  record: OriginalSaleRecord,
): Promise<VoidResult> {
  return session.voidTransaction(record);
}
