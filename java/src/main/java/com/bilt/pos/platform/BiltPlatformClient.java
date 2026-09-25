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

import java.util.concurrent.CompletableFuture;

/**
 * Executes authenticated requests against the Bilt platform.
 *
 * <p>This is the one seam through which every cloud-backed feature of the SDK reaches Bilt: member
 * resolution, session registration, the retail media widget's decision and event calls, and widgets
 * yet to come. It is deliberately small and transport-neutral. A request is a method, a relative
 * path, headers and bytes; a response is a status, headers and bytes. Endpoint paths and payload
 * shapes are owned by the features and by the platform integration spec, not by this interface.
 *
 * <p>Implementations own authentication end to end: they obtain an access token for the configured
 * {@link BiltCredentials}, attach it to every request, refresh it before it expires and retry a
 * request once when the platform reports the token stale. Callers never see a token.
 *
 * <p>{@link OkHttpPlatformClient} is the shipped implementation. Integrator tests may substitute
 * their own; the value types have public builders for that purpose.
 */
public interface BiltPlatformClient extends AutoCloseable {

  /**
   * Sends the request and blocks until the platform answers. Any HTTP status is returned as a
   * {@link PlatformResponse}; only the failure to obtain one throws.
   *
   * @throws PlatformAuthException when no access token could be obtained
   * @throws PlatformException when the exchange failed at the transport level
   * @throws IllegalStateException when the client has been closed
   */
  PlatformResponse execute(PlatformRequest request) throws PlatformException;

  /**
   * Sends the request on a background thread. The future completes with the response, or
   * exceptionally with the same {@link PlatformException} that {@link #execute} would have thrown.
   */
  CompletableFuture<PlatformResponse> executeAsync(PlatformRequest request);

  /**
   * Releases background threads and, for connections the client created itself, the connection
   * pool. Requests after {@code close()} fail with {@link IllegalStateException}. Closing twice is
   * harmless.
   */
  @Override
  void close();
}
