/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   Internal API — subject to change without notice.
 */
package com.bilt.pos.session.internal;

import com.bilt.pos.session.ReversalStep;
import com.bilt.pos.session.TenderType;
import com.bilt.pos.session.settlement.StoredValueLoadRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One reversible movement of a prior sale: which leg it is and the POI reference to reverse it by.
 * The step determines the wire verb — {@code StoredValueRequest(Reverse)} for fulfilled load lines,
 * {@code ReversalRequest} for tender legs, and the matching {@code LoyaltyRequest} refund type for
 * loyalty legs.
 */
public final class ReversalMovement {

  /** Stable resume identity; unlike a step enum, supports several load reversals. */
  public static final class Key {
    private final ReversalStep step;
    private final String poiTransactionId;

    private Key(ReversalStep step, String poiTransactionId) {
      this.step = step;
      this.poiTransactionId = poiTransactionId;
    }

    public ReversalStep getStep() {
      return step;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof Key)) {
        return false;
      }
      Key that = (Key) other;
      return step == that.step && poiTransactionId.equals(that.poiTransactionId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(step, poiTransactionId);
    }
  }

  private final ReversalStep step;
  final String poiTransactionId;
  final Instant poiTransactionTimestamp;
  final BigDecimal amount;
  final TenderType tender;
  // the rewards part of a card payment rewards paid part of, when this movement returns it whole
  final BigDecimal biltRewardsAmount;

  public ReversalMovement(
      ReversalStep step, String poiTransactionId, Instant poiTransactionTimestamp) {
    this(step, poiTransactionId, poiTransactionTimestamp, null, defaultTender(step));
  }

  /**
   * @param amount what the original movement moved, when known; a full reversal returns this much
   *     when the terminal does not echo an amount
   * @param tender the money tender the movement returns to; {@code null} for loyalty movements and
   *     stored value load reversals, which are not tenders
   */
  public ReversalMovement(
      ReversalStep step,
      String poiTransactionId,
      Instant poiTransactionTimestamp,
      BigDecimal amount,
      TenderType tender) {
    this(step, poiTransactionId, poiTransactionTimestamp, amount, tender, null);
  }

  /**
   * A tender movement whose {@code amount} includes {@code biltRewardsAmount} of Bilt rewards (a
   * card payment rewards paid part of): reversed or refunded in full, the rewards part goes back to
   * the member's rewards. {@code null} when none or unknown.
   */
  public ReversalMovement(
      ReversalStep step,
      String poiTransactionId,
      Instant poiTransactionTimestamp,
      BigDecimal amount,
      TenderType tender,
      BigDecimal biltRewardsAmount) {
    this.step = step;
    this.poiTransactionId = poiTransactionId;
    this.poiTransactionTimestamp = poiTransactionTimestamp;
    this.amount = amount;
    this.tender = tender;
    this.biltRewardsAmount = biltRewardsAmount;
  }

  private static TenderType defaultTender(ReversalStep step) {
    if (step == ReversalStep.CARD) {
      return TenderType.CARD;
    }
    if (step == ReversalStep.BILT_REWARDS) {
      return TenderType.BILT_REWARDS;
    }
    return step == ReversalStep.STORED_VALUE ? TenderType.STORED_VALUE : null;
  }

  public ReversalStep getStep() {
    return step;
  }

  public String getPoiTransactionId() {
    return poiTransactionId;
  }

  public Instant getPoiTransactionTimestamp() {
    return poiTransactionTimestamp;
  }

  public TenderType getTender() {
    return tender;
  }

  /**
   * This movement returning {@code amount}: a refund of part of it, or of all of it. The rewards
   * part the sale recorded goes with it only when {@code amount} is the whole original amount; for
   * less, the split is whatever the terminal reports.
   */
  public ReversalMovement withAmount(BigDecimal amount) {
    boolean whole = this.amount != null && amount != null && this.amount.compareTo(amount) == 0;
    return new ReversalMovement(
        step,
        poiTransactionId,
        poiTransactionTimestamp,
        amount,
        tender,
        whole ? biltRewardsAmount : null);
  }

  public Key key() {
    return key(step, poiTransactionId);
  }

  public static Key key(ReversalStep step, String poiTransactionId) {
    return new Key(
        Objects.requireNonNull(step, "step"),
        Objects.requireNonNull(poiTransactionId, "poiTransactionId"));
  }

  /**
   * The movements of a sale in reversal order — money legs first, mirroring the payment sequence's
   * reverse-commit unwind order — one per non-null reference. Every reference is nullable by
   * design: {@code null} means the sale has no such leg (a guest sale has no award, a card-only
   * sale no rebate or redemption, a rewards-covered sale no card leg), so no movement is produced
   * for it. Whether the resulting list may be empty is the caller's rule — the sessions guard it. A
   * card reference equal to the stored value one yields only the stored value movement.
   */
  public static List<ReversalMovement> ofSale(
      PoiRef card, PoiRef storedValue, PoiRef redemption, PoiRef rebate, PoiRef award) {
    return ofSale(
        List.of(),
        card == null ? List.of() : List.of(card),
        storedValue,
        redemption,
        rebate,
        award);
  }

  /**
   * A sale including stored value line fulfillments, reversed before its funding, and any number of
   * card payments, given in the order they were made and reversed newest first.
   */
  public static List<ReversalMovement> ofSale(
      List<StoredValueLoadRecord> storedValueLoads,
      List<PoiRef> cards,
      PoiRef storedValue,
      PoiRef redemption,
      PoiRef rebate,
      PoiRef award) {
    return ofSale(storedValueLoads, cards, List.of(), storedValue, redemption, rebate, award);
  }

  /**
   * A sale with Bilt rewards payments too (payments the terminal paid entirely with rewards), given
   * in the order they were made and reversed newest first, after the card payments: the same
   * reversal by POI reference, under {@link ReversalStep#BILT_REWARDS}.
   */
  public static List<ReversalMovement> ofSale(
      List<StoredValueLoadRecord> storedValueLoads,
      List<PoiRef> cards,
      List<PoiRef> biltRewards,
      PoiRef storedValue,
      PoiRef redemption,
      PoiRef rebate,
      PoiRef award) {
    List<ReversalMovement> movements = new ArrayList<>();
    for (StoredValueLoadRecord load : storedValueLoads) {
      movements.add(
          new ReversalMovement(
              ReversalStep.STORED_VALUE_LOAD,
              load.getPoiTransactionId(),
              load.getPoiTransactionTimestamp()));
    }
    // a reference listed twice is one payment: reversing it twice would fail the second time.
    // A card reference equal to the stored value one is a gift card payment recorded the old
    // way; it is reversed once, as the stored value leg.
    Set<String> cardTxnIds = new HashSet<>();
    for (int i = cards.size() - 1; i >= 0; i--) {
      PoiRef card = cards.get(i);
      boolean giftCard = storedValue != null && storedValue.txnId.equals(card.txnId);
      if (!giftCard && cardTxnIds.add(card.txnId)) {
        movements.add(
            new ReversalMovement(
                ReversalStep.CARD,
                card.txnId,
                card.timestamp,
                card.amount,
                TenderType.CARD,
                card.biltRewardsAmount));
      }
    }
    for (int i = biltRewards.size() - 1; i >= 0; i--) {
      PoiRef payment = biltRewards.get(i);
      if (cardTxnIds.add(payment.txnId)) {
        movements.add(
            new ReversalMovement(
                ReversalStep.BILT_REWARDS,
                payment.txnId,
                payment.timestamp,
                payment.amount,
                TenderType.BILT_REWARDS));
      }
    }
    add(movements, ReversalStep.STORED_VALUE, storedValue);
    add(movements, ReversalStep.REDEMPTION, redemption);
    add(movements, ReversalStep.REBATE, rebate);
    add(movements, ReversalStep.AWARD, award);
    return movements;
  }

  /** Adds the leg's movement; a {@code null} reference means the sale has no such leg. */
  private static void add(List<ReversalMovement> movements, ReversalStep step, PoiRef ref) {
    if (ref != null) {
      movements.add(new ReversalMovement(step, ref.txnId, ref.timestamp));
    }
  }
}
