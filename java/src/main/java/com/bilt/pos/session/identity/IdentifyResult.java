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

import java.util.Collections;
import java.util.List;

/**
 * Result of a member identification.
 *
 * <p>Member data ({@code memberId}, {@code rewards}, {@code pointBalance})
 * is populated only when {@link #getStatus()} is {@link IdentifyStatus#FOUND};
 * the other statuses describe why no member is attached (not found,
 * suspended, cancelled on the terminal).</p>
 */
public final class IdentifyResult {

    private final IdentifyStatus status;
    private final String memberId;
    private final String loyaltyBrand;
    private final List<Reward> rewards;
    private final int pointBalance;

    private IdentifyResult(IdentifyStatus status, String memberId, String loyaltyBrand,
                           List<Reward> rewards, int pointBalance) {
        this.status = status;
        this.memberId = memberId;
        this.loyaltyBrand = loyaltyBrand;
        this.rewards = rewards == null
                ? Collections.emptyList() : Collections.unmodifiableList(rewards);
        this.pointBalance = pointBalance;
    }

    /** A member was found. */
    public static IdentifyResult found(String memberId, String loyaltyBrand,
                                       List<Reward> rewards, int pointBalance) {
        return new IdentifyResult(IdentifyStatus.FOUND, memberId, loyaltyBrand,
                rewards, pointBalance);
    }

    /** No member is attached; {@code status} says why. */
    public static IdentifyResult withoutMember(IdentifyStatus status) {
        if (status == IdentifyStatus.FOUND) {
            throw new IllegalArgumentException("FOUND requires member data");
        }
        return new IdentifyResult(status, null, null, null, 0);
    }

    public IdentifyStatus getStatus() {
        return status;
    }

    /** The member's loyalty account ID, or {@code null} unless {@code FOUND}. */
    public String getMemberId() {
        return memberId;
    }

    /** The loyalty program name (e.g. {@code "K-Club"}), or {@code null}. */
    public String getLoyaltyBrand() {
        return loyaltyBrand;
    }

    /** Active rewards and coupons. Never {@code null}. */
    public List<Reward> getRewards() {
        return rewards;
    }

    /** Available point balance; {@code 0} when not reported. */
    public int getPointBalance() {
        return pointBalance;
    }
}
