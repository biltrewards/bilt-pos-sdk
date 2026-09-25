/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.media.service;

import com.bilt.pos.media.Offer;
import java.util.Objects;
import java.util.Optional;

/**
 * The platform's answer to a tapped call to action.
 *
 * <p>Three shapes, told apart by {@link #isAccepted()} and {@link #getOffer()}: an {@code
 * APPLY_OFFER} the platform honours is {@link #accepted(Offer) accepted with an offer} for the
 * register to apply; any other accepted action ({@code SEND_TO_PHONE}, {@code DETAILS}, {@code
 * DISMISS}) is {@link #accepted() accepted without one}; and a token the platform does not
 * recognise, has already redeemed, or considers expired is {@link #rejected(String) rejected} with
 * a reason meant for logs, not for the shopper.
 */
public final class ActionOutcome {

  private final boolean accepted;
  private final Offer offer;
  private final String reason;

  private ActionOutcome(boolean accepted, Offer offer, String reason) {
    this.accepted = accepted;
    this.offer = offer;
    this.reason = reason;
  }

  /** An {@code APPLY_OFFER} the platform honours; {@code offer} is what the register applies. */
  public static ActionOutcome accepted(Offer offer) {
    return new ActionOutcome(true, Objects.requireNonNull(offer, "offer"), null);
  }

  /** An accepted action that carries no offer. */
  public static ActionOutcome accepted() {
    return new ActionOutcome(true, null, null);
  }

  /** The platform refused the action; {@code reason} is diagnostic, never shown to the shopper. */
  public static ActionOutcome rejected(String reason) {
    return new ActionOutcome(false, null, Objects.requireNonNull(reason, "reason"));
  }

  public boolean isAccepted() {
    return accepted;
  }

  /** The offer to apply, present only for an accepted {@code APPLY_OFFER}. */
  public Optional<Offer> getOffer() {
    return Optional.ofNullable(offer);
  }

  /** Why the action was rejected, or {@code null} when it was accepted. */
  public String getReason() {
    return reason;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ActionOutcome)) {
      return false;
    }
    ActionOutcome other = (ActionOutcome) o;
    return accepted == other.accepted
        && Objects.equals(offer, other.offer)
        && Objects.equals(reason, other.reason);
  }

  @Override
  public int hashCode() {
    return Objects.hash(accepted, offer, reason);
  }

  @Override
  public String toString() {
    if (!accepted) {
      return "ActionOutcome{rejected: " + reason + "}";
    }
    return offer == null ? "ActionOutcome{accepted}" : "ActionOutcome{accepted, " + offer + "}";
  }
}
