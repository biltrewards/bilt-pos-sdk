package com.bilt.pos.emulator.session

import com.bilt.pos.emulator.catalog.Product
import com.bilt.pos.emulator.catalog.minorUnitsToDecimal
import kotlinx.coroutines.flow.StateFlow

enum class ConnectionPhase { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/** Connection state plus a short human-readable detail (POI status, error). */
data class ConnectionStatus(
    val phase: ConnectionPhase = ConnectionPhase.DISCONNECTED,
    val detail: String? = null,
)

/**
 * Outcome of the out-of-band TLS verification probe. Verification failure is
 * reported but never blocks communication — the payload channel runs on a
 * permissive client regardless.
 */
sealed interface TlsStatus {
    /** No CA configured; nothing to verify against. */
    data object NotConfigured : TlsStatus

    /** Chain and hostname pattern verified against the configured CA. */
    data object Verified : TlsStatus

    data class Failed(val reason: String) : TlsStatus

    /** Not probed yet. */
    data object Unknown : TlsStatus

    val label: String
        get() = when (this) {
            NotConfigured -> "TLS: unverified (no CA configured)"
            Verified -> "TLS: verified"
            is Failed -> "TLS: FAILED — $reason (still communicating)"
            Unknown -> "TLS: not checked"
        }
}

data class BasketLine(
    val sku: String,
    val description: String,
    val quantity: Int,
    val lineTotal: String,
    /** Compatibility flag for subtractive lines (returns and register
     *  credits). [type] distinguishes their settlement behavior. */
    val credit: Boolean = false,
    /** Session-assigned id used for mutations that must target one exact
     *  line (notably discounts when several referenced gift-card lines use
     *  the same SKU). */
    val itemId: String = sku,
    val type: BasketLineType = if (credit) BasketLineType.RETURN else BasketLineType.SALE,
    /** Signed pre-discount value and register discount total. */
    val originalTotal: String = lineTotal,
    val discountTotal: String = "0.00",
    val discountLabels: List<String> = emptyList(),
    /** True when this sale line has a settlement-time stored-value load. */
    val giftCard: Boolean = false,
)

enum class BasketLineType { SALE, RETURN, CREDIT }

/** Outcome of the last payment or refund attempt, shown as a popup until
 *  dismissed. */
data class PaymentOutcome(
    val success: Boolean,
    /** Dialog title, e.g. "Payment successful" or "Refund failed". */
    val title: String,
    val message: String,
    /** The transaction's receipt as the terminal rendered it (customer
     *  copy, falling back to the merchant copy); null when the terminal
     *  returned none. */
    val receipt: String? = null,
)

/**
 * Payment configuration. The SDK-side loyalty steps run only for a member
 * attached to the session — identified at Start Checkout, or by the
 * customer self-identifying on the terminal during the flow.
 */
data class LoyaltyOptions(
    /** Rebate (coupon) redemption. */
    val rebates: Boolean = true,
    /** Point/reward redemption for monetary value. */
    val redemption: Boolean = true,
    /** Point award on the completed purchase. */
    val award: Boolean = true,
)

/**
 * Gift (stored value) card tender for a payment. When present, the payment
 * charges the card first and collects any remainder with a standard card
 * payment (split tender). SDK-free so commonMain can construct it; the
 * controller maps it onto the SDK's stored value card.
 */
data class StoredValueOptions(
    /** Card number, charged as keyed entry. Blank means the terminal
     *  prompts the customer to swipe the card instead. */
    val cardNumber: String = "",
)

/**
 * A card read from the terminal (CardAcquisition request) that returned a
 * full card number, published for the gift card field to adopt. [sequence]
 * increments per read so re-reading the same card still counts as a new
 * value for UI effects keyed on it.
 */
data class AcquiredCard(
    val number: String,
    val sequence: Int,
)

/** One cart line of a stored sale, as the Refund tab shows it. */
data class SaleItemUi(
    val sku: String,
    val description: String,
    /** Quantity sold. */
    val quantity: Int,
    /** What an item-based refund returns for this line — shelf price plus
     *  tax of the [remainingQuantity], matching the credit line the refund
     *  cart will ring — in minor currency units (cents), so selections sum
     *  as Longs instead of decimal-string arithmetic. */
    val refundMinor: Long,
    /** Quantity earlier refunds have not returned yet; zero means the line
     *  cannot be refunded again. */
    val remainingQuantity: Int = quantity,
) {
    val refundLabel: String get() = "$" + minorUnitsToDecimal(refundMinor)

    val refundedQuantity: Int get() = quantity - remainingQuantity
}

/**
 * A completed sale as the Refund tab lists it — a UI projection of the
 * store's `StoredSale`, mapped in jvmShared so commonMain stays free of the
 * store types.
 */
data class StoredSaleUi(
    /** The store's record id ([com.bilt.pos.emulator.store.SaleRecord.id]). */
    val id: String,
    /** Completion time, already formatted for display in local time. */
    val completedAtLabel: String,
    /** Total authorized across all tenders, as a plain decimal string. */
    val totalAmount: String,
    /** Loyalty account of the identified member; null for a guest checkout. */
    val memberId: String? = null,
    val items: List<SaleItemUi> = emptyList(),
    /** True when refunds were already recorded against the sale. */
    val refunded: Boolean = false,
    /** True once a full-amount refund ran — nothing left to refund. */
    val fullyRefunded: Boolean = false,
    val voided: Boolean = false,
    /** Whether the Full amount mode may run — mirrors the controller's
     *  guard exactly: item refunds block it (a void would over-return),
     *  but the per-leg residue of a void that failed midway does NOT — a
     *  retried full refund is precisely how the outstanding tender gets
     *  finished. */
    val fullRefundAvailable: Boolean = !voided && !fullyRefunded,
) {
    /** A voided or fully refunded sale cannot be refunded again (mirrors
     *  `StoredSale.refundable`). */
    val refundable: Boolean get() = !voided && !fullyRefunded

    val memberLabel: String get() = memberId?.let { "member $it" } ?: "guest"

    /** One-line badge for the sales list: buyer plus what already happened
     *  to the sale. */
    val statusLabel: String
        get() = listOfNotNull(
            memberLabel,
            when {
                voided -> "voided"
                fullyRefunded -> "refunded"
                refunded -> "partially refunded"
                else -> null
            },
        ).joinToString(" · ")
}

data class EmulatorState(
    val terminalAddress: String = "",
    val addressAutodetected: Boolean = false,
    val connection: ConnectionStatus = ConnectionStatus(),
    val tls: TlsStatus = TlsStatus.Unknown,
    /** Whether the active (or last) connection encrypts messages. */
    val encryptionEnabled: Boolean = false,
    /** True when a passphrase is available from config (NEXO_PASSPHRASE), so
     *  the UI can offer encryption without asking for one. */
    val hasConfiguredPassphrase: Boolean = false,
    /** Active checkout session id, or null when no session is running.
     *  One session = one customer checkout; connect alone starts none. */
    val sessionId: String? = null,
    val basket: List<BasketLine> = emptyList(),
    val basketTotal: String = "0.00",
    val basketTax: String = "0.00",
    /** True while a payment is on the wire. */
    val paymentInProgress: Boolean = false,
    /** True while a terminal card read (CardAcquisition) is on the wire. */
    val cardReadInProgress: Boolean = false,
    /** True while the session-start member identification prompt is on the
     *  wire. */
    val identifyInProgress: Boolean = false,
    /** True while a referenced refund (ReversalSession) is on the wire. */
    val refundInProgress: Boolean = false,
    /** One-line summary of the checkout's completed payment; null until
     *  paid. A fully collected payment ends the checkout automatically; the
     *  summary stays visible until the next one starts. */
    val lastPayment: String? = null,
    /** Success/failure of the last payment or refund attempt, rendered as
     *  a popup until dismissed; cleared when a new attempt starts. */
    val paymentOutcome: PaymentOutcome? = null,
    /** Last terminal card read that carried a full card number; the gift
     *  card field adopts each new read. */
    val acquiredCard: AcquiredCard? = null,
    /** Stored completed sales, newest first, listed on the Refund tab. */
    val sales: List<StoredSaleUi> = emptyList(),
    /** Curated one-line event feed shown on the Events tab. */
    val events: List<String> = emptyList(),
    /** Raw logger output (SDK java.util.logging records, stack traces) for the Detailed tab. */
    val detailedEvents: List<String> = emptyList(),
)

/**
 * The UI's handle on the emulator, kept SDK-free so commonMain can depend on
 * it; the implementation lives in jvmShared where the Java SDK is available.
 */
interface EmulatorController {
    val state: StateFlow<EmulatorState>

    /** Try to prefill the terminal address (adb-based on desktop); when
     *  detection succeeds and nothing is connected yet, connects to the
     *  detected terminal automatically. */
    fun autodetectAddress()

    /**
     * Connect to the terminal at [address].
     *
     * @param encryptionEnabled whether to encrypt messages on this connection
     * @param passphraseOverride passphrase entered in the UI; blank/null falls
     *   back to the configured `NEXO_PASSPHRASE`
     * @param adbTunnel connect through a localhost `adb forward` tunnel to
     *   the device instead of dialing it directly — the way around macOS
     *   denying the JVM process local-network access. Requires the terminal
     *   attached via adb (USB, or wifi adb at the same address).
     */
    fun connect(
        address: String,
        encryptionEnabled: Boolean,
        passphraseOverride: String? = null,
        adbTunnel: Boolean = false,
    )

    fun disconnect()

    /**
     * Start a new checkout session (terminal Start bracket) on the
     * connection. With [identifyOnStart], the terminal prompts for member
     * identification right after the start acknowledges — its own
     * operation, not part of the bracket; a failed or declined prompt
     * degrades to a guest checkout.
     */
    fun startSession(identifyOnStart: Boolean = false)

    /** End the active checkout session (terminal End bracket). */
    fun endSession()

    /** Ring up one unit of [product] on the active session. */
    fun addProduct(product: Product)

    /**
     * Ring a gift-card sale line and arrange for the terminal to activate
     * and load that card after the basket has been funded. [amount] is the
     * face value. A blank [cardNumber] asks the terminal to read the card.
     */
    fun addGiftCardPurchase(amount: String, cardNumber: String = "")

    /**
     * Add a register-originated credit associated with the selected sale
     * line. It is represented as its own credit line because credits reduce
     * the charge without changing the fulfilled value of a gift-card line.
     */
    fun applyCredit(itemId: String, amount: String, label: String = "")

    /** Replace the selected sale line's register discount; zero clears it. */
    fun applyDiscount(itemId: String, amount: String, label: String = "")

    /**
     * Run settlement on the active session. [loyalty] picks which loyalty
     * steps run; [storedValue] adds a gift card as the first tender —
     * anything it doesn't cover falls through to the standard card payment.
     */
    /**
     * Settles the active checkout. With [net] (the default) a mixed basket
     * moves only the signed difference: a payment-dominant basket charges
     * the difference and the returns' value is absorbed by it; a
     * refund-dominant basket refunds only the difference to the original
     * tenders. Without [net], returns are refunded in full to their
     * original tenders and the sale lines are charged in full.
     */
    fun settle(
        loyalty: LoyaltyOptions,
        storedValue: StoredValueOptions? = null,
        net: Boolean = true,
    )

    /**
     * Read a card on the terminal (nexo CardAcquisition request) without
     * charging it. A read that returns a full card number is published as
     * [EmulatorState.acquiredCard] so the gift card field can adopt it; a
     * masked-only read is just logged.
     */
    fun acquireCard()

    /**
     * Full refund of the stored sale [saleId], run on a fresh checkout
     * session against the connected terminal — the originating checkout is
     * long gone; the stored transaction references identify what to
     * reverse. Voids every referenced movement (tender legs, redemption,
     * rebate, award). Requires a connection with no active checkout
     * session. The outcome reports as [EmulatorState.paymentOutcome], like
     * a payment.
     */
    fun refundSale(saleId: String)

    /**
     * Rings the selected items of the stored sale [saleId] into the ACTIVE
     * checkout's basket as returns (credit lines, shelf price plus tax per
     * line). The basket may mix returns with new items — settlement then
     * charges the sale lines and restores each return's value to its
     * original sale's outstanding tender via a refund allocation. Rung
     * returns are held until the settlement succeeds (recorded against
     * their sales then) or the checkout ends.
     */
    fun addReturnToBasket(saleId: String, skus: Set<String>)

    /**
     * Abort whatever is in flight, mirroring the SDK's operation-scoped
     * `abort()`: an aborted payment has its committed steps reversed and
     * the session settles retryable — the basket stays intact and Pay may
     * run again; an aborted prompt or card read is simply cancelled. An
     * abort that lands after the payment completed leaves the transaction
     * standing.
     */
    fun abort()

    /** Dismiss the payment outcome popup. */
    fun dismissPaymentOutcome()
}
