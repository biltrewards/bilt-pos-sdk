package com.bilt.pos.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OkHttpPlatformClientTest {

  private static final String CLIENT_ID = "pos-client";
  private static final String CLIENT_SECRET = "s3cret-Value-42";
  private static final String TOKEN_PATH = "/oauth2/token";

  private MockWebServer server;
  private OkHttpPlatformClient client;
  private final MutableClock clock = new MutableClock(Instant.parse("2026-09-25T12:00:00Z"));

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (client != null) {
      client.close();
    }
    server.shutdown();
  }

  @Test
  void tokenIsAcquiredOnFirstCallAndReused() throws Exception {
    server.enqueue(tokenResponse("tok-1", 3600));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":2}"));
    client = newClient(Duration.ofSeconds(60));

    PlatformResponse first =
        client.execute(PlatformRequest.post("v1/things").jsonBody("{\"a\":1}").build());
    PlatformResponse second = client.execute(PlatformRequest.get("/v1/things?x=1").build());

    assertEquals(200, first.status());
    assertEquals("{\"ok\":true}", first.bodyAsString());
    assertEquals(200, second.status());
    assertEquals(3, server.getRequestCount());

    RecordedRequest tokenRequest = server.takeRequest();
    assertEquals("POST", tokenRequest.getMethod());
    assertEquals(TOKEN_PATH, tokenRequest.getPath());
    assertEquals("grant_type=client_credentials", tokenRequest.getBody().readUtf8());
    assertTrue(
        tokenRequest.getHeader("Content-Type").startsWith("application/x-www-form-urlencoded"));
    assertEquals(
        CLIENT_ID + ":" + CLIENT_SECRET,
        new String(
            Base64.getDecoder()
                .decode(tokenRequest.getHeader("Authorization").substring("Basic ".length())),
            StandardCharsets.UTF_8));
    assertEquals("test-agent/1.0", tokenRequest.getHeader("User-Agent"));

    RecordedRequest apiRequest = server.takeRequest();
    assertEquals("POST", apiRequest.getMethod());
    assertEquals("/gateway/v1/things", apiRequest.getPath());
    assertEquals("Bearer tok-1", apiRequest.getHeader("Authorization"));
    assertEquals("test-agent/1.0", apiRequest.getHeader("User-Agent"));
    assertEquals(PlatformRequest.JSON_CONTENT_TYPE, apiRequest.getHeader("Content-Type"));
    assertEquals("{\"a\":1}", apiRequest.getBody().readUtf8());

    RecordedRequest secondApiRequest = server.takeRequest();
    assertEquals("GET", secondApiRequest.getMethod());
    assertEquals("/gateway/v1/things?x=1", secondApiRequest.getPath());
    assertEquals("Bearer tok-1", secondApiRequest.getHeader("Authorization"));
  }

  @Test
  void expiredTokenIsRefreshedOnNextCall() throws Exception {
    server.enqueue(tokenResponse("tok-1", 300));
    server.enqueue(new MockResponse().setResponseCode(200));
    server.enqueue(tokenResponse("tok-2", 300));
    server.enqueue(new MockResponse().setResponseCode(200));
    client = newClient(Duration.ofSeconds(60));

    client.execute(PlatformRequest.get("v1/a").build());
    clock.advance(Duration.ofSeconds(301));
    client.execute(PlatformRequest.get("v1/b").build());

    assertEquals(4, server.getRequestCount());
    server.takeRequest();
    assertEquals("Bearer tok-1", server.takeRequest().getHeader("Authorization"));
    assertEquals(TOKEN_PATH, server.takeRequest().getPath());
    assertEquals("Bearer tok-2", server.takeRequest().getHeader("Authorization"));
  }

  @Test
  void tokenIsRefreshedInTheBackgroundBeforeExpiry() throws Exception {
    CountingDispatcher dispatcher = new CountingDispatcher();
    server.setDispatcher(dispatcher);
    client =
        OkHttpPlatformClient.builder()
            .credentials(BiltCredentials.clientCredentials(CLIENT_ID, CLIENT_SECRET))
            .environment(environment())
            .refreshSkew(Duration.ofSeconds(2))
            .userAgent("test-agent/1.0")
            .build();

    // expires_in=3 with a 2 s skew schedules the refresh one second after issue.
    dispatcher.expiresIn = 3;
    client.execute(PlatformRequest.get("v1/a").build());
    assertEquals(1, dispatcher.tokenRequests.get());

    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    String authorization = null;
    while (System.nanoTime() < deadline) {
      client.execute(PlatformRequest.get("v1/poll").build());
      authorization = dispatcher.lastApiAuthorization;
      if ("Bearer tok-2".equals(authorization)) {
        break;
      }
      Thread.sleep(50);
    }
    assertEquals("Bearer tok-2", authorization, "background refresh did not replace the token");
    assertEquals(2, dispatcher.tokenRequests.get());
  }

  @Test
  void unauthorizedResponseRefreshesOnceAndRetries() throws Exception {
    server.enqueue(tokenResponse("tok-1", 3600));
    server.enqueue(new MockResponse().setResponseCode(401));
    server.enqueue(tokenResponse("tok-2", 3600));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("retried"));
    client = newClient(Duration.ofSeconds(60));

    PlatformResponse response =
        client.execute(PlatformRequest.post("v1/a").jsonBody("{\"n\":1}").build());

    assertEquals(200, response.status());
    assertEquals("retried", response.bodyAsString());
    assertEquals(4, server.getRequestCount());
    server.takeRequest();
    assertEquals("Bearer tok-1", server.takeRequest().getHeader("Authorization"));
    assertEquals(TOKEN_PATH, server.takeRequest().getPath());
    RecordedRequest retry = server.takeRequest();
    assertEquals("Bearer tok-2", retry.getHeader("Authorization"));
    assertEquals("{\"n\":1}", retry.getBody().readUtf8(), "retry re-sends the body");
  }

  @Test
  void secondUnauthorizedIsReturnedWithoutFurtherRetry() throws Exception {
    server.enqueue(tokenResponse("tok-1", 3600));
    server.enqueue(new MockResponse().setResponseCode(401).setBody("nope"));
    server.enqueue(tokenResponse("tok-2", 3600));
    server.enqueue(new MockResponse().setResponseCode(401).setBody("still no"));
    client = newClient(Duration.ofSeconds(60));

    PlatformResponse response = client.execute(PlatformRequest.get("v1/a").build());

    assertEquals(401, response.status());
    assertEquals("still no", response.bodyAsString());
    assertEquals(4, server.getRequestCount());
  }

  @Test
  void tokenFailureDuringRetryIsPlatformAuthException() throws Exception {
    server.enqueue(tokenResponse("tok-1", 3600));
    server.enqueue(new MockResponse().setResponseCode(401));
    server.enqueue(
        new MockResponse().setResponseCode(400).setBody("{\"error\":\"invalid_client\"}"));
    client = newClient(Duration.ofSeconds(60));

    PlatformAuthException e =
        assertThrows(
            PlatformAuthException.class, () -> client.execute(PlatformRequest.get("v1/a").build()));

    assertEquals(400, e.status());
    assertEquals("invalid_client", e.errorCode());
    assertEquals(3, server.getRequestCount());
  }

  @Test
  void suppliedHttpClientCarriesApiCallsButTokenRequestsStayUnauthenticated() throws Exception {
    server.enqueue(tokenResponse("tok-1", 3600));
    server.enqueue(new MockResponse().setResponseCode(200));
    List<String> seen = new ArrayList<>();
    OkHttpClient supplied =
        new OkHttpClient.Builder()
            .addInterceptor(
                chain -> {
                  seen.add(
                      chain.request().url().encodedPath()
                          + " "
                          + chain.request().header("Authorization"));
                  return chain.proceed(chain.request());
                })
            .build();
    client =
        OkHttpPlatformClient.builder()
            .credentials(BiltCredentials.clientCredentials(CLIENT_ID, CLIENT_SECRET))
            .environment(environment())
            .httpClient(supplied)
            .build();

    client.execute(PlatformRequest.get("v1/a").build());

    assertEquals(2, seen.size());
    assertTrue(seen.get(0).startsWith(TOKEN_PATH + " Basic "), seen.get(0));
    assertEquals("/gateway/v1/a Bearer tok-1", seen.get(1));
  }

  @Test
  void retryAfterUnauthorizedSpendsOnlyTheRemainingTimeout() throws Exception {
    server.enqueue(tokenResponse("tok-1", 3600));
    server.enqueue(new MockResponse().setResponseCode(200));
    server.enqueue(
        new MockResponse().setResponseCode(401).setHeadersDelay(700, TimeUnit.MILLISECONDS));
    server.enqueue(tokenResponse("tok-2", 3600).setHeadersDelay(3, TimeUnit.SECONDS));
    client = newClient(Duration.ofSeconds(60));
    // Holds tok-1, so the timed call below starts with a token and meets its 401 at ~0.7 s.
    client.execute(PlatformRequest.get("v1/warm").build());

    long started = System.nanoTime();
    PlatformException e =
        assertThrows(
            PlatformException.class,
            () ->
                client.execute(PlatformRequest.get("v1/a").timeout(Duration.ofSeconds(1)).build()));
    Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

    // Either the token wait or OkHttp's call timeout may report first; both end the call on time.
    assertFalse(e instanceof PlatformAuthException, String.valueOf(e));
    // A fresh budget for the retry's token wait would end near 1.7 s.
    assertTrue(elapsed.compareTo(Duration.ofMillis(1400)) < 0, "took " + elapsed);
  }

  @Test
  void concurrentCallersShareOneTokenRequest() throws Exception {
    CountingDispatcher dispatcher = new CountingDispatcher();
    dispatcher.tokenDelay = Duration.ofMillis(300);
    server.setDispatcher(dispatcher);
    client = newClient(Duration.ofSeconds(60));

    int callers = 8;
    CountDownLatch start = new CountDownLatch(1);
    List<CompletableFuture<PlatformResponse>> results = new ArrayList<>();
    for (int i = 0; i < callers; i++) {
      int n = i;
      results.add(
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  start.await();
                  return client.execute(PlatformRequest.get("v1/c/" + n).build());
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              }));
    }
    start.countDown();
    for (CompletableFuture<PlatformResponse> result : results) {
      assertEquals(200, result.get(10, TimeUnit.SECONDS).status());
    }

    assertEquals(1, dispatcher.tokenRequests.get());
    assertEquals(callers, dispatcher.apiRequests.get());
  }

  @Test
  void tokenEndpointRejectionIsTypedAndRedacted() throws Exception {
    server.enqueue(
        new MockResponse()
            .setResponseCode(401)
            .setBody(
                "{\"error\":\"invalid_client\",\"error_description\":\"bad secret "
                    + CLIENT_SECRET
                    + "\"}"));
    client = newClient(Duration.ofSeconds(60));

    PlatformAuthException e =
        assertThrows(
            PlatformAuthException.class, () -> client.execute(PlatformRequest.get("v1/a").build()));

    assertEquals(401, e.status());
    assertEquals("invalid_client", e.errorCode());
    assertFalse(fullText(e).contains(CLIENT_SECRET), "secret leaked: " + fullText(e));
    assertTrue(e.getMessage().contains("401"));
    assertTrue(e.getMessage().contains("invalid_client"));
    assertEquals(1, server.getRequestCount(), "no API call is attempted without a token");
  }

  @Test
  void malformedTokenDocumentIsTypedAndRedacted() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("<html>" + CLIENT_SECRET));
    client = newClient(Duration.ofSeconds(60));

    PlatformAuthException e =
        assertThrows(
            PlatformAuthException.class, () -> client.execute(PlatformRequest.get("v1/a").build()));

    assertEquals(0, e.status());
    assertFalse(fullText(e).contains(CLIENT_SECRET));
  }

  @Test
  void unsupportedTokenTypeIsRejected() throws Exception {
    server.enqueue(new MockResponse().setBody("{\"access_token\":\"t\",\"token_type\":\"MAC\"}"));
    client = newClient(Duration.ofSeconds(60));

    PlatformAuthException e =
        assertThrows(
            PlatformAuthException.class, () -> client.execute(PlatformRequest.get("v1/a").build()));
    assertTrue(e.getMessage().contains("MAC"));
  }

  @Test
  void invalidExpiresInIsRejected() throws Exception {
    List<String> invalid = List.of("0", "-5", "\"soon\"", "\"-5\"", "1.5", "true", "{}");
    for (String expiresIn : invalid) {
      server.enqueue(
          new MockResponse()
              .setBody(
                  "{\"access_token\":\"t\",\"token_type\":\"Bearer\",\"expires_in\":"
                      + expiresIn
                      + "}"));
    }
    client = newClient(Duration.ofSeconds(60));

    for (String expiresIn : invalid) {
      PlatformAuthException e =
          assertThrows(
              PlatformAuthException.class,
              () -> client.execute(PlatformRequest.get("v1/a").build()),
              expiresIn);
      assertTrue(e.getMessage().contains("expires_in"), e.getMessage());
    }
    assertEquals(invalid.size(), server.getRequestCount(), "no API call without a valid token");
  }

  @Test
  void quotedExpiresInIsHonoured() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody("{\"access_token\":\"t1\",\"token_type\":\"Bearer\",\"expires_in\":\"300\"}"));
    server.enqueue(new MockResponse().setResponseCode(200));
    server.enqueue(tokenResponse("t2", 300));
    server.enqueue(new MockResponse().setResponseCode(200));
    client = newClient(Duration.ofSeconds(60));

    client.execute(PlatformRequest.get("v1/a").build());
    clock.advance(Duration.ofSeconds(301));
    client.execute(PlatformRequest.get("v1/b").build());

    server.takeRequest();
    assertEquals("Bearer t1", server.takeRequest().getHeader("Authorization"));
    assertEquals(TOKEN_PATH, server.takeRequest().getPath(), "quoted lifetime was honoured");
    assertEquals("Bearer t2", server.takeRequest().getHeader("Authorization"));
  }

  @Test
  void omittedExpiresInKeepsTheTokenUntilRejected() throws Exception {
    server.enqueue(
        new MockResponse().setBody("{\"access_token\":\"t1\",\"token_type\":\"Bearer\"}"));
    server.enqueue(new MockResponse().setResponseCode(200));
    server.enqueue(new MockResponse().setResponseCode(200));
    client = newClient(Duration.ofSeconds(60));

    client.execute(PlatformRequest.get("v1/a").build());
    clock.advance(Duration.ofDays(1));
    client.execute(PlatformRequest.get("v1/b").build());

    assertEquals(3, server.getRequestCount());
  }

  @Test
  void failedTokenFetchIsRetriedOnTheNextCall() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(503));
    server.enqueue(tokenResponse("tok-1", 3600));
    server.enqueue(new MockResponse().setResponseCode(200));
    client = newClient(Duration.ofSeconds(60));

    assertThrows(
        PlatformAuthException.class, () -> client.execute(PlatformRequest.get("v1/a").build()));
    assertEquals(200, client.execute(PlatformRequest.get("v1/a").build()).status());
    assertEquals(3, server.getRequestCount());
  }

  @Test
  void executeAsyncDeliversResponseAndAuthFailures() throws Exception {
    server.enqueue(tokenResponse("tok-1", 3600));
    server.enqueue(new MockResponse().setResponseCode(204));
    client = newClient(Duration.ofSeconds(60));

    PlatformResponse response =
        client.executeAsync(PlatformRequest.get("v1/a").build()).get(10, TimeUnit.SECONDS);
    assertEquals(204, response.status());
    assertTrue(response.isSuccessful());
    client.close();

    server.enqueue(
        new MockResponse().setResponseCode(400).setBody("{\"error\":\"invalid_request\"}"));
    client = newClient(Duration.ofSeconds(60));
    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () ->
                client.executeAsync(PlatformRequest.get("v1/a").build()).get(10, TimeUnit.SECONDS));
    assertTrue(e.getCause() instanceof PlatformAuthException, String.valueOf(e.getCause()));
    assertEquals("invalid_request", ((PlatformAuthException) e.getCause()).errorCode());
  }

  @Test
  void transportFailureIsPlatformException() throws Exception {
    server.enqueue(tokenResponse("tok-1", 3600));
    server.enqueue(new MockResponse().setResponseCode(200));
    client = newClient(Duration.ofSeconds(60));
    client.execute(PlatformRequest.get("v1/a").timeout(Duration.ofSeconds(5)).build());
    server.shutdown();

    PlatformException e =
        assertThrows(
            PlatformException.class, () -> client.execute(PlatformRequest.get("v1/b").build()));
    assertFalse(e instanceof PlatformAuthException);
    assertFalse(fullText(e).contains(CLIENT_SECRET));
  }

  @Test
  void closedClientRejectsRequests() throws Exception {
    client = newClient(Duration.ofSeconds(60));
    client.close();
    client.close();

    assertThrows(
        IllegalStateException.class, () -> client.execute(PlatformRequest.get("v1/a").build()));
    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () -> client.executeAsync(PlatformRequest.get("v1/a").build()).get());
    assertTrue(e.getCause() instanceof IllegalStateException);
    assertEquals(0, server.getRequestCount());
  }

  @Test
  void closeReleasesCallersWaitingForAToken() throws Exception {
    CountingDispatcher dispatcher = new CountingDispatcher();
    dispatcher.tokenDelay = Duration.ofSeconds(2);
    server.setDispatcher(dispatcher);
    client = newClient(Duration.ofSeconds(60));

    CompletableFuture<PlatformResponse> sync =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return client.execute(PlatformRequest.get("v1/sync").build());
              } catch (PlatformException e) {
                throw new RuntimeException(e);
              }
            });
    CompletableFuture<PlatformResponse> async =
        client.executeAsync(PlatformRequest.get("v1/async").build());
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (dispatcher.tokenRequests.get() == 0 && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
    assertEquals(1, dispatcher.tokenRequests.get());

    client.close();

    ExecutionException syncFailure =
        assertThrows(ExecutionException.class, () -> sync.get(1, TimeUnit.SECONDS));
    assertTrue(
        syncFailure.getCause().getCause() instanceof PlatformException,
        String.valueOf(syncFailure.getCause()));
    assertThrows(ExecutionException.class, () -> async.get(1, TimeUnit.SECONDS));
    assertEquals(0, dispatcher.apiRequests.get());
  }

  @Test
  void pathsThatLeaveTheApiBaseAreRefusedBeforeAnyTokenIsFetched() throws Exception {
    client = newClient(Duration.ofSeconds(60));

    for (String path :
        List.of(
            "https://elsewhere.example/steal",
            "\\\\elsewhere.example/steal",
            "../outside",
            "v1/../../outside",
            "%2e%2e/outside")) {
      PlatformException e =
          assertThrows(
              PlatformException.class,
              () -> client.execute(PlatformRequest.get(path).build()),
              path);
      assertFalse(e instanceof PlatformAuthException, path);
    }
    assertEquals(0, server.getRequestCount());
  }

  @Test
  void dotSegmentsThatStayWithinTheApiBaseAreSent() throws Exception {
    server.enqueue(tokenResponse("tok-1", 3600));
    server.enqueue(new MockResponse().setResponseCode(200));
    client = newClient(Duration.ofSeconds(60));

    client.execute(PlatformRequest.get("v1/../v2/a").build());

    server.takeRequest();
    RecordedRequest api = server.takeRequest();
    assertEquals("/gateway/v2/a", api.getPath());
    assertEquals("Bearer tok-1", api.getHeader("Authorization"));
  }

  @Test
  void redirectToAnotherHostCarriesNoTokenAndItsUnauthorizedIsNotAnswered() throws Exception {
    try (MockWebServer elsewhere = new MockWebServer()) {
      elsewhere.enqueue(new MockResponse().setResponseCode(401));
      elsewhere.start();
      server.enqueue(tokenResponse("tok-1", 3600));
      server.enqueue(
          new MockResponse()
              .setResponseCode(302)
              .setHeader("Location", elsewhere.url("/landing").toString()));
      client = newClient(Duration.ofSeconds(60));

      PlatformResponse response = client.execute(PlatformRequest.get("v1/a").build());

      assertEquals(401, response.status());
      assertEquals(1, elsewhere.getRequestCount());
      assertNull(elsewhere.takeRequest().getHeader("Authorization"));
      assertEquals(2, server.getRequestCount(), "no second token was fetched for the other host");
    }
  }

  @Test
  void builderRequiresCredentialsAndEnvironment() {
    assertThrows(
        IllegalStateException.class,
        () -> OkHttpPlatformClient.builder().environment(environment()).build());
    assertThrows(
        IllegalStateException.class,
        () ->
            OkHttpPlatformClient.builder()
                .credentials(BiltCredentials.clientCredentials("a", "b"))
                .build());
    assertThrows(
        IllegalArgumentException.class,
        () -> OkHttpPlatformClient.builder().refreshSkew(Duration.ofSeconds(-1)));
  }

  private OkHttpPlatformClient newClient(Duration refreshSkew) {
    return OkHttpPlatformClient.builder()
        .credentials(BiltCredentials.clientCredentials(CLIENT_ID, CLIENT_SECRET))
        .environment(environment())
        .refreshSkew(refreshSkew)
        .userAgent("test-agent/1.0")
        .clock(clock)
        .build();
  }

  private BiltEnvironment environment() {
    return BiltEnvironment.custom(
        URI.create(server.url(TOKEN_PATH).toString()),
        URI.create(server.url("/gateway").toString()));
  }

  private static MockResponse tokenResponse(String token, long expiresIn) {
    return new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            "{\"access_token\":\""
                + token
                + "\",\"token_type\":\"Bearer\",\"expires_in\":"
                + expiresIn
                + "}");
  }

  private static String fullText(Throwable t) {
    StringBuilder text = new StringBuilder();
    for (Throwable current = t; current != null; current = current.getCause()) {
      text.append(current).append('\n');
    }
    return text.toString();
  }

  /** Serves numbered tokens on the token path and 200 elsewhere, counting each. */
  private static final class CountingDispatcher extends Dispatcher {
    final AtomicInteger tokenRequests = new AtomicInteger();
    final AtomicInteger apiRequests = new AtomicInteger();
    volatile long expiresIn = 3600;
    volatile Duration tokenDelay = Duration.ZERO;
    volatile String lastApiAuthorization;

    @Override
    public MockResponse dispatch(RecordedRequest request) {
      if (TOKEN_PATH.equals(request.getPath())) {
        int n = tokenRequests.incrementAndGet();
        MockResponse response = tokenResponse("tok-" + n, expiresIn);
        if (!tokenDelay.isZero()) {
          response.setHeadersDelay(tokenDelay.toMillis(), TimeUnit.MILLISECONDS);
        }
        return response;
      }
      apiRequests.incrementAndGet();
      lastApiAuthorization = request.getHeader("Authorization");
      return new MockResponse().setResponseCode(200);
    }
  }

  private static final class MutableClock extends Clock {
    private volatile Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
