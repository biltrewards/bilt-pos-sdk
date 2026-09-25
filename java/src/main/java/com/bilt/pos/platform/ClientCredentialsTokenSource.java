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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Obtains and caches an access token through the OAuth 2.0 client-credentials grant.
 *
 * <p>The grant is a {@code POST} of {@code grant_type=client_credentials} to the token endpoint,
 * with the client id and secret as HTTP Basic credentials, each form-encoded first as RFC 6749
 * section 2.3.1 requires. The answer's {@code access_token}, {@code token_type} (which must be
 * {@code Bearer}) and {@code expires_in} are read; anything else is ignored.
 *
 * <p>Lifecycle of a token: it is served from {@link #accessToken} until {@code expires_in} has
 * elapsed. A background refresh is scheduled {@code refreshSkew} before that moment, so under
 * normal conditions callers never wait for the token endpoint after the first call. Should the
 * proactive refresh fail, each later call retries it opportunistically while the current token is
 * still valid, and only once the token has actually expired do callers block on the refresh. A
 * token without {@code expires_in} is kept until a caller {@linkplain #invalidate invalidates} it
 * after a {@code 401}.
 *
 * <p>All refreshes run on one daemon thread, and at most one is in flight: every caller arriving
 * during a refresh joins the same future. Neither the secret nor any token is logged.
 */
final class ClientCredentialsTokenSource implements AutoCloseable {

  private static final Logger LOG = Logger.getLogger(ClientCredentialsTokenSource.class.getName());
  private static final MediaType FORM_MEDIA_TYPE =
      MediaType.get("application/x-www-form-urlencoded");
  private static final String GRANT_BODY = "grant_type=client_credentials";

  private final BiltCredentials credentials;
  private final URI tokenEndpoint;
  private final OkHttpClient httpClient;
  private final String userAgent;
  private final Duration refreshSkew;
  private final Clock clock;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ScheduledExecutorService refresher =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "bilt-platform-token-refresh");
            thread.setDaemon(true);
            return thread;
          });

  private final Object lock = new Object();
  private CachedToken current;
  private CompletableFuture<CachedToken> inFlight;
  private ScheduledFuture<?> scheduledRefresh;
  private boolean closed;

  ClientCredentialsTokenSource(
      BiltCredentials credentials,
      URI tokenEndpoint,
      OkHttpClient httpClient,
      String userAgent,
      Duration refreshSkew,
      Clock clock) {
    if (credentials.kind() != BiltCredentials.Kind.CLIENT_CREDENTIALS) {
      throw new IllegalArgumentException("Unsupported credential kind: " + credentials.kind());
    }
    this.credentials = credentials;
    this.tokenEndpoint = tokenEndpoint;
    this.httpClient = httpClient;
    this.userAgent = userAgent;
    this.refreshSkew = refreshSkew;
    this.clock = clock;
  }

  /**
   * The current access token, fetching or awaiting a refresh when none is valid. Waits at most
   * {@code timeout} for a refresh when one is given.
   */
  String accessToken(Duration timeout) throws PlatformException {
    CompletableFuture<CachedToken> pending;
    synchronized (lock) {
      if (closed) {
        throw new IllegalStateException("platform client is closed");
      }
      Instant now = clock.instant();
      if (current != null && !current.isExpired(now)) {
        if (current.isDueForRefresh(now) && inFlight == null) {
          startRefreshLocked();
        }
        return current.accessToken;
      }
      pending = inFlight != null ? inFlight : startRefreshLocked();
    }
    return await(pending, timeout).accessToken;
  }

  /**
   * Drops the cached token if it is still the one the caller used, so that the next {@link
   * #accessToken} fetches a fresh one. A token that has already been replaced is left alone, which
   * keeps a burst of {@code 401}s from discarding the replacement.
   */
  void invalidate(String accessToken) {
    synchronized (lock) {
      if (current != null && current.accessToken.equals(accessToken)) {
        current = null;
      }
    }
  }

  /**
   * Stops refreshing and releases every caller still waiting for a token with a {@link
   * PlatformException}. The pending refresh may be discarded by the executor shutdown before it
   * runs, so its future is failed here rather than left to the refresh task.
   */
  @Override
  public void close() {
    CompletableFuture<CachedToken> pending;
    synchronized (lock) {
      closed = true;
      current = null;
      pending = inFlight;
      inFlight = null;
      if (scheduledRefresh != null) {
        scheduledRefresh.cancel(false);
      }
    }
    refresher.shutdownNow();
    if (pending != null) {
      pending.completeExceptionally(
          new PlatformException("Platform client closed while waiting for an access token"));
    }
  }

  private CompletableFuture<CachedToken> startRefreshLocked() {
    CompletableFuture<CachedToken> future = new CompletableFuture<>();
    inFlight = future;
    refresher.execute(() -> refresh(future));
    return future;
  }

  private void refresh(CompletableFuture<CachedToken> future) {
    CachedToken token;
    try {
      token = fetchToken();
    } catch (PlatformAuthException | RuntimeException e) {
      synchronized (lock) {
        if (inFlight == future) {
          inFlight = null;
        }
      }
      LOG.log(Level.WARNING, "Access token refresh failed: {0}", e.getMessage());
      future.completeExceptionally(
          e instanceof PlatformAuthException
              ? e
              : new PlatformAuthException("Access token refresh failed", e));
      return;
    }
    synchronized (lock) {
      if (closed) {
        // close() has already failed this future; a late token must not reach its waiters.
        return;
      }
      inFlight = null;
      current = token;
      scheduleRefreshLocked(token);
    }
    future.complete(token);
  }

  private void scheduleRefreshLocked(CachedToken token) {
    if (scheduledRefresh != null) {
      scheduledRefresh.cancel(false);
      scheduledRefresh = null;
    }
    if (token.refreshAt == null) {
      return;
    }
    long delayMillis = Math.max(0, Duration.between(clock.instant(), token.refreshAt).toMillis());
    scheduledRefresh =
        refresher.schedule(
            () -> {
              synchronized (lock) {
                if (!closed && current == token && inFlight == null) {
                  startRefreshLocked();
                }
              }
            },
            delayMillis,
            TimeUnit.MILLISECONDS);
  }

  private CachedToken fetchToken() throws PlatformAuthException {
    Request request =
        new Request.Builder()
            .url(tokenEndpoint.toString())
            .header("Authorization", basicAuthorization())
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .post(RequestBody.create(GRANT_BODY, FORM_MEDIA_TYPE))
            .build();
    Instant requestedAt = clock.instant();
    LOG.fine("Requesting access token");
    try (Response response = httpClient.newCall(request).execute()) {
      ResponseBody responseBody = response.body();
      String body = responseBody != null ? responseBody.string() : "";
      if (!response.isSuccessful()) {
        String errorCode = errorCode(body);
        throw new PlatformAuthException(
            "Token endpoint answered HTTP "
                + response.code()
                + (errorCode != null ? " (" + errorCode + ")" : ""),
            response.code(),
            errorCode);
      }
      return parseToken(body, requestedAt);
    } catch (IOException e) {
      throw new PlatformAuthException("Failed to reach token endpoint " + tokenEndpoint, e);
    }
  }

  /**
   * The secret exists as a {@code String} only inside this method, for the moment it takes to
   * encode the header; the working copy of the {@code char[]} is zeroed on the way out.
   */
  private String basicAuthorization() {
    char[] secret = credentials.copySecret();
    try {
      String userInfo = formEncode(credentials.clientId()) + ":" + formEncode(new String(secret));
      return "Basic "
          + Base64.getEncoder().encodeToString(userInfo.getBytes(StandardCharsets.UTF_8));
    } finally {
      Arrays.fill(secret, '\0');
    }
  }

  private static String formEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private CachedToken parseToken(String body, Instant requestedAt) throws PlatformAuthException {
    JsonNode root;
    try {
      root = objectMapper.readTree(body);
    } catch (IOException e) {
      throw new PlatformAuthException("Token response is not valid JSON", e);
    }
    if (root == null || !root.isObject()) {
      throw new PlatformAuthException("Token response is not a JSON object");
    }
    String accessToken = root.path("access_token").asText("");
    if (accessToken.isEmpty()) {
      throw new PlatformAuthException("Token response has no access_token");
    }
    String tokenType = root.path("token_type").asText("");
    if (!tokenType.equalsIgnoreCase("Bearer")) {
      throw new PlatformAuthException(
          "Token response has unsupported token_type '" + tokenType + "'");
    }
    JsonNode expiresIn = root.path("expires_in");
    Instant expiresAt = null;
    Instant refreshAt = null;
    if (expiresIn.isNumber() && expiresIn.asLong() > 0) {
      expiresAt = requestedAt.plusSeconds(expiresIn.asLong());
      Instant proactive = expiresAt.minus(refreshSkew);
      // A token shorter than the skew is used until it expires; refreshing it from birth would
      // hammer the token endpoint.
      refreshAt = proactive.isAfter(requestedAt) ? proactive : null;
    }
    return new CachedToken(accessToken, expiresAt, refreshAt);
  }

  private String errorCode(String body) {
    try {
      JsonNode root = objectMapper.readTree(body);
      return root != null && root.hasNonNull("error") ? root.get("error").asText() : null;
    } catch (IOException e) {
      return null;
    }
  }

  private static CachedToken await(CompletableFuture<CachedToken> future, Duration timeout)
      throws PlatformException {
    try {
      return timeout == null ? future.get() : future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new PlatformAuthException("Interrupted while waiting for an access token", e);
    } catch (TimeoutException e) {
      throw new PlatformException("Timed out after " + timeout + " waiting for an access token", e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof PlatformException) {
        throw (PlatformException) cause;
      }
      throw new PlatformAuthException("Access token refresh failed", cause);
    }
  }

  private static final class CachedToken {
    final String accessToken;
    final Instant expiresAt;
    final Instant refreshAt;

    CachedToken(String accessToken, Instant expiresAt, Instant refreshAt) {
      this.accessToken = accessToken;
      this.expiresAt = expiresAt;
      this.refreshAt = refreshAt;
    }

    boolean isExpired(Instant now) {
      return expiresAt != null && !now.isBefore(expiresAt);
    }

    boolean isDueForRefresh(Instant now) {
      return refreshAt != null && !now.isBefore(refreshAt);
    }
  }
}
