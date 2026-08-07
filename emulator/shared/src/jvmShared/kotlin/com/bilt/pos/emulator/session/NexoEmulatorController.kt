package com.bilt.pos.emulator.session

import com.bilt.pos.emulator.catalog.Product
import com.bilt.pos.emulator.store.SaleStore
import com.bilt.pos.emulator.store.StoredSale
import com.bilt.pos.emulator.store.toSaleRecord
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
import com.bilt.pos.session.SessionErrorCode
import com.bilt.pos.session.SessionException
import com.bilt.pos.session.SessionState
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
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Display format for stored sales' completion times. Top-level, not an
 *  instance property: the constructor-launched sales refresh may format
 *  labels before instance initializers further down the class have run
 *  (they execute in textual order), whereas file-level vals are initialized
 *  on class load, before any access. */
private val saleTimeFormat =
    DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault())

/**
 * The emulator session engine. A connection to the terminal and a checkout
 * session are separate lifecycles:
 *
 * - **Connect** builds the client and runs the periodic diagnostics loop
 *   (raw nexo Diagnosis requests — pure connectivity, no session involved).
 * - **Start Checkout** opens a [CheckoutSession] (terminal Start bracket) on
 *   that connection: one session per customer checkout. **End Checkout**
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
    /** Persists completed sales for later referenced refunds/voids. */
    private val saleStore: SaleStore,
) : EmulatorController {

    private class Connection(
        val scope: CoroutineScope,
        val dispatcher: CoroutineDispatcher,
        @Volatile var client: BiltNexoTerminalClient? = null,
        @Volatile var session: CheckoutSession? = null,
    ) {
        /** Claimed synchronously by [pay] and [acquireCard] — one claim
         *  across both, not one each: the UI's disabled states publish only
         *  on recomposition, so a quick tap on the other button would
         *  otherwise claim its own flag and silently queue behind the
         *  in-flight operation on the serialized dispatcher. */
        val operationClaimed = java.util.concurrent.atomic.AtomicBoolean(false)

        /** Set by [abort] for the current payment attempt. SDK
         *  abort() only interrupts what is on the wire — during the
         *  pre-payment identify prompt the session is not yet PAYING, so
         *  without this flag a cancelled prompt would read as a guest
         *  outcome and the payment would continue to charge. */
        val paymentAbortRequested = java.util.concurrent.atomic.AtomicBoolean(false)
    }

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

    /** Serialized so overlapping refreshes publish in launch order — each
     *  refresh is launched after its sale is recorded, so with FIFO
     *  execution the snapshot published last always includes the newest
     *  sale; concurrent refreshes could land an older read on top of it.
     *  Declared before the init block: refreshSales() reads it during
     *  construction, and property initializers run in textual order. */
    private val salesRefreshDispatcher = Dispatchers.IO.limitedParallelism(1)

    init {
        // SDK diagnostics (client, session, payment internals) log via JUL;
        // surface them on the Detailed tab
        JulLogCapture.install(::detailedLog)
        // Populate the Refund tab's list; recordSale keeps it current from
        // here on, so this initial load is the only pull
        refreshSales()
    }

    override fun autodetectAddress() {
        scope.launch(Dispatchers.IO) {
            val detected = TerminalAddressDetector.detect()
            if (detected != null) {
                _state.update { it.copy(terminalAddress = detected, addressAutodetected = true) }
                log("Autodetected terminal address $detected via adb")
                // The operator's next move after a successful detect is
                // always Connect — do it for them. Only from DISCONNECTED:
                // autodetect runs at startup and must never tear down a
                // connection the operator made while adb was probing.
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
            // cancelling the app scope right after doesn't kill it. Like
            // shutdown(), wait for the serialized jobs first: an in-flight
            // payment's blocking call survives the cancel and holds the
            // session in PAYING, where end() is rejected — closing right
            // away would lose the End bracket for good once the payment
            // lands. The cap must outlast the client's read timeout (120s
            // default) so the join covers a terminal that answers late.
            teardownScope.launch {
                withTimeoutOrNull(180_000) { conn.scope.coroutineContext[Job]?.join() }
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
                        lastPayment = null,
                    )
                }
                log("Checkout session started (id ${started.sessionId})")
                // Blank the customer display: autoDisplay only fires on
                // basket mutations, so the previous checkout's receipt would
                // linger until the first item is rung in. Best-effort — a
                // failure logs via JUL (Detailed tab) and never throws.
                started.updateDisplay(started.basket().snapshot())
                log("Customer display cleared (empty basket)")
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
                // offering Start Checkout. Disconnect remains the escape hatch.
                conn.session = null
                if (currentCoroutineContext().isActive) {
                    _state.update {
                        it.copy(
                            sessionId = null,
                            basket = emptyList(),
                            basketTotal = "0.00",
                            basketTax = "0.00",
                            lastPayment = null,
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
            log("No active checkout session — press Start Checkout first")
            return
        }
        conn.scope.launch(conn.dispatcher) {
            // Read inside the serialized job (same as start/endSession) so an
            // End-then-tap sequence can't ring an item into a session already
            // cleared for its End bracket
            val current = conn.session
            if (current == null) {
                log("No active checkout session — press Start Checkout first")
                return@launch
            }
            try {
                val existing = current.basket().snapshot().getItemBySku(product.sku)
                val basket = if (existing == null) {
                    val item = BasketItem.builder()
                        .sku(product.sku)
                        .description(product.name)
                        .category(product.category)
                        .quantity(1)
                        .unitPrice(BigDecimal(product.priceDecimal))
                        .apply { NjSalesTax.rateFor(product)?.let(::taxRate) }
                        .build()
                    current.basket().addItem(item)
                } else {
                    current.basket().updateItemQuantityBySku(product.sku, existing.quantity + 1)
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

    override fun pay(loyalty: LoyaltyOptions, storedValue: StoredValueOptions?) {
        val conn = connection
        if (conn == null) {
            log("No active checkout session — press Start Checkout first")
            return
        }
        // Claimed here, not inside the job: two quick Pay taps would both
        // queue on the serialized dispatcher (paymentInProgress publishes
        // asynchronously), and the second would reach the already-completed
        // session only to log a spurious failure. Shared with acquireCard —
        // a Pay tap during a card read must not queue behind the prompt.
        if (!conn.operationClaimed.compareAndSet(false, true)) {
            log("Another operation is already in progress")
            return
        }
        conn.paymentAbortRequested.set(false)
        _state.update { it.copy(paymentInProgress = true, paymentOutcome = null) }
        val job = conn.scope.launch(conn.dispatcher) {
            val session = conn.session
            if (session == null) {
                log("No active checkout session — press Start Checkout first")
                return@launch
            }
            try {
                // The prompt runs only when the operator asked for it — the
                // loyalty toggles alone don't force it, since the customer
                // may self-identify on the terminal during the flow. A
                // no-member outcome (not found, cancelled) degrades to a
                // guest checkout instead of blocking the payment. FAILED is
                // included: a declined/cancelled payment retried with
                // identification switched on prompts before the retry.
                if (conn.paymentAbortRequested.get()) {
                    log("Payment aborted before it started — nothing charged")
                    return@launch
                }
                if (loyalty.identify && session.member == null) {
                    when (session.state) {
                        SessionState.IDLE, SessionState.IDENTIFIED, SessionState.ACTIVE,
                        SessionState.FAILED,
                        -> identifyMember(session)
                        else -> log(
                            "Member identification is unavailable in state " +
                                "${session.state} — paying as a guest checkout"
                        )
                    }
                }
                // An abort during the identify prompt cancels only the
                // prompt on the wire (the session is not PAYING yet), which
                // would otherwise read as a guest outcome — the attempt
                // flag is what stops the charge
                if (conn.paymentAbortRequested.get()) {
                    log("Payment aborted during member identification — nothing charged")
                    return@launch
                }
                if (!currentCoroutineContext().isActive) return@launch

                // Registered per attempt: set when requested, actively
                // cleared otherwise — the session keeps the card across a
                // retry, so a toggle switched off between attempts must
                // remove it rather than silently charge the card again
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
                val flow = session.pay(options)
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
                        session.updateDisplay(session.basket().snapshot())
                        if (isActive) {
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
                val result = try {
                    flow.get()
                } catch (e: SessionException) {
                    // ABORTED bypasses onError by design (the register asked
                    // for the abort); every other failure was already
                    // reported by the handler above
                    if (e.error.code == SessionErrorCode.ABORTED && isActive) {
                        session.updateDisplay(session.basket().snapshot())
                        log(
                            "Payment aborted; committed steps reversed — " +
                                "the basket is intact, Pay again to retry"
                        )
                    }
                    null
                }
                // Today a non-null result IS a success (the orchestrator's
                // only return site follows the last step; failures throw),
                // so the isSuccess guard is defense in depth against the SDK
                // ever returning a failed result — a declined attempt must
                // not enter the store as a refundable sale.
                if (result != null && result.isSuccess) {
                    // The terminal charged the customer, so the sale is
                    // recorded even when a disconnect cancelled this job
                    // mid-call (cancellation can't stop the blocking SDK
                    // call, and the charge stands) — deliberately NOT gated
                    // on isActive like the UI updates below: a charged
                    // transaction without its stored references could never
                    // be refunded or voided later. Before the session ends
                    // for the same reason: end() clears the member the
                    // record captures, and a failed end must not cost the
                    // sale its references either.
                    persistSale(session, result)
                }
                if (result != null && isActive) {
                    publishPaymentResult(result)
                    // The checkout is collected in full (pay() completes
                    // only then) — end the session for the operator. The
                    // basket clears like any other end; the payment summary
                    // stays visible until the next Start Checkout.
                    log("Payment complete — ending the checkout automatically")
                    try {
                        session.end().get()
                        conn.session = null
                        _state.update {
                            it.copy(
                                sessionId = null,
                                basket = emptyList(),
                                basketTotal = "0.00",
                                basketTax = "0.00",
                            )
                        }
                        log("Checkout ended")
                    } catch (e: Exception) {
                        log("Failed to end the checkout: ${e.message} — press End Checkout to retry")
                        detailedLog(e.stackTraceToString())
                    }
                }
            } catch (e: Exception) {
                // e.g. pay() rejecting an empty basket or a completed session
                if (isActive) {
                    log("Payment not started: ${e.message}")
                    _state.update {
                        it.copy(paymentOutcome = PaymentOutcome(
                            success = false,
                            message = "Payment not started\n${e.message}",
                        ))
                    }
                    detailedLog(e.stackTraceToString())
                }
            }
        }
        // Releases on every path, including a job the scope cancelled before
        // it ever ran (disconnect racing this call) — a finally inside the
        // job would never execute then and the claim would wedge the Pay
        // button. The claim is per-connection and always released; the
        // shared UI flag is cleared only while this connection is still
        // current — a cancelled job's blocking call can outlive a
        // disconnect, and its late completion must not re-enable Pay while
        // a NEW connection's payment is on the wire (the disconnect itself
        // already reset the flag via updateDisconnectedState).
        job.invokeOnCompletion {
            conn.operationClaimed.set(false)
            if (connection === conn) {
                _state.update { it.copy(paymentInProgress = false) }
            }
        }
    }

    override fun acquireCard() {
        val conn = connection
        if (conn == null) {
            log("No active checkout session — press Start Checkout first")
            return
        }
        // Claimed here, not inside the job (same reasoning as pay): two
        // quick taps would both queue on the serialized dispatcher before
        // cardReadInProgress publishes, prompting the terminal twice. The
        // claim is shared with pay for the same reason across buttons.
        if (!conn.operationClaimed.compareAndSet(false, true)) {
            log("Another operation is already in progress")
            return
        }
        _state.update { it.copy(cardReadInProgress = true) }
        val job = conn.scope.launch(conn.dispatcher) {
            // Read inside the serialized job (same as the other session
            // operations) so it can't interleave with an in-flight start/end
            val session = conn.session
            if (session == null) {
                log("No active checkout session — press Start Checkout first")
                return@launch
            }
            log("Reading card on the terminal (CardAcquisition, MagStripe/Scanned)…")
            // ForceEntryMode is sent explicitly: without it the request
            // carries no entry-mode element and the terminal arms only its
            // default reader set, which may not include the stripe.
            // MagStripe + Scanned are the gift-card capture methods.
            session.acquireCard(
                CardAcquisitionOptions.builder()
                    .forceEntryMode(ForceEntryMode.MAG_STRIPE)
                    .forceEntryMode(ForceEntryMode.SCANNED)
                    .build()
            )
                .onSuccess { acquired ->
                    if (!isActive) return@onSuccess
                    val label = listOfNotNull(
                        acquired.paymentBrand,
                        acquired.maskedPan ?: acquired.truncatedPan,
                        acquired.entryMode?.let { "via $it" },
                    ).joinToString(" ").ifEmpty { "no card details returned" }
                    val number = acquired.rawPan
                    if (number.isNullOrBlank()) {
                        // Masked/truncated reads carry no chargeable number —
                        // only PLCC-range cards (gift cards among them)
                        // return the full PAN
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
                    if (!isActive) return@onError
                    if (error.code == SessionErrorCode.ABORTED) {
                        log("Card read aborted")
                    } else {
                        log("Card read failed: ${error.message}")
                        error.cause?.let { detailedLog(it.stackTraceToString()) }
                    }
                }
                .execute()
        }
        // Releases on every path, including a job cancelled before it ran
        // (disconnect racing this call) — mirrors the pay() completion hook
        job.invokeOnCompletion {
            conn.operationClaimed.set(false)
            if (connection === conn) {
                _state.update { it.copy(cardReadInProgress = false) }
            }
        }
    }

    override fun abort() {
        val conn = connection
        val session = conn?.session
        if (conn == null || session == null) {
            log("No active checkout session")
            return
        }
        // The attempt flag covers the window the SDK cannot: during the
        // pre-payment identify prompt the session is not PAYING, so abort()
        // only cancels the prompt — the pay job checks this flag before it
        // lets the payment start. Setting it with no payment in flight
        // (e.g. aborting a card read) is harmless: pay() resets it when
        // the next attempt is claimed.
        conn.paymentAbortRequested.set(true)
        // Deliberately NOT on the serialized dispatcher: abort() is the
        // SDK's cross-thread entry point, and it must overtake the blocking
        // call it interrupts — queueing it would run it only after that
        // call finished on its own. Operation-scoped like the SDK's: an
        // aborted payment settles FAILED and the session stays retryable;
        // an aborted prompt or card read is simply cancelled.
        conn.scope.launch(Dispatchers.IO) {
            log("Aborting…")
            try {
                session.abort()
            } catch (e: Exception) {
                if (isActive) {
                    log("Abort failed: ${e.message}")
                    detailedLog(e.stackTraceToString())
                }
            }
        }
    }

    /** Terminal member-identification prompt; failures degrade to guest. */
    private fun identifyMember(session: CheckoutSession) {
        log("Identifying member on the terminal…")
        try {
            // The terminal's keyed loyalty capture (loyalty ID input form)
            // engages only for LoyaltyHandling=Required (the SDK default)
            // with ForceEntryMode containing Keyed — without Keyed it waits
            // on the card reader instead of showing the input form
            val outcome = session.identifyMember(
                IdentifyOptions.builder()
                    .forceEntryMode(ForceEntryMode.KEYED)
                    .build()
            ).get()
            if (outcome.status == IdentifyStatus.FOUND) {
                log(
                    "Member identified: ${outcome.memberId}" +
                        (outcome.loyaltyBrand?.let { " ($it)" } ?: "") +
                        ", ${outcome.pointBalance} pts, ${outcome.rewards.size} reward(s)"
                )
            } else {
                log("No member (${outcome.status}) — loyalty steps will be skipped")
            }
        } catch (e: Exception) {
            log("Member identification failed: ${e.message} — continuing as guest")
            detailedLog(e.stackTraceToString())
        }
    }

    /**
     * Stores the completed sale with every transaction leg's POI reference,
     * so referenced refunds/voids can run after the session is gone.
     * Best-effort: a storage failure must never fail the checkout.
     */
    private fun persistSale(session: CheckoutSession, result: CheckoutResult) {
        try {
            val record = result.toSaleRecord(
                sessionId = session.sessionId,
                saleId = config.saleId,
                poiId = config.poiId,
                currency = config.currency,
                memberId = session.member?.memberId,
                recordId = UUID.randomUUID().toString(),
                completedAt = Instant.now(),
            )
            saleStore.recordSale(record)
            log("Sale stored (${record.legs.size} transaction leg(s), id ${record.id})")
            // keep the Refund tab's list live when a payment lands while
            // the operator is already looking at it
            refreshSales()
        } catch (e: Exception) {
            log("Failed to store the sale: ${e.message}")
            detailedLog(e.stackTraceToString())
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
        // the popup breaks the breakdown into lines and carries the promo
        // messages and warnings that otherwise live only in the event log
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
     *  connection scope: the stored sales outlive any connection, and
     *  browsing them must work while disconnected. */
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

    /** The UI's gift card tender as the SDK's stored value card: a typed
     *  number is keyed entry; blank hands entry to the terminal's swipe. */
    private fun StoredValueOptions.toCard(): StoredValueCard {
        val number = cardNumber.trim()
        return if (number.isEmpty()) StoredValueCard.swiped() else StoredValueCard.number(number)
    }

    private fun onOff(enabled: Boolean) = if (enabled) "on" else "off"

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
                paymentInProgress = false,
                cardReadInProgress = false,
                lastPayment = null,
                paymentOutcome = null,
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
