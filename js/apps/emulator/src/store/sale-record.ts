import type { Money, OriginalSaleRecord, SettlementResult } from '@bilt/pos-sdk';
import { cents, money } from '../money';

/**
 * One movement the terminal committed as part of a sale, a port of the desktop emulator's
 * `SaleRecord`: a payment is not a single transaction (a split tender has a card and a stored
 * value leg, and the loyalty steps each carry their own POI transaction reference), and a later
 * referenced refund or void of a movement must present that reference.
 */
export type LegType = 'CARD' | 'STORED_VALUE' | 'AWARD' | 'REBATE' | 'REDEMPTION';

export interface TransactionLeg {
  readonly type: LegType;
  readonly poiTransactionId: string;
  readonly poiTimestamp?: string;
  readonly amount?: Money;
  readonly approvalCode?: string;
  readonly acquirerTransactionId?: string;
  readonly brand?: string;
}

export interface SaleItem {
  readonly sku: string;
  readonly description: string;
  readonly category?: string;
  readonly quantity: number;
  readonly unitPrice: Money;
  readonly taxRate?: Money;
  /** Line total after rebates and discounts. */
  readonly lineTotal: Money;
}

/** A gift-card activation or load the sale fulfilled; a whole-sale void reverses it before its funding. */
export interface GiftCardLoad {
  readonly basketReference: string;
  readonly amount: Money;
  readonly poiTransactionId: string;
  readonly poiTimestamp?: string;
}

/** A completed sale with everything a later referenced refund or void needs. */
export interface SaleRecord {
  /** Emulator-local identifier, unrelated to any terminal reference. */
  readonly id: string;
  readonly sessionId: string;
  readonly saleId: string;
  readonly poiId: string;
  readonly currency: string;
  readonly completedAt: string;
  readonly memberId?: string;
  readonly items: readonly SaleItem[];
  readonly authorizedAmount: Money;
  readonly pointsRedeemed: number;
  readonly totalPointsEarned: number;
  readonly legs: readonly TransactionLeg[];
  readonly giftCardLoads: readonly GiftCardLoad[];
  readonly externalPaymentAmount: Money;
}

/** A refund issued against a stored sale. */
export interface RefundRecord {
  readonly saleId: string;
  readonly recordedAt: string;
  readonly amount?: Money;
  readonly poiTransactionId?: string;
  readonly poiTimestamp?: string;
  /** The tender leg the refund drew from; a legless full record exhausted the whole sale. */
  readonly leg?: LegType;
  /**
   * What actually flowed back to `leg`: `amount` is the whole return value, which may include
   * shares netted against a charge or paid out by the register.
   */
  readonly tenderAmount?: Money;
  /** True for a refund that returned the whole leg (or, without a leg, the whole sale). */
  readonly full: boolean;
  /** True when this refund also reversed the sale's loyalty award. */
  readonly awardReversed: boolean;
  /**
   * True for the residue of a void that failed midway: the legs it did reverse, kept so a retry
   * omits them. Never a merchandise refund.
   */
  readonly reversalProgress: boolean;
  /**
   * On a `reversalProgress` record for a gift-card load rather than a leg: the load's original
   * POI transaction, which the SDK reports the reversed load by.
   */
  readonly giftCardLoad?: string;
  /** The returned items of an item-based refund; empty or absent for a full refund. */
  readonly items?: readonly RefundedItem[];
}

/** One returned line of an item-based refund: how much of the sold quantity of `sku` it gave back. */
export interface RefundedItem {
  readonly sku: string;
  readonly quantity: number;
}

/** A void issued against a stored sale. */
export interface VoidRecord {
  readonly saleId: string;
  readonly recordedAt: string;
  readonly poiTransactionId?: string;
  readonly poiTimestamp?: string;
}

/** A sale folded together with the refunds and void recorded against it: the view the Refunds pane consults. */
export interface StoredSale {
  readonly sale: SaleRecord;
  readonly refunds: readonly RefundRecord[];
  readonly voided: VoidRecord | null;
}

export function moneyLegs(sale: SaleRecord): readonly TransactionLeg[] {
  return sale.legs.filter((leg) => leg.type === 'CARD' || leg.type === 'STORED_VALUE');
}

export function legOf(sale: SaleRecord, type: LegType): TransactionLeg | undefined {
  return sale.legs.find((leg) => leg.type === type);
}

export function legRefunded(stored: StoredSale, type: LegType): boolean {
  return stored.refunds.some((refund) => refund.full && refund.leg === type);
}

export function isFullyRefunded(stored: StoredSale): boolean {
  // A stopped void's per-load progress record is full but legless too; it is not the whole sale.
  if (
    stored.refunds.some(
      (refund) => refund.full && refund.leg === undefined && !refund.reversalProgress,
    )
  ) {
    return true;
  }
  const legs = moneyLegs(stored.sale);
  return legs.length > 0 && legs.every((leg) => legRefunded(stored, leg.type));
}

/** How much of the sold quantity of `sku` earlier refunds already returned. */
export function refundedQuantity(stored: StoredSale, sku: string): number {
  return stored.refunds.reduce(
    (sum, refund) =>
      sum +
      (refund.items ?? [])
        .filter((item) => item.sku === sku)
        .reduce((n, item) => n + item.quantity, 0),
    0,
  );
}

export function isPartiallyRefunded(stored: StoredSale): boolean {
  return stored.refunds.some((refund) => !refund.full && !refund.reversalProgress);
}

/** The gift-card loads a void would still unwind: those a stopped void has not already reversed. */
export function standingLoads(stored: StoredSale): readonly GiftCardLoad[] {
  const reversed = new Set(stored.refunds.map((refund) => refund.giftCardLoad));
  return stored.sale.giftCardLoads.filter((load) => !reversed.has(load.poiTransactionId));
}

export function awardReversed(stored: StoredSale): boolean {
  return stored.refunds.some((refund) => refund.awardReversed);
}

/**
 * What a tender leg can still return: the amount it collected minus what earlier refunds drew
 * from it; `undefined` when unknown, the acquirer then enforces the cap.
 */
export function remainingLegAmount(stored: StoredSale, type: LegType): Money | undefined {
  const collected = legOf(stored.sale, type)?.amount;
  if (collected === undefined) return undefined;
  const drawn = stored.refunds
    .filter((refund) => refund.leg === type && !refund.reversalProgress)
    .reduce((sum, refund) => sum + cents(refund.tenderAmount ?? refund.amount), 0);
  return money(Math.max(0, cents(collected) - drawn));
}

export interface SaleContext {
  readonly sessionId: string;
  readonly saleId: string;
  readonly poiId: string;
  readonly currency: string;
  readonly memberId: string | undefined;
  readonly recordId: string;
  readonly completedAt: Date;
}

function leg(
  type: LegType,
  id: string | undefined,
  timestamp: string | undefined,
  amount: Money | undefined,
  extra: Partial<TransactionLeg> = {},
): TransactionLeg[] {
  if (id === undefined) return [];
  return [
    {
      type,
      poiTransactionId: id,
      ...(timestamp === undefined ? {} : { poiTimestamp: timestamp }),
      ...(amount === undefined ? {} : { amount: money(cents(amount)) }),
      ...extra,
    },
  ];
}

/**
 * Flattens a completed payment into a `SaleRecord`: only the legs the terminal committed are
 * recorded (a rewards-only checkout may have no card leg), each with the POI reference a later
 * referenced refund or void must present. Gift-card loads come from the `STORED_VALUE_LOAD`
 * movements, which name the fulfilled basket line.
 */
export function toSaleRecord(result: SettlementResult, context: SaleContext): SaleRecord {
  // A gift-card-only checkout has no card step: the SDK copies the stored value payment's
  // reference into `poiTransactionId`, so only a distinct reference is a real card payment.
  const storedValueIsPrimary =
    result.poiTransactionId !== undefined &&
    result.poiTransactionId === result.storedValuePoiTransactionId;
  const legs: TransactionLeg[] = [
    ...(storedValueIsPrimary
      ? []
      : leg(
          'CARD',
          result.poiTransactionId,
          result.poiTransactionTimestamp,
          result.cardAmountCharged,
          {
            ...(result.approvalCode === undefined ? {} : { approvalCode: result.approvalCode }),
            ...(result.acquirerTransactionId === undefined
              ? {}
              : { acquirerTransactionId: result.acquirerTransactionId }),
            ...(result.paymentBrand === undefined ? {} : { brand: result.paymentBrand }),
          },
        )),
    ...leg(
      'STORED_VALUE',
      result.storedValuePoiTransactionId,
      result.storedValuePoiTransactionTimestamp,
      result.storedValueAmountUsed,
      storedValueIsPrimary && result.approvalCode !== undefined
        ? { approvalCode: result.approvalCode }
        : {},
    ),
    ...leg('AWARD', result.awardPoiTransactionId, result.awardPoiTransactionTimestamp, undefined),
    ...leg(
      'REBATE',
      result.rebatePoiTransactionId,
      result.rebatePoiTransactionTimestamp,
      result.totalRebateAmount,
    ),
    ...leg(
      'REDEMPTION',
      result.redemptionPoiTransactionId,
      result.redemptionPoiTransactionTimestamp,
      result.pointsMonetaryValue,
    ),
  ];
  const giftCardLoads: GiftCardLoad[] = result.movements.flatMap((movement) =>
    movement.step === 'STORED_VALUE_LOAD' &&
    movement.target.basketReference !== undefined &&
    movement.poiTransactionId !== undefined
      ? [
          {
            basketReference: movement.target.basketReference,
            amount: money(cents(movement.amount)),
            poiTransactionId: movement.poiTransactionId,
            ...(movement.poiTransactionTimestamp === undefined
              ? {}
              : { poiTimestamp: movement.poiTransactionTimestamp }),
          },
        ]
      : [],
  );
  const fulfilled = new Set(giftCardLoads.map((load) => load.basketReference));
  // Only ordinary sale lines are refundable merchandise; a fulfilled gift-card purchase is
  // unwound by reversing its load as part of a whole-sale void.
  const items: SaleItem[] = result.finalBasket.items
    .filter((line) => line.type === 'SALE' && !(line.reference && fulfilled.has(line.reference)))
    .map((line) => ({
      sku: line.sku,
      description: line.description,
      ...(line.category === undefined ? {} : { category: line.category }),
      quantity: line.quantity,
      unitPrice: line.unitPrice,
      ...(line.taxRate === undefined ? {} : { taxRate: line.taxRate }),
      lineTotal: line.adjustedTotal,
    }));
  return {
    id: context.recordId,
    sessionId: context.sessionId,
    saleId: context.saleId,
    poiId: context.poiId,
    currency: context.currency,
    completedAt: context.completedAt.toISOString(),
    ...(context.memberId === undefined ? {} : { memberId: context.memberId }),
    items,
    authorizedAmount: money(cents(result.authorizedAmount)),
    pointsRedeemed: result.pointsRedeemed,
    totalPointsEarned: result.totalPointsEarned,
    legs,
    giftCardLoads,
    externalPaymentAmount: money(cents(result.externalPaymentAmount)),
  };
}

/**
 * The `OriginalSaleRecord` that voids a stored sale: every leg and gift-card load still standing.
 * Legs and loads a previous attempt already reversed (its `reversalProgress` records) and an
 * already-reversed award are left out, so a retried void does not send them again.
 */
export function originalSaleRecord(stored: StoredSale): OriginalSaleRecord {
  const { sale } = stored;
  const record: {
    -readonly [K in keyof OriginalSaleRecord]: OriginalSaleRecord[K];
  } = {};
  const card = legOf(sale, 'CARD');
  if (card && !legRefunded(stored, 'CARD')) {
    record.cardPoiTransactionId = card.poiTransactionId;
    if (card.poiTimestamp) record.cardPoiTransactionTimestamp = card.poiTimestamp;
  }
  const storedValue = legOf(sale, 'STORED_VALUE');
  if (storedValue && !legRefunded(stored, 'STORED_VALUE')) {
    record.storedValuePoiTransactionId = storedValue.poiTransactionId;
    if (storedValue.poiTimestamp) {
      record.storedValuePoiTransactionTimestamp = storedValue.poiTimestamp;
    }
  }
  const loads = standingLoads(stored);
  if (loads.length > 0) {
    record.storedValueLoads = loads.map((load) => ({
      basketReference: load.basketReference,
      amount: load.amount,
      poiTransactionId: load.poiTransactionId,
      ...(load.poiTimestamp === undefined ? {} : { poiTransactionTimestamp: load.poiTimestamp }),
    }));
  }
  const rebate = legOf(sale, 'REBATE');
  if (rebate && !legRefunded(stored, 'REBATE')) {
    record.rebatePoiTransactionId = rebate.poiTransactionId;
    if (rebate.poiTimestamp) record.rebatePoiTransactionTimestamp = rebate.poiTimestamp;
  }
  const redemption = legOf(sale, 'REDEMPTION');
  if (redemption && !legRefunded(stored, 'REDEMPTION')) {
    record.redemptionPoiTransactionId = redemption.poiTransactionId;
    if (redemption.poiTimestamp) {
      record.redemptionPoiTransactionTimestamp = redemption.poiTimestamp;
    }
  }
  const award = legOf(sale, 'AWARD');
  if (award && !awardReversed(stored)) {
    record.awardPoiTransactionId = award.poiTransactionId;
    if (award.poiTimestamp) record.awardPoiTransactionTimestamp = award.poiTimestamp;
  }
  if (sale.memberId !== undefined) record.memberId = sale.memberId;
  return record;
}
