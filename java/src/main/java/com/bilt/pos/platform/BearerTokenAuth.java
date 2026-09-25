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
 * <p>OkHttp only lets these hooks throw {@link IOException}, so a token failure travels as a {@link
 * TokenUnavailableException} and is unwrapped into its {@link PlatformException} by the caller.
 */
final class BearerTokenAuth implements Interceptor, Authenticator {

  private static final String BEARER_PREFIX = "Bearer ";

  private final ClientCredentialsTokenSource tokenSource;

  BearerTokenAuth(ClientCredentialsTokenSource tokenSource) {
    this.tokenSource = tokenSource;
  }

  /** Tags a request with the longest it may wait for a token; untagged requests wait as needed. */
  static final class TokenWait {
    final Duration timeout;

    TokenWait(Duration timeout) {
      this.timeout = timeout;
    }
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
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
    String used = request.header("Authorization");
    if (used != null && used.startsWith(BEARER_PREFIX)) {
      tokenSource.invalidate(used.substring(BEARER_PREFIX.length()));
    }
    return withToken(request, accessToken(request));
  }

  private String accessToken(Request request) throws TokenUnavailableException {
    TokenWait wait = request.tag(TokenWait.class);
    try {
      return tokenSource.accessToken(wait != null ? wait.timeout : null);
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
