package com.bilt.pos.emulator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.bilt.pos.emulator.catalog.MockProductProvider
import com.bilt.pos.emulator.session.NexoEmulatorController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val controller by lazy { NexoEmulatorController(scope) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EmulatorApp(controller, MockProductProvider.products())
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
