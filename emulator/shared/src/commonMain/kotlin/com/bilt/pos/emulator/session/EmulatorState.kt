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
)

/** Outcome of the last payment attempt, shown as a popup until dismissed. */
data class PaymentOutcome(
    val success: Boolean,
    val message: String,
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
    val quantity: Int,
    /** Line total in minor currency units (cents), so per-item refund
     *  selections sum as Longs instead of decimal-string arithmetic. */
    val lineTotalMinor: Long,
) {
    val lineTotalLabel: String get() = "$" + minorUnitsToDecimal(lineTotalMinor)
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
    val voided: Boolean = false,
) {
    /** A voided sale cannot be refunded (mirrors `StoredSale.refundable`). */
    val refundable: Boolean get() = !voided

    val memberLabel: String get() = memberId?.let { "member $it" } ?: "guest"

    /** One-line badge for the sales list: buyer plus what already happened
     *  to the sale. */
    val statusLabel: String
        get() = listOfNotNull(
            memberLabel,
            when {
                voided -> "voided"
                refunded -> "refunded"
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
    /** One-line summary of the checkout's completed payment; null until
     *  paid. A fully collected payment ends the checkout automatically; the
     *  summary stays visible until the next one starts. */
    val lastPayment: String? = null,
    /** Success/failure of the last payment attempt, rendered as a popup
     *  until dismissed; cleared when a new payment starts. */
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
     */
    fun connect(address: String, encryptionEnabled: Boolean, passphraseOverride: String? = null)

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
     * Run settlement on the active session. [loyalty] picks which loyalty
     * steps run; [storedValue] adds a gift card as the first tender —
     * anything it doesn't cover falls through to the standard card payment.
     */
    fun settle(loyalty: LoyaltyOptions, storedValue: StoredValueOptions? = null)

    /**
     * Read a card on the terminal (nexo CardAcquisition request) without
     * charging it. A read that returns a full card number is published as
     * [EmulatorState.acquiredCard] so the gift card field can adopt it; a
     * masked-only read is just logged.
     */
    fun acquireCard()

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
