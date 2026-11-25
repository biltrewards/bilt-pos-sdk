package com.bilt.pos.emulator

import com.bilt.pos.emulator.session.ConnectionPhase
import com.bilt.pos.emulator.session.EmulatorConfig
import com.bilt.pos.emulator.session.LoyaltyOptions
import com.bilt.pos.emulator.session.NexoEmulatorController
import com.bilt.pos.emulator.session.PartialApprovalAction
import com.bilt.pos.emulator.store.JsonlSaleStore
import com.bilt.pos.emulator.store.LegType
import com.bilt.pos.emulator.store.SaleItem
import com.bilt.pos.emulator.store.SaleRecord
import com.bilt.pos.emulator.store.TransactionLeg
import java.net.InetAddress
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
 * Bilt Rewards (points) spent inside a terminal payment, through the real controller against a
 * scripted terminal (a TLS MockWebServer on the controller's fixed 127.0.0.1:8443): the rewards leg
 * and the mixed card payment in the partial prompt, outcome, sales list and event log, and a void
 * and an item return of a sale that has a Bilt Rewards payment. The terminal reports the rewards as
 * `biltRewardsAmount` in the response's `AdditionalResponse`; the simulator does so for a card
 * amount whose cents end in 789 (rewards only, 40%) or 321 (mixed, 25% in rewards).
 */
class NexoEmulatorControllerBiltRewardsTest {

    private companion object {
        const val ADMIN_OK =
            """{"SaleToPOIResponse":{"AdminResponse":{"Response":{"Result":"Success"}}}}"""
        const val DIAGNOSIS_OK =
            """{"SaleToPOIResponse":{"DiagnosisResponse":{"Response":{"Result":"Success"}}}}"""
        const val DISPLAY_OK =
            """{"SaleToPOIResponse":{"DisplayResponse":{
                "OutputResult":[{"Response":{"Result":"Success"}}]}}}"""
        const val REFUND_OK =
            """{"SaleToPOIResponse":{"PaymentResponse":{
                "Response":{"Result":"Success"},
                "POIData":{"POITransactionID":{"TransactionID":"POI-REF-1",
                    "TimeStamp":"2026-03-02T16:00:05+00:00"}},
                "PaymentResult":{"AmountsResp":{"Currency":"USD","AuthorizedAmount":2.24},
                    "PaymentAcquirerData":{"ApprovalCode":"REFUND01"}}}}}"""
        const val REVERSAL_OK =
            """{"SaleToPOIResponse":{"ReversalResponse":{
                "Response":{"Result":"Success"},
                "POIData":{"POITransactionID":{"TransactionID":"POI-REV-1",
                    "TimeStamp":"2026-03-02T16:00:05+00:00"}},
                "PaymentReceipt":[{"DocumentQualifier":"CustomerReceipt",
                    "OutputContent":{"OutputFormat":"XHTML",
                        "OutputXHTML":"<receipt><plainTextReceipt>REVERSAL RECEIPT</plainTextReceipt></receipt>"}}]}}}"""

        /** The terminal's points leg: a Partial for the rewards value, with no card in it. */
        fun rewardsPartial(poi: String, rewards: String) =
            """{"SaleToPOIResponse":{"PaymentResponse":{
                "Response":{"Result":"Partial","AdditionalResponse":"biltRewardsAmount=$rewards"},
                "POIData":{"POITransactionID":{"TransactionID":"$poi",
                    "TimeStamp":"2026-03-02T16:10:00+00:00"}},
                "PaymentResult":{"AmountsResp":{"Currency":"USD","AuthorizedAmount":$rewards}}}}}"""

        /**
         * A card payment reply: [result] is Success or Partial; [rewards] is the part Bilt rewards
         * paid, reported in the response's AdditionalResponse.
         */
        fun card(
            poi: String,
            approved: String,
            result: String = "Success",
            n: Int = 1,
            rewards: String? = null,
        ) =
            """{"SaleToPOIResponse":{"PaymentResponse":{
                "Response":{"Result":"$result"${rewards?.let { ""","AdditionalResponse":"biltRewardsAmount=$it"""" } ?: ""}},
                "POIData":{"POITransactionID":{"TransactionID":"$poi",
                    "TimeStamp":"2026-03-02T16:10:0$n+00:00"}},
                "PaymentResult":{"AmountsResp":{"Currency":"USD","AuthorizedAmount":$approved},
                    "PaymentAcquirerData":{"ApprovalCode":"APPR-$n"},
                    "PaymentInstrumentData":{"CardData":{
                        "MaskedPAN":"411111******111$n","PaymentBrand":"Visa"}}},
                "PaymentReceipt":[{"DocumentQualifier":"CustomerReceipt",
                    "OutputContent":{"OutputFormat":"XHTML",
                        "OutputXHTML":"<receipt><plainTextReceipt>RECEIPT $n</plainTextReceipt></receipt>"}}]}}}"""
    }

    private lateinit var server: MockWebServer
    private val requests = ConcurrentLinkedQueue<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val controllers = mutableListOf<NexoEmulatorController>()
    private val callbackExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "test-ui").apply { isDaemon = true }
    }

    @BeforeTest
    fun startFakeTerminal() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()
        val tls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        server =
            MockWebServer().apply {
                useHttps(tls.sslSocketFactory(), false)
                dispatcher = script(emptyList())
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

    /** Answers the n-th card charge with the n-th of [charges]; everything else by request type. */
    private fun script(charges: List<String>): Dispatcher {
        val next = AtomicInteger()
        return object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.clone().readUtf8()
                requests.add(body)
                val reply =
                    when {
                        "\"DiagnosisRequest\"" in body -> DIAGNOSIS_OK
                        "\"DisplayRequest\"" in body -> DISPLAY_OK
                        "\"ReversalRequest\"" in body -> REVERSAL_OK
                        "\"PaymentRequest\"" in body && "Refund" in body -> REFUND_OK
                        "\"PaymentRequest\"" in body -> charges[next.getAndIncrement()]
                        else -> ADMIN_OK
                    }
                return MockResponse().setBody(reply)
            }
        }
    }

    private fun controller(store: JsonlSaleStore = emptyStore()) =
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
                saleStore = store,
                callbackExecutor = callbackExecutor,
            )
            .also { controllers += it }

    private fun emptyStore() =
        JsonlSaleStore(Files.createTempDirectory("rewards-e2e").resolve("sales.jsonl").toFile())

    /**
     * A prior sale paid with Bilt Rewards ($7.16 on poi-r1), a card that rewards paid $3.30 of
     * ($13.21 on poi-c1) and a plain card ($29.63 on poi-c2): $50.00.
     */
    private fun storeWithRewardsSale(): JsonlSaleStore {
        val store = emptyStore()
        store.recordSale(
            SaleRecord(
                id = "sale-rewards",
                sessionId = "session-rewards",
                saleId = "bilt-emulator",
                poiId = "EMULATOR",
                currency = "USD",
                completedAt = "2026-08-06T10:15:30Z",
                items =
                    listOf(
                        SaleItem(
                            sku = "SKU-1",
                            description = "Water",
                            quantity = 2,
                            unitPrice = "1.05",
                            taxRate = "0.06625",
                            lineTotal = "2.10",
                        ),
                        SaleItem(
                            sku = "SKU-2",
                            description = "Gum",
                            quantity = 1,
                            unitPrice = "3.00",
                            lineTotal = "3.00",
                        ),
                    ),
                authorizedAmount = "50.00",
                legs =
                    listOf(
                        TransactionLeg(
                            LegType.BILT_REWARDS,
                            "poi-r1",
                            "2026-08-06T10:15:28Z",
                            "7.16",
                        ),
                        TransactionLeg(
                            LegType.CARD,
                            "poi-c1",
                            "2026-08-06T10:15:29Z",
                            "13.21",
                            brand = "Visa",
                            maskedPan = "411111******1111",
                            biltRewardsAmount = "3.30",
                        ),
                        TransactionLeg(
                            LegType.CARD,
                            "poi-c2",
                            "2026-08-06T10:15:31Z",
                            "29.63",
                            brand = "Visa",
                            maskedPan = "411111******1112",
                        ),
                    ),
            )
        )
        return store
    }

    private fun connectWithBasket(controller: NexoEmulatorController, priceMinor: Long = 10_000) =
        runBlocking {
            controller.connect("127.0.0.1", encryptionEnabled = false)
            withTimeout(10_000) {
                controller.state.first { it.connection.phase == ConnectionPhase.CONNECTED }
            }
            controller.startSession()
            withTimeout(10_000) { controller.state.first { it.sessionId != null } }
            controller.addCustomItem(priceMinor)
            withTimeout(10_000) { controller.state.first { it.basket.isNotEmpty() } }
        }

    private val noLoyalty = LoyaltyOptions(rebates = false, redemption = false, award = false)

    private fun cardCharges() = requests.filter { "\"PaymentRequest\"" in it && "Refund" !in it }

    @Test
    fun aRewardsLegThenACardShowsBilltRewardsNeverACard() {
        server.dispatcher =
            script(listOf(rewardsPartial("POI-R1", "7.16"), card("POI-C1", "10.73", n = 1)))
        val controller = controller()
        connectWithBasket(controller, priceMinor = 1789)
        runBlocking {
            controller.settle(noLoyalty)
            val prompt =
                withTimeout(10_000) {
                    controller.state.first { it.partialApproval != null }.partialApproval!!
                }
            // the points leg is not a card approval
            assertEquals("Bilt Rewards paid $7.16 of $17.89, $10.73 still due", prompt.message)
            assertTrue(prompt.biltRewards)
            assertEquals(
                "Return the Bilt Rewards and reverse every card payment, and stop the sale.",
                prompt.describe(PartialApprovalAction.CANCEL_SALE),
            )
            prompt.choose(PartialApprovalAction.ANOTHER_CARD)

            val outcome =
                withTimeout(10_000) {
                    controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
                }
            assertTrue(outcome.success, outcome.message)
            // rewards next to the card, the totals apart, and the rewards leg is not Card 1
            assertTrue("Bilt Rewards $7.16" in outcome.message, outcome.message)
            assertTrue("card $10.73 (Visa)" in outcome.message, outcome.message)
            assertTrue("Bilt Rewards total $7.16" in outcome.message, outcome.message)
            assertTrue("Card 1 $7.16" !in outcome.message, outcome.message)

            val events = controller.state.value.events
            assertTrue(events.any { "Partial approval: Bilt Rewards paid $7.16 of $17.89" in it })
            assertTrue(events.any { "Bilt Rewards payment charged: $7.16" in it })
            // the card payments are numbered without the rewards leg
            assertTrue(events.any { "Card payment 1 charged: $10.73" in it })

            val sale =
                withTimeout(10_000) { controller.state.first { it.sales.isNotEmpty() } }
                    .sales
                    .single()
            assertEquals(listOf("7.16"), sale.biltRewardsPayments.map { it.amount })
            assertEquals(listOf("POI-R1"), sale.biltRewardsPayments.map { it.poiTransactionId })
            assertEquals(listOf("POI-C1"), sale.cardPayments.map { it.poiTransactionId })
            assertEquals(
                listOf("Bilt Rewards $7.16", "Card 1 $10.73 · Visa ····1111 · approval APPR-1"),
                sale.paymentLines,
            )
        }
    }

    @Test
    fun aMixedCardPaymentShowsItsRewardsPart() {
        server.dispatcher = script(listOf(card("POI-C1", "13.21", n = 1, rewards = "3.30")))
        val controller = controller()
        connectWithBasket(controller, priceMinor = 1321)
        runBlocking {
            controller.settle(noLoyalty)
            val outcome =
                withTimeout(10_000) {
                    controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
                }
            assertTrue(outcome.success, outcome.message)
            assertTrue("card $9.91 (Visa) + Bilt Rewards $3.30" in outcome.message, outcome.message)
            assertTrue(
                "Card 1 $13.21 (card $9.91 + Bilt Rewards $3.30) · Visa ····1111" in
                    outcome.message,
                outcome.message,
            )
            assertTrue("Bilt Rewards total $3.30" in outcome.message, outcome.message)

            val sale =
                withTimeout(10_000) { controller.state.first { it.sales.isNotEmpty() } }
                    .sales
                    .single()
            // the part is a card's, not a payment of its own
            assertTrue(sale.biltRewardsPayments.isEmpty())
            val card = sale.cardPayments.single()
            assertEquals("3.30", card.biltRewardsAmount)
            assertEquals("9.91", card.cardAmount)
        }
    }

    @Test
    fun aRewardsOnlySaleHasNoCardLeg() {
        // multi-tender off: the rewards covered the sale whole, as one Success with no card
        server.dispatcher =
            script(listOf(rewardsPartial("POI-R1", "17.89").replace("Partial", "Success")))
        val controller = controller()
        connectWithBasket(controller, priceMinor = 1789)
        runBlocking {
            controller.settle(noLoyalty)
            val outcome =
                withTimeout(10_000) {
                    controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
                }
            assertTrue(outcome.success, outcome.message)
            assertTrue("Bilt Rewards $17.89" in outcome.message, outcome.message)
            assertTrue(
                "card" !in outcome.message.lowercase().replace("cardholder", ""),
                outcome.message,
            )
            val sale =
                withTimeout(10_000) { controller.state.first { it.sales.isNotEmpty() } }
                    .sales
                    .single()
            assertEquals(listOf("17.89"), sale.biltRewardsPayments.map { it.amount })
            assertTrue(sale.cardPayments.isEmpty())
        }
    }

    @Test
    fun theSalesListShowsBilltRewardsAndAVoidReversesEachPaymentByItsReference() {
        val store = storeWithRewardsSale()
        val controller = controller(store)
        runBlocking {
            val sale =
                withTimeout(10_000) { controller.state.first { it.sales.isNotEmpty() } }
                    .sales
                    .single()
            assertEquals(
                listOf(
                    "Bilt Rewards $7.16",
                    "Card 1 $13.21 (card $9.91 + Bilt Rewards $3.30) · Visa ····1111",
                    "Card 2 $29.63 · Visa ····1112",
                ),
                sale.paymentLines,
            )
            controller.connect("127.0.0.1", encryptionEnabled = false)
            withTimeout(10_000) {
                controller.state.first { it.connection.phase == ConnectionPhase.CONNECTED }
            }
            controller.refundSale("sale-rewards")
            val outcome =
                withTimeout(10_000) {
                    controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
                }
            assertTrue(outcome.success, outcome.message)
            assertTrue("Bilt Rewards reversed $7.16" in outcome.message, outcome.message)
            assertTrue(
                "Card 1 reversed $13.21 (incl. Bilt Rewards $3.30)" in outcome.message,
                outcome.message,
            )
            // the rewards payment is not a card: the cards are numbered without it
            assertTrue("Card 3" !in outcome.message, outcome.message)
            assertTrue("Card 2 reversed $29.63" in outcome.message, outcome.message)

            // every payment is reversed by its own reference: the rewards payment as well
            val reversals = requests.filter { "\"ReversalRequest\"" in it }
            assertEquals(3, reversals.size)
            assertTrue(
                listOf("poi-c2", "poi-c1", "poi-r1").all { id -> reversals.any { id in it } }
            )
            assertTrue(assertNotNull(store.findSale("sale-rewards")).fullyRefunded)

            val after =
                withTimeout(10_000) {
                        controller.state.first { it.sales.singleOrNull()?.fullyRefunded == true }
                    }
                    .sales
                    .single()
            assertTrue(after.biltRewardsPayments.all { it.refunded && !it.returnable })
            assertTrue(after.cardPayments.all { it.refunded && !it.returnable })
        }
    }

    @Test
    fun anItemReturnGoesBackToTheBilltRewardsPaymentThePickNames() {
        val store = storeWithRewardsSale()
        val controller = controller(store)
        runBlocking {
            controller.connect("127.0.0.1", encryptionEnabled = false)
            withTimeout(10_000) {
                controller.state.first { it.connection.phase == ConnectionPhase.CONNECTED }
            }
            controller.startSession()
            withTimeout(10_000) { controller.state.first { it.sessionId != null } }
            controller.addReturnToBasket("sale-rewards", setOf("SKU-2"), "poi-r1")
            withTimeout(10_000) {
                controller.state.first { s -> s.basket.count { it.credit } == 1 }
            }
            withTimeout(10_000) { controller.state.first { !it.refundInProgress } }
            controller.settle(noLoyalty)
            val outcome =
                withTimeout(10_000) {
                    controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
                }
            assertTrue(outcome.success, outcome.message)
            // a linked refund of the rewards payment's reference, labelled Bilt Rewards
            val refunds = requests.filter { "\"PaymentRequest\"" in it && "Refund" in it }
            assertEquals(1, refunds.size, refunds.toString())
            assertTrue("poi-r1" in refunds[0], refunds[0])
            assertTrue(
                controller.state.value.events.any { "to the Bilt Rewards" in it },
                controller.state.value.events.toString(),
            )
            val sale =
                withTimeout(10_000) {
                        controller.state.first {
                            it.sales
                                .singleOrNull()
                                ?.biltRewardsPayments
                                ?.singleOrNull()
                                ?.remainingAmount != "7.16"
                        }
                    }
                    .sales
                    .single()
            assertEquals("4.16", sale.biltRewardsPayments.single().remainingAmount)
            assertEquals("29.63", sale.cardPayments[1].remainingAmount)
        }
    }

    @Test
    fun anUnknownTenderPickKeepsTheReturnOutOfTheBasket() {
        val controller = controller(storeWithRewardsSale())
        runBlocking {
            controller.connect("127.0.0.1", encryptionEnabled = false)
            withTimeout(10_000) {
                controller.state.first { it.connection.phase == ConnectionPhase.CONNECTED }
            }
            controller.startSession()
            withTimeout(10_000) { controller.state.first { it.sessionId != null } }
            controller.addReturnToBasket("sale-rewards", setOf("SKU-2"), "nope")
            withTimeout(10_000) {
                controller.state.first {
                    it.events.any { e -> "is not on this sale or was already refunded" in e }
                }
            }
            assertTrue(controller.state.value.basket.isEmpty())
        }
    }
}
