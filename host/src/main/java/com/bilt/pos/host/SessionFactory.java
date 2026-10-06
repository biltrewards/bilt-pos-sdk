/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.host;

import com.bilt.pos.session.ShopperSession;
import com.bilt.pos.session.TerminalShopperSession;

/**
 * Supplies the SDK builders a session is started from.
 *
 * <p>The host fills in everything the request carries — sale ID, currency, store location, the
 * terminal client, the initial member and context, widgets — and keeps the callback executor and
 * background-error hook for itself. What the embedding application owns is the rest of the
 * builder: Bilt platform credentials and environment for widgets, a display renderer, an external
 * display client. Return a fresh builder on each call; the SDK's builders are not reusable.
 *
 * <p>{@link #defaults()} returns the plain SDK builders, which is all a development host needs.
 */
public interface SessionFactory {

  /** The builder a {@code local} session starts from. */
  ShopperSession.Builder newLocalSession();

  /** The builder a {@code terminal} session starts from. */
  TerminalShopperSession.Builder newTerminalSession();

  /** Plain SDK builders with no credentials or customisation. */
  static SessionFactory defaults() {
    return new SessionFactory() {
      @Override
      public ShopperSession.Builder newLocalSession() {
        return ShopperSession.builder();
      }

      @Override
      public TerminalShopperSession.Builder newTerminalSession() {
        return TerminalShopperSession.builder();
      }
    };
  }
}
