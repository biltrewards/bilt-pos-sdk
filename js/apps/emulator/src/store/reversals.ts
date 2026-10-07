import type {
  BasketItem,
  Money,
  RefundResult,
  ReversalDecision,
  ReversalStep,
  ReversedMovement,
  SettlementOptions,
  SettlementResult,
  VoidResult,
} from '@bilt/pos-sdk';
import { cents } from '../money';
import {
  awardReversed,
  legOf,
  refundLeg,
  remainingLegAmount,
  standingLoads,
  type LegType,
  type RefundRecord,
  type StoredSale,
  type TransactionLeg,
  type VoidRecord,
} from './sale-record';

type RefundAllocation = NonNullable<SettlementOptions['refunds']>[number];

/**
 * A referenced refund of a stored sale, as the SDK runs it: a `RETURN` line for the amount in
 * the active basket and a refund allocation against the original tender leg in
 * `SettlementOptions.refunds`, so the terminal gets a linked `PaymentRequest(Refund)` naming the
 * original POI transaction. A full refund of a sale with an award also files the award's
 * reversal as a bookkeeping allocation.
 */
export interface ReferencedRefundPlan {
  readonly leg: TransactionLeg;
  readonly amount: Money;
  readonly full: boolean;
  readonly returnLine: BasketItem;
  readonly allocations: readonly RefundAllocation[];
  readonly reversesAward: boolean;
}

export function planReferencedRefund(
  stored: StoredSale,
  requested: Money | undefined,
): ReferencedRefundPlan | { error: string } {
  if (standingLoads(stored).length > 0) {
    return {
      error: 'The sale loaded a gift card: void it so the load is reversed with its funding.',
    };
  }
  const leg = refundLeg(stored);
  if (!leg) return { error: 'The sale has no tender leg left to refund.' };
  const remaining = remainingLegAmount(stored, leg.type) ?? leg.amount;
  if (remaining === undefined) return { error: 'The tender leg has no recorded amount.' };
  const amount = requested ?? remaining;
  if (cents(amount) <= 0) return { error: 'Nothing left to refund on this leg.' };
  if (cents(amount) > cents(remaining)) {
    return { error: `At most ${remaining} can still be refunded from the ${leg.type} leg.` };
  }
  const full = cents(amount) === cents(remaining);
  const award = legOf(stored.sale, 'AWARD');
  const reversesAward = full && award !== undefined && !awardReversed(stored);
  const allocations: RefundAllocation[] = [
    {
      type: leg.type === 'CARD' ? 'CARD' : 'STORED_VALUE',
      amount,
      originalPoiTransactionId: leg.poiTransactionId,
      ...(leg.poiTimestamp === undefined
        ? {}
        : { originalPoiTransactionTimestamp: leg.poiTimestamp }),
    },
    ...(reversesAward && award
      ? [
          {
            type: 'AWARD' as const,
            amount: '0',
            originalPoiTransactionId: award.poiTransactionId,
            ...(award.poiTimestamp === undefined
              ? {}
              : { originalPoiTransactionTimestamp: award.poiTimestamp }),
            ...(stored.sale.memberId === undefined ? {} : { memberId: stored.sale.memberId }),
          },
        ]
      : []),
  ];
  return {
    leg,
    amount,
    full,
    reversesAward,
    returnLine: {
      type: 'RETURN',
      sku: `REFUND-${stored.sale.id}`,
      description: `Refund of sale ${stored.sale.id}`,
      quantity: 1,
      unitPrice: amount,
      reference: `refund-${stored.sale.id}-${Date.now().toString(36)}`,
    },
    allocations,
  };
}

/** A referenced refund that stopped after committing an allocation, held for its retry. */
export interface PendingRefund {
  readonly sale: StoredSale;
  readonly plan: ReferencedRefundPlan;
}

/** The refund record a successful referenced-refund settlement leaves behind. */
export function refundRecordFrom(
  stored: StoredSale,
  plan: ReferencedRefundPlan,
  result: SettlementResult,
): RefundRecord {
  const movement = result.movements.find(
    (m) => m.step === (plan.leg.type === 'CARD' ? 'CARD_REFUND' : 'STORED_VALUE_REFUND'),
  );
  return {
    saleId: stored.sale.id,
    recordedAt: new Date().toISOString(),
    amount: plan.amount,
    ...(movement?.poiTransactionId === undefined
      ? {}
      : { poiTransactionId: movement.poiTransactionId }),
    ...(movement?.poiTransactionTimestamp === undefined
      ? {}
      : { poiTimestamp: movement.poiTransactionTimestamp }),
    leg: plan.leg.type,
    full: plan.full,
    awardReversed: plan.reversesAward && result.movements.some((m) => m.step === 'AWARD_REFUND'),
    reversalProgress: false,
  };
}

/** The refund record a same-session `refund()` leaves behind. */
export function linkedRefundRecord(
  stored: StoredSale,
  amount: Money | undefined,
  result: RefundResult,
): RefundRecord {
  const leg = refundLeg(stored);
  const remaining = leg ? remainingLegAmount(stored, leg.type) : undefined;
  const refunded = result.refundedAmount ?? amount ?? remaining;
  return {
    saleId: stored.sale.id,
    recordedAt: new Date().toISOString(),
    ...(refunded === undefined ? {} : { amount: refunded }),
    ...(result.poiTransactionId === undefined ? {} : { poiTransactionId: result.poiTransactionId }),
    ...(result.poiTransactionTimestamp === undefined
      ? {}
      : { poiTimestamp: result.poiTransactionTimestamp }),
    ...(leg ? { leg: leg.type } : {}),
    full: remaining !== undefined && refunded !== undefined && cents(refunded) >= cents(remaining),
    awardReversed: result.pointsReversed > 0,
    reversalProgress: false,
  };
}

export function voidRecordFrom(stored: StoredSale, result: VoidResult): VoidRecord {
  return {
    saleId: stored.sale.id,
    recordedAt: new Date().toISOString(),
    ...(result.poiTransactionId === undefined ? {} : { poiTransactionId: result.poiTransactionId }),
    ...(result.poiTransactionTimestamp === undefined
      ? {}
      : { poiTimestamp: result.poiTransactionTimestamp }),
  };
}

const LEG_OF_STEP: Partial<Record<ReversalStep, LegType>> = {
  CARD: 'CARD',
  STORED_VALUE: 'STORED_VALUE',
  AWARD: 'AWARD',
  REBATE: 'REBATE',
  REDEMPTION: 'REDEMPTION',
};

/**
 * What a void that stopped midway already reversed, as per-leg and per-load full records, so a
 * retry omits those legs and loads instead of reversing them twice.
 */
export function reversalProgress(
  stored: StoredSale,
  reversed: readonly ReversedMovement[],
): RefundRecord[] {
  return reversed.flatMap((movement): RefundRecord[] => {
    if (movement.step === 'STORED_VALUE_LOAD') {
      return [
        {
          saleId: stored.sale.id,
          recordedAt: new Date().toISOString(),
          poiTransactionId: movement.poiTransactionId,
          full: true,
          awardReversed: false,
          reversalProgress: true,
          giftCardLoad: movement.poiTransactionId,
        },
      ];
    }
    const leg = LEG_OF_STEP[movement.step];
    if (!leg) return [];
    return [
      {
        saleId: stored.sale.id,
        recordedAt: new Date().toISOString(),
        poiTransactionId: movement.poiTransactionId,
        leg,
        full: true,
        awardReversed: leg === 'AWARD',
        reversalProgress: true,
      },
    ];
  });
}

/**
 * The Java default policy for a failed reversal step, so the prompt can offer it as the
 * default: a failed tender reversal aborts, a loyalty movement riding along is skipped (the
 * terminal can retry it through store-and-forward).
 */
export function defaultReversalDecision(step: ReversalStep | null): ReversalDecision {
  return step === 'STORED_VALUE_LOAD' || step === 'CARD' || step === 'STORED_VALUE' || step === null
    ? 'ABORT'
    : 'SKIP';
}
