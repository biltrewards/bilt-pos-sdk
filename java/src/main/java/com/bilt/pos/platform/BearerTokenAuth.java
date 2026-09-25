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

import java.io.IOException;
import java.time.Duration;
import okhttp3.Authenticator;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

/**
 * Attaches the client-credentials access token to API calls and recovers from a rejected one.
 *
 * <p>As an application {@link Interceptor} it adds {@code Authorization: Bearer <token>} to each
 * call. As the client's {@link Authenticator} it answers a {@code 401} by invalidating the token
 * the call carried and retrying once with a fresh one; a {@code 401} on the retry is handed back as
 * the response. It must be installed on a client that the token source does not itself use, or the
 * token request would recurse into it.
 *
 * <p>A token is only ever sent to a URL {@linkplain #isWithin within} the API base: same scheme,
 * host and port, and a path beneath the base path. The application interceptor sees a call only
 * once, before any redirect, and OkHttp itself drops {@code Authorization} on a redirect only when
 * the scheme, host or port changes. {@link #redirectGuard()} is therefore also installed as a
 * network interceptor, which runs on every hop and strips the token from any hop outside the base,
 * including a same-host redirect out of the base path. A {@code 401} from outside the base is not
 * answered with a fresh token.
 *
 * <p>OkHttp only lets these hooks throw {@link IOException}, so a token failure travels as a {@link
 * TokenUnavailableException} and is unwrapped into its {@link PlatformException} by the caller.
 */
final class BearerTokenAuth implements Interceptor, Authenticator {

  private static final String BEARER_PREFIX = "Bearer ";

  private final ClientCredentialsTokenSource tokenSource;
  private final HttpUrl apiBase;

  /** {@code apiBase} must end in a slash, so that its path is a whole-segment prefix. */
  BearerTokenAuth(ClientCredentialsTokenSource tokenSource, HttpUrl apiBase) {
    this.tokenSource = tokenSource;
    this.apiBase = apiBase;
  }

  /**
   * Whether {@code url} addresses the API behind {@code apiBase}. OkHttp has already normalised
   * {@code url}, so dot segments, percent-encoded dots and backslashes cannot hide an escape.
   */
  /**
   * A network interceptor that removes {@code Authorization} from any hop that has left the API
   * base. Redirects that stay within the base keep the token.
   */
  Interceptor redirectGuard() {
    return chain -> {
      Request request = chain.request();
      if (request.header("Authorization") != null && !isWithin(apiBase, request.url())) {
        request = request.newBuilder().removeHeader("Authorization").build();
      }
      return chain.proceed(request);
    };
  }

  static boolean isWithin(HttpUrl apiBase, HttpUrl url) {
    return url.scheme().equals(apiBase.scheme())
        && url.host().equals(apiBase.host())
        && url.port() == apiBase.port()
        && url.encodedPath().startsWith(apiBase.encodedPath());
  }

  /**
   * Tags a request with the deadline of the call it belongs to. Every token wait in the call, the
   * first one and the one after a {@code 401}, takes only the time left, because OkHttp's call
   * timeout cancels the exchange but does not interrupt a thread waiting here. Untagged requests,
   * and calls without a timeout, wait as long as the token request takes.
   */
  static final class TokenWait {
    private final long deadlineNanos;
    private final boolean bounded;

    private TokenWait(long deadlineNanos, boolean bounded) {
      this.deadlineNanos = deadlineNanos;
      this.bounded = bounded;
    }

    /**
     * A call that must finish within {@code timeout} from now; {@code null} or zero is unbounded.
     */
    static TokenWait startingNow(Duration timeout) {
      return timeout == null || timeout.isZero()
          ? new TokenWait(0, false)
          : new TokenWait(System.nanoTime() + timeout.toNanos(), true);
    }

    Duration remaining() {
      return bounded ? Duration.ofNanos(Math.max(0, deadlineNanos - System.nanoTime())) : null;
    }
  }

  // Runs once per call, before any redirect; redirectGuard() keeps later hops within the base.
  @Override
  public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    if (!isWithin(apiBase, request.url())) {
      return chain.proceed(request);
    }
    return chain.proceed(withToken(request, accessToken(request)));
  }

  @Override
  public Request authenticate(Route route, Response response) throws IOException {
    for (Response prior = response.priorResponse(); prior != null; prior = prior.priorResponse()) {
      if (prior.code() == 401) {
        return null;
      }
    }
    Request request = response.request();
    if (!isWithin(apiBase, request.url())) {
      return null;
    }
    String used = request.header("Authorization");
    if (used != null && used.startsWith(BEARER_PREFIX)) {
      tokenSource.invalidate(used.substring(BEARER_PREFIX.length()));
    }
    return withToken(request, accessToken(request));
  }

  private String accessToken(Request request) throws TokenUnavailableException {
    TokenWait wait = request.tag(TokenWait.class);
    try {
      return tokenSource.accessToken(wait != null ? wait.remaining() : null);
    } catch (PlatformException e) {
      throw new TokenUnavailableException(e);
    }
  }

  private static Request withToken(Request request, String accessToken) {
    return request.newBuilder().header("Authorization", BEARER_PREFIX + accessToken).build();
  }

  /** Carries a token failure through OkHttp, which only propagates {@link IOException}s. */
  static final class TokenUnavailableException extends IOException {
    private static final long serialVersionUID = 1L;

    TokenUnavailableException(PlatformException cause) {
      super(cause.getMessage(), cause);
    }

    PlatformException platformException() {
      return (PlatformException) getCause();
    }
  }
}
