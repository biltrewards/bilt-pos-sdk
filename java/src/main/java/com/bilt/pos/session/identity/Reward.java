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

/**
 * A reward or coupon available to an identified member.
 *
 * <p>{@link #getRewardRef()} is the handle used to redeem the reward during
 * payment.</p>
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
}
