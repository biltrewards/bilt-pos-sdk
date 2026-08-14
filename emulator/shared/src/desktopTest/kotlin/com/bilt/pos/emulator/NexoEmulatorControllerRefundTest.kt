package com.bilt.pos.emulator

import com.bilt.pos.emulator.session.ConnectionPhase
import com.bilt.pos.emulator.session.EmulatorConfig
import com.bilt.pos.emulator.session.NexoEmulatorController
import com.bilt.pos.emulator.store.JsonlSaleStore
import com.bilt.pos.emulator.store.LegType
import com.bilt.pos.emulator.store.RefundedItem
import com.bilt.pos.emulator.store.SaleItem
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
            "<receipt><plainTextReceipt>REFUND $2.24</plainTextReceipt></receipt>"

        const val REFUND_OK =
            """{"SaleToPOIResponse":{"PaymentResponse":{
                "Response":{"Result":"Success"},
                "POIData":{"POITransactionID":{"TransactionID":"POI-REF-100",
                    "TimeStamp":"2026-03-02T16:00:05+00:00"}},
                "PaymentReceipt":[
                    {"DocumentQualifier":"CustomerReceipt",
                        "OutputContent":{"OutputFormat":"XHTML","OutputXHTML":"$RAW_RECEIPT_XML"}},
                    {"DocumentQualifier":"CashierReceipt",
                        "OutputContent":{"OutputFormat":"XHTML","OutputXHTML":"!!!not-a-receipt"}}],
                "PaymentResult":{"AmountsResp":{"Currency":"USD","AuthorizedAmount":24.99},
                    "PaymentAcquirerData":{"ApprovalCode":"APPR01"}}}}}"""
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
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.clone().readUtf8()
                    requests.add(body)
                    return MockResponse().setBody(
                        when {
                            "\"DiagnosisRequest\"" in body -> DIAGNOSIS_OK
                            "\"AdminRequest\"" in body -> ADMIN_OK
                            "\"LoyaltyRequest\"" in body -> LOYALTY_OK
                            "\"PaymentRequest\"" in body -> REFUND_OK
                            else -> ADMIN_OK
                        }
                    )
                }
            }
            // the controller's endpoint is fixed: https://<address>:8443/nexo
            start(InetAddress.getByName("127.0.0.1"), 8443)
        }
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

    private fun controller(store: JsonlSaleStore) = NexoEmulatorController(
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
            assertTrue("24.99" in outcome.message, "amount missing from: ${outcome.message}")

            // the refund landed in the store with the terminal's references,
            // marked as exhausting the sale
            val refund = assertNotNull(store.findSale("sale-1")).refunds.single()
            assertEquals("24.99", refund.amount)
            assertEquals("POI-REF-100", refund.poiTransactionId)
            assertTrue(refund.full, "a full-amount refund must be recorded as full")
            assertTrue(assertNotNull(store.findSale("sale-1")).fullyRefunded)

            // the sales list refreshed, so the UI shows the refunded badge,
            // and the busy flag released
            withTimeout(10_000) {
                controller.state.first { it.sales.singleOrNull()?.refunded == true }
                controller.state.first { !it.refundInProgress }
            }

            // the wire saw the refund referencing the stored card leg, plus
            // the award reversal carrying its own reference
            val payment = assertNotNull(
                requests.firstOrNull { "\"PaymentRequest\"" in it },
                "no refund PaymentRequest reached the terminal",
            )
            assertTrue("poi-card-1" in payment, "original card reference missing: $payment")
            val loyalty = assertNotNull(
                requests.firstOrNull { "\"LoyaltyRequest\"" in it },
                "no award reversal reached the terminal",
            )
            assertTrue("poi-award-1" in loyalty, "award reference missing: $loyalty")

            // a second attempt is refused before the wire — the sale was
            // refunded in full. Wait out the first attempt's claim first,
            // like the UI does via the disabled button.
            withTimeout(10_000) { controller.state.first { !it.refundInProgress } }
            val paymentsBefore = requests.count { "\"PaymentRequest\"" in it }
            controller.refundSale("sale-1")
            withTimeout(10_000) {
                controller.state.first { s ->
                    s.events.any { "already refunded in full" in it }
                }
            }
            assertEquals(paymentsBefore, requests.count { "\"PaymentRequest\"" in it })
            assertEquals(1, assertNotNull(store.findSale("sale-1")).refunds.size)
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
            assertTrue("card" in outcome.message && "gift card" in outcome.message,
                "expected both tender legs in the outcome: ${outcome.message}")

            // one linked refund per tender leg, each referencing its own
            // original transaction
            val payments = requests.filter { "\"PaymentRequest\"" in it }
            assertEquals(2, payments.size, "expected a refund per tender leg")
            assertTrue(payments.any { "poi-card-2" in it }, "card leg not refunded")
            assertTrue(payments.any { "poi-sv-2" in it }, "stored value leg not refunded")

            // both legs recorded as fully returned — only then is the sale
            // exhausted
            val stored = assertNotNull(store.findSale("sale-2"))
            assertEquals(
                setOf(LegType.CARD, LegType.STORED_VALUE),
                stored.refunds.mapNotNull { it.leg }.toSet(),
            )
            assertTrue(stored.refunds.all { it.full })
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

            controller.refundSale("sale-1", skus = setOf("SKU-1"))
            val outcome = withTimeout(10_000) {
                controller.state.first { it.paymentOutcome != null }.paymentOutcome!!
            }
            assertTrue(outcome.success, "expected a successful refund, got: ${outcome.message}")
            // the terminal's customer receipt (sent raw, not base64) lands
            // on the outcome popup
            assertEquals("REFUND $2.24", outcome.receipt)

            // the refund PaymentRequest carries the returned item and asks
            // for the cart total: 2 × 1.05 plus 0.14 tax (matching the UI's
            // refundMinor of 224 cents)
            val payment = assertNotNull(
                requests.firstOrNull { "\"PaymentRequest\"" in it },
                "no refund PaymentRequest reached the terminal",
            )
            assertTrue("SKU-1" in payment, "returned item missing from: $payment")
            assertTrue("2.24" in payment, "cart total missing from: $payment")

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

            // the store knows what was returned: the line is exhausted in
            // the UI projection and a repeat attempt never reaches the wire
            val refund = assertNotNull(store.findSale("sale-1")).refunds.single()
            assertEquals(listOf(RefundedItem("SKU-1", 2)), refund.items)
            assertTrue(!refund.full, "an item refund must not exhaust the sale")
            withTimeout(10_000) {
                controller.state.first { s ->
                    s.sales.singleOrNull()?.items?.singleOrNull()?.remainingQuantity == 0
                }
            }
            // the busy flag clears after the operation claim releases; a
            // second attempt before that is refused as a concurrent
            // operation, not as an exhausted sale (the UI disables the
            // button until then)
            withTimeout(10_000) { controller.state.first { !it.refundInProgress } }
            controller.refundSale("sale-1", skus = setOf("SKU-1"))
            withTimeout(10_000) {
                controller.state.first { s ->
                    s.events.any { "Nothing left to refund among the selected items" in it }
                }
            }
            assertEquals(1, requests.count { "\"PaymentRequest\"" in it })
            assertEquals(1, assertNotNull(store.findSale("sale-1")).refunds.size)
        }
    }
}
