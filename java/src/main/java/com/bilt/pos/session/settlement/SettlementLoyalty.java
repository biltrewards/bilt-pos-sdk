/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.settlement;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Who handles loyalty in a settlement: the SDK in separate loyalty steps ({@link #explicit()}), the
 * terminal during the card payment ({@link #implicit()}), or nobody ({@link #forbidden()}).
 *
 * <p>The card payment's Nexo {@code TransactionConditions.LoyaltyHandling} follows from it: absent
 * for {@code IMPLICIT} (the terminal may offer Bilt hosted checkout and earns automatically), and
 * {@code Forbidden} otherwise (no hosted checkout and no automatic earn). The Nexo payment cannot
 * tell rebates, points and award apart, so implicit handling covers all three.
 */
public final class SettlementLoyalty {

  /** Who handles loyalty. */
  public enum Handling {
    /** The SDK runs the selected loyalty steps; the card payment forbids loyalty. */
    EXPLICIT,
    /** No SDK loyalty steps; the terminal handles loyalty during the card payment. */
    IMPLICIT,
    /** No SDK loyalty steps; the card payment forbids loyalty. */
    FORBIDDEN
  }

  /** An SDK-run loyalty step of an {@link Handling#EXPLICIT} settlement. */
  public enum Step {
    /** Offer/rebate redemption before the payment. */
    REBATES,
    /** Point/reward redemption before the payment. */
    POINTS,
    /** The loyalty Award after the payment. */
    AWARD
  }

  private static final SettlementLoyalty IMPLICIT =
      new SettlementLoyalty(Handling.IMPLICIT, EnumSet.noneOf(Step.class));
  private static final SettlementLoyalty FORBIDDEN =
      new SettlementLoyalty(Handling.FORBIDDEN, EnumSet.noneOf(Step.class));

  private final Handling handling;
  private final Set<Step> steps;

  private SettlementLoyalty(Handling handling, Set<Step> steps) {
    this.handling = handling;
    this.steps = Collections.unmodifiableSet(steps);
  }

  /** The default: the SDK runs all loyalty steps. */
  public static SettlementLoyalty explicit() {
    return explicit(EnumSet.allOf(Step.class));
  }

  /** The SDK runs only {@code steps}; no steps is {@link #forbidden()}. */
  public static SettlementLoyalty explicit(Set<Step> steps) {
    Objects.requireNonNull(steps, "steps");
    return steps.isEmpty()
        ? FORBIDDEN
        : new SettlementLoyalty(Handling.EXPLICIT, EnumSet.copyOf(steps));
  }

  /** The terminal handles loyalty during the card payment. */
  public static SettlementLoyalty implicit() {
    return IMPLICIT;
  }

  /** No loyalty: no SDK steps, and the card payment forbids it. */
  public static SettlementLoyalty forbidden() {
    return FORBIDDEN;
  }

  public Handling getHandling() {
    return handling;
  }

  /** The SDK-run steps; empty unless {@link Handling#EXPLICIT}. */
  public Set<Step> getSteps() {
    return steps;
  }

  /** Whether the SDK runs {@code step}. */
  public boolean runs(Step step) {
    return steps.contains(step);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SettlementLoyalty)) {
      return false;
    }
    SettlementLoyalty other = (SettlementLoyalty) o;
    return handling == other.handling && steps.equals(other.steps);
  }

  @Override
  public int hashCode() {
    return Objects.hash(handling, steps);
  }

  @Override
  public String toString() {
    return handling == Handling.EXPLICIT ? "EXPLICIT" + steps : handling.name();
  }
}
