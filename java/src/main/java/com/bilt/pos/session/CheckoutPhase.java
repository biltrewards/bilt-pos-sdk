/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session;

/**
 * Where a checkout stands, as far as shopper-facing widgets are concerned.
 *
 * <p>Widgets use the phase for eligibility: a retail media widget, for example, requests and shows
 * media only in the phases the retailer declared eligible and clears its surface on leaving one.
 * The phase lives on {@link SessionContext} and the POS may set it at any time on either session
 * type. A {@link TerminalShopperSession} also moves it itself around settlement — {@link
 * #TENDERING} when a settlement starts executing, {@link #COMPLETE} when it succeeds, back to the
 * phase it had when a settlement fails or is aborted, and {@link #SCANNING} when the basket is
 * cleared for the next transaction. {@link #MEMBER_IDENTIFIED} is never set automatically today; a
 * POS that wants it sets it when the shopper signs in.
 */
public enum CheckoutPhase {

  /** Items are being rung; the default phase of a new session and of a freshly cleared basket. */
  SCANNING,

  /** The shopper has been identified and the basket is still open. */
  MEMBER_IDENTIFIED,

  /** Payment is being taken; a terminal session enters it when settlement begins executing. */
  TENDERING,

  /** The transaction settled; a terminal session enters it when settlement succeeds. */
  COMPLETE
}
