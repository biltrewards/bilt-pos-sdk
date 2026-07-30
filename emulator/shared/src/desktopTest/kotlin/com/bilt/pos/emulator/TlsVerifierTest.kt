package com.bilt.pos.emulator

import com.bilt.pos.emulator.session.TlsStatus
import com.bilt.pos.emulator.session.TlsVerifier
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Exercises the public [TlsVerifier.verify] against real TLS handshakes: a
 * local server presents a leaf minted under a test CA with terminal-style
 * synthetic SANs, covering chain validation, SAN extraction, and the
 * hostname-pattern semantics together.
 */
class TlsVerifierTest {

    private val ca = HeldCertificate.Builder()
        .certificateAuthority(1)
        .commonName("Bilt Test CA")
        .build()

    private val servers = mutableListOf<MockWebServer>()

    @AfterTest
    fun tearDown() {
        servers.forEach { runCatching { it.shutdown() } }
    }

    private fun serverPresenting(vararg sans: String): MockWebServer {
        val leaf = HeldCertificate.Builder()
            .signedBy(ca)
            .commonName("terminal")
            .apply { sans.forEach(::addSubjectAlternativeName) }
            .build()
        val certificates = HandshakeCertificates.Builder()
            .heldCertificate(leaf, ca.certificate)
            .build()
        return MockWebServer().apply {
            useHttps(certificates.sslSocketFactory(), false)
            start()
            servers.add(this)
        }
    }

    @Test
    fun verifiedWhenChainTrustedAndSanMatchesWildcard() {
        val server = serverPresenting("V400m-123.pos.staging.bilt.dev")

        val status = TlsVerifier.verify(
            server.hostName, server.port, ca.certificatePem(), "*.pos.staging.bilt.dev",
        )

        assertEquals(TlsStatus.Verified, status)
    }

    @Test
    fun matchingIsCaseInsensitive() {
        val server = serverPresenting("V400M-123.POS.STAGING.BILT.DEV")

        val status = TlsVerifier.verify(
            server.hostName, server.port, ca.certificatePem(), "*.pos.staging.bilt.dev",
        )

        assertEquals(TlsStatus.Verified, status)
    }

    @Test
    fun failsWhenSanIsFromTheOtherEnvironment() {
        val server = serverPresenting("V400m-123.live.pos.bilt.com")

        val status = TlsVerifier.verify(
            server.hostName, server.port, ca.certificatePem(), "*.pos.staging.bilt.dev",
        )

        assertIs<TlsStatus.Failed>(status)
    }

    @Test
    fun wildcardMatchesExactlyOneLabel() {
        val server = serverPresenting("a.b.pos.staging.bilt.dev")

        val status = TlsVerifier.verify(
            server.hostName, server.port, ca.certificatePem(), "*.pos.staging.bilt.dev",
        )

        assertIs<TlsStatus.Failed>(status)
    }

    @Test
    fun failsWhenChainDoesNotReachTheConfiguredCa() {
        val server = serverPresenting("V400m-123.pos.staging.bilt.dev")
        val otherCa = HeldCertificate.Builder()
            .certificateAuthority(1)
            .commonName("Unrelated CA")
            .build()

        val status = TlsVerifier.verify(
            server.hostName, server.port, otherCa.certificatePem(), "*.pos.staging.bilt.dev",
        )

        assertIs<TlsStatus.Failed>(status)
    }

    @Test
    fun notConfiguredWithoutCa() {
        val status = TlsVerifier.verify("localhost", 1, null, "*.pos.staging.bilt.dev")

        assertEquals(TlsStatus.NotConfigured, status)
    }
}
