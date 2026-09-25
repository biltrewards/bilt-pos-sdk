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

import com.bilt.pos.session.basket.BasketChange;
import com.bilt.pos.session.internal.BasketDisplay;
import com.bilt.pos.widget.SessionObserver;
import java.util.function.BooleanSupplier;

/**
 * The customer-display push behind {@code autoDisplay}: a {@link SessionObserver} the terminal
 * session registers, which sends every basket change it is handed to the terminal's display.
 *
 * <p>Sends run where observer callbacks run — on the session's operation lane, not the unordered
 * one — so pushes cannot race their snapshots out of order and never land mid-payment over the
 * terminal's payment screen: a {@code settle()} queued after a mutation waits for that mutation's
 * push first. Conflation is the observer queue's: since mutations return without waiting, a fast
 * ring-up outruns the terminal roundtrips, and the queue merges the changes that pile up behind an
 * in-flight send into one, so at most one send is queued behind the one in flight and it carries
 * the newest snapshot.
 *
 * <p>Failures are best-effort — the exception leaves this callback and the observer queue logs it
 * and reports it through {@code onBackgroundError}, never interrupting the checkout.
 */
final class AutoDisplayPush implements SessionObserver {

  private final BasketDisplay display;
  private final BooleanSupplier current;

  /**
   * @param current whether the snapshot still belongs on the display. A push that outlived its
   *     basket or session sends nothing because the settlement display or End bracket supersedes
   *     it.
   */
  AutoDisplayPush(BasketDisplay display, BooleanSupplier current) {
    this.display = display;
    this.current = current;
  }

  @Override
  public void basketChanged(BasketChange change) {
    if (current.getAsBoolean()) {
      display.show(change.current());
    }
  }
}
