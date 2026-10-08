import type { ReversalDecision, ReversalStep, ReversedMovement } from '@bilt/pos-sdk';
import type { LegType, RefundRecord, StoredSale } from './sale-record';

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
