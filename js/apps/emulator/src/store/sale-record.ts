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
  /** The tender leg the refund drew from. */
  readonly leg?: LegType;
  /** True for a refund that returned the whole leg. */
  readonly full: boolean;
  /** True when this refund also reversed the sale's loyalty award. */
  readonly awardReversed: boolean;
  /**
   * True for the residue of a void that failed midway: the legs it did reverse, kept so a retry
   * omits them. Never a merchandise refund.
   */
  readonly reversalProgress: boolean;
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
  const legs = moneyLegs(stored.sale);
  return legs.length > 0 && legs.every((leg) => legRefunded(stored, leg.type));
}

export function isPartiallyRefunded(stored: StoredSale): boolean {
  return stored.refunds.some((refund) => !refund.full && !refund.reversalProgress);
}

/** The legs a void would still send: money legs not yet refunded, loyalty legs, an award not yet reversed. */
export function standingLegs(stored: StoredSale): readonly TransactionLeg[] {
  return stored.sale.legs.filter((leg) => {
    if (leg.type === 'CARD' || leg.type === 'STORED_VALUE') return !legRefunded(stored, leg.type);
    if (leg.type === 'AWARD') return !awardReversed(stored);
    return true;
  });
}

/**
 * A void must not run on a voided sale, nor after a partial refund (it would return the full
 * amount on top), nor once nothing is left to reverse. The residue of a void that stopped midway
 * does not block it: the retry resumes at the first leg still standing.
 */
export function isVoidable(stored: StoredSale): boolean {
  return (
    stored.voided === null &&
    !isPartiallyRefunded(stored) &&
    !stored.refunds.some((refund) => refund.full && !refund.reversalProgress) &&
    (standingLegs(stored).length > 0 || stored.sale.giftCardLoads.length > 0)
  );
}

export function isRefundable(stored: StoredSale): boolean {
  return stored.voided === null && !isFullyRefunded(stored);
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
    .reduce((sum, refund) => sum + cents(refund.amount), 0);
  return money(Math.max(0, cents(collected) - drawn));
}

/** The tender leg a referenced refund draws from: the card leg when there is one, else the gift card. */
export function refundLeg(stored: StoredSale): TransactionLeg | undefined {
  return (
    moneyLegs(stored.sale).find((leg) => leg.type === 'CARD' && !legRefunded(stored, 'CARD')) ??
    moneyLegs(stored.sale).find((leg) => !legRefunded(stored, leg.type))
  );
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
      ...(amount === undefined ? {} : { amount }),
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
            amount: movement.amount,
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
    authorizedAmount: result.authorizedAmount,
    pointsRedeemed: result.pointsRedeemed,
    totalPointsEarned: result.totalPointsEarned,
    legs,
    giftCardLoads,
    externalPaymentAmount: result.externalPaymentAmount,
  };
}

/**
 * The `OriginalSaleRecord` that voids a stored sale: every leg still standing. Legs a previous
 * attempt already reversed (per-leg full refund records) and an already-reversed award are left
 * out, so a retried void does not send them again.
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
  if (sale.giftCardLoads.length > 0) {
    record.storedValueLoads = sale.giftCardLoads.map((load) => ({
      basketReference: load.basketReference,
      amount: load.amount,
      poiTransactionId: load.poiTransactionId,
      ...(load.poiTimestamp === undefined ? {} : { poiTransactionTimestamp: load.poiTimestamp }),
    }));
  }
  const rebate = legOf(sale, 'REBATE');
  if (rebate) {
    record.rebatePoiTransactionId = rebate.poiTransactionId;
    if (rebate.poiTimestamp) record.rebatePoiTransactionTimestamp = rebate.poiTimestamp;
  }
  const redemption = legOf(sale, 'REDEMPTION');
  if (redemption) {
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
