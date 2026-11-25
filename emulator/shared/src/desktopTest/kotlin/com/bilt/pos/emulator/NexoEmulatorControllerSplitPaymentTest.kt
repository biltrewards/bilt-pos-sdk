package com.bilt.pos.emulator

import com.bilt.pos.emulator.session.ConnectionPhase
import com.bilt.pos.emulator.session.EmulatorConfig
import com.bilt.pos.emulator.session.LoyaltyOptions
import com.bilt.pos.emulator.session.NexoEmulatorController
import com.bilt.pos.emulator.session.PartialApprovalAction
import com.bilt.pos.emulator.session.PaymentRecoveryAction
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
 * Split and partial card payments through the real controller against a scripted terminal (a TLS
 * MockWebServer on the controller's fixed 127.0.0.1:8443): the partial approval dialog and each of
 * its decisions, planned card amounts, the SDK's automatic follow-ups when the dialog is off, the
 * plan the SDK refuses, and refunds and voids of a sale with several card payments.
 */
class NexoEmulatorControllerSplitPaymentTest {

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

        /** A card payment reply: [result] is Success or Partial. */
        fun card(poi: String, approved: String, result: String = "Success", n: Int = 1) =
            """{"SaleToPOIResponse":{"PaymentResponse":{
                "Response":{"Result":"$result"},
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
        JsonlSaleStore(Files.createTempDirectory("split-e2e").resolve("sales.jsonl").toFile())

    /** A prior sale paid with two cards: $30.00 on poi-c1 and $20.00 on poi-c2. */
    private fun storeWithSplitSale(): JsonlSaleStore {
        val store = emptyStore()
        store.recordSale(
            SaleRecord(
                id = "sale-split",
                sessionId = "session-split",
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
                            LegType.CARD,
                            "poi-c1",
                            "2026-08-06T10:15:29Z",
                            "30.00",
                            brand = "Visa",
                            maskedPan = "411111******1111",
                            partial = true,
                        ),
                        TransactionLeg(
                            LegType.CARD,
                            "poi-c2",
                            "2026-08-06T10:15:31Z",
                            "20.00",
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
    fun anotherCardCompletesAPartialSaleAndStoresBothPayments() {
        server.dispatcher =
            script(listOf(card("POI-C1", "60.00", "Partial", 1), card("POI-C2", "40.00", n = 2)))
        val controller = controller()
        connectWithBasket(controller)
        runBlocking {
            controller.settle(noLoyalty)
            val prompt =
                withTimeout(10_000) {
                    controller.state.first { it.partialApproval != null }.partialApproval!!
                }
            assertEquals("Card approved $60.00 of $100.00, $40.00 still due", prompt.message)
            // no planned payment is left, so adding to the next card is not offered
            assertEquals(
                listOf(
                    PartialApprovalAction.ANOTHER_CARD,
                    PartialApprovalAction.CASH,
                    PartialApprovalAction.CANCEL_SALE,
                ),
                prompt.actions,
            )
            assertEquals(
                "Another card ($40.00)",
                prompt.caption(PartialApprovalAction.ANOTHER_CARD),
            )
            assertEquals("Rest in cash ($40.00)", prompt.caption(PartialApprovalAction.CASH))
            prompt.choose(PartialApprovalAction.ANOTHER_CARD)

            val outcome =
                withTimeout(10_000) {
                    controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
                }
            assertTrue(outcome.success, outcome.message)
            assertTrue(
                "Card 1 $60.00 · Visa ····1111 · approval APPR-1 · partial" in outcome.message,
                outcome.message,
            )
            assertTrue(
                "Card 2 $40.00 · Visa ····1112 · approval APPR-2" in outcome.message &&
                    "approval APPR-2 · partial" !in outcome.message,
                outcome.message,
            )
            assertEquals(null, outcome.receipt)
            assertEquals(
                listOf("Card 1 receipt" to "RECEIPT 1", "Card 2 receipt" to "RECEIPT 2"),
                outcome.receipts.map { it.label to it.text },
            )

            // the second charge is a nexo split payment of the shortfall
            val charges = cardCharges()
            assertEquals(2, charges.size)
            assertTrue("\"SplitPaymentFlag\"" !in charges[0], charges[0])
            assertTrue("\"SplitPaymentFlag\":true" in charges[1], charges[1])
            assertTrue("\"PaidAmount\":60.0" in charges[1], charges[1])
            assertTrue("\"RequestedAmount\":40.0" in charges[1], charges[1])

            // the decision and each card payment reach the event log
            val events = controller.state.value.events
            assertTrue(events.any { "Partial approval: card approved $60.00 of $100.00" in it })
            assertTrue(events.any { "Cashier chose Another card ($40.00)" in it })
            assertTrue(events.any { "Card payment 1 charged: $60.00" in it })
            assertTrue(events.any { "Card payment 2 charged: $40.00" in it })

            // the sale keeps both card payments, each with the reference its refund needs
            val sale =
                withTimeout(10_000) { controller.state.first { it.sales.isNotEmpty() } }
                    .sales
                    .single()
            assertEquals(
                listOf("poi-c1" to true, "poi-c2" to false).map { it.second },
                sale.cardPayments.map { it.partial },
            )
            assertEquals(listOf("POI-C1", "POI-C2"), sale.cardPayments.map { it.poiTransactionId })
            assertEquals(listOf("60.00", "40.00"), sale.cardPayments.map { it.amount })
            assertEquals(listOf("1111", "1112"), sale.cardPayments.map { it.lastDigits })
        }
    }

    @Test
    fun cancelSaleReversesEveryCardPayment() {
        server.dispatcher = script(listOf(card("POI-C1", "60.00", "Partial", 1)))
        val controller = controller()
        connectWithBasket(controller)
        runBlocking {
            controller.settle(noLoyalty)
            val prompt =
                withTimeout(10_000) {
                    controller.state.first { it.partialApproval != null }.partialApproval!!
                }
            prompt.choose(PartialApprovalAction.CANCEL_SALE)
            val outcome =
                withTimeout(10_000) {
                    controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
                }
            assertEquals("Sale cancelled", outcome.title)
            assertTrue(!outcome.success)
            assertEquals(
                1,
                requests.count { "\"ReversalRequest\"" in it && "POI-C1" in it },
                "the partial approval must be reversed",
            )
            assertEquals(
                "Every card payment was reversed and the sale stopped. The basket is intact.",
                outcome.message,
            )
            // the SDK's reversal notice reaches the event log
            assertTrue(
                controller.state.value.events.any {
                    "Card payment reversed: $60.00" in it && "reverses txn POI-C1" in it
                },
                controller.state.value.events.toString(),
            )
            withTimeout(10_000) { controller.state.first { !it.paymentInProgress } }
            assertTrue(controller.state.value.sales.isEmpty())
            assertEquals(null, controller.state.value.partialApproval)
        }
    }

    @Test
    fun restInCashKeepsTheCardPaymentAndRecordsTheCash() {
        server.dispatcher = script(listOf(card("POI-C1", "60.00", "Partial", 1)))
        val controller = controller()
        connectWithBasket(controller)
        runBlocking {
            controller.settle(noLoyalty)
            val prompt =
                withTimeout(10_000) {
                    controller.state.first { it.partialApproval != null }.partialApproval!!
                }
            prompt.choose(PartialApprovalAction.CASH)
            val outcome =
                withTimeout(10_000) {
                    controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
                }
            assertTrue(outcome.success, outcome.message)
            assertTrue("cash $40.00" in outcome.message, outcome.message)
            assertEquals(1, cardCharges().size)
            assertTrue(requests.none { "\"ReversalRequest\"" in it })
        }
    }

    @Test
    fun plannedCardAmountsSplitTheChargeAndAnAddToNextDecisionIsOffered() {
        // $100.00 as $30.00 then the rest; the first card approves only $20.00 of its $30.00
        server.dispatcher =
            script(
                listOf(
                    card("POI-C1", "20.00", "Partial", 1),
                    card("POI-C2", "80.00", n = 2),
                )
            )
        val controller = controller()
        connectWithBasket(controller)
        runBlocking {
            controller.settle(noLoyalty, cardPayments = listOf("30.00"))
            val prompt =
                withTimeout(10_000) {
                    controller.state.first { it.partialApproval != null }.partialApproval!!
                }
            assertEquals("Card approved $20.00 of $30.00, $80.00 still due", prompt.message)
            assertEquals(
                PartialApprovalAction.entries,
                prompt.actions,
                "a planned payment is left, so Add to next card is offered",
            )
            assertEquals(
                "Charge the $10.00 shortfall on another card, then continue the sale.",
                prompt.describe(PartialApprovalAction.ANOTHER_CARD),
            )
            prompt.choose(PartialApprovalAction.ADD_TO_NEXT)
            val outcome =
                withTimeout(10_000) {
                    controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
                }
            assertTrue(outcome.success, outcome.message)
            val charges = cardCharges()
            assertTrue("\"RequestedAmount\":30.0" in charges[0], charges[0])
            // the shortfall rides on the next card: $70.00 planned remainder + $10.00
            assertTrue("\"RequestedAmount\":80.0" in charges[1], charges[1])
            assertTrue("\"PaidAmount\":20.0" in charges[1], charges[1])
        }
    }

    @Test
    fun withoutPartialApprovalsTheSdkFollowsUpOnItsOwn() {
        server.dispatcher =
            script(listOf(card("POI-C1", "60.00", "Partial", 1), card("POI-C2", "40.00", n = 2)))
        val controller = controller()
        connectWithBasket(controller)
        runBlocking {
            controller.settle(noLoyalty, partialApprovals = false)
            val outcome =
                withTimeout(10_000) {
                    controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
                }
            assertTrue(outcome.success, outcome.message)
            assertEquals(2, cardCharges().size)
            assertEquals(null, controller.state.value.partialApproval)
            assertTrue(controller.state.value.events.none { "Partial approval:" in it })
        }
    }

    @Test
    fun withoutPartialApprovalsThreePartialsEndInTheRecoveryDialog() {
        server.dispatcher =
            script(
                listOf(
                    card("POI-C1", "60.00", "Partial", 1),
                    card("POI-C2", "24.00", "Partial", 2),
                    card("POI-C3", "9.60", "Partial", 3),
                )
            )
        val controller = controller()
        connectWithBasket(controller)
        runBlocking {
            controller.settle(noLoyalty, partialApprovals = false)
            val prompt =
                withTimeout(10_000) {
                    controller.state.first { it.paymentRecovery != null }.paymentRecovery!!
                }
            assertTrue("DECLINED" in prompt.message, prompt.message)
            assertEquals(3, cardCharges().size, "3 card requests in total")
            assertEquals(null, controller.state.value.partialApproval)
            prompt.choose(PaymentRecoveryAction.ABORT)
            withTimeout(10_000) {
                controller.state.first { it.paymentOutcome != null && !it.paymentInProgress }
            }
            assertEquals(3, requests.count { "\"ReversalRequest\"" in it })
        }
    }

    @Test
    fun aPlanReachingTheTotalOffersOnlyAbortAndAbandon() {
        val controller = controller()
        connectWithBasket(controller)
        runBlocking {
            controller.settle(noLoyalty, cardPayments = listOf("100.00"))
            val prompt =
                withTimeout(10_000) {
                    controller.state.first { it.paymentRecovery != null }.paymentRecovery!!
                }
            assertTrue("CARD_PAYMENTS_EXCEED_TOTAL" in prompt.message, prompt.message)
            assertEquals(
                listOf(PaymentRecoveryAction.ABORT, PaymentRecoveryAction.ABANDON),
                prompt.actions,
            )
            assertTrue(cardCharges().isEmpty(), "no card may be charged for a refused plan")
            prompt.choose(PaymentRecoveryAction.ABORT)
            withTimeout(10_000) { controller.state.first { it.paymentOutcome != null } }
        }
    }

    @Test
    fun aNonPositivePlannedAmountDoesNotStartTheSettlement() {
        val controller = controller()
        connectWithBasket(controller)
        runBlocking {
            controller.settle(noLoyalty, cardPayments = listOf("0.00"))
            controller.settle(noLoyalty, cardPayments = listOf("abc"))
            assertTrue(
                controller.state.value.events.count { "Settlement not started" in it } == 2,
                controller.state.value.events.toString(),
            )
            assertTrue(!controller.state.value.paymentInProgress)
            assertTrue(cardCharges().isEmpty())
            // the claim was not taken: a valid settlement still runs
            server.dispatcher = script(listOf(card("POI-C1", "100.00", n = 1)))
            controller.settle(noLoyalty)
            val outcome =
                withTimeout(10_000) {
                    controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
                }
            assertTrue(outcome.success, outcome.message)
        }
    }

    @Test
    fun theSalesListShowsEveryCardPaymentAndAFullRefundVoidsThemNewestFirst() {
        val store = storeWithSplitSale()
        val controller = controller(store)
        runBlocking {
            val sale =
                withTimeout(10_000) { controller.state.first { it.sales.isNotEmpty() } }
                    .sales
                    .single()
            assertEquals(
                listOf(
                    "Card 1 $30.00 · Visa ····1111 · partial",
                    "Card 2 $20.00 · Visa ····1112",
                ),
                sale.cardPayments.map { it.line },
            )
            controller.connect("127.0.0.1", encryptionEnabled = false)
            withTimeout(10_000) {
                controller.state.first { it.connection.phase == ConnectionPhase.CONNECTED }
            }
            controller.refundSale("sale-split")
            val outcome =
                withTimeout(10_000) {
                    controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
                }
            assertTrue(outcome.success, outcome.message)
            // every card payment's reversal is listed, with its own receipt
            assertTrue("Card 2 reversed" in outcome.message, outcome.message)
            assertTrue("Card 1 reversed" in outcome.message, outcome.message)
            assertEquals(null, outcome.receipt)
            assertEquals(
                listOf("Card 2 reversal receipt", "Card 1 reversal receipt"),
                outcome.receipts.map { it.label },
            )
            assertTrue(outcome.receipts.all { it.text == "REVERSAL RECEIPT" })
            val reversals = requests.filter { "\"ReversalRequest\"" in it }
            assertEquals(2, reversals.size)
            assertTrue("poi-c2" in reversals[0] && "poi-c1" in reversals[1], reversals.toString())
            assertTrue(assertNotNull(store.findSale("sale-split")).fullyRefunded)

            // the sales list shows every card payment as returned, with nothing left to give back
            val after =
                withTimeout(10_000) {
                        controller.state.first { it.sales.singleOrNull()?.fullyRefunded == true }
                    }
                    .sales
                    .single()
            assertTrue(after.cardPayments.all { it.refunded && !it.returnable })
            assertEquals(listOf("0.00", "0.00"), after.cardPayments.map { it.remainingAmount })
        }
    }

    @Test
    fun anItemReturnGoesBackToTheCardTheCashierPicked() {
        val store = storeWithSplitSale()
        val controller = controller(store)
        runBlocking {
            controller.connect("127.0.0.1", encryptionEnabled = false)
            withTimeout(10_000) {
                controller.state.first { it.connection.phase == ConnectionPhase.CONNECTED }
            }
            controller.startSession()
            withTimeout(10_000) { controller.state.first { it.sessionId != null } }
            controller.addReturnToBasket(
                "sale-split",
                setOf("SKU-1"),
                tenderPoiTransactionId = "poi-c2",
            )
            withTimeout(10_000) { controller.state.first { s -> s.basket.any { it.credit } } }
            withTimeout(10_000) { controller.state.first { !it.refundInProgress } }
            controller.settle(noLoyalty)
            val outcome =
                withTimeout(10_000) {
                    controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
                }
            assertTrue(outcome.success, outcome.message)
            assertTrue("to the card 2" in outcome.message, outcome.message)

            val refund = requests.single { "\"PaymentRequest\"" in it && "Refund" in it }
            assertTrue("poi-c2" in refund && "poi-c1" !in refund, refund)
            val record = assertNotNull(store.findSale("sale-split")).refunds.single()
            assertEquals("poi-c2", record.legPoiTransactionId)

            // only card 2 drew on the refund: card 1 still has everything to give
            val sale =
                withTimeout(10_000) {
                        controller.state.first { s ->
                            s.sales.singleOrNull()?.cardPayments?.get(1)?.remainingAmount == "17.76"
                        }
                    }
                    .sales
                    .single()
            assertEquals("30.00", sale.cardPayments[0].remainingAmount)
            assertTrue(sale.cardPayments.none { it.refunded })
        }
    }

    @Test
    fun anUnknownCardPickKeepsTheReturnOutOfTheBasket() {
        val controller = controller(storeWithSplitSale())
        runBlocking {
            controller.connect("127.0.0.1", encryptionEnabled = false)
            withTimeout(10_000) {
                controller.state.first { it.connection.phase == ConnectionPhase.CONNECTED }
            }
            controller.startSession()
            withTimeout(10_000) { controller.state.first { it.sessionId != null } }
            controller.addReturnToBasket(
                "sale-split",
                setOf("SKU-1"),
                tenderPoiTransactionId = "nope",
            )
            withTimeout(10_000) {
                controller.state.first { s ->
                    s.events.any { "chosen card or Bilt Rewards payment (nope)" in it }
                }
            }
            assertTrue(controller.state.value.basket.isEmpty())
        }
    }

    @Test
    fun returnsFromOneSaleGoBackToTheDifferentCardsTheyWerePickedFor() {
        val store = storeWithSplitSale()
        val controller = controller(store)
        runBlocking {
            controller.connect("127.0.0.1", encryptionEnabled = false)
            withTimeout(10_000) {
                controller.state.first { it.connection.phase == ConnectionPhase.CONNECTED }
            }
            controller.startSession()
            withTimeout(10_000) { controller.state.first { it.sessionId != null } }
            controller.addReturnToBasket(
                "sale-split",
                setOf("SKU-1"),
                tenderPoiTransactionId = "poi-c1",
            )
            withTimeout(10_000) {
                controller.state.first { s -> s.basket.count { it.credit } == 1 }
            }
            withTimeout(10_000) { controller.state.first { !it.refundInProgress } }
            controller.addReturnToBasket(
                "sale-split",
                setOf("SKU-2"),
                tenderPoiTransactionId = "poi-c2",
            )
            withTimeout(10_000) {
                controller.state.first { s -> s.basket.count { it.credit } == 2 }
            }
            withTimeout(10_000) { controller.state.first { !it.refundInProgress } }

            controller.settle(noLoyalty)
            val outcome =
                withTimeout(10_000) {
                    controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
                }
            assertTrue(outcome.success, outcome.message)

            // one refund per card, each with the amount of the items picked for it
            val refunds = requests.filter { "\"PaymentRequest\"" in it && "Refund" in it }
            assertEquals(2, refunds.size, refunds.toString())
            val toCard1 = refunds.single { "poi-c1" in it }
            val toCard2 = refunds.single { "poi-c2" in it }
            assertTrue("\"RequestedAmount\":2.24" in toCard1, toCard1)
            assertTrue("\"RequestedAmount\":3.0" in toCard2, toCard2)
            val recorded =
                assertNotNull(store.findSale("sale-split")).refunds.associate {
                    it.legPoiTransactionId to it.amount
                }
            assertEquals(mapOf<String?, String?>("poi-c1" to "2.24", "poi-c2" to "3.00"), recorded)
        }
    }

    @Test
    fun cancelSaleWarnsWhenAReversalFailsAndTheChargeStaysStanding() {
        val failedReversal =
            """{"SaleToPOIResponse":{"ReversalResponse":{
                "Response":{"Result":"Failure","ErrorCondition":"UnavailableService"}}}}"""
        val charges = script(listOf(card("POI-C1", "60.00", "Partial", 1)))
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.clone().readUtf8()
                    return if ("\"ReversalRequest\"" in body) {
                        requests.add(body)
                        MockResponse().setBody(failedReversal)
                    } else {
                        charges.dispatch(request)
                    }
                }
            }
        val controller = controller()
        connectWithBasket(controller)
        runBlocking {
            controller.settle(noLoyalty)
            val prompt =
                withTimeout(10_000) {
                    controller.state.first { it.partialApproval != null }.partialApproval!!
                }
            prompt.choose(PartialApprovalAction.CANCEL_SALE)
            val outcome =
                withTimeout(10_000) {
                    controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
                }
            assertEquals("Sale cancelled", outcome.title)
            assertTrue(!outcome.success)
            assertTrue(outcome.message.startsWith("WARNING"), outcome.message)
            // the charge no reversal notice took off the list is named, with its terminal reference
            assertTrue("card payment $60.00 (txn POI-C1)" in outcome.message, outcome.message)
            assertTrue("Every card payment was reversed" !in outcome.message, outcome.message)
            assertTrue(
                controller.state.value.events.any { "Still standing: card payment $60.00" in it },
                controller.state.value.events.toString(),
            )
            assertTrue(controller.state.value.events.none { "Card payment reversed" in it })
        }
    }

    @Test
    fun aCancelledSaleKeepsThePaymentBusyUntilItsOutcomeAndNeverOverwritesALaterOne() {
        server.dispatcher =
            script(listOf(card("POI-C1", "60.00", "Partial", 1), card("POI-C2", "100.00", n = 2)))
        val controller = controller()
        connectWithBasket(controller)
        runBlocking {
            controller.settle(noLoyalty)
            val prompt =
                withTimeout(10_000) {
                    controller.state.first { it.partialApproval != null }.partialApproval!!
                }
            prompt.choose(PartialApprovalAction.CANCEL_SALE)
            // the outcome and the release of the payment arrive together
            val cancelled =
                withTimeout(10_000) {
                    controller.state.first { it.paymentOutcome != null && !it.paymentInProgress }
                }
            assertEquals("Sale cancelled", cancelled.paymentOutcome!!.title)
            assertTrue(
                cancelled.events.none { "committed steps reversed" in it },
                "the cancellation must not be logged as already reversed: ${cancelled.events}",
            )

            controller.dismissPaymentOutcome()
            controller.settle(noLoyalty)
            val settled =
                withTimeout(10_000) {
                    controller.state.first { it.paymentOutcome?.success == true }.paymentOutcome!!
                }
            assertEquals("Settlement complete", settled.title)
            kotlinx.coroutines.delay(300)
            assertEquals("Settlement complete", controller.state.value.paymentOutcome?.title)
        }
    }

    @Test
    fun plannedCardPaymentsWithImplicitLoyaltyAreRefusedBeforeAnythingStarts() {
        val controller = controller()
        connectWithBasket(controller)
        runBlocking {
            controller.settle(
                LoyaltyOptions(rebates = false, redemption = false, award = false, implicit = true),
                cardPayments = listOf("30.00"),
            )
            val state = controller.state.value
            assertTrue(
                state.events.any { "cannot use implicit loyalty" in it },
                state.events.toString(),
            )
            assertTrue(!state.paymentInProgress)
            assertTrue(cardCharges().isEmpty())
            // nothing was claimed: an ordinary settlement still runs
            server.dispatcher = script(listOf(card("POI-C1", "100.00", n = 1)))
            controller.settle(noLoyalty)
            val outcome =
                withTimeout(10_000) {
                    controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
                }
            assertTrue(outcome.success, outcome.message)
        }
    }

    @Test
    fun partialApprovalsWorkWithImplicitLoyalty() {
        server.dispatcher =
            script(listOf(card("POI-C1", "60.00", "Partial", 1), card("POI-C2", "40.00", n = 2)))
        val controller = controller()
        connectWithBasket(controller)
        runBlocking {
            controller.settle(
                LoyaltyOptions(rebates = false, redemption = false, award = false, implicit = true)
            )
            val prompt =
                withTimeout(10_000) {
                    controller.state.first { it.partialApproval != null }.partialApproval!!
                }
            prompt.choose(PartialApprovalAction.ANOTHER_CARD)
            val outcome =
                withTimeout(10_000) {
                    controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
                }
            assertTrue(outcome.success, outcome.message)
            assertEquals(2, cardCharges().size)
        }
    }

    @Test
    fun aCardWhoseAmountItemRefundsAlreadyDrewIsNotOfferedAsAReturnTarget() {
        val store = storeWithSplitSale()
        // earlier item returns took all $30.00 card 1 collected, without refunding it in full
        store.recordRefund(
            "sale-split",
            com.bilt.pos.emulator.store.RefundRecord(
                amount = "30.00",
                tenderAmount = "30.00",
                leg = LegType.CARD,
                legPoiTransactionId = "poi-c1",
                recordedAt = "2026-09-01T01:00:00Z",
                items = listOf(com.bilt.pos.emulator.store.RefundedItem("SKU-2", 1)),
            ),
        )
        val controller = controller(store)
        runBlocking {
            val sale =
                withTimeout(10_000) { controller.state.first { it.sales.isNotEmpty() } }
                    .sales
                    .single()
            assertEquals(listOf(true, false), sale.cardPayments.map { it.depleted })
            assertEquals(listOf(false, true), sale.cardPayments.map { it.returnable })
            assertTrue(sale.cardPayments[0].line.endsWith("refunded"), sale.cardPayments[0].line)

            controller.connect("127.0.0.1", encryptionEnabled = false)
            withTimeout(10_000) {
                controller.state.first { it.connection.phase == ConnectionPhase.CONNECTED }
            }
            controller.startSession()
            withTimeout(10_000) { controller.state.first { it.sessionId != null } }
            // no card picked: the return goes to the card that still has room, not the first one
            controller.addReturnToBasket("sale-split", setOf("SKU-1"))
            withTimeout(10_000) { controller.state.first { s -> s.basket.any { it.credit } } }
            withTimeout(10_000) { controller.state.first { !it.refundInProgress } }
            controller.settle(noLoyalty)
            val outcome =
                withTimeout(10_000) {
                    controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
                }
            assertTrue(outcome.success, outcome.message)
            val refund = requests.single { "\"PaymentRequest\"" in it && "Refund" in it }
            assertTrue("poi-c2" in refund && "poi-c1" !in refund, refund)
            assertTrue("register-paid" !in outcome.message, outcome.message)
        }
    }

    @Test
    fun returnsAlreadyInTheBasketUseUpACardsCapacityForTheNextReturn() {
        val store = emptyStore()
        val base = storeWithSplitSale().findSale("sale-split")!!.sale
        // card 1 collected exactly what the SKU-1 return is worth
        store.recordSale(
            base.copy(
                legs =
                    listOf(
                        base.legs[0].copy(amount = "2.24"),
                        base.legs[1].copy(amount = "47.76"),
                    )
            )
        )
        val controller = controller(store)
        runBlocking {
            controller.connect("127.0.0.1", encryptionEnabled = false)
            withTimeout(10_000) {
                controller.state.first { it.connection.phase == ConnectionPhase.CONNECTED }
            }
            controller.startSession()
            withTimeout(10_000) { controller.state.first { it.sessionId != null } }
            controller.addReturnToBasket("sale-split", setOf("SKU-1"))
            withTimeout(10_000) {
                controller.state.first { s -> s.basket.count { it.credit } == 1 }
            }
            withTimeout(10_000) { controller.state.first { !it.refundInProgress } }
            // the pending return has taken card 1's whole capacity: the picker no longer offers it
            val afterFirst =
                withTimeout(10_000) {
                    controller.state.first { s ->
                        s.sales.singleOrNull()?.cardPayments?.get(0)?.depleted == true
                    }
                }
            assertEquals(
                listOf(false, true),
                afterFirst.sales.single().cardPayments.map { it.returnable },
            )

            // no pick: the second return goes to card 2, not on to the exhausted card 1
            controller.addReturnToBasket("sale-split", setOf("SKU-2"))
            withTimeout(10_000) {
                controller.state.first { s -> s.basket.count { it.credit } == 2 }
            }
            withTimeout(10_000) { controller.state.first { !it.refundInProgress } }
            controller.settle(noLoyalty)
            val outcome =
                withTimeout(10_000) {
                    controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
                }
            assertTrue(outcome.success, outcome.message)
            assertTrue("register-paid" !in outcome.message, outcome.message)
            val refunds = requests.filter { "\"PaymentRequest\"" in it && "Refund" in it }
            assertEquals(2, refunds.size, refunds.toString())
            assertTrue("\"RequestedAmount\":2.24" in refunds.single { "poi-c1" in it })
            assertTrue("\"RequestedAmount\":3.0" in refunds.single { "poi-c2" in it })
        }
    }

    @Test
    fun anAbortedSettlementNamesTheChargesItsRollbackLeftStanding() {
        val failedReversal =
            """{"SaleToPOIResponse":{"ReversalResponse":{
                "Response":{"Result":"Failure","ErrorCondition":"UnavailableService"}}}}"""
        val refusal =
            """{"SaleToPOIResponse":{"PaymentResponse":{
                "Response":{"Result":"Failure","ErrorCondition":"Refusal"}}}}"""
        val charges = script(listOf(card("POI-C1", "60.00", "Partial", 1), refusal))
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.clone().readUtf8()
                    return if ("\"ReversalRequest\"" in body) {
                        requests.add(body)
                        MockResponse().setBody(failedReversal)
                    } else {
                        charges.dispatch(request)
                    }
                }
            }
        val controller = controller()
        connectWithBasket(controller)
        runBlocking {
            // no handler: the SDK follows up on its own, then the recovery dialog decides
            controller.settle(noLoyalty, partialApprovals = false, cardPayments = emptyList())
            // the follow-up card request is refused, so the step fails and the recovery dialog
            // opens
            val prompt =
                withTimeout(15_000) {
                    controller.state.first { it.paymentRecovery != null }.paymentRecovery!!
                }
            prompt.choose(PaymentRecoveryAction.ABORT)
            val outcome =
                withTimeout(15_000) {
                    controller.state
                        .first {
                            it.paymentOutcome != null && !it.paymentInProgress
                        }
                        .paymentOutcome!!
                }
            assertTrue(!outcome.success)
            assertTrue("WARNING: the rollback was incomplete" in outcome.message, outcome.message)
            assertTrue("card payment $60.00 (txn POI-C1)" in outcome.message, outcome.message)
        }
    }

    @Test
    fun aVoidedSplitSaleOffersNoCardAsAReturnTarget() {
        val store = storeWithSplitSale()
        store.recordVoid(
            "sale-split",
            com.bilt.pos.emulator.store.VoidRecord(recordedAt = "2026-09-01T01:00:00Z"),
        )
        val controller = controller(store)
        runBlocking {
            val sale =
                withTimeout(10_000) { controller.state.first { it.sales.isNotEmpty() } }
                    .sales
                    .single()
            assertTrue(sale.voided)
            assertTrue(sale.cardPayments.all { it.refunded && !it.returnable })
            assertEquals(listOf("0.00", "0.00"), sale.cardPayments.map { it.remainingAmount })
        }
    }
}
