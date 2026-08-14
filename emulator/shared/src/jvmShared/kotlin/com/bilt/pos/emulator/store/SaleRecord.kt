package com.bilt.pos.emulator.store

import kotlinx.serialization.Serializable

/**
 * One movement the terminal committed as part of a sale. A payment is not a
 * single transaction: a split tender has a card and a stored value leg, and
 * the loyalty steps (award, rebate, redemption) each carry their own POI
 * transaction reference — the reference a later referenced refund/void of
 * that movement must present.
 *
 * Mirrors the SDK's `SettlementStep` one-to-one, but stays a separate enum:
 * these names are the persisted on-disk format and must not shift with SDK
 * internals (and a Java enum would need a hand-written serializer).
 */
enum class LegType { CARD, STORED_VALUE, AWARD, REBATE, REDEMPTION }

@Serializable
data class TransactionLeg(
    val type: LegType,
    val poiTransactionId: String,
    /** ISO-8601 timestamp the terminal reported for this movement. */
    val poiTimestamp: String? = null,
    /** Monetary amount of the movement as a plain decimal string. */
    val amount: String? = null,
    val approvalCode: String? = null,
    val acquirerTransactionId: String? = null,
    val brand: String? = null,
    /** The rewardRefs the redemption carried; a referenced redemption
     *  reversal must replay the same refs. Unset until the SDK exposes them
     *  on SettlementResult. */
    val rewardRefs: List<String>? = null,
)

@Serializable
data class SaleItem(
    val sku: String,
    val description: String,
    val category: String? = null,
    val quantity: Int,
    val unitPrice: String,
    val taxRate: String? = null,
    /** Line total after rebates/discounts, as a plain decimal string. */
    val lineTotal: String,
)

/**
 * A completed sale with everything a later referenced refund or void needs:
 * the cart contents, the totals, the member, and every committed movement's
 * POI transaction reference.
 */
@Serializable
data class SaleRecord(
    /** Emulator-local identifier, unrelated to any terminal reference. */
    val id: String,
    val sessionId: String,
    val saleId: String,
    val poiId: String,
    val currency: String,
    /** ISO-8601 instant the sale was recorded. */
    val completedAt: String,
    /** Loyalty account of the identified member; referenced loyalty
     *  reversals send it in LoyaltyData. Null for a guest checkout. */
    val memberId: String? = null,
    val items: List<SaleItem> = emptyList(),
    /** Total authorized across all tenders. Per-tender and per-loyalty
     *  amounts live on the [legs] — the single source of each movement. */
    val authorizedAmount: String,
    val pointsRedeemed: Int = 0,
    val totalPointsEarned: Int = 0,
    val legs: List<TransactionLeg> = emptyList(),
) {
    fun leg(type: LegType): TransactionLeg? = legs.firstOrNull { it.type == type }
}

/** One returned line of an item-based refund: how much of the sold
 *  quantity of [sku] this refund gave back. */
@Serializable
data class RefundedItem(
    val sku: String,
    val quantity: Int,
)

/** A refund issued against a stored sale. */
@Serializable
data class RefundRecord(
    /** Amount returned, or null when the terminal did not echo it. */
    val amount: String? = null,
    /** Terminal reference of the refund itself. */
    val poiTransactionId: String? = null,
    val poiTimestamp: String? = null,
    val recordedAt: String,
    /** True for a full-amount refund of the money leg — the sale has
     *  nothing left to refund after it. */
    val full: Boolean = false,
    /** The returned items of an item-based refund; empty for a full-amount
     *  refund. What was already returned is not returnable again. */
    val items: List<RefundedItem> = emptyList(),
)

/** A void issued against a stored sale. */
@Serializable
data class VoidRecord(
    /** Terminal reference of the reversal. */
    val poiTransactionId: String? = null,
    val poiTimestamp: String? = null,
    val recordedAt: String,
)

/**
 * A sale folded together with the refunds and void recorded against it —
 * the view refund/void flows consult before going to the terminal. The SDK's
 * "no void after a refund" guard is per-session; across sessions this
 * history is the source of truth.
 */
data class StoredSale(
    val sale: SaleRecord,
    val refunds: List<RefundRecord> = emptyList(),
    val voided: VoidRecord? = null,
) {
    /** A void must not run on a voided or already partially refunded sale. */
    val voidable: Boolean get() = voided == null && refunds.isEmpty()

    /** A full-amount refund exhausts the sale for good. Refund records
     *  from before the flag existed count as partial — they may enable a
     *  refund the acquirer then declines, never the other way around. */
    val fullyRefunded: Boolean get() = refunds.any { it.full }

    val refundable: Boolean get() = voided == null && !fullyRefunded

    /** How much of the sold quantity of [sku] earlier refunds already
     *  returned; what remains is the most a further refund may return. */
    fun refundedQuantity(sku: String): Int =
        refunds.sumOf { refund -> refund.items.filter { it.sku == sku }.sumOf { it.quantity } }
}
