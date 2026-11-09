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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The member attached to a shopper session, as the POS provides it or as identification resolved
 * it.
 *
 * <p>A member is in one of two states. <em>Resolved</em>: the loyalty member id is known — {@link
 * #id(String)} when the POS already has it, or what an identification reported ({@link
 * #resolved(IdentifyResult)}), which additionally carries the loyalty brand, rewards, and point
 * balance the terminal returned. <em>Pending resolution</em>: the POS has an identifier the shopper
 * is known by — an account id, phone number, email, or a retailer-specific value — and the session
 * resolves it in the background once the member is attached:
 *
 * <pre>{@code
 * // Attaches immediately
 * session.member(Member.id("mbr_8f2a"));
 * // Resolved in the background
 * session.member(Member.idResolver().lookup(LookupType.PHONE, "+12015550123"));
 * session.member(Member.idResolver().lookup(LookupType.ACCOUNT_NUMBER, "4823"));
 * // Signed out
 * session.member(null);
 * }</pre>
 *
 * <p>Immutable, with value semantics. {@link #toString()} masks every lookup identifier.
 */
public final class Member {

  private final String memberId;
  private final String loyaltyBrand;
  private final List<Reward> rewards;
  private final int pointBalance;
  private final IdentifyStatus status;
  private final MemberIdResolver resolver;

  private Member(
      String memberId,
      String loyaltyBrand,
      List<Reward> rewards,
      int pointBalance,
      IdentifyStatus status,
      MemberIdResolver resolver) {
    this.memberId = memberId;
    this.loyaltyBrand = loyaltyBrand;
    this.rewards =
        rewards == null || rewards.isEmpty()
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(rewards));
    this.pointBalance = pointBalance;
    this.status = status;
    this.resolver = resolver;
  }

  /**
   * The already-resolved loyalty member id supplied by the POS. Attaches immediately; terminal
   * sessions send AccountNumber/File and upgrade it with balance, brand and rewards on reply. For
   * an enterprise loyalty program, this is the same id used with that program's API.
   */
  public static Member id(String memberId) {
    Objects.requireNonNull(memberId, "memberId");
    if (memberId.isEmpty()) {
      throw new IllegalArgumentException("memberId must not be empty");
    }
    return new Member(memberId, null, null, 0, IdentifyStatus.FOUND, null);
  }

  /**
   * The member an identification found, carrying everything the terminal reported: id, loyalty
   * brand, rewards, and point balance.
   *
   * @throws IllegalArgumentException unless the result's status is {@link IdentifyStatus#FOUND}
   */
  public static Member resolved(IdentifyResult result) {
    Objects.requireNonNull(result, "result");
    if (result.getStatus() != IdentifyStatus.FOUND || result.getMemberId() == null) {
      throw new IllegalArgumentException(
          "only a FOUND identification carries a member; status was " + result.getStatus());
    }
    return new Member(
        result.getMemberId(),
        result.getLoyaltyBrand(),
        result.getRewards(),
        result.getPointBalance(),
        IdentifyStatus.FOUND,
        null);
  }

  /**
   * Factories for a member that still needs resolving from an identifier the POS has on file. Each
   * returns a {@link Member} whose {@link #isResolved()} is {@code false}; attaching it to a
   * session starts the lookup.
   */
  public static IdResolver idResolver() {
    return IdResolver.INSTANCE;
  }

  private static Member pending(MemberIdResolver resolver) {
    return new Member(null, null, null, 0, null, resolver);
  }

  /**
   * {@code true} once the loyalty member id is known; {@code false} while a lookup is outstanding.
   */
  public boolean isResolved() {
    return resolver == null;
  }

  /** The loyalty member id, or {@code null} while pending. */
  public String memberId() {
    return memberId;
  }

  /** How this member is to be looked up, or {@code null} once resolved. */
  public MemberIdResolver resolver() {
    return resolver;
  }

  /** {@link IdentifyStatus#FOUND} for a resolved member; {@code null} while pending. */
  public IdentifyStatus status() {
    return status;
  }

  /** The loyalty program name the terminal reported, e.g. {@code "K-Club"}, or {@code null}. */
  public String loyaltyBrand() {
    return loyaltyBrand;
  }

  /** Active rewards and coupons the terminal reported. Never {@code null}, empty when unknown. */
  public List<Reward> rewards() {
    return rewards;
  }

  /** The point balance the terminal reported; {@code 0} when not reported. */
  public int pointBalance() {
    return pointBalance;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Member)) {
      return false;
    }
    Member that = (Member) other;
    return Objects.equals(memberId, that.memberId)
        && Objects.equals(loyaltyBrand, that.loyaltyBrand)
        && rewards.equals(that.rewards)
        && pointBalance == that.pointBalance
        && status == that.status
        && Objects.equals(resolver, that.resolver);
  }

  @Override
  public int hashCode() {
    return Objects.hash(memberId, loyaltyBrand, rewards, pointBalance, status, resolver);
  }

  @Override
  public String toString() {
    if (resolver != null) {
      return "Member{pending " + resolver + "}";
    }
    StringBuilder text = new StringBuilder("Member{id=").append(memberId);
    if (loyaltyBrand != null) {
      text.append(", brand=").append(loyaltyBrand);
    }
    if (pointBalance != 0) {
      text.append(", points=").append(pointBalance);
    }
    if (!rewards.isEmpty()) {
      text.append(", rewards=").append(rewards.size());
    }
    return text.append('}').toString();
  }

  /** The factories behind {@link Member#idResolver()}. */
  public static final class IdResolver {

    private static final IdResolver INSTANCE = new IdResolver();

    private IdResolver() {}

    /** A member known by a customer identifier; terminal lookups use Keyed entry mode. */
    public Member lookup(LookupType type, String value) {
      return pending(MemberIdResolver.of(type, value));
    }
  }
}
