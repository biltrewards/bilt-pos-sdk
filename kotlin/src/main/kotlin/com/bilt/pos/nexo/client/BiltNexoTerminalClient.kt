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
import com.bilt.pos.nexo.model.SaleToPOIResponse
import com.bilt.pos.nexo.security.EncryptionException
import com.bilt.pos.nexo.security.MessageEncryptor
import com.bilt.pos.nexo.security.SaleToPOISecuredMessage
import com.bilt.pos.nexo.security.SecurityKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
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
 */
class BiltNexoTerminalClient(
    private val endpoint: String,
    securityKey: SecurityKey? = null,
    connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
    readTimeout: Duration = DEFAULT_READ_TIMEOUT,
    trustAllCertificates: Boolean = false,
    httpClient: OkHttpClient? = null
) {
    init {
        require(endpoint.isNotBlank()) { "endpoint is required" }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val encryptor: MessageEncryptor? = securityKey?.let { MessageEncryptor(it) }

    private val httpClient: OkHttpClient = httpClient ?: buildDefaultHttpClient(
        connectTimeout, readTimeout, trustAllCertificates
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
                val plainJson = json.encodeToString(
                    NexoTerminalAPI.serializer(), request
                )
                // Extract just the SaleToPOIRequest content for encryption
                val requestContent = json.parseToJsonElement(plainJson)
                    .jsonObject["SaleToPOIRequest"]!!.toString()
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
        val root = json.parseToJsonElement(responseJson).jsonObject
        val responseNode = root["SaleToPOIResponse"]?.jsonObject

        if (responseNode != null && "EnvelopedData" in responseNode) {
            if (encryptor == null) {
                throw EncryptionException(
                    "Received encrypted response but no SecurityKey is configured"
                )
            }
            // Extract raw header bytes from the JSON tree before deserialization,
            // so the HMAC is verified against the wire bytes, not a re-serialized object.
            val rawHeaderBytes = responseNode["MessageHeader"].toString()
                .toByteArray(Charsets.UTF_8)
            val envelope = json.decodeFromString(
                SecuredResponseEnvelope.serializer(), responseJson
            )
            val plainJson = encryptor.decrypt(envelope.saleToPOIResponse, rawHeaderBytes)
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

        private fun buildDefaultHttpClient(
            connectTimeout: Duration,
            readTimeout: Duration,
            trustAllCertificates: Boolean
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
                try {
                    val sslContext = SSLContext.getInstance("TLS")
                    sslContext.init(null, arrayOf(trustManager), null)
                    builder
                        .sslSocketFactory(sslContext.socketFactory, trustManager)
                        .hostnameVerifier { _, _ -> true }
                } catch (e: Exception) {
                    when (e) {
                        is NoSuchAlgorithmException, is KeyManagementException ->
                            throw RuntimeException("Failed to create trust-all SSLContext", e)
                        else -> throw e
                    }
                }
            }

            return builder.build()
        }
    }
}
