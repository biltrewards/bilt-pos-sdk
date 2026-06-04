package com.bilt.pos.nexo.client;

import com.bilt.pos.nexo.model.MessageHeader;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies CA-anchored TLS verification with synthetic-hostname matching
 * against a real HTTPS handshake served by {@link MockWebServer}.
 *
 * <p>The server mirrors a Bilt terminal: it presents a device <em>leaf</em>
 * certificate (SAN {@code {Model}-{Serial}.live.pos.bilt.com}) signed by a
 * merchant CA, while the client connects by IP/loopback. The client trusts the
 * CA and matches the SAN against a domain pattern.</p>
 */
class BiltNexoTerminalClientTlsTest {

    private static final String OK_RESPONSE =
            "{\"SaleToPOIResponse\":{\"MessageHeader\":{\"ProtocolVersion\":\"3.0\"}}}";
    private static final String PROD_PATTERN = "*.live.pos.bilt.com";

    private MockWebServer server;

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Start an HTTPS server presenting {@code leafSan} on a leaf certificate
     * signed by {@code ca}.
     */
    private void startServerWithLeaf(HeldCertificate ca, String leafSan) throws Exception {
        HeldCertificate leaf = new HeldCertificate.Builder()
                .commonName(leafSan)
                .addSubjectAlternativeName(leafSan)
                .signedBy(ca)
                .build();

        HandshakeCertificates serverCertificates = new HandshakeCertificates.Builder()
                .heldCertificate(leaf, ca.certificate())
                .build();

        server = new MockWebServer();
        server.useHttps(serverCertificates.sslSocketFactory(), false);
        server.start();
    }

    private static HeldCertificate merchantCa() {
        return new HeldCertificate.Builder()
                .certificateAuthority(0)
                .commonName("merchant-acme-123")
                .build();
    }

    private NexoTerminalAPI sampleRequest() {
        return NexoTerminalAPI.builder()
                .saleToPOIRequest(SaleToPOIRequest.builder()
                        .messageHeader(MessageHeader.builder().build())
                        .build())
                .build();
    }

    @Test
    void acceptsDeviceLeafChainingToTrustedCaWhenSanMatchesPattern() throws Exception {
        HeldCertificate ca = merchantCa();
        startServerWithLeaf(ca, "V240m-ABC123.live.pos.bilt.com");
        server.enqueue(new MockResponse().setBody(OK_RESPONSE));

        BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
                .endpoint(server.url("/nexo").toString())
                .trustCertificate(pemStream(ca))
                .expectedHostnamePattern(PROD_PATTERN)
                .build();

        NexoTerminalAPI response = client.request(sampleRequest());

        assertNotNull(response);
        assertEquals("3.0", response.getSaleToPOIResponse().getMessageHeader().getProtocolVersion());
    }

    @Test
    void acceptsTrustAnchorLoadedFromFile(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        HeldCertificate ca = merchantCa();
        startServerWithLeaf(ca, "P400Plus-789DEF.live.pos.bilt.com");
        server.enqueue(new MockResponse().setBody(OK_RESPONSE));

        File caFile = tempDir.resolve("merchant-ca.pem").toFile();
        Files.write(caFile.toPath(), ca.certificatePem().getBytes(StandardCharsets.UTF_8));

        BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
                .endpoint(server.url("/nexo").toString())
                .trustCertificate(caFile)
                .expectedHostnamePattern(PROD_PATTERN)
                .build();

        assertNotNull(client.request(sampleRequest()));
    }

    @Test
    void rejectsWhenSanDoesNotMatchPattern() throws Exception {
        HeldCertificate ca = merchantCa();
        // Leaf chains to the trusted CA, but its SAN is outside the expected domain.
        startServerWithLeaf(ca, "V240m-ABC123.evil.example.com");

        BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
                .endpoint(server.url("/nexo").toString())
                .trustCertificate(pemStream(ca))
                .expectedHostnamePattern(PROD_PATTERN)
                .build();

        assertThrows(BiltNexoClientException.class, () -> client.request(sampleRequest()));
    }

    @Test
    void rejectsWhenSanHasTooManyLabelsForWildcard() throws Exception {
        HeldCertificate ca = merchantCa();
        // Wildcard matches a single label only; two labels must be rejected.
        startServerWithLeaf(ca, "a.b.live.pos.bilt.com");

        BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
                .endpoint(server.url("/nexo").toString())
                .trustCertificate(pemStream(ca))
                .expectedHostnamePattern(PROD_PATTERN)
                .build();

        assertThrows(BiltNexoClientException.class, () -> client.request(sampleRequest()));
    }

    @Test
    void rejectsWhenLeafDoesNotChainToTrustedCa() throws Exception {
        HeldCertificate serverCa = merchantCa();
        startServerWithLeaf(serverCa, "V240m-ABC123.live.pos.bilt.com");

        // Client trusts a different CA; the SAN matches but the chain does not.
        HeldCertificate otherCa = new HeldCertificate.Builder()
                .certificateAuthority(0)
                .commonName("merchant-globex-456")
                .build();

        BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
                .endpoint(server.url("/nexo").toString())
                .trustCertificate(pemStream(otherCa))
                .expectedHostnamePattern(PROD_PATTERN)
                .build();

        assertThrows(BiltNexoClientException.class, () -> client.request(sampleRequest()));
    }

    @Test
    void stagingPatternRejectsProductionHostname() throws Exception {
        HeldCertificate ca = merchantCa();
        startServerWithLeaf(ca, "V240m-ABC123.live.pos.bilt.com");

        // A client scoped to staging must not accept a production terminal.
        BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
                .endpoint(server.url("/nexo").toString())
                .trustCertificate(pemStream(ca))
                .expectedHostnamePattern("*.staging.pos.bilt.com")
                .build();

        assertThrows(BiltNexoClientException.class, () -> client.request(sampleRequest()));
    }

    @Test
    void environmentConvenienceAppliesProductionPattern() throws Exception {
        HeldCertificate ca = merchantCa();
        startServerWithLeaf(ca, "V240m-ABC123.live.pos.bilt.com");
        server.enqueue(new MockResponse().setBody(OK_RESPONSE));

        BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
                .endpoint(server.url("/nexo").toString())
                .trustCertificate(pemStream(ca))
                .environment(BiltTerminalEnvironment.PRODUCTION)
                .build();

        assertNotNull(client.request(sampleRequest()));
    }

    @Test
    void stagingEnvironmentRejectsProductionTerminal() throws Exception {
        HeldCertificate ca = merchantCa();
        startServerWithLeaf(ca, "V240m-ABC123.live.pos.bilt.com");

        BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
                .endpoint(server.url("/nexo").toString())
                .trustCertificate(pemStream(ca))
                .environment(BiltTerminalEnvironment.STAGING)
                .build();

        assertThrows(BiltNexoClientException.class, () -> client.request(sampleRequest()));
    }

    @Test
    void throwsOnUnparseableCertificate() {
        ByteArrayInputStream garbage =
                new ByteArrayInputStream("not a certificate".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () ->
                BiltNexoTerminalClient.builder().trustCertificate(garbage));
    }

    @Test
    void throwsOnMissingResource() {
        assertThrows(IllegalArgumentException.class, () ->
                BiltNexoTerminalClient.builder().trustCertificateResource("does/not/exist.pem"));
    }

    private static ByteArrayInputStream pemStream(HeldCertificate certificate) {
        return new ByteArrayInputStream(
                certificate.certificatePem().getBytes(StandardCharsets.UTF_8));
    }
}
