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
    this.step = step;
    this.poiTransactionId = poiTransactionId;
    this.poiTransactionTimestamp = poiTransactionTimestamp;
    this.amount = amount;
    this.tender = tender;
  }

  private static TenderType defaultTender(ReversalStep step) {
    if (step == ReversalStep.CARD) {
      return TenderType.CARD;
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
                ReversalStep.CARD, card.txnId, card.timestamp, card.amount, TenderType.CARD));
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
