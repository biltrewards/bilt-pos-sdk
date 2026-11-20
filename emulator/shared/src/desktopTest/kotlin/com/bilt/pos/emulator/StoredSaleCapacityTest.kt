package com.bilt.pos.emulator

import com.bilt.pos.emulator.store.LegType
import com.bilt.pos.emulator.store.RefundRecord
import com.bilt.pos.emulator.store.SaleRecord
import com.bilt.pos.emulator.store.StoredSale
import com.bilt.pos.emulator.store.TransactionLeg
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The tender-capacity math behind refund allocation caps: only what the tender actually returned
 * draws on it, not the netted or register-paid shares of a return's full value.
 */
class StoredSaleCapacityTest {

    private fun sale(cardAmount: String?) =
        SaleRecord(
            id = "s",
            sessionId = "x",
            saleId = "pos",
            poiId = "poi",
            currency = "USD",
            completedAt = "2026-09-01T00:00:00Z",
            authorizedAmount = cardAmount ?: "0.00",
            legs = listOf(TransactionLeg(LegType.CARD, "poi-1", amount = cardAmount)),
        )

    @Test
    fun tenderDrawUsesTheTenderAmountNotTheFullReturnValue() {
        // a mixed net return worth 34.99 drew only 1.00 from the card —
        // the rest was netted or register-paid and left the leg untouched
        val stored =
            StoredSale(
                sale("32.75"),
                refunds =
                    listOf(
                        RefundRecord(
                            amount = "34.99",
                            tenderAmount = "1.00",
                            leg = LegType.CARD,
                            recordedAt = "2026-09-01T01:00:00Z",
                        )
                    ),
            )
        assertEquals(BigDecimal("31.75"), stored.remainingLegAmount(LegType.CARD))
    }

    @Test
    fun legacyRecordsFallBackToTheFullAmount() {
        // pre-split records may over-subtract — a later refund then
        // under-asks the tender, never over-asks it
        val stored =
            StoredSale(
                sale("32.75"),
                refunds =
                    listOf(
                        RefundRecord(
                            amount = "2.24",
                            leg = LegType.CARD,
                            recordedAt = "2026-09-01T01:00:00Z",
                        )
                    ),
            )
        assertEquals(BigDecimal("30.51"), stored.remainingLegAmount(LegType.CARD))
    }

    @Test
    fun unknownLegAmountMeansNoCap() {
        assertNull(StoredSale(sale(null)).remainingLegAmount(LegType.CARD))
    }

    private fun splitSale() =
        SaleRecord(
            id = "s",
            sessionId = "x",
            saleId = "pos",
            poiId = "poi",
            currency = "USD",
            completedAt = "2026-09-01T00:00:00Z",
            authorizedAmount = "50.00",
            legs =
                listOf(
                    TransactionLeg(LegType.CARD, "poi-1", amount = "30.00"),
                    TransactionLeg(LegType.CARD, "poi-2", amount = "20.00"),
                ),
        )

    @Test
    fun aRefundNamingOneCardPaymentLeavesTheOtherUntouched() {
        val stored =
            StoredSale(
                splitSale(),
                refunds =
                    listOf(
                        RefundRecord(
                            amount = "5.00",
                            tenderAmount = "5.00",
                            leg = LegType.CARD,
                            legPoiTransactionId = "poi-2",
                            recordedAt = "2026-09-01T01:00:00Z",
                        )
                    ),
            )
        val (first, second) = stored.sale.cardLegs
        assertEquals(BigDecimal("30.00"), stored.remainingLegAmount(first))
        assertEquals(BigDecimal("15.00"), stored.remainingLegAmount(second))
    }

    @Test
    fun theSaleIsRefundedInFullOnlyOnceEveryCardPaymentIs() {
        fun fullRefund(poi: String) =
            RefundRecord(
                full = true,
                leg = LegType.CARD,
                legPoiTransactionId = poi,
                recordedAt = "2026-09-01T01:00:00Z",
            )
        val one = StoredSale(splitSale(), refunds = listOf(fullRefund("poi-2")))
        assertEquals(false, one.fullyRefunded)
        assertEquals(true, one.legRefunded(one.sale.cardLegs[1]))
        assertEquals(false, one.legRefunded(one.sale.cardLegs[0]))
        assertEquals(false, one.legRefunded(LegType.CARD))

        val both =
            StoredSale(splitSale(), refunds = listOf(fullRefund("poi-2"), fullRefund("poi-1")))
        assertEquals(true, both.fullyRefunded)
        assertEquals(true, both.legRefunded(LegType.CARD))
    }

    @Test
    fun aRecordWithoutAPaymentReferenceCountsForEveryCardPayment() {
        // records from before split card payments name only the leg type
        val legacy =
            RefundRecord(full = true, leg = LegType.CARD, recordedAt = "2026-09-01T01:00:00Z")
        val stored = StoredSale(splitSale(), refunds = listOf(legacy))
        assertEquals(true, stored.fullyRefunded)
    }

    @Test
    fun aFullRefundNamingNoLegReturnedEveryCardPayment() {
        // what a completed void records: it exhausted the whole sale
        val voidRecord = RefundRecord(full = true, recordedAt = "2026-09-01T01:00:00Z")
        val stored = StoredSale(splitSale(), refunds = listOf(voidRecord))
        assertEquals(listOf(true, true), stored.sale.cardLegs.map { stored.legRefunded(it) })
        assertEquals(true, stored.legRefunded(LegType.CARD))
    }
}
