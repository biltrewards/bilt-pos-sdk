import type { Schemas } from '@bilt/pos-protocol';
import type { BasketItem, Money, StoredValueCard } from '@bilt/pos-sdk';

export type StoredValueLoad = Schemas['StoredValueLoad'];
export type StoredValueLoadType = StoredValueLoad['type'];

/**
 * A gift card sold in this basket: the commercial line is in the basket (a referenced `SALE`
 * line, `GIFT-CARD`), the terminal instruction to activate or reload the card rides along in
 * `SettlementOptions.fulfillments`, and the reference joins them so the face value has one
 * source of truth. Pending until the basket settles, dropped when its line leaves the basket.
 */
export interface PendingGiftCard {
  readonly reference: string;
  readonly card: StoredValueCard;
  readonly type: StoredValueLoadType;
  readonly amount: Money;
}

export const GIFT_CARD_SKU = 'GIFT-CARD';

let giftCardCounter = 0;

/** A keyed card number, or a card the terminal reads itself when the number is blank. */
export function storedValueCard(cardNumber: string): StoredValueCard {
  const number = cardNumber.trim();
  return number.length === 0
    ? { identificationType: 'PAN', entryMode: 'MAG_STRIPE' }
    : { storedValueId: number, identificationType: 'PAN', entryMode: 'KEYED' };
}

export function describeCard(card: StoredValueCard): string {
  return card.storedValueId ?? 'card read on the terminal';
}

export function giftCardLine(
  amount: Money,
  type: StoredValueLoadType,
): {
  item: BasketItem;
  reference: string;
} {
  const reference = `gift-card-${Date.now().toString(36)}-${++giftCardCounter}`;
  return {
    reference,
    item: {
      sku: GIFT_CARD_SKU,
      description: type === 'ACTIVATE' ? 'Gift card' : 'Gift card reload',
      quantity: 1,
      unitPrice: amount,
      reference,
      category: 'Gift card',
    },
  };
}

export function toFulfillment(pending: PendingGiftCard): StoredValueLoad {
  return { basketReference: pending.reference, type: pending.type, card: pending.card };
}
