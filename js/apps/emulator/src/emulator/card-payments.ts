import type { CardPaymentResult, Receipt } from '@biltorg/pos-sdk';
import { cents } from '../money';
import { lastDigits } from '../store/sale-record';
import type { CardPaymentUi, OutcomeReceipt } from './state';

/** A card payment of a settlement as the dialogs and the outcome list it. */
export function toCardPaymentUi(payment: CardPaymentResult): CardPaymentUi {
  const digits = lastDigits(payment.maskedPan);
  return {
    amount: payment.amount,
    ...(payment.paymentBrand === undefined ? {} : { brand: payment.paymentBrand }),
    ...(digits === undefined ? {} : { lastDigits: digits }),
    ...(payment.approvalCode === undefined ? {} : { approvalCode: payment.approvalCode }),
    ...(payment.poiTransactionId === undefined
      ? {}
      : { poiTransactionId: payment.poiTransactionId }),
    partial: payment.partialApproval,
    ...(cents(payment.biltRewardsAmount) > 0
      ? { biltRewardsAmount: payment.biltRewardsAmount }
      : {}),
  };
}

/** What a receipt prints: the customer's copy, else the merchant's, as text. */
export function receiptText(customer?: Receipt, merchant?: Receipt): string | undefined {
  const receipt = customer ?? merchant;
  return receipt?.plainText ?? receipt?.html;
}

/**
 * Every card payment's receipt in payment order, each captioned `Card N receipt` when the sale
 * has several card payments; payments whose terminal sent none are skipped.
 */
export function cardReceipts(payments: readonly CardPaymentResult[]): OutcomeReceipt[] {
  return payments.flatMap((payment, index) => {
    const text = receiptText(payment.customerReceipt, payment.merchantReceipt);
    if (text === undefined) return [];
    return [{ ...(payments.length > 1 ? { caption: `Card ${index + 1} receipt` } : {}), text }];
  });
}
