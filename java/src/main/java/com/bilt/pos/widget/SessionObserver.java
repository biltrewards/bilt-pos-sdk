/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.widget;

import com.bilt.pos.session.SessionContextSnapshot;
import com.bilt.pos.session.basket.BasketChange;
import com.bilt.pos.session.identity.Member;

/**
 * The seam between a shopper session and whatever reacts to its state: the terminal's customer
 * display push inside the SDK, and every {@link Widget}. The session fires these; observers never
 * call back into it from them.
 *
 * <p>This is an internal-facing contract. It is public only because {@link Widget} extends it and
 * widgets are public API; integrators use shipped widgets and neither implement nor register an
 * observer themselves. Every method is a no-op by default, so an observer overrides only what it
 * needs. The type lives in this package rather than {@code com.bilt.pos.session} to keep the
 * session package's public surface about what a register calls, and the widget author's contracts
 * together.
 *
 * <h2>Delivery</h2>
 *
 * <p>Callbacks run on the session's operation lane — the single thread that also runs the session's
 * lazy operations — never on the register thread that changed the state and never under the
 * session's lock. Calls are serialized: an observer is never entered from two threads at once and
 * sees its callbacks in the order the changes happened, the same order for every observer of the
 * session. Because the lane is shared with settlement and the display push, a callback must return
 * promptly; anything slow (a network request, a render) belongs on the observer's own executor. An
 * exception thrown from a callback is contained and reported through the session's {@code
 * onBackgroundError} handler; it never reaches the register and never affects other observers.
 *
 * <p>Basket changes are conflated: when changes arrive faster than the lane delivers them, the
 * undelivered ones are merged into a single {@link BasketChange} whose {@link
 * BasketChange#previous() previous()} is the last snapshot delivered and whose {@link
 * BasketChange#current() current()} is the newest. The diff spans the whole gap, so an observer
 * always learns what changed since it last looked, but it may never see the intermediate states and
 * a merged change carries source {@link BasketChange.Source#BATCH BATCH} when the merged changes
 * had different sources. A change that merges away to nothing — an item added and removed again
 * before delivery — is dropped. Context snapshots conflate the same way, to the newest snapshot.
 * Member changes and the lifecycle callbacks are never conflated.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>{@link #started(SessionContextSnapshot)} is the first callback and {@link #ended()} the last;
 * nothing is delivered before or after them. When the session starts with a member already
 * attached, {@link #memberChanged(Member)} follows {@code started} with that member, so an observer
 * can rely on member callbacks alone for the member state.
 */
public interface SessionObserver {

  /** The session has started; {@code context} is its context at that moment. */
  default void started(SessionContextSnapshot context) {}

  /**
   * The session's member changed: attached by the register, found by a terminal prompt, resolved by
   * a background lookup, or cleared — {@code member} is {@code null} when the shopper signed out
   * and may be {@linkplain Member#isResolved() pending} when a lookup is still under way.
   */
  default void memberChanged(Member member) {}

  /** The session's context changed; {@code context} is the whole context after the change. */
  default void contextChanged(SessionContextSnapshot context) {}

  /**
   * The basket changed. {@code change} carries the snapshots before and after and the diff between
   * them; see the class documentation for how fast changes are conflated.
   */
  default void basketChanged(BasketChange change) {}

  /** The session has ended, normally or forcibly. No further callbacks follow. */
  default void ended() {}
}
