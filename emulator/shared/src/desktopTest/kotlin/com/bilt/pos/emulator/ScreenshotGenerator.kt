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
import com.bilt.pos.emulator.session.SaleItemUi
import com.bilt.pos.emulator.session.StoredSaleUi
import com.bilt.pos.emulator.session.StoredValueOptions
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
        override fun connect(
            address: String,
            encryptionEnabled: Boolean,
            passphraseOverride: String?,
            adbTunnel: Boolean,
        ) = Unit
        override fun disconnect() = Unit
        override fun startSession(identifyOnStart: Boolean) = Unit
        override fun endSession() = Unit
        override fun addProduct(product: Product) = Unit
        override fun settle(loyalty: LoyaltyOptions, storedValue: StoredValueOptions?) = Unit
        override fun acquireCard() = Unit
        override fun refundSale(saleId: String) = Unit
        override fun addReturnToBasket(saleId: String, skus: Set<String>) = Unit
        override fun abort() = Unit
        override fun dismissPaymentOutcome() = Unit
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(state: EmulatorState, file: File, initialTab: EmulatorTab = EmulatorTab.SALE) {
        // 1100x1000 dp at 2x density — the desktop window's default size,
        // above the 700dp breakpoint so the wide layout renders
        val scene = ImageComposeScene(width = 2200, height = 2000, density = Density(2f)) {
            EmulatorApp(FakeController(state), MockProductProvider.products(), initialTab)
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
            // a fully collected payment ends the checkout automatically,
            // clearing the basket; the summary line carries the outcome
            sessionId = null,
            basket = emptyList(),
            basketTotal = "0.00",
            basketTax = "0.00",
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
                "10:42:03 Payment complete — ending the checkout automatically",
                "10:42:04 Checkout ended",
            ),
        )
        render(paid, File(dir, "emulator-paid.png"))

        val refund = paid.copy(
            sales = listOf(
                StoredSaleUi(
                    id = "9d1f4c2b-7a53-4e08-b1d9-2c6e91f0a487",
                    completedAtLabel = "Aug 6, 10:42",
                    totalAmount = "174.89",
                    memberId = "98234",
                    items = listOf(
                        SaleItemUi("SKU-014", "Wireless Earbuds", 1, 12999),
                        SaleItemUi("SKU-003", "Sparkling Water 12-pack", 2, 1398),
                        SaleItemUi("SKU-021", "Desk Lamp", 1, 3499),
                    ),
                ),
                StoredSaleUi(
                    id = "5b8e03d6-1f27-49c4-a8f2-70b3c9e514d2",
                    completedAtLabel = "Aug 6, 09:58",
                    totalAmount = "42.50",
                    items = listOf(
                        SaleItemUi("SKU-007", "Coffee Beans 1kg", 1, 4250),
                    ),
                    refunded = true,
                    fullyRefunded = true,
                ),
                StoredSaleUi(
                    id = "e2c76a91-3d40-4b6f-95c8-1a09d4f7b325",
                    completedAtLabel = "Aug 5, 17:21",
                    totalAmount = "89.99",
                    voided = true,
                ),
            ),
        )
        render(refund, File(dir, "emulator-refund.png"), initialTab = EmulatorTab.REFUND)
    }
}
