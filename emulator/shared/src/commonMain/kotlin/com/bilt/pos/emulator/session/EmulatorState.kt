package com.bilt.pos.emulator.session

import com.bilt.pos.emulator.catalog.Product
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
    val basket: List<BasketLine> = emptyList(),
    val basketTotal: String = "0.00",
    val events: List<String> = emptyList(),
)

/**
 * The UI's handle on the emulator, kept SDK-free so commonMain can depend on
 * it; the implementation lives in jvmShared where the Java SDK is available.
 */
interface EmulatorController {
    val state: StateFlow<EmulatorState>

    /** Try to prefill the terminal address (adb-based on desktop). */
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

    /** Ring up one unit of [product] on the active session. */
    fun addProduct(product: Product)
}
