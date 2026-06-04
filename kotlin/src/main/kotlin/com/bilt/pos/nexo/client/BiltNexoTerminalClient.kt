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

import com.bilt.pos.nexo.model.NexoTerminalAPI
import com.bilt.pos.nexo.model.SaleToPOIRequest
import com.bilt.pos.nexo.model.SaleToPOIResponse
import com.bilt.pos.nexo.security.EncryptionException
import com.bilt.pos.nexo.security.MessageEncryptor
import com.bilt.pos.nexo.security.SaleToPOISecuredMessage
import com.bilt.pos.nexo.security.SecurityKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyManagementException
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import javax.naming.ldap.LdapName
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Client for communicating with a Bilt payment terminal over the local Nexo
 * Sale to POI protocol (v3.0).
 *
 * The terminal exposes a single HTTPS endpoint at
 * `https://<device_ip>:8443/nexo`. Each call sends a [NexoTerminalAPI]
 * containing a `SaleToPOIRequest` and receives a [NexoTerminalAPI]
 * containing a `SaleToPOIResponse`.
 *
 * **Unencrypted (development/testing):**
 * ```kotlin
 * val client = BiltNexoTerminalClient(
 *     endpoint = "https://192.168.1.100:8443/nexo",
 *     trustAllCertificates = true
 * )
 * val response = client.request(request)
 * ```
 *
 * **Encrypted (production):**
 * ```kotlin
 * val key = SecurityKey(
 *     passphrase = "sharedSecret",
 *     keyIdentifier = "myTerminal",
 *     keyVersion = 0
 * )
 * val client = BiltNexoTerminalClient(
 *     endpoint = "https://192.168.1.100:8443/nexo",
 *     securityKey = key
 * )
 * val response = client.request(request)
 * ```
 *
 * **Verified TLS (recommended):** trust the merchant (or small-merchant-pool)
 * CA certificate so any device leaf issued under it is accepted, and verify
 * the device identity against the certificate's synthetic hostname instead of
 * the connection IP.
 * ```kotlin
 * val client = BiltNexoTerminalClient(
 *     endpoint = "https://192.168.1.50:8443/nexo",
 *     trustedCertificates = BiltNexoTerminalClient.certificatesFromPath(
 *         Path.of("/etc/bilt/merchant-ca.pem")
 *     ),
 *     environment = BiltTerminalEnvironment.PRODUCTION
 * )
 * ```
 */
class BiltNexoTerminalClient(
    private val endpoint: String,
    securityKey: SecurityKey? = null,
    connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
    readTimeout: Duration = DEFAULT_READ_TIMEOUT,
    trustAllCertificates: Boolean = false,
    trustedCertificates: List<X509Certificate> = emptyList(),
    expectedHostnamePattern: String? = null,
    environment: BiltTerminalEnvironment? = null,
    httpClient: OkHttpClient? = null
) {
    private val hostnamePattern: String? = expectedHostnamePattern ?: environment?.hostnamePattern

    init {
        require(endpoint.isNotBlank()) { "endpoint is required" }
        // Contradictory intent: trustAllCertificates disables all verification,
        // which would silently override the configured CA anchor and hostname
        // pattern. Fail rather than downgrade security.
        require(!(trustAllCertificates && (trustedCertificates.isNotEmpty() || hostnamePattern != null))) {
            "trustAllCertificates cannot be combined with trustedCertificates or " +
                "expectedHostnamePattern/environment. trustAllCertificates disables all TLS " +
                "verification; remove it to verify against the trust anchor, or remove the trust " +
                "anchor to skip verification (testing only)."
        }
        // A trust anchor verifies the chain, but the default verifier would still
        // match the connection IP against the certificate. Bilt terminals are
        // reached by IP yet present a synthetic-hostname SAN, so that check can
        // never pass — fail fast rather than emit a confusing handshake error.
        require(!(trustedCertificates.isNotEmpty() && hostnamePattern == null)) {
            "A trust anchor was set via trustedCertificates but no expectedHostnamePattern or " +
                "environment was configured. Bilt terminals are reached by IP while presenting a " +
                "certificate whose SAN is a synthetic hostname, so default hostname verification " +
                "would reject the connection. Set the expected hostname pattern (e.g. " +
                "expectedHostnamePattern = \"*.live.pos.bilt.com\" or " +
                "environment = BiltTerminalEnvironment.PRODUCTION), or use trustAllCertificates " +
                "for testing."
        }
        // A hostname pattern only checks identity; it does not validate the chain.
        // Without a trust anchor the chain is validated against the JVM default
        // trust store, which does not contain the private Bilt CA — so the
        // CA-anchored model is bypassed. Require the anchor.
        require(!(hostnamePattern != null && trustedCertificates.isEmpty() && !trustAllCertificates)) {
            "expectedHostnamePattern or environment was set without a trust anchor. Hostname " +
                "verification alone does not validate the certificate chain, and the terminal's " +
                "private CA is not in the JVM default trust store. Set trustedCertificates, or use " +
                "trustAllCertificates for testing."
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val logger = Logger.getLogger(BiltNexoTerminalClient::class.java.name)

    private val encryptor: MessageEncryptor? = securityKey?.let { MessageEncryptor(it) }

    private val httpClient: OkHttpClient = httpClient ?: buildDefaultHttpClient(
        connectTimeout, readTimeout, trustAllCertificates, trustedCertificates, hostnamePattern
    )

    /**
     * Returns `true` if this client encrypts requests.
     */
    val isEncrypted: Boolean get() = encryptor != null

    /**
     * Send a Sale to POI request to the terminal and return the response.
     *
     * If a [SecurityKey] was provided at construction, the request is
     * automatically encrypted and the response is decrypted.
     *
     * @param request the request envelope; must have `saleToPOIRequest` set
     * @param timeout per-request timeout override, or `null` to use the client default
     * @return the terminal's response envelope, or `null` if the terminal
     *         returns an empty body (e.g. abort requests)
     * @throws BiltNexoClientException if any step in the request/response pipeline fails
     */
    fun request(request: NexoTerminalAPI, timeout: Duration? = null): NexoTerminalAPI? {
        val saleToPOIRequest = request.saleToPOIRequest
            ?: throw BiltNexoClientException("request must have saleToPOIRequest set")

        try {
            val jsonBody = if (encryptor != null) {
                logger.fine("Encrypting Sale to POI request payload")
                val requestContent = json.encodeToString(
                    SaleToPOIRequest.serializer(), saleToPOIRequest
                )
                val secured = encryptor.encrypt(requestContent, saleToPOIRequest.messageHeader)
                json.encodeToString(
                    SecuredRequestEnvelope.serializer(),
                    SecuredRequestEnvelope(secured)
                )
            } else {
                json.encodeToString(NexoTerminalAPI.serializer(), request)
            }

            val httpRequest = Request.Builder()
                .url(endpoint)
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val client = if (timeout != null) {
                this.httpClient.newBuilder()
                    .readTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .build()
            } else {
                this.httpClient
            }

            val responseJson: String
            client.newCall(httpRequest).execute().use { response ->
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    throw BiltNexoClientException(
                        "Terminal returned HTTP ${response.code}: $body"
                    )
                }

                responseJson = body
            }

            if (responseJson.isEmpty()) return null

            return try {
                parseResponse(responseJson)
            } catch (e: EncryptionException) {
                throw e
            } catch (e: Exception) {
                throw BiltNexoClientException(
                    "Failed to parse terminal response: $responseJson", e
                )
            }
        } catch (e: BiltNexoClientException) {
            throw e
        } catch (e: EncryptionException) {
            throw BiltNexoClientException("Encryption/decryption failed", e)
        } catch (e: IOException) {
            throw BiltNexoClientException(
                "Failed to communicate with terminal at $endpoint", e
            )
        }
    }

    // -----------------------------------------------------------------------
    // Response parsing with encryption detection
    // -----------------------------------------------------------------------

    private fun parseResponse(responseJson: String): NexoTerminalAPI {
        val envelope = json.decodeFromString(
            SecuredResponseEnvelope.serializer(), responseJson
        )
        val secured = envelope.saleToPOIResponse

        if (secured.envelopedData != null) {
            if (encryptor == null) {
                throw EncryptionException(
                    "Received encrypted response but no SecurityKey is configured"
                )
            }
            logger.fine("Decrypting Sale to POI response payload")
            // Extract raw header bytes from the parsed JSON tree to preserve
            // wire field ordering for HMAC verification. Re-serializing the
            // deserialized MessageHeader could produce different byte output
            // if the terminal's JSON library uses a different field ordering.
            val responseNode = json.parseToJsonElement(responseJson)
                .jsonObject["SaleToPOIResponse"]!!.jsonObject
            val rawHeaderBytes = (responseNode["MessageHeader"]
                ?: throw EncryptionException("Missing MessageHeader in encrypted response"))
                .toString().toByteArray(Charsets.UTF_8)
            val plainJson = encryptor.decrypt(secured, rawHeaderBytes)
            val saleToPOIResponse = json.decodeFromString(
                SaleToPOIResponse.serializer(), plainJson
            )
            return NexoTerminalAPI(saleToPOIResponse = saleToPOIResponse)
        } else {
            return json.decodeFromString(NexoTerminalAPI.serializer(), responseJson)
        }
    }

    // -----------------------------------------------------------------------
    // Wire envelopes for encrypted messages
    // -----------------------------------------------------------------------

    @Serializable
    private data class SecuredRequestEnvelope(
        @SerialName("SaleToPOIRequest")
        val saleToPOIRequest: SaleToPOISecuredMessage
    )

    @Serializable
    private data class SecuredResponseEnvelope(
        @SerialName("SaleToPOIResponse")
        val saleToPOIResponse: SaleToPOISecuredMessage
    )

    companion object {
        private const val DEFAULT_PATH = "/nexo"
        private const val DEFAULT_PORT = 8443
        private val DEFAULT_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
        private val DEFAULT_READ_TIMEOUT: Duration = Duration.ofSeconds(120)
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /**
         * Convenience factory for an unencrypted client pointing at
         * `https://<host>:8443/nexo`.
         */
        fun create(host: String): BiltNexoTerminalClient =
            BiltNexoTerminalClient(
                endpoint = "https://$host:$DEFAULT_PORT$DEFAULT_PATH"
            )

        /**
         * Load X.509 certificate(s) (PEM or DER) from a filesystem path, for use
         * as `trustedCertificates`. In production this is the merchant or
         * small-merchant-pool CA certificate.
         *
         * @throws IllegalArgumentException if the file cannot be read or parsed
         */
        fun certificatesFromPath(path: Path): List<X509Certificate> =
            try {
                Files.newInputStream(path).use { certificatesFromStream(it) }
            } catch (e: IOException) {
                throw IllegalArgumentException("Failed to read certificate from $path", e)
            }

        /**
         * Load X.509 certificate(s) from a classpath resource (e.g. one bundled
         * in `src/main/resources`). See [certificatesFromPath].
         *
         * @throws IllegalArgumentException if the resource is missing or unparseable
         */
        fun certificatesFromResource(resource: String): List<X509Certificate> {
            val stream = BiltNexoTerminalClient::class.java.classLoader
                .getResourceAsStream(resource)
                ?: throw IllegalArgumentException(
                    "Certificate resource not found on classpath: $resource"
                )
            return try {
                stream.use { certificatesFromStream(it) }
            } catch (e: IOException) {
                throw IllegalArgumentException("Failed to read certificate resource: $resource", e)
            }
        }

        /** Load X.509 certificate(s) from an inline PEM string. See [certificatesFromPath]. */
        fun certificatesFromPem(pem: String): List<X509Certificate> =
            certificatesFromStream(pem.byteInputStream())

        /**
         * Load X.509 certificate(s) from a stream. The stream is consumed but
         * not closed; the caller owns its lifecycle. See [certificatesFromPath].
         *
         * @throws IllegalArgumentException if no certificate can be parsed
         */
        fun certificatesFromStream(inputStream: InputStream): List<X509Certificate> {
            val certificates = try {
                CertificateFactory.getInstance("X.509").generateCertificates(inputStream)
            } catch (e: java.security.cert.CertificateException) {
                throw IllegalArgumentException("Failed to parse X.509 certificate", e)
            }
            require(certificates.isNotEmpty()) {
                "No X.509 certificate found in the supplied stream"
            }
            return certificates.map { it as X509Certificate }
        }

        private fun buildDefaultHttpClient(
            connectTimeout: Duration,
            readTimeout: Duration,
            trustAllCertificates: Boolean,
            trustedCertificates: List<X509Certificate>,
            hostnamePattern: String?
        ): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS)

            if (trustAllCertificates) {
                val trustManager = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
                builder
                    .sslSocketFactory(socketFactory(trustManager), trustManager)
                    .hostnameVerifier { _, _ -> true }
            } else {
                if (trustedCertificates.isNotEmpty()) {
                    val trustManager = trustManagerFor(trustedCertificates)
                    builder.sslSocketFactory(socketFactory(trustManager), trustManager)
                }
                if (hostnamePattern != null) {
                    builder.hostnameVerifier(patternHostnameVerifier(hostnamePattern))
                }
            }

            return builder.build()
        }

        /**
         * Build a trust manager that accepts only certificates chaining to one
         * of the supplied trust anchors, loaded into an in-memory key store.
         */
        private fun trustManagerFor(certificates: List<X509Certificate>): X509TrustManager {
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                certificates.forEachIndexed { i, cert -> setCertificateEntry("anchor-$i", cert) }
            }
            val factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
            ).apply { init(keyStore) }
            return factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
                ?: throw IllegalStateException("No X509TrustManager produced for trust anchors")
        }

        private fun socketFactory(trustManager: X509TrustManager) = try {
            SSLContext.getInstance("TLS")
                .apply { init(null, arrayOf(trustManager), null) }
                .socketFactory
        } catch (e: Exception) {
            when (e) {
                is NoSuchAlgorithmException, is KeyManagementException ->
                    throw RuntimeException("Failed to create SSLContext", e)
                else -> throw e
            }
        }

        /**
         * A hostname verifier that ignores the connection address and matches
         * the peer leaf certificate's DNS SANs (CN fallback) against [pattern].
         * The chain is validated separately by the trust manager.
         */
        private fun patternHostnameVerifier(pattern: String) = HostnameVerifier { _, session ->
            try {
                val leaf = session.peerCertificates.firstOrNull() as? X509Certificate
                    ?: return@HostnameVerifier false
                certificateNames(leaf).any { hostnameMatchesPattern(it, pattern) }
            } catch (e: SSLPeerUnverifiedException) {
                false
            }
        }

        /** Collect a certificate's DNS SAN entries, with the Common Name as a fallback. */
        private fun certificateNames(certificate: X509Certificate): List<String> {
            val names = mutableListOf<String>()
            try {
                certificate.subjectAlternativeNames?.forEach { san ->
                    // [0] = general-name type (2 == dNSName), [1] = value
                    if (san.size >= 2 && san[0] == 2 && san[1] is String) {
                        names.add(san[1] as String)
                    }
                }
            } catch (e: java.security.cert.CertificateParsingException) {
                // Malformed SAN extension: fall through to the CN.
            }
            runCatching {
                LdapName(certificate.subjectX500Principal.name).rdns
                    .firstOrNull { it.type.equals("CN", ignoreCase = true) }
                    ?.value?.toString()
            }.getOrNull()?.let { names.add(it) }
            return names
        }

        /**
         * Match a certificate name against a pattern that supports a single
         * leading wildcard label (e.g. `*.live.pos.bilt.com` matches one label
         * before the suffix). Patterns without a wildcard are matched exactly.
         * Matching is case-insensitive.
         */
        private fun hostnameMatchesPattern(name: String, pattern: String): Boolean {
            val host = name.lowercase(Locale.ROOT)
            val expected = pattern.lowercase(Locale.ROOT)
            if (expected.startsWith("*.")) {
                val suffix = expected.substring(1) // ".live.pos.bilt.com"
                if (!host.endsWith(suffix)) return false
                val label = host.substring(0, host.length - suffix.length)
                return label.isNotEmpty() && !label.contains('.')
            }
            return host == expected
        }
    }
}
