package com.bilt.pos.emulator.session

import com.bilt.pos.emulator.catalog.Product
import com.bilt.pos.nexo.client.BiltNexoTerminalClient
import com.bilt.pos.nexo.model.DiagnosisRequest
import com.bilt.pos.nexo.model.MessageCategoryType
import com.bilt.pos.nexo.model.MessageClassType
import com.bilt.pos.nexo.model.MessageHeader
import com.bilt.pos.nexo.model.MessageTypeType
import com.bilt.pos.nexo.model.NexoTerminalAPI
import com.bilt.pos.nexo.model.SaleToPOIRequest
import com.bilt.pos.nexo.security.SecurityKey
import com.bilt.pos.session.CheckoutSession
import com.bilt.pos.session.basket.Basket
import com.bilt.pos.session.basket.BasketItem
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The emulator session engine. A connection to the terminal and a checkout
 * session are separate lifecycles:
 *
 * - **Connect** builds the client and runs the periodic diagnostics loop
 *   (raw nexo Diagnosis requests — pure connectivity, no session involved).
 * - **Start Session** opens a [CheckoutSession] (terminal Start bracket) on
 *   that connection: one session per customer checkout. **End Session**
 *   closes it (End bracket). Disconnect ends any active session best-effort.
 *
 * Each connection is a self-contained [Connection]: its own scope (child of
 * the app scope, cancelled wholesale on disconnect), its own single-threaded
 * dispatcher (SDK calls are serialized per connection — the session is a
 * stateful machine — but a reconnect never queues behind the previous
 * connection's in-flight blocking call), the client, and the session.
 *
 * Because the SDK calls block rather than suspend, cancellation alone can't
 * stop one mid-call — so each job re-checks that it is still active after a
 * blocking call returns, before touching [state].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NexoEmulatorController(
    private val scope: CoroutineScope,
    private val config: EmulatorConfig = EmulatorConfig.load(),
    private val diagnosticsInterval: Duration = 60.seconds,
) : EmulatorController {

    private class Connection(
        val scope: CoroutineScope,
        val dispatcher: CoroutineDispatcher,
        @Volatile var client: BiltNexoTerminalClient? = null,
        @Volatile var session: CheckoutSession? = null,
    )

    /**
     * Runs End brackets during teardown. Deliberately NOT a child of [scope]:
     * a ViewModel clearing its scope right after disconnect() must not kill
     * the in-flight best-effort end signal.
     */
    private val teardownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(
        EmulatorState(
            encryptionEnabled = config.encryptionEnabled,
            hasConfiguredPassphrase = config.encryptionEnabled,
        )
    )
    override val state: StateFlow<EmulatorState> = _state.asStateFlow()

    @Volatile
    private var connection: Connection? = null

    init {
        // SDK diagnostics (client, session, payment internals) log via JUL;
        // surface them on the Detailed tab
        JulLogCapture.install(::detailedLog)
    }

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

        val conn = Connection(
            scope = CoroutineScope(
                scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job])
            ),
            dispatcher = Dispatchers.IO.limitedParallelism(1),
        )
        connection = conn

        conn.scope.launch(conn.dispatcher) {
            val client = try {
                // Payload channel: always permissive, so a failing certificate
                // is reported (below) but never blocks terminal communication.
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
                clientBuilder.build()
            } catch (e: Exception) {
                // e.g. an unparsable address making the endpoint URL invalid —
                // without this the job dies silently and the chip stays on Connecting
                if (isActive) {
                    _state.update {
                        it.copy(
                            connection = ConnectionStatus(
                                ConnectionPhase.ERROR,
                                e.message ?: "client setup failed",
                            )
                        )
                    }
                    log("Failed to set up the client: ${e.message}")
                    detailedLog(e.stackTraceToString())
                }
                return@launch
            }
            conn.client = client

            while (isActive) {
                runDiagnosis(client)
                delay(diagnosticsInterval)
            }
        }

        // Strict verification probe, independent of the payload channel. A CA
        // that was configured but failed to load reports as a TLS failure —
        // not as "no CA configured", which would mask the misconfiguration.
        conn.scope.launch(Dispatchers.IO) {
            val tls = config.caError?.let { TlsStatus.Failed(it) }
                ?: TlsVerifier.verify(address, 8443, config.caPem, config.hostnamePattern)
            if (isActive) {
                _state.update { it.copy(tls = tls) }
                log(tls.label)
            }
        }
    }

    override fun disconnect() {
        val conn = connection ?: return updateDisconnectedState()
        connection = null
        conn.scope.cancel()
        val active = conn.session
        conn.session = null
        if (active != null) {
            // Session End bracket, best-effort; teardownScope so a ViewModel
            // cancelling the app scope right after doesn't kill it
            teardownScope.launch {
                runCatching { active.close() }
            }
        }
        log("Disconnected")
        updateDisconnectedState()
    }

    /**
     * Blocking best-effort teardown for process-exit paths (desktop window
     * close): ends the active session synchronously so the End bracket isn't
     * lost to the exiting process. Bounded by the client's timeouts plus a
     * join cap.
     */
    fun shutdown() {
        val conn = connection ?: return
        connection = null
        conn.scope.cancel()
        // Wait (bounded) for the serialized jobs to settle before reading the
        // session: an in-flight start() may have already succeeded on the
        // terminal without conn.session being set yet. After the join it has
        // either installed the session (closed below) or taken the cancelled
        // path and closed its own orphan — both need the process alive.
        runBlocking {
            withTimeoutOrNull(10_000) { conn.scope.coroutineContext[Job]?.join() }
        }
        val active = conn.session ?: return
        conn.session = null
        runCatching { active.close() }
    }

    override fun startSession() {
        val conn = connection
        val client = conn?.client
        if (conn == null || client == null) {
            log("Not connected — connect before starting a session")
            return
        }
        conn.scope.launch(conn.dispatcher) {
            // The authoritative already-active check lives INSIDE the
            // serialized job: a second quick press queues behind the first
            // start() and sees the installed session — checking only before
            // launch would let both presses start (and orphan) a terminal
            // session, since conn.session stays null until start() returns.
            if (conn.session != null) {
                log("A checkout session is already active — end it first")
                return@launch
            }
            // Visible before the roundtrip: a terminal that never answers the
            // Start bracket blocks here for the client's read timeout (120s
            // default), and the serialized dispatcher queues diagnostics
            // behind it — without this line the UI is silent the whole time
            log("Starting checkout session (Start bracket)…")
            try {
                val started = CheckoutSession.builder()
                    .client(client)
                    .saleId(config.saleId)
                    .poiId(config.poiId)
                    .currency(config.currency)
                    .start()
                    .get()
                if (!currentCoroutineContext().isActive) {
                    // disconnected while starting; end the orphaned session
                    runCatching { started.close() }
                    return@launch
                }
                conn.session = started
                _state.update {
                    it.copy(
                        sessionId = started.sessionId,
                        basket = emptyList(),
                        basketTotal = "0.00",
                        basketTax = "0.00",
                    )
                }
                log("Checkout session started (id ${started.sessionId})")
            } catch (e: Exception) {
                if (currentCoroutineContext().isActive) {
                    log("Failed to start a checkout session: ${e.message}")
                    detailedLog(e.stackTraceToString())
                }
            }
        }
    }

    override fun endSession() {
        val conn = connection
        if (conn == null) {
            log("No active checkout session")
            return
        }
        conn.scope.launch(conn.dispatcher) {
            // Read inside the serialized job so it can't interleave with an
            // in-flight startSession installing the session
            val active = conn.session
            if (active == null) {
                log("No active checkout session")
                return@launch
            }
            log("Ending checkout session (End bracket)…")
            try {
                active.end().get()
                // Clear only on a successful End bracket — a failed end keeps
                // the session held and retryable (the SDK doesn't seal it),
                // instead of orphaning terminal session state with the UI
                // offering Start Session. Disconnect remains the escape hatch.
                conn.session = null
                if (currentCoroutineContext().isActive) {
                    _state.update {
                        it.copy(
                            sessionId = null,
                            basket = emptyList(),
                            basketTotal = "0.00",
                            basketTax = "0.00",
                        )
                    }
                    log("Checkout session ended")
                }
            } catch (e: Exception) {
                if (currentCoroutineContext().isActive) {
                    log("Failed to end the session: ${e.message} — still active, retry or disconnect")
                    detailedLog(e.stackTraceToString())
                }
            }
        }
    }

    override fun addProduct(product: Product) {
        val conn = connection
        if (conn == null) {
            log("No active checkout session — press Start Session first")
            return
        }
        conn.scope.launch(conn.dispatcher) {
            // Read inside the serialized job (same as start/endSession) so an
            // End-then-tap sequence can't ring an item into a session already
            // cleared for its End bracket
            val current = conn.session
            if (current == null) {
                log("No active checkout session — press Start Session first")
                return@launch
            }
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
                    detailedLog(e.stackTraceToString())
                }
            }
        }
    }

    /** Connectivity probe: a raw nexo Diagnosis request, no session involved. */
    private suspend fun runDiagnosis(client: BiltNexoTerminalClient) {
        val previous = _state.value.connection.phase
        try {
            val request = NexoTerminalAPI.builder()
                .saleToPOIRequest(
                    SaleToPOIRequest.builder()
                        .messageHeader(
                            MessageHeader.builder()
                                .protocolVersion("3.0")
                                .messageClass(MessageClassType.SERVICE)
                                .messageCategory(MessageCategoryType.DIAGNOSIS)
                                .messageType(MessageTypeType.REQUEST)
                                .serviceID(UUID.randomUUID().toString().substring(0, 8))
                                .saleID(config.saleId)
                                .poiid(config.poiId)
                                .build()
                        )
                        .diagnosisRequest(DiagnosisRequest.builder().build())
                        .build()
                )
                .build()
            val response = client.request(request)
            if (!currentCoroutineContext().isActive) {
                return // disconnected while the request was in flight
            }
            val poi = response?.saleToPOIResponse?.diagnosisResponse
                ?.poiStatus?.globalStatus?.toString() ?: "OK"
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
            detailedLog(e.stackTraceToString())
        }
    }

    private fun updateDisconnectedState() {
        _state.update {
            it.copy(
                connection = ConnectionStatus(ConnectionPhase.DISCONNECTED),
                tls = TlsStatus.Unknown, // per-connection fact; stale FAILED would outlive it
                sessionId = null,
                basket = emptyList(),
                basketTotal = "0.00",
                basketTax = "0.00",
            )
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
        val stamped = "${timestamp()} $message"
        _state.update {
            it.copy(
                events = (it.events + stamped).takeLast(200),
                // events echo into the detailed stream so it reads as one timeline
                detailedEvents = (it.detailedEvents + stamped).takeLast(500),
            )
        }
    }

    private fun detailedLog(message: String) {
        val stamped = "${timestamp()} $message"
        _state.update { it.copy(detailedEvents = (it.detailedEvents + stamped).takeLast(500)) }
    }

    private fun timestamp(): String =
        java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
}
