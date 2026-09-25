/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.identity;

import java.time.Instant;
import java.util.Objects;

/**
 * A reward or coupon available to an identified member.
 *
 * <p>{@link #getRewardRef()} is the handle used to redeem the reward during payment.
 */
public final class Reward {

  private final String rewardRef;
  private final RewardType type;
  private final String description;
  private final Instant expirationDate;

  public Reward(String rewardRef, RewardType type, String description, Instant expirationDate) {
    this.rewardRef = rewardRef;
    this.type = type;
    this.description = description;
    this.expirationDate = expirationDate;
  }

  /** Redemption handle, e.g. {@code "rwd:RWD-44021"}. */
  public String getRewardRef() {
    return rewardRef;
  }

  public RewardType getType() {
    return type;
  }

  public String getDescription() {
    return description;
  }

  /** Expiry, or {@code null} if the reward does not expire. */
  public Instant getExpirationDate() {
    return expirationDate;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Reward)) {
      return false;
    }
    Reward that = (Reward) other;
    return Objects.equals(rewardRef, that.rewardRef)
        && type == that.type
        && Objects.equals(description, that.description)
        && Objects.equals(expirationDate, that.expirationDate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(rewardRef, type, description, expirationDate);
  }

  @Override
  public String toString() {
    return "Reward{" + rewardRef + ", " + type + ", " + description + "}";
  }
}
