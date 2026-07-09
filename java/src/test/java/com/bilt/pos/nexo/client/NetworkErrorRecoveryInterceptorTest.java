package com.bilt.pos.nexo.client;

import com.bilt.pos.nexo.model.MessageHeader;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.ResultType;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class NetworkErrorRecoveryInterceptorTest {

    private static final MediaType JSON = MediaType.get("application/json");

    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    /**
     * Builds a client with only the recovery interceptor added, leaving OkHttp's
     * default {@code retryOnConnectionFailure} on (as in production, so stale
     * pooled connections are recovered rather than surfacing as connect errors).
     * The read timeout doubles as the recovery budget, so it is set from
     * {@code budget}.
     */
    private OkHttpClient clientWithRecovery(Duration budget) {
        return new OkHttpClient.Builder()
                .readTimeout(budget.toMillis(), TimeUnit.MILLISECONDS)
                .addInterceptor(new NetworkErrorRecoveryInterceptor())
                .build();
    }

    private Request post() {
        return new Request.Builder()
                .url(server.url("/nexo"))
                .post(RequestBody.create("{}", JSON))
                .build();
    }

    @Test
    void retriesThroughTransientFailuresAndReusesRequestId() throws Exception {
        // Two dropped connections, then success. Extra successes are buffered so no
        // attempt blocks on an empty queue if OkHttp adds an internal route retry.
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        server.enqueue(new MockResponse().setBody("ok"));
        server.enqueue(new MockResponse().setBody("ok"));

        try (Response response = clientWithRecovery(Duration.ofSeconds(10)).newCall(post()).execute()) {
            assertEquals(200, response.code());
            assertEquals("ok", response.body().string());
        }

        // Both drops were served (and recorded) before the success, so at least
        // three requests reached the server.
        int count = server.getRequestCount();
        assertTrue(count >= 3, "expected at least two retries, saw " + count + " request(s)");

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
            assertNotNull(recorded);
            ids.add(recorded.getHeader(NetworkErrorRecoveryInterceptor.REQUEST_ID_HEADER));
        }
        // The same correlation id on every attempt...
        assertEquals(1, ids.stream().distinct().count());
        // ...and it is a valid UUID.
        assertDoesNotThrow(() -> UUID.fromString(ids.get(0)));
    }

    @Test
    void addsRequestIdHeaderOnFirstAttempt() throws Exception {
        server.enqueue(new MockResponse().setBody("ok"));

        try (Response response = clientWithRecovery(Duration.ofSeconds(10)).newCall(post()).execute()) {
            assertEquals(200, response.code());
        }

        assertEquals(1, server.getRequestCount());
        RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
        String id = recorded.getHeader(NetworkErrorRecoveryInterceptor.REQUEST_ID_HEADER);
        assertNotNull(id);
        assertDoesNotThrow(() -> UUID.fromString(id));
    }

    @Test
    void doesNotRetryOnReceivedHttpError() throws Exception {
        // A 500 is a received response, not a transport failure: returned as-is, no retry.
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        try (Response response = clientWithRecovery(Duration.ofSeconds(10)).newCall(post()).execute()) {
            assertEquals(500, response.code());
        }
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void givesUpAfterDeadline() throws Exception {
        for (int i = 0; i < 10; i++) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        }

        OkHttpClient client = clientWithRecovery(Duration.ofMillis(200));

        long start = System.nanoTime();
        assertThrows(IOException.class, () -> client.newCall(post()).execute());
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        // It attempted at least once, then stopped near the deadline rather than
        // retrying indefinitely against the (still failing) server.
        assertTrue(server.getRequestCount() >= 1);
        assertTrue(elapsedMillis < 5000, "should give up promptly, took " + elapsedMillis + "ms");
    }

    @Test
    void perAttemptTimeoutIsDerivedFromReadTimeout() {
        // max(readTimeout / 6, min(10s, readTimeout))
        assertEquals(20_000L, NetworkErrorRecoveryInterceptor.perAttemptTimeoutMillis(120_000)); // 120s -> 20s
        assertEquals(10_000L, NetworkErrorRecoveryInterceptor.perAttemptTimeoutMillis(60_000));  // 60s  -> 10s
        assertEquals(10_000L, NetworkErrorRecoveryInterceptor.perAttemptTimeoutMillis(30_000));  // floor -> 10s
        assertEquals(6_000L, NetworkErrorRecoveryInterceptor.perAttemptTimeoutMillis(6_000));    // 6s   -> whole timeout
        assertEquals(10_000L, NetworkErrorRecoveryInterceptor.perAttemptTimeoutMillis(0));       // no timeout -> 10s floor
    }

    @Test
    void clientRecoversAndReturnsParsedResponse() throws Exception {
        // End-to-end through the real client with recovery enabled: one drop, then
        // a valid Nexo response. Extra successes are buffered so no attempt can
        // block on an empty queue regardless of internal route retries.
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        String successBody = "{\"SaleToPOIResponse\":{\"MessageHeader\":{\"ProtocolVersion\":\"3.0\"},"
                + "\"PaymentResponse\":{\"Response\":{\"Result\":\"Success\"}}}}";
        server.enqueue(new MockResponse().setBody(successBody));
        server.enqueue(new MockResponse().setBody(successBody));

        BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
                .endpoint(server.url("/nexo").toString())
                .readTimeout(Duration.ofSeconds(10))  // recovery is on by default
                .build();

        NexoTerminalAPI response = client.request(NexoTerminalAPI.builder()
                .saleToPOIRequest(SaleToPOIRequest.builder()
                        .messageHeader(MessageHeader.builder().build())
                        .build())
                .build());

        assertEquals(ResultType.SUCCESS,
                response.getSaleToPOIResponse().getPaymentResponse().getResponse().getResult());

        int count = server.getRequestCount();
        assertTrue(count >= 2, "expected at least one retry, saw " + count + " request(s)");
        String firstId = null;
        for (int i = 0; i < count; i++) {
            RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
            String id = recorded.getHeader(NetworkErrorRecoveryInterceptor.REQUEST_ID_HEADER);
            assertNotNull(id);
            if (firstId == null) {
                firstId = id;
            } else {
                assertEquals(firstId, id, "every attempt must reuse the same correlation id");
            }
        }
    }
}
