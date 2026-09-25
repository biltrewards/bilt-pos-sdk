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

import java.util.Map;

/**
 * What the POS knows about the checkout that any widget may use: the {@link CheckoutPhase} and
 * free-form attributes, alongside the lane identifiers the session was built with. Obtained from
 * {@link ShopperSession#context()}; the session owns it, and there is one per session.
 *
 * <p>The context is mutable and can be updated at any time, from any thread: every call is pure
 * local compute under the session's lock, and nothing reaches a terminal or the platform because of
 * it. It is deliberately small — widget-specific tuning lives on the widget, not here. Writes are
 * refused with {@code IllegalStateException} once the session has ended, like basket changes.
 *
 * <pre>{@code
 * session.context().phase(CheckoutPhase.TENDERING);
 * session.context().attribute("lane-type", "pharmacy");
 * session.context().attribute("cashier-assisted", "true");
 * }</pre>
 *
 * <p>Readers that need a consistent point-in-time view take a {@link #snapshot()}; the live
 * accessors each read one field at a time.
 */
public interface SessionContext {

  /** The current checkout phase; {@link CheckoutPhase#SCANNING} until something moves it. */
  CheckoutPhase phase();

  /**
   * Moves the checkout to the given phase. On a terminal session the session's own transitions
   * around settlement may later override it — see {@link CheckoutPhase}.
   *
   * @throws NullPointerException if {@code phase} is null
   * @throws IllegalStateException if the session has ended
   */
  SessionContext phase(CheckoutPhase phase);

  /**
   * Sets a free-form attribute — targeting and policy input for widgets, such as {@code
   * "lane-type"} → {@code "pharmacy"}. Replaces any earlier value under the same key; a {@code
   * null} value removes the key, like {@link #removeAttribute(String)}.
   *
   * @throws NullPointerException if {@code key} is null
   * @throws IllegalStateException if the session has ended
   */
  SessionContext attribute(String key, String value);

  /**
   * Removes an attribute; a key that is not set is left alone.
   *
   * @throws NullPointerException if {@code key} is null
   * @throws IllegalStateException if the session has ended
   */
  SessionContext removeAttribute(String key);

  /**
   * The attributes as they are now: an unmodifiable copy in insertion order that later changes do
   * not affect.
   */
  Map<String, String> attributes();

  /** The register's identifier for this lane, as configured on the builder. */
  String saleId();

  /** ISO 4217 currency code of the session. */
  String currency();

  /** Store location identifier, or {@code null} if not configured. */
  String storeLocation();

  /** The terminal identifier on a {@link TerminalShopperSession}; {@code null} on a local one. */
  String poiId();

  /** A consistent, immutable copy of the whole context as it is now. */
  SessionContextSnapshot snapshot();
}
