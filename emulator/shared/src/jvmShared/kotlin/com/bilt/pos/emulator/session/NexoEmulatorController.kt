package com.bilt.pos.emulator.session

import com.bilt.pos.emulator.catalog.Product
import com.bilt.pos.nexo.client.BiltNexoTerminalClient
import com.bilt.pos.nexo.security.SecurityKey
import com.bilt.pos.session.CheckoutSession
import com.bilt.pos.session.basket.Basket
import com.bilt.pos.session.basket.BasketItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.math.BigDecimal
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The emulator session engine: owns the terminal client and
 * [CheckoutSession], runs the periodic diagnostics loop, and projects
 * everything into [EmulatorState] for the UI.
 *
 * All SDK calls run on a single-threaded dispatcher — the session is a
 * stateful machine and the emulator has no need for concurrent operations.
 *
 * Every coroutine belonging to a connection (diagnostics, TLS probe, basket
 * work) runs in a per-connection scope that [disconnect] cancels wholesale.
 * Because the SDK calls block rather than suspend, cancellation alone can't
 * stop one mid-call — so each job re-checks that it is still the live
 * connection after a blocking call returns, before touching [state].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NexoEmulatorController(
    private val scope: CoroutineScope,
    private val config: EmulatorConfig = EmulatorConfig.load(),
    private val diagnosticsInterval: Duration = 60.seconds,
) : EmulatorController {

    private val sessionDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val _state = MutableStateFlow(
        EmulatorState(
            encryptionEnabled = config.encryptionEnabled,
            hasConfiguredPassphrase = config.encryptionEnabled,
        )
    )
    override val state: StateFlow<EmulatorState> = _state.asStateFlow()

    /** Scope owning all coroutines of the active connection; child of [scope]. */
    @Volatile
    private var connection: CoroutineScope? = null

    @Volatile
    private var session: CheckoutSession? = null

    override fun autodetectAddress() {
        scope.launch(Dispatchers.IO) {
            val detected = TerminalAddressDetector.detect()
            if (detected != null) {
                _state.update { it.copy(terminalAddress = detected, addressAutodetected = true) }
                log("Autodetected terminal address $detected via adb")
            } else {
                log("Address autodetect found no adb device; enter the address manually")
            }
        }
    }

    override fun connect(address: String, encryptionEnabled: Boolean, passphraseOverride: String?) {
        disconnect()

        val passphrase = passphraseOverride?.takeIf { it.isNotBlank() } ?: config.passphrase
        val encrypt = encryptionEnabled && !passphrase.isNullOrBlank()
        if (encryptionEnabled && !encrypt) {
            log("Encryption requested but no passphrase available (enter one or set NEXO_PASSPHRASE) — connecting unencrypted")
        }

        _state.update {
            it.copy(
                terminalAddress = address,
                connection = ConnectionStatus(ConnectionPhase.CONNECTING),
                tls = TlsStatus.Unknown,
                encryptionEnabled = encrypt,
            )
        }
        val endpoint = "https://$address:8443/nexo"
        log("Connecting to $endpoint (encryption=$encrypt)")

        val conn = CoroutineScope(
            scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job])
        )
        connection = conn

        conn.launch(sessionDispatcher) {
            // Payload channel: always permissive, so a failing certificate is
            // reported (below) but never blocks terminal communication.
            val clientBuilder = BiltNexoTerminalClient.builder()
                .endpoint(endpoint)
                .trustAllCertificates()
            if (encrypt) {
                clientBuilder.securityKey(
                    SecurityKey.builder()
                        .passphrase(passphrase)
                        .keyIdentifier(config.keyId)
                        .keyVersion(config.keyVersion)
                        .build()
                )
            }
            val built = CheckoutSession.builder()
                .client(clientBuilder.build())
                .saleId(config.saleId)
                .poiId(config.poiId)
                .currency(config.currency)
                .build()
            if (!isActive) {
                return@launch // disconnected while building; don't install the session
            }
            session = built

            while (isActive) {
                runDiagnosis(built)
                delay(diagnosticsInterval)
            }
        }

        // Strict verification probe, independent of the payload channel.
        conn.launch(Dispatchers.IO) {
            val tls = TlsVerifier.verify(address, 8443, config.caPem, config.hostnamePattern)
            if (isActive) {
                _state.update { it.copy(tls = tls) }
                log(tls.label)
            }
        }
    }

    override fun disconnect() {
        connection?.cancel()
        connection = null
        if (session != null) {
            session = null
            log("Disconnected")
        }
        _state.update {
            it.copy(
                connection = ConnectionStatus(ConnectionPhase.DISCONNECTED),
                basket = emptyList(),
                basketTotal = "0.00",
                basketTax = "0.00",
            )
        }
    }

    override fun addProduct(product: Product) {
        val conn = connection
        val current = session
        if (conn == null || current == null) {
            log("Not connected — connect before adding items")
            return
        }
        conn.launch(sessionDispatcher) {
            try {
                val existing = current.basket.getItemBySku(product.sku)
                val basket = if (existing == null) {
                    val item = BasketItem.builder()
                        .sku(product.sku)
                        .description(product.name)
                        .category(product.category)
                        .quantity(1)
                        .unitPrice(BigDecimal(product.priceDecimal))
                        .apply { NjSalesTax.rateFor(product)?.let(::taxRate) }
                        .build()
                    current.addItem(item)
                } else {
                    current.updateItemQuantityBySku(product.sku, existing.quantity + 1)
                }
                if (isActive) {
                    publishBasket(basket)
                    log("Added ${product.name} (${product.priceLabel})")
                }
            } catch (e: Exception) {
                if (isActive) {
                    log("Failed to add ${product.name}: ${e.message}")
                }
            }
        }
    }

    private suspend fun runDiagnosis(current: CheckoutSession) {
        val previous = _state.value.connection.phase
        try {
            val result = current.diagnose().get()
            if (!currentCoroutineContext().isActive) {
                return // disconnected while the request was in flight
            }
            val poi = result.poiStatus?.globalStatus?.toString() ?: "OK"
            _state.update {
                it.copy(connection = ConnectionStatus(ConnectionPhase.CONNECTED, "POI $poi"))
            }
            if (previous != ConnectionPhase.CONNECTED) {
                log("Terminal connected (POI status: $poi)")
            }
        } catch (e: Exception) {
            if (!currentCoroutineContext().isActive) {
                return
            }
            _state.update {
                it.copy(
                    connection = ConnectionStatus(
                        ConnectionPhase.ERROR,
                        e.message ?: "diagnosis failed",
                    )
                )
            }
            if (previous == ConnectionPhase.CONNECTED || previous == ConnectionPhase.CONNECTING) {
                log("Terminal unreachable: ${e.message}")
            }
        }
    }

    private fun publishBasket(basket: Basket) {
        _state.update { state ->
            state.copy(
                basket = basket.items.map { line ->
                    BasketLine(
                        sku = line.sku,
                        description = line.description,
                        quantity = line.quantity,
                        lineTotal = line.adjustedTotal.toPlainString(),
                    )
                },
                basketTotal = basket.grandTotal.toPlainString(),
                basketTax = basket.taxTotal.toPlainString(),
            )
        }
    }

    private fun log(message: String) {
        _state.update { it.copy(events = (it.events + message).takeLast(200)) }
    }
}
