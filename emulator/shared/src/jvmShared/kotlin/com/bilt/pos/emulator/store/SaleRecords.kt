package com.bilt.pos.emulator.store

import com.bilt.pos.session.payment.CheckoutResult
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Flattens a completed payment into a [SaleRecord]: only the legs the
 * terminal actually committed are recorded (a rewards-only checkout may have
 * no CARD leg at all), each with the POI reference a later referenced
 * refund/void of that movement must present.
 */
fun CheckoutResult.toSaleRecord(
    sessionId: String,
    saleId: String,
    poiId: String,
    currency: String,
    memberId: String?,
    recordId: String = UUID.randomUUID().toString(),
    completedAt: Instant = Instant.now(),
): SaleRecord {
    // A gift-card-only checkout has no card step: the SDK copies the stored
    // value payment's reference into poiTransactionId so a same-session
    // void/refund can target the one committed transaction. Only a reference
    // distinct from the stored value leg's is a real card payment — mapping
    // the copy as a CARD leg would record the same transaction twice.
    val storedValueIsPrimary = poiTransactionId != null
        && poiTransactionId == storedValuePoiTransactionId
    val legs = buildList {
        if (!storedValueIsPrimary) {
            poiTransactionId?.let {
                add(TransactionLeg(
                    type = LegType.CARD,
                    poiTransactionId = it,
                    poiTimestamp = poiTransactionTimestamp?.toString(),
                    amount = cardAmountCharged?.plain(),
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
                amount = storedValueAmountUsed?.plain(),
                approvalCode = if (storedValueIsPrimary) approvalCode else null,
                acquirerTransactionId = if (storedValueIsPrimary) acquirerTransactionId else null,
            ))
        }
        awardPoiTransactionId?.let {
            add(TransactionLeg(
                type = LegType.AWARD,
                poiTransactionId = it,
                poiTimestamp = awardPoiTransactionTimestamp?.toString(),
            ))
        }
        rebatePoiTransactionId?.let {
            add(TransactionLeg(
                type = LegType.REBATE,
                poiTransactionId = it,
                poiTimestamp = rebatePoiTransactionTimestamp?.toString(),
                amount = totalRebateAmount?.plain(),
            ))
        }
        redemptionPoiTransactionId?.let {
            // rewardRefs stay unset until CheckoutResult exposes the refs the
            // redemption sent (SDK referenced-reversal work)
            add(TransactionLeg(
                type = LegType.REDEMPTION,
                poiTransactionId = it,
                poiTimestamp = redemptionPoiTransactionTimestamp?.toString(),
                amount = pointsMonetaryValue?.plain(),
            ))
        }
    }
    val items = finalBasket?.items.orEmpty().map { line ->
        SaleItem(
            sku = line.sku,
            description = line.description,
            category = line.category,
            quantity = line.quantity,
            unitPrice = line.unitPrice?.plain() ?: "0.00",
            taxRate = line.taxRate?.plain(),
            lineTotal = line.adjustedTotal?.plain() ?: "0.00",
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
        authorizedAmount = authorizedAmount?.plain() ?: "0.00",
        cardAmountCharged = cardAmountCharged?.plain() ?: "0.00",
        storedValueAmountUsed = storedValueAmountUsed?.plain() ?: "0.00",
        totalRebateAmount = totalRebateAmount?.plain() ?: "0.00",
        pointsRedeemed = pointsRedeemed,
        pointsMonetaryValue = pointsMonetaryValue?.plain() ?: "0.00",
        totalPointsEarned = totalPointsEarned,
        legs = legs,
    )
}

private fun BigDecimal.plain(): String = toPlainString()
