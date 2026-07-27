/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.payment;

import com.bilt.pos.session.identity.RewardType;

/** A reward earned by the completed purchase. */
public final class EarnedReward {

    private final RewardType type;
    private final String description;
    private final int quantity;
    private final String rewardRef;

    public EarnedReward(RewardType type, String description, int quantity, String rewardRef) {
        this.type = type;
        this.description = description;
        this.quantity = quantity;
        this.rewardRef = rewardRef;
    }

    public RewardType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getRewardRef() {
        return rewardRef;
    }
}
