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

/** Kind of shopper a {@link ShopperContext} describes. */
public enum ShopperKind {

  /** A known loyalty member. */
  MEMBER,

  /** A shopper the terminal could not tie to a member. */
  ANONYMOUS,

  /** A wire value this build does not recognize. */
  UNKNOWN;

  /**
   * Maps the wire value ({@code "member"}, {@code "anonymous"}); anything else is {@link #UNKNOWN}.
   */
  public static ShopperKind fromWire(String value) {
    if (value == null) {
      return UNKNOWN;
    }
    switch (value.toLowerCase(java.util.Locale.ROOT)) {
      case "member":
        return MEMBER;
      case "anonymous":
        return ANONYMOUS;
      default:
        return UNKNOWN;
    }
  }
}
