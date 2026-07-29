package com.bilt.pos.emulator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Root composable of the terminal emulator, shared by the Android and
 * desktop targets. Platform wrappers pass in anything target-specific;
 * [sdkInfo] is a placeholder proving the SDK seam until the session
 * wiring lands.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmulatorApp(sdkInfo: String) {
    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Bilt POS Emulator") }) },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ConnectionPanel()
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = sdkInfo,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionPanel() {
    var terminalIp by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        // Stack the controls when the window is narrow (phones, split screen)
        // or font scaling shrinks the usable field width
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            val compact = maxWidth < 420.dp
            val ipField: @Composable (Modifier) -> Unit = { modifier ->
                OutlinedTextField(
                    value = terminalIp,
                    onValueChange = { terminalIp = it },
                    label = { Text("Terminal IP") },
                    singleLine = true,
                    modifier = modifier,
                )
            }
            val connectButton: @Composable (Modifier) -> Unit = { modifier ->
                Button(
                    onClick = { /* session wiring lands in a follow-up */ },
                    enabled = terminalIp.isNotBlank(),
                    modifier = modifier,
                ) {
                    Text("Connect")
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
    }
}
