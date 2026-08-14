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
import com.bilt.pos.session.RefundResult
import com.bilt.pos.session.ReversalDecision
import com.bilt.pos.session.ReversalSession
import com.bilt.pos.session.ReversalStep
import com.bilt.pos.session.SessionErrorCode
import com.bilt.pos.session.SessionException
import com.bilt.pos.session.Terminal
import com.bilt.pos.session.basket.Basket
import com.bilt.pos.session.basket.BasketItem
import com.bilt.pos.session.identity.CardAcquisitionOptions
import com.bilt.pos.session.identity.ForceEntryMode
import com.bilt.pos.session.identity.IdentifyOptions
import com.bilt.pos.session.identity.IdentifyStatus
import com.bilt.pos.session.settlement.SettlementOptions
import com.bilt.pos.session.settlement.SettlementRecovery
import com.bilt.pos.session.settlement.SettlementResult
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

    private class Connection(
        val scope: CoroutineScope,
        val dispatcher: CoroutineDispatcher,
        val client: BiltNexoTerminalClient,
        val terminal: Terminal,
        /** The adb forward this connection runs through; null for a direct
         *  connection. Removed from the adb server on teardown. */
        val tunnel: AdbTunnel.Tunnel? = null,
        @Volatile var session: CheckoutSession? = null,
        /** The reversal session of the refund leg currently on the wire, so
         *  [abort] can reach a refund the way it reaches a payment; null
         *  outside a refund. */
        @Volatile var reversal: ReversalSession? = null,
    ) {
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
            if (detected != null) {
                _state.update { it.copy(terminalAddress = detected, addressAutodetected = true) }
                log("Autodetected terminal address $detected via adb")
                // Auto-connect only from DISCONNECTED: never tear down a
                // connection the operator made while adb was probing
                if (_state.value.connection.phase == ConnectionPhase.DISCONNECTED) {
                    log("Connecting automatically to the detected terminal")
                    connect(detected, _state.value.encryptionEnabled, null)
                }
            } else {
                log("Address autodetect found no adb device; enter the address manually")
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
        log("Opening adb tunnel to $address port 8443…")
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

    override fun settle(loyalty: LoyaltyOptions, storedValue: StoredValueOptions?) {
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

        val options = SettlementOptions.builder()
            .disableRebates(!loyalty.rebates)
            .disablePoints(!loyalty.redemption)
            .disableAward(!loyalty.award)
            .build()
        log(
            "Starting payment — rebates ${onOff(loyalty.rebates)}, " +
                "redemption ${onOff(loyalty.redemption)}, award ${onOff(loyalty.award)}" +
                (card?.let { ", gift card ${it.storedValueId ?: "(swipe on terminal)"}" } ?: "")
        )
        session.settle(options)
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
                    log("Payment failed: ${error.code} — ${error.message}")
                    _state.update {
                        it.copy(paymentOutcome = PaymentOutcome(
                            success = false,
                            title = "Payment failed",
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
                if (connection === conn) {
                    publishPaymentResult(result)
                    log("Payment complete — ending the checkout automatically")
                    endCompletedCheckout(conn, session)
                }
            }
            .onComplete {
                if (attempt != PaymentAttempt.SUCCEEDED && connection === conn) {
                    if (attempt == PaymentAttempt.RUNNING) {
                        log(
                            "Payment aborted; committed steps reversed — " +
                                "the basket is intact, Pay again to retry"
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

    override fun refundSale(saleId: String, skus: Set<String>?) {
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
            if (stored.moneyLegs.isEmpty()) {
                log("The sale has no money leg (rewards covered everything) — only a void could reverse it")
                return@launch
            }
            // Attached only while the store says the award is standing: the
            // SDK's own guard lives inside one ReversalSession, so without
            // this a retry (or a later partial refund) would reverse the
            // same award again on a fresh session
            val award = sale.leg(LegType.AWARD)?.takeUnless { stored.awardReversed }
            if (skus == null) {
                // A full refund returns EVERY tender leg still standing — a
                // split tender refunds the card and the gift card legs, each
                // by its own linked refund; a leg an earlier full refund
                // already returned is skipped, so a partial failure retries
                // only what is outstanding
                executeRefund(
                    conn, sale,
                    legs = stored.moneyLegs.filterNot { stored.legRefunded(it.type) },
                    items = null,
                    award = award,
                )
                return@launch
            }
            // Item refunds draw from the primary OUTSTANDING tender: the
            // card leg unless a full refund already returned it (a split
            // tender whose card leg was refunded but whose gift card leg
            // failed must not touch the card transaction again), else the
            // stored value leg. Ring only what earlier refunds have not
            // returned yet — the store's refund history caps every line.
            val outstanding = stored.moneyLegs.filterNot { stored.legRefunded(it.type) }
            val primaryLeg = outstanding.firstOrNull { it.type == LegType.CARD }
                ?: outstanding.firstOrNull()
            if (primaryLeg == null) {
                // unreachable while refundable, kept against future drift
                log("Every tender leg was already refunded in full — nothing left to draw from")
                return@launch
            }
            val items = sale.items.filter { it.sku in skus }.mapNotNull { item ->
                val remaining = item.quantity - stored.refundedQuantity(item.sku)
                when {
                    remaining >= item.quantity -> item
                    remaining > 0 -> {
                        log(
                            "${item.description}: ${item.quantity - remaining} of " +
                                "${item.quantity} already refunded — returning the remaining $remaining"
                        )
                        item.copy(quantity = remaining)
                    }
                    else -> {
                        log("${item.description} is already fully refunded — skipped")
                        null
                    }
                }
            }
            if (items.isEmpty()) {
                log("Nothing left to refund among the selected items")
                return@launch
            }
            executeRefund(conn, sale, legs = listOf(primaryLeg), items = items, award = award)
        }
        // Releases on every path, including a job cancelled before it ran
        // (disconnect racing this call). The claim always (per-connection);
        // the shared UI flag only while this connection is still current —
        // mirrors finishPaymentAttempt.
        job.invokeOnCompletion {
            conn.operationClaimed.set(false)
            if (connection === conn) {
                _state.update { it.copy(refundInProgress = false) }
            }
        }
    }

    /**
     * Runs the referenced refund, blocking the calling IO coroutine: one
     * fresh [ReversalSession] per tender leg in [legs] — a full linked
     * refund of each when [items] is null, otherwise an item-based refund
     * of the returned [items] via the (single) leg's refund cart. Each
     * leg's refund is recorded into the store as its money moves, so a
     * failure mid-way keeps what already landed and a retry covers only
     * the outstanding legs. The success popup publishes once every leg is
     * through; a failure publishes its own popup and stops the sequence.
     */
    private fun executeRefund(
        conn: Connection,
        sale: SaleRecord,
        legs: List<TransactionLeg>,
        items: List<SaleItem>?,
        award: TransactionLeg?,
    ) {
        val results = mutableListOf<Pair<TransactionLeg, RefundResult>>()
        var recorded = true
        // one award, one reversal: the references ride along until a leg's
        // session actually reverses the award, then never again — the
        // record of the reversal is what a retry checks
        var awardPending = award
        for (leg in legs) {
            val attached = awardPending
            val run = refundLeg(conn, sale, leg, items, attached) ?: return
            val awardReversed = attached != null && !run.awardFailed
            if (awardReversed) {
                awardPending = null
            }
            // Money moved on the terminal, so the refund is recorded even
            // when the operator disconnected mid-call — same rationale as
            // persistSale
            recorded = recordRefund(sale.id, leg, run.result, items, awardReversed) && recorded
            results += leg to run.result
        }
        if (connection === conn) {
            publishRefundResult(results, recorded)
        }
    }

    /** One leg's outcome: the terminal's result, and whether the attached
     *  award reversal failed (and was skipped) rather than committed. */
    private class LegRun(val result: RefundResult, val awardFailed: Boolean)

    /** One leg's linked refund on its own [ReversalSession]; null after a
     *  failure, which is logged and published as the outcome popup. */
    private fun refundLeg(
        conn: Connection,
        sale: SaleRecord,
        leg: TransactionLeg,
        items: List<SaleItem>?,
        award: TransactionLeg?,
    ): LegRun? {
        log(
            "Starting referenced refund of sale ${sale.id.take(8)} " +
                "(${leg.type} leg ${leg.poiTransactionId}" +
                (award?.let { ", award ${it.poiTransactionId}" } ?: "") + ")…"
        )
        try {
            val builder = ReversalSession.builder()
                .client(conn.client)
                // the record persisted the original sale's identity exactly
                // so a later referenced reversal can present it
                .saleId(sale.saleId)
                .poiId(sale.poiId)
                .currency(sale.currency)
                .poiTransactionId(leg.poiTransactionId)
            parseInstant(leg.poiTimestamp)?.let(builder::poiTransactionTimestamp)
            award?.let {
                builder.awardPoiTransactionId(it.poiTransactionId)
                parseInstant(it.poiTimestamp)?.let(builder::awardPoiTransactionTimestamp)
            }
            sale.memberId?.let(builder::memberId)
            val session = builder.start().get()
            // published for abort() while this leg is on the wire
            conn.reversal = session
            try {
                session.use {
                    return runLegFlow(session, items)
                }
            } finally {
                conn.reversal = null
            }
        } catch (e: SessionException) {
            if (connection === conn) {
                if (e.error.code == SessionErrorCode.ABORTED) {
                    log("Refund aborted (${leg.type} leg) — this leg was not refunded")
                } else {
                    log("Refund failed (${leg.type} leg): ${e.error.code} — ${e.error.message}")
                }
                _state.update {
                    it.copy(paymentOutcome = PaymentOutcome(
                        success = false,
                        title = "Refund failed",
                        message = "${e.error.code}\n${e.error.message}",
                    ))
                }
            }
            return null
        } catch (e: Exception) {
            // e.g. the session start being rejected by the terminal
            if (connection === conn) {
                log("Refund not completed (${leg.type} leg): ${e.message}")
                _state.update {
                    it.copy(paymentOutcome = PaymentOutcome(
                        success = false,
                        title = "Refund failed",
                        message = "Refund not completed\n${e.message}",
                    ))
                }
                detailedLog(e.stackTraceToString())
            }
            return null
        }
    }

    /** The leg's refund flow on its started [session]: full linked refund,
     *  or item-based via the refund cart. Failures throw to [refundLeg]. */
    private fun runLegFlow(
        session: ReversalSession,
        items: List<SaleItem>?,
    ): LegRun? {
        // Handlers of the blocking accessors run inline on this thread, so
        // a plain var sees the write; set when the award step failed and
        // was skipped — the award then stands, and the record must say so
        var awardFailed = false
        val flow = if (items == null) {
            session.refund()
        } else {
            items.forEach { item ->
                // rung exactly as sold — shelf price and tax rate — so the
                // credit total is price plus tax of the returns;
                // rebate/points proration is deliberately out of scope,
                // like the all-or-nothing award
                session.basket().addItem(
                    BasketItem.builder()
                        .sku(item.sku)
                        .description(item.description)
                        .quantity(item.quantity)
                        .unitPrice(BigDecimal(item.unitPrice))
                        .apply { item.category?.let(::category) }
                        .apply { item.taxRate?.let { rate -> taxRate(BigDecimal(rate)) } }
                        .build()
                )
            }
            session.refundBasket()
        }
        val result = flow
            .onError { step, error ->
                log("Refund step ${step ?: "(none ran)"} failed: ${error.message}")
                // the SDK's default policy, replicated so logging doesn't
                // change behavior: the award reversal is best-effort, a
                // failed tender refund aborts
                if (step == ReversalStep.AWARD) {
                    awardFailed = true
                    ReversalDecision.SKIP
                } else {
                    ReversalDecision.ABORT
                }
            }
            .get()
        return result.takeIf { it.isSuccess }?.let { LegRun(it, awardFailed) }
    }

    /** Stores the refund against its sale — including which tender leg it
     *  drew from and what it covered, so a later refund can't return the
     *  same thing again. A storage failure cannot fail the refund (the
     *  money already moved) but must not stay quiet either — without the
     *  record the sale offers the same refund again, and a terminal that
     *  accepts it would return the money twice. False on failure, so the
     *  outcome popup carries the warning. */
    private fun recordRefund(
        saleId: String,
        leg: TransactionLeg,
        result: RefundResult,
        items: List<SaleItem>?,
        awardReversed: Boolean,
    ): Boolean {
        return try {
            saleStore.recordRefund(saleId, RefundRecord(
                amount = result.refundedAmount?.toPlainString(),
                poiTransactionId = result.poiTransactionId,
                poiTimestamp = result.poiTransactionTimestamp?.toString(),
                recordedAt = Instant.now().toString(),
                full = items == null,
                leg = leg.type,
                awardReversed = awardReversed,
                items = items.orEmpty().map { RefundedItem(it.sku, it.quantity) },
            ))
            // keep the Refund tab's list (refunded badges) current
            refreshSales()
            true
        } catch (e: Exception) {
            log("Failed to store the refund: ${e.message}")
            detailedLog(e.stackTraceToString())
            false
        }
    }

    /** Publishes the outcome of a completed refund — one entry per tender
     *  leg refunded (a split tender lists both). [recorded] false means the
     *  refund history write failed: the money moved, so the popup stays a
     *  success, but it must warn that the sale will offer this refund
     *  again. */
    private fun publishRefundResult(
        results: List<Pair<TransactionLeg, RefundResult>>,
        recorded: Boolean,
    ) {
        val parts = buildList {
            results.forEach { (leg, result) ->
                add(
                    // the terminal does not always echo the refunded amount
                    "Refunded" + (result.refundedAmount?.let { " $${it.toPlainString()}" } ?: "") +
                        (if (results.size > 1) " (${legLabel(leg.type)})" else "")
                )
            }
            results.map { it.second }.firstOrNull { it.pointsReversed > 0 }?.let {
                add("reversed ${it.pointsReversed} pts (balance ${it.remainingPointBalance})")
            }
            results.mapNotNull { it.second.approvalCode }.forEach { add("approval $it") }
            if (!recorded) {
                add(
                    "WARNING: the refund could NOT be recorded — the sale " +
                        "will still offer what was just refunded; refunding " +
                        "it again would return the money twice"
                )
            }
        }
        val summary = parts.joinToString(", ")
        // the receipt of the first leg — the primary tender's copy
        val receipt = results.firstNotNullOfOrNull { (_, result) ->
            receiptText(result.customerReceipt, result.merchantReceipt)
        }
        _state.update {
            it.copy(paymentOutcome = PaymentOutcome(
                success = true,
                title = "Refund complete",
                message = parts.joinToString("\n"),
                receipt = receipt,
            ))
        }
        log(summary)
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
        // A refund in flight takes precedence — while one runs, no checkout
        // session can be active (they refuse to start together). abort() is
        // unordered on both session kinds: execute() overtakes the in-flight
        // call instead of queueing behind it.
        val reversal = conn.reversal
        if (reversal != null) {
            log("Aborting the refund…")
            reversal.abort()
                .onError { error ->
                    log("Abort failed: ${error.message}")
                    error.cause?.let { detailedLog(it.stackTraceToString()) }
                }
                .execute()
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

    private fun publishPaymentResult(result: SettlementResult) {
        result.finalBasket?.let(::publishBasket)
        val parts = buildList {
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
        val summary = "Paid $${result.authorizedAmount.toPlainString()}" +
            (if (parts.isEmpty()) "" else " — " + parts.joinToString(", "))
        // the popup carries the promo messages and warnings that otherwise
        // live only in the event log
        val popup = buildList {
            add("Paid $${result.authorizedAmount.toPlainString()}")
            addAll(parts)
            result.promotionMessages.forEach { add(it) }
            result.warnings.forEach { add("Warning: $it") }
        }.joinToString("\n")
        _state.update {
            it.copy(
                lastPayment = summary,
                paymentOutcome = PaymentOutcome(
                    success = true,
                    title = "Payment successful",
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
            val remaining = (item.quantity - refundedQuantity(item.sku))
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
        voided = voided != null,
    )

    /** What an item-based refund of [quantity] units of this line returns,
     *  in cents: shelf price plus tax, computed exactly like the refund
     *  cart's credit line (gross × rate, HALF_UP at scale 2) so the amount
     *  shown on the Refund button is the amount charged. A malformed amount
     *  degrades to zero rather than dropping the sale. */
    private fun refundMinor(item: SaleItem, quantity: Int): Long = try {
        val gross = BigDecimal(item.unitPrice).multiply(BigDecimal(quantity))
        val tax = item.taxRate
            ?.let { gross.multiply(BigDecimal(it)).setScale(2, RoundingMode.HALF_UP) }
            ?: BigDecimal.ZERO
        gross.add(tax).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
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
