package com.bilt.pos.emulator.session

import com.bilt.pos.emulator.catalog.Product
import com.bilt.pos.emulator.store.LegType
import com.bilt.pos.emulator.store.RefundRecord
import com.bilt.pos.emulator.store.RefundedItem
import com.bilt.pos.emulator.store.SaleItem
import com.bilt.pos.emulator.store.SaleRecord
import com.bilt.pos.emulator.store.SaleStore
import com.bilt.pos.emulator.store.StoredSale
import com.bilt.pos.emulator.store.TransactionLeg
import com.bilt.pos.emulator.store.toSaleRecord
import com.bilt.pos.nexo.client.BiltNexoTerminalClient
import com.bilt.pos.nexo.security.SecurityKey
import com.bilt.pos.session.CheckoutSession
import com.bilt.pos.session.Receipt
import com.bilt.pos.session.ReversalDecision
import com.bilt.pos.session.ReversalStep
import com.bilt.pos.session.SessionError
import com.bilt.pos.session.SessionErrorCode
import com.bilt.pos.session.SessionException
import com.bilt.pos.session.Terminal
import com.bilt.pos.session.basket.Basket
import com.bilt.pos.session.basket.BasketItem
import com.bilt.pos.session.basket.BasketItemType
import com.bilt.pos.session.identity.CardAcquisitionOptions
import com.bilt.pos.session.identity.ForceEntryMode
import com.bilt.pos.session.identity.IdentifyOptions
import com.bilt.pos.session.identity.IdentifyStatus
import com.bilt.pos.session.settlement.OriginalSaleRecord
import com.bilt.pos.session.settlement.RefundAllocation
import com.bilt.pos.session.settlement.SettlementOptions
import com.bilt.pos.session.settlement.SettlementRecovery
import com.bilt.pos.session.settlement.SettlementResult
import com.bilt.pos.session.settlement.SettlementStep
import com.bilt.pos.session.settlement.SettlementType
import com.bilt.pos.session.storedvalue.StoredValueCard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Top-level, not an instance property: initialized on class load, before
 *  the constructor-launched sales refresh can format labels (instance
 *  initializers only run in textual order). */
private val saleTimeFormat =
    DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault())

/**
 * The emulator session engine. A connection to the terminal and a checkout
 * session are separate lifecycles:
 *
 * - **Connect** builds the client and a device-level [Terminal] handle, and
 *   runs the periodic diagnostics loop on it (pure connectivity — no
 *   session involved, and none required).
 * - **Start Checkout** opens a [CheckoutSession] (terminal Start bracket) on
 *   that connection: one session per customer checkout. **End Checkout**
 *   closes it (End bracket). Disconnect ends any active session best-effort.
 *
 * Session operations run through the SDK's asynchronous `execute()`:
 * outcomes arrive via `onSuccess`/`onError` on [callbackExecutor], and
 * `onComplete` releases claims and busy flags on every completion path. A
 * callback that lands after a disconnect recognizes it by
 * `connection !== conn` and skips the UI updates; facts that must survive
 * the disconnect (a charged sale, the claim release) stay ungated.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NexoEmulatorController(
    private val scope: CoroutineScope,
    private val config: EmulatorConfig = EmulatorConfig.load(),
    private val diagnosticsInterval: Duration = 60.seconds,
    /** Persists completed sales for later referenced refunds/voids. */
    private val saleStore: SaleStore,
    /**
     * The UI-thread executor session handlers deliver on (Android main
     * executor, AWT event dispatch on desktop; headless harnesses pass a
     * serial stand-in). Handlers may touch UI directly; sale persistence
     * is handed off to an IO coroutine. Must never be blocked on a session
     * call outside a handler.
     */
    private val callbackExecutor: Executor,
) : EmulatorController {

    /** A payment attempt's settled outcome; [pay]'s onComplete reads
     *  RUNNING as aborted (no outcome handler fired). */
    private enum class PaymentAttempt { RUNNING, SUCCEEDED, FAILED }

    /** One return rung into the basket: which sale it came from, the
     *  references its refund allocation presents, the tender it restores
     *  to, the returned quantities, and the allocation amount (shelf price
     *  plus tax of the returned lines). */
    private class PendingReturn(
        val saleId: String,
        val original: OriginalSaleRecord,
        val legType: LegType,
        val items: List<SaleItem>,
        val amount: BigDecimal,
        /** What the tender leg can still return (collected minus already
         *  refunded), captured at ring time; null when unrecorded. A netted
         *  sale collected less than its items' shelf value, and the
         *  terminal must not be asked for more than the transaction took. */
        val legCapacity: BigDecimal?,
    )

    /** One returned sale's plan within a settlement: its [total] return
     *  value, how much goes back to the tender as a refund allocation
     *  ([allocated], capped by what the tender collected), how much the
     *  register pays out itself ([external] — the overflow past that cap),
     *  and the rest is netted against the charge. */
    private class PlannedReturn(
        val saleId: String,
        val original: OriginalSaleRecord,
        val legType: LegType,
        val items: List<SaleItem>,
        val total: BigDecimal,
        val allocated: BigDecimal,
        val external: BigDecimal,
    )

    private class Connection(
        val scope: CoroutineScope,
        val dispatcher: CoroutineDispatcher,
        val client: BiltNexoTerminalClient,
        val terminal: Terminal,
        /** The adb forward this connection runs through; null for a direct
         *  connection. Removed from the adb server on teardown. */
        val tunnel: AdbTunnel.Tunnel? = null,
        @Volatile var session: CheckoutSession? = null,
        /** The fresh checkout session a referenced refund is running on, so
         *  [abort] can reach a refund the way it reaches a payment; null
         *  outside a refund. */
        @Volatile var refundSession: CheckoutSession? = null,
    ) {
        /** Returns rung into the active checkout's basket, awaiting the
         *  settlement that restores their value to the original sales'
         *  tenders. Copy-on-write: mutations happen under the operation
         *  claim, reads (the sales projection) may race and need a
         *  consistent snapshot. Cleared with the checkout. */
        @Volatile var pendingReturns: List<PendingReturn> = emptyList()

        /** Set by [abort] for the current refund attempt. It covers the
         *  windows the SDK's abort cannot: during the store lookup, the
         *  reversal session's start roundtrip, and between the legs of a
         *  split tender nothing abortable is on the wire — the refund job
         *  checks this flag before every money movement. Cleared when the
         *  next refund attempt is claimed. */
        val refundAbortRequested = AtomicBoolean(false)

        /** The in-flight refund/return job, so [shutdown] can wait for its
         *  store write — it runs on the job's own thread, and money that
         *  moved on the terminal must not exit unrecorded. */
        @Volatile var refundJob: kotlinx.coroutines.Job? = null

        /** Guards against a double-tap bracketing two terminal sessions:
         *  [session] is only installed once the Start roundtrip acknowledges. */
        val startClaimed = AtomicBoolean(false)

        /** One claim across [pay] and [acquireCard]: the UI's disabled
         *  states publish only on recomposition, so a quick second tap
         *  would otherwise queue its prompt behind the in-flight operation.
         *  Released by each operation's `onComplete`. */
        val operationClaimed = AtomicBoolean(false)

    }

    /**
     * Sale writes happen here, off the UI callback thread — and NOT as a
     * child of [scope]: teardown must not kill the record of a charged
     * transaction. [shutdown] joins it before process exit.
     */
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(
        EmulatorState(
            encryptionEnabled = config.encryptionEnabled,
            hasConfiguredPassphrase = config.encryptionEnabled,
        )
    )
    override val state: StateFlow<EmulatorState> = _state.asStateFlow()

    @Volatile
    private var connection: Connection? = null

    /** Bumped by every teardown, captured by connect(): the tunnel comes up
     *  on an IO coroutine, and its continuation must not install a
     *  connection the operator has since disconnected or replaced. Bumps
     *  and installs happen under [connectionLock], so a teardown between an
     *  epoch check and the install cannot slip through. */
    private val connectEpoch = java.util.concurrent.atomic.AtomicInteger()

    /** Linearizes [connection] installs against teardowns — held only for
     *  the field mutation and epoch check, never across I/O. */
    private val connectionLock = Any()

    /** Serial, so overlapping refreshes publish in launch order and the
     *  newest sale wins. Declared before the init block, which triggers
     *  refreshSales(). */
    private val salesRefreshDispatcher = Dispatchers.IO.limitedParallelism(1)

    init {
        // SDK internals log via JUL; surface them on the Detailed tab —
        // and warnings on the curated Events feed too, first line only: an
        // operator watching Events must not miss a WARNING that scrolled
        // past among Detailed's FINE-level protocol chatter
        JulLogCapture.install { message ->
            detailedLog(message)
            if (message.startsWith("WARNING") || message.startsWith("SEVERE")) {
                log("SDK ${message.lineSequence().first()}")
            }
        }
        // recordSale keeps the list current from here on
        refreshSales()
    }

    override fun autodetectAddress() {
        scope.launch(Dispatchers.IO) {
            val detected = TerminalAddressDetector.detect()
            when {
                detected == null ->
                    log("Address autodetect found no adb device; enter the address manually")
                detected.address != null -> {
                    _state.update {
                        it.copy(terminalAddress = detected.address, addressAutodetected = true)
                    }
                    log("Autodetected terminal address ${detected.address} via adb")
                    // Auto-connect only from DISCONNECTED: never tear down a
                    // connection the operator made while adb was probing
                    if (_state.value.connection.phase == ConnectionPhase.DISCONNECTED) {
                        log("Connecting automatically to the detected terminal")
                        connect(detected.address, _state.value.encryptionEnabled, null)
                    }
                }
                else -> {
                    // A USB-only terminal: nothing to dial, but the adb
                    // tunnel reaches it — prefill the serial (the tunnel
                    // matches devices by it) and leave connecting to the
                    // operator, who must tick the tunnel first
                    _state.update {
                        it.copy(terminalAddress = detected.serial, addressAutodetected = true)
                    }
                    log(
                        "Terminal ${detected.serial} is attached via adb but has no " +
                            "network address — tick \"adb tunnel\" and Connect"
                    )
                }
            }
        }
    }

    override fun connect(
        address: String,
        encryptionEnabled: Boolean,
        passphraseOverride: String?,
        adbTunnel: Boolean,
    ) {
        if (!tryDisconnect()) {
            return
        }
        val epoch = connectEpoch.get()

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

        if (!adbTunnel) {
            openConnection(host = address, port = 8443, tunnel = null,
                encrypt = encrypt, passphrase = passphrase, epoch = epoch)
            return
        }
        // The forward comes up on an IO coroutine — adb is a subprocess and
        // may first have to start its server; connect() runs on the UI thread
        log("Opening adb tunnel to ${address.ifBlank { "the attached device" }} (device port 8443)…")
        scope.launch(Dispatchers.IO) {
            val tunnel = try {
                AdbTunnel.open(address)
            } catch (e: Exception) {
                if (connectEpoch.get() == epoch) {
                    _state.update {
                        it.copy(connection = ConnectionStatus(
                            ConnectionPhase.ERROR,
                            e.message ?: "adb tunnel failed",
                        ))
                    }
                    log("adb tunnel failed: ${e.message}")
                }
                return@launch
            }
            // disconnected or reconnected while the forward was coming up —
            // this connect attempt is stale, remove its forward again. Just
            // an early exit; the authoritative check is the locked one at
            // install time.
            if (connectEpoch.get() != epoch) {
                AdbTunnel.close(tunnel)
                return@launch
            }
            log("adb tunnel up — localhost:${tunnel.localPort} → ${tunnel.serial} port 8443")
            openConnection(host = "127.0.0.1", port = tunnel.localPort, tunnel = tunnel,
                encrypt = encrypt, passphrase = passphrase, epoch = epoch)
        }
    }

    /** Builds the client and installs the connection against
     *  `https://[host]:[port]/nexo` — the terminal itself, or the local end
     *  of [tunnel]. The install is atomic with the [epoch] check: an
     *  attempt a teardown has since invalidated dismantles what it built
     *  instead of installing a connection the operator dismissed. */
    private fun openConnection(
        host: String,
        port: Int,
        tunnel: AdbTunnel.Tunnel?,
        encrypt: Boolean,
        passphrase: String?,
        epoch: Int,
    ) {
        val endpoint = "https://$host:$port/nexo"
        log("Connecting to $endpoint (encryption=$encrypt)")

        val client = createNexoClient(endpoint, encrypt, passphrase, epoch) ?: run {
            tunnel?.let { t -> scope.launch(Dispatchers.IO) { AdbTunnel.close(t) } }
            return
        }
        // The device-level handle: diagnose() is session-free, so the
        // connectivity loop needs no checkout session — just the terminal
        val terminal = Terminal.builder()
            .client(client)
            .saleId(config.saleId)
            .poiId(config.poiId)
            .callbackExecutor(callbackExecutor)
            .build()
        val conn = Connection(
            scope = CoroutineScope(
                scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job])
            ),
            dispatcher = Dispatchers.IO.limitedParallelism(1),
            client = client,
            terminal = terminal,
            tunnel = tunnel,
        )
        val installed = synchronized(connectionLock) {
            (connectEpoch.get() == epoch).also { current ->
                if (current) {
                    connection = conn
                }
            }
        }
        if (!installed) {
            // torn down (or replaced) while the client was being built:
            // nothing launched yet, just release what this attempt created
            conn.scope.cancel()
            terminal.close()
            tunnel?.let { AdbTunnel.close(it) }
            return
        }

        runDiagnosisLoop(conn, terminal)
        // Probed at the endpoint actually used: through a tunnel the direct
        // route is exactly what macOS blocks for this process. The SAN
        // pattern check is unaffected — it reads the certificate, not the
        // address dialed.
        verifyTls(conn, host, port)
    }

    /** Strict TLS probe, independent of the always-permissive payload
     *  channel. A CA that was configured but failed to load reports as a
     *  TLS failure, not as "no CA configured". */
    private fun verifyTls(conn: Connection, host: String, port: Int) {
        conn.scope.launch(Dispatchers.IO) {
            val tls = config.caError?.let { TlsStatus.Failed(it) }
                ?: TlsVerifier.verify(host, port, config.caPem, config.hostnamePattern)
            if (isActive) {
                _state.update { it.copy(tls = tls) }
                log(tls.label)
            }
        }
    }

    private fun createNexoClient(
        endpoint: String,
        encrypt: Boolean,
        passphrase: String?,
        epoch: Int,
    ): BiltNexoTerminalClient? {
        return try {
            // Always permissive: a failing certificate is reported by the
            // TLS probe but never blocks terminal communication
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
            // e.g. an unparsable address; reported instead of thrown — but
            // only while this attempt is still the current one, so a stale
            // tunnel connect can't stamp ERROR over a newer attempt's state
            if (connectEpoch.get() == epoch) {
                _state.update {
                    it.copy(
                        connection = ConnectionStatus(
                            ConnectionPhase.ERROR,
                            e.message ?: "client setup failed",
                        )
                    )
                }
            }
            log("Failed to set up the client: ${e.message}")
            detailedLog(e.stackTraceToString())
            null
        }
    }

    override fun disconnect() {
        tryDisconnect()
    }

    /** Tears the connection down; returns false when the disconnect was
     *  refused because a payment is in flight — unlike end(), which queues
     *  behind the payment on the session's operation thread, a disconnect
     *  tears down immediately and would free the register while the charge
     *  is still landing. */
    private fun tryDisconnect(): Boolean {
        if (_state.value.paymentInProgress) {
            log("A payment is in progress — abort it or wait for it to finish before disconnecting")
            return false
        }
        if (_state.value.refundInProgress) {
            log("A refund is in progress — wait for it to finish before disconnecting")
            return false
        }
        // Bump and take under the lock, atomically with any concurrent
        // install — and bump even with nothing installed: a tunnel may be
        // coming up, and it must see this teardown
        val conn = synchronized(connectionLock) {
            connectEpoch.incrementAndGet()
            connection.also { connection = null }
        } ?: run {
            updateDisconnectedState()
            return true
        }
        val hadSession = conn.session != null
        conn.scope.cancel()
        conn.terminal.close()
        // End bracket, best-effort: the async end() queues behind anything
        // still in flight on the session's operation thread
        conn.session?.end()?.onError { error ->
            detailedLog("End bracket on disconnect failed: ${error.message}")
        }?.execute()
        conn.tunnel?.let { tunnel ->
            scope.launch(Dispatchers.IO) {
                // the End bracket above still crosses this forward — give it
                // a grace before pulling the road out from under it
                if (hadSession) {
                    delay(10.seconds)
                }
                AdbTunnel.close(tunnel)
                log("adb tunnel closed (localhost:${tunnel.localPort})")
            }
        }
        log("Disconnected")
        updateDisconnectedState()
        return true
    }

    /**
     * Blocking teardown for process-exit paths (desktop window close): the
     * session's operation threads are daemons, so an asynchronous end would
     * race the exit. close() queues behind anything in flight, bounded by
     * the client's timeouts.
     */
    fun shutdown() {
        val conn = synchronized(connectionLock) {
            connectEpoch.incrementAndGet()
            connection.also { connection = null }
        }
        if (conn != null) {
            // A refund may be mid-void with money already reversed on the
            // terminal; its store write runs on the refund job's own thread
            // (unlike sale writes, which hop to persistenceScope), so the
            // exit must wait it out — bounded, a hung terminal must not
            // wedge the window close. Before the tunnel teardown: the
            // refund's wire traffic may cross the forward.
            runBlocking {
                withTimeoutOrNull(15_000) { conn.refundJob?.join() }
            }
            conn.scope.cancel()
            conn.terminal.close()
            conn.session?.let { runCatching { it.close() } }
            // after the blocking session close — its End bracket crossed the
            // forward; an unremoved forward would outlive the process in the
            // adb server
            conn.tunnel?.let { AdbTunnel.close(it) }
        }
        // a just-landed sale may still be writing; it must reach disk
        runBlocking {
            withTimeoutOrNull(10_000) {
                persistenceScope.coroutineContext[Job]?.children?.forEach { it.join() }
            }
        }
    }

    override fun startSession(identifyOnStart: Boolean) {
        val conn = connection ?: run {
            log("Not connected — connect before starting a session")
            return
        }
        if (conn.session != null) {
            log("A checkout session is already active — end it first")
            return
        }
        // the reversal session brackets the terminal too — refundSale's
        // mirror guard refuses while a checkout is active or starting
        if (_state.value.refundInProgress) {
            log("A refund is in progress — wait for it to finish")
            return
        }
        if (!conn.startClaimed.compareAndSet(false, true)) {
            log("A checkout session is already being started")
            return
        }
        // Logged before the roundtrip: an unresponsive terminal blocks the
        // Start bracket for the read timeout, and the UI would be silent
        log("Starting checkout session (Start bracket)…")
        CheckoutSession.builder()
            .client(conn.client)
            .saleId(config.saleId)
            .poiId(config.poiId)
            .currency(config.currency)
            .callbackExecutor(callbackExecutor)
            // auto-display pushes fail out-of-band (no result to hold);
            // surface them like any other event
            .onBackgroundError { error ->
                log("Customer display update failed: ${error.message}")
            }
            .start()
            .onSuccess { started ->
                if (connection !== conn) {
                    // disconnected while starting; end the orphan on its own
                    // operation thread rather than block the callback thread
                    started.end()
                        .onError { error ->
                            detailedLog("End bracket for the orphaned session failed: ${error.message}")
                        }
                        .execute()
                    return@onSuccess
                }
                conn.session = started
                // a previous checkout's unsettled returns die with it
                clearPendingReturns(conn)
                _state.update {
                    it.withCheckoutCleared(sessionId = started.sessionId, lastPayment = null)
                }
                log("Checkout session started (id ${started.sessionId})")
                if (identifyOnStart) {
                    identifyMember(conn, started)
                } else {
                    clearCustomerDisplay(started)
                }
            }
            .onError { error ->
                if (connection === conn) {
                    log("Failed to start a checkout session: ${error.message}")
                    error.cause?.let { detailedLog(it.stackTraceToString()) }
                }
            }
            .onComplete { conn.startClaimed.set(false) }
            .execute()
    }

    override fun endSession() {
        val conn = connection
        val active = conn?.session
        if (conn == null || active == null) {
            log("No active checkout session")
            return
        }
        log("Ending checkout session (End bracket)…")
        active.end()
            .onSuccess {
                // cleared only on success: a failed end keeps the session
                // held and retryable instead of orphaning terminal state
                conn.session = null
                // unsettled returns are discarded with the basket
                clearPendingReturns(conn)
                if (connection === conn) {
                    _state.update { it.withCheckoutCleared(lastPayment = null) }
                    log("Checkout session ended")
                }
            }
            .onError { error ->
                if (connection === conn) {
                    log("Failed to end the session: ${error.message} — still active, retry or disconnect")
                    error.cause?.let { detailedLog(it.stackTraceToString()) }
                }
            }
            .execute()
    }

    override fun addProduct(product: Product) {
        // Basket mutations are eager local calls, safe on the UI thread —
        // the auto-display push they trigger runs asynchronously in the SDK
        val session = connection?.session
        if (session == null) {
            log("No active checkout session — press Start Checkout first")
            return
        }
        try {
            val existing = session.basket().snapshot().getItemBySku(product.sku)
            val basket = if (existing == null) {
                val item = BasketItem.builder()
                    .sku(product.sku)
                    .description(product.name)
                    .category(product.category)
                    .quantity(1)
                    .unitPrice(BigDecimal(product.priceDecimal))
                    .apply { NjSalesTax.rateFor(product)?.let(::taxRate) }
                    .build()
                session.basket().addItem(item)
            } else {
                session.basket().updateItemQuantityBySku(product.sku, existing.quantity + 1)
            }
            publishBasket(basket)
            log("Added ${product.name} (${product.priceLabel})")
        } catch (e: Exception) {
            log("Failed to add ${product.name}: ${e.message}")
            detailedLog(e.stackTraceToString())
        }
    }

    /**
     * The post-start member-identification prompt (its own operation, not
     * part of the Start bracket); a failed or declined prompt degrades to
     * a guest checkout.
     */
    private fun identifyMember(conn: Connection, session: CheckoutSession) {
        // Claimed like pay/acquireCard: without the claim, a Pay tapped
        // during the prompt would queue behind it on the session's
        // operation thread — and an abort would cancel only the prompt
        // while the queued payment went on to charge
        if (!conn.operationClaimed.compareAndSet(false, true)) {
            log("Another operation is already in progress — continuing as guest")
            clearCustomerDisplay(session)
            return
        }
        _state.update { it.copy(identifyInProgress = true) }
        log("Identifying member on the terminal…")
        // The terminal's keyed loyalty capture engages only with
        // ForceEntryMode=Keyed; without it the terminal waits on the card
        // reader instead of showing the input form
        session.identifyMember(
            IdentifyOptions.builder()
                .forceEntryMode(ForceEntryMode.KEYED)
                .build()
        )
            .onSuccess { outcome ->
                if (connection !== conn) return@onSuccess
                if (outcome.status == IdentifyStatus.FOUND) {
                    log(
                        "Member identified: ${outcome.memberId}" +
                            (outcome.loyaltyBrand?.let { " ($it)" } ?: "") +
                            ", ${outcome.pointBalance} pts, ${outcome.rewards.size} reward(s)"
                    )
                } else {
                    log("No member (${outcome.status}) — loyalty steps will be skipped")
                }
            }
            .onError { error ->
                if (connection !== conn) return@onError
                log("Member identification failed: ${error.message} — continuing as guest")
                error.cause?.let { detailedLog(it.stackTraceToString()) }
            }
            .onComplete {
                conn.operationClaimed.set(false)
                if (connection === conn) {
                    _state.update { it.copy(identifyInProgress = false) }
                    // after the prompt settles, so the clear can't race it
                    clearCustomerDisplay(session)
                }
            }
            .execute()
    }

    /** Blank the customer display — autoDisplay only fires on basket
     *  mutations, so the previous checkout's receipt (or the identify
     *  prompt's leftovers) would linger. */
    private fun clearCustomerDisplay(session: CheckoutSession) {
        session.updateDisplay(session.basket().snapshot())
            .onSuccess { log("Customer display cleared (empty basket)") }
            .onError { error -> log("Display clear failed: ${error.message}") }
            .execute()
    }

    override fun settle(loyalty: LoyaltyOptions, storedValue: StoredValueOptions?, net: Boolean) {
        val conn = connection
        val session = conn?.session
        if (conn == null || session == null) {
            log("No active checkout session — press Start Checkout first")
            return
        }
        if (!conn.operationClaimed.compareAndSet(false, true)) {
            log("Another operation is already in progress")
            return
        }
        _state.update { it.copy(paymentInProgress = true, paymentOutcome = null) }
        // Still RUNNING when the flow completes means aborted: the SDK
        // bypasses onError by design when the register asked for the abort.
        // A plain var is safe on ANY executor, pool included: the payment
        // thread awaits each handler and only then submits the next, so
        // handlers never overlap and each submission carries a
        // happens-before edge.
        var attempt = PaymentAttempt.RUNNING
        // Set or actively cleared each attempt: the session keeps the card
        // across a retry, and a toggle switched off in between must not
        // silently charge the card again
        val card = storedValue?.toCard()
        session.setStoredValueCard(card)

        // The refund allocations must total exactly what the selected mode
        // returns: the full return value in gross mode, only the
        // refund-dominant difference under NET (zero when the charge
        // absorbs the returns). One allocation per returned sale, filled
        // in ring order until the required total is covered. Read under
        // the claim, so it matches exactly the credit lines in the basket.
        val settlementType =
            if (net) SettlementType.NET else SettlementType.REFUND_THEN_CHARGE
        val required = session.basket().snapshot().getRefundAmount(settlementType)
        var unallocated = required
        val returns = conn.pendingReturns.groupBy { it.saleId }.values.map { group ->
            val total = group.fold(BigDecimal.ZERO) { acc, pending -> acc.add(pending.amount) }
                .setScale(2, RoundingMode.HALF_UP)
            val allocated = if (total <= unallocated) total else unallocated
            unallocated = unallocated.subtract(allocated)
            // the tender can only give back what it collected — a netted
            // sale collected less than shelf value; the overflow is paid
            // out by the register (an external allocation, no wire)
            val capacity = group.first().legCapacity
            val toTender = if (capacity != null && capacity < allocated) capacity else allocated
            PlannedReturn(
                saleId = group.first().saleId,
                legType = group.first().legType,
                original = group.first().original,
                items = group.flatMap { it.items },
                total = total,
                allocated = toTender,
                external = allocated.subtract(toTender),
            )
        }
        val optionsBuilder = SettlementOptions.builder()
            .settlementType(settlementType)
            .disableRebates(!loyalty.rebates)
            .disablePoints(!loyalty.redemption)
            .disableAward(!loyalty.award)
        returns.forEach { planned ->
            if (planned.allocated.signum() > 0) {
                optionsBuilder.addRefund(
                    if (planned.legType == LegType.CARD) {
                        RefundAllocation.card(planned.allocated, planned.original)
                    } else {
                        RefundAllocation.storedValue(planned.allocated, planned.original)
                    }
                )
            }
            if (planned.external.signum() > 0) {
                optionsBuilder.addRefund(RefundAllocation.external(planned.external))
            }
        }
        val options = optionsBuilder.build()
        log(
            "Starting ${if (net) "net " else ""}settlement — rebates ${onOff(loyalty.rebates)}, " +
                "redemption ${onOff(loyalty.redemption)}, award ${onOff(loyalty.award)}" +
                (card?.let { ", gift card ${it.storedValueId ?: "(swipe on terminal)"}" } ?: "") +
                (if (returns.isEmpty()) "" else
                    ", ${returns.size} prior sale(s) returned" +
                        " ($${required.toPlainString()} to refund after netting)")
        )
        session.settle(options)
            .onCardRefunded { movement ->
                log(
                    "Card refund committed: $${movement.amount?.toPlainString()}" +
                        " (txn ${movement.poiTransactionId})"
                )
            }
            .onGiftCardRefunded { movement ->
                log(
                    "Gift card refund committed: $${movement.amount?.toPlainString()}" +
                        " (txn ${movement.poiTransactionId})"
                )
            }
            .onRebatesRedeemed { rebates ->
                log("Rebates applied: −$${rebates.totalRebateAmount.toPlainString()} → total $${rebates.suggestedTotal.toPlainString()}")
                rebates.suggestedTotal
            }
            .onPointsRedeemed { points ->
                log("Points redeemed: ${points.pointsUsed} (−$${points.monetaryValue.toPlainString()}) → total $${points.suggestedTotal.toPlainString()}")
                points.suggestedTotal
            }
            .onGiftCardPayment { giftCard ->
                val balance = giftCard.remainingCardBalance
                    ?.let { " (card balance $${it.toPlainString()})" } ?: ""
                log(
                    "Gift card charged: $${giftCard.amountCharged.toPlainString()}$balance" +
                        " → total $${giftCard.suggestedTotal.toPlainString()}"
                )
                giftCard.suggestedTotal
            }
            .onError { error ->
                attempt = PaymentAttempt.FAILED
                if (connection === conn) {
                    log("Settlement failed: ${error.code} — ${error.message}")
                    _state.update {
                        it.copy(paymentOutcome = PaymentOutcome(
                            success = false,
                            title = "Settlement failed",
                            message = "${error.code}\n${error.message}",
                        ))
                    }
                }
                SettlementRecovery.abort()
            }
            .onSuccess { result ->
                attempt = PaymentAttempt.SUCCEEDED
                // Not gated on the connection: the charge stands even if
                // the operator disconnected mid-payment, and an unrecorded
                // sale could never be refunded or voided. Session facts are
                // captured now — the chained end() clears the member before
                // the IO write runs.
                persistSale(session.sessionId, session.member?.memberId, result)
                val returnParts = recordSettledReturns(conn, returns, result)
                if (connection === conn) {
                    publishPaymentResult(result, returnParts)
                    log("Settlement complete — ending the checkout automatically")
                    endCompletedCheckout(conn, session)
                }
            }
            .onComplete {
                if (attempt != PaymentAttempt.SUCCEEDED && connection === conn) {
                    if (attempt == PaymentAttempt.RUNNING) {
                        log(
                            "Settlement aborted; committed steps reversed — " +
                                "the basket is intact, Settle again to retry"
                        )
                    }
                    // reset the customer display to the intact basket
                    session.updateDisplay(session.basket().snapshot())
                        .onError { error -> detailedLog("Display restore failed: ${error.message}") }
                        .execute()
                }
                finishPaymentAttempt(conn)
            }
            .execute()
    }

    /**
     * The auto-end after a completed payment. Submitted from the payment's
     * `onSuccess`, it queues behind the settling payment on the session's
     * operation thread; the payment summary stays visible until the next
     * Start Checkout.
     */
    private fun endCompletedCheckout(conn: Connection, session: CheckoutSession) {
        session.end()
            .onSuccess {
                conn.session = null
                if (connection === conn) {
                    _state.update { it.withCheckoutCleared() }
                    log("Checkout ended")
                }
            }
            .onError { error ->
                if (connection === conn) {
                    log("Failed to end the checkout: ${error.message} — press End Checkout to retry")
                    error.cause?.let { detailedLog(it.stackTraceToString()) }
                }
            }
            .execute()
    }

    /**
     * Releases the payment claim and busy flag. The claim always (it is
     * per-connection); the shared UI flag only while this connection is
     * still current — a late completion must not re-enable Pay while a NEW
     * connection's payment is on the wire.
     */
    private fun finishPaymentAttempt(conn: Connection) {
        conn.operationClaimed.set(false)
        if (connection === conn) {
            _state.update { it.copy(paymentInProgress = false) }
        }
    }

    override fun acquireCard() {
        val conn = connection
        val session = conn?.session
        if (conn == null || session == null) {
            log("No active checkout session — press Start Checkout first")
            return
        }
        if (!conn.operationClaimed.compareAndSet(false, true)) {
            log("Another operation is already in progress")
            return
        }
        _state.update { it.copy(cardReadInProgress = true) }
        log("Reading card on the terminal (CardAcquisition, MagStripe/Scanned)…")
        // ForceEntryMode is sent explicitly: without it the terminal arms
        // only its default reader set, which may not include the stripe —
        // MagStripe + Scanned are the gift-card capture methods
        session.acquireCard(
            CardAcquisitionOptions.builder()
                .forceEntryMode(ForceEntryMode.MAG_STRIPE)
                .forceEntryMode(ForceEntryMode.SCANNED)
                .build()
        )
            .onSuccess { acquired ->
                if (connection !== conn) return@onSuccess
                val label = listOfNotNull(
                    acquired.paymentBrand,
                    acquired.maskedPan ?: acquired.truncatedPan,
                    acquired.entryMode?.let { "via $it" },
                ).joinToString(" ").ifEmpty { "no card details returned" }
                val number = acquired.rawPan
                if (number.isNullOrBlank()) {
                    // only PLCC-range cards (gift cards among them) return
                    // the full PAN; masked reads carry no chargeable number
                    log("Card read: $label — no full card number returned, type it manually")
                } else {
                    _state.update {
                        it.copy(acquiredCard = AcquiredCard(
                            number = number,
                            sequence = (it.acquiredCard?.sequence ?: 0) + 1,
                        ))
                    }
                    log("Card read: $label — filled into the gift card field")
                }
            }
            .onError { error ->
                if (connection !== conn) return@onError
                if (error.code == SessionErrorCode.ABORTED) {
                    log("Card read aborted")
                } else {
                    log("Card read failed: ${error.message}")
                    error.cause?.let { detailedLog(it.stackTraceToString()) }
                }
            }
            .onComplete {
                conn.operationClaimed.set(false)
                if (connection === conn) {
                    _state.update { it.copy(cardReadInProgress = false) }
                }
            }
            .execute()
    }

    override fun refundSale(saleId: String) {
        val conn = connection
        if (conn == null) {
            log("Not connected — connect before refunding")
            return
        }
        // A reversal session brackets the terminal the way a checkout does,
        // so one must not open while a checkout is active (or starting —
        // startSession refuses while refundInProgress for the same reason)
        if (conn.session != null || conn.startClaimed.get()) {
            log("A checkout session is active — end it before refunding")
            return
        }
        // Shares the operation claim with pay/acquireCard/identify so a
        // refund can't run concurrently with a checkout operation
        if (!conn.operationClaimed.compareAndSet(false, true)) {
            log("Another operation is already in progress")
            return
        }
        conn.refundAbortRequested.set(false)
        _state.update { it.copy(refundInProgress = true, paymentOutcome = null) }
        // An IO coroutine, not conn.dispatcher (that lane is the diagnostics
        // poller's): the store lookup and the reversal roundtrips all block
        val job = conn.scope.launch(Dispatchers.IO) {
            val stored = try {
                saleStore.findSale(saleId)
            } catch (e: Exception) {
                log("Failed to load the stored sale: ${e.message}")
                detailedLog(e.stackTraceToString())
                return@launch
            }
            if (stored == null) {
                log("Sale $saleId is not in the store")
                return@launch
            }
            if (!stored.refundable) {
                log(
                    if (stored.voided != null) {
                        "The sale was voided — nothing left to refund"
                    } else {
                        "The sale was already refunded in full — nothing left to refund"
                    }
                )
                return@launch
            }
            val sale = stored.sale
            // A full refund is a void of the prior sale: one flow reverses
            // every referenced movement — the tender legs, redemption,
            // rebate, and the award. After an item-based partial refund it
            // would return the full legs on top of what was already given
            // back, so it is refused then. Per-leg FULL records are
            // different: they are the residue of a void that failed midway,
            // and the retry omits those references — see executeFullRefund.
            if (stored.refunds.any { !it.full }) {
                log("The sale was already partially refunded — refund the remaining items instead")
                return@launch
            }
            if (conn.pendingReturns.any { it.saleId == sale.id }) {
                log("The sale has returns in the basket — settle or end the checkout first")
                return@launch
            }
            if (sale.legs.isEmpty()) {
                log("The sale has no recorded movements — nothing to reverse")
                return@launch
            }
            executeFullRefund(conn, stored)
        }
        // Releases on every path, including a job cancelled before it ran
        // (disconnect racing this call). The claim always (per-connection);
        // the shared UI flag only while this connection is still current —
        // mirrors finishPaymentAttempt.
        conn.refundJob = job
        job.invokeOnCompletion {
            conn.operationClaimed.set(false)
            if (connection === conn) {
                _state.update { it.copy(refundInProgress = false) }
            }
        }
    }

    override fun addReturnToBasket(saleId: String, skus: Set<String>) {
        val conn = connection
        val session = conn?.session
        if (conn == null || session == null) {
            log("No active checkout session — press Start Checkout first")
            return
        }
        // Shares the operation claim so the ring can't interleave with a
        // settlement reading the pending returns
        if (!conn.operationClaimed.compareAndSet(false, true)) {
            log("Another operation is already in progress")
            return
        }
        // The ring participates in the refund abort protocol: the busy flag
        // routes abort() here (there is no wire call for it to cancel), and
        // the flag — cleared per attempt, like refundSale — is checked
        // before the basket mutation, so an abort during the store lookup
        // keeps the return out of the basket.
        conn.refundAbortRequested.set(false)
        _state.update { it.copy(refundInProgress = true) }
        // IO coroutine: the store lookup blocks
        val job = conn.scope.launch(Dispatchers.IO) {
            val stored = try {
                saleStore.findSale(saleId)
            } catch (e: Exception) {
                log("Failed to load the stored sale: ${e.message}")
                detailedLog(e.stackTraceToString())
                return@launch
            }
            if (stored == null) {
                log("Sale $saleId is not in the store")
                return@launch
            }
            if (!stored.refundable) {
                log(
                    if (stored.voided != null) {
                        "The sale was voided — nothing left to refund"
                    } else {
                        "The sale was already refunded in full — nothing left to refund"
                    }
                )
                return@launch
            }
            val sale = stored.sale
            if (stored.moneyLegs.isEmpty()) {
                log("The sale has no tender leg (rewards covered everything) — use the full refund")
                return@launch
            }
            // Returns restore to the primary OUTSTANDING tender: the card
            // leg unless an earlier refund already returned it, else the
            // stored value leg
            val outstanding = stored.moneyLegs.filterNot { stored.legRefunded(it.type) }
            val leg = outstanding.firstOrNull { it.type == LegType.CARD }
                ?: outstanding.firstOrNull()
            if (leg == null) {
                // unreachable while refundable, kept against future drift
                log("Every tender leg was already refunded in full — nothing left to draw from")
                return@launch
            }
            // Ring only what earlier refunds AND returns already sitting in
            // the basket have not consumed — both cap every line
            val items = sale.items.filter { it.sku in skus }.mapNotNull { item ->
                val remaining = item.quantity - stored.refundedQuantity(item.sku) -
                    pendingReturnedQuantity(conn, sale.id, item.sku)
                when {
                    remaining >= item.quantity -> item
                    remaining > 0 -> {
                        log(
                            "${item.description}: ${item.quantity - remaining} of " +
                                "${item.quantity} already returned — ringing the remaining $remaining"
                        )
                        item.copy(quantity = remaining)
                    }
                    else -> {
                        log("${item.description} is already fully returned — skipped")
                        null
                    }
                }
            }
            if (items.isEmpty()) {
                log("Nothing left to return among the selected items")
                return@launch
            }
            // last stop before the basket mutation — an abort that landed
            // during the lookup must keep the return out
            if (conn.refundAbortRequested.get()) {
                log("Return aborted — nothing was rung into the basket")
                return@launch
            }
            try {
                items.forEach { item ->
                    // rung exactly as sold — shelf price and tax rate — so
                    // the credit total is price plus tax of the returns
                    session.basket().addItem(
                        BasketItem.builder()
                            .sku(item.sku)
                            .description(item.description)
                            .quantity(item.quantity)
                            .unitPrice(BigDecimal(item.unitPrice))
                            .type(BasketItemType.RETURN)
                            .apply { item.category?.let(::category) }
                            .apply { item.taxRate?.let { rate -> taxRate(BigDecimal(rate)) } }
                            .build()
                    )
                }
                conn.pendingReturns = conn.pendingReturns + PendingReturn(
                    saleId = sale.id,
                    original = originalSaleRecord(sale, stored),
                    legType = leg.type,
                    items = items,
                    amount = items.fold(BigDecimal.ZERO) { acc, item ->
                        acc.add(refundValue(item, item.quantity))
                    }.setScale(2, RoundingMode.HALF_UP),
                    legCapacity = stored.remainingLegAmount(leg.type),
                )
                publishBasket(session.basket().snapshot())
                // the sales projection nets rung returns off the remaining
                // quantities
                refreshSales()
                log(
                    "Return rung in: " +
                        items.joinToString { "${it.quantity}× ${it.description}" } +
                        " — restores to the ${legLabel(leg.type)} on settlement"
                )
            } catch (e: Exception) {
                // e.g. the basket sealed against mutation by session state
                log("Failed to ring the return: ${e.message}")
                detailedLog(e.stackTraceToString())
            }
        }
        conn.refundJob = job
        job.invokeOnCompletion {
            conn.operationClaimed.set(false)
            if (connection === conn) {
                _state.update { it.copy(refundInProgress = false) }
            }
        }
    }

    /** Quantity of [sku] from sale [saleId] already rung into the active
     *  basket as returns but not settled yet. */
    private fun pendingReturnedQuantity(conn: Connection, saleId: String, sku: String): Int =
        conn.pendingReturns.filter { it.saleId == saleId }
            .sumOf { pending -> pending.items.filter { it.sku == sku }.sumOf { it.quantity } }

    /**
     * Records the settled returns against their original sales, matching
     * each allocation's committed refund movement (they commit in
     * allocation order) for the terminal reference, and releases the
     * pending returns. Returns the popup lines describing what was
     * restored — including the loud warning when a record write failed.
     */
    private fun recordSettledReturns(
        conn: Connection,
        returns: List<PlannedReturn>,
        result: SettlementResult,
    ): List<String> {
        if (returns.isEmpty()) {
            return emptyList()
        }
        // refund movements commit in allocation order — one per planned
        // return with money actually flowing back; fully netted returns
        // have none
        val movements = result.movements.filter {
            it.step == SettlementStep.CARD_REFUND || it.step == SettlementStep.STORED_VALUE_REFUND
        }
        var movementIndex = 0
        var recorded = true
        val parts = mutableListOf<String>()
        returns.forEach { planned ->
            val refunded = planned.allocated.signum() > 0
            val movement = if (refunded) movements.getOrNull(movementIndex++) else null
            val netted = planned.total.subtract(planned.allocated).subtract(planned.external)
            parts += if (planned.allocated == planned.total) {
                "returned $${planned.total.toPlainString()} to the ${legLabel(planned.legType)}"
            } else if (planned.total.signum() > 0 && planned.allocated.signum() == 0 &&
                planned.external.signum() == 0
            ) {
                "netted $${planned.total.toPlainString()} against the purchase"
            } else {
                val details = buildList {
                    if (refunded) {
                        add("$${planned.allocated.toPlainString()} to the ${legLabel(planned.legType)}")
                    }
                    if (planned.external.signum() > 0) {
                        add(
                            "$${planned.external.toPlainString()} register-paid — the " +
                                "${legLabel(planned.legType)} collected less than the shelf value"
                        )
                    }
                    if (netted.signum() > 0) {
                        add("$${netted.toPlainString()} netted")
                    }
                }
                "returned $${planned.total.toPlainString()} (${details.joinToString(", ")})"
            }
            recorded = recordRefund(planned.saleId, RefundRecord(
                // the full return value: the customer received it all, as
                // tender refund and/or as offset against the charge
                amount = planned.total.toPlainString(),
                poiTransactionId = movement?.poiTransactionId,
                poiTimestamp = movement?.poiTransactionTimestamp?.toString(),
                recordedAt = Instant.now().toString(),
                full = false,
                // the leg names a tender money moved back to; a fully
                // netted return touched none
                leg = planned.legType.takeIf { refunded },
                items = planned.items.map { RefundedItem(it.sku, it.quantity) },
            )) && recorded
        }
        conn.pendingReturns = emptyList()
        if (!recorded) {
            parts += "WARNING: a return could NOT be recorded — its sale " +
                "will still offer what was just refunded; refunding it " +
                "again would return the money twice"
        }
        return parts
    }

    /** Discards returns whose checkout went away without settling them. */
    private fun clearPendingReturns(conn: Connection) {
        if (conn.pendingReturns.isNotEmpty()) {
            conn.pendingReturns = emptyList()
            refreshSales()
        }
    }

    /**
     * Full refund of the prior sale, blocking the calling IO coroutine: a
     * void of every referenced movement — the tender legs, redemption,
     * rebate, and award — on a fresh [CheckoutSession], recorded as a
     * legless full [RefundRecord] (the sale is exhausted for good).
     */
    private fun executeFullRefund(conn: Connection, stored: StoredSale) {
        val sale = stored.sale
        log(
            "Starting full refund of sale ${sale.id.take(8)} — voiding every " +
                "movement of the prior sale (${sale.legs.size} reference(s))…"
        )
        runRefundSession(conn, sale) { session ->
            // The step a money reversal aborted on. The SDK resumes a
            // partial void only on the same session instance — which this
            // flow closes — so cross-session resume is reconstructed via
            // the store: reversal order is CARD, STORED_VALUE, then the
            // loyalty movements, and only money steps abort here, so a
            // failure at [failedMoneyStep] means every referenced tender
            // leg ordered before it committed and must not be sent again.
            var failedMoneyStep: ReversalStep? = null
            val result = try {
                session.voidTransaction(originalSaleRecord(sale, stored))
                    .onError { step, error ->
                        log("Refund step ${step ?: "(none ran)"} failed: ${error.message}")
                        // the default policy, replicated so logging doesn't
                        // change behavior: a failed tender reversal aborts, a
                        // loyalty movement riding along is skipped (the
                        // terminal can retry it via store-and-forward)
                        if (step == ReversalStep.CARD || step == ReversalStep.STORED_VALUE) {
                            failedMoneyStep = step
                            ReversalDecision.ABORT
                        } else {
                            ReversalDecision.SKIP
                        }
                    }
                    .get()
            } catch (e: SessionException) {
                if (!recordPartialVoid(stored, failedMoneyStep)) {
                    // the reversed leg has no record: the failure popup must
                    // carry the double-reversal warning, not just the error
                    throw SessionException(SessionError(
                        e.error.code,
                        e.error.message + "\nWARNING: a reversed tender leg could NOT " +
                            "be recorded — retrying the full refund would reverse it again",
                    ))
                }
                throw e
            }
            if (result.isSuccess) {
                // Money moved on the terminal, so the refund is recorded
                // unconditionally; unlike sale writes this one runs on the
                // refund job's own thread — shutdown() joins the job so a
                // window close cannot exit before it reaches disk
                val recorded = recordRefund(sale.id, RefundRecord(
                    amount = result.reversedAmount?.toPlainString(),
                    poiTransactionId = result.poiTransactionId,
                    poiTimestamp = result.poiTransactionTimestamp?.toString(),
                    recordedAt = Instant.now().toString(),
                    // legless: the void exhausted the whole sale
                    full = true,
                    awardReversed = sale.leg(LegType.AWARD) != null,
                ))
                val parts = buildList {
                    // the terminal does not always echo the reversed amount
                    add("Refunded" + (result.reversedAmount?.let { " $${it.toPlainString()}" } ?: ""))
                    if (result.pointsReversed > 0) {
                        add("reversed ${result.pointsReversed} pts (balance ${result.remainingPointBalance})")
                    }
                }
                publishRefundResult(
                    conn, parts, recorded,
                    receiptText(result.customerReceipt, result.merchantReceipt),
                )
            }
        }
    }

    /**
     * Opens the fresh checkout session a referenced refund runs on, keeps
     * it reachable for [abort] while [body] drives it, and funnels
     * failures into the outcome popup. The session is bracketed around the
     * body (close() sends the End signal on every path).
     */
    private fun runRefundSession(
        conn: Connection,
        sale: SaleRecord,
        body: (CheckoutSession) -> Unit,
    ) {
        try {
            val session = CheckoutSession.builder()
                .client(conn.client)
                // the record persisted the original sale's identity exactly
                // so a later referenced reversal can present it
                .saleId(sale.saleId)
                .poiId(sale.poiId)
                .currency(sale.currency)
                .onBackgroundError { error ->
                    log("Customer display update failed: ${error.message}")
                }
                .start()
                .get()
            conn.refundSession = session
            try {
                session.use {
                    // an abort during the start roundtrip had nothing to
                    // cancel (the session was not published yet) — this
                    // check is what keeps the money from moving; from here
                    // on the SDK abort reaches the operation itself
                    if (conn.refundAbortRequested.get()) {
                        publishRefundAborted(conn)
                        return
                    }
                    body(session)
                }
            } finally {
                conn.refundSession = null
            }
        } catch (e: SessionException) {
            if (connection === conn) {
                if (e.error.code == SessionErrorCode.ABORTED) {
                    log("Refund aborted — the interrupted movement was not committed")
                } else {
                    log("Refund failed: ${e.error.code} — ${e.error.message}")
                }
                _state.update {
                    it.copy(paymentOutcome = PaymentOutcome(
                        success = false,
                        title = "Refund failed",
                        message = "${e.error.code}\n${e.error.message}",
                    ))
                }
            }
        } catch (e: Exception) {
            // e.g. the session start being rejected by the terminal
            if (connection === conn) {
                log("Refund not completed: ${e.message}")
                _state.update {
                    it.copy(paymentOutcome = PaymentOutcome(
                        success = false,
                        title = "Refund failed",
                        message = "Refund not completed\n${e.message}",
                    ))
                }
                detailedLog(e.stackTraceToString())
            }
        }
    }

    /**
     * The prior sale's persisted terminal references, as the settlement
     * API takes them — MINUS what earlier refunds already reversed: a
     * tender leg with a full per-leg record, and the award once any record
     * says it is gone. The SDK reverses only the references supplied, so a
     * void retried after a partial failure sends just the outstanding
     * movements instead of re-crediting the reversed ones.
     */
    private fun originalSaleRecord(sale: SaleRecord, stored: StoredSale): OriginalSaleRecord {
        val builder = OriginalSaleRecord.builder()
        sale.leg(LegType.CARD)?.takeUnless { stored.legRefunded(LegType.CARD) }?.let {
            builder.cardPoiTransactionId(it.poiTransactionId)
            parseInstant(it.poiTimestamp)?.let(builder::cardPoiTransactionTimestamp)
        }
        sale.leg(LegType.STORED_VALUE)
            ?.takeUnless { stored.legRefunded(LegType.STORED_VALUE) }?.let {
                builder.storedValuePoiTransactionId(it.poiTransactionId)
                parseInstant(it.poiTimestamp)?.let(builder::storedValuePoiTransactionTimestamp)
            }
        sale.leg(LegType.REBATE)?.let {
            builder.rebatePoiTransactionId(it.poiTransactionId)
            parseInstant(it.poiTimestamp)?.let(builder::rebatePoiTransactionTimestamp)
        }
        sale.leg(LegType.REDEMPTION)?.let {
            builder.redemptionPoiTransactionId(it.poiTransactionId)
            parseInstant(it.poiTimestamp)?.let(builder::redemptionPoiTransactionTimestamp)
        }
        sale.leg(LegType.AWARD)?.takeUnless { stored.awardReversed }?.let {
            builder.awardPoiTransactionId(it.poiTransactionId)
            parseInstant(it.poiTimestamp)?.let(builder::awardPoiTransactionTimestamp)
        }
        sale.memberId?.let(builder::memberId)
        return builder.build()
    }

    /**
     * Persists a failed void's progress: the tender legs the void reversed
     * before aborting on [failedMoneyStep], each as a full per-leg record.
     * Money legs reverse first and in a fixed order (card, then stored
     * value), so everything referenced ahead of the failed step committed.
     * The retry's [originalSaleRecord] then omits those legs; the loyalty
     * movements never ran (they reverse after the tenders).
     */
    private fun recordPartialVoid(stored: StoredSale, failedMoneyStep: ReversalStep?): Boolean {
        if (failedMoneyStep != ReversalStep.STORED_VALUE) {
            // a CARD failure aborts at the first movement; a pre-step
            // rejection ran nothing — either way there is no progress
            return true
        }
        val sale = stored.sale
        val committed = sale.leg(LegType.CARD)
            ?.takeUnless { stored.legRefunded(LegType.CARD) } ?: return true
        val recorded = recordRefund(sale.id, RefundRecord(
            amount = committed.amount,
            recordedAt = Instant.now().toString(),
            full = true,
            leg = committed.type,
        ))
        log(
            if (recorded) {
                "The ${legLabel(committed.type)} leg was reversed before the failure — " +
                    "recorded; retrying the full refund covers only what is outstanding"
            } else {
                "WARNING: the ${legLabel(committed.type)} leg was reversed but its " +
                    "record could NOT be stored — retrying the full refund would " +
                    "reverse it AGAIN"
            }
        )
        return recorded
    }

    /** The outcome popup for a refund stopped by the abort flag before it
     *  reached the wire. */
    private fun publishRefundAborted(conn: Connection) {
        val message = "Refund aborted before any money moved"
        log(message)
        if (connection === conn) {
            _state.update {
                it.copy(paymentOutcome = PaymentOutcome(
                    success = false,
                    title = "Refund aborted",
                    message = message,
                ))
            }
        }
    }

    /** Stores the refund against its sale — including what it covered, so
     *  a later refund can't return the same thing again. A storage failure
     *  cannot fail the refund (the money already moved) but must not stay
     *  quiet either — without the record the sale offers the same refund
     *  again, and a terminal that accepts it would return the money twice.
     *  False on failure, so the outcome popup carries the warning. */
    private fun recordRefund(saleId: String, record: RefundRecord): Boolean {
        return try {
            saleStore.recordRefund(saleId, record)
            // keep the Refund tab's list (refunded badges) current
            refreshSales()
            true
        } catch (e: Exception) {
            log("Failed to store the refund: ${e.message}")
            detailedLog(e.stackTraceToString())
            false
        }
    }

    /** Publishes a completed refund. [recorded] false means the refund
     *  history write failed: the money moved, so the popup stays a
     *  success, but it must warn that the sale will offer this refund
     *  again. */
    private fun publishRefundResult(
        conn: Connection,
        parts: List<String>,
        recorded: Boolean,
        receipt: String?,
    ) {
        val all = if (recorded) parts else parts +
            ("WARNING: the refund could NOT be recorded — the sale will " +
                "still offer what was just refunded; refunding it again " +
                "would return the money twice")
        log(all.joinToString(", "))
        if (connection === conn) {
            _state.update {
                it.copy(paymentOutcome = PaymentOutcome(
                    success = true,
                    title = "Refund complete",
                    message = all.joinToString("\n"),
                    receipt = receipt,
                ))
            }
        }
    }

    private fun legLabel(type: LegType): String =
        if (type == LegType.STORED_VALUE) "gift card" else "card"

    /** The popup's receipt block: the customer copy when the terminal
     *  returned one, else the merchant copy; the plain-text rendering
     *  preferred over the HTML body. */
    private fun receiptText(customer: Receipt?, merchant: Receipt?): String? =
        (customer ?: merchant)?.let { it.plainText ?: it.html }

    /** The stored ISO instant, or null for a malformed record — the refund
     *  then runs on the transaction id alone rather than not at all. */
    private fun parseInstant(iso: String?): Instant? =
        iso?.let { runCatching { Instant.parse(it) }.getOrNull() }

    override fun abort() {
        val conn = connection
        if (conn == null) {
            log("Nothing to abort")
            return
        }
        // A refund operation in flight takes precedence — a full refund
        // cannot coexist with an active checkout (they refuse to start
        // together), and a return ring holds the operation claim of the
        // checkout it mutates. abort() is unordered: execute() overtakes
        // the in-flight call instead of queueing behind it. The flag is set
        // regardless of whether a refund session is on the wire: an abort
        // landing during the store lookup, a session start, or a return
        // ring has nothing to cancel, and the flag is what stops the
        // refund's money movement — or keeps the return out of the basket.
        if (_state.value.refundInProgress || conn.refundSession != null) {
            conn.refundAbortRequested.set(true)
            log("Aborting the refund…")
            conn.refundSession?.abort()
                ?.onError { error ->
                    log("Abort failed: ${error.message}")
                    error.cause?.let { detailedLog(it.stackTraceToString()) }
                }
                ?.execute()
            return
        }
        val session = conn.session
        if (session == null) {
            log("No active checkout session")
            return
        }
        log("Aborting…")
        session.abort()
            .onError { error ->
                log("Abort failed: ${error.message}")
                error.cause?.let { detailedLog(it.stackTraceToString()) }
            }
            .execute()
    }

    /**
     * Stores the completed sale with every transaction leg's POI reference,
     * so referenced refunds/voids can run after the session is gone. The
     * session facts arrive as arguments because the write runs later, on
     * [persistenceScope]. Best-effort: a storage failure must never fail
     * the checkout.
     */
    private fun persistSale(sessionId: String, memberId: String?, result: SettlementResult) {
        persistenceScope.launch {
            try {
                // a settlement that sold nothing (returns only) is not a
                // sale — its movements live on the ORIGINAL sales' refund
                // history, and an empty record would clutter the Refund tab
                if (result.finalBasket?.items.orEmpty().none { it.isSale }) {
                    return@launch
                }
                val record = result.toSaleRecord(
                    sessionId = sessionId,
                    saleId = config.saleId,
                    poiId = config.poiId,
                    currency = config.currency,
                    memberId = memberId,
                    recordId = UUID.randomUUID().toString(),
                    completedAt = Instant.now(),
                )
                saleStore.recordSale(record)
                log("Sale stored (${record.legs.size} transaction leg(s), id ${record.id})")
                refreshSales()
            } catch (e: Exception) {
                log("Failed to store the sale: ${e.message}")
                detailedLog(e.stackTraceToString())
            }
        }
    }

    private fun publishPaymentResult(
        result: SettlementResult,
        returnParts: List<String> = emptyList(),
    ) {
        result.finalBasket?.let(::publishBasket)
        val parts = buildList {
            addAll(returnParts)
            if (result.cardAmountCharged.signum() > 0) {
                add(
                    "card $${result.cardAmountCharged.toPlainString()}" +
                        (result.paymentBrand?.let { " ($it)" } ?: "")
                )
            }
            if (result.storedValueAmountUsed.signum() > 0) {
                add("gift card $${result.storedValueAmountUsed.toPlainString()}")
            }
            if (result.totalRebateAmount.signum() > 0) {
                add("rebates −$${result.totalRebateAmount.toPlainString()}")
            }
            if (result.pointsRedeemed > 0) {
                add("${result.pointsRedeemed} pts −$${result.pointsMonetaryValue.toPlainString()}")
            }
            if (result.totalPointsEarned > 0) {
                add("earned ${result.totalPointsEarned} pts (balance ${result.pointsBalance})")
            }
        }
        val summary = "Settled $${result.authorizedAmount.toPlainString()}" +
            (if (parts.isEmpty()) "" else " — " + parts.joinToString(", "))
        // the popup carries the promo messages and warnings that otherwise
        // live only in the event log
        val popup = buildList {
            add("Settled $${result.authorizedAmount.toPlainString()}")
            addAll(parts)
            result.promotionMessages.forEach { add(it) }
            result.warnings.forEach { add("Warning: $it") }
        }.joinToString("\n")
        _state.update {
            it.copy(
                lastPayment = summary,
                paymentOutcome = PaymentOutcome(
                    success = true,
                    title = "Settlement complete",
                    message = popup,
                    receipt = receiptText(result.customerReceipt, result.merchantReceipt),
                ),
            )
        }
        log(summary)
        result.promotionMessages.forEach { log("Promo: $it") }
        result.warnings.forEach { log("Warning: $it") }
    }

    override fun dismissPaymentOutcome() {
        _state.update { it.copy(paymentOutcome = null) }
    }

    /** Reload [EmulatorState.sales] from the store. App scope, not a
     *  connection scope: browsing stored sales must work while disconnected. */
    private fun refreshSales() {
        scope.launch(salesRefreshDispatcher) {
            val sales = try {
                saleStore.listSales()
            } catch (e: Exception) {
                log("Failed to load stored sales: ${e.message}")
                detailedLog(e.stackTraceToString())
                return@launch
            }
            _state.update { state -> state.copy(sales = sales.map { it.toUi() }) }
        }
    }

    private fun StoredSale.toUi() = StoredSaleUi(
        id = sale.id,
        completedAtLabel = formatCompletedAt(sale.completedAt),
        totalAmount = sale.authorizedAmount,
        memberId = sale.memberId,
        items = sale.items.map { item ->
            // rung-but-unsettled returns count too: what sits in the basket
            // must not be returnable a second time
            val pending = connection?.let { pendingReturnedQuantity(it, sale.id, item.sku) } ?: 0
            val remaining = (item.quantity - refundedQuantity(item.sku) - pending)
                .coerceIn(0, item.quantity)
            SaleItemUi(
                sku = item.sku,
                description = item.description,
                quantity = item.quantity,
                refundMinor = refundMinor(item, remaining),
                remainingQuantity = remaining,
            )
        },
        refunded = refunds.isNotEmpty(),
        fullyRefunded = fullyRefunded,
        fullRefundAvailable = refundable && refunds.all { it.full },
        voided = voided != null,
    )

    /** What returning [quantity] units of this line restores: shelf price
     *  plus tax, computed exactly like the basket engine's credit line
     *  (gross × rate, HALF_UP at scale 2) — so the refund allocations sum
     *  to precisely the credit-line total settlement checks against. */
    private fun refundValue(item: SaleItem, quantity: Int): BigDecimal {
        val gross = BigDecimal(item.unitPrice).multiply(BigDecimal(quantity))
        val tax = item.taxRate
            ?.let { gross.multiply(BigDecimal(it)).setScale(2, RoundingMode.HALF_UP) }
            ?: BigDecimal.ZERO
        return gross.add(tax)
    }

    /** [refundValue] in cents for the UI, so selections sum as Longs. A
     *  malformed amount degrades to zero rather than dropping the sale. */
    private fun refundMinor(item: SaleItem, quantity: Int): Long = try {
        refundValue(item, quantity)
            .movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    } catch (e: Exception) {
        0L
    }

    /** Local wall-clock label for the stored ISO instant; falls back to the
     *  raw string rather than dropping the sale over a malformed record. */
    private fun formatCompletedAt(iso: String): String = try {
        saleTimeFormat.format(Instant.parse(iso))
    } catch (e: Exception) {
        iso
    }

    /** A typed number is keyed entry; blank hands entry to the terminal. */
    private fun StoredValueOptions.toCard(): StoredValueCard {
        val number = cardNumber.trim()
        return if (number.isEmpty()) StoredValueCard.swiped() else StoredValueCard.number(number)
    }

    private fun onOff(enabled: Boolean) = if (enabled) "on" else "off"

    /**
     * The periodic connectivity loop on the device-level [Terminal] — no
     * session involved. Each probe runs blocking (`executeSync()`): the
     * loop owns its thread and its pacing, so a slow terminal never stacks
     * probes, and cancellation (disconnect) lands at [delay].
     */
    private fun runDiagnosisLoop(conn: Connection, terminal: Terminal) {
        conn.scope.launch(conn.dispatcher) {
            while (true) {
                val previous = _state.value.connection.phase
                terminal.diagnose()
                    .onSuccess { result ->
                        if (connection !== conn) return@onSuccess
                        val poi = result.poiStatus?.globalStatus?.toString() ?: "OK"
                        _state.update {
                            it.copy(connection = ConnectionStatus(ConnectionPhase.CONNECTED, "POI $poi"))
                        }
                        if (previous != ConnectionPhase.CONNECTED) {
                            log("Terminal connected (POI status: $poi)")
                        }
                    }
                    .onError { error ->
                        if (connection !== conn) return@onError
                        _state.update {
                            it.copy(
                                connection = ConnectionStatus(
                                    ConnectionPhase.ERROR,
                                    error.message ?: "diagnosis failed",
                                )
                            )
                        }
                        if (previous == ConnectionPhase.CONNECTED || previous == ConnectionPhase.CONNECTING) {
                            log("Terminal unreachable: ${error.message}")
                        }
                        error.cause?.let { detailedLog(it.stackTraceToString()) }
                    }
                    .executeSync()
                delay(diagnosticsInterval)
            }
        }
    }

    private fun updateDisconnectedState() {
        _state.update {
            it.withCheckoutCleared(lastPayment = null).copy(
                connection = ConnectionStatus(ConnectionPhase.DISCONNECTED),
                tls = TlsStatus.Unknown, // per-connection fact; stale FAILED would outlive it
                paymentInProgress = false,
                cardReadInProgress = false,
                identifyInProgress = false,
                refundInProgress = false,
                paymentOutcome = null,
            )
        }
    }

    /** The checkout-scoped fields, reset for a fresh or ended checkout.
     *  [lastPayment] survives by default: the payment summary stays visible
     *  until the next checkout starts. */
    private fun EmulatorState.withCheckoutCleared(
        sessionId: String? = null,
        lastPayment: String? = this.lastPayment,
    ) = copy(
        sessionId = sessionId,
        basket = emptyList(),
        basketTotal = "0.00",
        basketTax = "0.00",
        lastPayment = lastPayment,
    )

    private fun publishBasket(basket: Basket) {
        _state.update { state ->
            state.copy(
                basket = basket.items.map { line ->
                    BasketLine(
                        sku = line.sku,
                        description = line.description,
                        quantity = line.quantity,
                        lineTotal = line.adjustedTotal.toPlainString(),
                        credit = line.isReturn || line.isCredit,
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
