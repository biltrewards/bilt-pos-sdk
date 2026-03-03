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

import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.SaleToPOIResponse;
import com.bilt.pos.nexo.security.EncryptionException;
import com.bilt.pos.nexo.security.MessageEncryptor;
import com.bilt.pos.nexo.security.SaleToPOISecuredMessage;
import com.bilt.pos.nexo.security.SecurityKey;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Client for communicating with a Bilt payment terminal over the local Nexo
 * Sale to POI protocol (v3.0).
 *
 * <p>The terminal exposes a single HTTPS endpoint at
 * {@code https://<device_ip>:8443/nexo}. Each call sends a
 * {@link SaleToPOIRequest} and receives a {@link SaleToPOIResponse}.</p>
 *
 * <h3>Unencrypted (development/testing)</h3>
 * <pre>{@code
 * BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
 *     .endpoint("https://192.168.1.100:8443/nexo")
 *     .trustAllCertificates()
 *     .build();
 *
 * SaleToPOIResponse response = client.request(myRequest);
 * }</pre>
 *
 * <h3>Encrypted (production)</h3>
 * <pre>{@code
 * SecurityKey key = SecurityKey.builder()
 *     .passphrase("sharedSecret")
 *     .keyIdentifier("myTerminal")
 *     .keyVersion(0)
 *     .build();
 *
 * BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
 *     .endpoint("https://192.168.1.100:8443/nexo")
 *     .securityKey(key)
 *     .build();
 *
 * SaleToPOIResponse response = client.request(myRequest);
 * }</pre>
 */
public final class BiltNexoTerminalClient {

    private static final String DEFAULT_PATH = "/nexo";
    private static final int DEFAULT_PORT = 8443;
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(120);
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");

    private final String endpoint;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final MessageEncryptor encryptor;

    private BiltNexoTerminalClient(Builder builder) {
        this.endpoint = builder.endpoint;
        this.objectMapper = builder.objectMapper != null
                ? builder.objectMapper
                : createDefaultObjectMapper();
        this.httpClient = builder.httpClient != null
                ? builder.httpClient
                : createDefaultHttpClient(builder);
        this.encryptor = builder.securityKey != null
                ? new MessageEncryptor(builder.securityKey)
                : null;
    }

    /**
     * Send a Sale to POI request to the terminal and return the response.
     *
     * <p>If a {@link SecurityKey} was provided to the builder, the request is
     * automatically encrypted and the response is decrypted.</p>
     *
     * @param request the request to send
     * @return the terminal's response
     * @throws BiltNexoClientException if serialization, encryption, HTTP transport,
     *         decryption, or deserialization fails
     */
    public SaleToPOIResponse request(SaleToPOIRequest request) throws BiltNexoClientException {
        return request(request, null);
    }

    /**
     * Send a Sale to POI request with a per-request timeout override.
     *
     * @param request the request to send
     * @param timeout per-request timeout, or {@code null} to use the client default
     * @return the terminal's response
     * @throws BiltNexoClientException if any step in the request/response pipeline fails
     */
    public SaleToPOIResponse request(SaleToPOIRequest request, Duration timeout)
            throws BiltNexoClientException {
        try {
            String jsonBody;

            if (encryptor != null) {
                jsonBody = encryptRequest(request);
            } else {
                jsonBody = objectMapper.writeValueAsString(request);
            }

            Request httpRequest = new Request.Builder()
                    .url(endpoint)
                    .post(RequestBody.create(jsonBody, JSON_MEDIA_TYPE))
                    .build();

            OkHttpClient client = this.httpClient;
            if (timeout != null) {
                client = this.httpClient.newBuilder()
                        .readTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                        .build();
            }

            try (Response httpResponse = client.newCall(httpRequest).execute()) {
                ResponseBody body = httpResponse.body();
                String responseJson = body != null ? body.string() : "";

                if (!httpResponse.isSuccessful()) {
                    throw new BiltNexoClientException(
                            "Terminal returned HTTP " + httpResponse.code() + ": " + responseJson);
                }

                if (encryptor != null) {
                    return decryptResponse(responseJson);
                } else {
                    return objectMapper.readValue(responseJson, SaleToPOIResponse.class);
                }
            }
        } catch (BiltNexoClientException e) {
            throw e;
        } catch (EncryptionException e) {
            throw new BiltNexoClientException("Encryption/decryption failed", e);
        } catch (IOException e) {
            throw new BiltNexoClientException(
                    "Failed to communicate with terminal at " + endpoint, e);
        }
    }

    /**
     * Returns {@code true} if this client encrypts requests.
     */
    public boolean isEncrypted() {
        return encryptor != null;
    }

    /**
     * Create a new {@link Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Convenience factory for an unencrypted client pointing at
     * {@code https://<host>:8443/nexo}.
     *
     * @param host the terminal's IP address or hostname
     * @return a new client instance
     */
    public static BiltNexoTerminalClient create(String host) {
        return builder()
                .endpoint("https://" + host + ":" + DEFAULT_PORT + DEFAULT_PATH)
                .build();
    }

    // -----------------------------------------------------------------------
    // Encryption helpers
    // -----------------------------------------------------------------------

    private String encryptRequest(SaleToPOIRequest request) throws IOException, EncryptionException {
        String plainJson = objectMapper.writeValueAsString(request);
        SaleToPOISecuredMessage secured =
                encryptor.encrypt(plainJson, request.getMessageHeader());

        SecuredRequestEnvelope envelope = new SecuredRequestEnvelope();
        envelope.saleToPOIRequest = secured;
        return objectMapper.writeValueAsString(envelope);
    }

    private SaleToPOIResponse decryptResponse(String responseJson) throws IOException, EncryptionException {
        SecuredResponseEnvelope envelope =
                objectMapper.readValue(responseJson, SecuredResponseEnvelope.class);
        String plainJson = encryptor.decrypt(envelope.saleToPOIResponse);
        return objectMapper.readValue(plainJson, SaleToPOIResponse.class);
    }

    /** Wire envelope: {@code {"SaleToPOIRequest": <secured message>}} */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static final class SecuredRequestEnvelope {
        @JsonProperty("SaleToPOIRequest")
        SaleToPOISecuredMessage saleToPOIRequest;
    }

    /** Wire envelope: {@code {"SaleToPOIResponse": <secured message>}} */
    static final class SecuredResponseEnvelope {
        @JsonProperty("SaleToPOIResponse")
        SaleToPOISecuredMessage saleToPOIResponse;
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static final class Builder {
        private String endpoint;
        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private Duration readTimeout = DEFAULT_READ_TIMEOUT;
        private OkHttpClient httpClient;
        private ObjectMapper objectMapper;
        private boolean trustAllCertificates;
        private SecurityKey securityKey;

        private Builder() {}

        /**
         * Set the full terminal endpoint URL
         * (e.g. {@code https://192.168.1.100:8443/nexo}).
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Set the TCP connect timeout. Default is 10 seconds.
         */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        /**
         * Set the read timeout (time waiting for the terminal to respond).
         * Default is 120 seconds, which accommodates cardholder interaction
         * during payment transactions.
         */
        public Builder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        /**
         * Enable Nexo message encryption. When set, all requests are
         * AES-encrypted and all responses are decrypted transparently.
         *
         * @param securityKey the shared key provisioned on the terminal
         */
        public Builder securityKey(SecurityKey securityKey) {
            this.securityKey = securityKey;
            return this;
        }

        /**
         * Disable TLS certificate verification.
         * <strong>For development and testing only</strong> — the terminal uses
         * a self-signed certificate.
         */
        public Builder trustAllCertificates() {
            this.trustAllCertificates = true;
            return this;
        }

        /**
         * Provide a custom {@link OkHttpClient}. When set, the timeout and
         * TLS settings on this builder are ignored.
         */
        public Builder httpClient(OkHttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Provide a custom {@link ObjectMapper}. When not set, a default mapper
         * is created that omits null fields and ignores unknown properties.
         */
        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        public BiltNexoTerminalClient build() {
            if (endpoint == null) {
                throw new IllegalStateException("endpoint is required");
            }
            return new BiltNexoTerminalClient(this);
        }
    }

    // -----------------------------------------------------------------------
    // Defaults
    // -----------------------------------------------------------------------

    private static ObjectMapper createDefaultObjectMapper() {
        return new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static OkHttpClient createDefaultHttpClient(Builder builder) {
        OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder()
                .connectTimeout(builder.connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(builder.readTimeout.toMillis(), TimeUnit.MILLISECONDS);

        if (builder.trustAllCertificates) {
            X509TrustManager trustManager = createTrustAllManager();
            httpBuilder
                    .sslSocketFactory(createTrustAllSocketFactory(trustManager), trustManager)
                    .hostnameVerifier((hostname, session) -> true);
        }

        return httpBuilder.build();
    }

    private static X509TrustManager createTrustAllManager() {
        return new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
    }

    private static SSLSocketFactory createTrustAllSocketFactory(X509TrustManager trustManager) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, null);
            return sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Failed to create trust-all SSLContext", e);
        }
    }
}
