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

/**
 * Shopper details the terminal resolved for a wallet-pass sign-in, carried in the identification
 * response's {@code shopperContext.shopper} object.
 *
 * <p>Every field but {@link #getKind()} may be absent; {@link #getPointsBalance()} in particular is
 * {@code null} when the terminal did not report a balance — distinct from a real zero.
 */
public final class ShopperContext {

  private final ShopperKind kind;
  private final String walletIdentityId;
  private final String neighborhoodId;
  private final String firstName;
  private final String lastName;
  private final Integer pointsBalance;
  private final String tierName;

  public ShopperContext(
      ShopperKind kind,
      String walletIdentityId,
      String neighborhoodId,
      String firstName,
      String lastName,
      Integer pointsBalance,
      String tierName) {
    this.kind = kind == null ? ShopperKind.UNKNOWN : kind;
    this.walletIdentityId = walletIdentityId;
    this.neighborhoodId = neighborhoodId;
    this.firstName = firstName;
    this.lastName = lastName;
    this.pointsBalance = pointsBalance;
    this.tierName = tierName;
  }

  /** Whether the shopper is a member or anonymous; never {@code null}. */
  public ShopperKind getKind() {
    return kind;
  }

  /** The wallet pass identity the shopper tapped, or {@code null}. */
  public String getWalletIdentityId() {
    return walletIdentityId;
  }

  /** The shopper's neighborhood id, or {@code null}. */
  public String getNeighborhoodId() {
    return neighborhoodId;
  }

  /** The shopper's first name, or {@code null}. */
  public String getFirstName() {
    return firstName;
  }

  /** The shopper's last name, or {@code null}. */
  public String getLastName() {
    return lastName;
  }

  /** The shopper's point balance, or {@code null} when the terminal did not report one. */
  public Integer getPointsBalance() {
    return pointsBalance;
  }

  /** The shopper's loyalty tier name (e.g. {@code "Gold"}), or {@code null}. */
  public String getTierName() {
    return tierName;
  }
}
