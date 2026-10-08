import type {
  Basket,
  BasketItem,
  SettlementOptions,
  SettlementResult,
  VoidResult,
} from '@bilt/pos-sdk';
import { cents, formatMinor, money } from '../money';
import {
  isFullyRefunded,
  isPartiallyRefunded,
  legOf,
  legRefunded,
  moneyLegs,
  refundedQuantity,
  remainingLegAmount,
  type RefundRecord,
  type SaleItem,
  type StoredSale,
  type TransactionLeg,
} from '../store/sale-record';
import type { StoredSaleUi } from './state';

/**
 * Item-based returns and full refunds of stored sales, the desktop controller's
 * `addReturnToBasket`, `settle` (refund allocations) and `refundSale` bookkeeping as pure
 * functions, so the controller stays about the SDK calls and the contract test can drive the
 * same plans against the real host.
 */

type RefundAllocation = NonNullable<SettlementOptions['refunds']>[number];

/** Returns rung into the active basket from one stored sale, held until settlement. */
export interface PendingReturn {
  readonly saleId: string;
  /** The tender leg the returns restore to: the outstanding card leg, else the gift card. */
  readonly leg: TransactionLeg;
  /** The returned lines, quantities capped at what earlier refunds left. */
  readonly items: readonly SaleItem[];
  /** Shelf price plus tax of `items`, in cents. */
  readonly amountMinor: number;
  /** What the leg can still return, in cents; `null` when unknown. */
  readonly legCapacityMinor: number | null;
}

/**
 * What returning `quantity` units of a sold line restores: shelf price plus tax, the same figure
 * the basket's credit line carries, in cents.
 */
export function refundValueMinor(item: SaleItem, quantity: number): number {
  const gross = cents(item.unitPrice) * quantity;
  const tax = item.taxRate === undefined ? 0 : Math.round(gross * Number(item.taxRate));
  return gross + tax;
}

/** A sold line rung back as a return: shelf price and tax rate as sold. */
export function returnLine(item: SaleItem): BasketItem {
  return {
    sku: item.sku,
    description: item.description,
    quantity: item.quantity,
    unitPrice: item.unitPrice,
    type: 'RETURN',
    ...(item.category === undefined ? {} : { category: item.category }),
    ...(item.taxRate === undefined ? {} : { taxRate: item.taxRate }),
  };
}

export type ReturnPlan =
  | { readonly pending: PendingReturn; readonly notes: readonly string[] }
  | { readonly error: string };

/**
 * Plans an item return of the selected `skus` of a stored sale, as the desktop's
 * `addReturnToBasket` does: refused for a voided or refunded sale, a sale with a gift-card
 * purchase, or one without a tender leg; each line capped at what earlier refunds and returns
 * already in the basket (`pendingQuantity`) have not consumed.
 */
export function planReturn(
  stored: StoredSale,
  skus: ReadonlySet<string>,
  pendingQuantity: (sku: string) => number,
): ReturnPlan {
  if (stored.voided) return { error: 'The sale was voided — nothing left to refund' };
  if (isFullyRefunded(stored)) {
    return { error: 'The sale was already refunded in full — nothing left to refund' };
  }
  const { sale } = stored;
  if (sale.giftCardLoads.length > 0) {
    return {
      error:
        'The sale contains a gift card purchase — use the full refund to reverse its load and funding together',
    };
  }
  const legs = moneyLegs(sale);
  if (legs.length === 0) {
    return {
      error: 'The sale has no tender leg (rewards covered everything) — use the full refund',
    };
  }
  const outstanding = legs.filter((leg) => !legRefunded(stored, leg.type));
  const leg = outstanding.find((candidate) => candidate.type === 'CARD') ?? outstanding[0];
  if (!leg) {
    return {
      error: 'Every tender leg was already refunded in full — nothing left to draw from',
    };
  }
  const notes: string[] = [];
  const items = sale.items
    .filter((item) => skus.has(item.sku))
    .flatMap((item): SaleItem[] => {
      const remaining =
        item.quantity - refundedQuantity(stored, item.sku) - pendingQuantity(item.sku);
      if (remaining >= item.quantity) return [item];
      if (remaining > 0) {
        notes.push(
          `${item.description}: ${item.quantity - remaining} of ${item.quantity} already returned — ringing the remaining ${remaining}`,
        );
        return [{ ...item, quantity: remaining }];
      }
      notes.push(`${item.description} is already fully returned — skipped`);
      return [];
    });
  if (items.length === 0) return { error: 'Nothing left to return among the selected items' };
  const capacity = remainingLegAmount(stored, leg.type);
  return {
    notes,
    pending: {
      saleId: sale.id,
      leg,
      items,
      amountMinor: items.reduce((sum, item) => sum + refundValueMinor(item, item.quantity), 0),
      legCapacityMinor: capacity === undefined ? null : cents(capacity),
    },
  };
}

/**
 * The refund the settlement must allocate for the basket's returns: the full return value
 * separately, or under `NET` only a negative basket's difference (zero when the charge absorbs
 * the returns). The Java `Basket.getRefundAmount`.
 */
export function requiredRefundMinor(basket: Basket, net: boolean): number {
  if (net) {
    const total = cents(basket.grandTotal);
    return total < 0 ? -total : 0;
  }
  return Math.abs(
    basket.items
      .filter((line) => line.type === 'RETURN')
      .reduce((sum, line) => sum + cents(line.adjustedTotal) + cents(line.taxAmount), 0),
  );
}

/** One returned sale's share of a settlement's refund. */
export interface PlannedReturn {
  readonly saleId: string;
  readonly leg: TransactionLeg;
  readonly items: readonly SaleItem[];
  readonly totalMinor: number;
  /** What goes back to the tender leg. */
  readonly allocatedMinor: number;
  /** What the register pays out because the leg collected less than the shelf value. */
  readonly externalMinor: number;
}

/**
 * Splits the required refund over the pending returns, one share per returned sale in ring
 * order, each capped by what its tender leg can still give back; the overflow is an external
 * (register-paid) allocation.
 */
export function planAllocations(
  pending: readonly PendingReturn[],
  requiredMinor: number,
): { readonly returns: readonly PlannedReturn[]; readonly allocations: RefundAllocation[] } {
  const groups = new Map<string, PendingReturn[]>();
  for (const entry of pending) {
    groups.set(entry.saleId, [...(groups.get(entry.saleId) ?? []), entry]);
  }
  let unallocated = requiredMinor;
  const returns: PlannedReturn[] = [];
  const allocations: RefundAllocation[] = [];
  for (const group of groups.values()) {
    const first = group[0]!;
    const total = group.reduce((sum, entry) => sum + entry.amountMinor, 0);
    const allocated = Math.min(total, unallocated);
    unallocated -= allocated;
    const capacity = first.legCapacityMinor;
    const toTender = capacity !== null && capacity < allocated ? capacity : allocated;
    const planned: PlannedReturn = {
      saleId: first.saleId,
      leg: first.leg,
      items: group.flatMap((entry) => entry.items),
      totalMinor: total,
      allocatedMinor: toTender,
      externalMinor: allocated - toTender,
    };
    returns.push(planned);
    if (planned.allocatedMinor > 0) {
      allocations.push({
        type: planned.leg.type === 'CARD' ? 'CARD' : 'STORED_VALUE',
        amount: money(planned.allocatedMinor),
        originalPoiTransactionId: planned.leg.poiTransactionId,
        ...(planned.leg.poiTimestamp === undefined
          ? {}
          : { originalPoiTransactionTimestamp: planned.leg.poiTimestamp }),
      });
    }
    if (planned.externalMinor > 0) {
      allocations.push({ type: 'EXTERNAL', amount: money(planned.externalMinor) });
    }
  }
  return { returns, allocations };
}

function legLabel(leg: TransactionLeg): string {
  return leg.type === 'STORED_VALUE' ? 'gift card' : 'card';
}

/**
 * The refund records a settlement leaves behind for its returns, matching each tender
 * allocation to its committed refund movement (they commit in allocation order), and the outcome
 * lines describing what was restored.
 */
export function settledReturns(
  returns: readonly PlannedReturn[],
  result: SettlementResult,
): { readonly records: readonly RefundRecord[]; readonly parts: readonly string[] } {
  const movements = result.movements.filter(
    (movement) => movement.step === 'CARD_REFUND' || movement.step === 'STORED_VALUE_REFUND',
  );
  let index = 0;
  const records: RefundRecord[] = [];
  const parts: string[] = [];
  for (const planned of returns) {
    const refunded = planned.allocatedMinor > 0;
    const movement = refunded ? movements[index++] : undefined;
    const netted = planned.totalMinor - planned.allocatedMinor - planned.externalMinor;
    const total = formatMinor(planned.totalMinor);
    if (planned.allocatedMinor === planned.totalMinor) {
      parts.push(`returned $${total} to the ${legLabel(planned.leg)}`);
    } else if (planned.totalMinor > 0 && !refunded && planned.externalMinor === 0) {
      parts.push(`netted $${total} against the purchase`);
    } else {
      const details = [
        refunded ? `$${formatMinor(planned.allocatedMinor)} to the ${legLabel(planned.leg)}` : null,
        planned.externalMinor > 0
          ? `$${formatMinor(planned.externalMinor)} register-paid — the ${legLabel(planned.leg)} collected less than the shelf value`
          : null,
        netted > 0 ? `$${formatMinor(netted)} netted` : null,
      ].filter(Boolean);
      parts.push(`returned $${total} (${details.join(', ')})`);
    }
    records.push({
      saleId: planned.saleId,
      recordedAt: new Date().toISOString(),
      amount: money(planned.totalMinor),
      ...(movement?.poiTransactionId === undefined
        ? {}
        : { poiTransactionId: movement.poiTransactionId }),
      ...(movement?.poiTransactionTimestamp === undefined
        ? {}
        : { poiTimestamp: movement.poiTransactionTimestamp }),
      full: false,
      ...(refunded ? { leg: planned.leg.type, tenderAmount: money(planned.allocatedMinor) } : {}),
      awardReversed: false,
      reversalProgress: false,
      items: planned.items.map((item) => ({ sku: item.sku, quantity: item.quantity })),
    });
  }
  return { records, parts };
}

/** The legless full refund record a completed whole-sale void leaves behind. */
export function fullRefundRecord(stored: StoredSale, result: VoidResult): RefundRecord {
  return {
    saleId: stored.sale.id,
    recordedAt: new Date().toISOString(),
    ...(result.reversedAmount === undefined ? {} : { amount: result.reversedAmount }),
    ...(result.poiTransactionId === undefined ? {} : { poiTransactionId: result.poiTransactionId }),
    ...(result.poiTransactionTimestamp === undefined
      ? {}
      : { poiTimestamp: result.poiTransactionTimestamp }),
    full: true,
    awardReversed: legOf(stored.sale, 'AWARD') !== undefined,
    reversalProgress: false,
  };
}

function completedAtLabel(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}

/** A stored sale as the Refund tab lists it; `pendingQuantity` nets returns still in the basket. */
export function toStoredSaleUi(
  stored: StoredSale,
  pendingQuantity: (saleId: string, sku: string) => number,
): StoredSaleUi {
  const { sale } = stored;
  const voided = stored.voided !== null;
  const fullyRefunded = isFullyRefunded(stored);
  const refundable = !voided && !fullyRefunded;
  return {
    id: sale.id,
    completedAtLabel: completedAtLabel(sale.completedAt),
    totalAmount: sale.authorizedAmount,
    memberId: sale.memberId ?? null,
    items: sale.items.map((item) => {
      const remaining = Math.min(
        item.quantity,
        Math.max(
          0,
          item.quantity - refundedQuantity(stored, item.sku) - pendingQuantity(sale.id, item.sku),
        ),
      );
      return {
        sku: item.sku,
        description: item.description,
        quantity: item.quantity,
        refundMinor: refundValueMinor(item, remaining),
        remainingQuantity: remaining,
      };
    }),
    hasGiftCardPurchase: sale.giftCardLoads.length > 0,
    refunded: stored.refunds.length > 0,
    fullyRefunded,
    voided,
    fullRefundAvailable:
      refundable &&
      !isPartiallyRefunded(stored) &&
      (sale.legs.length > 0 || sale.giftCardLoads.length > 0),
    externalPaymentAmount:
      cents(sale.externalPaymentAmount) > 0 ? sale.externalPaymentAmount : null,
  };
}
