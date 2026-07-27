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

/** Kind of a loyalty {@link Reward}. */
public enum RewardType {

    /** A redeemable reward (e.g. "$10 Off Purchase"). */
    REWARD,

    /** A coupon (e.g. "15% Off for Gold Members"). */
    COUPON,

    /** A point-based entitlement. */
    POINT;

    /** Maps the wire value ({@code "reward"}, {@code "coupon"}, {@code "point"}). */
    public static RewardType fromWire(String value) {
        if (value == null) {
            return null;
        }
        switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "reward":
                return REWARD;
            case "coupon":
                return COUPON;
            case "point":
                return POINT;
            default:
                return null;
        }
    }
}
