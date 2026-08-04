package com.bilt.pos.emulator.store

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * [SaleStore] backed by an append-only JSONL file: one JSON object per line,
 * each a sale, refund, or void event. Appending never rewrites earlier
 * records, so a crash can at worst tear the final line — reads skip lines
 * that do not parse. Reads fold the event log into the current state.
 */
class JsonlSaleStore(private val file: File) : SaleStore {

    private val json = Json { ignoreUnknownKeys = true }

    override fun recordSale(sale: SaleRecord) = append(SaleEvent.Sale(sale))

    override fun recordRefund(saleId: String, refund: RefundRecord) =
        append(SaleEvent.Refund(saleId, refund))

    override fun recordVoid(saleId: String, voidRecord: VoidRecord) =
        append(SaleEvent.Void(saleId, voidRecord))

    override fun findSale(saleId: String): StoredSale? = fold()[saleId]

    override fun listSales(limit: Int): List<StoredSale> =
        fold().values.toList().takeLast(limit).asReversed()

    private fun append(event: SaleEvent) {
        synchronized(this) {
            file.parentFile?.mkdirs()
            file.appendText(json.encodeToString(SaleEvent.serializer(), event) + "\n")
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

    private fun readEvents(): List<SaleEvent> = synchronized(this) {
        val lines = if (file.isFile) file.readLines() else return emptyList()
        lines.mapNotNull { line ->
            if (line.isBlank()) {
                null
            } else {
                runCatching { json.decodeFromString(SaleEvent.serializer(), line) }.getOrNull()
            }
        }
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
