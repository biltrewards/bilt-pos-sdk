/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.nexo.client;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ProtocolException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OkHttp application interceptor that recovers an in-flight request across a
 * transient network failure without re-executing the operation on the terminal.
 *
 * <p>Each logical request is tagged with a stable {@value #REQUEST_ID_HEADER}
 * UUID. On any {@link IOException} the interceptor re-sends the <em>identical</em>
 * request (same id) after a backoff, until a response arrives or the operation
 * timeout elapses. The terminal keys execution on the id and treats a request
 * whose id it already knows as a recovery rather than a new operation, so
 * re-sending is always safe (at-most-once execution).</p>
 *
 * <p>The overall recovery budget is the call's read timeout
 * ({@link Chain#readTimeoutMillis()}), treated as the timeout for the whole
 * operation; overriding the timeout (on the client or per request) changes the
 * budget with it, and {@code 0} ("no timeout") means recovery retries until it
 * succeeds. Each individual attempt is additionally capped at a shorter
 * per-attempt timeout — derived from the read timeout as
 * {@code max(readTimeout / 6, min(10s, readTimeout))} (≈20s at the 120s default) —
 * via {@link Chain#withReadTimeout}, so a single stuck attempt — e.g. a TLS
 * handshake to a silently black-holed peer, which OkHttp otherwise bounds by the
 * full read timeout — cannot consume the whole budget.</p>
 *
 * <p>This class is intentionally package-private: it is wired in by the client
 * whenever recovery is active (on by default; see
 * {@link BiltNexoTerminalClient.Builder#disableRecoveryOnNetworkError()}) and is
 * not part of the public API. See {@code docs/network-error-recovery.md} for the
 * full client/server contract.</p>
 */
final class NetworkErrorRecoveryInterceptor implements Interceptor {

    /** Correlation header carrying the per-request idempotency UUID. */
    static final String REQUEST_ID_HEADER = "X-Bilt-Request-Id";

    private static final Logger LOG =
            Logger.getLogger(NetworkErrorRecoveryInterceptor.class.getName());

    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(3);
    private static final long MAX_JITTER_MILLIS = 500L;
    private static final long PER_ATTEMPT_FLOOR_MILLIS = 10_000L;
    private static final long PER_ATTEMPT_DIVISOR = 6L;

    /**
     * Derive the per-attempt timeout from the overall (read) timeout:
     * {@code max(readTimeout / 6, min(10s, readTimeout))}. At least ~10s (or the
     * whole timeout if it is shorter), otherwise a sixth of the total — roughly
     * six attempts for a large timeout. A non-positive ("no timeout") read timeout
     * yields the 10s floor so black holes stay bounded.
     */
    static long perAttemptTimeoutMillis(int readTimeoutMillis) {
        if (readTimeoutMillis <= 0) {
            return PER_ATTEMPT_FLOOR_MILLIS;
        }
        long floor = Math.min(PER_ATTEMPT_FLOOR_MILLIS, readTimeoutMillis);
        return Math.max(readTimeoutMillis / PER_ATTEMPT_DIVISOR, floor);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        String requestId = UUID.randomUUID().toString();
        Request request = chain.request().newBuilder()
                .header(REQUEST_ID_HEADER, requestId)
                .build();

        // The overall recovery budget is this call's read timeout, treated as the
        // timeout for the whole operation (0 means "no timeout"). Each attempt is
        // additionally capped at perAttemptMillis via withReadTimeout, so a single
        // stuck attempt -- e.g. a TLS handshake to a silently black-holed peer,
        // which OkHttp otherwise bounds by the full read timeout -- cannot consume
        // the whole budget. Re-sending the same id after such a cap is safe: the
        // terminal treats it as a recovery and keeps waiting; it does not re-run.
        int timeoutMillis = chain.readTimeoutMillis();
        long budgetNanos = timeoutMillis > 0 ? TimeUnit.MILLISECONDS.toNanos(timeoutMillis) : -1L;
        long perAttemptMillis = perAttemptTimeoutMillis(timeoutMillis);
        long startNanos = System.nanoTime();
        long backoffMillis = INITIAL_BACKOFF.toMillis();
        int attempt = 0;
        IOException lastError;

        while (true) {
            attempt++;
            int attemptTimeoutMillis = (int) perAttemptMillis;
            if (budgetNanos >= 0) {
                long remainingMillis = TimeUnit.NANOSECONDS.toMillis(
                        budgetNanos - (System.nanoTime() - startNanos));
                attemptTimeoutMillis = (int) Math.min(perAttemptMillis, Math.max(1L, remainingMillis));
            }
            try {
                // Re-sending the identical request is safe: the terminal keys
                // execution on the request id and only runs the operation once.
                return chain.withReadTimeout(attemptTimeoutMillis, TimeUnit.MILLISECONDS)
                        .proceed(request);
            } catch (IOException e) {
                // Deterministic failures (TLS trust/identity, protocol) will not
                // recover on a retry; surface them immediately.
                if (isNonRetryable(e)) {
                    throw e;
                }
                lastError = e;
                // Backoff plus a small random jitter so re-attempts from many
                // clients don't resynchronize into bursts.
                long sleepMillis = backoffMillis + ThreadLocalRandom.current().nextLong(MAX_JITTER_MILLIS);
                if (budgetNanos >= 0) {
                    long remainingNanos = budgetNanos - (System.nanoTime() - startNanos);
                    if (remainingNanos <= 0) {
                        break;
                    }
                    sleepMillis = Math.min(sleepMillis,
                            TimeUnit.NANOSECONDS.toMillis(remainingNanos));
                }
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Network error on attempt " + attempt + " for request "
                            + requestId + " (" + e + "); re-sending in " + sleepMillis + "ms");
                }
                sleep(sleepMillis);
                if (budgetNanos >= 0 && budgetNanos - (System.nanoTime() - startNanos) <= 0) {
                    break;
                }
                backoffMillis = Math.min(backoffMillis * 2, MAX_BACKOFF.toMillis());
            }
        }
        throw lastError;
    }

    /**
     * True for failures that a retry cannot fix: TLS identity/trust failures and
     * protocol errors. Mirrors OkHttp's own non-recoverable classification so
     * recovery does not pointlessly re-attempt a misconfigured certificate.
     */
    private static boolean isNonRetryable(IOException e) {
        if (e instanceof ProtocolException || e instanceof SSLPeerUnverifiedException) {
            return true;
        }
        return e instanceof SSLHandshakeException && e.getCause() instanceof CertificateException;
    }

    private static void sleep(long millis) throws InterruptedIOException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Network error recovery interrupted");
        }
    }
}
