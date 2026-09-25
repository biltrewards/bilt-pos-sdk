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

/**
 * Where a {@link Surface} reports what the shopper did with a {@link Rendering}.
 *
 * <p>The widget hands a sink to the surface together with each rendering, and the sink is bound to
 * that rendering. Every callback is fire-and-forget from the surface's point of view: it returns
 * promptly, never throws, and may be invoked from any thread.
 *
 * <h2>Token validation</h2>
 *
 * <p>The sink validates every {@link Cta} it receives against the rendering it handed out — the
 * token must be one of that rendering's own tokens and the rendering must still be the one on
 * display. A foreign token (from another creative or another session), a stale one (the rendering
 * has since been replaced or cleared) or a tampered one is rejected: nothing is applied, and the
 * rejection is reported through the widget's background-error channel. It is never thrown back to
 * the surface, so a renderer — native or a web page — cannot crash the register by sending the
 * wrong thing.
 */
public interface ActionSink {

  /**
   * The shopper tapped {@code cta}. The sink validates the token and, if the platform accepts it,
   * performs the {@link Action}; an {@code APPLY_OFFER} then reaches the register as a validated
   * offer.
   */
  void perform(Cta cta);

  /**
   * The rendering became visible to the shopper for long enough to count as seen, by whatever
   * viewability rule the surface applies. Fires at most once per rendering.
   */
  void viewed(Rendering rendering);

  /**
   * The shopper closed the rendering from the surface itself (a close affordance rather than a
   * {@link Action#DISMISS} CTA).
   */
  void dismissed(Rendering rendering);

  /** The rendering's video played to its end. Only surfaces that play video report this. */
  void completed(Rendering rendering);
}
