package com.bilt.pos.nexo.client

import com.bilt.pos.nexo.model.*
import com.bilt.pos.nexo.security.MessageEncryptor
import com.bilt.pos.nexo.security.SaleToPOISecuredMessage
import com.bilt.pos.nexo.security.SecurityKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class BiltNexoTerminalClientTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private lateinit var server: MockWebServer
    private lateinit var client: BiltNexoTerminalClient

    // Minimal valid response header (all required fields present)
    private val responseHeaderJson = """"MessageHeader":{"ProtocolVersion":"3.0","MessageClass":"Service","MessageCategory":"Payment","MessageType":"Response","POIID":"TERM-1","SaleID":"POS-1","ServiceID":"txn-001"}"""

    // Minimal valid POIData + SaleData blocks required by PaymentResponse
    private val poiDataJson = """"POIData":{"POITransactionID":{"TransactionID":"POI-001","TimeStamp":"2026-04-20T14:30:00Z"}}"""
    private val saleDataJson = """"SaleData":{"SaleTransactionID":{"TransactionID":"TX-001","TimeStamp":"2026-04-20T14:30:00Z"}}"""

    private fun successResponseJson() =
        """{"SaleToPOIResponse":{$responseHeaderJson,"PaymentResponse":{"Response":{"Result":"Success"},$poiDataJson,$saleDataJson}}}"""

    private fun successResponseWithAmountJson(amount: Double) =
        """{"SaleToPOIResponse":{$responseHeaderJson,"PaymentResponse":{"Response":{"Result":"Success"},"PaymentResult":{"PaymentType":"Normal","AmountsResp":{"Currency":"USD","AuthorizedAmount":$amount}},$poiDataJson,$saleDataJson}}}"""

    private fun failureResponseJson(additionalResponse: String = "Insufficient funds") =
        """{"SaleToPOIResponse":{$responseHeaderJson,"PaymentResponse":{"Response":{"Result":"Failure","ErrorCondition":"Refusal","AdditionalResponse":"$additionalResponse"},$poiDataJson,$saleDataJson}}}"""

    private fun minimalResponseJson() =
        """{"SaleToPOIResponse":{$responseHeaderJson}}"""

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = BiltNexoTerminalClient(
            endpoint = server.url("/nexo").toString()
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun header() = MessageHeader(
        protocolVersion = "3.0",
        messageClass = MessageClassType.Service,
        messageCategory = MessageCategoryType.Payment,
        messageType = MessageTypeType.Request,
        serviceID = "txn-001",
        saleID = "POS-1",
        poiid = "TERM-1"
    )

    private fun paymentRequest(amount: Double = 25.0) = NexoTerminalAPI(
        saleToPOIRequest = SaleToPOIRequest(
            messageHeader = header(),
            paymentRequest = PaymentRequest(
                paymentTransaction = PaymentTransaction(
                    amountsReq = AmountsReq(
                        currency = "USD",
                        requestedAmount = amount
                    )
                ),
                saleData = SaleData(
                    saleTransactionID = TransactionIdentificationType(
                        transactionID = "TX-001",
                        timeStamp = "2026-04-20T14:30:00Z"
                    )
                )
            )
        )
    )

    @Test
    fun `request serializes and sends payment`() {
        server.enqueue(MockResponse().setBody(successResponseJson()))

        val response = client.request(paymentRequest())

        response.shouldNotBeNull()
        response.saleToPOIResponse!!.paymentResponse!!.response.result shouldBe ResultType.Success

        val recorded = server.takeRequest()
        recorded.method shouldBe "POST"
        recorded.path shouldBe "/nexo"
        recorded.getHeader("Content-Type")!! shouldContain "application/json"

        val sentJson = recorded.body.readUtf8()
        sentJson shouldContain "\"SaleToPOIRequest\""
        sentJson shouldContain "\"RequestedAmount\":25.0"
        sentJson shouldContain "\"Currency\":\"USD\""
    }

    @Test
    fun `request deserializes full payment response`() {
        server.enqueue(MockResponse().setBody(successResponseWithAmountJson(25.0)))

        val response = client.request(paymentRequest())!!

        val poiResponse = response.saleToPOIResponse!!
        poiResponse.messageHeader.protocolVersion shouldBe "3.0"
        val paymentResp = poiResponse.paymentResponse!!
        paymentResp.response.result shouldBe ResultType.Success
        paymentResp.paymentResult!!.amountsResp!!.authorizedAmount shouldBe 25.0
    }

    @Test
    fun `request handles failure response`() {
        server.enqueue(MockResponse().setBody(failureResponseJson()))

        val response = client.request(paymentRequest())!!

        val failResp = response.saleToPOIResponse!!.paymentResponse!!
        failResp.response.result shouldBe ResultType.Failure
        failResp.response.additionalResponse shouldBe "Insufficient funds"
    }

    @Test
    fun `request throws on HTTP error`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal error"))

        val ex = assertThrows<BiltNexoClientException> {
            client.request(paymentRequest())
        }

        ex.message!! shouldContain "500"
        ex.message!! shouldContain "Internal error"
    }

    @Test
    fun `request throws on connection failure`() {
        server.shutdown()

        val ex = assertThrows<BiltNexoClientException> {
            client.request(paymentRequest())
        }

        ex.message!! shouldContain "Failed to communicate"
    }

    @Test
    fun `request with per-request timeout`() {
        server.enqueue(MockResponse().setBody(minimalResponseJson()))

        val response = client.request(paymentRequest(), Duration.ofSeconds(5))

        response.shouldNotBeNull()
    }

    @Test
    fun `constructor requires endpoint`() {
        assertThrows<IllegalArgumentException> {
            BiltNexoTerminalClient(endpoint = "")
        }
    }

    @Test
    fun `create convenience factory`() {
        val c = BiltNexoTerminalClient.create("192.168.1.100")
        c.isEncrypted shouldBe false
    }

    @Test
    fun `isEncrypted returns false by default`() {
        client.isEncrypted shouldBe false
    }

    @Test
    fun `encrypted client round trip`() {
        val key = SecurityKey(
            passphrase = "testPassphrase",
            keyIdentifier = "testTerminal",
            keyVersion = 0
        )
        val encryptedClient = BiltNexoTerminalClient(
            endpoint = server.url("/nexo").toString(),
            securityKey = key
        )
        encryptedClient.isEncrypted shouldBe true

        // Build an encrypted mock response using MessageEncryptor directly
        val encryptor = MessageEncryptor(key)
        val respHeader = MessageHeader(
            protocolVersion = "3.0",
            messageClass = MessageClassType.Service,
            messageCategory = MessageCategoryType.Payment,
            messageType = MessageTypeType.Response,
            poiid = "TERM-1",
            saleID = "POS-1",
            serviceID = "txn-001"
        )
        val plainResponse = """{"MessageHeader":{"ProtocolVersion":"3.0","MessageClass":"Service","MessageCategory":"Payment","MessageType":"Response","POIID":"TERM-1","SaleID":"POS-1","ServiceID":"txn-001"},"PaymentResponse":{"Response":{"Result":"Success"},"POIData":{"POITransactionID":{"TransactionID":"POI-001","TimeStamp":"2026-04-20T14:30:00Z"}},"SaleData":{"SaleTransactionID":{"TransactionID":"TX-001","TimeStamp":"2026-04-20T14:30:00Z"}}}}"""
        val securedResp = encryptor.encrypt(plainResponse, respHeader)
        val encryptedResponseJson = json.encodeToString(
            TestSecuredResponseEnvelope.serializer(),
            TestSecuredResponseEnvelope(securedResp)
        )

        server.enqueue(MockResponse().setBody(encryptedResponseJson))

        val response = encryptedClient.request(paymentRequest(42.0))

        response.shouldNotBeNull()
        response.saleToPOIResponse!!.paymentResponse!!.response.result shouldBe ResultType.Success

        // Verify the request was sent encrypted
        val recorded = server.takeRequest()
        val sentJson = recorded.body.readUtf8()
        sentJson shouldContain "EnvelopedData"
        sentJson shouldNotContain "RequestedAmount"
    }

    @Test
    fun `encrypted client handles unencrypted error response`() {
        val key = SecurityKey(
            passphrase = "testPassphrase",
            keyIdentifier = "testTerminal",
            keyVersion = 0
        )
        val encryptedClient = BiltNexoTerminalClient(
            endpoint = server.url("/nexo").toString(),
            securityKey = key
        )

        // Server sends back plaintext error (no encryption key configured on terminal)
        server.enqueue(MockResponse().setBody(
            failureResponseJson("Encryption not configured")
        ))

        val response = encryptedClient.request(paymentRequest())

        response.shouldNotBeNull()
        val errResp = response.saleToPOIResponse!!.paymentResponse!!
        errResp.response.result shouldBe ResultType.Failure
        errResp.response.additionalResponse shouldBe "Encryption not configured"
    }

    @Test
    fun `request throws when SaleToPOIRequest is null`() {
        val ex = assertThrows<BiltNexoClientException> {
            client.request(NexoTerminalAPI())
        }

        ex.message!! shouldContain "saleToPOIRequest"
    }

    /** Test helper to serialize the secured response envelope */
    @Serializable
    data class TestSecuredResponseEnvelope(
        @SerialName("SaleToPOIResponse")
        val saleToPOIResponse: SaleToPOISecuredMessage
    )
}
