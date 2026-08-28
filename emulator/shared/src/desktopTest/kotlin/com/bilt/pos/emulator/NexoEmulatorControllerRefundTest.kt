package com.bilt.pos.emulator

import com.bilt.pos.emulator.catalog.Product
import com.bilt.pos.emulator.session.ConnectionPhase
import com.bilt.pos.emulator.session.EmulatorConfig
import com.bilt.pos.emulator.session.LoyaltyOptions
import com.bilt.pos.emulator.session.NexoEmulatorController
import com.bilt.pos.emulator.store.JsonlSaleStore
import com.bilt.pos.emulator.store.LegType
import com.bilt.pos.emulator.store.RefundRecord
import com.bilt.pos.emulator.store.RefundedItem
import com.bilt.pos.emulator.store.SaleItem
import com.bilt.pos.emulator.store.SaleStore
import com.bilt.pos.emulator.store.StoredSale
import com.bilt.pos.emulator.store.VoidRecord
import com.bilt.pos.emulator.store.SaleRecord
import com.bilt.pos.emulator.store.TransactionLeg
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
import java.net.InetAddress
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end refund through the real controller against a scripted terminal:
 * a TLS MockWebServer bound to 127.0.0.1:8443 — the controller's fixed
 * endpoint — answering the diagnosis probe, the reversal session brackets,
 * and the refund verbs by request type. Covers the whole chain the Refund
 * button drives: stored-sale lookup, ReversalSession construction from the
 * persisted references, the refund itself, the store record, and the state
 * the UI renders (outcome popup, refunded badge, released busy flag).
 */
class NexoEmulatorControllerRefundTest {

    private companion object {
        const val ADMIN_OK =
            """{"SaleToPOIResponse":{"AdminResponse":{"Response":{"Result":"Success"}}}}"""
        const val DIAGNOSIS_OK =
            """{"SaleToPOIResponse":{"DiagnosisResponse":{"Response":{"Result":"Success"}}}}"""
        const val LOYALTY_OK =
            """{"SaleToPOIResponse":{"LoyaltyResponse":{"Response":{"Result":"Success"}}}}"""
        /** A raw (un-Base64ed) receipt payload — some terminals send the
         *  receipt XML like this; it must parse without a warning. */
        const val RAW_RECEIPT_XML =
            "<receipt><plainTextReceipt>REFUND RECEIPT</plainTextReceipt></receipt>"

        const val REFUND_OK =
            """{"SaleToPOIResponse":{"PaymentResponse":{
                "Response":{"Result":"Success"},
                "POIData":{"POITransactionID":{"TransactionID":"POI-REF-100",
                    "TimeStamp":"2026-03-02T16:00:05+00:00"}},
                "PaymentResult":{"AmountsResp":{"Currency":"USD","AuthorizedAmount":24.99},
                    "PaymentAcquirerData":{"ApprovalCode":"APPR01"}}}}}"""

        const val PAYMENT_OK =
            """{"SaleToPOIResponse":{"PaymentResponse":{
                "Response":{"Result":"Success"},
                "POIData":{"POITransactionID":{"TransactionID":"POI-NEW-1",
                    "TimeStamp":"2026-03-02T16:10:00+00:00"}},
                "PaymentResult":{"AmountsResp":{"Currency":"USD","AuthorizedAmount":34.99},
                    "PaymentAcquirerData":{"ApprovalCode":"APPR02"}}}}}"""

        const val REVERSAL_FAIL =
            """{"SaleToPOIResponse":{"ReversalResponse":{
                "Response":{"Result":"Failure","ErrorCondition":"UnavailableService"}}}}"""

        const val DISPLAY_OK =
            """{"SaleToPOIResponse":{"DisplayResponse":{
                "OutputResult":[{"Response":{"Result":"Success"}}]}}}"""

        /** The void path's reply; carries a raw-XML customer receipt (must
         *  parse silently) and a garbage cashier receipt (must warn). */
        const val REVERSAL_OK =
            """{"SaleToPOIResponse":{"ReversalResponse":{
                "Response":{"Result":"Success"},
                "POIData":{"POITransactionID":{"TransactionID":"POI-REV-100",
                    "TimeStamp":"2026-03-02T16:00:05+00:00"}},
                "PaymentReceipt":[
                    {"DocumentQualifier":"CustomerReceipt",
                        "OutputContent":{"OutputFormat":"XHTML","OutputXHTML":"$RAW_RECEIPT_XML"}},
                    {"DocumentQualifier":"CashierReceipt",
                        "OutputContent":{"OutputFormat":"XHTML","OutputXHTML":"!!!not-a-receipt"}}]}}}"""
    }

    private lateinit var server: MockWebServer
    private val requests = ConcurrentLinkedQueue<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val callbackExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "test-ui").apply { isDaemon = true }
    }

    @BeforeTest
    fun startFakeTerminal() {
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("127.0.0.1")
            .build()
        val tls = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build()
        server = MockWebServer().apply {
            useHttps(tls.sslSocketFactory(), false)
            dispatcher = respondingWith(::defaultResponse)
            // the controller's endpoint is fixed: https://<address>:8443/nexo
            start(InetAddress.getByName("127.0.0.1"), 8443)
        }
    }

    /** A dispatcher recording every request and answering via [respond];
     *  tests swap in a wrapper to inject failures. */
    private fun respondingWith(respond: (String) -> String): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.clone().readUtf8()
                requests.add(body)
                return MockResponse().setBody(respond(body))
            }
        }

    private fun defaultResponse(body: String): String = when {
        "\"DiagnosisRequest\"" in body -> DIAGNOSIS_OK
        "\"AdminRequest\"" in body -> ADMIN_OK
        "\"DisplayRequest\"" in body -> DISPLAY_OK
        "\"LoyaltyRequest\"" in body -> LOYALTY_OK
        "\"ReversalRequest\"" in body -> REVERSAL_OK
        // the refund leg of a settlement carries the Refund payment type;
        // a charge does not
        "\"PaymentRequest\"" in body && "Refund" in body -> REFUND_OK
        "\"PaymentRequest\"" in body -> PAYMENT_OK
        else -> ADMIN_OK
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        callbackExecutor.shutdownNow()
        server.shutdown()
    }

    private fun storeWithOneSale(): JsonlSaleStore {
        val store = JsonlSaleStore(
            Files.createTempDirectory("refund-e2e").resolve("sales.jsonl").toFile()
        )
        store.recordSale(
            SaleRecord(
                id = "sale-1",
                sessionId = "session-1",
                saleId = "bilt-emulator",
                poiId = "EMULATOR",
                currency = "USD",
                completedAt = "2026-08-06T10:15:30Z",
                memberId = "member-42",
                items = listOf(
                    SaleItem(
                        sku = "SKU-1",
                        description = "Water",
                        quantity = 2,
                        unitPrice = "1.05",
                        taxRate = "0.06625",
                        lineTotal = "2.10",
                    )
                ),
                authorizedAmount = "2.10",
                legs = listOf(
                    TransactionLeg(LegType.CARD, "poi-card-1", "2026-08-06T10:15:29Z"),
                    TransactionLeg(LegType.AWARD, "poi-award-1"),
                ),
            )
        )
        return store
    }

    /** A store whose lookup blocks until released — pins an abort into the
     *  window where the refund has nothing on the wire yet. */
    private class LatchedLookupStore(private val delegate: SaleStore) : SaleStore {
        val lookupEntered = java.util.concurrent.CountDownLatch(1)
        val lookupRelease = java.util.concurrent.CountDownLatch(1)
        override fun recordSale(sale: SaleRecord) = delegate.recordSale(sale)
        override fun recordRefund(saleId: String, refund: RefundRecord) =
            delegate.recordRefund(saleId, refund)
        override fun recordVoid(saleId: String, voidRecord: VoidRecord) =
            delegate.recordVoid(saleId, voidRecord)
        override fun findSale(saleId: String): StoredSale? {
            lookupEntered.countDown()
            lookupRelease.await()
            return delegate.findSale(saleId)
        }
        override fun listSales(limit: Int): List<StoredSale> = delegate.listSales(limit)
    }

    /** A store whose refund write always fails — the disk-full case the
     *  outcome popup must not stay quiet about. */
    private class RefundWriteFailingStore(private val delegate: SaleStore) : SaleStore {
        override fun recordSale(sale: SaleRecord) = delegate.recordSale(sale)
        override fun recordRefund(saleId: String, refund: RefundRecord): Unit =
            throw IllegalStateException("disk full")
        override fun recordVoid(saleId: String, voidRecord: VoidRecord) =
            delegate.recordVoid(saleId, voidRecord)
        override fun findSale(saleId: String): StoredSale? = delegate.findSale(saleId)
        override fun listSales(limit: Int): List<StoredSale> = delegate.listSales(limit)
    }

    private fun controller(store: SaleStore) = NexoEmulatorController(
        scope = scope,
        config = EmulatorConfig(
            passphrase = null,
            keyId = "emulator",
            keyVersion = 0,
            caPem = null,
            hostnamePattern = "*",
        ),
        saleStore = store,
        callbackExecutor = callbackExecutor,
    )

    @Test
    fun fullRefundRunsAgainstTheTerminalAndRecordsIntoTheStore() {
        val store = storeWithOneSale()
        val controller = controller(store)
        runBlocking {
            controller.connect("127.0.0.1", encryptionEnabled = false)
            withTimeout(10_000) {
                controller.state.first { it.connection.phase == ConnectionPhase.CONNECTED }
            }

            controller.refundSale("sale-1")
            val outcome = withTimeout(10_000) {
                controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
            }

            assertTrue(outcome.success, "expected a successful refund, got: ${outcome.message}")
            assertEquals("Refund complete", outcome.title)
            // the terminal's customer receipt (sent raw, not base64) lands
            // on the outcome popup
            assertEquals("REFUND RECEIPT", outcome.receipt)

            // Receipt handling, through the SDK's JUL logs: the garbage
            // cashier receipt warns — once, as the raw-XML customer receipt
            // parses silently — and the warning surfaces on the curated
            // Events feed (one line) with the full record on Detailed
            val state = controller.state.value
            assertEquals(
                1, state.events.count { "unparsable receipt payload" in it },
                "expected exactly the cashier-receipt warning on the events feed; " +
                    "events tail: ${state.events.takeLast(8)}",
            )
            assertTrue(
                state.detailedEvents.any {
                    "unparsable receipt payload" in it && "IllegalArgumentException" in it
                },
                "expected the warning's stack trace on the detailed log",
            )

            // the refund landed in the store as a legless full record — the
            // void exhausted the whole sale, award included
            val refund = assertNotNull(store.findSale("sale-1")).refunds.single()
            assertTrue(refund.full, "a full refund must be recorded as full")
            assertEquals(null, refund.leg)
            assertTrue(
                refund.awardReversed,
                "the committed award reversal must be recorded",
            )
            assertTrue(assertNotNull(store.findSale("sale-1")).fullyRefunded)

            // the sales list refreshed, so the UI shows the refunded badge,
            // and the busy flag released
            withTimeout(10_000) {
                controller.state.first { it.sales.singleOrNull()?.refunded == true }
                controller.state.first { !it.refundInProgress }
            }

            // the wire saw the void of the stored card leg, plus the award
            // reversal carrying its own reference
            val reversal = assertNotNull(
                requests.firstOrNull { "\"ReversalRequest\"" in it },
                "no ReversalRequest reached the terminal",
            )
            assertTrue("poi-card-1" in reversal, "original card reference missing: $reversal")
            val loyalty = assertNotNull(
                requests.firstOrNull { "\"LoyaltyRequest\"" in it },
                "no award reversal reached the terminal",
            )
            assertTrue("poi-award-1" in loyalty, "award reference missing: $loyalty")

            // a second attempt is refused before the wire — the sale was
            // refunded in full. Wait out the first attempt's claim first,
            // like the UI does via the disabled button.
            withTimeout(10_000) { controller.state.first { !it.refundInProgress } }
            val reversalsBefore = requests.count { "\"ReversalRequest\"" in it }
            controller.refundSale("sale-1")
            withTimeout(10_000) {
                controller.state.first { s ->
                    s.events.any { "already refunded in full" in it }
                }
            }
            assertEquals(reversalsBefore, requests.count { "\"ReversalRequest\"" in it })
            assertEquals(1, assertNotNull(store.findSale("sale-1")).refunds.size)
        }
    }

    @Test
    fun abortBeforeAnythingIsOnTheWireStopsTheRefund() {
        val store = LatchedLookupStore(storeWithOneSale())
        val controller = controller(store)
        runBlocking {
            controller.connect("127.0.0.1", encryptionEnabled = false)
            withTimeout(10_000) {
                controller.state.first { it.connection.phase == ConnectionPhase.CONNECTED }
            }

            controller.refundSale("sale-1")
            // the refund is now parked in the store lookup: nothing on the
            // wire, no reversal session yet — the flag is the only brake
            assertTrue(store.lookupEntered.await(10, java.util.concurrent.TimeUnit.SECONDS))
            controller.abort()
            store.lookupRelease.countDown()

            val outcome = withTimeout(10_000) {
                controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
            }
            assertEquals("Refund aborted", outcome.title)
            assertTrue("before any money moved" in outcome.message, outcome.message)
            // no refund reached the terminal and nothing was recorded
            assertTrue(requests.none { "\"PaymentRequest\"" in it })
            assertTrue(assertNotNull(store.findSale("sale-1")).refunds.isEmpty())
        }
    }

    @Test
    fun abortDuringTheReturnRingKeepsTheBasketUntouched() {
        val store = LatchedLookupStore(storeWithOneSale())
        val controller = controller(store)
        runBlocking {
            controller.connect("127.0.0.1", encryptionEnabled = false)
            withTimeout(10_000) {
                controller.state.first { it.connection.phase == ConnectionPhase.CONNECTED }
            }
            controller.startSession()
            withTimeout(10_000) { controller.state.first { it.sessionId != null } }

            controller.addReturnToBasket("sale-1", setOf("SKU-1"))
            // the ring is parked in the store lookup: nothing on the wire,
            // nothing in the basket yet — the abort flag is the only brake
            assertTrue(store.lookupEntered.await(10, java.util.concurrent.TimeUnit.SECONDS))
            controller.abort()
            store.lookupRelease.countDown()

            withTimeout(10_000) {
                controller.state.first { s ->
                    s.events.any { "Return aborted — nothing was rung into the basket" in it }
                }
            }
            withTimeout(10_000) { controller.state.first { !it.refundInProgress } }
            assertTrue(controller.state.value.basket.isEmpty(), "the return still landed")
            // the sale's remaining quantity is untouched
            assertEquals(
                2,
                controller.state.value.sales.single().items.single().remainingQuantity,
            )
        }
    }

    @Test
    fun failedRefundRecordWarnsOnTheOutcome() {
        val store = storeWithOneSale()
        val controller = controller(RefundWriteFailingStore(store))
        runBlocking {
            controller.connect("127.0.0.1", encryptionEnabled = false)
            withTimeout(10_000) {
                controller.state.first { it.connection.phase == ConnectionPhase.CONNECTED }
            }

            controller.refundSale("sale-1")
            val outcome = withTimeout(10_000) {
                controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
            }

            // the money moved, so the outcome stays a success — but it must
            // warn that the unrecorded refund will be offered again
            assertTrue(outcome.success, "the refund itself succeeded: ${outcome.message}")
            assertTrue(
                "could NOT be recorded" in outcome.message,
                "expected the unrecorded-refund warning: ${outcome.message}",
            )
            assertTrue(assertNotNull(store.findSale("sale-1")).refunds.isEmpty())
        }
    }

    @Test
    fun itemRefundDrawsFromTheOutstandingLegWhenTheCardLegWasReturned() {
        val store = JsonlSaleStore(
            Files.createTempDirectory("refund-e2e").resolve("sales.jsonl").toFile()
        )
        store.recordSale(
            SaleRecord(
                id = "sale-3",
                sessionId = "session-3",
                saleId = "bilt-emulator",
                poiId = "EMULATOR",
                currency = "USD",
                completedAt = "2026-08-06T12:00:00Z",
                items = listOf(
                    SaleItem(
                        sku = "SKU-1",
                        description = "Water",
                        quantity = 2,
                        unitPrice = "1.05",
                        lineTotal = "2.10",
                    )
                ),
                authorizedAmount = "30.00",
                legs = listOf(
                    TransactionLeg(LegType.CARD, "poi-card-3", amount = "20.00"),
                    TransactionLeg(LegType.STORED_VALUE, "poi-sv-3", amount = "10.00"),
                    TransactionLeg(LegType.AWARD, "poi-award-3"),
                ),
            )
        )
        // the state a split-tender full refund leaves behind when the card
        // leg succeeded (reversing the award with it) and the gift card leg
        // failed
        store.recordRefund(
            "sale-3",
            RefundRecord(
                recordedAt = "2026-08-06T12:30:00Z",
                full = true,
                leg = LegType.CARD,
                awardReversed = true,
            ),
        )
        val controller = controller(store)
        runBlocking {
            controller.connect("127.0.0.1", encryptionEnabled = false)
            withTimeout(10_000) {
                controller.state.first { it.connection.phase == ConnectionPhase.CONNECTED }
            }

            controller.startSession()
            withTimeout(10_000) { controller.state.first { it.sessionId != null } }
            controller.addReturnToBasket("sale-3", setOf("SKU-1"))
            withTimeout(10_000) { controller.state.first { s -> s.basket.any { it.credit } } }
            withTimeout(10_000) { controller.state.first { !it.refundInProgress } }
            controller.settle(LoyaltyOptions(rebates = false, redemption = false, award = false))
            val outcome = withTimeout(10_000) {
                controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
            }
            assertTrue(outcome.success, "expected a successful refund, got: ${outcome.message}")

            // the return must not touch the already-returned card
            // transaction — it restores to the outstanding gift card leg
            val payment = assertNotNull(
                requests.firstOrNull { "\"PaymentRequest\"" in it && "Refund" in it },
                "no refund PaymentRequest reached the terminal",
            )
            assertTrue("poi-sv-3" in payment, "expected the stored value reference: $payment")
            assertTrue("poi-card-3" !in payment, "the returned card leg was referenced: $payment")
            assertEquals(
                LegType.STORED_VALUE,
                assertNotNull(store.findSale("sale-3")).refunds.last().leg,
            )

            // loyalty is off and returns never touch the award
            assertTrue(
                requests.none { "\"LoyaltyRequest\"" in it },
                "the already-reversed award was sent for reversal again",
            )
        }
    }

    @Test
    fun splitTenderFullRefundReturnsEveryTenderLeg() {
        val store = JsonlSaleStore(
            Files.createTempDirectory("refund-e2e").resolve("sales.jsonl").toFile()
        )
        store.recordSale(
            SaleRecord(
                id = "sale-2",
                sessionId = "session-2",
                saleId = "bilt-emulator",
                poiId = "EMULATOR",
                currency = "USD",
                completedAt = "2026-08-06T11:00:00Z",
                authorizedAmount = "30.00",
                legs = listOf(
                    TransactionLeg(LegType.CARD, "poi-card-2", amount = "20.00"),
                    TransactionLeg(LegType.STORED_VALUE, "poi-sv-2", amount = "10.00"),
                ),
            )
        )
        val controller = controller(store)
        runBlocking {
            controller.connect("127.0.0.1", encryptionEnabled = false)
            withTimeout(10_000) {
                controller.state.first { it.connection.phase == ConnectionPhase.CONNECTED }
            }

            controller.refundSale("sale-2")
            val outcome = withTimeout(10_000) {
                controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
            }
            assertTrue(outcome.success, "expected a successful refund, got: ${outcome.message}")

            // the void reverses both tender legs in one flow, each with its
            // own original transaction reference
            val reversals = requests.filter { "\"ReversalRequest\"" in it }
            assertEquals(2, reversals.size, "expected a reversal per tender leg")
            assertTrue(reversals.any { "poi-card-2" in it }, "card leg not reversed")
            assertTrue(reversals.any { "poi-sv-2" in it }, "stored value leg not reversed")

            // recorded as one legless full refund — the sale is exhausted
            val stored = assertNotNull(store.findSale("sale-2"))
            val refund = stored.refunds.single()
            assertTrue(refund.full)
            assertEquals(null, refund.leg)
            assertTrue(stored.fullyRefunded)
        }
    }

    @Test
    fun itemRefundSendsTheReturnedItemsAndTheCartTotal() {
        val store = storeWithOneSale()
        val controller = controller(store)
        runBlocking {
            controller.connect("127.0.0.1", encryptionEnabled = false)
            withTimeout(10_000) {
                controller.state.first { it.connection.phase == ConnectionPhase.CONNECTED }
            }

            controller.startSession()
            withTimeout(10_000) { controller.state.first { it.sessionId != null } }

            controller.addReturnToBasket("sale-1", setOf("SKU-1"))
            // the return lands in the shared basket as a credit line, and
            // the sales projection nets it off the remaining quantity
            withTimeout(10_000) { controller.state.first { s -> s.basket.any { it.credit } } }
            withTimeout(10_000) {
                controller.state.first { s ->
                    s.sales.singleOrNull()?.items?.singleOrNull()?.remainingQuantity == 0
                }
            }
            // the ring holds the operation claim until the busy flag drops;
            // settling before that would be refused as concurrent
            withTimeout(10_000) { controller.state.first { !it.refundInProgress } }

            controller.settle(LoyaltyOptions(rebates = false, redemption = false, award = false))
            val outcome = withTimeout(10_000) {
                controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
            }
            assertTrue(outcome.success, "expected a successful settlement, got: ${outcome.message}")
            assertTrue(
                "returned $2.24" in outcome.message,
                "return missing from: ${outcome.message}",
            )

            // the settlement's refund leg references the card leg and
            // carries the returned item with the credit total: 2 × 1.05
            // plus 0.14 tax (matching the UI's refundMinor of 224 cents)
            val payment = assertNotNull(
                requests.firstOrNull { "\"PaymentRequest\"" in it && "Refund" in it },
                "no refund PaymentRequest reached the terminal",
            )
            assertTrue("poi-card-1" in payment, "original card reference missing: $payment")
            assertTrue("SKU-1" in payment, "returned item missing from: $payment")
            assertTrue("2.24" in payment, "credit total missing from: $payment")
            // nothing sold and loyalty off: the refund leg is the only
            // money movement, and the award stands
            assertTrue(requests.none { "\"PaymentRequest\"" in it && "Refund" !in it })
            assertTrue(requests.none { "\"LoyaltyRequest\"" in it })

            // recorded against the original sale
            val refund = assertNotNull(store.findSale("sale-1")).refunds.single()
            assertEquals(listOf(RefundedItem("SKU-1", 2)), refund.items)
            assertTrue(!refund.full, "an item return must not exhaust the sale")
            assertEquals(LegType.CARD, refund.leg)
            assertEquals("2.24", refund.amount)

            // the settlement auto-ended the checkout; a fresh one offers
            // nothing left to return, before anything reaches the basket
            withTimeout(10_000) { controller.state.first { it.sessionId == null } }
            controller.startSession()
            withTimeout(10_000) { controller.state.first { it.sessionId != null } }
            controller.addReturnToBasket("sale-1", setOf("SKU-1"))
            withTimeout(10_000) {
                controller.state.first { s ->
                    s.events.any { "Nothing left to return among the selected items" in it }
                }
            }
            assertTrue(controller.state.value.basket.isEmpty())
            assertEquals(1, assertNotNull(store.findSale("sale-1")).refunds.size)
            // the returns-only settlement did not fabricate a new "sale"
            assertEquals(1, store.listSales().size)
        }
    }

    @Test
    fun mixedSettlementChargesNewItemsAndRestoresReturns() {
        val store = storeWithOneSale()
        val controller = controller(store)
        runBlocking {
            controller.connect("127.0.0.1", encryptionEnabled = false)
            withTimeout(10_000) {
                controller.state.first { it.connection.phase == ConnectionPhase.CONNECTED }
            }
            controller.startSession()
            withTimeout(10_000) { controller.state.first { it.sessionId != null } }

            // one new item and one return in the same basket ("Grocery" is
            // tax-exempt, so the sale line stays a flat 34.99)
            controller.addProduct(Product("SKU-NEW", "Desk Lamp", 3499, "Grocery"))
            controller.addReturnToBasket("sale-1", setOf("SKU-1"))
            withTimeout(10_000) {
                controller.state.first { s -> s.basket.size == 2 && s.basket.any { it.credit } }
            }
            withTimeout(10_000) { controller.state.first { !it.refundInProgress } }
            // the display nets the return off the total: 34.99 − 2.24
            assertEquals("32.75", controller.state.value.basketTotal)

            controller.settle(LoyaltyOptions(rebates = false, redemption = false, award = false))
            val outcome = withTimeout(10_000) {
                controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
            }
            assertTrue(outcome.success, "expected a successful settlement, got: ${outcome.message}")
            assertTrue(
                "netted $2.24 against the purchase" in outcome.message,
                "netted return missing from: ${outcome.message}",
            )

            // net settlement (the default): the charge absorbs the return —
            // ONE movement for the difference, no refund leg at all
            assertTrue(
                requests.none { "\"PaymentRequest\"" in it && "\"Refund\"" in it },
                "a netted settlement must not send a refund leg",
            )
            val charge = assertNotNull(
                requests.firstOrNull { "\"PaymentRequest\"" in it },
                "no charge PaymentRequest reached the terminal",
            )
            assertTrue("32.75" in charge, "netted charge amount missing: $charge")

            // the return still lands on the ORIGINAL sale's history — full
            // value, but no tender leg (no money flowed back); the new sale
            // is persisted with only the sold line
            val refund = assertNotNull(store.findSale("sale-1")).refunds.single()
            assertEquals(listOf(RefundedItem("SKU-1", 2)), refund.items)
            assertEquals("2.24", refund.amount)
            assertEquals(null, refund.leg)
            withTimeout(10_000) { controller.state.first { it.sales.size == 2 } }
            val newSale = store.listSales().first { it.sale.id != "sale-1" }.sale
            assertEquals(listOf("SKU-NEW"), newSale.items.map { it.sku })
        }
    }

    @Test
    fun partialVoidRecordsProgressAndTheRetryCoversOnlyTheOutstandingLeg() {
        val store = JsonlSaleStore(
            Files.createTempDirectory("refund-e2e").resolve("sales.jsonl").toFile()
        )
        store.recordSale(
            SaleRecord(
                id = "sale-2",
                sessionId = "session-2",
                saleId = "bilt-emulator",
                poiId = "EMULATOR",
                currency = "USD",
                completedAt = "2026-08-06T11:00:00Z",
                authorizedAmount = "30.00",
                legs = listOf(
                    TransactionLeg(LegType.CARD, "poi-card-2", amount = "20.00"),
                    TransactionLeg(LegType.STORED_VALUE, "poi-sv-2", amount = "10.00"),
                ),
            )
        )
        // the stored value reversal fails once: the card leg reverses,
        // then the void aborts
        var svFailures = 1
        server.dispatcher = respondingWith { body ->
            if ("\"ReversalRequest\"" in body && "poi-sv-2" in body && svFailures > 0) {
                svFailures--
                REVERSAL_FAIL
            } else {
                defaultResponse(body)
            }
        }
        val controller = controller(store)
        runBlocking {
            controller.connect("127.0.0.1", encryptionEnabled = false)
            withTimeout(10_000) {
                controller.state.first { it.connection.phase == ConnectionPhase.CONNECTED }
            }

            controller.refundSale("sale-2")
            val failure = withTimeout(10_000) {
                controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
            }
            assertTrue(!failure.success, "the first attempt must fail on the SV leg")

            // the reversed card leg is recorded, so the sale is partially
            // refunded — not exhausted, and not offered as fully refundable
            val partial = assertNotNull(store.findSale("sale-2"))
            val record = partial.refunds.single()
            assertTrue(record.full)
            assertEquals(LegType.CARD, record.leg)
            assertEquals("20.00", record.amount)
            assertTrue(!partial.fullyRefunded)

            // the retry sends ONLY the outstanding stored value leg — the
            // reversed card must not be re-credited
            withTimeout(10_000) { controller.state.first { !it.refundInProgress } }
            controller.refundSale("sale-2")
            withTimeout(10_000) {
                controller.state.first {
                    it.paymentOutcome?.title == "Refund complete"
                }
            }
            assertEquals(
                1,
                requests.count { "\"ReversalRequest\"" in it && "poi-card-2" in it },
                "the reversed card leg was sent again",
            )
            assertEquals(
                2,
                requests.count { "\"ReversalRequest\"" in it && "poi-sv-2" in it },
                "expected the failed SV reversal plus its retry",
            )
            assertTrue(assertNotNull(store.findSale("sale-2")).fullyRefunded)
        }
    }

    @Test
    fun grossSettlementRefundsAndChargesSeparately() {
        val store = storeWithOneSale()
        val controller = controller(store)
        runBlocking {
            controller.connect("127.0.0.1", encryptionEnabled = false)
            withTimeout(10_000) {
                controller.state.first { it.connection.phase == ConnectionPhase.CONNECTED }
            }
            controller.startSession()
            withTimeout(10_000) { controller.state.first { it.sessionId != null } }
            controller.addProduct(Product("SKU-NEW", "Desk Lamp", 3499, "Grocery"))
            controller.addReturnToBasket("sale-1", setOf("SKU-1"))
            withTimeout(10_000) {
                controller.state.first { s -> s.basket.size == 2 && s.basket.any { it.credit } }
            }
            withTimeout(10_000) { controller.state.first { !it.refundInProgress } }

            // net off: the return refunds in full to the original tender
            // and the sale lines charge in full — two movements
            controller.settle(
                LoyaltyOptions(rebates = false, redemption = false, award = false),
                net = false,
            )
            val outcome = withTimeout(10_000) {
                controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
            }
            assertTrue(outcome.success, "expected a successful settlement, got: ${outcome.message}")
            assertTrue(
                "returned $2.24 to the card" in outcome.message,
                "return missing from: ${outcome.message}",
            )
            val refundLeg = assertNotNull(
                requests.firstOrNull { "\"PaymentRequest\"" in it && "\"Refund\"" in it },
                "no refund PaymentRequest reached the terminal",
            )
            assertTrue("poi-card-1" in refundLeg && "2.24" in refundLeg)
            val charge = assertNotNull(
                requests.firstOrNull { "\"PaymentRequest\"" in it && "\"Refund\"" !in it },
                "no charge PaymentRequest reached the terminal",
            )
            assertTrue("34.99" in charge, "charge amount missing: $charge")
            val refund = assertNotNull(store.findSale("sale-1")).refunds.single()
            assertEquals(LegType.CARD, refund.leg)
            assertEquals("2.24", refund.amount)
        }
    }
}
