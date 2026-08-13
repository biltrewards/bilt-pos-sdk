package com.bilt.pos.emulator

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.bilt.pos.emulator.catalog.MockProductProvider
import com.bilt.pos.emulator.session.NexoEmulatorController
import com.bilt.pos.emulator.store.JsonlSaleStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File

/**
 * Retains the controller across configuration changes — a rotation must not
 * drop an active terminal session, basket, or diagnostics loop.
 * AndroidViewModel because the sale store persists into [Application.getFilesDir].
 */
class EmulatorViewModel(application: Application) : AndroidViewModel(application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val controller = NexoEmulatorController(
        scope = scope,
        saleStore = JsonlSaleStore.inDirectory(application.filesDir),
        callbackExecutor = ContextCompat.getMainExecutor(application),
    )

    override fun onCleared() {
        // Teardown, not a user action: disconnect()'s mid-payment refusal
        // has no one to read it here. shutdown() instead queues the End
        // bracket behind any in-flight payment — off the main thread, since
        // it blocks for the client's timeouts; best-effort, bounded by
        // process death like the desktop exit path.
        Thread(controller::shutdown, "emulator-shutdown")
            .apply { isDaemon = true }
            .start()
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
