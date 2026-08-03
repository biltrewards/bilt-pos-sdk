package com.bilt.pos.emulator

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.Density
import com.bilt.pos.emulator.catalog.MockProductProvider
import com.bilt.pos.emulator.catalog.Product
import com.bilt.pos.emulator.session.BasketLine
import com.bilt.pos.emulator.session.ConnectionPhase
import com.bilt.pos.emulator.session.ConnectionStatus
import com.bilt.pos.emulator.session.EmulatorController
import com.bilt.pos.emulator.session.EmulatorState
import com.bilt.pos.emulator.session.LoyaltyOptions
import com.bilt.pos.emulator.session.TlsStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the full emulator UI headlessly ([ImageComposeScene], no window
 * or display needed) in two representative states — a render smoke test
 * that doubles as the screenshot generator: the PNGs land in
 * `build/screenshots`, and the copies committed under `emulator/docs` are
 * refreshed from there when the UI changes.
 */
class ScreenshotGenerator {

    private class FakeController(state: EmulatorState) : EmulatorController {
        override val state: StateFlow<EmulatorState> = MutableStateFlow(state)
        override fun autodetectAddress() = Unit
        override fun connect(address: String, encryptionEnabled: Boolean, passphraseOverride: String?) = Unit
        override fun disconnect() = Unit
        override fun startSession() = Unit
        override fun endSession() = Unit
        override fun addProduct(product: Product) = Unit
        override fun pay(loyalty: LoyaltyOptions) = Unit
        override fun abortPayment() = Unit
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(state: EmulatorState, file: File) {
        // 1100x800 dp at 2x density — the desktop window's default size,
        // above the 700dp breakpoint so the wide layout renders
        val scene = ImageComposeScene(width = 2200, height = 1600, density = Density(2f)) {
            EmulatorApp(FakeController(state), MockProductProvider.products())
        }
        val png = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
        scene.close()
        // the render itself is the test: a composition crash or an empty
        // frame fails here, screenshots are the byproduct
        assertTrue(png.size > 10_000, "suspiciously small render: ${png.size} bytes")
        file.writeBytes(png)
        println("wrote ${file.absolutePath}")
    }

    @Test
    fun generate() {
        // test JVM user.dir is the module directory
        val dir = File("build/screenshots").apply { mkdirs() }.absolutePath
        val midCheckout = EmulatorState(
            terminalAddress = "192.168.1.57",
            connection = ConnectionStatus(ConnectionPhase.CONNECTED, "POI OK"),
            tls = TlsStatus.Verified,
            encryptionEnabled = true,
            sessionId = "3f9c2a7e-5d41-4b8a-9c16-8e2f70d1b634",
            basket = listOf(
                BasketLine("SKU-014", "Wireless Earbuds", 1, "129.99"),
                BasketLine("SKU-003", "Sparkling Water 12-pack", 2, "13.98"),
                BasketLine("SKU-021", "Desk Lamp", 1, "34.99"),
            ),
            basketTotal = "189.89",
            basketTax = "10.93",
            events = listOf(
                "10:41:03 Connecting to https://192.168.1.57:8443/nexo (encryption=true)",
                "10:41:04 TLS: verified",
                "10:41:04 Terminal connected (POI status: OK)",
                "10:41:12 Checkout session started (id 3f9c2a7e)",
                "10:41:12 Customer display cleared (empty basket)",
                "10:41:20 Added Wireless Earbuds ($129.99)",
                "10:41:24 Added Sparkling Water 12-pack ($6.99)",
                "10:41:25 Added Sparkling Water 12-pack ($6.99)",
                "10:41:31 Added Desk Lamp ($34.99)",
            ),
        )
        render(midCheckout, File(dir, "emulator-mid-checkout.png"))

        val paid = midCheckout.copy(
            basket = midCheckout.basket.map {
                if (it.sku == "SKU-014") it.copy(lineTotal = "119.99") else it
            },
            basketTotal = "174.89",
            lastPayment = "Paid $174.89 — card $169.89 (Visa), rebates −$10.00, " +
                "5 pts −$5.00, earned 175 pts (balance 964)",
            events = midCheckout.events + listOf(
                "10:41:40 Identifying member on the terminal…",
                "10:41:47 Member identified: 98234 (K-Club), 789 pts, 1 reward(s)",
                "10:41:48 Starting payment — rebates on, redemption on, award on",
                "10:41:50 Rebates applied: −$10.00 → total $179.89",
                "10:41:51 Points redeemed: 5 (−$5.00) → total $174.89",
                "10:42:03 Paid $174.89 — card $169.89 (Visa), rebates −$10.00, " +
                    "5 pts −$5.00, earned 175 pts (balance 964)",
            ),
        )
        render(paid, File(dir, "emulator-paid.png"))
    }
}
