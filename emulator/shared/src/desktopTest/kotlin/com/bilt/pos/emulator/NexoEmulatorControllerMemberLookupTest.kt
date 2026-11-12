package com.bilt.pos.emulator

import com.bilt.pos.emulator.session.ConnectionPhase
import com.bilt.pos.emulator.session.EmulatorConfig
import com.bilt.pos.emulator.session.MemberIdentity
import com.bilt.pos.emulator.session.MemberLookupKind
import com.bilt.pos.emulator.session.NexoEmulatorController
import com.bilt.pos.emulator.store.JsonlSaleStore
import java.net.InetAddress
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate

/**
 * The register-supplied member identity through the real controller against a scripted terminal:
 * each kind must reach the terminal as the BalanceInquiry the SDK defines for it.
 */
class NexoEmulatorControllerMemberLookupTest {

    private companion object {
        const val ADMIN_OK =
            """{"SaleToPOIResponse":{"AdminResponse":{"Response":{"Result":"Success"}}}}"""
        const val DIAGNOSIS_OK =
            """{"SaleToPOIResponse":{"DiagnosisResponse":{"Response":{"Result":"Success"}}}}"""
        const val DISPLAY_OK =
            """{"SaleToPOIResponse":{"DisplayResponse":{
                "OutputResult":[{"Response":{"Result":"Success"}}]}}}"""
        const val BALANCE_INQUIRY_NOT_FOUND =
            """{"SaleToPOIResponse":{"BalanceInquiryResponse":{
                "Response":{"Result":"Failure","ErrorCondition":"NotFound"}}}}"""
    }

    private lateinit var server: MockWebServer
    private val requests = ConcurrentLinkedQueue<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val controllers = mutableListOf<NexoEmulatorController>()
    private val callbackExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "test-ui").apply { isDaemon = true }
    }

    private fun balanceInquiryFound(memberId: String): String {
        val rewards =
            """{"rewards":[{"rewardRef":"rwd:RWD-1","type":"reward","name":"${'$'}10 Off"}],""" +
                """"rewardCount":1}"""
        val encoded = Base64.getEncoder().encodeToString(rewards.toByteArray())
        return """{"SaleToPOIResponse":{"BalanceInquiryResponse":{
                "Response":{"Result":"Success","AdditionalResponse":"$encoded"},
                "LoyaltyAccountStatus":{"LoyaltyAccount":{
                    "LoyaltyAccountID":{"LoyaltyID":"$memberId"},
                    "LoyaltyBrand":"K-Club"},"CurrentBalance":1240}}}}"""
    }

    @BeforeTest
    fun startFakeTerminal() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()
        val tls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        server =
            MockWebServer().apply {
                useHttps(tls.sslSocketFactory(), false)
                dispatcher = respondingWith { defaultResponse(it) }
                // the controller's endpoint is fixed: https://<address>:8443/nexo
                start(InetAddress.getByName("127.0.0.1"), 8443)
            }
    }

    @AfterTest
    fun tearDown() {
        controllers.forEach { it.shutdown() }
        scope.cancel()
        callbackExecutor.shutdownNow()
        server.shutdown()
    }

    private fun respondingWith(respond: (String) -> String): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.clone().readUtf8()
                requests.add(body)
                return MockResponse().setBody(respond(body))
            }
        }

    private fun defaultResponse(body: String): String =
        when {
            "\"DiagnosisRequest\"" in body -> DIAGNOSIS_OK
            "\"DisplayRequest\"" in body -> DISPLAY_OK
            else -> ADMIN_OK
        }

    private fun answeringInquiriesWith(reply: String) {
        server.dispatcher = respondingWith { body ->
            if ("\"BalanceInquiryRequest\"" in body) reply else defaultResponse(body)
        }
    }

    private fun controller() =
        NexoEmulatorController(
                scope = scope,
                config =
                    EmulatorConfig(
                        passphrase = null,
                        keyId = "emulator",
                        keyVersion = 0,
                        caPem = null,
                        hostnamePattern = "*",
                    ),
                saleStore =
                    JsonlSaleStore(
                        Files.createTempDirectory("lookup-e2e").resolve("sales.jsonl").toFile()
                    ),
                callbackExecutor = callbackExecutor,
            )
            .also { controllers += it }

    private suspend fun NexoEmulatorController.connectAndStartCheckout() {
        connect("127.0.0.1", encryptionEnabled = false)
        withTimeout(10_000) { state.first { it.connection.phase == ConnectionPhase.CONNECTED } }
        startSession()
        withTimeout(10_000) { state.first { it.sessionId != null } }
    }

    private val balanceInquiries: List<String>
        get() = requests.filter { "\"BalanceInquiryRequest\"" in it }

    /** Looks up [value] as [kind] and returns the BalanceInquiry sent and the member published. */
    private fun lookUp(kind: MemberLookupKind, value: String): Pair<String, MemberIdentity?> {
        val controller = controller()
        return runBlocking {
            controller.connectAndStartCheckout()
            controller.identifyMemberBy(kind, value)
            val member =
                withTimeout(10_000) {
                    controller.state
                        .first {
                            balanceInquiries.size == 1 &&
                                it.member != null &&
                                !it.identifyInProgress
                        }
                        .member
                }
            balanceInquiries.single() to member
        }
    }

    private fun assertLoyaltyAccount(
        request: String,
        identificationType: String,
        entryMode: String,
        loyaltyId: String,
    ) {
        val compact = request.replace(Regex("\\s"), "")
        assertTrue("\"IdentificationType\":\"$identificationType\"" in compact, request)
        assertTrue("\"EntryMode\":[\"$entryMode\"]" in compact, request)
        assertTrue("\"LoyaltyID\":\"$loyaltyId\"" in compact, request)
    }

    private fun assertFound(member: MemberIdentity?, memberId: String) {
        assertTrue(member is MemberIdentity.Found, "expected a found member, got $member")
        assertEquals(memberId, member.memberId)
        assertEquals("K-Club", member.loyaltyBrand)
        assertEquals(1240, member.pointBalance)
        assertEquals("rwd:RWD-1", member.rewards.single().rewardRef)
    }

    @Test
    fun phoneIsAKeyedPhoneNumberLookup() {
        answeringInquiriesWith(balanceInquiryFound("98234"))
        val (request, member) = lookUp(MemberLookupKind.PHONE, " 5558675309 ")
        assertLoyaltyAccount(request, "PhoneNumber", "Keyed", "5558675309")
        assertFound(member, "98234")
    }

    @Test
    fun emailIsAKeyedAccountNumberLookup() {
        answeringInquiriesWith(balanceInquiryFound("98234"))
        val (request, member) = lookUp(MemberLookupKind.EMAIL, "shopper@example.com")
        assertLoyaltyAccount(request, "AccountNumber", "Keyed", "shopper@example.com")
        assertFound(member, "98234")
    }

    @Test
    fun accountNumberIsAKeyedAccountNumberLookup() {
        answeringInquiriesWith(balanceInquiryFound("98234"))
        val (request, member) = lookUp(MemberLookupKind.ACCOUNT_NUMBER, "ACCT-77")
        assertLoyaltyAccount(request, "AccountNumber", "Keyed", "ACCT-77")
        assertFound(member, "98234")
    }

    @Test
    fun aLookupNobodyMatchesLeavesTheCheckoutWithoutAMember() {
        answeringInquiriesWith(BALANCE_INQUIRY_NOT_FOUND)
        val (_, member) = lookUp(MemberLookupKind.EMAIL, "nobody@example.com")
        assertEquals(MemberIdentity.Absent(MemberIdentity.Absent.Reason.NOT_FOUND), member)
    }

    @Test
    fun aResolvedIdAttachesAndIsConfirmedAsAFileAccountNumber() {
        answeringInquiriesWith(balanceInquiryFound("mbr_8f2a"))
        val controller = controller()
        runBlocking {
            controller.connectAndStartCheckout()
            controller.identifyMemberBy(MemberLookupKind.RESOLVED_ID, "mbr_8f2a")
            // the confirmation upgrades the attached member with what the terminal knows
            val member =
                withTimeout(10_000) {
                    controller.state
                        .first { (it.member as? MemberIdentity.Found)?.loyaltyBrand != null }
                        .member
                }
            assertLoyaltyAccount(balanceInquiries.single(), "AccountNumber", "File", "mbr_8f2a")
            assertFound(member, "mbr_8f2a")
            assertTrue(
                controller.state.value.events.any { "Member attached: mbr_8f2a · K-Club" in it }
            )
        }
    }

    @Test
    fun aResolvedIdTheTerminalDoesNotKnowStaysAttachedAndLogsTheDecline() {
        answeringInquiriesWith(BALANCE_INQUIRY_NOT_FOUND)
        val controller = controller()
        runBlocking {
            controller.connectAndStartCheckout()
            controller.identifyMemberBy(MemberLookupKind.RESOLVED_ID, "missing")
            withTimeout(10_000) {
                controller.state.first { state ->
                    state.events.any { "Background operation failed (DECLINED)" in it }
                }
            }
            assertEquals(MemberIdentity.Found(memberId = "missing"), controller.state.value.member)
            assertTrue(
                controller.state.value.events.none { "Customer display update failed" in it }
            )
        }
    }

    @Test
    fun aBlankValueSendsNothing() {
        val controller = controller()
        runBlocking {
            controller.connectAndStartCheckout()
            controller.identifyMemberBy(MemberLookupKind.PHONE, "   ")
            withTimeout(10_000) {
                controller.state.first { state ->
                    state.events.any { "Enter a phone to identify the member" in it }
                }
            }
            assertTrue(balanceInquiries.isEmpty())
            assertEquals(null, controller.state.value.member)
        }
    }
}
