package com.bilt.pos.emulator

import com.bilt.pos.emulator.session.ConnectionPhase
import com.bilt.pos.emulator.session.EmulatorConfig
import com.bilt.pos.emulator.session.MemberIdentity
import com.bilt.pos.emulator.session.MemberRewardKind
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
import kotlin.test.assertNull
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
 * The Loyalty Sign-In button through the real controller against a scripted terminal, the same
 * MockWebServer rig the refund tests use.
 *
 * The load-bearing fact these pin down is that the terminal only engages its keyed loyalty capture
 * when the request carries `LoyaltyHandling` **and** `ForceEntryMode=["Keyed"]` together — drop
 * either and it arms the card reader and shows no loyalty UI at all, which no compiler catches.
 */
class NexoEmulatorControllerIdentifyTest {

    private companion object {
        const val ADMIN_OK =
            """{"SaleToPOIResponse":{"AdminResponse":{"Response":{"Result":"Success"}}}}"""
        const val DIAGNOSIS_OK =
            """{"SaleToPOIResponse":{"DiagnosisResponse":{"Response":{"Result":"Success"}}}}"""
        const val DISPLAY_OK =
            """{"SaleToPOIResponse":{"DisplayResponse":{
                "OutputResult":[{"Response":{"Result":"Success"}}]}}}"""

        /** The customer dismissed the prompt: Failure + Cancel, carrying no loyalty account. */
        const val CARD_ACQUISITION_CANCELLED =
            """{"SaleToPOIResponse":{"CardAcquisitionResponse":{
                "Response":{"Result":"Failure","ErrorCondition":"Cancel"}}}}"""

        /** No member matched what was keyed — an affirmative answer, unlike a cancel. */
        const val CARD_ACQUISITION_NOT_FOUND =
            """{"SaleToPOIResponse":{"CardAcquisitionResponse":{
                "Response":{"Result":"Failure","ErrorCondition":"NotFound"}}}}"""
    }

    private lateinit var server: MockWebServer
    private val requests = ConcurrentLinkedQueue<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val controllers = mutableListOf<NexoEmulatorController>()
    private val callbackExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "test-ui").apply { isDaemon = true }
    }

    /**
     * The rewards payload rides in `AdditionalResponse` as Base64 JSON, as the terminal sends it.
     */
    private fun cardAcquisitionFound(memberId: String): String {
        val rewards =
            """{"rewards":[{"rewardRef":"rwd:RWD-1","type":"reward","name":"${'$'}10 Off",""" +
                """"expirationDate":"2026-12-31T00:00:00Z"}],"rewardCount":1}"""
        val encoded = Base64.getEncoder().encodeToString(rewards.toByteArray())
        return """{"SaleToPOIResponse":{"CardAcquisitionResponse":{
                "Response":{"Result":"Success","AdditionalResponse":"$encoded"},
                "LoyaltyAccount":[{"LoyaltyAccountID":{"LoyaltyID":"$memberId",
                    "IdentificationType":"PhoneNumber","EntryMode":["Keyed"]}}]}}}"""
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
                        Files.createTempDirectory("identify-e2e").resolve("sales.jsonl").toFile()
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

    private val cardAcquisitions: List<String>
        get() = requests.filter { "\"CardAcquisitionRequest\"" in it }

    @Test
    fun signInAsksForKeyedLoyaltyAndPublishesTheMember() {
        server.dispatcher = respondingWith { body ->
            if ("\"CardAcquisitionRequest\"" in body) {
                cardAcquisitionFound("98234")
            } else {
                defaultResponse(body)
            }
        }
        val controller = controller()
        runBlocking {
            controller.connectAndStartCheckout()
            controller.identifyMember()
            val member =
                withTimeout(10_000) {
                    controller.state.first { it.member != null && !it.identifyInProgress }.member
                }

            // both halves of the terminal's gate, or it reads a card instead
            val request = cardAcquisitions.single()
            assertTrue("\"LoyaltyHandling\":\"Required\"" in request, request)
            assertTrue("\"ForceEntryMode\":[\"Keyed\"]" in request, request)

            val found = assertIsFound(member)
            assertEquals("98234", found.memberId)
            // the prompted response carries no balance, so the card must not
            // claim one — 0 from the SDK means "not reported"
            assertNull(found.pointBalance)
            assertEquals(false, found.retained)
            val reward = found.rewards.single()
            assertEquals("rwd:RWD-1", reward.rewardRef)
            assertEquals(MemberRewardKind.REWARD, reward.kind)
            // rendered in the local zone, so the exact day depends on where
            // the test runs; what matters is the instant was parsed and
            // formatted as a date rather than left as the raw ISO string
            assertTrue(
                reward.expiresAtLabel.orEmpty().matches(Regex("\\w{3} \\d{1,2}, 2026")),
                "unexpected expiry label: ${'$'}{reward.expiresAtLabel}",
            )

            // the claim is released, so the next operation can run
            controller.identifyMember()
            withTimeout(10_000) { controller.state.first { cardAcquisitions.size == 2 } }
        }
    }

    @Test
    fun signInWithoutACheckoutSendsNothing() {
        val controller = controller()
        runBlocking {
            controller.connect("127.0.0.1", encryptionEnabled = false)
            withTimeout(10_000) {
                controller.state.first { it.connection.phase == ConnectionPhase.CONNECTED }
            }
            controller.identifyMember()
            withTimeout(10_000) {
                controller.state.first { state ->
                    state.events.any { "No active checkout session" in it }
                }
            }
            assertTrue(cardAcquisitions.isEmpty(), "no checkout, so nothing may reach the terminal")
            assertNull(controller.state.value.member)
        }
    }

    /**
     * The regression that matters most: the SDK detaches a member only on an affirmative NotFound
     * or Suspended, so a cancelled re-sign-in leaves the earlier one attached and settlement goes
     * on applying loyalty to it. The card has to say so rather than report an empty checkout.
     */
    @Test
    fun cancelledReSignInKeepsTheMemberItDidNotReplace() {
        var cancelNext = false
        server.dispatcher = respondingWith { body ->
            when {
                "\"CardAcquisitionRequest\"" !in body -> defaultResponse(body)
                cancelNext -> CARD_ACQUISITION_CANCELLED
                else -> cardAcquisitionFound("98234")
            }
        }
        val controller = controller()
        runBlocking {
            controller.connectAndStartCheckout()
            controller.identifyMember()
            withTimeout(10_000) {
                controller.state.first {
                    it.member is MemberIdentity.Found && !it.identifyInProgress
                }
            }

            cancelNext = true
            controller.identifyMember()
            val member =
                withTimeout(10_000) {
                    controller.state
                        .first { cardAcquisitions.size == 2 && !it.identifyInProgress }
                        .member
                }
            val found = assertIsFound(member)
            assertEquals("98234", found.memberId)
            assertTrue(found.retained, "a cancelled prompt leaves the prior member attached")
            assertTrue("kept from an earlier sign-in" in found.headline, found.headline)
        }
    }

    /** NotFound is affirmative — the SDK detaches, so the card must report an empty checkout. */
    @Test
    fun notFoundReSignInDetachesTheEarlierMember() {
        var notFoundNext = false
        server.dispatcher = respondingWith { body ->
            when {
                "\"CardAcquisitionRequest\"" !in body -> defaultResponse(body)
                notFoundNext -> CARD_ACQUISITION_NOT_FOUND
                else -> cardAcquisitionFound("98234")
            }
        }
        val controller = controller()
        runBlocking {
            controller.connectAndStartCheckout()
            controller.identifyMember()
            withTimeout(10_000) {
                controller.state.first {
                    it.member is MemberIdentity.Found && !it.identifyInProgress
                }
            }

            notFoundNext = true
            controller.identifyMember()
            val member =
                withTimeout(10_000) {
                    controller.state
                        .first { cardAcquisitions.size == 2 && !it.identifyInProgress }
                        .member
                }
            assertEquals(
                MemberIdentity.Absent(MemberIdentity.Absent.Reason.NOT_FOUND),
                member,
                "NotFound detaches the member, so nothing may be shown as attached",
            )
        }
    }

    private fun assertIsFound(member: MemberIdentity?): MemberIdentity.Found {
        assertTrue(member is MemberIdentity.Found, "expected a found member, got $member")
        return member
    }
}
