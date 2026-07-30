package com.bilt.pos.emulator.session

import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory

/**
 * Out-of-band TLS verification probe. The emulator's payload channel always
 * runs on a trust-all client so a bad certificate never blocks testing; this
 * probe performs one strict handshake — chain validated against the
 * configured CA, leaf identity matched against the environment's hostname
 * pattern (terminals present a synthetic SAN, never the IP we dial) — and
 * reports the outcome for display.
 */
object TlsVerifier {

    private const val TIMEOUT_MS = 5_000

    fun verify(host: String, port: Int, caPem: String?, hostnamePattern: String): TlsStatus {
        if (caPem.isNullOrBlank()) {
            return TlsStatus.NotConfigured
        }
        return try {
            val context = strictContext(caPem)
            val leaf = handshake(context, host, port)
            val names = certificateNames(leaf)
            if (names.any { matchesPattern(it, hostnamePattern) }) {
                TlsStatus.Verified
            } else {
                TlsStatus.Failed(
                    "certificate names $names do not match pattern $hostnamePattern"
                )
            }
        } catch (e: Exception) {
            TlsStatus.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun strictContext(caPem: String): SSLContext {
        val factory = CertificateFactory.getInstance("X.509")
        val anchors = factory.generateCertificates(ByteArrayInputStream(caPem.toByteArray()))
        require(anchors.isNotEmpty()) { "no X.509 certificate in NEXO_CA_CERT/NEXO_CA_BUNDLE" }

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            anchors.forEachIndexed { i, cert -> setCertificateEntry("ca-$i", cert) }
        }
        val trustManagers = TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore) }
            .trustManagers
        return SSLContext.getInstance("TLS").apply { init(null, trustManagers, null) }
    }

    /** Performs the handshake (chain validation happens here) and returns the leaf. */
    private fun handshake(context: SSLContext, host: String, port: Int): X509Certificate {
        // Connect explicitly so the connect phase is bounded too — the
        // createSocket(host, port) constructor connects with no timeout and
        // soTimeout only bounds reads after the TCP connect succeeds
        Socket().use { tcp ->
            tcp.connect(InetSocketAddress(host, port), TIMEOUT_MS)
            tcp.soTimeout = TIMEOUT_MS
            (context.socketFactory.createSocket(tcp, host, port, true) as SSLSocket).use { socket ->
                socket.soTimeout = TIMEOUT_MS
                socket.startHandshake()
                return socket.session.peerCertificates.first() as X509Certificate
            }
        }
    }

    /** DNS Subject Alternative Names, falling back to the Common Name. */
    private fun certificateNames(cert: X509Certificate): List<String> {
        val dnsType = 2
        val sans = cert.subjectAlternativeNames
            ?.filter { it.size >= 2 && it[0] == dnsType }
            ?.mapNotNull { it[1] as? String }
            .orEmpty()
        if (sans.isNotEmpty()) {
            return sans
        }
        val cn = cert.subjectX500Principal.name
            .split(',')
            .firstOrNull { it.trim().startsWith("CN=") }
            ?.trim()?.removePrefix("CN=")
        return listOfNotNull(cn)
    }

    /**
     * Same semantics as the SDK client: a single leading `*.` wildcard matches
     * exactly one label; otherwise exact match. Case-insensitive.
     */
    private fun matchesPattern(name: String, pattern: String): Boolean {
        if (!pattern.startsWith("*.")) {
            return name.equals(pattern, ignoreCase = true)
        }
        val suffix = pattern.substring(1) // ".pos.staging.bilt.dev"
        if (!name.endsWith(suffix, ignoreCase = true)) {
            return false
        }
        val label = name.dropLast(suffix.length)
        return label.isNotEmpty() && !label.contains('.')
    }
}
