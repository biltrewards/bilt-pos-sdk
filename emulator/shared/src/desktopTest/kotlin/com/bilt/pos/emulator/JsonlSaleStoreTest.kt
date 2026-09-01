package com.bilt.pos.emulator

import com.bilt.pos.emulator.store.JsonlSaleStore
import com.bilt.pos.emulator.store.GiftCardLoad
import com.bilt.pos.emulator.store.LegType
import com.bilt.pos.emulator.store.RefundRecord
import com.bilt.pos.emulator.store.SaleItem
import com.bilt.pos.emulator.store.SaleRecord
import com.bilt.pos.emulator.store.TransactionLeg
import com.bilt.pos.emulator.store.VoidRecord
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonlSaleStoreTest {

    private fun newFile(): File =
        Files.createTempDirectory("sale-store").resolve("sales.jsonl").toFile()

    private fun sale(id: String, legs: List<TransactionLeg> = emptyList()) = SaleRecord(
        id = id,
        sessionId = "session-$id",
        saleId = "bilt-emulator",
        poiId = "EMULATOR",
        currency = "USD",
        completedAt = "2026-08-04T10:15:30Z",
        memberId = "member-42",
        items = listOf(
            SaleItem(
                sku = "SKU-1",
                description = "Water",
                category = "Grocery",
                quantity = 2,
                unitPrice = "1.05",
                lineTotal = "2.10",
            )
        ),
        authorizedAmount = "2.10",
        legs = legs,
    )

    @Test
    fun recordedSaleRoundTripsWithAllLegs() {
        val store = JsonlSaleStore(newFile())
        val legs = listOf(
            TransactionLeg(LegType.CARD, "poi-1", "2026-08-04T10:15:30Z", "1.10",
                approvalCode = "OK1", acquirerTransactionId = "acq-1", brand = "VISA"),
            TransactionLeg(LegType.STORED_VALUE, "poi-2", amount = "1.00"),
            TransactionLeg(LegType.AWARD, "poi-3"),
            TransactionLeg(LegType.REBATE, "poi-4", amount = "0.50"),
            TransactionLeg(LegType.REDEMPTION, "poi-5", amount = "0.25",
                rewardRefs = listOf("ref-a", "ref-b")),
        )
        val recorded = sale("s1", legs).copy(
            giftCardLoads = listOf(GiftCardLoad(
                basketReference = "gift-card-1",
                amount = "25.00",
                poiTransactionId = "poi-load-1",
                poiTimestamp = "2026-08-04T10:15:31Z",
            ))
        )
        store.recordSale(recorded)

        val found = store.findSale("s1")
        assertNotNull(found)
        assertEquals(recorded, found.sale)
        assertTrue(found.voidable)
        assertTrue(found.refundable)
        assertEquals("poi-5", found.sale.leg(LegType.REDEMPTION)?.poiTransactionId)
        assertEquals(listOf("ref-a", "ref-b"), found.sale.leg(LegType.REDEMPTION)?.rewardRefs)
        assertEquals("poi-load-1", found.sale.giftCardLoads.single().poiTransactionId)
    }

    @Test
    fun listSalesReturnsNewestFirstAndHonorsLimit() {
        val store = JsonlSaleStore(newFile())
        store.recordSale(sale("s1"))
        store.recordSale(sale("s2"))
        store.recordSale(sale("s3"))

        assertEquals(listOf("s3", "s2", "s1"), store.listSales().map { it.sale.id })
        assertEquals(listOf("s3", "s2"), store.listSales(limit = 2).map { it.sale.id })
    }

    @Test
    fun refundsAndVoidFoldIntoTheSale() {
        val store = JsonlSaleStore(newFile())
        store.recordSale(sale("s1"))
        store.recordSale(sale("s2"))

        store.recordRefund("s1", RefundRecord(
            amount = "1.00", poiTransactionId = "poi-r1", recordedAt = "2026-08-04T11:00:00Z"))
        store.recordRefund("s1", RefundRecord(
            amount = "0.50", poiTransactionId = "poi-r2", recordedAt = "2026-08-04T11:05:00Z"))
        store.recordVoid("s2", VoidRecord(
            poiTransactionId = "poi-v1", recordedAt = "2026-08-04T11:10:00Z"))

        val refunded = store.findSale("s1")
        assertNotNull(refunded)
        assertEquals(listOf("poi-r1", "poi-r2"), refunded.refunds.map { it.poiTransactionId })
        assertNull(refunded.voided)
        // a refunded sale must not be voided on top (per-session SDK guard,
        // replicated cross-session by the store's history)
        assertFalse(refunded.voidable)
        assertTrue(refunded.refundable)

        val voided = store.findSale("s2")
        assertNotNull(voided)
        assertEquals("poi-v1", voided.voided?.poiTransactionId)
        assertFalse(voided.voidable)
        assertFalse(voided.refundable)
    }

    @Test
    fun unparsableLinesAreSkippedOnRead() {
        val file = newFile()
        val store = JsonlSaleStore(file)
        store.recordSale(sale("s1"))
        // a foreign line and a torn (crash-truncated) trailing line
        file.appendText("not json at all\n")
        file.appendText("""{"type":"sale","sale":{"id":"s2","sess""")

        assertEquals(listOf("s1"), store.listSales().map { it.sale.id })
    }

    @Test
    fun eventsForUnknownSalesAreIgnored() {
        val store = JsonlSaleStore(newFile())
        store.recordRefund("ghost", RefundRecord(recordedAt = "2026-08-04T11:00:00Z"))
        store.recordVoid("ghost", VoidRecord(recordedAt = "2026-08-04T11:00:00Z"))
        store.recordSale(sale("s1"))

        assertNull(store.findSale("ghost"))
        assertEquals(listOf("s1"), store.listSales().map { it.sale.id })
    }

    @Test
    fun missingFileReadsAsEmpty() {
        val store = JsonlSaleStore(newFile())
        assertTrue(store.listSales().isEmpty())
        assertNull(store.findSale("s1"))
    }

    @Test
    fun storeCreatesParentDirectoriesOnFirstWrite() {
        val dir = Files.createTempDirectory("sale-store").toFile()
        val store = JsonlSaleStore(File(dir, "nested/deeper/sales.jsonl"))
        store.recordSale(sale("s1"))
        assertEquals(listOf("s1"), store.listSales().map { it.sale.id })
    }
}
