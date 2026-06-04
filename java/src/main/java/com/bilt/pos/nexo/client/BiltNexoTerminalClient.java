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

import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.SaleToPOIResponse;
import com.bilt.pos.nexo.security.EncryptionException;
import com.bilt.pos.nexo.security.MessageEncryptor;
import com.bilt.pos.nexo.security.SaleToPOISecuredMessage;
import com.bilt.pos.nexo.security.SecurityKey;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Client for communicating with a Bilt payment terminal over the local Nexo
 * Sale to POI protocol (v3.0).
 *
 * <p>The terminal exposes a single HTTPS endpoint at
 * {@code https://<device_ip>:8443/nexo}. Each call sends a
 * {@link NexoTerminalAPI} containing a {@link SaleToPOIRequest} and receives
 * a {@link NexoTerminalAPI} containing a {@link SaleToPOIResponse}.</p>
 *
 * <h2>Unencrypted (development/testing)</h2>
 * <pre>{@code
 * BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
 *     .endpoint("https://192.168.1.100:8443/nexo")
 *     .trustAllCertificates()
 *     .build();
 *
 * NexoTerminalAPI request = NexoTerminalAPI.builder()
 *     .saleToPOIRequest(myRequest)
 *     .build();
 * NexoTerminalAPI response = client.request(request);
 * if (response != null) {
 *     SaleToPOIResponse poiResponse = response.getSaleToPOIResponse();
 * }
 * }</pre>
 *
 * <h2>Encrypted (production)</h2>
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
 * NexoTerminalAPI response = client.request(request);
 * if (response != null) {
 *     SaleToPOIResponse poiResponse = response.getSaleToPOIResponse();
 * }
 * }</pre>
 *
 * <h2>Verified TLS (recommended)</h2>
 * <p>Rather than disabling verification with {@link Builder#trustAllCertificates()},
 * trust the merchant (or small-merchant-pool) CA certificate so any device leaf
 * certificate issued under it is accepted, and verify the device identity
 * against the certificate's synthetic hostname instead of the connection IP:</p>
 * <pre>{@code
 * BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
 *     .endpoint("https://192.168.1.100:8443/nexo")
 *     .trustCertificate(Path.of("/etc/bilt/merchant-ca.pem"))
 *     .expectedHostnamePattern("*.live.pos.bilt.com")
 *     .build();
 * }</pre>
 */
public final class BiltNexoTerminalClient {

    private static final Logger LOG = Logger.getLogger(BiltNexoTerminalClient.class.getName());

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
     * @param request the request envelope; must have {@code saleToPOIRequest} set
     * @return the terminal's response envelope containing {@code saleToPOIResponse},
     *         or {@code null} if the terminal returns an empty body (e.g. abort requests)
     * @throws BiltNexoClientException if serialization, encryption, HTTP transport,
     *         decryption, or deserialization fails
     */
    public NexoTerminalAPI request(NexoTerminalAPI request) throws BiltNexoClientException {
        return request(request, null);
    }

    /**
     * Send a Sale to POI request with a per-request timeout override.
     *
     * @param request the request envelope; must have {@code saleToPOIRequest} set
     * @param timeout per-request timeout, or {@code null} to use the client default
     * @return the terminal's response envelope containing {@code saleToPOIResponse},
     *         or {@code null} if the terminal returns an empty body (e.g. abort requests)
     * @throws BiltNexoClientException if any step in the request/response pipeline fails
     */
    public NexoTerminalAPI request(NexoTerminalAPI request, Duration timeout)
            throws BiltNexoClientException {
        SaleToPOIRequest saleToPOIRequest = request.getSaleToPOIRequest();
        if (saleToPOIRequest == null) {
            throw new BiltNexoClientException("request must have saleToPOIRequest set");
        }

        try {
            String jsonBody;

            if (encryptor != null) {
                LOG.fine("Encrypting Sale to POI request payload");
                jsonBody = encryptRequest(saleToPOIRequest);
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

            String responseJson;
            try (Response httpResponse = client.newCall(httpRequest).execute()) {
                ResponseBody body = httpResponse.body();
                responseJson = body != null ? body.string() : "";

                if (!httpResponse.isSuccessful()) {
                    throw new BiltNexoClientException(
                            "Terminal returned HTTP " + httpResponse.code() + ": " + responseJson);
                }
            }

            if (responseJson.isEmpty()) {
                return null;
            }

            NexoTerminalAPI responseApi;
            try {
                responseApi = parseResponse(responseJson);
            } catch (EncryptionException e) {
                throw e;
            } catch (Exception e) {
                throw new BiltNexoClientException(
                        "Failed to parse terminal response: " + responseJson, e);
            }

            return responseApi;
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

    private NexoTerminalAPI parseResponse(String responseJson) throws IOException, EncryptionException {
        SecuredResponseEnvelope envelope =
                objectMapper.readValue(responseJson, SecuredResponseEnvelope.class);
        SaleToPOISecuredMessage secured = envelope.saleToPOIResponse;

        if (secured != null && secured.getEnvelopedData() != null) {
            if (encryptor == null) {
                throw new EncryptionException(
                        "Received encrypted response but no SecurityKey is configured");
            }
            LOG.fine("Decrypting Sale to POI response payload");
            // Extract raw header bytes from the JSON tree to preserve wire field
            // ordering for HMAC verification. Re-serializing the deserialized
            // MessageHeader could produce different byte output if the terminal's
            // JSON library uses a different field ordering.
            JsonNode responseNode = objectMapper.readTree(responseJson)
                    .get("SaleToPOIResponse");
            JsonNode headerNode = responseNode != null ? responseNode.get("MessageHeader") : null;
            if (headerNode == null) {
                throw new EncryptionException("Missing MessageHeader in encrypted response");
            }
            byte[] rawHeaderBytes = objectMapper.writeValueAsBytes(headerNode);
            String plainJson = encryptor.decrypt(secured, rawHeaderBytes);
            SaleToPOIResponse saleToPOIResponse =
                    objectMapper.readValue(plainJson, SaleToPOIResponse.class);
            return NexoTerminalAPI.builder()
                    .saleToPOIResponse(saleToPOIResponse)
                    .build();
        } else {
            return objectMapper.readValue(responseJson, NexoTerminalAPI.class);
        }
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
        private final List<X509Certificate> trustedCertificates = new ArrayList<>();
        private String hostnamePattern;
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
         * Add a trust anchor: trust the terminal's TLS certificate by supplying
         * the certificate it chains to — in production this is the merchant or
         * small-merchant-pool CA certificate, so every device leaf certificate
         * issued under that CA is accepted.
         *
         * <p>Unlike {@link #trustAllCertificates()}, the TLS handshake chain is
         * fully verified: only certificates that chain to one of the supplied
         * anchors are accepted. Because Bilt terminals present a leaf
         * certificate whose Subject Alternative Name is a synthetic,
         * non-resolving hostname ({@code {Model}-{Serial}.live.pos.bilt.com})
         * while the client connects by IP address, pair this with
         * {@link #expectedHostnamePattern(String)} so the certificate identity
         * is verified against the SAN rather than the connection address.</p>
         *
         * <p>This method may be called multiple times to add several anchors.</p>
         *
         * @param certificatePath path to a PEM- or DER-encoded X.509 certificate
         * @throws IllegalArgumentException if the file cannot be read or parsed
         */
        public Builder trustCertificate(Path certificatePath) {
            try (InputStream in = Files.newInputStream(certificatePath)) {
                return trustCertificate(in);
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        "Failed to read certificate from " + certificatePath, e);
            }
        }

        /**
         * Trust the terminal's TLS certificate by pinning the supplied public
         * certificate file. See {@link #trustCertificate(Path)}.
         *
         * @param certificateFile a PEM- or DER-encoded X.509 certificate file
         * @throws IllegalArgumentException if the file cannot be read or parsed
         */
        public Builder trustCertificate(File certificateFile) {
            return trustCertificate(certificateFile.toPath());
        }

        /**
         * Trust the terminal's TLS certificate by pinning a certificate loaded
         * from the given classpath resource (e.g. one bundled in {@code
         * src/main/resources}). See {@link #trustCertificate(Path)}.
         *
         * @param resourceName the resource path, e.g. {@code "certs/terminal.pem"}
         * @throws IllegalArgumentException if the resource is missing or unparseable
         */
        public Builder trustCertificateResource(String resourceName) {
            InputStream in = BiltNexoTerminalClient.class
                    .getClassLoader().getResourceAsStream(resourceName);
            if (in == null) {
                throw new IllegalArgumentException(
                        "Certificate resource not found on classpath: " + resourceName);
            }
            try (InputStream stream = in) {
                return trustCertificate(stream);
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        "Failed to read certificate resource: " + resourceName, e);
            }
        }

        /**
         * Trust the terminal's TLS certificate by pinning one or more X.509
         * certificates read from the supplied stream. See
         * {@link #trustCertificate(Path)}.
         *
         * <p>The stream is consumed but not closed; the caller owns its
         * lifecycle.</p>
         *
         * @param certificateStream PEM- or DER-encoded X.509 certificate data
         * @throws IllegalArgumentException if no certificate can be parsed
         */
        public Builder trustCertificate(InputStream certificateStream) {
            try {
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                Collection<? extends Certificate> certs =
                        factory.generateCertificates(certificateStream);
                if (certs.isEmpty()) {
                    throw new IllegalArgumentException(
                            "No X.509 certificate found in the supplied stream");
                }
                for (Certificate cert : certs) {
                    this.trustedCertificates.add((X509Certificate) cert);
                }
                return this;
            } catch (CertificateException e) {
                throw new IllegalArgumentException("Failed to parse X.509 certificate", e);
            }
        }

        /**
         * Trust the terminal's TLS certificate by pinning an already-loaded
         * {@link X509Certificate}. See {@link #trustCertificate(Path)}.
         */
        public Builder trustCertificate(X509Certificate certificate) {
            this.trustedCertificates.add(certificate);
            return this;
        }

        /**
         * Verify the terminal's identity against the standard hostname pattern
         * for the given Bilt environment, instead of matching the connection's
         * IP address. Equivalent to {@code expectedHostnamePattern(
         * environment.hostnamePattern())}.
         *
         * <p>The caller must still trust the CA hierarchy for the <em>same</em>
         * environment via {@link #trustCertificate(Path)}; production and
         * staging use fully separate CA roots, so the trust anchor and the
         * environment must agree.</p>
         *
         * @param environment the deployment environment to scope trust to
         */
        public Builder environment(BiltTerminalEnvironment environment) {
            return expectedHostnamePattern(environment.hostnamePattern());
        }

        /**
         * Verify the terminal's identity by matching the certificate's Subject
         * Alternative Name (DNS entries, falling back to the Common Name)
         * against the given pattern, instead of matching the connection's IP
         * address.
         *
         * <p>Bilt terminals are reached by IP address but present a leaf
         * certificate carrying a synthetic, non-resolving hostname of the form
         * {@code {Model}-{Serial}.live.pos.bilt.com}. Standard hostname
         * verification would reject this because the connection host (the IP)
         * never matches that name. Setting a pattern such as
         * {@code "*.live.pos.bilt.com"} replaces the default verifier with one
         * that accepts any terminal whose certificate name matches the
         * pattern.</p>
         *
         * <p>The pattern supports a single leading wildcard label, e.g.
         * {@code "*.live.pos.bilt.com"} matches {@code
         * "V240m-ABC123.live.pos.bilt.com"} (one label before the suffix) but
         * not {@code "a.b.live.pos.bilt.com"}. A pattern without a wildcard is
         * matched exactly. Matching is case-insensitive.</p>
         *
         * <p>This controls identity verification only; the certificate chain is
         * still validated against the anchors added with
         * {@link #trustCertificate(Path)}. It has no effect when
         * {@link #trustAllCertificates()} is set.</p>
         *
         * @param pattern the expected SAN/CN pattern, e.g. {@code "*.live.pos.bilt.com"}
         */
        public Builder expectedHostnamePattern(String pattern) {
            this.hostnamePattern = pattern;
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
            if (!trustedCertificates.isEmpty() && hostnamePattern == null) {
                // A trust anchor verifies the chain, but OkHttp's default verifier
                // would still match the connection IP against the certificate.
                // Bilt terminals are reached by IP yet present a synthetic-hostname
                // SAN, so that check can never pass — fail fast rather than emit a
                // confusing handshake error at request time.
                throw new IllegalStateException(
                        "A trust anchor was set via trustCertificate(...) but no "
                        + "expectedHostnamePattern(...) or environment(...) was configured. "
                        + "Bilt terminals are reached by IP while presenting a certificate "
                        + "whose SAN is a synthetic hostname, so default hostname verification "
                        + "would reject the connection. Set the expected hostname pattern "
                        + "(e.g. expectedHostnamePattern(\"*.live.pos.bilt.com\") or "
                        + "environment(BiltTerminalEnvironment.PRODUCTION)), or use "
                        + "trustAllCertificates() for testing.");
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
                    .sslSocketFactory(createSocketFactory(trustManager), trustManager)
                    .hostnameVerifier((hostname, session) -> true);
        } else {
            if (!builder.trustedCertificates.isEmpty()) {
                X509TrustManager trustManager = createTrustManager(builder.trustedCertificates);
                httpBuilder.sslSocketFactory(createSocketFactory(trustManager), trustManager);
            }
            if (builder.hostnamePattern != null) {
                httpBuilder.hostnameVerifier(createPatternHostnameVerifier(builder.hostnamePattern));
            }
        }

        return httpBuilder.build();
    }

    /**
     * Build a trust manager that accepts only certificates chaining to one of
     * the supplied trust anchors. The anchors are loaded into an in-memory
     * {@link KeyStore} as trusted entries.
     */
    private static X509TrustManager createTrustManager(List<X509Certificate> certificates) {
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            int i = 0;
            for (X509Certificate certificate : certificates) {
                keyStore.setCertificateEntry("anchor-" + i++, certificate);
            }

            TrustManagerFactory factory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            factory.init(keyStore);

            for (TrustManager trustManager : factory.getTrustManagers()) {
                if (trustManager instanceof X509TrustManager) {
                    return (X509TrustManager) trustManager;
                }
            }
            throw new IllegalStateException("No X509TrustManager produced for pinned certificates");
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Failed to build pinned-certificate trust manager", e);
        }
    }

    private static X509TrustManager createTrustAllManager() {
        return new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
    }

    private static SSLSocketFactory createSocketFactory(X509TrustManager trustManager) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, null);
            return sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Failed to create SSLContext", e);
        }
    }

    /**
     * Build a hostname verifier that ignores the connection address and instead
     * matches the peer leaf certificate's DNS SANs (falling back to the CN)
     * against {@code pattern}. The certificate chain is validated separately by
     * the trust manager; this only checks identity.
     */
    private static HostnameVerifier createPatternHostnameVerifier(String pattern) {
        return (connectionHost, session) -> {
            try {
                Certificate[] peerCertificates = session.getPeerCertificates();
                if (peerCertificates.length == 0
                        || !(peerCertificates[0] instanceof X509Certificate)) {
                    return false;
                }
                X509Certificate leaf = (X509Certificate) peerCertificates[0];
                for (String name : certificateNames(leaf)) {
                    if (hostnameMatchesPattern(name, pattern)) {
                        return true;
                    }
                }
                return false;
            } catch (SSLPeerUnverifiedException e) {
                return false;
            }
        };
    }

    /** Collect a certificate's DNS SAN entries, with the Common Name as a fallback. */
    private static List<String> certificateNames(X509Certificate certificate) {
        List<String> names = new ArrayList<>();
        try {
            Collection<List<?>> sans = certificate.getSubjectAlternativeNames();
            if (sans != null) {
                for (List<?> san : sans) {
                    // [0] = general-name type (2 == dNSName), [1] = value
                    if (san.size() >= 2 && Integer.valueOf(2).equals(san.get(0))
                            && san.get(1) instanceof String) {
                        names.add((String) san.get(1));
                    }
                }
            }
        } catch (CertificateParsingException e) {
            // Malformed SAN extension: fall through to the CN.
        }
        String commonName = commonName(certificate);
        if (commonName != null) {
            names.add(commonName);
        }
        return names;
    }

    private static String commonName(X509Certificate certificate) {
        try {
            LdapName name = new LdapName(certificate.getSubjectX500Principal().getName());
            for (Rdn rdn : name.getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    return String.valueOf(rdn.getValue());
                }
            }
        } catch (InvalidNameException e) {
            // Unparseable subject: no CN fallback available.
        }
        return null;
    }

    /**
     * Match a certificate name against a pattern that supports a single leading
     * wildcard label (e.g. {@code "*.live.pos.bilt.com"} matches one label
     * before the suffix). Patterns without a wildcard are matched exactly.
     * Matching is case-insensitive.
     */
    private static boolean hostnameMatchesPattern(String name, String pattern) {
        String host = name.toLowerCase(Locale.ROOT);
        String expected = pattern.toLowerCase(Locale.ROOT);
        if (expected.startsWith("*.")) {
            String suffix = expected.substring(1); // ".live.pos.bilt.com"
            if (!host.endsWith(suffix)) {
                return false;
            }
            String label = host.substring(0, host.length() - suffix.length());
            return !label.isEmpty() && label.indexOf('.') < 0;
        }
        return host.equals(expected);
    }
}
