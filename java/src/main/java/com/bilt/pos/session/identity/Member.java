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
import java.util.Objects;

/**
 * The member attached to a shopper session, as the POS provides it or as identification resolved
 * it.
 *
 * <p>A member is in one of two states. <em>Resolved</em>: the Bilt member id is known — {@link
 * #id(String)} when the POS already has it, or what an identification reported ({@link
 * #resolved(IdentifyResult)}), which additionally carries the loyalty brand, rewards, and point
 * balance the terminal returned. <em>Pending resolution</em>: the POS has an identifier the shopper
 * is known by — an account id, phone number, email, or a retailer-specific value — and the session
 * resolves it in the background once the member is attached:
 *
 * <pre>{@code
 * session.member(Member.id("mbr_8f2a"));                              // attaches immediately
 * session.member(Member.idResolver().phone("+12015550123"));          // resolved in the background
 * session.member(Member.idResolver().accountId("4823").keyedByCashier());
 * session.member(null);                                               // signed out
 * }</pre>
 *
 * <p>Immutable, with value semantics. {@link #toString()} masks phone numbers and email addresses.
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
            : Collections.unmodifiableList(rewards);
    this.pointBalance = pointBalance;
    this.status = status;
    this.resolver = resolver;
  }

  /** A member the POS already knows the Bilt member id of. Attaches without any roundtrip. */
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

  /** {@code true} once the Bilt member id is known; {@code false} while a lookup is outstanding. */
  public boolean isResolved() {
    return resolver == null;
  }

  /** The Bilt member id, or {@code null} while pending resolution. */
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

  /**
   * A copy whose identifier is marked as typed in by the cashier rather than loaded from a profile;
   * a terminal lookup then sends {@code EntryMode=Keyed}.
   *
   * @throws IllegalStateException on a resolved member, which has no identifier to resolve
   */
  public Member keyedByCashier() {
    if (resolver == null) {
      throw new IllegalStateException("a resolved member has no identifier to mark as keyed");
    }
    return pending(resolver.withKeyedByCashier());
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

    /** A member known by a loyalty account id. */
    public Member accountId(String accountId) {
      return pending(MemberIdResolver.of(MemberIdResolver.Type.ACCOUNT_ID, null, accountId));
    }

    /** A member known by a phone number. */
    public Member phone(String phoneNumber) {
      return pending(MemberIdResolver.of(MemberIdResolver.Type.PHONE, null, phoneNumber));
    }

    /** A member known by an email address. */
    public Member email(String email) {
      return pending(MemberIdResolver.of(MemberIdResolver.Type.EMAIL, null, email));
    }

    /**
     * A member known by a retailer-specific identifier, e.g. {@code custom("retailer-card",
     * "99-1234")}; {@code type} names the identifier kind the resolving platform understands.
     */
    public Member custom(String type, String value) {
      return pending(MemberIdResolver.of(MemberIdResolver.Type.CUSTOM, type, value));
    }
  }
}
