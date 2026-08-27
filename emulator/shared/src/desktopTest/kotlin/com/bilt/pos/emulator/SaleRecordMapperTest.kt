package com.bilt.pos.emulator

import com.bilt.pos.emulator.store.LegType
import com.bilt.pos.emulator.store.toSaleRecord
import com.bilt.pos.session.basket.Basket
import com.bilt.pos.session.basket.BasketLineItem
import com.bilt.pos.session.settlement.SettlementResult
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SaleRecordMapperTest {

    private val cardTime = Instant.parse("2026-08-04T10:00:00Z")
    private val recordTime = Instant.parse("2026-08-04T10:00:05Z")

    private fun record(result: SettlementResult) = result.toSaleRecord(
        sessionId = "sess-1",
        saleId = "bilt-emulator",
        poiId = "EMULATOR",
        currency = "USD",
        memberId = "member-42",
        recordId = "rec-1",
        completedAt = recordTime,
    )

    @Test
    fun mapsEveryCommittedLegWithItsReference() {
        val result = SettlementResult.builder()
            .success(true)
            .authorizedAmount(BigDecimal("20.00"))
            .cardAmountCharged(BigDecimal("12.75"))
            .storedValueAmountUsed(BigDecimal("5.00"))
            .approvalCode("APPR")
            .acquirerTransactionId("acq-9")
            .paymentBrand("VISA")
            .totalRebateAmount(BigDecimal("1.50"))
            .pointsRedeemed(75)
            .pointsMonetaryValue(BigDecimal("0.75"))
            .totalPointsEarned(20)
            .poiTransactionId("poi-card").poiTransactionTimestamp(cardTime)
            .storedValuePoiTransactionId("poi-sv")
            .awardPoiTransactionId("poi-award")
            .rebatePoiTransactionId("poi-rebate")
            .redemptionPoiTransactionId("poi-redeem")
            .finalBasket(Basket.builder()
                .items(listOf(BasketLineItem.builder()
                    .itemId("i1")
                    .sku("SKU-1")
                    .description("Desk Lamp")
                    .category("Home")
                    .quantity(1)
                    .unitPrice(BigDecimal("34.99"))
                    .adjustedTotal(BigDecimal("33.49"))
                    .taxRate(BigDecimal("0.06625"))
                    .build()))
                .build())
            .build()

        val sale = record(result)

        assertEquals("rec-1", sale.id)
        assertEquals("sess-1", sale.sessionId)
        assertEquals("member-42", sale.memberId)
        assertEquals(recordTime.toString(), sale.completedAt)
        assertEquals("20.00", sale.authorizedAmount)
        assertEquals(75, sale.pointsRedeemed)
        assertEquals(20, sale.totalPointsEarned)

        assertEquals(
            listOf(LegType.CARD, LegType.STORED_VALUE, LegType.AWARD,
                LegType.REBATE, LegType.REDEMPTION),
            sale.legs.map { it.type },
        )
        val card = sale.leg(LegType.CARD)!!
        assertEquals("poi-card", card.poiTransactionId)
        assertEquals(cardTime.toString(), card.poiTimestamp)
        assertEquals("12.75", card.amount)
        assertEquals("APPR", card.approvalCode)
        assertEquals("acq-9", card.acquirerTransactionId)
        assertEquals("VISA", card.brand)
        assertEquals("5.00", sale.leg(LegType.STORED_VALUE)?.amount)
        assertEquals("1.50", sale.leg(LegType.REBATE)?.amount)
        assertEquals("0.75", sale.leg(LegType.REDEMPTION)?.amount)

        assertEquals(1, sale.items.size)
        val item = sale.items.single()
        assertEquals("SKU-1", item.sku)
        assertEquals("34.99", item.unitPrice)
        assertEquals("33.49", item.lineTotal)
        assertEquals("0.06625", item.taxRate)
    }

    @Test
    fun onlyCommittedLegsAreRecorded() {
        val result = SettlementResult.builder()
            .success(true)
            .authorizedAmount(BigDecimal("5.00"))
            .cardAmountCharged(BigDecimal("5.00"))
            .poiTransactionId("poi-card").poiTransactionTimestamp(cardTime)
            .build()

        val sale = record(result)

        assertEquals(listOf(LegType.CARD), sale.legs.map { it.type })
        assertNull(sale.leg(LegType.AWARD))
        assertEquals(emptyList(), sale.items)
    }

    @Test
    fun giftCardOnlyCheckoutRecordsOneStoredValueLeg() {
        // gift-card-only: the SDK copies the stored value payment's reference
        // into poiTransactionId (no card step ran) — the mapper must not
        // record that copy as a CARD leg on top of the STORED_VALUE leg
        val result = SettlementResult.builder()
            .success(true)
            .authorizedAmount(BigDecimal("8.00"))
            .storedValueAmountUsed(BigDecimal("8.00"))
            .approvalCode("SV-APPR")
            .acquirerTransactionId("acq-sv")
            .poiTransactionId("poi-sv").poiTransactionTimestamp(cardTime)
            .storedValuePoiTransactionId("poi-sv")
            .storedValuePoiTransactionTimestamp(cardTime)
            .build()

        val sale = record(result)

        assertEquals(listOf(LegType.STORED_VALUE), sale.legs.map { it.type })
        val leg = sale.leg(LegType.STORED_VALUE)!!
        assertEquals("poi-sv", leg.poiTransactionId)
        assertEquals("8.00", leg.amount)
        // as the sole tender, the payment artifacts describe the gift card leg
        assertEquals("SV-APPR", leg.approvalCode)
        assertEquals("acq-sv", leg.acquirerTransactionId)
    }

    @Test
    fun splitTenderKeepsPaymentArtifactsOnTheCardLeg() {
        val result = SettlementResult.builder()
            .success(true)
            .authorizedAmount(BigDecimal("10.00"))
            .cardAmountCharged(BigDecimal("6.00"))
            .storedValueAmountUsed(BigDecimal("4.00"))
            .approvalCode("CARD-APPR")
            .acquirerTransactionId("acq-card")
            .poiTransactionId("poi-card").poiTransactionTimestamp(cardTime)
            .storedValuePoiTransactionId("poi-sv")
            .build()

        val sale = record(result)

        assertEquals(listOf(LegType.CARD, LegType.STORED_VALUE), sale.legs.map { it.type })
        assertEquals("CARD-APPR", sale.leg(LegType.CARD)?.approvalCode)
        // the result's artifacts belong to the card step; the stored value
        // leg's own approval code was overwritten and must not be claimed
        assertNull(sale.leg(LegType.STORED_VALUE)?.approvalCode)
        assertNull(sale.leg(LegType.STORED_VALUE)?.acquirerTransactionId)
    }

    @Test
    fun loyaltyOnlyCheckoutHasNoTenderLegs() {
        val result = SettlementResult.builder()
            .success(true)
            .authorizedAmount(BigDecimal.ZERO)
            .totalRebateAmount(BigDecimal("3.00"))
            .rebatePoiTransactionId("poi-rebate")
            .redemptionPoiTransactionId("poi-redeem")
            .pointsRedeemed(200)
            .pointsMonetaryValue(BigDecimal("2.00"))
            .build()

        val sale = record(result)

        assertEquals(listOf(LegType.REBATE, LegType.REDEMPTION), sale.legs.map { it.type })
    }
}
