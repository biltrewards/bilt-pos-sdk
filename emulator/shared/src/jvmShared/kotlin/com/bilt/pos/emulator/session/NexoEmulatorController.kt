package com.bilt.pos.emulator.session

import com.bilt.pos.emulator.catalog.Product
import com.bilt.pos.emulator.store.SaleStore
import com.bilt.pos.emulator.store.StoredSale
import com.bilt.pos.emulator.store.toSaleRecord
import com.bilt.pos.nexo.client.BiltNexoTerminalClient
import com.bilt.pos.nexo.security.SecurityKey
import com.bilt.pos.session.CheckoutSession
import com.bilt.pos.session.SessionErrorCode
import com.bilt.pos.session.Terminal
import com.bilt.pos.session.basket.Basket
import com.bilt.pos.session.basket.BasketItem
import com.bilt.pos.session.identity.CardAcquisitionOptions
import com.bilt.pos.session.identity.ForceEntryMode
import com.bilt.pos.session.identity.IdentifyOptions
import com.bilt.pos.session.identity.IdentifyStatus
import com.bilt.pos.session.payment.CheckoutResult
import com.bilt.pos.session.payment.PaymentOptions
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

    private class Connection(
        val scope: CoroutineScope,
        val dispatcher: CoroutineDispatcher,
        val client: BiltNexoTerminalClient,
        val terminal: Terminal,
        @Volatile var session: CheckoutSession? = null,
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

    /** Serial, so overlapping refreshes publish in launch order and the
     *  newest sale wins. Declared before the init block, which triggers
     *  refreshSales(). */
    private val salesRefreshDispatcher = Dispatchers.IO.limitedParallelism(1)

    init {
        // SDK internals log via JUL; surface them on the Detailed tab
        JulLogCapture.install(::detailedLog)
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

    override fun connect(address: String, encryptionEnabled: Boolean, passphraseOverride: String?) {
        if (!tryDisconnect()) {
            return
        }

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

        val client = createNexoClient(endpoint, encrypt, passphrase) ?: return
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
        )
        connection = conn

        runDiagnosisLoop(conn, terminal)
        verifyTls(conn, address)
    }

    /** Strict TLS probe, independent of the always-permissive payload
     *  channel. A CA that was configured but failed to load reports as a
     *  TLS failure, not as "no CA configured". */
    private fun verifyTls(conn: Connection, address: String) {
        conn.scope.launch(Dispatchers.IO) {
            val tls = config.caError?.let { TlsStatus.Failed(it) }
                ?: TlsVerifier.verify(address, 8443, config.caPem, config.hostnamePattern)
            if (isActive) {
                _state.update { it.copy(tls = tls) }
                log(tls.label)
            }
        }
    }

    private fun createNexoClient(
        endpoint: String,
        encrypt: Boolean,
        passphrase: String?
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
            // e.g. an unparsable address; reported instead of thrown
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
        val conn = connection ?: run {
            updateDisconnectedState()
            return true
        }
        if (_state.value.paymentInProgress) {
            log("A payment is in progress — abort it or wait for it to finish before disconnecting")
            return false
        }
        connection = null
        conn.scope.cancel()
        conn.terminal.close()
        // End bracket, best-effort: the async end() queues behind anything
        // still in flight on the session's operation thread
        conn.session?.end()?.onError { error ->
            detailedLog("End bracket on disconnect failed: ${error.message}")
        }?.execute()
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
        val conn = connection
        if (conn != null) {
            connection = null
            conn.scope.cancel()
            conn.terminal.close()
            conn.session?.let { runCatching { it.close() } }
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

    override fun pay(loyalty: LoyaltyOptions, storedValue: StoredValueOptions?) {
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
        // Outcome markers for onComplete: an attempt that settles with
        // neither set was aborted — the SDK bypasses onError by design when
        // the register asked for the abort. Plain vars are safe on ANY
        // executor, pool included: the payment thread awaits each handler
        // and only then submits the next, so handlers never overlap and
        // each submission carries a happens-before edge.
        var succeeded = false
        var failed = false
        // Set or actively cleared each attempt: the session keeps the card
        // across a retry, and a toggle switched off in between must not
        // silently charge the card again
        val card = storedValue?.toCard()
        session.setStoredValueCard(card)

        val options = PaymentOptions.builder()
            .disableRebates(!loyalty.rebates)
            .disablePoints(!loyalty.redemption)
            .disableAward(!loyalty.award)
            .build()
        log(
            "Starting payment — rebates ${onOff(loyalty.rebates)}, " +
                "redemption ${onOff(loyalty.redemption)}, award ${onOff(loyalty.award)}" +
                (card?.let { ", gift card ${it.storedValueId ?: "(swipe on terminal)"}" } ?: "")
        )
        session.pay(options)
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
                failed = true
                if (connection === conn) {
                    log("Payment failed: ${error.code} — ${error.message}")
                    _state.update {
                        it.copy(paymentOutcome = PaymentOutcome(
                            success = false,
                            message = "${error.code}\n${error.message}",
                        ))
                    }
                }
                PaymentOptions.voidAndAbort()
            }
            .onSuccess { result ->
                succeeded = true
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
                if (!succeeded && connection === conn) {
                    if (!failed) {
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

    override fun abort() {
        val conn = connection
        val session = conn?.session
        if (conn == null || session == null) {
            log("No active checkout session")
            return
        }
        // abort() is unordered: execute() overtakes the in-flight call
        // instead of queueing behind it
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
    private fun persistSale(sessionId: String, memberId: String?, result: CheckoutResult) {
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

    private fun publishPaymentResult(result: CheckoutResult) {
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
                paymentOutcome = PaymentOutcome(success = true, message = popup),
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
        items = sale.items.map {
            SaleItemUi(it.sku, it.description, it.quantity, minorUnits(it.lineTotal))
        },
        refunded = refunds.isNotEmpty(),
        voided = voided != null,
    )

    /** Cents from the store's plain decimal string; a malformed amount
     *  degrades to zero rather than dropping the sale. */
    private fun minorUnits(amount: String): Long = try {
        BigDecimal(amount).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
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
