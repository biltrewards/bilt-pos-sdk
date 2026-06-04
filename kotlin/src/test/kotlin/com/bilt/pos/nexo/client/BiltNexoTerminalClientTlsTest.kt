package com.bilt.pos.nexo.client

import com.bilt.pos.nexo.model.MessageCategoryType
import com.bilt.pos.nexo.model.MessageClassType
import com.bilt.pos.nexo.model.MessageHeader
import com.bilt.pos.nexo.model.MessageTypeType
import com.bilt.pos.nexo.model.NexoTerminalAPI
import com.bilt.pos.nexo.model.SaleToPOIRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies CA-anchored TLS verification with synthetic-hostname matching
 * against a real HTTPS handshake served by [MockWebServer].
 *
 * The server mirrors a Bilt terminal: it presents a device *leaf* certificate
 * (SAN `{Model}-{Serial}.live.pos.bilt.com`) signed by a merchant CA, while
 * the client connects by IP/loopback and trusts the CA.
 */
class BiltNexoTerminalClientTlsTest {

    private companion object {
        const val OK_RESPONSE =
            """{"SaleToPOIResponse":{"MessageHeader":{"ProtocolVersion":"3.0","MessageClass":"Service","MessageCategory":"Payment","MessageType":"Response","POIID":"TERM-1","SaleID":"POS-1","ServiceID":"txn-001"}}}"""
        const val PROD_PATTERN = "*.live.pos.bilt.com"
    }

    private var server: MockWebServer? = null

    @AfterEach
    fun tearDown() {
        server?.shutdown()
    }

    private fun merchantCa(): HeldCertificate =
        HeldCertificate.Builder()
            .certificateAuthority(0)
            .commonName("test-merchant-ca")
            .build()

    /** Start an HTTPS server presenting [leafSan] on a leaf certificate signed by [ca]. */
    private fun startServerWithLeaf(ca: HeldCertificate, leafSan: String): MockWebServer {
        val leaf = HeldCertificate.Builder()
            .commonName(leafSan)
            .addSubjectAlternativeName(leafSan)
            .signedBy(ca)
            .build()

        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(leaf, ca.certificate)
            .build()

        return MockWebServer().also {
            it.useHttps(serverCertificates.sslSocketFactory(), false)
            it.start()
            server = it
        }
    }

    private fun sampleRequest() = NexoTerminalAPI(
        saleToPOIRequest = SaleToPOIRequest(
            messageHeader = MessageHeader(
                protocolVersion = "3.0",
                messageClass = MessageClassType.Service,
                messageCategory = MessageCategoryType.Payment,
                messageType = MessageTypeType.Request,
                serviceID = "txn-001",
                saleID = "POS-1",
                poiid = "TERM-1"
            )
        )
    )

    @Test
    fun `accepts device leaf chaining to trusted CA when SAN matches pattern`() {
        val ca = merchantCa()
        val srv = startServerWithLeaf(ca, "V240m-ABC123.live.pos.bilt.com")
        srv.enqueue(MockResponse().setBody(OK_RESPONSE))

        val client = BiltNexoTerminalClient(
            endpoint = srv.url("/nexo").toString(),
            trustedCertificates = BiltNexoTerminalClient.certificatesFromPem(ca.certificatePem()),
            expectedHostnamePattern = PROD_PATTERN
        )

        val response = client.request(sampleRequest())

        response.shouldNotBeNull()
        response.saleToPOIResponse!!.messageHeader.protocolVersion shouldBe "3.0"
    }

    @Test
    fun `accepts trust anchor loaded from file via environment`(@TempDir tempDir: Path) {
        val ca = merchantCa()
        val srv = startServerWithLeaf(ca, "P400Plus-789DEF.live.pos.bilt.com")
        srv.enqueue(MockResponse().setBody(OK_RESPONSE))

        val caFile = tempDir.resolve("merchant-ca.pem")
        Files.write(caFile, ca.certificatePem().toByteArray())

        val client = BiltNexoTerminalClient(
            endpoint = srv.url("/nexo").toString(),
            trustedCertificates = BiltNexoTerminalClient.certificatesFromPath(caFile),
            environment = BiltTerminalEnvironment.PRODUCTION
        )

        client.request(sampleRequest()).shouldNotBeNull()
    }

    @Test
    fun `rejects when SAN does not match pattern`() {
        val ca = merchantCa()
        val srv = startServerWithLeaf(ca, "V240m-ABC123.evil.example.com")

        val client = BiltNexoTerminalClient(
            endpoint = srv.url("/nexo").toString(),
            trustedCertificates = BiltNexoTerminalClient.certificatesFromPem(ca.certificatePem()),
            expectedHostnamePattern = PROD_PATTERN
        )

        shouldThrow<BiltNexoClientException> { client.request(sampleRequest()) }
    }

    @Test
    fun `rejects when SAN has too many labels for wildcard`() {
        val ca = merchantCa()
        val srv = startServerWithLeaf(ca, "a.b.live.pos.bilt.com")

        val client = BiltNexoTerminalClient(
            endpoint = srv.url("/nexo").toString(),
            trustedCertificates = BiltNexoTerminalClient.certificatesFromPem(ca.certificatePem()),
            expectedHostnamePattern = PROD_PATTERN
        )

        shouldThrow<BiltNexoClientException> { client.request(sampleRequest()) }
    }

    @Test
    fun `rejects when leaf does not chain to trusted CA`() {
        val serverCa = merchantCa()
        val srv = startServerWithLeaf(serverCa, "V240m-ABC123.live.pos.bilt.com")

        val otherCa = HeldCertificate.Builder()
            .certificateAuthority(0)
            .commonName("other-test-ca")
            .build()

        val client = BiltNexoTerminalClient(
            endpoint = srv.url("/nexo").toString(),
            trustedCertificates = BiltNexoTerminalClient.certificatesFromPem(otherCa.certificatePem()),
            expectedHostnamePattern = PROD_PATTERN
        )

        shouldThrow<BiltNexoClientException> { client.request(sampleRequest()) }
    }

    @Test
    fun `staging environment rejects production terminal`() {
        val ca = merchantCa()
        val srv = startServerWithLeaf(ca, "V240m-ABC123.live.pos.bilt.com")

        val client = BiltNexoTerminalClient(
            endpoint = srv.url("/nexo").toString(),
            trustedCertificates = BiltNexoTerminalClient.certificatesFromPem(ca.certificatePem()),
            environment = BiltTerminalEnvironment.STAGING
        )

        shouldThrow<BiltNexoClientException> { client.request(sampleRequest()) }
    }

    @Test
    fun `construction fails when trust anchor set without hostname pattern`() {
        val ca = merchantCa()

        val ex = shouldThrow<IllegalArgumentException> {
            BiltNexoTerminalClient(
                endpoint = "https://192.168.1.50:8443/nexo",
                trustedCertificates = BiltNexoTerminalClient.certificatesFromPem(ca.certificatePem())
            )
        }
        ex.message.shouldNotBeNull() shouldContain "expectedHostnamePattern"
    }

    @Test
    fun `construction fails when trustAll combined with trust anchor`() {
        val ca = merchantCa()

        val ex = shouldThrow<IllegalArgumentException> {
            BiltNexoTerminalClient(
                endpoint = "https://192.168.1.50:8443/nexo",
                trustAllCertificates = true,
                trustedCertificates = BiltNexoTerminalClient.certificatesFromPem(ca.certificatePem()),
                expectedHostnamePattern = PROD_PATTERN
            )
        }
        ex.message.shouldNotBeNull() shouldContain "trustAllCertificates"
    }

    @Test
    fun `throws on unparseable certificate`() {
        shouldThrow<IllegalArgumentException> {
            BiltNexoTerminalClient.certificatesFromPem("not a certificate")
        }
    }

    @Test
    fun `throws on missing resource`() {
        shouldThrow<IllegalArgumentException> {
            BiltNexoTerminalClient.certificatesFromResource("does/not/exist.pem")
        }
    }
}
