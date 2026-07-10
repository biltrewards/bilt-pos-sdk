/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.nexo.client

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ProtocolException
import java.security.cert.CertificateException
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * OkHttp application interceptor that recovers an in-flight request across a
 * transient network failure without re-executing the operation on the terminal.
 *
 * Each logical request is tagged with a stable [REQUEST_ID_HEADER] UUID. On any
 * [IOException] the interceptor re-sends the identical request (same id) after a
 * backoff, until a response arrives or the operation timeout elapses. The
 * terminal keys execution on the id and treats an already-seen id as a recovery
 * rather than a new operation, so the operation runs at most once no matter how
 * many times the client re-attempts.
 *
 * The overall recovery budget is the call's read timeout
 * ([Interceptor.Chain.readTimeoutMillis]), treated as the timeout for the whole
 * operation, so overriding the timeout (on the client or per request) changes the
 * budget too; `0` ("no timeout") means recovery retries until it succeeds. Each
 * individual attempt is additionally capped at a shorter per-attempt timeout —
 * derived from the read timeout as `max(readTimeout / 6, min(10s, readTimeout))`
 * (≈20s at the 120s default) — via [Interceptor.Chain.withReadTimeout], so a
 * single stuck attempt — e.g. a TLS handshake to a silently black-holed peer,
 * which OkHttp otherwise bounds by the full read timeout — cannot consume the
 * whole budget.
 *
 * `internal` by design: wired in only via the `recoverOnNetworkError` constructor
 * flag and not part of the public API. See `docs/network-error-recovery.md` for
 * the full client/server contract.
 */
internal class NetworkErrorRecoveryInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val requestId = UUID.randomUUID().toString()
        val request = chain.request().newBuilder()
            .header(REQUEST_ID_HEADER, requestId)
            .build()

        val timeoutMillis = chain.readTimeoutMillis()
        val budgetNanos = if (timeoutMillis > 0) TimeUnit.MILLISECONDS.toNanos(timeoutMillis.toLong()) else -1L
        val startNanos = System.nanoTime()
        val perAttemptMillis = perAttemptTimeoutMillis(timeoutMillis)
        var backoffMillis = INITIAL_BACKOFF.toMillis()
        var attempt = 0
        var lastError: IOException? = null

        while (true) {
            attempt++
            var attemptTimeoutMillis = perAttemptMillis
            if (budgetNanos >= 0) {
                val remainingMillis = TimeUnit.NANOSECONDS.toMillis(budgetNanos - (System.nanoTime() - startNanos))
                attemptTimeoutMillis = minOf(perAttemptMillis, maxOf(1L, remainingMillis))
            }
            try {
                // Re-sending the identical request is safe: the terminal keys
                // execution on the request id and only runs the operation once.
                return chain.withReadTimeout(attemptTimeoutMillis.toInt(), TimeUnit.MILLISECONDS)
                    .proceed(request)
            } catch (e: IOException) {
                // Deterministic failures (TLS trust/identity, protocol) will not
                // recover on a retry; surface them immediately.
                if (isNonRetryable(e)) throw e
                lastError = e
                // Backoff plus a small random jitter so re-attempts from many
                // clients don't resynchronize into bursts.
                var sleepMillis = backoffMillis + ThreadLocalRandom.current().nextLong(MAX_JITTER_MILLIS)
                if (budgetNanos >= 0) {
                    val remainingNanos = budgetNanos - (System.nanoTime() - startNanos)
                    if (remainingNanos <= 0) break
                    sleepMillis = minOf(sleepMillis, TimeUnit.NANOSECONDS.toMillis(remainingNanos))
                }
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine(
                        "Network error on attempt $attempt for request $requestId " +
                            "($e); re-sending in ${sleepMillis}ms"
                    )
                }
                sleep(sleepMillis)
                if (budgetNanos >= 0 && budgetNanos - (System.nanoTime() - startNanos) <= 0) break
                backoffMillis = minOf(backoffMillis * 2, MAX_BACKOFF.toMillis())
            }
        }
        throw lastError
    }

    private fun sleep(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Network error recovery interrupted")
        }
    }

    /**
     * True for failures that a retry cannot fix: TLS identity/trust failures and
     * protocol errors. Mirrors OkHttp's own non-recoverable classification so
     * recovery does not pointlessly re-attempt a misconfigured certificate.
     */
    private fun isNonRetryable(e: IOException): Boolean {
        if (e is ProtocolException || e is SSLPeerUnverifiedException) return true
        return e is SSLHandshakeException && e.cause is CertificateException
    }

    companion object {
        /** Correlation header carrying the per-request idempotency UUID. */
        const val REQUEST_ID_HEADER = "X-Bilt-Request-Id"

        private val LOG = Logger.getLogger(NetworkErrorRecoveryInterceptor::class.java.name)
        private val INITIAL_BACKOFF = Duration.ofMillis(500)
        private val MAX_BACKOFF = Duration.ofSeconds(3)
        private const val MAX_JITTER_MILLIS = 500L
        private const val PER_ATTEMPT_FLOOR_MILLIS = 10_000L
        private const val PER_ATTEMPT_DIVISOR = 6L

        /**
         * Derive the per-attempt timeout from the overall (read) timeout:
         * `max(readTimeout / 6, min(10s, readTimeout))`. At least ~10s (or the
         * whole timeout if shorter), otherwise a sixth of the total. A non-positive
         * ("no timeout") read timeout yields the 10s floor so black holes stay
         * bounded.
         */
        internal fun perAttemptTimeoutMillis(readTimeoutMillis: Int): Long {
            if (readTimeoutMillis <= 0) return PER_ATTEMPT_FLOOR_MILLIS
            val floor = minOf(PER_ATTEMPT_FLOOR_MILLIS, readTimeoutMillis.toLong())
            return maxOf(readTimeoutMillis / PER_ATTEMPT_DIVISOR, floor)
        }
    }
}
