/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.media.service;

import com.bilt.pos.media.AdInteraction;
import com.bilt.pos.media.Capabilities;
import com.bilt.pos.media.Placement;
import com.bilt.pos.widget.Cta;
import com.bilt.pos.widget.Rendering;
import java.time.Duration;
import java.util.Optional;

/**
 * The ad platform as the retail-media widget sees it: register a session, ask for decisions,
 * validate taps, report interactions, listen for platform-originated events.
 *
 * <p><b>Provisional.</b> This contract is defined ahead of the platform integration spec so the
 * widget, surfaces and emulator can be built against it; it will be revised when that spec lands
 * and the platform-backed implementation follows in RET-6784. Until then the only implementation is
 * {@link InMemoryAdDecisionService}. Method shapes may change; the principle that the service never
 * sees checkout session types (only {@link AdSessionSnapshot}) will not.
 *
 * <h2>Timeouts and failures</h2>
 *
 * <p>The register's checkout must never wait on advertising. Every method is bounded:
 *
 * <ul>
 *   <li>{@link #registerSession} and {@link #updateSession} return promptly. An implementation that
 *       talks to a network does so asynchronously behind the handle; registration never blocks on
 *       the platform, and a register that is slow or down manifests as decisions coming back empty,
 *       not as a stalled checkout.
 *   <li>{@link #decide} returns within the caller's {@code timeout} or answers {@link
 *       Optional#empty()}. A miss — no fill, a slow platform, a network failure, an unsupported
 *       creative — is an empty answer, never an exception. Only programming errors (a {@code null}
 *       argument, a handle that was never issued) throw.
 *   <li>{@link #validateAction} answers {@link ActionOutcome#rejected} rather than throwing when
 *       the platform refuses or cannot be reached; an unreachable platform means the offer is not
 *       applied, which is the safe default.
 *   <li>{@link #report} is fire-and-forget; implementations buffer and retry internally and drop on
 *       the floor before they block the caller.
 *   <li>{@link #subscribe} delivers on a service-owned thread and tolerates a listener that is
 *       slow; a listener that throws is logged and dropped from that delivery only.
 * </ul>
 *
 * <p>Implementations are thread-safe; the widget calls them from its executor and the surfaces'
 * callback threads.
 */
public interface AdDecisionService {

  /**
   * Announces a shopper session and returns the handle every later call uses. Returns promptly
   * without waiting on the platform.
   */
  SessionHandle registerSession(AdSessionSnapshot snapshot);

  /**
   * Replaces the service's view of the session with {@code snapshot}. The whole snapshot is sent
   * each time; the service diffs against what it has if it wants to. Returns promptly.
   */
  void updateSession(SessionHandle handle, AdSessionSnapshot snapshot);

  /**
   * Asks for a creative to show in {@code placement}, given what the register can render. Returns
   * within {@code timeout} either a rendering the platform decided on and the capabilities allow,
   * or empty for any kind of miss. Never throws for a miss.
   */
  Optional<Rendering> decide(
      SessionHandle handle, Placement placement, Capabilities capabilities, Duration timeout);

  /**
   * Presents a tapped {@code cta} for {@code creativeId} to the platform. The answer is accepted
   * with an {@link com.bilt.pos.media.Offer} for an {@code APPLY_OFFER}, accepted without one for
   * the other actions, or rejected with a diagnostic reason when the token is unknown, already
   * used, expired, or belongs to a different creative or session.
   */
  ActionOutcome validateAction(SessionHandle handle, Cta cta, String creativeId);

  /** Reports one interaction for measurement. Fire-and-forget; never blocks the caller. */
  void report(SessionHandle handle, AdInteraction interaction);

  /**
   * Opens the server-to-SDK event channel for {@code handle}: offers and interactions that
   * originate outside the SDK's own renderer (a hosted page, a send-to-phone confirmation). Close
   * the returned subscription to stop delivery; {@link #closeSession} closes it too.
   */
  AdEventSubscription subscribe(SessionHandle handle, AdEventListener listener);

  /**
   * Ends the session on the service side: outstanding tokens stop validating, subscriptions are
   * closed, and the handle is no longer accepted. Idempotent.
   */
  void closeSession(SessionHandle handle);
}
