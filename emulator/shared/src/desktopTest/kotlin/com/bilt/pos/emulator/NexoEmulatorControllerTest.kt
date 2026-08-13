package com.bilt.pos.emulator

import com.bilt.pos.emulator.session.EmulatorConfig
import com.bilt.pos.emulator.session.NexoEmulatorController
import com.bilt.pos.emulator.store.JsonlSaleStore
import com.bilt.pos.emulator.store.LegType
import com.bilt.pos.emulator.store.SaleItem
import com.bilt.pos.emulator.store.SaleRecord
import com.bilt.pos.emulator.store.TransactionLeg
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class NexoEmulatorControllerTest {

    /**
     * Constructs the REAL controller — the fakes used elsewhere skip its
     * init block, which once read a property declared further down the class
     * (initializers run in textual order) and crashed every app startup.
     * Also covers the init-time sales load and the StoredSale→UI projection.
     */
    @Test
    fun constructionLoadsStoredSalesIntoState() {
        val store = JsonlSaleStore(
            Files.createTempDirectory("ctrl-sales").resolve("sales.jsonl").toFile()
        )
        store.recordSale(
            SaleRecord(
                id = "sale-1",
                sessionId = "session-1",
                saleId = "bilt-emulator",
                poiId = "EMULATOR",
                currency = "USD",
                completedAt = "2026-08-06T10:15:30Z",
                memberId = "member-42",
                items = listOf(
                    SaleItem(
                        sku = "SKU-1",
                        description = "Water",
                        quantity = 2,
                        unitPrice = "1.05",
                        lineTotal = "2.10",
                    )
                ),
                authorizedAmount = "2.10",
                legs = listOf(TransactionLeg(LegType.CARD, "poi-1")),
            )
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val controller = NexoEmulatorController(
                scope = scope,
                config = EmulatorConfig(
                    passphrase = null,
                    keyId = "emulator",
                    keyVersion = 0,
                    caPem = null,
                    hostnamePattern = "*",
                ),
                saleStore = store,
                // serial stand-in for the app's UI thread
                callbackExecutor = Executors.newSingleThreadExecutor { task ->
                    Thread(task, "test-ui").apply { isDaemon = true }
                },
            )
            // the init-time load runs on a background dispatcher
            val sales = runBlocking {
                withTimeout(5_000) {
                    controller.state.first { it.sales.isNotEmpty() }.sales
                }
            }
            val sale = sales.single()
            assertEquals("sale-1", sale.id)
            assertEquals("2.10", sale.totalAmount)
            assertEquals("member-42", sale.memberId)
            assertEquals(210L, sale.items.single().lineTotalMinor)
            // the label must be formatted, not the raw-ISO fallback that a
            // not-yet-initialized formatter would silently produce (the
            // exact text depends on the system zone, so only assert the
            // fallback didn't happen)
            assertNotEquals("2026-08-06T10:15:30Z", sale.completedAtLabel)
        } finally {
            scope.cancel()
        }
    }
}
