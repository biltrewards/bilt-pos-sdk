package com.bilt.pos.emulator.store

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile

/**
 * [SaleStore] backed by an append-only JSONL file: one JSON object per line,
 * each a sale, refund, or void event. Appending never rewrites earlier
 * records, so a crash can at worst tear the final line — reads skip lines
 * that do not parse. Reads fold the event log into the current state.
 */
class JsonlSaleStore(private val file: File) : SaleStore {

    companion object {
        /** The store's file within a platform's data directory, so the two
         *  entry points don't each restate the layout. */
        fun inDirectory(dataDir: File): JsonlSaleStore =
            JsonlSaleStore(File(dataDir, "sales.jsonl"))
    }

    private val json = Json { ignoreUnknownKeys = true }

    override fun recordSale(sale: SaleRecord) = append(SaleEvent.Sale(sale))

    override fun recordRefund(saleId: String, refund: RefundRecord) =
        append(SaleEvent.Refund(saleId, refund))

    override fun recordVoid(saleId: String, voidRecord: VoidRecord) =
        append(SaleEvent.Void(saleId, voidRecord))

    override fun findSale(saleId: String): StoredSale? = fold()[saleId]

    override fun listSales(limit: Int): List<StoredSale> =
        fold().values.reversed().take(limit)

    private fun append(event: SaleEvent) {
        synchronized(this) {
            file.parentFile?.mkdirs()
            // A crash can tear the final line mid-record, leaving no trailing
            // newline; appending straight onto that wreckage would glue THIS
            // record to the torn one and lose both on read. A leading newline
            // isolates the torn fragment on its own (skipped) line.
            val line = json.encodeToString(SaleEvent.serializer(), event) + "\n"
            file.appendText(if (endsMidLine()) "\n$line" else line)
        }
    }

    private fun endsMidLine(): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        return RandomAccessFile(file, "r").use {
            it.seek(it.length() - 1)
            it.read() != '\n'.code
        }
    }

    /** Sales by id in recording order, with refunds/voids folded in. Events
     *  referencing an unknown sale (foreign or truncated file) are dropped. */
    private fun fold(): Map<String, StoredSale> {
        val sales = LinkedHashMap<String, StoredSale>()
        for (event in readEvents()) {
            when (event) {
                is SaleEvent.Sale -> sales[event.sale.id] = StoredSale(event.sale)
                is SaleEvent.Refund -> sales.computeIfPresent(event.saleId) { _, held ->
                    held.copy(refunds = held.refunds + event.refund)
                }
                is SaleEvent.Void -> sales.computeIfPresent(event.saleId) { _, held ->
                    held.copy(voided = event.voidRecord)
                }
            }
        }
        return sales
    }

    private fun readEvents(): List<SaleEvent> {
        if (!file.isFile) return emptyList()
        return synchronized(this) { file.readLines() }
            .mapNotNull { runCatching { json.decodeFromString(SaleEvent.serializer(), it) }.getOrNull() }
    }

    /** The on-disk line format; the discriminator is the default "type" field. */
    @Serializable
    private sealed interface SaleEvent {

        @Serializable
        @SerialName("sale")
        data class Sale(val sale: SaleRecord) : SaleEvent

        @Serializable
        @SerialName("refund")
        data class Refund(val saleId: String, val refund: RefundRecord) : SaleEvent

        @Serializable
        @SerialName("void")
        data class Void(val saleId: String, val voidRecord: VoidRecord) : SaleEvent
    }
}
