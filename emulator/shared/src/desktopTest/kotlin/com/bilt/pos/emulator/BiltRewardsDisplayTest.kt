package com.bilt.pos.emulator

import com.bilt.pos.emulator.session.BiltRewardsPaymentUi
import com.bilt.pos.emulator.session.CardPaymentUi
import com.bilt.pos.emulator.session.PartialApprovalAction
import com.bilt.pos.emulator.session.PartialApprovalPrompt
import com.bilt.pos.emulator.session.StoredSaleUi
import com.bilt.pos.emulator.store.JsonlSaleStore
import com.bilt.pos.emulator.store.LegType
import com.bilt.pos.emulator.store.RefundRecord
import com.bilt.pos.emulator.store.SaleRecord
import com.bilt.pos.emulator.store.TransactionLeg
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * How a Bilt Rewards payment is named and persisted: the display strings of the outcome, the
 * partial prompt and the sales list, and the sale record keeping what a later void or refund needs.
 */
class BiltRewardsDisplayTest {

    private fun prompt(biltRewards: Boolean) =
        PartialApprovalPrompt(
            approved = "7.16",
            requested = "17.89",
            shortfall = "10.73",
            amountDue = "10.73",
            actions = PartialApprovalAction.entries,
            biltRewards = biltRewards,
        ) {}

    @Test
    fun theRewardsPromptSaysBilltRewardsPaidNotACardApproved() {
        assertEquals(
            "Bilt Rewards paid $7.16 of $17.89, $10.73 still due",
            prompt(biltRewards = true).message,
        )
        assertEquals(
            "Card approved $7.16 of $17.89, $10.73 still due",
            prompt(biltRewards = false).message,
        )
        assertEquals(
            "Record $10.73 as cash and keep the payments. Choose only after collecting it.",
            prompt(true).describe(PartialApprovalAction.CASH),
        )
        assertEquals(
            "Record $10.73 as cash and keep the card payments. Choose only after collecting it.",
            prompt(false).describe(PartialApprovalAction.CASH),
        )
    }

    @Test
    fun aRewardsPaymentIsLabelledBilltRewardsWithNoCardData() {
        assertEquals("Bilt Rewards $17.89", BiltRewardsPaymentUi(amount = "17.89").line)
        assertEquals(
            "Bilt Rewards $17.89 · refunded",
            BiltRewardsPaymentUi(amount = "17.89", refunded = true).line,
        )
        assertTrue(BiltRewardsPaymentUi(amount = "17.89").returnable)
        assertTrue(!BiltRewardsPaymentUi(amount = "17.89", depleted = true).returnable)
    }

    @Test
    fun aMixedCardPaymentSplitsItsAmountIntoCardAndBilltRewards() {
        val mixed =
            CardPaymentUi(
                label = "Card 1",
                amount = "13.21",
                brand = "Visa",
                lastDigits = "1111",
                biltRewardsAmount = "3.30",
                cardAmount = "9.91",
            )
        assertEquals(
            "Card 1 $13.21 (card $9.91 + Bilt Rewards $3.30) · Visa ····1111",
            mixed.line,
        )
        // a plain card keeps its line
        assertEquals(
            "Card 1 $13.21 · Visa ····1111",
            mixed.copy(biltRewardsAmount = null, cardAmount = null).line,
        )
    }

    @Test
    fun theSalesListLinesPutBilltRewardsAheadOfTheCardsAndStayEmptyForOnePlainCard() {
        val card = CardPaymentUi(label = "Card 1", amount = "10.73", brand = "Visa")
        val rewards = BiltRewardsPaymentUi(amount = "7.16")
        fun sale(cards: List<CardPaymentUi>, payments: List<BiltRewardsPaymentUi>) =
            StoredSaleUi(
                id = "s",
                completedAtLabel = "now",
                totalAmount = "17.89",
                cardPayments = cards,
                biltRewardsPayments = payments,
            )

        assertEquals(
            listOf("Bilt Rewards $7.16", "Card 1 $10.73 · Visa"),
            sale(listOf(card), listOf(rewards)).paymentLines,
        )
        assertTrue(sale(listOf(card), emptyList()).paymentLines.isEmpty())
        // one card that rewards paid part of is worth a line
        assertEquals(
            1,
            sale(
                    listOf(card.copy(biltRewardsAmount = "3.30", cardAmount = "7.43")),
                    emptyList(),
                )
                .paymentLines
                .size,
        )
    }

    private val recordWithRewards =
        SaleRecord(
            id = "sale-1",
            sessionId = "session-1",
            saleId = "bilt-emulator",
            poiId = "EMULATOR",
            currency = "USD",
            completedAt = "2026-08-04T10:15:30Z",
            authorizedAmount = "20.37",
            legs =
                listOf(
                    TransactionLeg(LegType.BILT_REWARDS, "poi-r1", "2026-08-04T10:15:28Z", "7.16"),
                    TransactionLeg(
                        LegType.CARD,
                        "poi-c1",
                        "2026-08-04T10:15:29Z",
                        "13.21",
                        brand = "Visa",
                        maskedPan = "411111******1111",
                        biltRewardsAmount = "3.30",
                    ),
                ),
        )

    @Test
    fun theSaleRecordKeepsTheRewardsPaymentAndTheCardsRewardsPartAcrossTheStore() {
        val file = Files.createTempDirectory("rewards-store").resolve("sales.jsonl").toFile()
        JsonlSaleStore(file).recordSale(recordWithRewards)

        // a fresh store reads the file back: what a void or refund needs survives a restart
        val stored = assertNotNull(JsonlSaleStore(file).findSale("sale-1"))

        assertEquals(recordWithRewards, stored.sale)
        assertEquals(listOf("poi-r1"), stored.sale.biltRewardsLegs.map { it.poiTransactionId })
        assertEquals(listOf("poi-c1"), stored.sale.cardLegs.map { it.poiTransactionId })
        assertEquals("3.30", stored.sale.cardLegs.single().biltRewardsAmount)
        // the rewards payment is a tender the full refund must cover, but never a card
        assertEquals(listOf("poi-r1", "poi-c1"), stored.moneyLegs.map { it.poiTransactionId })
    }

    @Test
    fun aRefundOfTheRewardsPaymentExhaustsItsCapacityAndNotTheCards() {
        val file = Files.createTempDirectory("rewards-store").resolve("sales.jsonl").toFile()
        val store = JsonlSaleStore(file)
        store.recordSale(recordWithRewards)
        store.recordRefund(
            "sale-1",
            RefundRecord(
                amount = "7.16",
                recordedAt = "2026-08-05T10:00:00Z",
                full = true,
                leg = LegType.BILT_REWARDS,
                legPoiTransactionId = "poi-r1",
                tenderAmount = "7.16",
            ),
        )

        val stored = assertNotNull(store.findSale("sale-1"))
        val rewards = stored.sale.biltRewardsLegs.single()
        val card = stored.sale.cardLegs.single()

        assertTrue(stored.legRefunded(rewards))
        assertTrue(!stored.legRefunded(card))
        assertEquals("0.00", stored.remainingLegAmount(rewards)?.toPlainString())
        assertEquals("13.21", stored.remainingLegAmount(card)?.toPlainString())
        assertTrue(!stored.fullyRefunded)
    }

    @Test
    fun aRecordFromBeforeBilltRewardsStillDecodes() {
        val old =
            """{"id":"s","sessionId":"x","saleId":"a","poiId":"p","currency":"USD",
               "completedAt":"2026-08-04T10:15:30Z","authorizedAmount":"5.00",
               "legs":[{"type":"CARD","poiTransactionId":"poi-c","amount":"5.00"}]}"""
        val sale = Json { ignoreUnknownKeys = true }.decodeFromString<SaleRecord>(old)

        assertNull(sale.legs.single().biltRewardsAmount)
        assertTrue(sale.biltRewardsLegs.isEmpty())
        // and a record with rewards encodes the new fields by name
        assertTrue("\"biltRewardsAmount\":\"3.30\"" in Json.encodeToString(recordWithRewards))
    }
}
