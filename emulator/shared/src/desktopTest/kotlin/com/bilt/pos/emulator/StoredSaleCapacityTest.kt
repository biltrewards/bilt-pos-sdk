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

/** The tender-capacity math behind refund allocation caps: only what the
 *  tender actually returned draws on it, not the netted or register-paid
 *  shares of a return's full value. */
class StoredSaleCapacityTest {

    private fun sale(cardAmount: String?) = SaleRecord(
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
        val stored = StoredSale(sale("32.75"), refunds = listOf(
            RefundRecord(
                amount = "34.99",
                tenderAmount = "1.00",
                leg = LegType.CARD,
                recordedAt = "2026-09-01T01:00:00Z",
            ),
        ))
        assertEquals(BigDecimal("31.75"), stored.remainingLegAmount(LegType.CARD))
    }

    @Test
    fun legacyRecordsFallBackToTheFullAmount() {
        // pre-split records may over-subtract — a later refund then
        // under-asks the tender, never over-asks it
        val stored = StoredSale(sale("32.75"), refunds = listOf(
            RefundRecord(
                amount = "2.24",
                leg = LegType.CARD,
                recordedAt = "2026-09-01T01:00:00Z",
            ),
        ))
        assertEquals(BigDecimal("30.51"), stored.remainingLegAmount(LegType.CARD))
    }

    @Test
    fun unknownLegAmountMeansNoCap() {
        assertNull(StoredSale(sale(null)).remainingLegAmount(LegType.CARD))
    }
}
