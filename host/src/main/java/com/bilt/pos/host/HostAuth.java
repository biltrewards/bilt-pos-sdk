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

/**
 * Decides whether a request may reach the protocol.
 *
 * <p>The host consults it once per HTTP request and once per event-stream connection, before any
 * handler runs; {@code GET /health} is exempt so an install prompt can probe an unpaired host. A
 * refusal is answered with 401 and a {@code SessionError} body. What a token means is the embedding
 * application's business: the bridge will check its per-origin pairing tokens here, the cloud
 * service its connection tokens.
 *
 * <p>This iteration ships only {@link #permitAll()}, the development-mode default: {@code
 * Authorization} is accepted and ignored.
 */
@FunctionalInterface
public interface HostAuth {

  /** Whether this request may proceed. */
  boolean permits(HostRequest request);

  /** Lets everything through; the development-mode default. */
  static HostAuth permitAll() {
    return request -> true;
  }
}
