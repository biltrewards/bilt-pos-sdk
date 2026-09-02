package com.bilt.pos.emulator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bilt.pos.emulator.catalog.Product
import com.bilt.pos.emulator.catalog.minorUnitsToDecimal
import com.bilt.pos.emulator.session.BasketLine
import com.bilt.pos.emulator.session.BasketLineType
import com.bilt.pos.emulator.session.ConnectionPhase
import com.bilt.pos.emulator.session.EmulatorController
import com.bilt.pos.emulator.session.EmulatorState
import com.bilt.pos.emulator.session.LoyaltyOptions
import com.bilt.pos.emulator.session.PaymentOutcome
import com.bilt.pos.emulator.session.StoredSaleUi
import com.bilt.pos.emulator.session.StoredValueOptions

/** Top-level screens of the emulator. */
internal enum class EmulatorTab(val label: String) { SALE("Sale"), REFUND("Refund") }

/** Width at which tab content switches from stacked to side-by-side panes. */
private val WIDE_LAYOUT_BREAKPOINT = 700.dp

/** Width from which the event log moves into its own right-hand column;
 *  below it the log stacks under the tab content instead of starving it. */
private val SIDE_LOG_BREAKPOINT = 1000.dp

/**
 * Root composable of the terminal emulator, shared by the Android and
 * desktop targets. All interaction is driven through [controller], whose
 * [EmulatorController.state] this UI observes; [products] populates the
 * Sale tab's quick-buy grid.
 */
@Composable
fun EmulatorApp(controller: EmulatorController, products: List<Product>) {
    EmulatorApp(controller, products, EmulatorTab.SALE)
}

/**
 * [initialTab] seeds the tab selection — the screenshot generator renders
 * the Refund tab through it, since tab state is local and a headless test
 * cannot switch it after composition. Internal so the production entry
 * point above stays free of test-shaped surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmulatorApp(
    controller: EmulatorController,
    products: List<Product>,
    initialTab: EmulatorTab,
) {
    val state by controller.state.collectAsState()

    MaterialTheme {
        state.paymentOutcome?.let { outcome ->
            PaymentOutcomeDialog(outcome) { controller.dismissPaymentOutcome() }
        }
        // ordinal rather than the enum itself so rememberSaveable needs
        // no custom Saver
        var selectedTabIndex by rememberSaveable { mutableStateOf(initialTab.ordinal) }
        val selectedTab = EmulatorTab.entries[selectedTabIndex]
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Bilt POS Emulator") },
                    actions = { StatusIndicators(state) },
                )
            },
            bottomBar = {
                // Same container color as the cards, so the bar reads as a
                // panel rather than blending into the window background
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    EmulatorTab.entries.forEach { tab ->
                        Tab(
                            selected = tab == selectedTab,
                            onClick = { selectedTabIndex = tab.ordinal },
                            text = { Text(tab.label) },
                        )
                    }
                }
            },
        ) { padding ->
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            ) {
                val sideLog = maxWidth >= SIDE_LOG_BREAKPOINT
                // The basket sits above the tab content and is shared by
                // every tab: a settlement may mix new items (Sale tab) with
                // returns of prior sales (Refund tab) in one basket. The
                // event log is shared too — its own right-hand column when
                // the window is wide, stacked below otherwise. Weights, not
                // fillMaxSize(): a non-weighted child measures against the
                // full height and would overflow by its siblings' heights.
                val basketAndTab: @Composable ColumnScope.() -> Unit = {
                    BasketCard(state, controller, Modifier.fillMaxWidth().weight(1f))
                    when (selectedTab) {
                        EmulatorTab.SALE ->
                            SaleWorkspace(
                                products, state, controller,
                                Modifier.fillMaxWidth().weight(1.1f),
                            )
                        EmulatorTab.REFUND ->
                            RefundTab(state, controller, Modifier.fillMaxWidth().weight(1.1f))
                    }
                }
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ConnectionPanel(state, controller)
                    if (sideLog) {
                        Row(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(2f).fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                content = basketAndTab,
                            )
                            EventsCard(state, Modifier.weight(1f).fillMaxHeight())
                        }
                    } else {
                        basketAndTab()
                        EventsCard(state, Modifier.fillMaxWidth().weight(0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RefundTab(
    state: EmulatorState,
    controller: EmulatorController,
    modifier: Modifier = Modifier,
) {
    val sales = state.sales
    var selectedSaleId by rememberSaveable { mutableStateOf<String?>(null) }
    // resolve against the current list (a refresh may have dropped the
    // sale); with no explicit pick, default to the newest sale — the one a
    // refund most likely targets
    val selected = sales.firstOrNull { it.id == selectedSaleId } ?: sales.firstOrNull()

    val list: @Composable (Modifier) -> Unit = { m ->
        SalesListCard(sales, selected?.id, { selectedSaleId = it }, m)
    }
    // no details card without a sale — that only happens with nothing
    // stored, and the list's empty message is the single empty state
    val details: (@Composable (Modifier) -> Unit)? = selected?.let { sale ->
        { m -> RefundDetailsCard(sale, state, controller, m) }
    }
    BoxWithConstraints(modifier = modifier) {
        if (maxWidth < WIDE_LAYOUT_BREAKPOINT) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                list(Modifier.weight(1f))
                details?.invoke(Modifier.weight(1.2f))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                list(Modifier.weight(1f))
                details?.invoke(Modifier.weight(2f))
            }
        }
    }
}

@Composable
private fun SalesListCard(
    sales: List<StoredSaleUi>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Completed sales", style = MaterialTheme.typography.titleMedium)
            if (sales.isEmpty()) {
                Text(
                    "None stored yet — complete a payment on the Sale tab",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp).weight(1f),
                ) {
                    items(sales, key = { it.id }) { sale ->
                        val isSelected = sale.id == selectedId
                        Button(
                            onClick = { onSelect(sale.id) },
                            colors = if (isSelected) {
                                ButtonDefaults.buttonColors()
                            } else {
                                ButtonDefaults.filledTonalButtonColors()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "$${sale.totalAmount}",
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        sale.completedAtLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                Text(sale.statusLabel, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** What the Refund button returns: everything, or the checked items. */
private enum class RefundMode { FULL, ITEMS }

@Composable
private fun RefundDetailsCard(
    sale: StoredSaleUi,
    state: EmulatorState,
    controller: EmulatorController,
    modifier: Modifier = Modifier,
) {
    // A full refund voids every movement of the prior sale, so it is only
    // offered while no ITEM refunds exist — those would make it
    // over-return. The per-leg residue of a void that failed midway keeps
    // it available: retrying is how the outstanding tender gets finished.
    val fullAvailable = sale.fullRefundAvailable
    // keyed on the sale so picking another sale resets the selections
    var mode by remember(sale.id) {
        mutableStateOf(if (fullAvailable) RefundMode.FULL else RefundMode.ITEMS)
    }
    var selectedSkus by remember(sale.id) { mutableStateOf(emptySet<String>()) }
    // Gift-card loads and their funding must stay atomic: the controller
    // only unwinds them via a full refund, so mixed sales cannot offer their
    // ordinary merchandise as a separate item refund.
    val hasRecordedItems = sale.items.isNotEmpty()
    val itemsAvailable = hasRecordedItems && !sale.hasGiftCardPurchase

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Refund", style = MaterialTheme.typography.titleMedium)
            Text(
                "$${sale.totalAmount} · ${sale.completedAtLabel} · ${sale.memberLabel}",
                style = MaterialTheme.typography.bodyMedium,
            )
            when {
                sale.voided -> Text(
                    "Voided — nothing left to refund",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                sale.fullyRefunded -> Text(
                    "Refunded in full — nothing left to refund",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                sale.refunded && fullAvailable -> Text(
                    "Partially refunded — Full amount reverses what is outstanding",
                    style = MaterialTheme.typography.bodySmall,
                )
                sale.refunded -> Text(
                    "Already partially refunded",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabeledRadio("Full amount", mode == RefundMode.FULL,
                    sale.refundable && fullAvailable) {
                    mode = RefundMode.FULL
                }
                LabeledRadio(
                    "Selected items",
                    mode == RefundMode.ITEMS,
                    sale.refundable && itemsAvailable,
                ) {
                    mode = RefundMode.ITEMS
                }
            }
            if (hasRecordedItems) {
                if (sale.hasGiftCardPurchase) {
                    Text(
                        "Contains a gift card purchase — refund the full sale to reverse " +
                            "the load and its funding together",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(sale.items, key = { it.sku }) { item ->
                        // rows show what a refund can still return: the
                        // remaining quantity and its value, with the already
                        // returned part called out
                        val description = if (item.refundedQuantity > 0) {
                            "${item.description} (${item.refundedQuantity} of " +
                                "${item.quantity} refunded)"
                        } else {
                            item.description
                        }
                        LineItemRow(item.remainingQuantity, description, item.refundLabel) {
                            Checkbox(
                                checked = item.sku in selectedSkus,
                                onCheckedChange = { checked ->
                                    selectedSkus =
                                        if (checked) selectedSkus + item.sku
                                        else selectedSkus - item.sku
                                },
                                // the refundable guard is not redundant with the
                                // radio's: a refresh can void the sale while the
                                // mode is already ITEMS
                                enabled = itemsAvailable && mode == RefundMode.ITEMS &&
                                    sale.refundable && item.remainingQuantity > 0,
                            )
                        }
                    }
                }
            } else {
                Text(
                    "No line items recorded for this sale — only a full refund is possible",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f).padding(top = 8.dp),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            // The two bases differ by design: FULL voids every movement of
            // the prior sale (tenders, loyalty, and award), so the label
            // shows what was charged (authorizedAmount) and the outcome
            // popup reports what actually came back; ITEMS settles credit
            // lines against the outstanding tender — shelf price plus tax
            // of each selected return, the same figure its rows show.
            // Rebate/points proration is out of scope.
            // exhausted lines contribute zero (their refundMinor already is),
            // so a selection that outlived a refresh can't inflate the amount
            val itemsMinor = sale.items.filter { it.sku in selectedSkus }.sumOf { it.refundMinor }
            val amount = when (mode) {
                RefundMode.FULL -> sale.totalAmount
                RefundMode.ITEMS -> minorUnitsToDecimal(itemsMinor)
            }
            // The two modes need opposite session states: the full refund
            // voids the prior sale on its own session (no active checkout),
            // while item returns ring into the ACTIVE checkout's basket and
            // settle with it. Each disabled state says so — stored sales
            // browse fine offline, so a silently gray button would read as
            // broken.
            val connected = state.connection.phase != ConnectionPhase.DISCONNECTED
            val sessionActive = state.sessionId != null
            when {
                !connected -> Text(
                    "Connect to the terminal to run a refund",
                    style = MaterialTheme.typography.bodySmall,
                )
                mode == RefundMode.FULL && sessionActive -> Text(
                    "End the active checkout to run a full refund",
                    style = MaterialTheme.typography.bodySmall,
                )
                mode == RefundMode.ITEMS && !sessionActive -> Text(
                    "Start a checkout to ring returns into its basket",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = {
                    when (mode) {
                        RefundMode.FULL -> controller.refundSale(sale.id)
                        RefundMode.ITEMS -> {
                            controller.addReturnToBasket(sale.id, selectedSkus)
                            // the rung lines re-project with zero remaining;
                            // a stale selection would just read confusingly
                            selectedSkus = emptySet()
                        }
                    }
                },
                enabled = sale.refundable && connected && when (mode) {
                    RefundMode.FULL ->
                        fullAvailable && !sessionActive && !state.refundInProgress
                    RefundMode.ITEMS -> sessionActive && itemsMinor > 0
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        mode == RefundMode.FULL && state.refundInProgress -> "Refunding…"
                        mode == RefundMode.FULL -> "Refund $$amount"
                        else -> "Add return to basket ($$amount)"
                    }
                )
            }
        }
    }
}

@Composable
private fun LabeledRadio(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One cart line: optional leading control, "qty× description", trailing amount. */
@Composable
private fun LineItemRow(
    quantity: Int,
    description: String,
    amountLabel: String,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Text(
            "$quantity× $description",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(amountLabel, style = MaterialTheme.typography.bodyMedium)
        trailing()
    }
}

@Composable
private fun PaymentOutcomeDialog(outcome: PaymentOutcome, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                outcome.title,
                color = if (outcome.success) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
            )
        },
        text = {
            Column {
                Text(outcome.message)
                outcome.receipt?.let { receipt ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // the receipt as the terminal rendered it: monospace to
                    // keep its column layout, capped and scrollable so a
                    // long one doesn't push the dialog off screen
                    SelectionContainer {
                        Text(
                            receipt,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}

@Composable
private fun ConnectionPanel(state: EmulatorState, controller: EmulatorController) {
    // Saveable so an Android configuration change keeps the typed values;
    // the passphrase is deliberately plain remember — secrets don't belong
    // in saved instance state
    var terminalIp by rememberSaveable { mutableStateOf("") }
    var adbTunnel by rememberSaveable { mutableStateOf(false) }
    var encryptionOn by rememberSaveable { mutableStateOf(state.encryptionEnabled) }
    var passphrase by remember { mutableStateOf("") }
    var identifyOnStart by rememberSaveable { mutableStateOf(false) }

    // Adopt the autodetected address unless the operator already typed one
    LaunchedEffect(state.terminalAddress, state.addressAutodetected) {
        if (state.addressAutodetected && terminalIp.isBlank()) {
            terminalIp = state.terminalAddress
        }
    }

    val connected = state.connection.phase != ConnectionPhase.DISCONNECTED
    // Encryption needs a passphrase from somewhere: the field or NEXO_PASSPHRASE
    val passphraseAvailable = state.hasConfiguredPassphrase || passphrase.isNotBlank()
    // The adb tunnel needs no address — a USB-only terminal has none; the
    // field then only narrows the device pick (by serial or wifi-adb ip)
    val canConnect = (terminalIp.isNotBlank() || adbTunnel) &&
        (!encryptionOn || passphraseAvailable)

    val passphrasePlaceholder = if (state.hasConfiguredPassphrase) {
        "passphrase (NEXO_PASSPHRASE)"
    } else {
        "passphrase (required)"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Stack the controls when the window is narrow (phones, split
            // screen). The breakpoint scales with the font size — dp widths
            // alone don't grow with fontScale but the controls' intrinsic
            // widths do.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val compact = maxWidth < 560.dp * maxOf(1f, LocalDensity.current.fontScale)
                val ipField: @Composable (Modifier) -> Unit = { modifier ->
                    CompactTextField(
                        value = terminalIp,
                        onValueChange = { terminalIp = it },
                        placeholder = "Terminal IP",
                        enabled = !connected,
                        modifier = modifier,
                    )
                }
                val passphraseField: @Composable (Modifier) -> Unit = { modifier ->
                    CompactTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        placeholder = passphrasePlaceholder,
                        enabled = !connected,
                        masked = true,
                        modifier = modifier,
                    )
                }
                val encryptToggle: @Composable () -> Unit = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = encryptionOn,
                            onCheckedChange = { encryptionOn = it },
                            enabled = !connected,
                        )
                        Text("Encrypt", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                // Connect through a localhost `adb forward` instead of
                // dialing the terminal directly — the way around macOS
                // denying the JVM process local-network access
                val tunnelToggle: @Composable () -> Unit = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = adbTunnel,
                            onCheckedChange = { adbTunnel = it },
                            enabled = !connected,
                        )
                        Text("adb tunnel", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                val connectButton: @Composable (Modifier) -> Unit = { modifier ->
                    Button(
                        onClick = {
                            if (connected) {
                                controller.disconnect()
                            } else {
                                controller.connect(
                                    terminalIp.trim(), encryptionOn, passphrase, adbTunnel,
                                )
                            }
                        },
                        enabled = connected || canConnect,
                        modifier = modifier,
                    ) {
                        Text(if (connected) "Disconnect" else "Connect")
                    }
                }
                // One checkout session per customer — started and ended
                // explicitly, independent of the connection lifecycle
                val sessionButton: @Composable (Modifier) -> Unit = { modifier ->
                    val sessionActive = state.sessionId != null
                    Button(
                        onClick = {
                            if (sessionActive) {
                                controller.endSession()
                            } else {
                                controller.startSession(identifyOnStart)
                            }
                        },
                        enabled = connected,
                        modifier = modifier,
                    ) {
                        Text(if (sessionActive) "End Checkout" else "Start Checkout")
                    }
                }
                // Read at Start Checkout: prompts for member identification
                // right after the session starts
                val identifyToggle: @Composable () -> Unit = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = identifyOnStart,
                            onCheckedChange = { identifyOnStart = it },
                            enabled = state.sessionId == null,
                        )
                        Text("Identify", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                // Aborts whatever is on the terminal (payment, identify
                // prompt, card read) — it sits with the connection/session
                // controls rather than next to Pay because its scope is
                // wider than the payment
                val abortButton: @Composable (Modifier) -> Unit = { modifier ->
                    Button(
                        onClick = { controller.abort() },
                        enabled = state.sessionOperationInProgress,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = modifier,
                    ) {
                        Text("Abort operation")
                    }
                }
                val clearBasketButton: @Composable (Modifier) -> Unit = { modifier ->
                    Button(
                        onClick = { controller.clearBasket() },
                        enabled = state.sessionId != null && state.basket.isNotEmpty() &&
                            !state.sessionOperationInProgress,
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = modifier,
                    ) {
                        Text("Clear basket")
                    }
                }
                if (compact) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ipField(Modifier.fillMaxWidth())
                        tunnelToggle()
                        encryptToggle()
                        if (encryptionOn) {
                            passphraseField(Modifier.fillMaxWidth())
                        }
                        connectButton(Modifier.fillMaxWidth())
                        identifyToggle()
                        sessionButton(Modifier.fillMaxWidth())
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            clearBasketButton(Modifier.weight(1f))
                            abortButton(Modifier.weight(1f))
                        }
                    }
                } else {
                    // Two rows, one per concern: reaching the terminal, then
                    // driving the checkout. One row no longer fits — an
                    // overflowing Row squeezes its last children into
                    // word-per-line buttons that blow the panel up vertically.
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ipField(Modifier.width(160.dp))
                            tunnelToggle()
                            encryptToggle()
                            if (encryptionOn) {
                                passphraseField(Modifier.width(240.dp))
                            }
                            connectButton(Modifier)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            sessionButton(Modifier)
                            identifyToggle()
                            Spacer(Modifier.weight(1f))
                            clearBasketButton(Modifier)
                            abortButton(Modifier)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A single-line text field about two-thirds the height of Material's
 * [OutlinedTextField] (which has a fixed 56dp minimum) so the connection
 * controls stay one slim row.
 */
@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    masked: Boolean = false,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface.let {
        if (enabled) it else it.copy(alpha = 0.5f)
    }
    Box(
        modifier = modifier
            .height(38.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = contentColor),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isEmpty()) {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Connection, TLS, and encryption indicators for the top app bar. */
@Composable
private fun StatusIndicators(state: EmulatorState) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.padding(end = 16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val (color, label) = when (state.connection.phase) {
                ConnectionPhase.CONNECTED -> Color(0xFF2E7D32) to "Connected"
                ConnectionPhase.CONNECTING -> Color(0xFFF9A825) to "Connecting…"
                ConnectionPhase.ERROR -> Color(0xFFC62828) to "Unreachable"
                ConnectionPhase.DISCONNECTED -> Color(0xFF9E9E9E) to "Disconnected"
            }
            Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
            Text(
                text = listOfNotNull(label, state.connection.detail).joinToString(" — "),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = state.tls.label +
                "   ·   Encryption: " + (if (state.encryptionEnabled) "on" else "off") +
                "   ·   Session: " + (state.sessionId?.take(8) ?: "none"),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private enum class SaleWorkspaceTab(val label: String) {
    PRODUCTS("Products"), STORED_VALUE("Stored Value")
}

private enum class StoredValueAction(val label: String) {
    BALANCE("Balance inquiry"), ACTIVATION("Activation"), PURCHASE("Purchase")
}

@Composable
private fun SaleWorkspace(
    products: List<Product>,
    state: EmulatorState,
    controller: EmulatorController,
    modifier: Modifier = Modifier,
) {
    var selectedTabIndex by rememberSaveable { mutableStateOf(SaleWorkspaceTab.PRODUCTS.ordinal) }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SaleWorkspaceTab.entries.forEach { tab ->
                    Tab(
                        selected = tab.ordinal == selectedTabIndex,
                        onClick = { selectedTabIndex = tab.ordinal },
                        text = { Text(tab.label) },
                    )
                }
            }
            when (SaleWorkspaceTab.entries[selectedTabIndex]) {
                SaleWorkspaceTab.PRODUCTS -> ProductGrid(
                    products = products,
                    state = state,
                    controller = controller,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                SaleWorkspaceTab.STORED_VALUE -> StoredValuePanel(
                    state = state,
                    controller = controller,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ProductGrid(
    products: List<Product>,
    state: EmulatorState,
    controller: EmulatorController,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (state.sessionId == null) {
            Text(
                "Start Checkout to add products to the basket",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        // weight(1f) so the grid measures against the space under the
        // tabs instead of the card's full height (clips the last rows).
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 8.dp).weight(1f),
        ) {
            items(products, key = { it.sku }) { product ->
                Button(
                    onClick = { controller.addProduct(product) },
                    enabled = state.canRingProducts,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            product.name,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(product.priceLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun StoredValuePanel(
    state: EmulatorState,
    controller: EmulatorController,
    modifier: Modifier = Modifier,
) {
    var selectedActionIndex by rememberSaveable {
        mutableStateOf(StoredValueAction.BALANCE.ordinal)
    }
    val action = StoredValueAction.entries[selectedActionIndex]
    var cardNumber by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var consumedReadSequence by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(state.acquiredCard) {
        val read = state.acquiredCard ?: return@LaunchedEffect
        if (read.sequence > consumedReadSequence) {
            consumedReadSequence = read.sequence
            cardNumber = read.number
        }
    }
    val canOperate = state.sessionId != null && !state.sessionOperationInProgress

    Column(
        modifier = modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TabRow(
            selectedTabIndex = selectedActionIndex,
            modifier = Modifier.fillMaxWidth(),
        ) {
            StoredValueAction.entries.forEach { option ->
                Tab(
                    selected = option.ordinal == selectedActionIndex,
                    onClick = { selectedActionIndex = option.ordinal },
                    text = { Text(option.label) },
                )
            }
        }
        Text(
            when (action) {
                StoredValueAction.BALANCE ->
                    "Query the available balance without changing the card."
                StoredValueAction.ACTIVATION ->
                    "Activate a new card with a zero starting balance."
                StoredValueAction.PURCHASE ->
                    "Add a funded card purchase to the basket; activation runs after payment."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactTextField(
                value = cardNumber,
                onValueChange = { cardNumber = it },
                placeholder = when (action) {
                    StoredValueAction.PURCHASE ->
                        "Card number (blank = read at settlement)"
                    else -> "Card number (blank = read on terminal)"
                },
                enabled = !state.sessionOperationInProgress,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { controller.acquireCard() },
                enabled = canOperate,
            ) {
                Text(if (state.cardReadInProgress) "Reading…" else "Read card")
            }
        }
        if (action == StoredValueAction.PURCHASE) {
            CompactTextField(
                value = amount,
                onValueChange = { amount = it },
                placeholder = "Purchase amount (for example 25.00)",
                enabled = !state.sessionOperationInProgress,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.sessionId == null) {
            Text(
                "Start Checkout to use stored value operations",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                when (action) {
                    StoredValueAction.BALANCE ->
                        controller.inquireStoredValueBalance(cardNumber)
                    StoredValueAction.ACTIVATION ->
                        controller.activateStoredValue(cardNumber)
                    StoredValueAction.PURCHASE -> {
                        controller.addGiftCardPurchase(amount, cardNumber)
                        amount = ""
                        cardNumber = ""
                    }
                }
            },
            enabled = when (action) {
                StoredValueAction.PURCHASE ->
                    positiveMoneyMinor(amount) != null && state.canRingProducts
                else -> canOperate
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    state.storedValueInProgress -> "Working…"
                    action == StoredValueAction.BALANCE -> "Check balance"
                    action == StoredValueAction.ACTIVATION -> "Activate card"
                    else -> "Add purchase to basket"
                }
            )
        }
    }
}

private enum class AdjustmentKind { CREDIT, DISCOUNT }

private data class BasketAdjustment(val line: BasketLine, val kind: AdjustmentKind)

@Composable
private fun BasketCard(
    state: EmulatorState,
    controller: EmulatorController,
    modifier: Modifier = Modifier,
) {
    var adjustment by remember { mutableStateOf<BasketAdjustment?>(null) }
    adjustment?.let { selected ->
        BasketAdjustmentDialog(selected, state, controller) { adjustment = null }
    }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Basket — total $${state.basketTotal}",
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.basketTax != "0.00") {
                Text(
                    "incl. $${state.basketTax} tax (NJ 6.625%)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            if (state.basket.isEmpty()) {
                Text("Empty", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    // keyed on sku AND direction: a mixed basket may hold a
                    // sale line and a return of the same sku side by side
                    items(state.basket, key = { it.itemId }) { line ->
                        LineItemRow(
                            line.quantity,
                            buildString {
                                append(line.description)
                                when (line.type) {
                                    BasketLineType.SALE -> if (line.giftCard) {
                                        append(" (stored value purchase)")
                                    }
                                    BasketLineType.RETURN -> append(" (return)")
                                    BasketLineType.CREDIT -> append(" (credit)")
                                }
                                if (line.discountLabels.isNotEmpty()) {
                                    append(" · ")
                                    append(line.discountLabels.joinToString())
                                    append(" −$")
                                    append(line.discountTotal)
                                }
                            },
                            "$${line.lineTotal}",
                            Modifier.padding(vertical = 2.dp),
                            trailing = {
                                if (line.type == BasketLineType.SALE) {
                                    TextButton(
                                        onClick = {
                                            adjustment = BasketAdjustment(
                                                line, AdjustmentKind.DISCOUNT,
                                            )
                                        },
                                        enabled = state.sessionId != null &&
                                            !state.paymentInProgress,
                                        contentPadding = PaddingValues(horizontal = 6.dp),
                                    ) {
                                        Text("Discount")
                                    }
                                    TextButton(
                                        onClick = {
                                            adjustment = BasketAdjustment(
                                                line, AdjustmentKind.CREDIT,
                                            )
                                        },
                                        enabled = state.sessionId != null &&
                                            !state.paymentInProgress,
                                        contentPadding = PaddingValues(horizontal = 6.dp),
                                    ) {
                                        Text("Credit")
                                    }
                                }
                            },
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            PaymentControls(state, controller)
        }
    }
}

@Composable
private fun BasketAdjustmentDialog(
    adjustment: BasketAdjustment,
    state: EmulatorState,
    controller: EmulatorController,
    onDismiss: () -> Unit,
) {
    val line = adjustment.line
    val discount = adjustment.kind == AdjustmentKind.DISCOUNT
    var amount by remember(adjustment) {
        mutableStateOf(
            if (discount && line.discountTotal != "0.00") {
                line.discountTotal.removePrefix("-")
            } else {
                ""
            }
        )
    }
    var label by remember(adjustment) {
        mutableStateOf(
            if (discount) line.discountLabels.firstOrNull().orEmpty()
            else "Credit for ${line.description}"
        )
    }
    val enteredMinor = nonNegativeMoneyMinor(amount)
    val maximumMinor = nonNegativeMoneyMinor(
        if (discount) line.originalTotal else line.lineTotal
    ) ?: 0L
    val valid = enteredMinor != null && enteredMinor <= maximumMinor &&
        (discount || enteredMinor > 0L)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (discount) "Apply line discount" else "Apply item credit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${line.quantity}× ${line.description} · maximum $${minorUnitsToDecimal(maximumMinor)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                CompactTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    placeholder = if (discount) "Amount (0 clears)" else "Credit amount",
                    enabled = !state.paymentInProgress,
                    modifier = Modifier.fillMaxWidth(),
                )
                CompactTextField(
                    value = label,
                    onValueChange = { label = it },
                    placeholder = "Receipt label",
                    enabled = !state.paymentInProgress,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (discount) {
                        controller.applyDiscount(line.itemId, amount, label)
                    } else {
                        controller.applyCredit(line.itemId, amount, label)
                    }
                    onDismiss()
                },
                enabled = valid && !state.sessionOperationInProgress,
            ) {
                Text(if (discount && enteredMinor == 0L) "Clear" else "Apply")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Exact two-decimal parser for UI validation; controller parsing remains
 * authoritative and uses BigDecimal on JVM. */
private fun nonNegativeMoneyMinor(raw: String): Long? {
    val value = raw.trim()
    if (!Regex("\\d+(\\.\\d{0,2})?").matches(value)) return null
    val pieces = value.split('.', limit = 2)
    val whole = pieces[0].toLongOrNull() ?: return null
    if (whole > Long.MAX_VALUE / 100) return null
    val fraction = pieces.getOrNull(1).orEmpty().padEnd(2, '0').ifEmpty { "00" }
    val minor = whole * 100 + (fraction.toLongOrNull() ?: return null)
    return minor.takeIf { it >= 0 }
}

private fun positiveMoneyMinor(raw: String): Long? =
    nonNegativeMoneyMinor(raw)?.takeIf { it > 0 }

private val EmulatorState.sessionOperationInProgress: Boolean
    get() = paymentInProgress || cardReadInProgress || storedValueInProgress ||
        identifyInProgress || refundInProgress

private val EmulatorState.canRingProducts: Boolean
    get() = sessionId != null && !sessionOperationInProgress

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentControls(state: EmulatorState, controller: EmulatorController) {
    // Toggles for the next payment; the loyalty steps run only for an
    // identified member (at Start Checkout, or self-identified on the
    // terminal during the flow)
    var rebates by rememberSaveable { mutableStateOf(false) }
    var redemption by rememberSaveable { mutableStateOf(false) }
    var award by rememberSaveable { mutableStateOf(false) }
    // Gift card tender: charged first, remainder goes to the card payment
    var giftCard by rememberSaveable { mutableStateOf(false) }
    // Net by default: a mixed basket moves only the signed difference;
    // unchecked, returns refund in full to their original tenders and the
    // sale lines are charged in full
    var netSettlement by rememberSaveable { mutableStateOf(true) }
    var giftCardNumber by rememberSaveable { mutableStateOf("") }

    // One payment per checkout: pay again only after the next Start Checkout
    val paid = state.lastPayment != null
    // no Pay during a card read or identify prompt: the shared operation
    // claim would refuse it
    val canPay = state.sessionId != null && state.basket.isNotEmpty() &&
        !state.sessionOperationInProgress && !paid

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // FlowRow: five labeled checkboxes overflow a narrow card; wrap
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LoyaltyCheckbox("Rebates", rebates, !state.paymentInProgress) { rebates = it }
            LoyaltyCheckbox("Redemption", redemption, !state.paymentInProgress) { redemption = it }
            LoyaltyCheckbox("Award", award, !state.paymentInProgress) { award = it }
            LoyaltyCheckbox("Gift card", giftCard, !state.paymentInProgress) { giftCard = it }
            LoyaltyCheckbox("Net settlement", netSettlement, !state.paymentInProgress) {
                netSettlement = it
            }
        }
        if (giftCard) {
            // Adopt each terminal card read into the field. Tracking the
            // consumed sequence (saveable, like the field itself) keeps a
            // re-shown field from resurrecting an old read the operator
            // already cleared, while a genuine re-read still lands.
            var consumedReadSequence by rememberSaveable { mutableStateOf(0) }
            LaunchedEffect(state.acquiredCard) {
                val read = state.acquiredCard ?: return@LaunchedEffect
                if (read.sequence > consumedReadSequence) {
                    consumedReadSequence = read.sequence
                    giftCardNumber = read.number
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactTextField(
                    value = giftCardNumber,
                    onValueChange = { giftCardNumber = it },
                    placeholder = "Gift card number (blank = swipe on terminal)",
                    enabled = !state.paymentInProgress,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { controller.acquireCard() },
                    enabled = state.sessionId != null && !state.sessionOperationInProgress,
                ) {
                    Text("Read card")
                }
            }
        }
        if (paid) {
            // the checkout auto-ends on full payment; the hint covers the
            // fallback where that end failed and the session is still open
            val next = if (state.sessionId == null) {
                "Start Checkout for the next customer"
            } else {
                "End Checkout to start the next one"
            }
            Text(
                "${state.lastPayment} — $next",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF2E7D32),
            )
        }
        Button(
            onClick = {
                controller.settle(
                    LoyaltyOptions(
                        rebates = rebates,
                        redemption = redemption,
                        award = award,
                    ),
                    if (giftCard) StoredValueOptions(cardNumber = giftCardNumber) else null,
                    net = netSettlement,
                )
            },
            enabled = canPay,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                // "Settle", not "Pay": a settlement may collect, refund
                // (returns exceed additions), or both across mixed tenders
                when {
                    state.paymentInProgress -> "Settling…"
                    paid -> "Settled"
                    state.basketTotal.startsWith("-") ->
                        "Settle (refund $${state.basketTotal.drop(1)})"
                    else -> "Settle $${state.basketTotal}"
                }
            )
        }
    }
}

@Composable
private fun LoyaltyCheckbox(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EventsCard(state: EmulatorState, modifier: Modifier = Modifier) {
    // 0 = curated events, 1 = raw logger output (SDK JUL records, stack traces)
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Events") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Detailed") },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
            val lines = if (selectedTab == 0) state.events else state.detailedEvents
            SelectionContainer(modifier = Modifier.weight(1f)) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(lines.asReversed()) { event ->
                        Text(
                            event,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
