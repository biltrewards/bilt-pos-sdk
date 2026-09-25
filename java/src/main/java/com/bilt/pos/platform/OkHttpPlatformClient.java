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
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * {@link BiltPlatformClient} over OkHttp with OAuth 2.0 client-credentials authentication.
 *
 * <pre>{@code
 * BiltPlatformClient client = OkHttpPlatformClient.builder()
 *     .credentials(BiltCredentials.clientCredentials(clientId, clientSecret))
 *     .environment(BiltEnvironment.STAGING)
 *     .build();
 *
 * PlatformResponse response = client.execute(
 *     PlatformRequest.post("v1/example").jsonBody("{}").build());
 * }</pre>
 *
 * <p>The first request acquires an access token from the environment's token endpoint; later
 * requests reuse it. The token is refreshed on a background thread {@linkplain
 * Builder#refreshSkew(Duration) a little before} it expires, so callers do not pay for the token
 * exchange in the normal case. When the platform answers {@code 401} the token is discarded,
 * re-acquired and the request is sent once more; a second {@code 401} is returned to the caller as
 * an ordinary response. Concurrent callers who all need a token share one token request.
 *
 * <p>Without an explicit {@link Builder#httpClient(OkHttpClient) OkHttpClient} the client creates
 * its own with a 10 s connect and 30 s call timeout, and releases its pool and threads on {@link
 * #close()}. A supplied client is used as is and left running on close, since its owner may share
 * it. Per-request {@link PlatformRequest#timeout() timeouts} bound the exchange with the API and,
 * separately, the wait for a token that the exchange may have to make first.
 */
public final class OkHttpPlatformClient implements BiltPlatformClient {

  private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration DEFAULT_CALL_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration DEFAULT_REFRESH_SKEW = Duration.ofSeconds(60);

  private final HttpUrl apiBase;
  private final OkHttpClient httpClient;
  private final boolean ownsHttpClient;
  private final String userAgent;
  private final ClientCredentialsTokenSource tokenSource;
  private final ExecutorService asyncExecutor;
  private final AtomicBoolean closed = new AtomicBoolean();

  private OkHttpPlatformClient(Builder builder) {
    this.apiBase = withTrailingSlash(HttpUrl.get(builder.environment.apiBaseUrl().toString()));
    this.ownsHttpClient = builder.httpClient == null;
    this.httpClient =
        ownsHttpClient
            ? new OkHttpClient.Builder()
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .callTimeout(DEFAULT_CALL_TIMEOUT)
                .build()
            : builder.httpClient;
    this.userAgent = builder.userAgent != null ? builder.userAgent : defaultUserAgent();
    this.tokenSource =
        new ClientCredentialsTokenSource(
            builder.credentials,
            builder.environment.tokenEndpoint(),
            httpClient,
            userAgent,
            builder.refreshSkew,
            builder.clock);
    AtomicInteger counter = new AtomicInteger();
    this.asyncExecutor =
        Executors.newCachedThreadPool(
            runnable -> {
              Thread thread =
                  new Thread(runnable, "bilt-platform-async-" + counter.incrementAndGet());
              thread.setDaemon(true);
              return thread;
            });
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public PlatformResponse execute(PlatformRequest request) throws PlatformException {
    Objects.requireNonNull(request, "request");
    ensureOpen();
    String token = tokenSource.accessToken(request.timeout());
    PlatformResponse response = send(request, token);
    if (response.status() != 401) {
      return response;
    }
    tokenSource.invalidate(token);
    return send(request, tokenSource.accessToken(request.timeout()));
  }

  @Override
  public CompletableFuture<PlatformResponse> executeAsync(PlatformRequest request) {
    Objects.requireNonNull(request, "request");
    CompletableFuture<PlatformResponse> future = new CompletableFuture<>();
    try {
      asyncExecutor.execute(
          () -> {
            try {
              future.complete(execute(request));
            } catch (Throwable t) {
              future.completeExceptionally(t);
            }
          });
    } catch (RejectedExecutionException e) {
      future.completeExceptionally(new IllegalStateException("platform client is closed", e));
    }
    return future;
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    tokenSource.close();
    asyncExecutor.shutdownNow();
    if (ownsHttpClient) {
      httpClient.dispatcher().executorService().shutdown();
      httpClient.connectionPool().evictAll();
    }
  }

  private void ensureOpen() {
    if (closed.get()) {
      throw new IllegalStateException("platform client is closed");
    }
  }

  private PlatformResponse send(PlatformRequest request, String accessToken)
      throws PlatformException {
    HttpUrl url = apiBase.resolve(request.path());
    if (url == null) {
      throw new PlatformException("Request path cannot be resolved: " + request.path());
    }
    Request.Builder httpRequest = new Request.Builder().url(url);
    boolean userAgentSet = false;
    for (Map.Entry<String, String> header : request.headers().entrySet()) {
      httpRequest.header(header.getKey(), header.getValue());
      userAgentSet |= header.getKey().equalsIgnoreCase("User-Agent");
    }
    if (!userAgentSet) {
      httpRequest.header("User-Agent", userAgent);
    }
    httpRequest.header("Authorization", "Bearer " + accessToken);
    httpRequest.method(request.method(), requestBody(request));

    OkHttpClient client =
        request.timeout() == null
            ? httpClient
            : httpClient.newBuilder().callTimeout(request.timeout()).build();
    try (Response response = client.newCall(httpRequest.build()).execute()) {
      PlatformResponse.Builder result = PlatformResponse.builder().status(response.code());
      Headers headers = response.headers();
      for (int i = 0; i < headers.size(); i++) {
        result.addHeader(headers.name(i), headers.value(i));
      }
      ResponseBody body = response.body();
      if (body != null) {
        result.body(body.bytes());
      }
      return result.build();
    } catch (IOException e) {
      throw new PlatformException(
          "Request " + request.method() + " " + request.path() + " to the platform failed", e);
    }
  }

  private static RequestBody requestBody(PlatformRequest request) {
    if (request.hasBody()) {
      return RequestBody.create(request.body(), MediaType.parse(request.contentType()));
    }
    // OkHttp insists on a body for POST/PUT/PATCH; an empty one keeps a body-less POST legal.
    switch (request.method()) {
      case "POST":
      case "PUT":
      case "PATCH":
        return RequestBody.create(new byte[0], null);
      default:
        return null;
    }
  }

  private static HttpUrl withTrailingSlash(HttpUrl base) {
    return base.encodedPath().endsWith("/")
        ? base
        : base.newBuilder().encodedPath(base.encodedPath() + "/").build();
  }

  private static String defaultUserAgent() {
    String version = OkHttpPlatformClient.class.getPackage().getImplementationVersion();
    return version != null ? "bilt-pos-sdk/" + version : "bilt-pos-sdk";
  }

  /** Configures an {@link OkHttpPlatformClient}. Credentials and environment are required. */
  public static final class Builder {

    private BiltCredentials credentials;
    private BiltEnvironment environment;
    private OkHttpClient httpClient;
    private String userAgent;
    private Duration refreshSkew = DEFAULT_REFRESH_SKEW;
    private Clock clock = Clock.systemUTC();

    private Builder() {}

    /** The credentials to exchange for access tokens. */
    public Builder credentials(BiltCredentials credentials) {
      this.credentials = Objects.requireNonNull(credentials, "credentials");
      return this;
    }

    /** The platform deployment to talk to. */
    public Builder environment(BiltEnvironment environment) {
      this.environment = Objects.requireNonNull(environment, "environment");
      return this;
    }

    /**
     * An {@link OkHttpClient} to send with instead of the client's own. Its timeouts, proxy and TLS
     * configuration apply to token requests and API calls alike, and it is not shut down on {@link
     * OkHttpPlatformClient#close()}.
     */
    public Builder httpClient(OkHttpClient httpClient) {
      this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
      return this;
    }

    /** The {@code User-Agent} sent on every request. Defaults to {@code bilt-pos-sdk/<version>}. */
    public Builder userAgent(String userAgent) {
      Objects.requireNonNull(userAgent, "userAgent");
      if (userAgent.trim().isEmpty()) {
        throw new IllegalArgumentException("userAgent must not be blank");
      }
      this.userAgent = userAgent;
      return this;
    }

    /**
     * How long before a token's expiry its background refresh starts. Defaults to 60 seconds. A
     * token that lives shorter than the skew is simply used until it expires.
     */
    public Builder refreshSkew(Duration refreshSkew) {
      Objects.requireNonNull(refreshSkew, "refreshSkew");
      if (refreshSkew.isNegative()) {
        throw new IllegalArgumentException("refreshSkew must not be negative: " + refreshSkew);
      }
      this.refreshSkew = refreshSkew;
      return this;
    }

    /** Clock for token expiry arithmetic; tests substitute a controllable one. */
    Builder clock(Clock clock) {
      this.clock = Objects.requireNonNull(clock, "clock");
      return this;
    }

    public OkHttpPlatformClient build() {
      if (credentials == null) {
        throw new IllegalStateException("credentials are required");
      }
      if (environment == null) {
        throw new IllegalStateException("environment is required");
      }
      return new OkHttpPlatformClient(this);
    }
  }
}
