package com.bilt.pos.emulator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import com.bilt.pos.emulator.catalog.MockProductProvider
import com.bilt.pos.emulator.session.NexoEmulatorController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Retains the controller across configuration changes — a rotation must not
 * drop an active terminal session, basket, or diagnostics loop.
 */
class EmulatorViewModel : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val controller = NexoEmulatorController(scope)

    override fun onCleared() {
        controller.disconnect()
        scope.cancel()
    }
}

class MainActivity : ComponentActivity() {

    private val viewModel: EmulatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EmulatorApp(viewModel.controller, MockProductProvider.products())
        }
    }
}
