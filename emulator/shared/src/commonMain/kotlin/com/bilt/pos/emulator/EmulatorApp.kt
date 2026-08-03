package com.bilt.pos.emulator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bilt.pos.emulator.catalog.Product
import com.bilt.pos.emulator.session.ConnectionPhase
import com.bilt.pos.emulator.session.EmulatorController
import com.bilt.pos.emulator.session.EmulatorState
import com.bilt.pos.emulator.session.LoyaltyOptions

/**
 * Root composable of the terminal emulator, shared by the Android and
 * desktop targets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmulatorApp(controller: EmulatorController, products: List<Product>) {
    val state by controller.state.collectAsState()

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Bilt POS Emulator") },
                    actions = { StatusIndicators(state) },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ConnectionPanel(state, controller)
                // weight(1f), not fillMaxSize(): a non-weighted child measures
                // against the Column's full height and would overflow by the
                // connection panel's height
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (maxWidth < 700.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ProductGrid(products, controller, Modifier.weight(1.4f))
                            BasketCard(state, controller, Modifier.weight(0.8f))
                            EventsCard(state, Modifier.weight(0.8f))
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ProductGrid(products, controller, Modifier.weight(1f))
                            Column(
                                modifier = Modifier.weight(2f),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                BasketCard(state, controller, Modifier.weight(1f))
                                EventsCard(state, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionPanel(state: EmulatorState, controller: EmulatorController) {
    // Saveable so an Android configuration change keeps the typed values;
    // the passphrase is deliberately plain remember — secrets don't belong
    // in saved instance state
    var terminalIp by rememberSaveable { mutableStateOf("") }
    var encryptionOn by rememberSaveable { mutableStateOf(state.encryptionEnabled) }
    var passphrase by remember { mutableStateOf("") }

    // Adopt the autodetected address unless the operator already typed one
    LaunchedEffect(state.terminalAddress, state.addressAutodetected) {
        if (state.addressAutodetected && terminalIp.isBlank()) {
            terminalIp = state.terminalAddress
        }
    }

    val connected = state.connection.phase != ConnectionPhase.DISCONNECTED
    // Encryption needs a passphrase from somewhere: the field or NEXO_PASSPHRASE
    val passphraseAvailable = state.hasConfiguredPassphrase || passphrase.isNotBlank()
    val canConnect = terminalIp.isNotBlank() && (!encryptionOn || passphraseAvailable)

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
                val connectButton: @Composable (Modifier) -> Unit = { modifier ->
                    Button(
                        onClick = {
                            if (connected) {
                                controller.disconnect()
                            } else {
                                controller.connect(terminalIp.trim(), encryptionOn, passphrase)
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
                            if (sessionActive) controller.endSession() else controller.startSession()
                        },
                        enabled = connected,
                        modifier = modifier,
                    ) {
                        Text(if (sessionActive) "End Checkout" else "Start Checkout")
                    }
                }
                if (compact) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ipField(Modifier.fillMaxWidth())
                        encryptToggle()
                        if (encryptionOn) {
                            passphraseField(Modifier.fillMaxWidth())
                        }
                        connectButton(Modifier.fillMaxWidth())
                        sessionButton(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ipField(Modifier.width(160.dp))
                        connectButton(Modifier)
                        sessionButton(Modifier)
                        encryptToggle()
                        if (encryptionOn) {
                            passphraseField(Modifier.width(240.dp))
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

@Composable
private fun ProductGrid(
    products: List<Product>,
    controller: EmulatorController,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Products", style = MaterialTheme.typography.titleMedium)
            // weight(1f) so the list measures against the space under the
            // header instead of the card's full height (clips the last rows)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp).weight(1f),
            ) {
                items(products, key = { it.sku }) { product ->
                    Button(
                        onClick = { controller.addProduct(product) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                product.name,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(product.priceLabel, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BasketCard(
    state: EmulatorState,
    controller: EmulatorController,
    modifier: Modifier = Modifier,
) {
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
                    items(state.basket, key = { it.sku }) { line ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                "${line.quantity}× ${line.description}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text("$${line.lineTotal}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            PaymentControls(state, controller)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentControls(state: EmulatorState, controller: EmulatorController) {
    // Toggles for the next payment. Identify prompts on the terminal before
    // paying; the loyalty steps work without it when the customer
    // self-identifies on the terminal during the flow.
    var identify by rememberSaveable { mutableStateOf(true) }
    var rebates by rememberSaveable { mutableStateOf(true) }
    var redemption by rememberSaveable { mutableStateOf(true) }
    var award by rememberSaveable { mutableStateOf(true) }

    // One payment per checkout: pay again only after the next Start Checkout
    val paid = state.lastPayment != null
    val canPay = state.sessionId != null && state.basket.isNotEmpty() &&
        !state.paymentInProgress && !paid

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // FlowRow: four labeled checkboxes overflow a narrow card; wrap
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LoyaltyCheckbox("Identify", identify, !state.paymentInProgress) { identify = it }
            LoyaltyCheckbox("Rebates", rebates, !state.paymentInProgress) { rebates = it }
            LoyaltyCheckbox("Redemption", redemption, !state.paymentInProgress) { redemption = it }
            LoyaltyCheckbox("Award", award, !state.paymentInProgress) { award = it }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    controller.pay(
                        LoyaltyOptions(
                            identify = identify,
                            rebates = rebates,
                            redemption = redemption,
                            award = award,
                        )
                    )
                },
                enabled = canPay,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    when {
                        state.paymentInProgress -> "Paying…"
                        paid -> "Paid"
                        else -> "Pay $${state.basketTotal}"
                    }
                )
            }
            Button(
                onClick = { controller.abortPayment() },
                enabled = state.paymentInProgress,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Abort")
            }
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
            LazyColumn(modifier = Modifier.weight(1f)) {
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
