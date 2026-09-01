package com.bilt.pos.emulator.store

import com.bilt.pos.session.settlement.SettlementResult
import java.math.BigDecimal
import java.time.Instant

/**
 * Flattens a completed payment into a [SaleRecord]: only the legs the
 * terminal actually committed are recorded (a rewards-only checkout may have
 * no CARD leg at all), each with the POI reference a later referenced
 * refund/void of that movement must present.
 *
 * A pure projection — [recordId] and [completedAt] belong to the act of
 * persisting, so the caller supplies them.
 */
fun SettlementResult.toSaleRecord(
    sessionId: String,
    saleId: String,
    poiId: String,
    currency: String,
    memberId: String?,
    recordId: String,
    completedAt: Instant,
): SaleRecord {
    // A gift-card-only checkout has no card step: the SDK copies the stored
    // value payment's reference into poiTransactionId so a same-session
    // void/refund can target the one committed transaction. Only a reference
    // distinct from the stored value leg's is a real card payment — mapping
    // the copy as a CARD leg would record the same transaction twice.
    val storedValueIsPrimary = poiTransactionId != null
        && poiTransactionId == storedValuePoiTransactionId
    val legs = buildList {
        fun loyaltyLeg(type: LegType, id: String?, timestamp: Instant?, amount: BigDecimal?) {
            if (id != null) {
                add(TransactionLeg(type, id, timestamp?.toString(), amount?.toPlainString()))
            }
        }
        if (!storedValueIsPrimary) {
            poiTransactionId?.let {
                add(TransactionLeg(
                    type = LegType.CARD,
                    poiTransactionId = it,
                    poiTimestamp = poiTransactionTimestamp?.toString(),
                    amount = cardAmountCharged.toPlainString(),
                    approvalCode = approvalCode,
                    acquirerTransactionId = acquirerTransactionId,
                    brand = paymentBrand,
                ))
            }
        }
        storedValuePoiTransactionId?.let {
            // as the sole tender, the result's payment artifacts (approval
            // code, acquirer id) describe the gift card payment itself; in a
            // split tender the card step overwrote them, so they stay off
            add(TransactionLeg(
                type = LegType.STORED_VALUE,
                poiTransactionId = it,
                poiTimestamp = storedValuePoiTransactionTimestamp?.toString(),
                amount = storedValueAmountUsed.toPlainString(),
                approvalCode = if (storedValueIsPrimary) approvalCode else null,
                acquirerTransactionId = if (storedValueIsPrimary) acquirerTransactionId else null,
            ))
        }
        loyaltyLeg(LegType.AWARD, awardPoiTransactionId, awardPoiTransactionTimestamp, null)
        loyaltyLeg(LegType.REBATE, rebatePoiTransactionId, rebatePoiTransactionTimestamp,
            totalRebateAmount)
        // rewardRefs stay unset until SettlementResult exposes the refs the
        // redemption sent (SDK referenced-reversal work)
        loyaltyLeg(LegType.REDEMPTION, redemptionPoiTransactionId,
            redemptionPoiTransactionTimestamp, pointsMonetaryValue)
    }
    val giftCardLoads = storedValueLoads.map { load ->
        GiftCardLoad(
            basketReference = load.basketReference,
            amount = load.amount.toPlainString(),
            poiTransactionId = load.poiTransactionId,
            poiTimestamp = load.poiTransactionTimestamp?.toString(),
        )
    }
    val fulfilledReferences = giftCardLoads.mapTo(mutableSetOf()) { it.basketReference }
    // Only ordinary sale lines are item-refundable. Returns belong to the
    // earlier sales' refund history, credits to neither, and gift-card
    // purchases are unwound by reversing their stored-value load as part of
    // a full refund rather than as merchandise returns. A sale containing a
    // fulfilled gift-card purchase is therefore full-refund-only, keeping
    // the load and its original funding reversal atomic.
    val items = finalBasket?.items.orEmpty().filter {
        it.isSale && it.reference !in fulfilledReferences
    }.map { line ->
        SaleItem(
            sku = line.sku,
            description = line.description,
            category = line.category,
            quantity = line.quantity,
            unitPrice = line.unitPrice?.toPlainString() ?: "0.00",
            taxRate = line.taxRate?.toPlainString(),
            lineTotal = line.adjustedTotal?.toPlainString() ?: "0.00",
        )
    }
    return SaleRecord(
        id = recordId,
        sessionId = sessionId,
        saleId = saleId,
        poiId = poiId,
        currency = currency,
        completedAt = completedAt.toString(),
        memberId = memberId,
        items = items,
        authorizedAmount = authorizedAmount.toPlainString(),
        pointsRedeemed = pointsRedeemed,
        totalPointsEarned = totalPointsEarned,
        legs = legs,
        giftCardLoads = giftCardLoads,
    )
}
