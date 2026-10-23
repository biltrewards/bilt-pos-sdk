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
 * Mints the Nexo {@code ServiceID} of every request the SDK sends to the terminal: settlement legs
 * and their retries, Transaction Status recovery polls, aborts, input updates, displays, session
 * signals, and terminal services. Supply one through {@code serviceIdProvider(...)} on {@link
 * TerminalShopperSession.Builder} or {@link Terminal.Builder} when the register needs ids it can
 * persist or derive, e.g. to run Transaction Status after a crash.
 *
 * <p>Each call must return a fresh id of 1–10 characters {@code [A-Za-z0-9]}, unique within the
 * terminal's correlation window. An id outside that format is rejected before anything is sent: the
 * request fails with a {@link SessionException} and nothing reaches the terminal. Without a
 * provider the SDK mints random 10-character uppercase alphanumeric ids.
 *
 * <p>Called on the session's operation thread, synchronously, before the request it names is sent.
 */
@FunctionalInterface
public interface ServiceIdProvider {

  /** Returns the {@code ServiceID} for the next request. */
  String next();
}
