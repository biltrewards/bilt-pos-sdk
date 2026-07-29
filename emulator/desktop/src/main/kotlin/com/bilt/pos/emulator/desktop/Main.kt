package com.bilt.pos.emulator.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.bilt.pos.emulator.EmulatorApp
import com.bilt.pos.emulator.catalog.MockProductProvider
import com.bilt.pos.emulator.session.NexoEmulatorController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

fun main() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val controller = NexoEmulatorController(scope)
    controller.autodetectAddress()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Bilt POS Emulator",
            state = rememberWindowState(width = 1100.dp, height = 800.dp),
        ) {
            EmulatorApp(controller, MockProductProvider.products())
        }
    }
}
