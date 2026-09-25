/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.platform;

/**
 * A call to the Bilt platform could not produce a response.
 *
 * <p>Transport failures (connection refused, timeouts, TLS errors) are reported directly as this
 * type; the inability to obtain an access token is its subtype {@link PlatformAuthException}. A
 * non-2xx status from the API is not an exception but a {@link PlatformResponse}, since each
 * endpoint's error payloads belong to the feature that calls it.
 *
 * <p>Messages never contain credentials, tokens or response bodies. The token endpoint's body in
 * particular is only ever inspected for its {@code error} code, so a server that echoes request
 * material back cannot smuggle it into a log line.
 */
public class PlatformException extends Exception {

  private static final long serialVersionUID = 1L;

  public PlatformException(String message) {
    super(message);
  }

  public PlatformException(String message, Throwable cause) {
    super(message, cause);
  }
}
