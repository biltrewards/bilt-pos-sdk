package com.bilt.pos.emulator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bilt.pos.emulator.catalog.Product
import com.bilt.pos.emulator.session.ConnectionPhase
import com.bilt.pos.emulator.session.EmulatorController
import com.bilt.pos.emulator.session.EmulatorState

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
            topBar = { TopAppBar(title = { Text("Bilt POS Emulator") }) },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ConnectionPanel(state, controller)
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    if (maxWidth < 700.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ProductGrid(products, controller, Modifier.weight(1.4f))
                            BasketCard(state, Modifier.weight(0.8f))
                            EventsCard(state, Modifier.weight(0.8f))
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ProductGrid(products, controller, Modifier.weight(1.6f))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                BasketCard(state, Modifier.weight(1f))
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
    var terminalIp by remember { mutableStateOf("") }
    var encryptionOn by remember { mutableStateOf(state.encryptionEnabled) }
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

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Stack the controls when the window is narrow (phones, split
            // screen). The breakpoint scales with the font size — dp widths
            // alone don't grow with fontScale but the button's intrinsic
            // width does.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val compact = maxWidth < 420.dp * maxOf(1f, LocalDensity.current.fontScale)
                val ipField: @Composable (Modifier) -> Unit = { modifier ->
                    OutlinedTextField(
                        value = terminalIp,
                        onValueChange = { terminalIp = it },
                        label = { Text("Terminal IP") },
                        singleLine = true,
                        enabled = !connected,
                        modifier = modifier,
                    )
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
                if (compact) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ipField(Modifier.fillMaxWidth())
                        connectButton(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ipField(Modifier.weight(1f))
                        connectButton(Modifier)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = encryptionOn,
                    onCheckedChange = { encryptionOn = it },
                    enabled = !connected,
                )
                Text("Encrypt messages", style = MaterialTheme.typography.bodyMedium)
            }
            if (encryptionOn) {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Passphrase") },
                    placeholder = {
                        Text(
                            if (state.hasConfiguredPassphrase) {
                                "using NEXO_PASSPHRASE — type to override"
                            } else {
                                "required — no NEXO_PASSPHRASE configured"
                            }
                        )
                    },
                    singleLine = true,
                    enabled = !connected,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            StatusRow(state)
        }
    }
}

@Composable
private fun StatusRow(state: EmulatorState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        text = state.tls.label + "   ·   Encryption: " +
            if (state.encryptionEnabled) "on" else "off",
        style = MaterialTheme.typography.bodySmall,
    )
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
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                items(products, key = { it.sku }) { product ->
                    Button(onClick = { controller.addProduct(product) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(product.name, style = MaterialTheme.typography.labelLarge)
                            Text(product.priceLabel, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BasketCard(state: EmulatorState, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Basket — total $${state.basketTotal}",
                style = MaterialTheme.typography.titleMedium,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            if (state.basket.isEmpty()) {
                Text("Empty", style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn {
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
        }
    }
}

@Composable
private fun EventsCard(state: EmulatorState, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Log", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn {
                items(state.events.asReversed()) { event ->
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
