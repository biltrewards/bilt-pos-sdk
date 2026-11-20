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

/** The money tender a reversal or refund returned funds to. Loyalty movements are not tenders. */
public enum TenderType {
  /** A card payment, reversed or refunded on the terminal. */
  CARD,
  /** A stored value (gift card) payment, reversed or refunded on the terminal. */
  STORED_VALUE,
  /**
   * A register-managed tender such as cash. Voids and session refunds never return to it: the
   * terminal does not hold it. It is listed so a per-tender breakdown can name every tender.
   */
  EXTERNAL
}
