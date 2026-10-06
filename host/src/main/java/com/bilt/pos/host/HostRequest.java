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
 * The slice of an incoming request a {@link HostAuth} needs: method, path, headers and where it
 * came from. Kept server-agnostic so an auth policy is not tied to the HTTP library the host
 * happens to use.
 */
public interface HostRequest {

  /** The HTTP method, upper case; {@code GET} for a WebSocket upgrade. */
  String method();

  /** The request path without query string, for example {@code /v1/sessions/abc/basket}. */
  String path();

  /**
   * A request header, or {@code null} when absent. {@code Authorization} and {@code Origin} live
   * here.
   */
  String header(String name);

  /** The peer address as the server saw it. */
  String remoteAddress();
}
