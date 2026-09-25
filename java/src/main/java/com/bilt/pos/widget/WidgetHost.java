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

import com.bilt.pos.platform.BiltCredentials;
import com.bilt.pos.platform.BiltEnvironment;
import com.bilt.pos.platform.BiltPlatformClient;
import com.bilt.pos.session.SessionContextSnapshot;
import com.bilt.pos.session.SessionError;
import java.util.concurrent.Executor;

/**
 * What a {@link Widget} may use of the session that owns it, handed over in {@link
 * Widget#attach(WidgetHost)}. It is the widget's whole view of the session: a widget never holds
 * the session itself, so it cannot ring the basket or move money, and everything it needs from the
 * platform side — credentials, environment, a ready client — comes from here rather than from its
 * own configuration.
 *
 * <p>Implemented by the session; integrators do not implement this.
 */
public interface WidgetHost {

  /** The identifier of the owning session, as {@code ShopperSession.getSessionId()} reports it. */
  String sessionId();

  /**
   * The credentials the session was built with, or {@code null} when it has none. A widget that
   * needs the platform checks {@link #platformClient()} instead; this is for widgets that talk to
   * the platform through a channel of their own.
   */
  BiltCredentials credentials();

  /** The platform deployment the session was built for; never {@code null}. */
  BiltEnvironment environment();

  /**
   * A client for the Bilt platform, authenticated with the session's credentials against its
   * environment. Created by the session the first time a widget asks and shared by every widget of
   * the session; the session closes it when it ends, so a widget must not. {@code null} when the
   * session was built without credentials — a widget that cannot work without the platform throws
   * from {@link Widget#attach(WidgetHost)} then, with a {@code SessionError} that says so.
   */
  BiltPlatformClient platformClient();

  /**
   * The session's operation lane: the single thread its operations, and every observer callback,
   * run on. Work submitted here runs in order with the session's own work and blocks it while it
   * runs, so it suits short state updates that must be ordered with the callbacks, not anything
   * that waits on the network or the screen.
   */
  Executor operationExecutor();

  /**
   * Where the session delivers its handlers to the register — the builder's {@code
   * callbackExecutor}, or an executor that runs on the calling thread when none was configured. A
   * widget delivers its own register-facing callbacks here so they arrive the way the register
   * configured for the session.
   */
  Executor callbackExecutor();

  /**
   * Reports a failure of the widget's background work — a decision request that failed, a surface
   * that could not render — through the session's {@code onBackgroundError} handler, the one place
   * a register hears about failures that have no result object to carry them. Fire-and-forget;
   * never throws.
   */
  void reportBackgroundError(SessionError error);

  /** The session's context as of now: phase, attributes and lane identifiers. */
  SessionContextSnapshot context();
}
