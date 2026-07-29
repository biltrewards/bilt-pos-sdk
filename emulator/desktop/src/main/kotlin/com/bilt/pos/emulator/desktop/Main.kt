package com.bilt.pos.emulator.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.bilt.pos.emulator.EmulatorApp
import com.bilt.pos.emulator.SdkProbe

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Bilt POS Emulator",
        state = rememberWindowState(width = 1100.dp, height = 800.dp),
    ) {
        EmulatorApp(sdkInfo = SdkProbe.describe())
    }
}
