package com.bilt.pos.emulator.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.bilt.pos.emulator.EmulatorApp
import com.bilt.pos.emulator.catalog.MockProductProvider
import com.bilt.pos.emulator.session.NexoEmulatorController
import com.bilt.pos.emulator.store.JsonlSaleStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

fun main() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val controller = NexoEmulatorController(
        scope = scope,
        saleStore = JsonlSaleStore(
            File(System.getProperty("user.home"), ".bilt-pos-emulator/sales.jsonl")
        ),
    )
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

    // application {} has returned (window closed): end any active checkout
    // session synchronously so the terminal doesn't keep session-scoped
    // state after the emulator is gone. Bounded by the client's timeouts.
    controller.shutdown()
}
